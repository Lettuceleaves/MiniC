package craken.debug;

import craken.compiler.SourceFile;
import craken.compiler.ir.IrResult;
import craken.compiler.ir.model.IrType;
import craken.debug.DebugLibraryCallResult.Returned;
import craken.debug.DebugRuntime.MemoryBlock;
import craken.debug.DebugRuntime.RuntimeState;
import craken.debug.DebugRuntime.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-debug")
@Tag("stdlib-parity")
@Timeout(30)
final class DebugLocaleLibraryParityTest {
    private DebugRuntime runtime;
    private DebugSystemLibrary library;

    @BeforeEach
    void setUp() {
        SourceFile source = new SourceFile("locale-provider.mc", "");
        runtime = new DebugRuntime(new DebugProgram(source, new IrResult(List.of())));
        library = new DebugSystemLibrary();
    }

    @Test
    void setlocaleQueriesAndSelectsTheCLocaleForEveryWindowsCategory() {
        long first = call("setlocale", integer(0), pointer(0)).integer();
        assertTrue(first != 0);
        assertEquals("C", runtime.readCString(first));
        assertEquals(first, call("setlocale", integer(0), pointer(0)).integer());

        long cLocale = cString("C");
        for (int category = 0; category <= 5; category++) {
            assertEquals(first, call("setlocale", integer(category), pointer(cLocale)).integer());
            assertEquals(first, call("setlocale", integer(category), pointer(0)).integer());
        }

        RuntimeState state = runtime.snapshot();
        assertEquals(List.of("C", "C", "C", "C", "C", "C"), state.localeCategories());
        assertEquals(14, state.localeCalls());
        assertEquals(6, state.localeSetCalls());
        assertEquals(first, state.localeNamePointer());
    }

    @Test
    void invalidCategoryAndNameReturnNullWithoutChangingLocaleOrErrno() {
        runtime.setErrno(73);
        long invalidName = cString("Craken_invalid_locale_name_!");
        assertEquals(0, call("setlocale", integer(-1), pointer(0)).integer());
        assertEquals(0, call("setlocale", integer(6), pointer(cString("C"))).integer());
        assertEquals(0, call("setlocale", integer(0), pointer(invalidName)).integer());

        long current = call("setlocale", integer(0), pointer(0)).integer();
        assertEquals("C", runtime.readCString(current));
        assertEquals(73, runtime.errno());
        assertEquals(List.of("C", "C", "C", "C", "C", "C"), runtime.localeCategories());
        assertEquals(0, runtime.localeSetCalls());
    }

    @Test
    void localeconvExposesTheCompleteAlignedMsvcrtCLocaleLayoutAtAStableAddress() {
        long first = call("localeconv").integer();
        long second = call("localeconv").integer();
        assertEquals(first, second);
        assertEquals(0, first % Long.BYTES);

        MemoryBlock block = runtime.libraryMemory().stream()
                .filter(candidate -> candidate.address() == first)
                .findFirst()
                .orElseThrow();
        assertEquals(88, block.size());
        assertEquals(88, block.initializedBytes());

        for (int field = 0; field < 10; field++) {
            long string = runtime.read(first + field * (long) Long.BYTES, IrType.POINTER).integer();
            assertEquals(field == 0 ? "." : "", runtime.readCString(string), "lconv pointer field " + field);
        }
        for (int field = 0; field < 8; field++) {
            assertEquals(127, runtime.read(first + 80L + field, IrType.CHAR).integer(),
                    "lconv char field " + field);
        }

        RuntimeState state = runtime.snapshot();
        assertEquals(first, state.localeConventionPointer());
        assertEquals(2, state.localeConventionCalls());
    }

    private Value call(String name, Value... arguments) {
        DebugLibraryCallResult result = library.invoke(name, runtime, List.of(arguments)).orElseThrow();
        return ((Returned) result).value();
    }

    private long cString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
        long address = runtime.allocateZeroed(bytes.length + 1, 1, "heap", "locale-test");
        for (int index = 0; index < bytes.length; index++) {
            runtime.writeByte(address + index, bytes[index]);
        }
        return address;
    }

    private Value integer(long value) {
        return Value.of(IrType.INT, value);
    }

    private Value pointer(long value) {
        return Value.of(IrType.POINTER, value);
    }
}
