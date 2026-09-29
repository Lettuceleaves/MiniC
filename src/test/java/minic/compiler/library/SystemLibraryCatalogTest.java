package minic.compiler.library;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static minic.compiler.library.LibraryBinding.NativeCallingConvention.WINDOWS_X64;
import static minic.compiler.library.LibraryBinding.NativeKind.DLL_IMPORT;
import static minic.compiler.library.LibraryBinding.RuntimeFamily.MSVCRT;
import static minic.compiler.library.LibrarySymbol.SymbolKind.FUNCTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SystemLibraryCatalogTest {
    @Test
    void loadsTheExistingCrtFunctionsAsStructuredBindings() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();

        assertTrue(catalog.bindings().keySet().containsAll(
                Set.of("ExitProcess", "malloc", "calloc", "free", "printf", "scanf", "abs")
        ));
        for (String sourceName : Set.of("malloc", "calloc", "free", "printf", "scanf", "abs")) {
            LibraryBinding binding = catalog.binding(sourceName).orElseThrow();
            assertEquals(sourceName, binding.exportName());
            assertEquals("msvcrt.dll", binding.dllName());
            assertEquals(FUNCTION, binding.symbolKind());
            assertEquals(MSVCRT, binding.runtimeFamily());
            assertEquals(WINDOWS_X64, binding.callingConvention());
            assertEquals(DLL_IMPORT, binding.nativeKind());
        }
    }

    @Test
    void parsesAnExplicitNativeExportAlias() {
        SystemLibraryCatalog catalog = load("""
                public_name=example.dll|native_export|FUNCTION|WINDOWS|WINDOWS_X64|DLL_IMPORT
                """);

        LibraryBinding binding = catalog.binding("public_name").orElseThrow();
        assertEquals("public_name", binding.sourceName());
        assertEquals("native_export", binding.exportName());
        assertEquals("example.dll", binding.dllName());
    }

    @Test
    void rejectsDuplicateSourceSymbols() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> load("""
                duplicate=first.dll|first|FUNCTION|WINDOWS|WINDOWS_X64|DLL_IMPORT
                duplicate=second.dll|second|FUNCTION|WINDOWS|WINDOWS_X64|DLL_IMPORT
                """));

        assertTrue(exception.getMessage().contains("duplicate source symbol: duplicate"));
    }

    @Test
    void rejectsMalformedCatalogRowsAndBindings() {
        IllegalStateException rowException = assertThrows(IllegalStateException.class, () -> load("""
                broken=missing-fields.dll
                """));
        assertTrue(rowException.getMessage().contains("expected dll|export|symbolKind"));

        assertThrows(IllegalArgumentException.class, () -> new LibraryBinding(
                "bad-name",
                "example.dll",
                "native",
                FUNCTION,
                LibraryBinding.RuntimeFamily.WINDOWS,
                WINDOWS_X64,
                DLL_IMPORT
        ));
        assertThrows(IllegalArgumentException.class, () -> new LibraryBinding(
                "valid_name",
                "C:\\Windows\\example.dll",
                "native",
                FUNCTION,
                LibraryBinding.RuntimeFamily.WINDOWS,
                WINDOWS_X64,
                DLL_IMPORT
        ));

        LibraryBinding binding = binding("same");
        assertThrows(
                IllegalArgumentException.class,
                () -> SystemLibraryCatalog.ofBindings(List.of(binding, binding))
        );
    }

    private static LibraryBinding binding(String name) {
        return new LibraryBinding(
                name,
                "example.dll",
                name,
                FUNCTION,
                LibraryBinding.RuntimeFamily.WINDOWS,
                WINDOWS_X64,
                DLL_IMPORT
        );
    }

    private static SystemLibraryCatalog load(String content) {
        return SystemLibraryCatalog.load(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                "test catalog"
        );
    }
}
