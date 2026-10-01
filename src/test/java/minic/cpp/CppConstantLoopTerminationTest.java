package minic.cpp;
import minic.compiler.*;
import minic.compiler.ir.optimize.*;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
final class CppConstantLoopTerminationTest {
 @TempDir Path temporary;
 static Stream<Arguments> modes(){return Stream.of(LanguageMode.C,LanguageMode.CPP17_ALGORITHM).flatMap(language->Stream.of(OptimizationLevel.values()).map(level->Arguments.of(language,level)));}
 @ParameterizedTest(name="{0}/{1}") @MethodSource("modes")
 void finiteReturnsInsideConstantLoopsVerifyAndExecute(LanguageMode language,OptimizationLevel level)throws Exception{
  String source="""
   #include <stdio.h>
   int w(int n){while(1){if(--n==0)return 7;}}
   int d(int n){do{if(--n==0)return 9;}while(1);}
   int f(int n){for(;1;){if(--n==0)return 11;}}
   int main(){int count=0,hits=0;do{++count;}while((++hits,0));while(0){++count;}for(;0;){++count;}printf("%d %d %d %d %d\\n",w(3),d(4),f(2),count,hits);return 0;}
   """;
  var api=new CompilerApi(new SourceFile("constant-loops.c",source),language,level);
  assertDoesNotThrow(()->IrVerifier.verify(api.runToIr()));
  var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),language,level).run("constant-loops",source,"");
  assertTrue(report.passed(),report::describe);assertEquals("7 9 11 1 1\n",report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
 }
 @ParameterizedTest @ValueSource(strings={"while(1){}","do{}while(1);","for(;1;){}","for(;;){}"})
 void uncalledNonReturningFunctionsHaveNoSpuriousReachableExit(String loop){
  var ir=new CompilerApi(new SourceFile("never.c","int never(){"+loop+"}int main(){return 0;}"),LanguageMode.CPP17_ALGORITHM).runToIr();
  var result=IrVerifier.inspect(ir);assertTrue(result.isEmpty(),result::toString);
 }
 @Test void breakAndRuntimeConditionStillRequireARealReturn(){
  for(String body:new String[]{"while(1){break;}","do{break;}while(1);","for(;1;){break;}","while(n){}"}){
   var api=new CompilerApi(new SourceFile("fallthrough.c","int f(int n){"+body+"}int main(){return 0;}"),LanguageMode.CPP17_ALGORITHM);
   var semantic=api.stages().stream().filter(minic.compiler.semantic.SemanticAnalyzer.class::isInstance).findFirst().orElseThrow();api.runThrough(semantic);assertFalse(semantic.succeeded(),body);
  }
 }
 @Test void actualShuffleLibraryPassesTheOptimizedPipeline()throws Exception{
  String source;try(var in=getClass().getResourceAsStream("/cpp/library-containers/shuffle.cpp")){assertNotNull(in);source=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
  var limits=new CppDifferentialHarness.Limits(Duration.ofSeconds(90),Duration.ofSeconds(30),2000000,1048576);
  var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),limits,LanguageMode.CPP17_ALGORITHM,OptimizationLevel.OPTIMIZED).run("shuffle-constant-loop",source,"");
  assertTrue(report.passed(),report::describe);assertEquals("1\n",report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
 }
}
