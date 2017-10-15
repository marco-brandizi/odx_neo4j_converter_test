package net.sourceforge.ondex.parser.neo4j.plugin;

import static org.neo4j.driver.v1.Values.parameters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.sourceforge.ondex.core.ConceptClass;
import net.sourceforge.ondex.core.ConceptName;
import net.sourceforge.ondex.core.ONDEXConcept;
import net.sourceforge.ondex.core.ONDEXRelation;
import net.sourceforge.ondex.core.RelationType;


/**
 * A rough Neo4j test exporter, using Cypher and creating a flat model, where there is only one type of
 * node and one type of relation.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>4 Oct 2017</dd></dl>
 *
 */
public class CypherExporterFlat extends AbstractCypherExporter
{	
	@Override
	public String getId ()
	{
		return "cypherExporterFlat";
	}

	@Override
	public String getName ()
	{
		return "Neo4J Flat Model Exporter, based on Cypher";
	}

	@Override
	public String getVersion ()
	{
		return "1.0";
	}


	@Override
	protected void run ()
	{ 
		int ct = 0;
		
		// Concept Classes
		//
		
		for ( ConceptClass cc: this.graph.getMetaData ().getConceptClasses () )
		{
			if ( Optional.ofNullable ( this.graph.getConceptsOfConceptClass ( cc ) ).map ( Set::size ).orElse ( 0 ) == 0 ) 
				continue;
			
			log.info ( "Creating Concept Class \"{}\"", cc.getId () );
			
			session.run ( 
				"CREATE ( :ConceptClass { name: $name, fullName: $fullName, description: $description } )", 
			  parameters ( "name", cc.getId (), "fullName", cc.getFullname (), "description", cc.getDescription () )
			);
		}
		
		session.run ( "CREATE INDEX ON :ConceptClass(name)" );
		
		
		// Concepts
		//
		
		List<Map<String, Object>> conceptsQueue = new ArrayList<> ();

		String cypherCreateConcepts = "UNWIND {concepts} AS concept\n" +
		  "MATCH (cc:ConceptClass{ name: concept.ccName })\n" +
			"CREATE (c:Concept)-[:conceptClass]->(cc)\n" +
			"SET c = concept";
		
		for ( ONDEXConcept concept: this.graph.getConcepts () )
		{			
			String id = String.valueOf ( concept.getId () );
			String name = Optional.ofNullable ( concept.getConceptName () ).map ( ConceptName::getName ).orElse ( id );

			ConceptClass cc = concept.getOfType ();

			Map<String, Object> cprops = getAttrs ( concept );
			cprops.put ( "id", id );
			cprops.put ( "name", name );
			cprops.put ( "ccName", cc.getId () );
						
			conceptsQueue.add ( cprops );
			
			if ( this.queueCypher ( session, cypherCreateConcepts, ++ct, "concepts", conceptsQueue ) )
				conceptsQueue.clear ();
		}

		
		// flush
		if ( !conceptsQueue.isEmpty () ) {
			this.queueCypher ( true, session, cypherCreateConcepts, ct += conceptsQueue.size (), "concepts", conceptsQueue );
			conceptsQueue = null;
		}

		session.run ( "CREATE INDEX ON :Concept(id)" );
		session.run ( "CREATE INDEX ON :Concept(name)" );
		session.run ( "CREATE INDEX ON :Concept(ccName)" );

		
		// Relation Types
		//
		
		for ( RelationType rt: this.graph.getMetaData ().getRelationTypes () )
		{
			if ( Optional.ofNullable ( this.graph.getRelationsOfRelationType ( rt ) ).map ( Set::size ).orElse ( 0 ) == 0 ) 
				continue;
			
			String rtId = rt.getId ();
			log.info ( "Creating Relation Type \"{}\"", rtId );
			
			session.run ( 
				"CREATE ( :RelationType { name: $name, fullName: $fullName, description: $description } )", 
			  parameters ( "name", rtId, "fullName", rt.getFullname (), "description", rt.getDescription () )
			);
		}
		
		session.run ( "CREATE INDEX ON :RelationType(name)" );
		
		
		// Relation Types
		//
		
		List<Map<String, Object>> relsQueue = new ArrayList<> ();

		String cypherCreateRels = "UNWIND {relations} AS rel\n" +
		  "MATCH (from:Concept{ id: rel.fromId }), (to:Concept{ id: rel.toId }), (rt:RelationType{name: rel.attrs.name})\n" +
			"CREATE (r:Relation)-[:relationType]->(rt), (from)<-[:from]-(r)-[:to]->(to)\n" +
			"SET r = rel.attrs";
			
		for ( ONDEXRelation rel: this.graph.getRelations () )
		{
			ONDEXConcept from = rel.getFromConcept ();
			ONDEXConcept to = rel.getToConcept ();
			
			Map<String, Object> params = new HashMap<> ();
			String relName = rel.getOfType ().getId ();
			params.put ( "fromId", String.valueOf ( from.getId () ) );
			params.put ( "toId", String.valueOf ( to.getId () ) );

			Map<String, Object> attrs = this.getAttrs ( rel );
			attrs.put ( "name", relName  );
			
			params.put ( "attrs", attrs );
			
			relsQueue.add ( params );
			
			if ( this.queueCypher ( session, cypherCreateRels, ++ct, "relations", relsQueue ) )
				relsQueue.clear ();
		}

		// flush
		if ( !relsQueue.isEmpty () )
			this.queueCypher ( true, session, cypherCreateRels, ct += relsQueue.size (), "relations", relsQueue );
		
		session.run ( "CREATE INDEX ON :Relation(id)" );
		session.run ( "CREATE INDEX ON :Relation(name)" );
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
