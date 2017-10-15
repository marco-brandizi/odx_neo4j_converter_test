package net.sourceforge.ondex.parser.neo4j.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.neo4j.driver.v1.Session;

import net.sourceforge.ondex.core.ConceptClass;
import net.sourceforge.ondex.core.ConceptName;
import net.sourceforge.ondex.core.ONDEXConcept;
import net.sourceforge.ondex.core.ONDEXRelation;
import net.sourceforge.ondex.core.RelationType;


/**
 * A rough Neo4j test exporter, using Cypher.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>4 Oct 2017</dd></dl>
 *
 */
public class CypherExporter extends AbstractCypherExporter
{
	private static class RelationQueue
	{
		public RelationQueue ( String fromLabel, String toLabel ) {
			this.fromLabel = fromLabel;
			this.toLabel = toLabel;
		}
		public String fromLabel;
		public String toLabel;
		public List<Map<String, Object>> relsAttrs = new ArrayList<> ();
	}
	
	
	@Override
	public String getId ()
	{
		return "cypherExporter";
	}

	@Override
	public String getName ()
	{
		return "Neo4J Exporter, based on Cypher";
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
		List<Map<String, Object>> nodesQueue = new ArrayList<> ();
		
		for ( ConceptClass cc: this.graph.getMetaData ().getConceptClasses () )
		{
			nodesQueue.clear ();
			String label = cc.getId ();
			
			String cypherCreateNodes = "UNWIND {nodes} AS node\n" + 
				"CREATE (n:`%s`)\n" +
				"SET n = node";
			
			cypherCreateNodes = String.format ( cypherCreateNodes, label );

			for ( ONDEXConcept concept: this.graph.getConceptsOfConceptClass ( cc ) )
			{			
				String id = String.valueOf ( concept.getId () );
				String name = Optional.ofNullable ( concept.getConceptName () ).map ( ConceptName::getName ).orElse ( id );
				
				Map<String, Object> props = getAttrs ( concept );

				props.put ( "id", id );
				props.put ( "name", name );
	
				nodesQueue.add ( props );
				
				if ( this.queueCypher ( session, cypherCreateNodes, ++ct, "nodes", nodesQueue ) )
					nodesQueue.clear ();
			}

			// flush
			if ( !nodesQueue.isEmpty () )
				this.queueCypher ( true, session, cypherCreateNodes, ct += nodesQueue.size (), "nodes", nodesQueue );
		
		} // for CC

		
		for ( ConceptClass cc: this.graph.getMetaData ().getConceptClasses () )
		{
			if ( Optional.ofNullable ( this.graph.getConceptsOfConceptClass ( cc ) ).map ( Set::size ).orElse ( 0 ) == 0 ) 
				continue;
			
			String ctype = cc.getId ();
			log.info ( "Indexing \"{}\"", ctype );
			session.run ( String.format ( "CREATE INDEX ON :`%s`(id)", ctype ) );
		}

		
		Map<String, RelationQueue> relationsQueue = new HashMap<> ();		
		for ( RelationType relType: this.graph.getMetaData ().getRelationTypes () )
		{
			relationsQueue.clear ();
			
			for ( ONDEXRelation rel: this.graph.getRelationsOfRelationType ( relType ) )
			{
				ONDEXConcept from = rel.getFromConcept ();
				String fromLabel = from.getOfType ().getId ();
				ONDEXConcept to = rel.getToConcept ();
				String toLabel = to.getOfType ().getId ();
	
				String queueKey = fromLabel+toLabel;
				RelationQueue queue = relationsQueue.get ( queueKey );
				if ( queue == null ) relationsQueue.put ( queueKey, queue = new RelationQueue ( fromLabel, toLabel ) );
				
				Map<String, Object> params = new HashMap<> (); 
				params.put ( "fromId", String.valueOf ( from.getId () ) );
				params.put ( "toId", String.valueOf ( to.getId () ) );
				params.put ( "attributes", this.getAttrs ( rel ) );
				
				queue.relsAttrs.add ( params );

				if ( queue.relsAttrs.size () == this.bufferSize )
					ct = this.commitRelations ( session, queue, ct, relType );
			}

			// flush the queue
			ct = this.commitRelations ( session, ct, relationsQueue, relType );
		
		} // relType 
	}

	private int commitRelations ( Session session, int ct, Map<String, RelationQueue> queues, RelationType relType )
	{
		for ( RelationQueue queue: queues.values () )
			ct = commitRelations ( session, queue, ct, relType );
		return ct;
	}
	
	private int commitRelations ( Session session, RelationQueue queue, int ct, RelationType relType )
	{
		if ( queue.relsAttrs.size () == 0 ) return ct;
		
		String cypherCreateRel = 
			"UNWIND {relations} AS rel\n" +
			"MATCH ( from:`%s`{ id: rel.fromId } ), ( to:`%s`{ id: rel.toId } )\n" +
			"CREATE (from)-[r:`%s`]->(to)\n" +
			"SET r = rel.attributes";
		
		cypherCreateRel = String.format ( cypherCreateRel, queue.fromLabel, queue.toLabel, relType.getId () );
		this.queueCypher ( true, session, cypherCreateRel, ct += queue.relsAttrs.size (), "relations", queue.relsAttrs );
		queue.relsAttrs.clear ();
		return ct;
	}
}
