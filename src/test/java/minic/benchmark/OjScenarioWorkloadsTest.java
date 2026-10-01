package minic.benchmark;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OjScenarioWorkloadsTest {
 private static OjScenarioWorkloads.Scenario scenario(String id){return OjScenarioWorkloads.scenarios().stream().filter(s->s.id().equals(id)).findFirst().orElseThrow();}
 private static String expected(long... values){return "round=0 observations="+values.length+" hash="+Long.toUnsignedString(OjScenarioWorkloads.sequenceHash(values))+"\n";}
 @Test void catalogueHasMeaningfulFactorialConfigurations(){
  assertEquals(8,OjScenarioWorkloads.scenarios().size());var matrix=OjScenarioWorkloads.matrix();assertEquals(96,matrix.size());assertEquals(96,matrix.stream().map(OjScenarioWorkloads.Configuration::id).distinct().count());
  for(var s:OjScenarioWorkloads.scenarios()){assertEquals(3,new HashSet<>(s.shapes()).size());assertTrue(s.largeSize()>s.smallSize());assertFalse(s.operationDefinition().isBlank());assertFalse(s.sizeUnit().isBlank());assertEquals(12,matrix.stream().filter(c->c.scenario().equals(s)).count());}
 }
 @Test void allNinetySixInputsHaveReproducibleCompleteOracles(){
  for(var c:OjScenarioWorkloads.matrix()){var a=OjScenarioWorkloads.input(c,1);assertEquals(a,OjScenarioWorkloads.input(c,1),c.id());assertEquals(a.expectedStdout(),OjScenarioWorkloads.oracle(c.scenario(),a.stdin()),c.id());assertEquals(1,a.expectedStdout().lines().count());assertTrue(a.logicalInputItems()>0);assertEquals(1,a.rounds());}
 }
 @Test void repetitionsReadDifferentRealDataAndCannotHoistOneCase(){
  for(var s:OjScenarioWorkloads.scenarios())for(String shape:s.shapes()){
   var c=new OjScenarioWorkloads.Configuration(s,shape,8,1729);var a=OjScenarioWorkloads.input(c,2);assertEquals(2,a.expectedStdout().lines().count());
   assertNotEquals(a.stdin(),OjScenarioWorkloads.input(new OjScenarioWorkloads.Configuration(s,shape,8,104729),2).stdin());
   assertNotEquals(OjScenarioWorkloads.input(c,1).stdin(),a.stdin());
  }
 }
 @Test void independentHandSolvedCasesCoverAllEightAlgorithms(){
  assertEquals(expected(0,2,5,-1),OjScenarioWorkloads.oracle(scenario("dijkstra"),"1\n4 4\n0 1 2\n1 2 3\n0 2 10\n2 1 1\n"));
  assertEquals(expected(5,5,5,4),OjScenarioWorkloads.oracle(scenario("sliding-window-max"),"1\n6 3\n2 1 5 3 4 0\n"));
  assertEquals(expected(3,-2,4,9,2,0,2,1,0),OjScenarioWorkloads.oracle(scenario("coordinate-compression"),"1\n5\n9 -2 9 4 -2\n"));
  assertEquals(expected(0,1,1,0,2,1,3,0,2,4),OjScenarioWorkloads.oracle(scenario("interval-scheduling"),"1\n4 1\n0 2\n1 3\n2 4\n3 5\n"));
  assertEquals(expected(5,2,3,4,5,0,3,3,1,3,3),OjScenarioWorkloads.oracle(scenario("sparse-accumulator"),"1\n4\n5 2\n3 4\n5 -2\n3 -1\n"));
  assertEquals(expected(3,1,97,1,2,97,97,1,1,98,2),OjScenarioWorkloads.oracle(scenario("word-frequency"),"1\n4\nb a b aa\n"));
  long[] reachable=new long[4097];for(int i:new int[]{0,2,3,5,7,8,10})reachable[i]=1;
  assertEquals(expected(reachable),OjScenarioWorkloads.oracle(scenario("bitset-knapsack"),"1\n3\n2 3 5\n"));
  assertEquals(expected(0,1,2,1,-1,3,-1,5,4),OjScenarioWorkloads.oracle(scenario("grid-bfs"),"1\n3 0 0\n...\n.#.\n#..\n"));
 }
 @Test void shapesReallyExerciseSsoRepetitionAndSharedPrefixes(){
  var s=scenario("word-frequency");
  for(String shape:s.shapes()){
   String[] words=OjScenarioWorkloads.input(new OjScenarioWorkloads.Configuration(s,shape,128,1729),1).stdin().strip().split("\\s+");
   var tokens=Arrays.asList(words).subList(2,words.length);assertEquals(128,tokens.size());
   if(shape.equals("hot-short")){assertTrue(new HashSet<>(tokens).size()<=32);assertTrue(tokens.stream().allMatch(w->w.length()<15));}
   if(shape.equals("unique-sso-boundary")){assertEquals(128,new HashSet<>(tokens).size());assertEquals(64,tokens.stream().filter(w->w.length()==15).count());assertEquals(64,tokens.stream().filter(w->w.length()==16).count());}
   if(shape.equals("shared-prefix")){assertTrue(new HashSet<>(tokens).size()<=32);assertTrue(tokens.stream().allMatch(w->w.length()==44&&w.startsWith("sharedprefixsharedprefixsharedprefix")));}
  }
 }
 @Test void knapsackShapesCoverShiftEdgesWithoutLosingTheirDistributions(){
  var s=scenario("bitset-knapsack");var shifts=new HashSet<Integer>();
  for(String shape:s.shapes()){
   String[] tokens=OjScenarioWorkloads.input(new OjScenarioWorkloads.Configuration(s,shape,128,1729),1).stdin().strip().split("\\s+");
   int[] weights=Arrays.stream(tokens).skip(2).mapToInt(Integer::parseInt).toArray();assertEquals(128,weights.length);for(int w:weights)shifts.add(w);
   if(shape.equals("dense-small"))assertTrue(Arrays.stream(weights).filter(w->w<=16).count()>115);
   if(shape.equals("sparse-large"))assertTrue(Arrays.stream(weights).filter(w->w>=2048).count()>115);
   if(shape.equals("common-divisor"))assertTrue(Arrays.stream(weights).allMatch(w->w%64==0));
  }
  assertTrue(shifts.containsAll(Set.of(0,1,63,64,65,127,128,4096,4097,8192)));
  long[] expected=new long[4097];expected[0]=expected[4096]=1;
  assertEquals(expected(expected),OjScenarioWorkloads.oracle(s,"1\n4\n0 4096 4097 8192\n"));
 }
 @Test void graphWeightsRetainLongDistancesAndDisconnectedAnswers(){
  assertEquals(expected(0,1000000000L,2000000000L,3000000000L,-1),OjScenarioWorkloads.oracle(scenario("dijkstra"),"1\n5 3\n0 1 1000000000\n1 2 1000000000\n2 3 1000000000\n"));
  var s=scenario("dijkstra");String[] tokens=OjScenarioWorkloads.input(new OjScenarioWorkloads.Configuration(s,"layered-disconnected",256,1729),1).stdin().strip().split("\\s+");
  int m=Integer.parseInt(tokens[2]);for(int i=0;i<m;i++){int u=Integer.parseInt(tokens[3+i*3]),v=Integer.parseInt(tokens[4+i*3]);assertEquals(u<192,v<192);}
 }
 @Test void hashIncludesOrderLengthAndSignedValues(){assertEquals(-4721365089366217608L,OjScenarioWorkloads.sequenceHash(1,2,3));assertNotEquals(OjScenarioWorkloads.sequenceHash(1,2),OjScenarioWorkloads.sequenceHash(2,1));assertNotEquals(OjScenarioWorkloads.sequenceHash(1),OjScenarioWorkloads.sequenceHash(1,0));assertNotEquals(OjScenarioWorkloads.sequenceHash(-1),OjScenarioWorkloads.sequenceHash(1));}
 @Test void invalidGenerationAndMalformedOracleInputsAreRejected(){var s=scenario("dijkstra");
  assertThrows(IllegalArgumentException.class,()->OjScenarioWorkloads.input(new OjScenarioWorkloads.Configuration(s,"unknown",8,1),1));
  assertThrows(IllegalArgumentException.class,()->OjScenarioWorkloads.input(new OjScenarioWorkloads.Configuration(s,s.shapes().getFirst(),0,1),1));
  assertThrows(IllegalArgumentException.class,()->OjScenarioWorkloads.input(new OjScenarioWorkloads.Configuration(s,s.shapes().getFirst(),8,1),0));
  assertThrows(IllegalArgumentException.class,()->OjScenarioWorkloads.oracle(s,"1\n3 1\n0 5 2\n"));
  assertThrows(IllegalArgumentException.class,()->OjScenarioWorkloads.oracle(s,"1\n3 0\n trailing"));
  assertThrows(IllegalArgumentException.class,()->OjScenarioWorkloads.oracle(s,"1\n3 1\n0 1 -2\n"));
 }
}
