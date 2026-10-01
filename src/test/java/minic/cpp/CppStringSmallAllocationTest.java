package minic.cpp;

import minic.benchmark.StlBenchmarkProbes;
import minic.compiler.LanguageMode;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.cpp.support.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

/** Exact copied-header counters; no timing or claim that host STL must provide SSO. */
@Tag("stl-contract") @Timeout(900) @Execution(ExecutionMode.SAME_THREAD)
final class CppStringSmallAllocationTest {
    @TempDir Path temporary;
    @Test void shortObjectsAndAliasedShortEditsAllocateNothingInTheOwnLibrary()throws Exception {
        checkAllocation("zero");
    }
    @Test void distinctWholeStringCopiesAllocateOnlyWhenStorageMustGrow()throws Exception {
        checkAllocation("copy");
    }
    private void checkAllocation(String contract)throws Exception {
        Path root=Path.of(System.getProperty("minic.project.root",".")).toAbsolutePath();
        var headers=StlBenchmarkProbes.instrumentOwnHeaders(root,temporary.resolve("p"));
        String body;try(var input=getClass().getResourceAsStream("/cpp/string-small-storage/"+contract+"-allocation.cpp")){
            assertNotNull(input);body=new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        Path source=temporary.resolve("zero-allocation.cpp");
        Files.writeString(source,Files.readString(root.resolve("benchmarks/stl/probe-hooks.mh"))+"\n"+body);
        // An isolated working directory selects the copied library for every existing worker.
        // A parent-only system property would not be inherited by the differential harness.
        var result=BoundedProcess.run(ProcessProbe.javaCommand(Worker.class,source.toString(),contract),headers.projectRoot(),"",
            Duration.ofSeconds(840),1_048_576);
        Files.writeString(temporary.resolve("zero-allocation-result.txt"),result.toString());
        assertFalse(result.timedOut(),result::toString);assertFalse(result.outputExceeded(),result::toString);
        assertEquals(0,result.exitCode(),result::toString);
        assertEquals("own allocation contracts passed\n",result.stdout().replace("\r\n","\n"));
    }
    public static final class Worker {
        public static void main(String[] args)throws Exception {
            String source=Files.readString(Path.of(args[0]));
            var limits=new CppDifferentialHarness.Limits(Duration.ofSeconds(120),Duration.ofSeconds(45),3_000_000,1_048_576);
            int failures=0;
            for(var level:OptimizationLevel.values()){
                Path out=Files.createTempDirectory(Path.of(".").toAbsolutePath().normalize(),"s-");
                var report=new CppDifferentialHarness(out,CppDifferentialHarness.referenceCompiler(System.getenv()),limits,
                    LanguageMode.CPP17_ALGORITHM,level).run("sso",source,"");
                var own=CppOwnLibraryReference.run(out,source,"",limits);
                Files.writeString(out.resolve("report.txt"),report.describe()+"\n"+own);
                if(!report.passed()||!own.passed()){System.err.println(level+"\n"+report.describe()+"\n"+own);failures++;continue;}
                if(!own.stdout().replace("\r\n","\n").equals(args[1]+" allocation ok\n"))throw new AssertionError(own);
            }
            if(failures!=0)throw new AssertionError("Failed allocation modes: "+failures);
            System.out.println("own allocation contracts passed");
        }
    }
}
