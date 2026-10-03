package minic.compiler.link;

import minic.compiler.lexer.Lexer;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.SourceFile;
import minic.compiler.CompilerApi;
import minic.compiler.execute.ExecutableRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-native")
final class StdlibErrnoNativeTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("behaviorCases")
    void linksOnlyTheExpectedExportsAndRunsTheNativeBehavior(NativeCase testCase) {
        runNativeCase(testCase);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("errnoFailureCases")
    void reportsConversionRangeFailuresThroughTheAssignableErrnoMacro(NativeCase testCase) {
        runNativeCase(testCase);
    }

    private static void runNativeCase(NativeCase testCase) {
        SourceFile sourceFile = new SourceFile("stdlib-native-" + testCase.name() + ".mc", testCase.source());
        CompilerApi session = new CompilerApi(sourceFile);

        session.runThrough(session.stage(Linker.class));

        assertTrue(session.stage(Linker.class).succeeded(), () -> testCase.name() + ": pre="
                + session.stage(Preprocessor.class).errors() + ", lex=" + session.stage(Lexer.class).errors()
                + ", parse=" + session.stage(Parser.class).errors() + ", semantic="
                + session.stage(SemanticAnalyzer.class).errors() + ", obj=" + session.stage(ObjBuilder.class).errors()
                + ", link=" + session.stage(Linker.class).errors());
        Map<String, Set<String>> imports = PeImportIntegrationTest.readImports(
                session.stage(Linker.class).peImage().orElseThrow().bytes()
        );
        assertEquals(testCase.imports(), imports, testCase.name());

        var artifact = session.stage(Linker.class).result().executableArtifactOptional().orElseThrow();
        var executionStage = new ExecutableRunner();
        var execution = executionStage.run(sourceFile, artifact);
        assertTrue(executionStage.errors().isEmpty(), () -> testCase.name() + ": " + executionStage.errors());
        assertEquals("", execution.stderr(), testCase.name());
        assertEquals(0, execution.exitCode(), testCase.name());
    }

    static Stream<Arguments> behaviorCases() {
        return Stream.of(
                nativeCase("realloc", stdlib("""
                        char *pointer = (char *)realloc(NULL, 4);
                        if (pointer == NULL) return 1;
                        pointer[0] = 'a'; pointer[1] = 'b'; pointer[2] = 'c'; pointer[3] = 0;
                        char *grown = (char *)realloc(pointer, 8);
                        if (grown == NULL) return 2;
                        return grown[0] == 'a' && grown[1] == 'b'
                            && grown[2] == 'c' && grown[3] == 0 ? 0 : 3;
                        """), "realloc"),
                nativeCase("atof", stdlib("""
                        return atof("12.5tail") == 12.5 ? 0 : 1;
                        """), "atof"),
                nativeCase("atoi", stdlib("""
                        return atoi("  -42tail") == -42 ? 0 : 1;
                        """), "atoi"),
                nativeCase("atol", stdlib("""
                        return sizeof(long) == 4 && atol("2147483647") == 2147483647L ? 0 : 1;
                        """), "atol"),
                nativeCase("atoll-alias", stdlib("""
                        return sizeof(long long) == 8 && atoll("4294967296") == 4294967296LL ? 0 : 1;
                        """), "_atoi64"),
                nativeCase("strtod", stdlib("""
                        char *end = NULL;
                        double value = strtod("12.5tail", &end);
                        return value == 12.5 && end[0] == 't' ? 0 : 1;
                        """), Map.of("ucrtbase.dll", Set.of("strtod", "_errno"), "msvcrt.dll", Set.of("_errno"))),
                nativeCase("strtol", stdlib("""
                        char *end = NULL;
                        long value = strtol("-7f!", &end, 16);
                        return sizeof(long) == 4 && value == -127L && end[0] == '!' ? 0 : 1;
                        """), "strtol"),
                nativeCase("strtoll-alias", stdlib("""
                        char *end = NULL;
                        long long value = strtoll("-4294967296!", &end, 10);
                        return value == -4294967296LL && end[0] == '!' ? 0 : 1;
                        """), "_strtoi64"),
                nativeCase("strtoul", stdlib("""
                        char *end = NULL;
                        unsigned long value = strtoul("ffffffff!", &end, 16);
                        return sizeof(unsigned long) == 4
                            && value == 4294967295UL && end[0] == '!' ? 0 : 1;
                        """), "strtoul"),
                nativeCase("strtoull-alias", stdlib("""
                        char *end = NULL;
                        unsigned long long value = strtoull("4294967296!", &end, 10);
                        return value == 4294967296ULL && end[0] == '!' ? 0 : 1;
                        """), "_strtoui64"),
                nativeCase("labs", stdlib("""
                        return sizeof(long) == 4 && labs(-2147483647L) == 2147483647L ? 0 : 1;
                        """), "labs"),
                nativeCase("llabs-alias", stdlib("""
                        return llabs(-4294967296LL) == 4294967296LL ? 0 : 1;
                        """), "_abs64"),
                nativeCase("rand", stdlib("""
                        int value = rand();
                        return value >= 0 && value <= RAND_MAX ? 0 : 1;
                        """), "rand"),
                nativeCase("srand-state", stdlib("""
                        srand(123U);
                        int first = rand();
                        srand(123U);
                        int second = rand();
                        return first == second ? 0 : 1;
                        """), "rand", "srand"),
                nativeCase("errno-lvalue-alias", errno("""
                        errno = 0;
                        errno = EDOM;
                        return errno == EDOM ? 0 : 1;
                        """), "_errno")
        );
    }

    static Stream<Arguments> errnoFailureCases() {
        return Stream.of(
                nativeCase("strtod-erange", stdlibAndErrno("""
                        errno = 0;
                        char *end = NULL;
                        double value = strtod("1e9999", &end);
                        return errno == ERANGE && end[0] == 0 && value > 0.0 ? 0 : 1;
                        """), Map.of("ucrtbase.dll", Set.of("strtod", "_errno"), "msvcrt.dll", Set.of("_errno"))),
                nativeCase("strtol-erange", stdlibAndErrno("""
                        errno = 0;
                        char *end = NULL;
                        long value = strtol("999999999999999999999", &end, 10);
                        return errno == ERANGE && end[0] == 0 && value == 2147483647L ? 0 : 1;
                        """), "_errno", "strtol"),
                nativeCase("strtoll-erange", stdlibAndErrno("""
                        errno = 0;
                        char *end = NULL;
                        long long value = strtoll("999999999999999999999999999999", &end, 10);
                        return errno == ERANGE && end[0] == 0
                            && value == 9223372036854775807LL ? 0 : 1;
                        """), "_errno", "_strtoi64"),
                nativeCase("strtoul-erange", stdlibAndErrno("""
                        errno = 0;
                        char *end = NULL;
                        unsigned long value = strtoul("999999999999999999999", &end, 10);
                        return errno == ERANGE && end[0] == 0 && value == 4294967295UL ? 0 : 1;
                        """), "_errno", "strtoul"),
                nativeCase("strtoull-erange", stdlibAndErrno("""
                        errno = 0;
                        char *end = NULL;
                        unsigned long long value = strtoull("999999999999999999999999999999", &end, 10);
                        return errno == ERANGE && end[0] == 0 && value > 0ULL ? 0 : 1;
                        """), "_errno", "_strtoui64")
        );
    }

    private static String stdlib(String body) {
        return program("#include \"stdlib.mh\"\n", body);
    }

    private static String errno(String body) {
        return program("#include \"errno.mh\"\n", body);
    }

    private static String stdlibAndErrno(String body) {
        return program("#include \"stdlib.mh\"\n#include \"errno.mh\"\n", body);
    }

    private static String program(String includes, String body) {
        return includes + "int main() {\n" + body + "\n}\n";
    }

    private static Arguments nativeCase(String name, String source, String... msvcrtExports) {
        return nativeCase(name, source, Map.of("msvcrt.dll", Set.of(msvcrtExports)));
    }

    /** Header adapters may import from UCRT in addition to (or instead of) MSVCRT. */
    private static Arguments nativeCase(String name, String source, Map<String, Set<String>> crtImports) {
        var imports = new java.util.HashMap<>(crtImports);
        imports.put("KERNEL32.dll", Set.of("ExitProcess"));
        return Arguments.of(new NativeCase(name, source, Map.copyOf(imports)));
    }

    record NativeCase(String name, String source, Map<String, Set<String>> imports) {
        @Override
        public String toString() {
            return name;
        }
    }
}
