package minic.cpp;

import minic.compiler.*;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.ir.instruction.ControlInstruction.IrBranchInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrLoadPointerInstruction;
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

/** Direct-data-pointer candidate: semantic ownership checks plus an unoptimized accessor cost contract. */
@Tag("stl-contract") @Execution(ExecutionMode.SAME_THREAD) @Timeout(600)
final class CppStringDirectStorageTest {
 @TempDir Path temporary;
 static final CppDifferentialHarness.Limits LIMITS=new CppDifferentialHarness.Limits(Duration.ofSeconds(120),Duration.ofSeconds(45),5_000_000,1_048_576);
 static Stream<Arguments> cases(){return Stream.of("ownership","returns","containers","aliases-and-swap").flatMap(n->Stream.of(OptimizationLevel.BASELINE,OptimizationLevel.OPTIMIZED).map(l->Arguments.of(n,l)));}
 @ParameterizedTest(name="{0} [{1}]") @MethodSource("cases")
 void ownershipSurvivesMovesReturnsContainersAndAliases(String name,OptimizationLevel level)throws Exception {
  String source;try(var in=getClass().getResourceAsStream("/cpp/string-direct-storage/"+name+".cpp")){assertNotNull(in);source=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
  var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),LIMITS,LanguageMode.CPP17_ALGORITHM,level).run(name,source,"");
  assertTrue(report.passed(),report::describe);for(var out:report.outcomes().values())assertEquals("ok\n",out.stdout().replace("\r\n","\n"),report::describe);
  var own=CppOwnLibraryReference.run(temporary,source,"",LIMITS);assertTrue(own.passed(),own::toString);assertEquals("ok\n",own.stdout().replace("\r\n","\n"));
 }
 @Test void dataAccessorsLoadOneStoredPointerWithoutSelectingStorage(){
  var api=new CompilerApi(new SourceFile("data-accessors.cpp","#include <string>\nchar* writable(std::string& s){return s.data();}\nconst char* readable(const std::string& s){return s.data();}\nint main(){std::string a;return writable(a)!=readable(a);}"),LanguageMode.CPP17_ALGORITHM);
  var ir=api.runToIr();var methods=ir.functions().stream().filter(f->ir.displayName(f.name()).equals("std::string::data")).toList();
  assertEquals(2,methods.size(),()->ir.displayNames().toString());
  for(var method:methods){var instructions=method.blocks().stream().flatMap(b->b.instructions().stream()).toList();
   assertEquals(0,instructions.stream().filter(IrBranchInstruction.class::isInstance).count(),"data() must not select inline versus heap storage");
   assertEquals(1,instructions.stream().filter(IrLoadPointerInstruction.class::isInstance).count(),"data() loads its stored pointer exactly once");
  }
 }
 @Test void storedPointerIsActualDataForEveryShortLengthAndHeapTransition()throws Exception {
  Path library=SystemLibraryCatalog.defaults().includeRoot().resolve("cpp"),copy=temporary.resolve("cpp");
  try(var paths=Files.walk(library)){for(Path src:paths.toList()){Path dst=copy.resolve(library.relativize(src));if(Files.isDirectory(src))Files.createDirectories(dst);else Files.copy(src,dst);}}
  Path header=copy.resolve("string.mh");String text=Files.readString(header);
  assertTrue(text.contains("public:"));Files.writeString(header,text.replaceFirst("public:","public:\n    bool __direct_pointer_probe() const { return buffer_ == data(); }"));
  String source="""
    #include <string>
    #include <utility>
    #include <stdio.h>
    int main(){for(int n=0;n<18;++n){std::string a(n,'a');std::string b(a);std::string c(std::move(a));
     if(!a.__direct_pointer_probe()||!b.__direct_pointer_probe()||!c.__direct_pointer_probe()){printf("pointer invariant n=%d\\n",n);return 10;}
     a.reserve(80);a.assign(n,'r');a.shrink_to_fit();b=std::move(c);a.swap(b);
     if(!a.__direct_pointer_probe()||!b.__direct_pointer_probe()||!c.__direct_pointer_probe())return 11;
    }puts("pointer invariant ok");}
    """;
  Path program=temporary.resolve("probe.cpp"),exe=temporary.resolve("probe.exe");Files.writeString(program,source);
  Path headers=CppOwnLibraryReference.prepareHeaders(temporary.resolve("headers"),copy);
  var command=CppOwnLibraryReference.compileCommand(CppDifferentialHarness.referenceCompiler(System.getenv()),temporary,headers,program,exe);
  var compile=BoundedProcess.run(command,temporary,"",Duration.ofSeconds(90),65536);assertFalse(compile.timedOut());assertEquals(0,compile.exitCode(),compile::stderr);
  var run=BoundedProcess.run(List.of(exe.toString()),temporary,"",Duration.ofSeconds(10),65536);assertFalse(run.timedOut());assertEquals(0,run.exitCode(),run::toString);assertEquals("pointer invariant ok\n",run.stdout().replace("\r\n","\n"));
 }
}
