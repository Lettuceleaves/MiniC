package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.model.IrType;
import minic.debug.DebugLibraryCallResult.Terminated;
import minic.debug.DebugRuntime.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Tag("stdlib-debug")
final class DebugTerminationLibraryProviderTest {
    private DebugRuntime runtime;
    private DebugSystemLibrary library;

    @BeforeEach
    void setUp() {
        SourceFile source = new SourceFile("termination-provider.mc", "");
        runtime = new DebugRuntime(new DebugProgram(source, new IrResult(List.of())), "");
        library = new DebugSystemLibrary();
    }

    @Test
    void exitPreservesTheRequestedStatus() {
        Terminated result = terminate("exit", List.of(Value.of(IrType.INT, -27)));

        assertEquals(-27, result.status());
        assertEquals("exit", result.reason());
    }

    @Test
    void immediateExitPreservesTheRequestedStatus() {
        Terminated result = terminate("minic_immediate_exit", List.of(
                Value.of(IrType.LONG_LONG, -1),
                Value.of(IrType.INT, 42)
        ));

        assertEquals(42, result.status());
        assertEquals("_Exit", result.reason());
    }

    @Test
    void abortUsesAStableNonZeroStatusAndReason() {
        Terminated result = terminate("abort", List.of());

        assertEquals(3, result.status());
        assertEquals("abort", result.reason());
    }

    private Terminated terminate(String name, List<Value> arguments) {
        DebugLibraryCallResult result = library.invoke(name, runtime, arguments).orElseThrow();
        return assertInstanceOf(Terminated.class, result);
    }
}
