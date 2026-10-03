package minic.debug;

import minic.compiler.CompilerApi;
import minic.compiler.ir.IrLowerer;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.SourceFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-debug")
final class DebugAssertLibraryTest {
    @Test
    void failedAssertionReportsSourceAndTerminatesWithoutExecutingFollowingCode() {
        SourceFile source = new SourceFile("assert-debug.mc", "#include \"assert.mh\"\n#include \"stdio.mh\"\n"
                + "int main() { assert(2 + 2 == 5); printf(\"unreachable\"); return 0; }\n");
        CompilerApi compilation = new CompilerApi(source);
        compilation.runToIr();
        assertTrue(compilation.stage(IrLowerer.class).succeeded(), () -> "parse=" + compilation.stage(Parser.class).errors()
                + ", semantic=" + compilation.stage(SemanticAnalyzer.class).errors());
        DebugApi debugger = new DebugApi(source);

        while (debugger.canNext()) {
            debugger.next();
        }

        DebugRuntime.RuntimeState runtime = debugger.current().runtime();
        assertEquals("", runtime.stdout());
        assertTrue(runtime.stderr().contains("2 + 2 == 5"), runtime.stderr());
        assertTrue(runtime.stderr().contains("assert-debug.mc"), runtime.stderr());
        assertTrue(runtime.stderr().contains("line 3"), runtime.stderr());
        assertEquals(DebugRuntime.TerminationKind.EXITED, runtime.termination().kind());
        assertEquals(3, runtime.termination().status());
        assertEquals("assert", runtime.termination().detail());
    }
}
