package net.sourceforge.ondex.parser.neo4j.plugin;

import static org.neo4j.driver.v1.Values.parameters;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.ondex.args.ArgumentDefinition;
import net.sourceforge.ondex.args.StringArgumentDefinition;
import net.sourceforge.ondex.core.ONDEXEntity;
import net.sourceforge.ondex.export.ONDEXExport;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>11 Oct 2017</dd></dl>
 *
 */
public abstract class AbstractCypherExporter extends ONDEXExport
{
	protected Logger log = LoggerFactory.getLogger ( this.getClass () );
	protected int bufferSize = 100;
	protected String neo4jUrl;
	protected Driver driver;
	protected Session session;

	@Override
	public ArgumentDefinition<?>[] getArgumentDefinitions ()
	{
		return new ArgumentDefinition<?>[] {
			new StringArgumentDefinition ( "neo4jUrl", "Neo4j Connection URL" , true, "bolt://127.0.0.1:7687", false ),
			new StringArgumentDefinition ( "bufferSize", "Neo4j CREATE instructions commited per transaction" , true, "1000", false )
		};
	}


	public AbstractCypherExporter ()
	{
		super ();
	}

	@Override
	public String[] requiresValidators ()
	{
		return new String [ 0 ];
	}

	@Override
	public boolean requiresIndexedGraph ()
	{
		return false;
	}


	protected Map<String, Object> getAttrs ( ONDEXEntity oxe )
	{
		Map<String, Object> attributes = Optional.ofNullable ( oxe.getAttributes () )
		.map ( attrs ->
		  attrs
		  .stream ()
		  .collect ( Collectors.toMap (
		  	attr -> attr.getOfType ().getId (),
		  	attr -> (Object) attr.getValue ().toString ()
		  ))
		)
		.orElse ( Collections.emptyMap () );
		
		return attributes;
	}	
	
	protected boolean queueCypher ( Session session, String cypher, int ct, Object... keyVals )
	{
		return this.queueCypher ( false, session, cypher, ct, keyVals );
	}

	protected boolean queueCypher ( boolean doForce, Session session, String cypher, int ct, Object... keyVals )
	{
		if ( ! ( doForce || ct % this.bufferSize == 0 ) ) return false;
		
		session.run ( cypher, parameters ( keyVals ) );
		log.info ( "Neo4j, {} items committed", String.valueOf ( ct ) );
		return true;
	}


	@Override
	public void start () throws Exception
	{
		this.neo4jUrl = (String) this.getArguments ().getUniqueValue ( "neo4jUrl" );
		this.bufferSize = Integer.valueOf ( (String) this.getArguments ().getUniqueValue ( "bufferSize" ) );
		
		this.driver = GraphDatabase.driver( neo4jUrl, AuthTokens.basic( "neo4j", "graph" ) );
		this.session = this.driver.session();
		
		this.run ();

		session.close ();
		driver.close ();

	}

	protected abstract void run ();
}