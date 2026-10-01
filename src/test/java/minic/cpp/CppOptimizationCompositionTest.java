package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.CallInstruction.IrCallInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrCheckInitializedInstruction;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.ir.value.IrValue.IrConstant;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
final class CppOptimizationCompositionTest {
    @TempDir Path temporary;

    @Test void verifiedPassesComposeAndKeepSourceIrSeparate() {
        var source=new SourceFile("composition.cpp", """
                #include <stdio.h>
                int helper(int n){return (n+6)*7;}
                int main(){int ready=1;ready;printf("%d\\n",helper(2));return 0;}
                """);
        var api=new CompilerApi(source,LanguageMode.CPP17_ALGORITHM,OptimizationLevel.OPTIMIZED);
        IrResult original=api.runToIr();
        var functions=original.functions();
        var assembler=api.stages().stream().filter(Assembler.class::isInstance).map(Assembler.class::cast).findFirst().orElseThrow();
        api.runThrough(assembler);
        assertTrue(assembler.succeeded(),()->assembler.errors().toString());
        assertEquals(List.of("initialized-check-elimination","small-function-inlining","constant-propagation","dead-code-elimination"),
                assembler.optimizationResult().passNames());
        var optimized=assembler.input().irResult();
        var main=optimized.functions().stream().filter(f->optimized.displayName(f.name()).equals("main")).findFirst().orElseThrow();
        var instructions=main.blocks().stream().flatMap(b->b.instructions().stream()).toList();
        assertTrue(instructions.stream().noneMatch(IrBinaryInstruction.class::isInstance));
        assertTrue(instructions.stream().noneMatch(IrCheckInitializedInstruction.class::isInstance));
        var calls=instructions.stream().filter(IrCallInstruction.class::isInstance).map(IrCallInstruction.class::cast).toList();
        assertEquals(1,calls.size());
        assertEquals("printf",calls.getFirst().calleeName());
        assertEquals(new IrConstant(56),calls.getFirst().arguments().get(1));
        assertSame(functions,original.functions());
        assertTrue(original.functions().stream().flatMap(f->f.blocks().stream()).flatMap(b->b.instructions().stream())
                .anyMatch(IrCheckInitializedInstruction.class::isInstance));
        assertEquals(original.structLayouts(),optimized.structLayouts());
        assertEquals(original.displayNames(),optimized.displayNames());
    }

    @ParameterizedTest(name="{0}") @MethodSource("minic.cpp.CppConstantPropagationTest#programs")
    void defaultCombinedPipelineMatchesReferenceAndSourceDebug(String name,String source,String expected,boolean ignored) throws Exception {
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM,OptimizationLevel.OPTIMIZED)
                .run("combined-"+name,source,"");
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(outcome->assertEquals(expected,outcome.stdout().replace("\r\n","\n"),report::describe));
    }
}
