package minic.compiler.asm;
import minic.compiler.LanguageMode;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
@Timeout(120)
final class NativeStackProbeTest {
 @TempDir Path temporary;
 private void agree(String name,String source,String expected)throws Exception {
  for(var level:OptimizationLevel.values()) {
   var limits=new CppDifferentialHarness.Limits(Duration.ofSeconds(30),Duration.ofSeconds(15),200000,1048576);
   var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),limits,LanguageMode.C,level).run(name+level,source,"");
   assertTrue(report.passed(),report::describe);
   assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.MINIC_NATIVE).stdout().replace("\r\n","\n"));
  }
 }
 @Test void largeOrdinaryFramesPreserveRegisterAndStackArguments()throws Exception {
  agree("arguments","""
   #include <stdio.h>
   int f(int a,int b,int c,int d,int e,int f){volatile int data[16384];data[0]=a;data[16383]=f;return data[0]+b+c+d+e+data[16383];}
   int main(){printf("%d\\n",f(1,2,3,4,5,6));return 0;}
   ""","21\n");
 }
 @Test void largeFramesProbeAcrossNestedCallsAndRestoreTheirStack()throws Exception {
  agree("recursive","""
   #include <stdio.h>
   int f(int n){volatile char data[32768];data[0]=n;data[32767]=n+1;return n?data[0]+f(n-1)+data[32767]:data[32767];}
   int main(){printf("%d %d\\n",f(3),f(2));return 0;}
   ""","16 9\n");
 }
 @Test void largeFramesPreserveFloatingPointAndVariadicInputs()throws Exception {
  agree("variadic","""
   #include <stdio.h>
   #include <stdarg.h>
   double f(int n,...){volatile char data[32768];data[0]=n;data[32767]=1;va_list ap;va_start(ap,n);double a=va_arg(ap,double);double b=va_arg(ap,double);va_end(ap);return a+b+data[0]+data[32767];}
   int main(){printf("%.1f\\n",f(2,3.5,4.5));return 0;}
   ""","11.0\n");
 }
}
