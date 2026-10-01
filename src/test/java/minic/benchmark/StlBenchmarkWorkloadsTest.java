package minic.benchmark;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StlBenchmarkWorkloadsTest {
 @Test void catalogueIsCompleteAndDistinct(){assertEquals(9,StlBenchmarkWorkloads.workloads().size());assertEquals(9,StlBenchmarkWorkloads.workloads().stream().map(StlBenchmarkWorkloads.Workload::id).distinct().count());}
 @Test void inputIsBoundedAndReproducible(){var w=StlBenchmarkWorkloads.workloads().getFirst();var a=StlBenchmarkWorkloads.input(w,3,2,17);assertEquals("3 2 17\n",a.stdin());assertEquals(a,StlBenchmarkWorkloads.input(w,3,2,17));assertEquals(6,a.operationCount());assertEquals(2,a.expectedStdout().lines().count());assertNotEquals(a.expectedStdout(),StlBenchmarkWorkloads.input(w,3,2,18).expectedStdout());assertThrows(IllegalArgumentException.class,()->StlBenchmarkWorkloads.input(w,0,1,1));assertThrows(IllegalArgumentException.class,()->StlBenchmarkWorkloads.input(w,w.maximumSize()+1,1,1));assertThrows(IllegalArgumentException.class,()->StlBenchmarkWorkloads.input(w,3,0,1));assertThrows(IllegalArgumentException.class,()->StlBenchmarkWorkloads.input(w,3,1,-1));}
 @Test void hashPreservesOrderAndLength(){assertNotEquals(StlBenchmarkWorkloads.sequenceHash(1,2),StlBenchmarkWorkloads.sequenceHash(2,1));assertNotEquals(StlBenchmarkWorkloads.sequenceHash(1),StlBenchmarkWorkloads.sequenceHash(1,0));assertEquals(-4721365089366217608L,StlBenchmarkWorkloads.sequenceHash(1,2,3));}
 @Test void fixedReferenceVectorsCoverEveryOperationSequence(){
  assertEquals("round=0 hash=17035318119664837026\nround=1 hash=7509545777851846361\n",StlBenchmarkWorkloads.input(StlBenchmarkWorkloads.workloads().stream().filter(w->w.id().equals("vector-sort")).findFirst().orElseThrow(),17,2,23).expectedStdout());
  assertEquals("round=0 hash=3993022414191902330\nround=1 hash=5348125961767757215\n",StlBenchmarkWorkloads.input(StlBenchmarkWorkloads.workloads().stream().filter(w->w.id().equals("binary-search")).findFirst().orElseThrow(),17,2,23).expectedStdout());
  assertEquals("round=0 hash=7173766903994084602\nround=1 hash=554577295051601633\n",StlBenchmarkWorkloads.input(StlBenchmarkWorkloads.workloads().stream().filter(w->w.id().equals("priority-queue")).findFirst().orElseThrow(),17,2,23).expectedStdout());
  assertEquals("round=0 hash=416899072613547255\nround=1 hash=2321814091829531595\n",StlBenchmarkWorkloads.input(StlBenchmarkWorkloads.workloads().stream().filter(w->w.id().equals("ordered-map")).findFirst().orElseThrow(),17,2,23).expectedStdout());
  assertEquals("round=0 hash=14277903358157951927\nround=1 hash=15123192404036787557\n",StlBenchmarkWorkloads.input(StlBenchmarkWorkloads.workloads().stream().filter(w->w.id().equals("deque")).findFirst().orElseThrow(),17,2,23).expectedStdout());
  assertEquals("round=0 hash=12013568011882749661\nround=1 hash=14630856975616236701\n",StlBenchmarkWorkloads.input(StlBenchmarkWorkloads.workloads().stream().filter(w->w.id().equals("string")).findFirst().orElseThrow(),17,2,23).expectedStdout());
  assertEquals("round=0 hash=17217167919137291522\nround=1 hash=1290464303928802520\n",StlBenchmarkWorkloads.input(StlBenchmarkWorkloads.workloads().stream().filter(w->w.id().equals("bitset")).findFirst().orElseThrow(),17,2,23).expectedStdout());
 }
 @Test void addedWorkloadsMatchIndependentFixedVectors(){
  // Confirmed with the host STL and a separate set-based Python count oracle.
  var shortStrings=StlBenchmarkWorkloads.workloads().stream().filter(w->w.id().equals("string-short")).findFirst().orElseThrow();
  var repeatedCount=StlBenchmarkWorkloads.workloads().stream().filter(w->w.id().equals("bitset-count")).findFirst().orElseThrow();
  assertEquals("round=0 hash=14484259935218049567\nround=1 hash=4034900178130450295\n",StlBenchmarkWorkloads.input(shortStrings,17,2,23).expectedStdout());
  assertEquals("round=0 hash=11160244451176241739\nround=1 hash=193929769481890375\n",StlBenchmarkWorkloads.input(repeatedCount,17,2,23).expectedStdout());
 }
 @Test void shortStringAndRepeatedCountCoverBoundariesAndChangingInputs(){
  for(String id:java.util.List.of("string-short","bitset-count")){
   var w=StlBenchmarkWorkloads.workloads().stream().filter(item->item.id().equals(id)).findFirst().orElseThrow();
   String previous=null;
   for(int size:java.util.List.of(1,15,16,17,33,65)){
    var input=StlBenchmarkWorkloads.input(w,size,2,23);
    assertEquals(size*2L,input.operationCount());assertEquals(2,input.expectedStdout().lines().count());
    assertNotEquals(previous,input.expectedStdout());assertNotEquals(input.expectedStdout(),StlBenchmarkWorkloads.input(w,size,2,24).expectedStdout());
    previous=input.expectedStdout();
   }
  }
 }
 @Test void unknownWorkloadCannotSilentlyUseAnotherOracle(){var bad=new StlBenchmarkWorkloads.Workload("unknown",java.nio.file.Path.of("none"),1,3);assertThrows(IllegalArgumentException.class,()->StlBenchmarkWorkloads.input(bad,1,1,0));}
}
