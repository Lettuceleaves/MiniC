package minic.compiler.library;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static minic.compiler.library.LibraryBinding.NativeCallingConvention.WINDOWS_X64;
import static minic.compiler.library.LibraryBinding.NativeKind.DLL_IMPORT;
import static minic.compiler.library.LibraryBinding.RuntimeFamily.MSVCRT;
import static minic.compiler.library.LibrarySymbol.SymbolKind.FUNCTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-contract")
final class StringC23CatalogTest {
    @Test
    void directlyExportedC23FunctionsUseVerifiedMsvcrtAliases() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();
        Map<String, String> expectedExports = Map.of(
                "memccpy", "_memccpy",
                "strdup", "_strdup"
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
    }

    @Test
    void headerPublishesTheFourC23InterfacesWithStandardQualifiedTypes() {
        String header = SystemLibraryCatalog.defaults().header("string.mh").orElseThrow().content();

        assertTrue(header.contains("#include \"stddef.mh\""));
        assertFalse(header.contains("#include \"stdlib.mh\""));
        assertTrue(header.contains(
                "extern void *memccpy(void * restrict destination, const void * restrict source, "
                        + "int character, size_t count);"
        ));
        assertTrue(header.contains("extern char *strdup(const char *string);"));
        assertTrue(header.contains("char *strndup(const char *string, size_t count) {"));
        assertTrue(header.contains(
                "void *memset_explicit(void *destination, int character, size_t count) {"
        ));
        assertFalse(header.contains("unsigned long long"), "string.h must spell the portable size_t name");
        assertFalse(header.contains("_memccpy"));
        assertFalse(header.contains("_strdup"));
    }

    @Test
    void headerOwnedAdaptersHaveNoInventedDllBindings() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();

        assertFalse(catalog.bindings().containsKey("strndup"));
        assertFalse(catalog.bindings().containsKey("memset_explicit"));
        LibraryBinding allocator = catalog.binding("minic_string_malloc").orElseThrow();
        assertEquals("malloc", allocator.exportName());
        assertEquals("msvcrt.dll", allocator.dllName());
    }
}
