package minic.cpp;
import minic.compiler.LanguageMode;
import minic.cpp.support.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
final class CppFloatingLibraryRuntimeTest {
 @TempDir Path temporary;
 private void agree(String name,String source,String expected)throws Exception{
  var limits=new CppDifferentialHarness.Limits(Duration.ofSeconds(90),Duration.ofSeconds(30),2000000,1048576);
  var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),limits,LanguageMode.CPP17_ALGORITHM).run(name,source,"");
  var own=CppOwnLibraryReference.run(temporary,source,"",limits);
  assertAll(()->assertTrue(report.passed(),report::describe),()->assertTrue(own.passed(),own::toString),()->assertEquals(expected,own.stdout().replace("\r\n","\n")));
  assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
 }
 private String resource(String name)throws Exception{try(var r=getClass().getResourceAsStream("/cpp/"+name)){assertNotNull(r);return new String(r.readAllBytes(),StandardCharsets.UTF_8);}}
 @Test void originalFloatingFormattingPreservesCaseAndNonFiniteValues()throws Exception{
  agree("stream-format",resource("library-review/stream-floating-format.cpp"),"1.25e+01 1.25e-03\n+1.00E+03\n1.2e+05 0.00012\n12.\ninf -inf nan\n+inf +nan\n-___inf\n");
 }
 @Test void floatParsingRoundsOnceAcrossTheFourBuilds()throws Exception{
  agree("stof-round",resource("strtof-runtime/string-stof.cpp"),"3f800001 46 1.25\n");
 }
 @Test void strtodPreservesSpecialPrefixesSuffixesAndErrno()throws Exception{
  agree("strtod-boundaries","""
   #include <stdlib.h>
   #include <stdio.h>
   #include <float.h>
   #include <errno.h>
   int main(){char*end;const char*text=" -INFINITYtail";errno=17;double a=strtod(text,&end);printf("%d %lld %d\\n",a<-DBL_MAX,(long long)(end-text),errno);
   text="nan(payload)!";double b=strtod(text,&end);printf("%d %lld\\n",b!=b,(long long)(end-text));
   text="0x1.8p+2end";double c=strtod(text,&end);printf("%.1f %lld\\n",c,(long long)(end-text));
   text="nonsense";errno=19;double d=strtod(text,&end);printf("%d %lld %d\\n",d==0,(long long)(end-text),errno);
   errno=0;double e=strtod("1e9999",0);printf("%d %d\\n",e>DBL_MAX,errno==ERANGE);
   errno=0;double f=strtod("1e-9999",0);printf("%d %d\\n",f==0,errno==ERANGE);return 0;}
   ""","1 10 17\n1 12\n6.0 8\n1 0 19\n1 1\n1 1\n");
 }
 @Test void fixedUsesLowercaseWhileScientificAndDefaultHonorUppercase()throws Exception{
  // N4659 [facet.num.put.virtuals] table 76: fixed is %f regardless of uppercase.
  agree("nonfinite-case","""
   #include <iostream>
   #include <iomanip>
   #include <stdlib.h>
   int main(){double a=strtod("inf",0),b=strtod("nan",0);
   std::cout<<std::uppercase<<std::showpos<<std::defaultfloat<<a<<' '<<b<<'\\n';
   std::cout<<std::scientific<<a<<' '<<b<<'\\n';
   std::cout<<std::fixed<<a<<' '<<b<<'\\n';
   std::cout<<std::internal<<std::setfill('_')<<std::setw(7)<<-a<<'\\n';}
   ""","+INF +NAN\n+INF +NAN\n+inf +nan\n-___inf\n");
 }
}
