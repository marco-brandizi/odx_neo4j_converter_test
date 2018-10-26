package net.sourceforge.ondex.cypher.test;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.Values;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>26 Oct 2018</dd></dl>
 *
 */
public class ParemeterizedCypherTest
{
	@Test
	public void testParameterizedCypher ()
	{
		/*
		 * Creates relations between the pairs
		 * 
		 *   1. Gene { "iri": "http://www.ondex.org/bioknet/resources/gene_at3g13540_locus_2092820" }
		 *   		Publication { iri: "http://www.ondex.org/bioknet/resources/publication_27923613_pmid_27923613" }
		 *   
		 *   2. Gene { iri: "http://www.ondex.org/bioknet/resources/gene_at5g57050_locus_2164610" }
		 *      Publication { iri: "http://www.ondex.org/bioknet/resources/publication_18711121" }
		 *      
		 * The relation is :TEXT_MINING_ANNOTATIONS and has the attribute 'score', with values 1. 0.9 2. 0.8
		 * 
		 * This method works by creating one cypher query per pair (so, 2 queried in total)
		 * 
		 */
		
		// Define the list of IRI pairs and relation scores
		// This is what you have computed from elsewhere and can have your own structure
	  Object [][] pairs = new Object [][] 
	  {
	  	// Pair 1.
	  	new Object [] { 	  			
	  		"http://www.ondex.org/bioknet/resources/gene_at3g13540_locus_2092820",  // gene
	  		"http://www.ondex.org/bioknet/resources/publication_27923613_pmid_27923613", // pub
	  		0.9 // score
	  	},
	  	
	  	// Pair 2.
	  	new Object [] { 	  			
	  		"http://www.ondex.org/bioknet/resources/gene_at5g57050_locus_2164610",  // gene
	  		"http://www.ondex.org/bioknet/resources/publication_18711121", // pub
	  		0.8 // score
	  	}
	  };
		
	  // Let's process this input.
	  
  	// The try(...){...} block ensures auto-closing
	  // (http://tutorials.jenkov.com/java-exception-handling/try-with-resources.html)
	  //
	  try ( 
	  	Driver neoDriver = GraphDatabase.driver( "bolt://127.0.0.1:7687", AuthTokens.basic( "neo4j", "test" ) );
	  	Session neoSession = neoDriver.session();

	  	// Wrap all or multiple instructions into a single transaction, typically you want to fit
	  	// 2000-3000 Cypher commands in a transaction
	  	// Transaction is in the try() block so that it is closed too at the end
			Transaction neoTransaction = neoSession.beginTransaction ();
	  )
	  {
			for ( Object[] pair: pairs )
			{
				String geneIri = (String) pair [ 0 ];
				String pubIri = (String) pair [ 1 ];
				double score = (Double) pair [ 2 ];
				
				// Matches the two IRIs and links the corresponding nodes with a new relation (which has a score)
				String cypherCreate = "MATCH (gene:Gene{ iri:{geneIri} }), (pub:Publication{ iri:{pubIri} })\n" + 
						"CREATE (gene)-[:TEXT_MINING{score: {relScore} }]->(pub)";
				
				// In cypherCreate, {geneIri}, {pubIri}, {relScore} are replaced with the values specified here 
				// and then the query is executed (at commit time)
				neoTransaction.run ( 
					cypherCreate, 
					// There are several ways to instantiate a query with pairs of placeholder/value (use the IDE to inspect 
					// Values for see them). This a simple one where you explicitly list such pairs
					Values.parameters ( "geneIri", geneIri, "pubIri", pubIri, "relScore", score )
				);				
			} 
			
			neoTransaction.success ();
			// End of loop, commit!
	  }
	  // End of try block, every close() method is invoked here, even in case of exceptions
	
	} // end of test method.

}
