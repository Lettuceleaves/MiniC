package minic.compiler.ir.lowering;

import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.IrModule;
import minic.compiler.pipeline.MiniCompiler;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IrLowererRegressionTest {
    @Test
    void lowersProgramShapeArithmeticCallsAndRuntimeChecks() {
        IrModule module = compileToIr("""
                extern int printf(char *fmt, ...);
                int add(int a, int b) { return a + b; }
                int main() {
                    int x = add(1, 2);
                    printf("x=%d\\n", x);
                    return x / 1;
                }
                """);

        assertThat(module.findFunction("add")).isPresent();
        assertThat(module.findFunction("main")).isPresent();
        assertThat(module.externalFunctionNames()).contains("printf");
        assertThat(module.stringData()).isNotEmpty();
        assertThat(instructionText(module)).contains("IrCallInstruction", "IrCheckNonZeroInstruction", "IrReturnInstruction");
    }

    @Test
    void lowersControlFlowLoopsSwitchAndShortCircuitExpressions() {
        IrModule module = compileToIr("""
                int main() {
                    int x = 0;
                    while (x < 3) { x++; }
                    do { x--; } while (x > 1);
                    switch (x) { case 0: return 0; default: return x && 1 ? x : 0; }
                    return x;
                }
                """);

        assertThat(module.findFunction("main")).isPresent();
        assertThat(instructionText(module)).contains("IrBranchInstruction", "IrJumpInstruction", "IrBinaryInstruction");
    }

    @Test
    void lowersPointersArraysStructsFunctionPointersLayoutsAndShadowedLocals() {
        IrModule module = compileToIr("""
                struct Pair { int left; int right; };
                int inc(int value) { return value + 1; }
                int main() {
                    int (*fn)(int) = inc;
                    struct Pair p;
                    p.left = 1;
                    p.right = 2;
                    int values[2];
                    values[0] = p.left;
                    int *cursor = &values[0];
                    { int values = fn(*cursor); return values + p.right; }
                }
                """);

        assertThat(instructionText(module))
                .contains("IrElementAddressInstruction", "IrFieldAddressInstruction", "IrIndirectCallInstruction");
    }

    private static IrModule compileToIr(String source) {
        var result = new MiniCompiler().compile(new SourceFile("ir.mc", source));
        assertThat(result.diagnostics()).isEmpty();
        return result.irModuleOptional().orElseThrow();
    }

    private static String instructionText(IrModule module) {
        StringBuilder builder = new StringBuilder();
        module.functions().forEach(function -> function.blocks().forEach(block -> block.instructions().stream()
                .map(IrInstruction::getClass)
                .map(Class::getSimpleName)
                .forEach(name -> builder.append(name).append('\n'))));
        return builder.toString();
    }
}
