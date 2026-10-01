package minic.cpp;

import minic.cpp.support.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Public-operation count bounds, independent of timings and exact STL counts. */
@Tag("stl-contract") @Timeout(600)
final class CppLibraryComplexityTest {
 @TempDir Path temporary;
 private String sample(String kind,int n) {
  int count=kind.equals("sort")?6:kind.equals("vector")?3:5;
  StringBuilder out=new StringBuilder();
  for(int c=0;c<count;c++) {
   out.append(kind).append(" case=").append(c).append(" n=").append(n);
   if(kind.equals("sort"))out.append(" comparisons=").append(n*4L);
   else if(kind.equals("vector"))out.append(" value=").append(n).append(" copies=0 moves=0 destroyed=").append(n).append(" live=0 allocations=1 frees=1 bytes=16 freed_bytes=16").append(c==1?" extra_alloc=0 extra_copy=0 extra_move=0 stable=1":"");
   else out.append(" comparisons=").append(n).append(" copy_comparisons=0 copies=").append(n).append(" copy_allocations=").append(n).append(" allocations=").append(2L*n).append(" frees=").append(2L*n).append(" bytes=16 freed_bytes=16 value=").append(n).append(" all_copies=").append(n).append(" moves=0 destroyed=").append(2L*n).append(" live=0");
   out.append(" checksum=").append(CppComplexityContracts.checksum(kind,c,n)).append('\n');
  }
  return out.toString();
 }
 @Test void acceptsPortableLooseLinearAndNLogNCounts() {
  assertEquals(6,CppComplexityContracts.validate("sort",4096,sample("sort",4096)).size());
  assertEquals(3,CppComplexityContracts.validate("vector",4096,sample("vector",4096)).size());
  assertEquals(5,CppComplexityContracts.validate("tree",4096,sample("tree",4096)).size());
 }
 @Test void rejectsCorrectOutputWithQuadraticComparisonOrRelocationCounts() {
  int n=4096;
  var sort=sample("sort",n).replace("comparisons=16384","comparisons=8386560");
  assertTrue(assertThrows(IllegalStateException.class,()->CppComplexityContracts.validate("sort",n,sort)).getMessage().contains("sort comparisons"));
  var vector=sample("vector",n).replace("copies=0 moves=0 destroyed=4096","copies=8386560 moves=0 destroyed=8390656");
  assertTrue(assertThrows(IllegalStateException.class,()->CppComplexityContracts.validate("vector",n,vector)).getMessage().contains("vector relocation"));
 }
 @Test void rejectsRootSearchForEveryHintAndReinsertionInsteadOfLinearCopy() {
  int n=4096;
  String hint=sample("tree",n).replace("comparisons=4096","comparisons=100000");
  assertTrue(assertThrows(IllegalStateException.class,()->CppComplexityContracts.validate("tree",n,hint)).getMessage().contains("hint comparisons"));
  String copy=sample("tree",n).replace("copy_comparisons=0","copy_comparisons=100000");
  assertTrue(assertThrows(IllegalStateException.class,()->CppComplexityContracts.validate("tree",n,copy)).getMessage().contains("copy comparisons"));
 }
 @Test void rejectsMissingAllocationHooksLeaksAndReserveReallocation() {
  int n=4096;
  String hooks=sample("vector",n).replace("allocations=1 frees=1","allocations=0 frees=0");
  assertTrue(assertThrows(IllegalStateException.class,()->CppComplexityContracts.validate("vector",n,hooks)).getMessage().contains("allocation hooks"));
  String reserve=sample("vector",n).replace("extra_alloc=0","extra_alloc=1");
  assertTrue(assertThrows(IllegalStateException.class,()->CppComplexityContracts.validate("vector",n,reserve)).getMessage().contains("reserve"));
  String leak=sample("tree",n).replace("live=0","live=1");
  assertTrue(assertThrows(IllegalStateException.class,()->CppComplexityContracts.validate("tree",n,leak)).getMessage().contains("lifetime"));
 }
 @Test void rejectsMalformedOrIncompleteCounterReports() {
  assertThrows(IllegalStateException.class,()->CppComplexityContracts.validate("sort",4096,""));
  assertThrows(IllegalStateException.class,()->CppComplexityContracts.validate("sort",4096,sample("sort",4096).replace("case=1","case=0")));
  assertThrows(IllegalStateException.class,()->CppComplexityContracts.validate("sort",4096,sample("sort",4096).replace("comparisons=16384","comparisons=-1")));
 }
 @Test void actualCorrectButQuadraticSortIsRejectedByCountAcceptance()throws Exception {
  Path root=Path.of(System.getProperty("minic.project.root", ".")).toAbsolutePath().normalize();
  Path out=System.getProperty("minic.complexity.output")==null?temporary.resolve("quadratic-control"):Path.of(System.getProperty("minic.complexity.output")).resolve("quadratic-control");
  String observed=CppComplexityRunner.runQuadraticControl(root,out);
  // The control itself verifies ordering; verify all independent checksums before
  // asserting the count gate rejects its otherwise correct result.
  for(int mode=0;mode<6;mode++)assertTrue(observed.contains("case="+mode+" n=4096 comparisons=8386560 checksum="+CppComplexityContracts.checksum("sort",mode,4096)),observed);
  assertTrue(assertThrows(IllegalStateException.class,()->CppComplexityContracts.validate("sort",4096,observed)).getMessage().contains("sort comparisons"));
 }
 @ParameterizedTest(name="{0}: count bounds, four native builds and bounded debug")
 @ValueSource(strings={"sort","vector","tree"})
 void actualLibraryOperationsMeetTheIndependentBounds(String kind)throws Exception {
  Path root=Path.of(System.getProperty("minic.project.root", ".")).toAbsolutePath().normalize();
  Path out=System.getProperty("minic.complexity.output")==null?temporary.resolve(kind):Path.of(System.getProperty("minic.complexity.output")).resolve(kind);
  CppComplexityRunner.run(root,out,kind,4096,64);
 }
}
