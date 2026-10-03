package minic.compiler.library;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
final class TimeCatalogTest {
    private static final Map<String, String> EXPORTS = Map.of(
            "clock", "clock",
            "difftime", "_difftime64",
            "time", "_time64",
            "mktime", "_mktime64",
            "gmtime", "_gmtime64",
            "localtime", "_localtime64",
            "strftime", "strftime"
    );

    @Test
    void timeFunctionsUseTheVerifiedMsvcrtExportsAndAliases() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();

        for (Map.Entry<String, String> expected : EXPORTS.entrySet()) {
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
    void headerMatchesTheWindowsLlp64TimeAbi() {
        String header = SystemLibraryCatalog.defaults().header("time.mh").orElseThrow().content();

        assertTrue(header.contains("typedef long clock_t;"));
        assertTrue(header.contains("typedef long long time_t;"));
        assertTrue(header.contains("#define CLOCKS_PER_SEC 1000L"));
        assertTrue(header.contains("extern clock_t clock(void);"));
        assertTrue(header.contains("extern double difftime(time_t later, time_t earlier);"));
        assertTrue(header.contains("extern time_t time(time_t *destination);"));
        assertTrue(header.contains("extern time_t mktime(struct tm *timeValue);"));
        assertTrue(header.contains("extern struct tm *gmtime(time_t *timeValue);"));
        assertTrue(header.contains("extern struct tm *localtime(time_t *timeValue);"));
        assertTrue(header.contains("extern unsigned long long strftime("));
        assertEquals(EXPORTS.keySet(), declaredFunctions(header));

        for (String privateExport : Set.of(
                "_difftime64", "_time64", "_mktime64", "_gmtime64", "_localtime64")) {
            assertFalse(header.contains(privateExport), privateExport);
        }
    }

    @Test
    void structTmHasTheExactNineIntMsvcrtFieldOrder() {
        String header = SystemLibraryCatalog.defaults().header("time.mh").orElseThrow().content();
        Matcher matcher = Pattern.compile("struct tm \\{(?<body>.*?)\\};", Pattern.DOTALL).matcher(header);
        assertTrue(matcher.find());

        Matcher fieldMatcher = Pattern.compile("int\\s+(tm_[a-z]+)\\s*;").matcher(matcher.group("body"));
        java.util.ArrayList<String> fields = new java.util.ArrayList<>();
        while (fieldMatcher.find()) {
            fields.add(fieldMatcher.group(1));
        }
        assertEquals(java.util.List.of(
                "tm_sec", "tm_min", "tm_hour", "tm_mday", "tm_mon",
                "tm_year", "tm_wday", "tm_yday", "tm_isdst"
        ), fields);
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
