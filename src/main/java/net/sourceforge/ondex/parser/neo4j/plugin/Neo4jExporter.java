//package net.sourceforge.ondex.parser.neo4j.plugin;
//
//import java.io.File;
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Optional;
//import java.util.Set;
//import java.util.stream.Collectors;
//
//import org.neo4j.graphdb.Label;
//import org.neo4j.graphdb.RelationshipType;
//import org.neo4j.unsafe.batchinsert.BatchInserter;
//import org.neo4j.unsafe.batchinsert.BatchInserters;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import net.sourceforge.ondex.args.ArgumentDefinition;
//import net.sourceforge.ondex.args.StringArgumentDefinition;
//import net.sourceforge.ondex.core.ConceptClass;
//import net.sourceforge.ondex.core.ConceptName;
//import net.sourceforge.ondex.core.ONDEXConcept;
//import net.sourceforge.ondex.core.ONDEXEntity;
//import net.sourceforge.ondex.core.ONDEXRelation;
//import net.sourceforge.ondex.export.ONDEXExport;
//
//
///**
// * A rough Neo4j test exporter, using the Inserter tool.
// *
// * @author brandizi
// * <dl><dt>Date:</dt><dd>4 Oct 2017</dd></dl>
// *
// */
//public class Neo4jExporter extends ONDEXExport
//{
//	private Logger log = LoggerFactory.getLogger ( this.getClass () );
//	
//	@Override
//	public String getId ()
//	{
//		return "neo4jExporter";
//	}
//
//	@Override
//	public String getName ()
//	{
//		return "Neo4J Exporter, based on Inserter";
//	}
//
//	@Override
//	public String getVersion ()
//	{
//		return "1.0";
//	}
//
//	@Override
//	public ArgumentDefinition<?>[] getArgumentDefinitions ()
//	{
//		return new ArgumentDefinition<?>[] {
//			new StringArgumentDefinition ( "file", "Neo4j Graph Path" , true, "", false ) 
//		};
//	}
//
//	@Override
//	public void start () throws Exception
//	{ 
//		String file = (String) this.getArguments ().getUniqueValue ( "file" );
//
//		BatchInserter inserter = null;
//		
//		try
//		{
//			inserter = BatchInserters.inserter ( new File ( file ) );
//		
//			// Prepare the indexes
//			for ( ConceptClass cc: this.graph.getMetaData ().getConceptClasses () )
//			{
//				if ( Optional.ofNullable ( this.graph.getConceptsOfConceptClass ( cc ) ).map ( Set::size ).orElse ( 0 ) == 0 )
//					continue;
//				
//				String cctype = cc.getId ();
//				log.info ( "Indexing \"{}\"", cctype );
//				
//				try {					
//			    Label cclabel = Label.label( cctype );
//			    inserter.createDeferredSchemaIndex ( cclabel ).on( "id" ).create();
//				}
//				catch ( RuntimeException ex ) {
//					log.error ( 
//						String.format ( "error while indexing '%s': %s", cctype, ex.getMessage () ), 
//						ex
//					);
//				}
//			}
//					
//			int ct = 0;
//			
//			for ( ONDEXConcept concept: this.graph.getConcepts () )
//			{		
//				int id = concept.getId ();
//				String idStr = String.valueOf ( id );
//				String type = concept.getOfType ().getId ();
//				String name = Optional.ofNullable ( concept.getConceptName () ).map ( ConceptName::getName ).orElse ( idStr );
//				
//				Map<String, Object> attributes = getAttrs ( concept );
//	
//				Map<String, Object> params = new HashMap<> ();
//				params.put ( "id", idStr );
//				params.put ( "name", name );
//				params.putAll ( attributes );
//
//				try {
//			    Label cclabel = Label.label( type );
//					inserter.createNode ( id, params, cclabel );
//				}
//				catch ( RuntimeException ex ) {
//					log.error ( 
//						String.format ( "error while indexing '%s:%s': %s", idStr, type, ex.getMessage () ), 
//						ex
//					);
//				}
//				
//				if ( ++ct % 1000 == 0 ) log.info ( "{} concepts saved", ct );
//			}
//			
//			ct = 0;
//			for ( ONDEXRelation rel: this.graph.getRelations () )
//			{
//				String type = rel.getOfType ().getId ();
//				ONDEXConcept from = rel.getFromConcept ();
//				ONDEXConcept to = rel.getToConcept ();
//				
//				Map<String, Object> params = getAttrs ( rel );
//
//				try {
//					RelationshipType grtype = RelationshipType.withName ( type );
//					inserter.createRelationship ( from.getId (), to.getId (), grtype, params );
//				}
//				catch ( RuntimeException ex ) {
//					log.error ( 
//						String.format ( "error while indexing '%s'-'%s'->'%s': %s", 
//							from.getConceptName (), type, to.getConceptName (), ex.getMessage () 
//						), 
//						ex
//					);
//				}
//				
//
//				if ( ++ct % 1000 == 0 ) log.info ( "{} relations saved", ct );				
//			}
//		}
//		finally {
//			if ( inserter != null ) {
//				inserter.shutdown ();
//			}
//		}
//	}
//
//
//
//	private Map<String, Object> getAttrs ( ONDEXEntity oxe )
//	{
//		Map<String, Object> attributes = Optional.ofNullable ( oxe.getAttributes () )
//		.map ( attrs ->
//		  attrs
//		  .stream ()
//		  .collect ( Collectors.toMap (
//		  	attr -> attr.getOfType ().getId (),
//		  	attr -> (Object) attr.getValue ().toString ()
//		  ))
//		)
//		.orElse ( Collections.emptyMap () );
//		
//		return attributes;
//	}
//	
//	
//	@Override
//	public String[] requiresValidators ()
//	{
//		return new String [ 0 ];
//	}
//
//	@Override
//	public boolean requiresIndexedGraph() {
//		return false;
//	}
//}
