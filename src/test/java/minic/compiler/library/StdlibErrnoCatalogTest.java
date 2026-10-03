package minic.compiler.library;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Map;
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
final class StdlibErrnoCatalogTest {
    private static final Map<String, String> SECOND_BATCH_EXPORTS = Map.ofEntries(
            Map.entry("realloc", "realloc"),
            Map.entry("atof", "atof"),
            Map.entry("atoi", "atoi"),
            Map.entry("atol", "atol"),
            Map.entry("atoll", "_atoi64"),
            Map.entry("strtol", "strtol"),
            Map.entry("strtoll", "_strtoi64"),
            Map.entry("strtoul", "strtoul"),
            Map.entry("strtoull", "_strtoui64"),
            Map.entry("labs", "labs"),
            Map.entry("llabs", "_abs64"),
            Map.entry("rand", "rand"),
            Map.entry("srand", "srand"),
            Map.entry("minic_errno_location", "_errno")
    );

    private static final Set<String> STDLIB_DECLARATIONS = Set.of(
            "malloc", "calloc", "realloc", "free", "atof", "atoi", "atol", "atoll",
            "strtol", "strtoll", "strtoul", "strtoull", "abs", "labs", "llabs",
            "rand", "srand", "abort", "exit", "minic_immediate_exit",
            "minic_ucrt_strtod", "minic_ucrt_strtof", "minic_ucrt_errno_location"
    );

    @Test
    void everySupportedEntryHasTheVerifiedMsvcrtBinding() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();

        for (Map.Entry<String, String> expected : SECOND_BATCH_EXPORTS.entrySet()) {
            LibraryBinding binding = catalog.binding(expected.getKey()).orElseThrow();
            assertEquals(expected.getKey(), binding.sourceName());
            assertEquals(expected.getValue(), binding.exportName(), expected.getKey());
            assertEquals("msvcrt.dll", binding.dllName(), expected.getKey());
            assertEquals(FUNCTION, binding.symbolKind(), expected.getKey());
            assertEquals(MSVCRT, binding.runtimeFamily(), expected.getKey());
            assertEquals(WINDOWS_X64, binding.callingConvention(), expected.getKey());
            assertEquals(DLL_IMPORT, binding.nativeKind(), expected.getKey());
        }
    }

    @Test
    void headersExposeOnlyStandardNamesAndTheMiniCInternalErrnoAccessor() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();
        String stdlib = catalog.header("stdlib.mh").orElseThrow().content();
        String errno = catalog.header("errno.mh").orElseThrow().content();

        assertEquals(STDLIB_DECLARATIONS, declaredFunctions(stdlib));
        assertEquals(Set.of("minic_errno_location"), declaredFunctions(errno));
        assertTrue(errno.contains("#define errno minic_errno_location()[0]"));
        assertTrue(errno.contains("#define EDOM 33"));
        assertTrue(errno.contains("#define ERANGE 34"));
        assertTrue(errno.contains("#define EILSEQ 42"));
        assertTrue(stdlib.contains("#define RAND_MAX 32767"));

        for (String privateExport : Set.of("_atoi64", "_strtoi64", "_strtoui64", "_abs64", "_errno(")) {
            assertFalse(stdlib.contains(privateExport), privateExport);
            assertFalse(errno.contains(privateExport), privateExport);
        }
    }

    @Test
    void stdlibDeclarationsPreserveTheWindowsLlp64Abi() {
        String header = SystemLibraryCatalog.defaults().header("stdlib.mh").orElseThrow().content();

        for (String signature : Set.of(
                "extern void *realloc(void *pointer, unsigned long long size);",
                "extern long atol(const char *string);",
                "extern long long atoll(const char *string);",
                "extern long strtol(const char *string, char **endPointer, int base);",
                "extern long long strtoll(const char *string, char **endPointer, int base);",
                "extern unsigned long strtoul(const char *string, char **endPointer, int base);",
                "extern unsigned long long strtoull(const char *string, char **endPointer, int base);",
                "extern long labs(long value);",
                "extern long long llabs(long long value);"
        )) {
            assertTrue(header.contains(signature), signature);
        }
    }

    @Test
    void strtofUsesAHeaderAdapterToTheRealUcrtFloatParserAndCopiesErrno() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();
        String header = catalog.header("stdlib.mh").orElseThrow().content();

        assertFalse(declaredFunctions(header).contains("strtof"));
        assertFalse(catalog.bindings().containsKey("strtof"));
        assertTrue(header.contains("float strtof(const char *string, char **endPointer)"));
        assertTrue(header.contains("if (conversion_errno != 0) errno = conversion_errno;"));
        LibraryBinding parser=catalog.binding("minic_ucrt_strtof").orElseThrow();
        assertEquals("ucrtbase.dll",parser.dllName());assertEquals("strtof",parser.exportName());
        assertEquals(LibraryBinding.RuntimeFamily.UCRT,parser.runtimeFamily());
        LibraryBinding error=catalog.binding("minic_ucrt_errno_location").orElseThrow();
        assertEquals("ucrtbase.dll",error.dllName());assertEquals("_errno",error.exportName());
    }

    @Test
    void strtodUsesAHeaderAdapterToTheUcrtParser() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();
        String header = catalog.header("stdlib.mh").orElseThrow().content();
        assertTrue(header.contains("extern double minic_ucrt_strtod(const char *string, char **endPointer);"));
        assertFalse(declaredFunctions(header).contains("strtod"));
        assertFalse(catalog.bindings().containsKey("strtod"));
        assertTrue(header.contains("double strtod(const char *string, char **endPointer)"));
        LibraryBinding parser = catalog.binding("minic_ucrt_strtod").orElseThrow();
        assertEquals("minic_ucrt_strtod", parser.sourceName());
        assertEquals("strtod", parser.exportName());
        assertEquals("ucrtbase.dll", parser.dllName());
        assertEquals(FUNCTION, parser.symbolKind());
        assertEquals(LibraryBinding.RuntimeFamily.UCRT, parser.runtimeFamily());
        assertEquals(WINDOWS_X64, parser.callingConvention());
        assertEquals(DLL_IMPORT, parser.nativeKind());
        LibraryBinding error = catalog.binding("minic_ucrt_errno_location").orElseThrow();
        assertEquals("ucrtbase.dll", error.dllName());
        assertEquals("_errno", error.exportName());
        assertEquals(LibraryBinding.RuntimeFamily.UCRT, error.runtimeFamily());
    }

    private static Set<String> declaredFunctions(String header) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        Pattern functionName = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
        for (String line : header.lines().filter(value -> value.startsWith("extern ")).toList()) {
            Matcher matcher = functionName.matcher(line);
            if (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return Set.copyOf(names);
    }
}
