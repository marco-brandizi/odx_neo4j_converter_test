package net.sourceforge.ondex.parser.neo4j.plugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.ondex.args.ArgumentDefinition;
import net.sourceforge.ondex.args.StringArgumentDefinition;
import net.sourceforge.ondex.args.URLArgumentDefinition;
import net.sourceforge.ondex.core.ConceptClass;
import net.sourceforge.ondex.core.ConceptName;
import net.sourceforge.ondex.core.ONDEXConcept;
import net.sourceforge.ondex.core.ONDEXEntity;
import net.sourceforge.ondex.core.ONDEXRelation;
import net.sourceforge.ondex.core.RelationType;
import net.sourceforge.ondex.export.ONDEXExport;
import net.sourceforge.ondex.parser.ONDEXParser;


/**
 * A rough Neo4j test exporter.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>4 Oct 2017</dd></dl>
 *
 */
public class CypherExporter extends ONDEXExport
{
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	@Override
	public String getId ()
	{
		return "cypherParser";
	}

	@Override
	public String getName ()
	{
		return "Cypher/Neo4J Parser";
	}

	@Override
	public String getVersion ()
	{
		return "1.0";
	}

	@Override
	public ArgumentDefinition<?>[] getArgumentDefinitions ()
	{
		return new ArgumentDefinition<?>[] {
			new StringArgumentDefinition ( "url", "Neo4j Connection URL" , true, "", false ) 
		};
	}

	@Override
	public void start () throws Exception
	{ 
		String url = (String) this.getArguments ().getUniqueValue ( "url" );
		
		Driver driver = GraphDatabase.driver( url, AuthTokens.basic( "neo4j", "graph" ) );
		Session session = driver.session();
				
		int ct = 0;
		StringBuilder stmtQueue = new StringBuilder ();
		
		for ( ONDEXConcept concept: this.graph.getConcepts () )
		{			
			String id = String.valueOf ( concept.getId () );
			String type = concept.getOfType ().getId ();
			String name = Optional.ofNullable ( concept.getConceptName () ).map ( ConceptName::getName ).orElse ( id );
			
			Map<String, String> attributes = getAttrs ( concept );

			Map<String, String> params = new HashMap<> ();
			params.put ( "id", id );
			params.put ( "name", name );
			params.putAll ( attributes );
			
			StringBuilder stmt = new StringBuilder ( "CREATE ( :" );
			stmt.append ( type );
			
			appendParams ( stmt, params );
			stmt.append ( " )\n" );
			
			this.queueCypher ( session, stmtQueue, stmt, ++ct, "concepts" );
		}
		
		// flush the queue
		this.queueCypher ( session, stmtQueue, null, ct, "concepts" );

		
		for ( ConceptClass cc: this.graph.getMetaData ().getConceptClasses () )
		{
			String ctype = cc.getId ();
			log.info ( "Indexing \"{}\"", ctype );
			StringBuilder stmt = new StringBuilder ( "CREATE INDEX ON :`" + ctype + "`(id)" );
			runCypher ( session, stmt );
		}
				
		ct = 0;
		for ( ONDEXRelation rel: this.graph.getRelations () )
		{
			String type = rel.getOfType ().getId ();
			ONDEXConcept from = rel.getFromConcept ();
			ONDEXConcept to = rel.getToConcept ();
			Map<String, String> params = getAttrs ( rel );
			
			StringBuilder stmt = new StringBuilder ( "MATCH ( from:" );
			stmt.append ( from.getOfType ().getId () );
			stmt.append ( " { id: '" ).append ( from.getId () ).append ( "'} ), " );

			stmt.append ( "( to:" );
			stmt.append ( to.getOfType ().getId () );
			stmt.append ( " { id: '" ).append ( to.getId () ).append ( "'} ) " );
			
			stmt.append ( "CREATE (from) - [:" );
			stmt.append ( type );
						
			appendParams ( stmt, params );
			stmt.append ( "]" );
			stmt.append ( " -> (to)\n"  );

			this.queueCypher ( session, stmtQueue, stmt, ++ct, "relations" );
		}
		
		// flush the queue		
		this.queueCypher ( session, stmtQueue, null, ct, "relations" );
		
		session.close ();
		driver.close ();
	}


	private void queueCypher ( Session session, StringBuilder stmtQueue, StringBuilder stmt, int counter, String itemType )
	{
		if ( stmt != null )
		{
			if ( stmtQueue.length () != 0 )
				// This is how you separate multiple commands in Cypher
				stmtQueue.append ( "WITH 0 AS foo\n" );

			stmtQueue.append ( stmt );
		}
		
		// null statement is for committing the still-pending statements
		if ( stmt == null || counter % 500 == 0 )
		{
			this.runCypher ( session, stmtQueue );
			log.info ( "{} {} committed", counter, itemType );
			stmtQueue.setLength ( 0 );
		}
	}

	
	/**
	 * @param session
	 * @param stmt
	 */
	private void runCypher ( Session session, StringBuilder stmt )
	{
		String smtStr = stmt.toString ();
		try {
			session.run ( smtStr );
		}
		catch ( ClientException ex ) {
			log.error ( String.format ( "Error \"%s\" while sending Cypher command:\n%s", ex.getMessage (), smtStr ), ex );
		}
	}

	private Map<String, String> getAttrs ( ONDEXEntity oxe )
	{
		Map<String, String> attributes = Optional.ofNullable ( oxe.getAttributes () )
		.map ( attrs ->
		  attrs
		  .stream ()
		  .collect ( Collectors.toMap (
		  	attr -> attr.getOfType ().getId (),
		  	attr -> attr.getValue ().toString ()
		  ))
		)
		.orElse ( Collections.emptyMap () );
		
		return attributes;
	}
	
	private StringBuilder appendParams ( StringBuilder stmt, Map<String, String> params )
	{
		if ( params == null || params.size () == 0 ) return new StringBuilder ();
		
		stmt.append ( " { " );
		String sep = null;
		for ( Entry<String, String> e: params.entrySet () ) {
			if ( sep != null ) stmt.append ( sep );
			stmt.append ( "`" ).append ( e.getKey () ).append ( "`" );
			stmt.append ( ": \"" );
			stmt.append ( StringEscapeUtils.escapeJava ( e.getValue () ) );
			stmt.append ( "\"" );
			sep = ", ";
		}
		stmt.append ( "}" );		
		return stmt;
	}
	
	
	@Override
	public String[] requiresValidators ()
	{
		return new String [ 0 ];
	}

	@Override
	public boolean requiresIndexedGraph() {
		return false;
	}
}
