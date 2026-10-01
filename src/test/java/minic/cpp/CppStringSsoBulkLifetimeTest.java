package minic.cpp;
import minic.compiler.*;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.library.SystemLibraryCatalog;
import minic.cpp.support.*;
import minic.debug.*;
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

@Tag("stl-contract") @Execution(ExecutionMode.SAME_THREAD) @Timeout(300)
final class CppStringSsoBulkLifetimeTest {
 @TempDir Path temporary;
 static final CppDifferentialHarness.Limits LIMITS=new CppDifferentialHarness.Limits(Duration.ofSeconds(90),Duration.ofSeconds(45),4_000_000,1_048_576);
 static Stream<Arguments> cases(){return Stream.of("activation","representation","string-transitions").flatMap(n->Stream.of(OptimizationLevel.BASELINE,OptimizationLevel.OPTIMIZED).map(l->Arguments.of(n,l)));}
 @ParameterizedTest(name="{0} [{1}]") @MethodSource("cases")
 void onlyInitializedCharactersAndTerminatorAreRead(String name,OptimizationLevel level)throws Exception {
  String source;try(var in=getClass().getResourceAsStream("/cpp/string-sso-bulk/"+name+".cpp")){assertNotNull(in);source=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
  var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),LIMITS,LanguageMode.CPP17_ALGORITHM,level).run(name,source,"");
  assertTrue(report.passed(),report::describe);for(var out:report.outcomes().values())assertEquals("ok\n",out.stdout().replace("\r\n","\n"),report::describe);
  var own=CppOwnLibraryReference.run(temporary,source,"",LIMITS);assertTrue(own.passed(),own::toString);assertEquals("ok\n",own.stdout().replace("\r\n","\n"));
 }
 @ParameterizedTest @ValueSource(booleans={false,true})
 void copyingRepresentationsDoesNotInventInitializedTailValues(boolean wholeUnion){
  // Invalid tail read is executed only by checked MiniC, never used as a C++ native oracle.
  String operation=wholeUnion?"target=source;":"target.small[0]=source.small[0];::memcpy(target.small+1,source.small+1,1);";
  String preparation=wholeUnion?"for(int i=0;i<16;++i)target.small[i]='x';":"target.small[0]=0;";
  String text="#include <string.h>\n#include <stdio.h>\nunion Storage{unsigned long long capacity;char small[16];};\nint main(){Storage source;source.small[0]='a';source.small[1]=0;Storage target;"+preparation+operation+"if(target.small[0]!='a'||target.small[1]!=0)return 1;puts(\"copied\");return target.small[15];}";
  var source=new SourceFile("uninitialized-union-tail.cpp",text);var api=new CompilerApi(source,LanguageMode.CPP17_ALGORITHM);var ir=api.runToIr();
  var result=DebugApi.execute(source,ir,"",10000,65536);assertEquals(Debugger.Status.FAILED,result.context().stop().status());
  assertTrue(result.context().stop().error().toLowerCase().contains("uninitialized"),()->result.context().stop().toString());
  assertEquals("copied\n",result.context().runtime().stdout().replace("\r\n","\n"));
 }
 @Test void shortMoveAndShrinkCopyOnlyTheRemainingInitializedPrefix()throws Exception {
  Path library=SystemLibraryCatalog.defaults().includeRoot().resolve("cpp"),copy=temporary.resolve("cpp");
  try(var paths=Files.walk(library)){for(Path src:paths.toList()){Path dst=copy.resolve(library.relativize(src));if(Files.isDirectory(src))Files.createDirectories(dst);else Files.copy(src,dst);}}
  Path header=copy.resolve("string.mh");Files.writeString(header,Files.readString(header).replace("::memcpy(","::observed_memcpy("));
  String source="""
    #include <string.h>
    #include <stdio.h>
    unsigned long long copies=0,bytes=0;
    void* observed_memcpy(void*to,const void*from,unsigned long long n){++copies;bytes+=n;return ::memcpy(to,from,n);}
    #include <string>
    bool count(int n){return copies==(n!=0?1ULL:0ULL)&&bytes==(unsigned long long)n;}
    int main(){for(int n=0;n<16;++n){std::string a(n,'x');copies=0;bytes=0;std::string b(std::move(a));
      if(!count(n)){printf("move n=%d copies=%llu bytes=%llu\\n",n,copies,bytes);return 10;}
      std::string c(64,'z');copies=0;bytes=0;c=std::move(b);
      if(!count(n)){printf("assign n=%d copies=%llu bytes=%llu\\n",n,copies,bytes);return 11;}
      c.reserve(64);copies=0;bytes=0;c.shrink_to_fit();
      if(!count(n)){printf("shrink n=%d copies=%llu bytes=%llu\\n",n,copies,bytes);return 12;}
      if(c.size()!=(unsigned long long)n||c.data()[n]!=0)return 13;
      for(int i=0;i<n;++i)if(c[i]!='x')return 14;
    }puts("prefix counts ok");return 0;}
    """;
  Path program=temporary.resolve("probe.cpp"),exe=temporary.resolve("probe.exe");Files.writeString(program,source);
  Path headers=CppOwnLibraryReference.prepareHeaders(temporary.resolve("headers"),copy);
  var command=CppOwnLibraryReference.compileCommand(CppDifferentialHarness.referenceCompiler(System.getenv()),temporary,headers,program,exe);
  var compile=BoundedProcess.run(command,temporary,"",Duration.ofSeconds(90),65536);assertFalse(compile.timedOut());assertEquals(0,compile.exitCode(),compile::stderr);
  var run=BoundedProcess.run(List.of(exe.toString()),temporary,"",Duration.ofSeconds(10),65536);assertFalse(run.timedOut());assertEquals(0,run.exitCode(),run::toString);assertEquals("prefix counts ok\n",run.stdout().replace("\r\n","\n"));
 }
}
