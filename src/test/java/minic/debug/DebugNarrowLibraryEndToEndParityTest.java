package minic.debug;

import minic.compiler.CompilerApi;
import minic.compiler.lexer.Lexer;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("stdlib-debug")
@Tag("stdlib-parity")
@Timeout(30)
final class DebugNarrowLibraryEndToEndParityTest {
    @Test
    void executesTheSameCtypeAndStringProgramNativelyAndInTheDebugger() {
        String source = """
                #include "ctype.mh"
                #include "string.mh"

                int next_ctype_character(int *calls, int character) {
                    *calls += 1;
                    return character;
                }

                int main() {
                    if (!isalnum('A') || !isalnum('7') || isalnum('-')) return 1;
                    if (!isalpha('z') || isalpha('4') || !iscntrl('\\n')) return 2;
                    if (!isdigit('8') || !isgraph('!') || isgraph(' ')) return 3;
                    if (!islower('a') || !isprint(' ') || !ispunct('?')) return 4;
                    if (!isspace('\\r') || !isupper('Q') || !isxdigit('f')) return 5;
                    if (tolower('A') != 'a' || toupper('z') != 'Z') return 6;
                    int blank_calls = 0;
                    if (!isblank(next_ctype_character(&blank_calls, '\\t')) || blank_calls != 1
                            || isblank(next_ctype_character(&blank_calls, '\\n')) || blank_calls != 2) return 7;

                    char first[32];
                    char second[32];
                    memcpy(first, "abcdef", 7);
                    if (memcmp(first, "abcdef", 7) != 0) return 10;
                    memmove(&first[2], &first[0], 4);
                    if (strcmp(first, "ababcd") != 0) return 11;
                    memset(second, 0, 32);
                    memset(second, 'x', 3);
                    if (strcmp(second, "xxx") != 0) return 12;
                    if (memchr(first, 'c', 6) != &first[4]) return 13;

                    strcpy(second, "ab");
                    strcat(second, "cd");
                    strncat(second, "efgh", 2);
                    if (strcmp(second, "abcdef") != 0) return 14;
                    strncpy(first, "xy", 5);
                    if (first[0] != 'x' || first[1] != 'y' || first[2] != 0
                            || first[3] != 0 || first[4] != 0) return 15;
                    if (strncmp("abc", "abd", 2) != 0 || strcmp("abc", "abd") >= 0) return 16;
                    if (strcoll("abe", "abd") <= 0) return 17;

                    char *banana = "banana";
                    char *abcde = "abcde";
                    char *abcabc = "abcabc";
                    if (strchr(banana, 'a') != &banana[1]) return 18;
                    if (strrchr(banana, 'a') != &banana[5]) return 19;
                    if (strpbrk(abcde, "dx") != &abcde[3]) return 20;
                    if (strstr(abcabc, "cab") != &abcabc[2]) return 21;
                    if (strspn("aaab", "a") != 3 || strcspn("abc,def", ",") != 3) return 22;
                    if (strlen("hello") != 5) return 23;
                    char transformed[8];
                    if (strxfrm(transformed, "abc", 8) != 3 || strcmp(transformed, "abc") != 0) return 24;

                    char tokens[6];
                    memcpy(tokens, "a,b,c", 6);
                    char *one = strtok(tokens, ",");
                    char *two = strtok(NULL, ",");
                    char *three = strtok(NULL, ",");
                    if (one[0] != 'a' || two[0] != 'b' || three[0] != 'c'
                            || strtok(NULL, ",") != NULL) return 25;
                    char *message = strerror(22);
                    if (message == NULL || message[0] == 0) return 26;
                    return 0;
                }
                """;
        SourceFile sourceFile = new SourceFile("narrow-library-parity.mc", source);

        CompilerApi nativeSession = new CompilerApi(sourceFile);
        nativeSession.runThrough(nativeSession.stage(Linker.class));
        assertTrue(nativeSession.stage(Linker.class).succeeded(), () -> "stage=" + nativeSession.currentStage()
                + ", preprocess=" + nativeSession.stage(Preprocessor.class).errors()
                + ", lexer=" + nativeSession.stage(Lexer.class).errors()
                + ", parser=" + nativeSession.stage(Parser.class).errors()
                + ", semantic=" + nativeSession.stage(SemanticAnalyzer.class).errors()
                + ", obj=" + nativeSession.stage(ObjBuilder.class).errors()
                + ", link=" + nativeSession.stage(Linker.class).errors());
        var nativeExecutionStage = new ExecutableRunner();
        var nativeExecution = nativeExecutionStage.run(
                sourceFile,
                nativeSession.stage(Linker.class).result().executableArtifactOptional().orElseThrow()
        );
        assertTrue(nativeExecutionStage.errors().isEmpty(), () -> nativeExecutionStage.errors().toString());

        DebugApi debug = new DebugApi(sourceFile);
        int remaining = 50_000;
        while (debug.canNext() && remaining-- > 0) {
            debug.next();
        }
        if (debug.canNext()) {
            fail("debugger did not complete within the step budget");
        }

        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop().error());
        assertEquals(nativeExecution.exitCode(), (int) debug.current().runtime().returnValue().integer());
        assertEquals(nativeExecution.stdout().replace("\r\n", "\n"), debug.current().runtime().stdout());
        assertEquals(0, nativeExecution.exitCode());
    }
}
