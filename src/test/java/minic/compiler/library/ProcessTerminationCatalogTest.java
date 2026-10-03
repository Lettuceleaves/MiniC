package minic.compiler.library;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static minic.compiler.library.LibraryBinding.NativeCallingConvention.WINDOWS_X64;
import static minic.compiler.library.LibraryBinding.NativeKind.DLL_IMPORT;
import static minic.compiler.library.LibraryBinding.RuntimeFamily.MSVCRT;
import static minic.compiler.library.LibraryBinding.RuntimeFamily.WINDOWS;
import static minic.compiler.library.LibrarySymbol.SymbolKind.FUNCTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-contract")
final class ProcessTerminationCatalogTest {
    @Test
    void terminationFunctionsHaveTheVerifiedMsvcrtBindings() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();
        Map<String, String> expectedExports = Map.of(
                "abort", "abort",
                "exit", "exit"
        );

        for (Map.Entry<String, String> expected : expectedExports.entrySet()) {
            LibraryBinding binding = catalog.binding(expected.getKey()).orElseThrow();
            assertEquals(expected.getKey(), binding.sourceName());
            assertEquals(expected.getValue(), binding.exportName());
            assertEquals("msvcrt.dll", binding.dllName());
            assertEquals(FUNCTION, binding.symbolKind());
            assertEquals(MSVCRT, binding.runtimeFamily());
            assertEquals(WINDOWS_X64, binding.callingConvention());
            assertEquals(DLL_IMPORT, binding.nativeKind());
        }

        LibraryBinding immediateExit = catalog.binding("minic_immediate_exit").orElseThrow();
        assertEquals("TerminateProcess", immediateExit.exportName());
        assertEquals("KERNEL32.dll", immediateExit.dllName());
        assertEquals(FUNCTION, immediateExit.symbolKind());
        assertEquals(WINDOWS, immediateExit.runtimeFamily());
        assertEquals(WINDOWS_X64, immediateExit.callingConvention());
        assertEquals(DLL_IMPORT, immediateExit.nativeKind());
    }

    @Test
    void stdlibExposesTheStandardExitApiWithoutPrivateCrtNames() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();
        String header = catalog.header("stdlib.mh").orElseThrow().content();

        assertTrue(header.contains("#define EXIT_SUCCESS 0"));
        assertTrue(header.contains("#define EXIT_FAILURE 1"));
        assertTrue(header.contains("extern void abort(void);"));
        assertTrue(header.contains("extern void exit(int status);"));
        assertTrue(header.contains("extern int minic_immediate_exit(long long processHandle, int status);"));
        assertTrue(header.contains("#define _Exit(status) minic_immediate_exit(-1LL, (status))"));
        assertFalse(header.contains("extern void _exit("));
        assertFalse(catalog.bindings().containsKey("_Exit"));
        assertFalse(catalog.bindings().containsKey("quick_exit"));
        assertFalse(catalog.bindings().containsKey("atexit"));
    }
}
