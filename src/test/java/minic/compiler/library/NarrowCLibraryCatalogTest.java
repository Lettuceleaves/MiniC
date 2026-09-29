package minic.compiler.library;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
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
final class NarrowCLibraryCatalogTest {
    static final Set<String> CTYPE_DIRECT_FUNCTIONS = Set.of(
            "isalnum", "isalpha", "iscntrl", "isdigit", "isgraph", "islower", "isprint",
            "ispunct", "isspace", "isupper", "isxdigit", "tolower", "toupper"
    );

    static final Set<String> CTYPE_PUBLIC_FUNCTIONS = Set.of(
            "isalnum", "isalpha", "isblank", "iscntrl", "isdigit", "isgraph", "islower",
            "isprint", "ispunct", "isspace", "isupper", "isxdigit", "tolower", "toupper"
    );

    static final Set<String> CTYPE_HEADER_FUNCTIONS = Set.of(
            "minic_isctype", "isalnum", "isalpha", "isblank", "iscntrl", "isdigit", "isgraph",
            "islower", "isprint", "ispunct", "isspace", "isupper", "isxdigit", "tolower", "toupper"
    );

    static final Set<String> STRING_DIRECT_FUNCTIONS = Set.of(
            "memcpy", "memmove", "strcpy", "strncpy", "strcat", "strncat", "memcmp",
            "strcmp", "strcoll", "strncmp", "strxfrm", "memchr", "strchr", "strcspn",
            "strpbrk", "strrchr", "strspn", "strstr", "strtok", "memset", "strerror",
            "strlen"
    );

    private static final Set<String> WINDOWS_SIZE_T_SIGNATURES = Set.of(
            "extern void *memcpy(void *destination, void *source, unsigned long long count);",
            "extern void *memmove(void *destination, void *source, unsigned long long count);",
            "extern char *strncpy(char *destination, char *source, unsigned long long count);",
            "extern char *strncat(char *destination, char *source, unsigned long long count);",
            "extern int memcmp(void *left, void *right, unsigned long long count);",
            "extern int strncmp(char *left, char *right, unsigned long long count);",
            "extern unsigned long long strxfrm(char *destination, char *source, unsigned long long count);",
            "extern void *memchr(void *buffer, int character, unsigned long long count);",
            "extern unsigned long long strcspn(char *string, char *reject);",
            "extern unsigned long long strspn(char *string, char *accept);",
            "extern void *memset(void *destination, int character, unsigned long long count);",
            "extern unsigned long long strlen(char *string);"
    );

    @Test
    void everySupportedNarrowLibraryFunctionHasAnMsvcrtBinding() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();
        LinkedHashSet<String> expected = new LinkedHashSet<>(CTYPE_DIRECT_FUNCTIONS);
        expected.addAll(STRING_DIRECT_FUNCTIONS);

        for (String sourceName : expected) {
            LibraryBinding binding = catalog.binding(sourceName).orElseThrow();
            assertEquals(sourceName, binding.exportName(), sourceName);
            assertEquals("msvcrt.dll", binding.dllName(), sourceName);
            assertEquals(FUNCTION, binding.symbolKind(), sourceName);
            assertEquals(MSVCRT, binding.runtimeFamily(), sourceName);
            assertEquals(WINDOWS_X64, binding.callingConvention(), sourceName);
            assertEquals(DLL_IMPORT, binding.nativeKind(), sourceName);
        }
    }

    @Test
    void compatibleHeadersExposeEveryStandardFunctionWithoutInventingAnIsblankImport() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();
        String ctype = catalog.header("ctype.mh").orElseThrow().content();
        String string = catalog.header("string.mh").orElseThrow().content();

        assertEquals(CTYPE_HEADER_FUNCTIONS, declaredFunctions(ctype));
        assertEquals(STRING_DIRECT_FUNCTIONS, declaredFunctions(string));

        assertTrue(ctype.contains("int isblank(int character) {"),
                "isblank must be implemented by the header-owned once-evaluating adapter");
        assertFalse(catalog.bindings().containsKey("isblank"));
        LibraryBinding adapter = catalog.binding("minic_isctype").orElseThrow();
        assertEquals("_isctype", adapter.exportName());
        assertEquals("msvcrt.dll", adapter.dllName());
        assertEquals(FUNCTION, adapter.symbolKind());
        assertEquals(MSVCRT, adapter.runtimeFamily());
        assertEquals(WINDOWS_X64, adapter.callingConvention());
        assertEquals(DLL_IMPORT, adapter.nativeKind());
    }

    @Test
    void stringHeaderUsesTheWindowsX64SizeTypeContract() {
        String header = SystemLibraryCatalog.defaults().header("string.mh").orElseThrow().content();

        assertEquals(13, Pattern.compile("unsigned long long").matcher(header).results().count());
        for (String signature : WINDOWS_SIZE_T_SIGNATURES) {
            assertTrue(header.contains(signature), signature);
        }
        assertFalse(
                Pattern.compile("\\blong\\b").matcher(header.replace("unsigned long long", "")).find(),
                "Windows x64 size_t must never collapse to LLP64 long"
        );
        assertEquals(
                1,
                Pattern.compile("extern unsigned long long strlen\\(char \\*string\\);")
                        .matcher(header)
                        .results()
                        .count(),
                "strlen must return the 64-bit Windows size_t representation"
        );
    }

    private static Set<String> declaredFunctions(String header) {
        Matcher matcher = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(").matcher(header);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return Set.copyOf(names);
    }
}
