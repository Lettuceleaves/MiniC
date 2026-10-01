package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.cpp.support.*;
import minic.compiler.library.SystemLibraryCatalog;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Endpoint growth is linear in newly needed blocks, with the original lifetime/address contract. */
@Tag("stl-contract") @Execution(ExecutionMode.SAME_THREAD) @Timeout(300)
final class CppDequePerformanceTest {
    @TempDir Path temporary;
    static final CppDifferentialHarness.Limits LIMITS=new CppDifferentialHarness.Limits(
            Duration.ofSeconds(90),Duration.ofSeconds(60),3_000_000,1_048_576);
    static Stream<Arguments> cases(){return Stream.of("endpoints","alias","move-only","odd-block")
            .flatMap(name->Stream.of(OptimizationLevel.BASELINE,OptimizationLevel.OPTIMIZED).map(level->Arguments.of(name,level)));}
    @ParameterizedTest(name="{0} [{1}]") @MethodSource("cases")
    void endpointFastPathsPreserveValuesAddressesAndLifetimes(String name,OptimizationLevel level)throws Exception {
        String source;
        try(var input=getClass().getResourceAsStream("/cpp/deque-performance/"+name+".cpp")){
            assertNotNull(input);source=new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        var result=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),LIMITS,LanguageMode.CPP17_ALGORITHM,level).run(name,source,"");
        assertTrue(result.passed(),result::describe);
        for(var outcome:result.outcomes().values()){assertEquals("ok\n",outcome.stdout().replace("\r\n","\n"),result::describe);assertEquals("",outcome.stderr());}
        var own=CppOwnLibraryReference.run(temporary,source,"",LIMITS);assertTrue(own.passed(),own::toString);assertEquals("ok\n",own.stdout().replace("\r\n","\n"));
    }
    @Test void frontGrowthChecksOnlyNewBlocksInsteadOfRescanningLiveBlocks()throws Exception {
        // Count allocation-slot checks in a private COPY of the real header, never in timing builds.
        Path library=SystemLibraryCatalog.defaults().includeRoot().resolve("cpp"),copy=temporary.resolve("cpp");
        try(var paths=Files.walk(library)){
            for(Path source:paths.toList()){Path target=copy.resolve(library.relativize(source));if(Files.isDirectory(source))Files.createDirectories(target);else Files.copy(source,target);}
        }
        Path header=copy.resolve("deque.mh");String original=Files.readString(header);
        String guard="if(map_[first_+i]==nullptr)";
        assertEquals(2,original.split(java.util.regex.Pattern.quote(guard),-1).length-1,"Both front/back allocation loops must remain observable");
        Files.writeString(header,original.replace(guard,"if((++::allocation_checks,map_[first_+i]==nullptr))"));
        String source="""
            unsigned long long allocation_checks=0;
            #include <deque>
            #include <stdio.h>
            int main(){std::deque<int> values;
              for(int cycle=0;cycle<3;++cycle){
                for(int i=0;i<1024;++i)values.push_front(i);
                for(int i=1023;i>=0;--i){if(values.front()!=i)return 1;values.pop_front();}
              }
              printf("%llu\\n",allocation_checks);return 0;}
            """;
        Path program=temporary.resolve("count.cpp"),executable=temporary.resolve("count.exe");Files.writeString(program,source);
        Path headers=CppOwnLibraryReference.prepareHeaders(temporary.resolve("headers"),copy);
        var command=CppOwnLibraryReference.compileCommand(CppDifferentialHarness.referenceCompiler(System.getenv()),temporary,headers,program,executable);
        var compiled=BoundedProcess.run(command,temporary,"",Duration.ofSeconds(90),65536);
        assertFalse(compiled.timedOut());assertFalse(compiled.outputExceeded());assertEquals(0,compiled.exitCode(),compiled::stderr);
        var run=BoundedProcess.run(List.of(executable.toString()),temporary,"",Duration.ofSeconds(10),65536);
        assertFalse(run.timedOut());assertFalse(run.outputExceeded());assertEquals(0,run.exitCode(),run::stderr);
        long checks=Long.parseLong(run.stdout().strip());
        assertTrue(checks<=30,"Three growth cycles require at most 3*(ceil(1024/128)+2) allocation-slot checks, actual="+checks);
    }
}
