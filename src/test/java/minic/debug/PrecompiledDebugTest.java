package minic.debug;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.ir.instruction.ControlInstruction.IrTrapInstruction;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-frontend")
@Timeout(10)
final class PrecompiledDebugTest {
    @Test
    void reusesValidatedIrWithoutRecompilationOrSharedRuntimeState() {
        var source = new SourceFile("already-compiled.cpp", """
                #include <stdio.h>
                int main() {
                    int value = 0;
                    scanf("%d", &value);
                    if (value > 0 and value < 10) printf("%d", value);
                    return 0;
                }
                """);
        var ir = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM).runToIr();
        var first = DebugApi.fromIr(source, ir, "3");
        var second = DebugApi.fromIr(source, ir, "4");
        while (first.canNext()) first.next();
        while (second.canNext()) second.next();
        assertEquals(Debugger.Status.COMPLETED, first.current().stop().status());
        assertEquals(Debugger.Status.COMPLETED, second.current().stop().status());
        assertEquals("3", first.current().runtime().stdout());
        assertEquals("4", second.current().runtime().stdout());
        var completed = first.current();
        first.previous();
        assertSame(completed, first.next());
        assertFalse(ir.functions().stream().flatMap(f -> f.blocks().stream())
                .flatMap(b -> b.instructions().stream()).anyMatch(IrTrapInstruction.class::isInstance));
    }
}
