package minic.compiler.library;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static minic.compiler.library.LibraryBinding.NativeCallingConvention.WINDOWS_X64;
import static minic.compiler.library.LibraryBinding.NativeKind.DLL_IMPORT;
import static minic.compiler.library.LibraryBinding.RuntimeFamily.MSVCRT;
import static minic.compiler.library.LibrarySymbol.SymbolKind.FUNCTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-contract")
final class LocaleCatalogTest {
    private static final Set<String> FUNCTIONS = Set.of("setlocale", "localeconv");

    @Test
    void localeFunctionsUseTheirExactMsvcrtExports() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();

        for (String name : FUNCTIONS) {
            LibraryBinding binding = catalog.binding(name).orElseThrow();
            assertEquals(name, binding.sourceName());
            assertEquals(name, binding.exportName());
            assertEquals("msvcrt.dll", binding.dllName());
            assertEquals(FUNCTION, binding.symbolKind());
            assertEquals(MSVCRT, binding.runtimeFamily());
            assertEquals(WINDOWS_X64, binding.callingConvention());
            assertEquals(DLL_IMPORT, binding.nativeKind());
        }
    }

    @Test
    void localeHeaderPublishesTheWindowsCategoryValuesAndFunctions() {
        String header = SystemLibraryCatalog.defaults().header("locale.mh").orElseThrow().content();

        assertTrue(header.contains("#define LC_ALL 0"));
        assertTrue(header.contains("#define LC_COLLATE 1"));
        assertTrue(header.contains("#define LC_CTYPE 2"));
        assertTrue(header.contains("#define LC_MONETARY 3"));
        assertTrue(header.contains("#define LC_NUMERIC 4"));
        assertTrue(header.contains("#define LC_TIME 5"));
        assertTrue(header.contains("extern char *setlocale(int category, const char *locale);"));
        assertTrue(header.contains("extern struct lconv *localeconv(void);"));
        assertEquals(FUNCTIONS, declaredFunctions(header));
    }

    @Test
    void structLconvHasTheCompleteMsvcrtFieldOrder() {
        String header = SystemLibraryCatalog.defaults().header("locale.mh").orElseThrow().content();
        Matcher struct = Pattern.compile("struct lconv \\{(?<body>.*?)\\};", Pattern.DOTALL).matcher(header);
        assertTrue(struct.find());

        Matcher fields = Pattern.compile("(?:char \\*|char )([a-z_]+)\\s*;").matcher(struct.group("body"));
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        while (fields.find()) {
            names.add(fields.group(1));
        }
        assertEquals(List.of(
                "decimal_point", "thousands_sep", "grouping", "int_curr_symbol",
                "currency_symbol", "mon_decimal_point", "mon_thousands_sep", "mon_grouping",
                "positive_sign", "negative_sign", "int_frac_digits", "frac_digits",
                "p_cs_precedes", "p_sep_by_space", "n_cs_precedes", "n_sep_by_space",
                "p_sign_posn", "n_sign_posn"
        ), names);
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
