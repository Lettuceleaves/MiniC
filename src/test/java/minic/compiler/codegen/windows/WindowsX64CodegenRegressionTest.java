package minic.compiler.codegen.windows;

import minic.compiler.codegen.AssemblySource;
import minic.compiler.codegen.target.TargetPlatform;
import minic.compiler.ir.model.IrModule;
import minic.compiler.pipeline.MiniCompiler;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsX64CodegenRegressionTest {
    @Test
    void emitsWindowsX64AssemblyForRepresentativeRuntimeAndLanguageFeatures() {
        AssemblySource assembly = new MiniCompiler().compile(new SourceFile("asm.mc", """
                extern int printf(char *fmt, ...);
                struct Pair { int left; int right; };
                int main() {
                    struct Pair p;
                    p.left = 1;
                    p.right = 2;
                    int values[2];
                    values[0] = p.left;
                    values[1] = p.right;
                    printf("sum=%d\\n", values[0] + values[1]);
                    return values[0] < values[1] ? values[1] : values[0];
                }
                """)).assemblySourceOptional().orElseThrow();

        assertThat(assembly.targetPlatform()).isEqualTo(TargetPlatform.WINDOWS_X86_64);
        assertThat(assembly.entrySymbol()).isEqualTo("minic$entry");
        assertThat(assembly.text()).contains("PUBLIC minic$entry", "EXTERN printf:PROC", "main PROC", "call printf", "END");
    }

    @Test
    void advancesCodegenStepStateThroughAssemblyLines() {
        IrModule module = new MiniCompiler()
                .compile(new SourceFile("step.mc", "int main() { return 3; }"))
                .irModuleOptional()
                .orElseThrow();
        WindowsX64CodegenStepState state = new WindowsX64CodegenStepState(module);

        assertThat(state.canNext()).isTrue();
        WindowsX64AssemblyLine first = state.next();
        assertThat(first.kind()).isEqualTo(WindowsX64AssemblyLineKind.HEADER);
        assertThat(first.text()).contains("target");
        while (state.canNext()) {
            state.next();
        }

        assertThat(state.work().assemblyLineData()).extracting(WindowsX64AssemblyLine::text).contains("END");
        assertThat(state.toAssemblySource().text()).contains("main PROC", "END");
    }
}
