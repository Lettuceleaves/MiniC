package craken.debug;

import craken.compiler.SourceFile;
import craken.compiler.ir.IrResult;
import craken.compiler.ir.model.IrType;
import craken.debug.DebugLibraryCallResult.Returned;
import craken.debug.DebugRuntime.Value;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("stdlib-debug")
final class DebugLocaleBoundaryTest {
    @Test
    void emptyEnvironmentLocaleIsExplicitlyDeferredAndLeavesTheCLocaleUntouched() {
        DebugRuntime runtime = new DebugRuntime(new DebugProgram(
                new SourceFile("locale-empty-boundary.mc", ""),
                new IrResult(List.of())
        ));
        DebugSystemLibrary library = new DebugSystemLibrary();
        long empty = runtime.allocateZeroed(1, 1, "heap", "empty locale");
        runtime.setErrno(91);

        Value result = ((Returned) library.invoke(
                "setlocale",
                runtime,
                List.of(Value.of(IrType.INT, 0), Value.of(IrType.POINTER, empty))
        ).orElseThrow()).value();

        assertEquals(0, result.integer());
        assertEquals(91, runtime.errno());
        assertEquals(0, runtime.localeSetCalls());
        assertEquals(List.of("C", "C", "C", "C", "C", "C"), runtime.localeCategories());
    }
}
