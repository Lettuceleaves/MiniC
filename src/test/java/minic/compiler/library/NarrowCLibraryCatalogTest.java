package minic.compiler.library;

import minic.compiler.SourceFile;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.Preprocessor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

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

    static final Set<String> STRING_LEGACY_DIRECT_FUNCTIONS = Set.of(
            "memcpy", "memmove", "strcpy", "strncpy", "strcat", "strncat", "memcmp",
            "strcmp", "strcoll", "strncmp", "strxfrm", "memchr", "strchr", "strcspn",
            "strpbrk", "strrchr", "strspn", "strstr", "strtok", "memset", "strerror",
            "strlen"
    );

    static final Set<String> STRING_PUBLIC_FUNCTIONS = Set.of(
            "memcpy", "memmove", "memccpy", "strcpy", "strncpy", "strdup", "strndup",
            "strcat", "strncat", "memcmp", "strcmp", "strcoll", "strncmp", "strxfrm",
            "memchr", "strchr", "strcspn", "strpbrk", "strrchr", "strspn", "strstr",
            "strtok", "memset", "memset_explicit", "strerror", "strlen"
    );

    static final Set<String> STRING_HEADER_FUNCTIONS = union(
            STRING_PUBLIC_FUNCTIONS,
            Set.of("minic_string_malloc")
    );

    @Test
    void everySupportedNarrowLibraryFunctionHasAnMsvcrtBinding() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();
        LinkedHashSet<String> expected = new LinkedHashSet<>(CTYPE_DIRECT_FUNCTIONS);
        expected.addAll(STRING_LEGACY_DIRECT_FUNCTIONS);

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

        assertEquals(CTYPE_HEADER_FUNCTIONS, declaredFunctions("ctype.mh"));
        assertEquals(STRING_HEADER_FUNCTIONS, declaredFunctions("string.mh"));

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
    void stringHeaderUsesThePortableNameForTheWindowsX64SizeTypeContract() {
        String header = SystemLibraryCatalog.defaults().header("string.mh").orElseThrow().content();

        assertTrue(header.contains("#include \"stddef.mh\""));
        assertTrue(header.contains("extern size_t strlen(const char *string);"));
        assertFalse(header.contains("unsigned long long"),
                "string.h must name size_t instead of exposing its LLP64 representation");
        assertFalse(header.contains("__STDC_VERSION_STRING_H__"),
                "the partial header must not claim C23 type-generic search support");
    }

    private static Set<String> declaredFunctions(String headerName) {
        var preprocessed = new Preprocessor().preprocess(new SourceFile(
                "contract-" + headerName + ".mc",
                "#include \"" + headerName + "\"\n"
        ));
        assertTrue(preprocessed.diagnostics().isEmpty(), preprocessed.diagnostics()::toString);
        var lexed = new Lexer(preprocessed.sourceFile()).lex();
        assertTrue(lexed.diagnostics().isEmpty(), lexed.diagnostics()::toString);
        var parsed = new Parser(lexed.tokens()).parse();
        assertTrue(parsed.diagnostics().isEmpty(), parsed.diagnostics()::toString);
        return parsed.program().functions().stream()
                .map(function -> function.name())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        LinkedHashSet<String> values = new LinkedHashSet<>(left);
        values.addAll(right);
        return Set.copyOf(values);
    }
}
