package minic.compiler.library;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static minic.compiler.library.LibraryBinding.NativeCallingConvention.WINDOWS_X64;
import static minic.compiler.library.LibraryBinding.NativeKind.DLL_IMPORT;
import static minic.compiler.library.LibraryBinding.RuntimeFamily.MSVCRT;
import static minic.compiler.library.LibrarySymbol.SymbolKind.FUNCTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-contract")
final class StdioCatalogTest {
    private static final Set<String> PUBLIC_FUNCTIONS = Set.of(
            "fgetc", "fputc", "ungetc", "fflush",
            "printf", "scanf", "getchar", "putchar", "puts",
            "sprintf", "sscanf", "remove", "rename"
    );

    @Test
    void everySupportedStdioFunctionUsesItsExactMsvcrtExport() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();

        for (String name : PUBLIC_FUNCTIONS) {
            LibraryBinding binding = catalog.binding(name).orElseThrow();
            assertEquals(name, binding.sourceName());
            assertEquals(name, binding.exportName(), name);
            assertEquals("msvcrt.dll", binding.dllName(), name);
            assertEquals(FUNCTION, binding.symbolKind(), name);
            assertEquals(MSVCRT, binding.runtimeFamily(), name);
            assertEquals(WINDOWS_X64, binding.callingConvention(), name);
            assertEquals(DLL_IMPORT, binding.nativeKind(), name);
        }
    }

    @Test
    void stdioHeaderPublishesTheSupportedStreamInterface() {
        String header = SystemLibraryCatalog.defaults().header("stdio.mh").orElseThrow().content();

        assertEquals(Set.of(
                "minic_iob_base",
                "fgetc", "fputc", "ungetc", "fflush",
                "printf", "scanf", "getchar", "putchar", "puts",
                "sprintf", "sscanf", "remove", "rename"
        ), declaredFunctions(header));
        assertTrue(header.contains("#define EOF (-1)"));
        assertTrue(header.contains("typedef struct __minic_file FILE;"));
        assertTrue(header.contains("#define stdin ((FILE *)minic_iob_base())"));
        assertTrue(header.contains("#define stdout ((FILE *)((char *)minic_iob_base() + 48))"));
        assertTrue(header.contains("#define stderr ((FILE *)((char *)minic_iob_base() + 96))"));
        assertTrue(header.contains("extern int getchar(void);"));
        assertTrue(header.contains("extern int putchar(int character);"));
        assertTrue(header.contains("extern int puts(const char *string);"));
        assertTrue(header.contains("extern int sprintf(char *buffer, const char *format, ...);"));
        assertTrue(header.contains("extern int sscanf(const char *buffer, const char *format, ...);"));
        assertTrue(header.contains("extern int remove(const char *filename);"));
        assertTrue(header.contains("extern int rename(const char *oldName, const char *newName);"));
    }

    @Test
    void snprintfRemainsDeferredInsteadOfAliasingTheIncompatiblePrivateExport() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();
        String header = catalog.header("stdio.mh").orElseThrow().content();

        assertFalse(declaredFunctions(header).contains("snprintf"));
        assertFalse(catalog.bindings().containsKey("snprintf"));
        assertFalse(catalog.bindings().containsKey("_snprintf"));
        assertTrue(header.contains("_snprintf has incompatible truncation semantics"));
    }

    private static Set<String> declaredFunctions(String header) {
        Pattern pattern = Pattern.compile("(?m)^extern\\s+[^;]+?\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^;]*\\);$");
        Matcher matcher = pattern.matcher(header);
        java.util.LinkedHashSet<String> functions = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            functions.add(matcher.group(1));
        }
        return Set.copyOf(functions);
    }
}
