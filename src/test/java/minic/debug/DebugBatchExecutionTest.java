package minic.debug;
import minic.compiler.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
final class DebugBatchExecutionTest {
 private static final String PROGRAM="""
  #include <stdio.h>
  #include <stdlib.h>
  int total;
  int twice(int n){return n*2;}
  int main(){int n;scanf("%d",&n);int*p=(int*)malloc(4);*p=3;for(int i=0;i<n;++i)total+=twice(i);printf("%d %d",total,*p);free(p);return 7;}
  """;
 @Test void batchMatchesSteppingIncludingMemoryTerminationAndAbsoluteStopIndex(){
  var source=new SourceFile("batch.c",PROGRAM);var ir=new CompilerApi(source).runToIr();
  var stepped=DebugApi.fromIr(source,ir,"5");while(stepped.canNext())stepped.next();
  var batch=DebugApi.execute(source,ir,"5",10000,1024);
  assertFalse(batch.stepLimitReached());assertFalse(batch.outputLimitReached());
  assertEquals(stepped.current().runtime(),batch.context().runtime());
  assertEquals(stepped.current().stop(),batch.context().stop());assertEquals(stepped.current().index(),batch.context().index());
  assertEquals("20 3",batch.context().runtime().stdout());assertEquals(7,batch.context().runtime().termination().status());
  while(stepped.canPrevious())stepped.previous();assertEquals(0,stepped.current().index());
 }
 @Test void cppLibraryDestructionStillRunsAndFreesStorage(){
  var source=new SourceFile("batch.cpp","#include <vector>\nint live;struct T{T(){++live;}~T(){--live;}};int main(){{std::vector<T> a(3);}return live;}");
  var ir=new CompilerApi(source,LanguageMode.CPP17_ALGORITHM).runToIr();
  var batch=DebugApi.execute(source,ir,"",10000,1024);
  assertEquals(Debugger.Status.COMPLETED,batch.context().stop().status(),batch.context().stop()::error);
  assertEquals(0,batch.context().runtime().termination().status());assertTrue(batch.context().runtime().heap().isEmpty());
 }
 @Test void sourceStopBudgetStillBoundsInfiniteLoops(){
  var source=new SourceFile("infinite.c","int main(){while(1){}} ");var ir=new CompilerApi(source).runToIr();
  var batch=DebugApi.execute(source,ir,"",12,0);assertTrue(batch.stepLimitReached());
  assertEquals(12,batch.context().index());assertEquals(Debugger.Status.PAUSED,batch.context().stop().status());
 }
 @Test void exactTerminalStopBudgetCompletesButOneFewerPreservesTheLastPause(){
  var source=new SourceFile("terminal-budget.c","int main(){int value=1;\nvalue+=2;\nreturn value;}");
  var ir=new CompilerApi(source).runToIr();
  var stepped=DebugApi.fromIr(source,ir,"");while(stepped.canNext())stepped.next();
  var terminal=stepped.current();var lastPause=stepped.previous();
  assertTrue(terminal.index()>1);assertEquals(Debugger.Status.PAUSED,lastPause.stop().status());
  var exact=DebugApi.execute(source,ir,"",terminal.index(),0);
  assertFalse(exact.stepLimitReached());assertFalse(exact.outputLimitReached());
  assertEquals(terminal.index(),exact.context().index());assertEquals(terminal.stop(),exact.context().stop());
  assertEquals(terminal.runtime(),exact.context().runtime());
  var shortBudget=DebugApi.execute(source,ir,"",terminal.index()-1,0);
  assertTrue(shortBudget.stepLimitReached());assertFalse(shortBudget.outputLimitReached());
  assertEquals(lastPause.index(),shortBudget.context().index());assertEquals(lastPause.stop(),shortBudget.context().stop());
  assertEquals(lastPause.runtime(),shortBudget.context().runtime());
 }
 @Test void exactRawByteBudgetIsInclusiveAndIndependentForBothStreams(){
  var source=new SourceFile("exact-output.c","#include <stdio.h>\nint main(){const char*s=\"中文\";for(int i=0;s[i];++i){fputc((unsigned char)s[i],stdout);fputc((unsigned char)s[i],stderr);}return 0;}");
  var ir=new CompilerApi(source).runToIr();
  var exact=DebugApi.execute(source,ir,"",1000,6);
  assertFalse(exact.outputLimitReached());assertFalse(exact.stepLimitReached());
  assertEquals(Debugger.Status.COMPLETED,exact.context().stop().status(),exact.context().stop()::error);
  assertEquals("中文",exact.context().runtime().stdout());assertEquals("中文",exact.context().runtime().stderr());
  var oneByteOver=DebugApi.execute(source,ir,"",1000,5);
  assertTrue(oneByteOver.outputLimitReached());assertFalse(oneByteOver.stepLimitReached());
 }
 @Test void outputBudgetCountsBytesForEitherStream(){
  for(String target:new String[]{"stdout","stderr"}){
   var source=new SourceFile("output.c","#include <stdio.h>\nint main(){const char*s=\"中文\";for(int i=0;s[i];++i)fputc((unsigned char)s[i],"+target+");return 0;}");
   var api=new CompilerApi(source);var semantic=api.stages().stream().filter(minic.compiler.semantic.SemanticAnalyzer.class::isInstance).map(minic.compiler.semantic.SemanticAnalyzer.class::cast).findFirst().orElseThrow();api.runThrough(semantic);assertTrue(semantic.succeeded(),()->semantic.errors().toString());
   var batch=DebugApi.execute(source,api.runToIr(),"",1000,4);
   assertTrue(batch.outputLimitReached());
  }
 }
 @Test void runtimeErrorsRetainSourceLocationAndDiagnostic(){
  var source=new SourceFile("uninitialized.c","int main(){int value;\nreturn value;}");var ir=new CompilerApi(source).runToIr();
  var batch=DebugApi.execute(source,ir,"",1000,0);
  assertEquals(Debugger.Status.FAILED,batch.context().stop().status());assertTrue(batch.context().stop().error().contains("uninitialized"));
  assertEquals(2,batch.context().stop().range().startLine());assertFalse(batch.stepLimitReached());
 }
 @Test void invalidBudgetsAreRejected(){
  var source=new SourceFile("empty.c","int main(){return 0;}");var ir=new CompilerApi(source).runToIr();
  assertThrows(IllegalArgumentException.class,()->DebugApi.execute(source,ir,"",0,1));
  assertThrows(IllegalArgumentException.class,()->DebugApi.execute(source,ir,"",1,-1));
 }
}
