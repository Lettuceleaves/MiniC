package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.MemoryInstruction.IrCheckInitializedInstruction;
import minic.compiler.ir.optimize.DeadCodeEliminationPass;
import minic.compiler.ir.optimize.IrOptimizationPipeline;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Native checks cover non-addressed scalar locals; debug byte initialization also tracks alias writes. */
@Tag("cpp-differential")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(90)
final class CppNativeAliasInitializationTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(LanguageMode.C, LanguageMode.CPP17_ALGORITHM).flatMap(mode -> Stream.of(
                Arguments.of(mode, "direct-pointer", "int main(){int value;int *p=&value;*p=4;printf(\"%d\\n\",value);return 0;}", "4\n"),
                Arguments.of(mode, "callee-write", "void fill(int *target){*target=5;}int main(){int value;fill(&value);printf(\"%d\\n\",value);return 0;}", "5\n"),
                Arguments.of(mode, "conditional-callee-write", "void fill(int *target,int enabled){if(enabled)*target=6;}int main(){int value;fill(&value,1);printf(\"%d\\n\",value);return 0;}", "6\n"),
                Arguments.of(mode, "forwarded-pointer-parameter", "void leaf(int *target){*target=7;}void middle(int *target){leaf(target);}void outer(int *target){middle(target);}int main(){int value;outer(&value);printf(\"%d\\n\",value);return 0;}", "7\n"),
                Arguments.of(mode, "pointer-to-pointer-parameter", "void fill(int **slot){**slot=8;}int main(){int value;int *p=&value;fill(&p);printf(\"%d\\n\",value);return 0;}", "8\n"),
                Arguments.of(mode, "external-sscanf-write", "int main(){int value;sscanf(\"9\",\"%d\",&value);printf(\"%d\\n\",value);return 0;}", "9\n"),
                Arguments.of(mode, "redeclared-alias-write", "void fill(int *target,int value){*target=value;}int main(){int total=0;for(int i=0;i<3;i++){int value;fill(&value,i+1);total+=value;}printf(\"%d\\n\",total);return 0;}", "6\n"),
                Arguments.of(mode, "unused-address-is-removable", "int main(){int value=7;&value;printf(\"%d\\n\",value);return 0;}", "7\n")
        ));
    }

    @ParameterizedTest(name="{0}: {1}") @MethodSource("programs")
    void aliasWritesDoNotTriggerFalseNativeFailures(LanguageMode mode, String name, String body, String expected) throws Exception {
        String text="#include <stdio.h>\n"+body;
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),mode).run(name,text,"");
        assertTrue(report.passed(),report::describe);
        for(var outcome:report.outcomes().values()) assertEquals(expected,normalize(outcome.stdout()),report::describe);

        var source=new SourceFile(name+".cpp",text);
        IrResult original=new CompilerApi(source,mode).runToIr();
        var functions=original.functions();
        long checks=checks(original);
        var optimized=runNative(source,original,true);
        assertEquals(0,optimized.exitCode(),optimized::stderr);
        assertEquals(expected,normalize(optimized.stdout()));
        assertSame(functions,original.functions());
        assertEquals(checks,checks(original),"Native policy must leave original source/debug IR intact");
    }

    @ParameterizedTest @EnumSource(LanguageMode.class)
    void nonAddressedUninitializedScalarStillFailsNatively(LanguageMode mode) throws Exception {
        var source=new SourceFile("uninitialized.mc","int main(){int value;return value;}");
        var ir=new CompilerApi(source,mode).runToIr();
        assertTrue(checks(ir)>0);
        assertEquals(101,runNative(source,ir,false).exitCode());
        assertEquals(101,runNative(source,ir,true).exitCode());
        assertDebugUninitialized(source,ir);
    }

    @ParameterizedTest @EnumSource(LanguageMode.class)
    void addressedUninitializedStorageRemainsCheckedByDebug(LanguageMode mode) {
        // This is source UB; neither G++ nor native machine contents are used as an oracle.
        var source=new SourceFile("addressed-uninitialized.mc","int main(){int value;int *p=&value;return *p;}");
        assertDebugUninitialized(source,new CompilerApi(source,mode).runToIr());
    }

    private BoundedProcess.Result runNative(SourceFile source,IrResult ir,boolean dce) throws Exception {
        Path directory=temporary.resolve(dce?"dce":"baseline");
        var pipeline=new IrOptimizationPipeline(dce?OptimizationLevel.OPTIMIZED:OptimizationLevel.BASELINE,
                dce?List.of(new DeadCodeEliminationPass()):List.of());
        var assembler=new Assembler(ir,pipeline);
        var object=new ObjBuilder(source,assembler,directory,"program");
        var linker=new Linker(source,object,directory,"program");
        new CompilerApi(List.of(assembler,object,linker)).runThrough(linker);
        assertTrue(linker.succeeded(),()->assembler.errors()+" / "+object.errors()+" / "+linker.errors());
        var result=BoundedProcess.run(List.of(directory.resolve("program.exe").toString()),temporary,"",Duration.ofSeconds(10),65536);
        assertFalse(result.timedOut(),result::stderr);
        assertFalse(result.outputExceeded(),result::stderr);
        return result;
    }
    private static void assertDebugUninitialized(SourceFile source,IrResult ir) {
        var debug=DebugApi.fromIr(source,ir,"");
        for(int steps=0;debug.canNext()&&steps<1000;steps++)debug.next();
        assertFalse(debug.canNext());
        assertEquals(Debugger.Status.FAILED,debug.current().stop().status());
        assertTrue(debug.current().stop().error().contains("uninitialized"),debug.current().stop()::error);
    }
    private static long checks(IrResult ir) {
        return ir.functions().stream().flatMap(f->f.blocks().stream()).flatMap(b->b.instructions().stream())
                .filter(IrCheckInitializedInstruction.class::isInstance).count();
    }
    private static String normalize(String text){return text.replace("\r\n","\n");}
}
