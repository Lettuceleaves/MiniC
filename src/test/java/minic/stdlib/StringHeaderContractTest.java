package minic.stdlib;

import minic.compiler.SourceFile;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.preprocess.PreprocessResult;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-contract")
final class StringHeaderContractTest {
    private static final Set<String> C23_PUBLIC_FUNCTIONS = Set.of(
            "memcpy", "memmove", "memccpy", "strcpy", "strncpy", "strdup", "strndup",
            "strcat", "strncat", "memcmp", "strcmp", "strcoll", "strncmp", "strxfrm",
            "memchr", "strchr", "strcspn", "strpbrk", "strrchr", "strspn", "strstr",
            "strtok", "memset", "memset_explicit", "strerror", "strlen"
    );

    private static final Set<String> STANDARD_SIGNATURES = Set.of(
            "extern void *memcpy(void * restrict destination, const void * restrict source, size_t count);",
            "extern void *memmove(void *destination, const void *source, size_t count);",
            "extern void *memccpy(void * restrict destination, const void * restrict source, int character, size_t count);",
            "extern char *strcpy(char * restrict destination, const char * restrict source);",
            "extern char *strncpy(char * restrict destination, const char * restrict source, size_t count);",
            "extern char *strdup(const char *string);",
            "char *strndup(const char *string, size_t count) {",
            "extern char *strcat(char * restrict destination, const char * restrict source);",
            "extern char *strncat(char * restrict destination, const char * restrict source, size_t count);",
            "extern int memcmp(const void *left, const void *right, size_t count);",
            "extern int strcmp(const char *left, const char *right);",
            "extern int strcoll(const char *left, const char *right);",
            "extern int strncmp(const char *left, const char *right, size_t count);",
            "extern size_t strxfrm(char * restrict destination, const char * restrict source, size_t count);",
            "extern void *memchr(const void *buffer, int character, size_t count);",
            "extern char *strchr(const char *string, int character);",
            "extern size_t strcspn(const char *string, const char *reject);",
            "extern char *strpbrk(const char *string, const char *accept);",
            "extern char *strrchr(const char *string, int character);",
            "extern size_t strspn(const char *string, const char *accept);",
            "extern char *strstr(const char *string, const char *substring);",
            "extern char *strtok(char * restrict string, const char * restrict delimiters);",
            "extern void *memset(void *destination, int character, size_t count);",
            "void *memset_explicit(void *destination, int character, size_t count) {",
            "extern char *strerror(int errorNumber);",
            "extern size_t strlen(const char *string);"
    );

    @Test
    void exposesExactlyTheC17FunctionsPlusTheFourC23Additions() {
        HeaderModel header = parseHeader();
        Set<String> allFunctions = header.functions().keySet();
        Set<String> publicFunctions = allFunctions.stream()
                .filter(name -> !name.startsWith("minic_"))
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(26, C23_PUBLIC_FUNCTIONS.size());
        assertEquals(C23_PUBLIC_FUNCTIONS, publicFunctions);
        assertEquals(Set.of("minic_string_malloc"), allFunctions.stream()
                .filter(name -> name.startsWith("minic_"))
                .collect(Collectors.toUnmodifiableSet()));
        assertTrue(header.functions().get("strndup").hasBody());
        assertTrue(header.functions().get("memset_explicit").hasBody());
    }

    @Test
    void spellsEveryPublicFunctionWithItsStandardSizeConstAndRestrictContract() {
        String rawHeader = minic.compiler.library.SystemLibraryCatalog.defaults()
                .header("string.mh").orElseThrow().content();

        STANDARD_SIGNATURES.forEach(signature -> assertTrue(rawHeader.contains(signature), signature));
        assertTrue(rawHeader.contains("#include \"stddef.mh\""));
        assertFalse(rawHeader.contains("#include \"stdlib.mh\""));
        assertFalse(rawHeader.contains("unsigned long long"),
                "the public header must name size_t instead of exposing its LLP64 representation");

        HeaderModel header = parseHeader();
        FunctionDecl memcpy = header.functions().get("memcpy");
        assertRestrictedMutablePointer(memcpy.parameters().get(0).type());
        assertRestrictedConstPointer(memcpy.parameters().get(1).type());
        assertEquals(MiniType.UNSIGNED_LONG_LONG, memcpy.parameters().get(2).type());

        FunctionDecl memmove = header.functions().get("memmove");
        assertFalse(memmove.parameters().get(0).type().isRestrictQualified());
        assertFalse(memmove.parameters().get(1).type().isRestrictQualified());
        assertTrue(memmove.parameters().get(1).type().pointee().isConstQualified());

        FunctionDecl strtok = header.functions().get("strtok");
        assertRestrictedMutablePointer(strtok.parameters().get(0).type());
        assertRestrictedConstPointer(strtok.parameters().get(1).type());

        FunctionDecl strlen = header.functions().get("strlen");
        assertEquals(MiniType.UNSIGNED_LONG_LONG, strlen.returnType());
        assertTrue(strlen.parameters().getFirst().type().pointee().isConstQualified());
    }

    @Test
    void publishesSizeTAndNullWithoutClaimingTheDeferredTypeGenericSearchContract() {
        HeaderModel header = parseHeader();
        assertEquals(MiniType.UNSIGNED_LONG_LONG, header.typedefs().get("size_t"));
        assertEquals("((void *)0)", header.macros().get("NULL"));
        assertFalse(header.macros().containsKey("__STDC_VERSION_STRING_H__"),
                "the five qualifier-preserving C23 generic searches are not implemented");

        // These are the standard concrete declarations exposed when the C23 generic
        // interface is suppressed. MiniC does not yet pretend that their return type
        // automatically follows the const qualification of the first argument.
        for (String name : Set.of("memchr", "strchr", "strpbrk", "strrchr", "strstr")) {
            FunctionDecl function = header.functions().get(name);
            assertFalse(function.returnType().pointee().isConstQualified(), name);
            assertTrue(function.parameters().getFirst().type().pointee().isConstQualified(), name);
        }
    }

    private static void assertRestrictedMutablePointer(MiniType type) {
        assertTrue(type.isPointer());
        assertTrue(type.isRestrictQualified());
        assertFalse(type.pointee().isConstQualified());
    }

    private static void assertRestrictedConstPointer(MiniType type) {
        assertTrue(type.isPointer());
        assertTrue(type.isRestrictQualified());
        assertTrue(type.pointee().isConstQualified());
    }

    private static HeaderModel parseHeader() {
        PreprocessResult preprocessed = new Preprocessor().preprocess(new SourceFile(
                "string-header-contract.mc",
                "#include \"string.mh\"\n"
        ));
        assertTrue(preprocessed.diagnostics().isEmpty(), preprocessed.diagnostics()::toString);
        var lexed = new Lexer(preprocessed.sourceFile()).lex();
        assertTrue(lexed.diagnostics().isEmpty(), lexed.diagnostics()::toString);
        var parsed = new Parser(lexed.tokens()).parse();
        assertTrue(parsed.diagnostics().isEmpty(), parsed.diagnostics()::toString);

        LinkedHashMap<String, FunctionDecl> functions = new LinkedHashMap<>();
        parsed.program().functions().forEach(function -> functions.put(function.name(), function));
        LinkedHashMap<String, MiniType> typedefs = new LinkedHashMap<>();
        parsed.program().typedefs().forEach(typedef -> typedefs.put(typedef.name(), typedef.type()));
        LinkedHashMap<String, String> macros = new LinkedHashMap<>();
        preprocessed.macros().forEach(macro -> {
            if (macro.defined()) {
                macros.put(macro.name(), macro.replacement());
            } else {
                macros.remove(macro.name());
            }
        });
        return new HeaderModel(Map.copyOf(functions), Map.copyOf(typedefs), Map.copyOf(macros));
    }

    private record HeaderModel(
            Map<String, FunctionDecl> functions,
            Map<String, MiniType> typedefs,
            Map<String, String> macros
    ) {
    }
}
