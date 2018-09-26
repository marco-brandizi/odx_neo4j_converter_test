## `List<String> didYouMean( keyword )`

    * base version returns an empty list, guess it's supposed to do something in the specialisations.
    * TODO: check again for real implementations

## + `displayGraphStats ()`

    * show several gross numbers, which require typical aggregation queries (count classes, count relations, etc)    
    * Cypher:
```
// Concepts count
MATCH (c:Concept) RETURN COUNT ( DISINCT c ) AS count
// Relations count
MATCH ()-[r]-() RETURN COUNT ( DISINCT r ) AS count  
```

    * SPARQL
 
```
// Concepts count
SELECT COUNT ( DISTINCT ?c ) AS ?count {
  ?c a odx:Concept
}
// Relations count
SELECT COUNT ( DISTINCT ?r ) AS ?count {
  ?r a odx:Property
  ?cfrom ?r ?cto.
}
```    

## + `validateOndexKB ()`
    * check that the graph has certain node/relation types
      * should be done against a specific schema/ontology
    * Cypher: 
```
   UNWIND [ 'Chromosome', 'Begin', 'End' ] AS myattr
   MATCH (g:Gene) WHERE NOT myattr IN KEYS(g)
   RETURN COUNT ( DISTINCT g ) AS errors    
```
    * SPARQL:
```
   SELECT COUNT ( distinct ?g) AS ?errors {
     ?g a odx:Gene.
     NOT EXISTS ( ?g ?myAttr ?value ).
     VALUES (?myAttr) { ( 'Chromosome' ),  ( 'Begin' ), ( 'End' ) }
   }
```

## + `indexOndexGraph()`
    * uses LuceneEnv(), typical graph indexing operations (concepts, concept classes, relations, their attributes/fields)
    * Currently, it requires full graph loading, could be done aganist a graph db as input
    * TODO: can this be done against a graph db? Would it support Lucene syntax?
    * Typical support queries:
```
MATCH (c:Concept) RETURN c.*
MATCH ()-[r]-() RETURN r.*
```

```
# For concepts
SELECT DISTINCT ?attrName ?attrValue {
  ?attrProp rdf:subPropertyOf odx:attributeProperty;
            rdfs:label ?attrName.
  ?c a odx:Concept.
  ?c ?attrProp ?attrValue.
}

# For relations
SELECT DISTINCT ?attrName ?attrValue {
  ?attrProp rdf:subPropertyOf odx:attributeProperty;
            rdfs:label ?attrName.
  ?r a odx:Relation.
  ?r ?attrProp ?attrValue.
}
```
## `exportGraph()`
    * does the OXL export, + json export (via plugin)

## + HashMap<ONDEXConcept, Float> searchLucene(String keywords)
    * performs several queries against several concept fields and collect results
    * should be possible to do it against a graph DB, not sure we want it

## SortedMap<ONDEXConcept, Double> getScoredGenesMap( Map<ONDEXConcept, Float> hit2score )
    * uses mapConcept2Genes to return the concept genes associated to the input concepts
    * doesn't seem to be relevant for a graph database.

## + Set<ONDEXConcept> searchQTLs(List<QTL> qtls)
    * search concepts of type QTL and maps them to an internal QTL structure
    * Cypher:
```
MATCH ( g:Gene ) RETURN g.name, g.begin, g.chromosome, g.end
```
```
SELECT DISTINCT ?begin ?end ?chromosome {
  ?g a odx:Gene;
    odx:geneBegin ?begin;
    odx:geneEnd ?end;
    odx:chromosome ?chromosome
}
```

## + `Set<QTL> findQTL(String keyword)`
    * searches gene concepts over ends of relations
    * Searches via index are not affected by the backend
    * Searches over backend are similar to the previous case

## + `Set<ONDEXConcept> searchGenes(List<String> accessions)`
    * fetches genes from the graph, based on accessions
    * TODO: we need to see how to model structured values like accessions, but roughly would be:
```
MATCH (g:Gene{acc:$accession}) RETURN g;
```
```
SELECT DISTINCT ?g ?attr {
   ?attrProp rdf:subPropertyOf odx:attrProp.
   ?g a odx:Gene;
     odx:accession $accession;
     ?attrProp ?attr.
}
```

## + `ONDEXGraph evidencePath(Integer evidenceOndexId)`
    * finds genes starting from the ID of a related concept
    * uses mapConcept2Genes to get initial concepts, then uses the graph traverser got from the
    semantic motif queries to find further concepts

## ++ ONDEXGraph findSemanticMotifs(Set<ONDEXConcept> seed, String keyword)
    1. using seed and graph traverser, finds evidence paths
    1. clones concepts coming from evidence paths into a subgraph
    1. using lucene, searches by keyword and mark resulting genes in the subgraph
    1. some other op TODO: better analysis
    1. returns the subgraph
    * Queries actually happen in traverseGraph() (see below)

## ONDEXGraph getGenes(Integer[] ids, String regex)
    * Uses findSemanticMotifs() with the different seeds. It says regex, but it's actually the keyword for
    this method.
    * See traverseGraph() below
  * void highlightPath(EvidencePathNode path, ONDEXGraphCloner graphCloner)
  * void hidePath(EvidencePathNode path, ONDEXGraphCloner graphCloner)
    change highlighting in a memory graph, no backend involved.

## + int getGeneCount(String chr, int start, int end)
    * stats over gene concepts
    * done using the memory graph, maybe it could be interesting to use the backend

## void populateHashMaps()
    * uses the graph and the traverser

## `Map<ONDEXConcept, List<EvidencePathNode>> traverseGraph(ONDEXGraph aog, Set<ONDEXConcept> concepts, FilterPaths<EvidencePathNode> filter)`
    * associates concepts to instantiated paths
    * launches traversers in parallel
    * collect results as list of evidence nodes

```
MATCH (start:Type{ id: $startId}) - [r1:RType1] -> (n1:NType1) ... (end:NTypeN) RETURN start, end
```

```
SELECT DISTINCT * {
  ?start a Type;
         dc:identifier $id;
         ^odx:from ?r1.
  ?r1 odx:to ?n1.

  ?n1 a NType1 ...

  ?end a NTypeN.
}
```
