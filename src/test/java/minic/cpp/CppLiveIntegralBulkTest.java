package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppOwnLibraryReference;
import minic.cpp.support.BoundedProcess;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Four-build value, lifetime, assignment-selection and invalid-program acceptance. */
@Tag("stl-contract") @Execution(ExecutionMode.SAME_THREAD) @Timeout(300)
final class CppLiveIntegralBulkTest {
    @TempDir Path temporary;
    static final CppDifferentialHarness.Limits LIMITS=new CppDifferentialHarness.Limits(
            Duration.ofSeconds(90),Duration.ofSeconds(45),2_000_000,1_048_576);
    record Case(String file,String expected){}
    static Stream<Case> cases(){return Stream.of(
            new Case("algorithm-ranges.cpp",rangeOracle()),
            new Case("integral-widths.cpp",widthOracle()),
            new Case("vector-live.cpp",vectorOracle()),
            new Case("vector-alias.cpp","7 9 7 \n7 9 7 7 7 7 \n7 7 9 7 7 7 7 \n"),
            new Case("nontrivial-fallback.cpp","copy 3 0 2 5 8\nmove 0 3 2 5 8 -900\nerase 0 3 3 2 0 1 5 6 7\nselected 2 11 13\n"),
            new Case("volatile-fallback.cpp","3 5 8\n17 23\n"),
            new Case("conversion-fallback.cpp","1 -2 16777216 1 2\n"),
            new Case("proxy-fallback.cpp","proxy 4 4 101 102 103 104\nreverse 4 3 2 1\nbits 1 1 0\n"));}
    static Stream<Arguments> programs(){return cases().flatMap(c->Stream.of(OptimizationLevel.BASELINE,OptimizationLevel.OPTIMIZED)
            .map(level->Arguments.of(c.file(),c.expected(),level)));}
    @ParameterizedTest(name="{0} [{2}]") @MethodSource("programs")
    void validOperationsMatchIndependentValuesInAllBuilds(String name,String expected,OptimizationLevel level,TestReporter reporter)throws Exception{
        String source=resource(name);
        String compiler=CppDifferentialHarness.referenceCompiler(System.getenv());
        var harness=new CppDifferentialHarness(temporary,compiler,LIMITS,LanguageMode.CPP17_ALGORITHM,level);
        var report=harness.run(name+"-"+level,source,"");
        boolean legacyHybrid=name.equals("nontrivial-fallback.cpp") && !normalize(report.outcomes()
                .get(CppDifferentialHarness.Backend.GXX).stdout()).equals(expected);
        if(legacyHybrid){
            // N4659 [alg.copy]/2,4 specifies the element assignment expressions.
            // The precise GCC 8.1 library observed here instead byte-copies Hybrid,
            // skipping its better non-const templated assignment. Preserve both results.
            var reference=report.outcomes().get(CppDifferentialHarness.Backend.GXX);
            assertEquals(CppDifferentialHarness.Status.OK,reference.status(),report::describe);
            assertEquals(0,reference.exitCode());assertEquals("",reference.stderr());
            assertEquals(expected.replace("selected 2 11 13", "selected 0 11 13"),normalize(reference.stdout()),report::describe);
            var version=BoundedProcess.run(List.of(compiler,"-dumpfullversion"),temporary,"",Duration.ofSeconds(10),4096);
            assertFalse(version.timedOut());assertFalse(version.outputExceeded());assertEquals(0,version.exitCode());
            assertEquals("8.1.0",version.stdout().strip(),report::describe);
            String explicitSource=source.replace("std::copy(from,from+2,to);","for(int i=0;i<2;++i)to[i]=from[i];");
            assertNotEquals(source,explicitSource);
            var control=harness.run(name+"-element-assignment-control-"+level,explicitSource,"");
            assertTrue(control.passed(),control::describe);
            for(var result:control.outcomes().values())assertEquals(expected,normalize(result.stdout()),control::describe);
            reporter.publishEntry(Map.of("reference-rule","https://timsong-cpp.github.io/cppwp/n4659/alg.copy#2",
                    "reference-version",version.stdout().strip(),"original-source",source,"host-copy-output",reference.stdout(),
                    "explicit-assignment-source",explicitSource,"explicit-assignment-output",expected));
        }else assertTrue(report.passed(),report::describe);
        for(var outcome:report.outcomes().values()){
            if(legacyHybrid&&outcome.backend()==CppDifferentialHarness.Backend.GXX)continue;
            assertEquals(CppDifferentialHarness.Status.OK,outcome.status(),report::describe);
            assertEquals(0,outcome.exitCode(),report::describe);
            assertEquals(expected,normalize(outcome.stdout()),outcome::toString);
            assertEquals("",normalize(outcome.stderr()),outcome::toString);
        }
        var own=CppOwnLibraryReference.run(temporary,source,"",LIMITS);
        assertTrue(own.passed(),own::toString);
        assertEquals(expected,normalize(own.stdout()),own::toString);
        assertEquals("",normalize(own.stderr()),own::toString);
    }
    static Stream<Arguments> invalidPrograms(){return Stream.of(
        Arguments.of("const-destination","#include <algorithm>\nint main(){int a[2]={1,2};const int b[2]={};std::copy(a,a+2,b);}"),
        Arguments.of("deleted-assignment","#include <algorithm>\nstruct X{int n;X&operator=(const X&)=delete;};int main(){X a[2]={},b[2]={};std::copy(a,a+2,b);}"),
        Arguments.of("private-assignment","#include <algorithm>\nstruct X{int n;private:X&operator=(const X&)=default;};int main(){X a[2],b[2];std::copy(a,a+2,b);}"));}
    @ParameterizedTest(name="reject {0}") @MethodSource("invalidPrograms")
    void fastPathCannotLegalizeForbiddenAssignment(String name,String source)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),LIMITS,
                LanguageMode.CPP17_ALGORITHM).compile(name,source);
        for(var outcome:report.outcomes().values())assertEquals(CppDifferentialHarness.Status.COMPILE_ERROR,outcome.status(),report::describe);
        var own=CppOwnLibraryReference.compile(temporary,source,LIMITS);
        assertEquals(CppDifferentialHarness.Status.COMPILE_ERROR,own.status(),own::toString);
    }
    static String resource(String name)throws Exception{
        try(var stream=CppLiveIntegralBulkTest.class.getResourceAsStream("/cpp/bulk-live-integral/"+name)){
            assertNotNull(stream,name);return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    static String normalize(String value){return value.replace("\r\n","\n");}
    static String rangeOracle(){
        int[] sizes={0,1,2,3,7,8,15,16,17,31,32,33};var out=new StringBuilder();
        for(int op=0;op<4;op++)for(int n:sizes){
            int[] before=new int[42];for(int i=0;i<before.length;i++)before[i]=i*13-201;
            int[] after=before.clone();boolean forward=op==0||op==2;int source=forward?4:1,target=forward?1:4;
            System.arraycopy(before,source,after,target,n);
            out.append(op).append(' ').append(n).append(' ').append(forward?target+n:target);
            for(int value:after)out.append(' ').append(value);out.append('\n');
        }
        return out.append("empty-ok\n").toString();
    }
    static String widthOracle(){
        var out=new StringBuilder();
        for(int id=0;id<12;id++)out.append(id).append(" 5").append(id==0?" 1 1 0 1 1 1\n":" 7 1 0 3 2 7\n");
        return out.toString();
    }
    static String vectorOracle(){
        var out=new StringBuilder();int[] starts={0,0,2,6,0,8},ends={0,1,5,8,8,8};
        for(int c=0;c<starts.length;c++){
            var values=sequence(8,1,0);values.subList(starts[c],ends[c]).clear();row(out,0,c,starts[c],values);
        }
        int[] oldSizes={3,8,4,2,5},newSizes={6,3,4,20,5};
        for(int c=0;c<oldSizes.length;c++)row(out,1,c,0,c==4?sequence(oldSizes[c],-1,-1):sequence(newSizes[c],3,1));
        int[] counts={1,6,9};for(int capacity=0;capacity<2;capacity++)for(int c=0;c<3;c++){
            var values=sequence(8,1,0);values.addAll(2,Collections.nCopies(counts[c],1));row(out,2,capacity*3+c,2,values);
        }
        return out.append("empty-ok\n").toString();
    }
    static ArrayList<Integer> sequence(int n,int step,int start){var result=new ArrayList<Integer>();for(int i=0;i<n;i++)result.add(start+i*step);return result;}
    static void row(StringBuilder out,int group,int id,int position,List<Integer> values){
        out.append(group).append(' ').append(id).append(' ').append(position).append(' ').append(values.size());
        for(int value:values)out.append(' ').append(value);out.append('\n');
    }
}
