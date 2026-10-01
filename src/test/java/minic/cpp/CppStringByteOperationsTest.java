package minic.cpp;
import minic.compiler.LanguageMode;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.library.SystemLibraryCatalog;
import minic.cpp.support.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Bulk byte paths must retain string aliasing, terminators and SSO lifetime boundaries. */
@Tag("stl-contract") @Execution(ExecutionMode.SAME_THREAD) @Timeout(300)
final class CppStringByteOperationsTest {
 @TempDir Path temporary;
 static final CppDifferentialHarness.Limits LIMITS=new CppDifferentialHarness.Limits(Duration.ofSeconds(90),Duration.ofSeconds(45),4_000_000,1_048_576);
 static Stream<Arguments> cases(){return Stream.of("bytes","alias","storage","fill").flatMap(name->Stream.of(OptimizationLevel.BASELINE,OptimizationLevel.OPTIMIZED).map(level->Arguments.of(name,level)));}
 @ParameterizedTest(name="{0} [{1}]") @MethodSource("cases")
 void bulkOperationsPreserveStringContract(String name,OptimizationLevel level)throws Exception {
  String source;try(var input=getClass().getResourceAsStream("/cpp/string-byte-operations/"+name+".cpp")){assertNotNull(input);source=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
  var result=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),LIMITS,LanguageMode.CPP17_ALGORITHM,level).run(name,source,"");
  assertTrue(result.passed(),result::describe);for(var outcome:result.outcomes().values()){assertEquals("ok\n",outcome.stdout().replace("\r\n","\n"),result::describe);assertEquals("",outcome.stderr());}
  var own=CppOwnLibraryReference.run(temporary,source,"",LIMITS);assertTrue(own.passed(),own::toString);assertEquals("ok\n",own.stdout().replace("\r\n","\n"));
 }
 @Test void independentBulkProbeObservesAllFiveByteOperations()throws Exception {
  Path library=SystemLibraryCatalog.defaults().includeRoot().resolve("cpp"),copy=temporary.resolve("cpp");
  try(var paths=Files.walk(library)){for(Path source:paths.toList()){Path target=copy.resolve(library.relativize(source));if(Files.isDirectory(source))Files.createDirectories(target);else Files.copy(source,target);}}
  Path header=copy.resolve("string.mh");String original=Files.readString(header),instrumented=original;
  for(String name:List.of("memcpy","memmove","memset","memcmp","strlen"))instrumented=instrumented.replace("::"+name+"(","::observed_"+name+"(");
  Files.writeString(header,instrumented);
  String source="""
    #include <string.h>
    #include <stdio.h>
    unsigned long long calls[5]={0,0,0,0,0};
    void* observed_memcpy(void*d,const void*s,unsigned long long n){++calls[0];return ::memcpy(d,s,n);}
    void* observed_memmove(void*d,const void*s,unsigned long long n){++calls[1];return ::memmove(d,s,n);}
    void* observed_memset(void*d,int v,unsigned long long n){++calls[2];return ::memset(d,v,n);}
    int observed_memcmp(const void*a,const void*b,unsigned long long n){++calls[3];return ::memcmp(a,b,n);}
    unsigned long long observed_strlen(const char*s){++calls[4];return ::strlen(s);}
    #include <string>
    int main(){std::string a("abcdefghijklmnopqrstuvwxyz");std::string b(a);a.reserve(80);
      a.insert(2,a.data()+4,11);a.replace(1,12,3,'!');a.erase(2,3);
      char output[8];if(b.copy(output,4,1)!=4||output[0]!='b')return 1;
      if(a.compare(b)>=0)return 2;
      printf("%llu %llu %llu %llu %llu\\n",calls[0],calls[1],calls[2],calls[3],calls[4]);return 0;}
    """;
  Path program=temporary.resolve("probe.cpp"),executable=temporary.resolve("probe.exe");Files.writeString(program,source);
  Path headers=CppOwnLibraryReference.prepareHeaders(temporary.resolve("headers"),copy);
  var command=CppOwnLibraryReference.compileCommand(CppDifferentialHarness.referenceCompiler(System.getenv()),temporary,headers,program,executable);
  var compiled=BoundedProcess.run(command,temporary,"",Duration.ofSeconds(90),65536);
  assertFalse(compiled.timedOut());assertFalse(compiled.outputExceeded());assertEquals(0,compiled.exitCode(),compiled::stderr);
  var result=BoundedProcess.run(List.of(executable.toString()),temporary,"",Duration.ofSeconds(10),65536);
  assertFalse(result.timedOut());assertFalse(result.outputExceeded());assertEquals(0,result.exitCode(),result::stderr);
  String[] counts=result.stdout().strip().split("\\s+");assertEquals(5,counts.length);
  for(int i=0;i<counts.length;++i)assertTrue(Long.parseLong(counts[i])>0,"Expected private string header to dispatch byte operation "+i+", counts="+result.stdout());
 }
}
