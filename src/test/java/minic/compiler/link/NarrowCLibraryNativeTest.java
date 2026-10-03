package minic.compiler.link;

import minic.compiler.CompilerApi;
import minic.compiler.lexer.Lexer;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.SourceFile;
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
final class NarrowCLibraryNativeTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("nativeCases")
    void linksAndRunsEveryDirectNarrowLibraryFunction(NativeCase testCase) {
        String source = "#include \"" + testCase.header() + "\"\n"
                + "int main() {\n" + testCase.body() + "\n}\n";
        SourceFile sourceFile = new SourceFile("stdlib-native-" + testCase.symbol() + ".mc", source);
        CompilerApi session = new CompilerApi(sourceFile);

        session.runThrough(session.stage(Linker.class));

        assertTrue(session.stage(Linker.class).succeeded(), () -> testCase.symbol() + ": pre="
                + session.stage(Preprocessor.class).errors() + ", lex=" + session.stage(Lexer.class).errors()
                + ", parse=" + session.stage(Parser.class).errors() + ", semantic="
                + session.stage(SemanticAnalyzer.class).errors() + ", obj=" + session.stage(ObjBuilder.class).errors()
                + ", link=" + session.stage(Linker.class).errors());
        Map<String, Set<String>> imports = PeImportIntegrationTest.readImports(
                session.stage(Linker.class).peImage().orElseThrow().bytes()
        );
        assertEquals(Set.of(testCase.symbol()), imports.get("msvcrt.dll"), testCase.symbol());
        assertEquals(Set.of("ExitProcess"), imports.get("KERNEL32.dll"), testCase.symbol());

        var artifact = session.stage(Linker.class).result().executableArtifactOptional().orElseThrow();
        var executionStage = new ExecutableRunner();
        var execution = executionStage.run(sourceFile, artifact);
        assertTrue(executionStage.errors().isEmpty(), () -> testCase.symbol() + ": " + executionStage.errors());
        assertEquals("", execution.stderr(), testCase.symbol());
        assertEquals(0, execution.exitCode(), testCase.symbol());
    }

    static Stream<Arguments> nativeCases() {
        return Stream.of(
                nativeCase("isalnum", "ctype.mh", """
                        return isalnum('A') && isalnum('7') && !isalnum('-') ? 0 : 1;
                        """),
                nativeCase("isalpha", "ctype.mh", """
                        return isalpha('Z') && isalpha('a') && !isalpha('4') ? 0 : 1;
                        """),
                nativeCase("iscntrl", "ctype.mh", """
                        return iscntrl('\\n') && !iscntrl('A') ? 0 : 1;
                        """),
                nativeCase("isdigit", "ctype.mh", """
                        return isdigit('7') && !isdigit('a') ? 0 : 1;
                        """),
                nativeCase("isgraph", "ctype.mh", """
                        return isgraph('!') && !isgraph(' ') ? 0 : 1;
                        """),
                nativeCase("islower", "ctype.mh", """
                        return islower('a') && !islower('A') ? 0 : 1;
                        """),
                nativeCase("isprint", "ctype.mh", """
                        return isprint(' ') && isprint('A') && !isprint('\\n') ? 0 : 1;
                        """),
                nativeCase("ispunct", "ctype.mh", """
                        return ispunct('!') && !ispunct('A') ? 0 : 1;
                        """),
                nativeCase("isspace", "ctype.mh", """
                        return isspace(' ') && isspace('\\n') && !isspace('A') ? 0 : 1;
                        """),
                nativeCase("isupper", "ctype.mh", """
                        return isupper('A') && !isupper('a') ? 0 : 1;
                        """),
                nativeCase("isxdigit", "ctype.mh", """
                        return isxdigit('F') && isxdigit('9') && !isxdigit('G') ? 0 : 1;
                        """),
                nativeCase("tolower", "ctype.mh", """
                        return tolower('A') == 'a' && tolower('?') == '?' ? 0 : 1;
                        """),
                nativeCase("toupper", "ctype.mh", """
                        return toupper('a') == 'A' && toupper('?') == '?' ? 0 : 1;
                        """),
                nativeCase("memcpy", "string.mh", """
                        char destination[4];
                        void *result = memcpy(destination, "abc", 4);
                        return result == destination
                            && destination[0] == 'a' && destination[1] == 'b'
                            && destination[2] == 'c' && destination[3] == 0 ? 0 : 1;
                        """),
                nativeCase("memmove", "string.mh", """
                        char right[7];
                        right[0] = 'a'; right[1] = 'b'; right[2] = 'c';
                        right[3] = 'd'; right[4] = 'e'; right[5] = 'f'; right[6] = 0;
                        void *first = memmove(&right[2], &right[0], 4);
                        if (first != &right[2] || right[0] != 'a' || right[1] != 'b'
                                || right[2] != 'a' || right[3] != 'b'
                                || right[4] != 'c' || right[5] != 'd') return 1;
                        char left[7];
                        left[0] = 'a'; left[1] = 'b'; left[2] = 'c';
                        left[3] = 'd'; left[4] = 'e'; left[5] = 'f'; left[6] = 0;
                        void *second = memmove(&left[0], &left[2], 4);
                        return second == &left[0]
                            && left[0] == 'c' && left[1] == 'd'
                            && left[2] == 'e' && left[3] == 'f'
                            && left[4] == 'e' && left[5] == 'f' ? 0 : 2;
                        """),
                nativeCase("strcpy", "string.mh", """
                        char destination[8];
                        char *result = strcpy(destination, "abc");
                        return result == destination && destination[0] == 'a'
                            && destination[1] == 'b' && destination[2] == 'c'
                            && destination[3] == 0 ? 0 : 1;
                        """),
                nativeCase("strncpy", "string.mh", """
                        char destination[6];
                        destination[0] = 'x'; destination[1] = 'x'; destination[2] = 'x';
                        destination[3] = 'x'; destination[4] = 'x'; destination[5] = 'x';
                        char *result = strncpy(destination, "ab", 5);
                        return result == destination && destination[0] == 'a'
                            && destination[1] == 'b' && destination[2] == 0
                            && destination[3] == 0 && destination[4] == 0
                            && destination[5] == 'x' ? 0 : 1;
                        """),
                nativeCase("strcat", "string.mh", """
                        char destination[8];
                        destination[0] = 'a'; destination[1] = 0;
                        char *result = strcat(destination, "bc");
                        return result == destination && destination[0] == 'a'
                            && destination[1] == 'b' && destination[2] == 'c'
                            && destination[3] == 0 ? 0 : 1;
                        """),
                nativeCase("strncat", "string.mh", """
                        char destination[8];
                        destination[0] = 'a'; destination[1] = 0;
                        char *result = strncat(destination, "bcde", 2);
                        return result == destination && destination[0] == 'a'
                            && destination[1] == 'b' && destination[2] == 'c'
                            && destination[3] == 0 ? 0 : 1;
                        """),
                nativeCase("memcmp", "string.mh", """
                        return memcmp("abc", "abc", 3) == 0
                            && memcmp("abc", "abd", 3) < 0
                            && memcmp("abe", "abd", 3) > 0 ? 0 : 1;
                        """),
                nativeCase("strcmp", "string.mh", """
                        return strcmp("abc", "abc") == 0
                            && strcmp("abc", "abd") < 0
                            && strcmp("abe", "abd") > 0 ? 0 : 1;
                        """),
                nativeCase("strcoll", "string.mh", """
                        return strcoll("abc", "abc") == 0
                            && strcoll("abc", "abd") < 0
                            && strcoll("abe", "abd") > 0 ? 0 : 1;
                        """),
                nativeCase("strncmp", "string.mh", """
                        return strncmp("abc", "abd", 2) == 0
                            && strncmp("abc", "abd", 3) < 0
                            && strncmp("abe", "abd", 3) > 0
                            && strncmp("a", "b", 4294967296ULL) < 0 ? 0 : 1;
                        """),
                nativeCase("strxfrm", "string.mh", """
                        char destination[8];
                        unsigned long long length = strxfrm(destination, "abc", 8);
                        return length == 3 && destination[0] == 'a'
                            && destination[1] == 'b' && destination[2] == 'c'
                            && destination[3] == 0 ? 0 : 1;
                        """),
                nativeCase("memchr", "string.mh", """
                        char buffer[4];
                        buffer[0] = 'a'; buffer[1] = 'b'; buffer[2] = 'c'; buffer[3] = 0;
                        return memchr(buffer, 'b', 3) == &buffer[1]
                            && memchr(buffer, 'z', 3) == NULL ? 0 : 1;
                        """),
                nativeCase("strchr", "string.mh", """
                        char *value = "abcba";
                        return strchr(value, 'b') == &value[1]
                            && strchr(value, 0) == &value[5]
                            && strchr(value, 'z') == NULL ? 0 : 1;
                        """),
                nativeCase("strcspn", "string.mh", """
                        return strcspn("abc123", "123") == 3
                            && strcspn("abc123", "z") == 6 ? 0 : 1;
                        """),
                nativeCase("strpbrk", "string.mh", """
                        char *value = "abc123";
                        return strpbrk(value, "21") == &value[3]
                            && strpbrk(value, "z") == NULL ? 0 : 1;
                        """),
                nativeCase("strrchr", "string.mh", """
                        char *value = "abcba";
                        return strrchr(value, 'b') == &value[3]
                            && strrchr(value, 'z') == NULL ? 0 : 1;
                        """),
                nativeCase("strspn", "string.mh", """
                        return strspn("aaab", "a") == 3
                            && strspn("baaa", "a") == 0 ? 0 : 1;
                        """),
                nativeCase("strstr", "string.mh", """
                        char *value = "abcabc";
                        return strstr(value, "cab") == &value[2]
                            && strstr(value, "") == value
                            && strstr(value, "xyz") == NULL ? 0 : 1;
                        """),
                nativeCase("strtok", "string.mh", """
                        char value[6];
                        value[0] = 'a'; value[1] = ','; value[2] = 'b';
                        value[3] = ','; value[4] = 'c'; value[5] = 0;
                        char *first = strtok(value, ",");
                        char *second = strtok(NULL, ",");
                        char *third = strtok(NULL, ",");
                        char *end = strtok(NULL, ",");
                        return first != NULL && first[0] == 'a' && first[1] == 0
                            && second != NULL && second[0] == 'b' && second[1] == 0
                            && third != NULL && third[0] == 'c' && third[1] == 0
                            && end == NULL ? 0 : 1;
                        """),
                nativeCase("memset", "string.mh", """
                        char destination[5];
                        void *result = memset(destination, 'x', 4);
                        destination[4] = 0;
                        return result == destination && destination[0] == 'x'
                            && destination[1] == 'x' && destination[2] == 'x'
                            && destination[3] == 'x' && destination[4] == 0 ? 0 : 1;
                        """),
                nativeCase("strerror", "string.mh", """
                        char *first = strerror(2);
                        if (first == NULL || first[0] == 0) return 1;
                        char saved = first[0];
                        char *second = strerror(22);
                        if (second == NULL || second[0] == 0) return 2;
                        return saved != 0 ? 0 : 3;
                        """),
                nativeCase("strlen", "string.mh", """
                        return sizeof strlen("hello") == 8
                            && strlen("") == 0 && strlen("hello") == 5 ? 0 : 1;
                        """)
        );
    }

    private static Arguments nativeCase(String symbol, String header, String body) {
        return Arguments.of(new NativeCase(symbol, header, body));
    }

    record NativeCase(String symbol, String header, String body) {
        @Override
        public String toString() {
            return symbol;
        }
    }
}
