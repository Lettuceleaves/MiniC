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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-native")
final class TimeNativeTest {
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @ParameterizedTest(name = "{0}")
    @MethodSource("timeCases")
    void runsEachTimeFunctionWithItsExactPeImports(TimeCase testCase) {
        SourceFile sourceFile = new SourceFile("stdlib-native-time-" + testCase.name() + ".mc", testCase.source());
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
        assertEquals(testCase.msvcrtExports(), imports.get("msvcrt.dll"), testCase.name());
        assertEquals(Set.of("ExitProcess"), imports.get("KERNEL32.dll"), testCase.name());
        assertEquals(Set.of("msvcrt.dll", "KERNEL32.dll"), imports.keySet(), testCase.name());

        var artifact = session.stage(Linker.class).result().executableArtifactOptional().orElseThrow();
        var executionStage = new ExecutableRunner(Duration.ofSeconds(3));
        var execution = executionStage.run(sourceFile, artifact);
        assertTrue(executionStage.errors().isEmpty(), executionStage.errors()::toString);
        assertEquals(0, execution.exitCode(), () -> testCase.name() + ": " + execution.stderr());
        assertEquals(testCase.stdout(), execution.stdout(), testCase.name());
        assertEquals("", execution.stderr(), testCase.name());
    }

    static Stream<Arguments> timeCases() {
        return Stream.of(
                timeCase("clock", """
                        #include "time.mh"
                        int main(void) {
                            clock_t first = clock();
                            long value = 0;
                            for (int i = 0; i < 10000; i = i + 1) value += i;
                            clock_t second = clock();
                            return sizeof(clock_t) == 4 && CLOCKS_PER_SEC == 1000L
                                && first != -1L && second != -1L && second >= first && value > 0 ? 0 : 1;
                        }
                        """, Set.of("clock"), ""),
                timeCase("difftime", """
                        #include "time.mh"
                        int main(void) {
                            double elapsed = difftime(5000000000LL, 1000000000LL);
                            return sizeof(time_t) == 8 && elapsed == 4000000000.0 ? 0 : 1;
                        }
                        """, Set.of("_difftime64"), ""),
                timeCase("time", """
                        #include "time.mh"
                        int main(void) {
                            time_t stored = -1LL;
                            time_t first = time(&stored);
                            time_t second = time(NULL);
                            return first == stored && first >= 0LL
                                && second >= first && second - first <= 2LL ? 0 : 1;
                        }
                        """, Set.of("_time64"), ""),
                timeCase("gmtime-static-storage", """
                        #include "time.mh"
                        int main(void) {
                            time_t epoch = 0LL;
                            time_t nextDay = 86400LL;
                            struct tm *first = gmtime(&epoch);
                            if (first == NULL || sizeof(struct tm) != 36) return 1;
                            if (first->tm_sec != 0 || first->tm_min != 0 || first->tm_hour != 0
                                    || first->tm_mday != 1 || first->tm_mon != 0 || first->tm_year != 70
                                    || first->tm_wday != 4 || first->tm_yday != 0 || first->tm_isdst != 0) return 2;
                            struct tm *second = gmtime(&nextDay);
                            return second != NULL && second == first && first->tm_mday == 2
                                && first->tm_wday == 5 && first->tm_yday == 1 ? 0 : 3;
                        }
                        """, Set.of("_gmtime64"), ""),
                timeCase("localtime-static-storage", """
                        #include "time.mh"
                        int main(void) {
                            time_t firstValue = 946684800LL;
                            time_t secondValue = 946771200LL;
                            struct tm *first = localtime(&firstValue);
                            if (first == NULL) return 1;
                            struct tm *second = localtime(&secondValue);
                            return second != NULL && first == second
                                && second->tm_sec >= 0 && second->tm_sec <= 60
                                && second->tm_min >= 0 && second->tm_min <= 59
                                && second->tm_hour >= 0 && second->tm_hour <= 23
                                && second->tm_mon >= 0 && second->tm_mon <= 11
                                && second->tm_wday >= 0 && second->tm_wday <= 6
                                && second->tm_yday >= 0 && second->tm_yday <= 365 ? 0 : 2;
                        }
                        """, Set.of("_localtime64"), ""),
                timeCase("mktime", """
                        #include "time.mh"
                        int main(void) {
                            struct tm value;
                            value.tm_sec = 56; value.tm_min = 34; value.tm_hour = 12;
                            value.tm_mday = 1; value.tm_mon = 0; value.tm_year = 100;
                            value.tm_wday = 0; value.tm_yday = 0; value.tm_isdst = -1;
                            time_t result = mktime(&value);
                            return result != -1LL && value.tm_sec == 56 && value.tm_min == 34
                                && value.tm_hour == 12 && value.tm_mday == 1 && value.tm_mon == 0
                                && value.tm_year == 100 && value.tm_wday >= 0 && value.tm_wday <= 6
                                && value.tm_yday >= 0 && value.tm_yday <= 365 ? 0 : 1;
                        }
                        """, Set.of("_mktime64"), ""),
                timeCase("strftime", """
                        #include "time.mh"
                        #include "stdio.mh"
                        int main(void) {
                            time_t epoch = 0LL;
                            struct tm *value = gmtime(&epoch);
                            if (value == NULL) return 1;
                            char tooSmall[4];
                            if (strftime(tooSmall, 4ULL, "%Y", value) != 0ULL) return 2;
                            char buffer[32];
                            unsigned long long written = strftime(
                                buffer, 32ULL, "%Y-%m-%d %H:%M:%S", value
                            );
                            if (written != 19ULL || buffer[19] != 0) return 3;
                            printf("%s", buffer);
                            return 0;
                        }
                        """, Set.of("_gmtime64", "printf", "strftime"), "1970-01-01 00:00:00")
        );
    }

    private static Arguments timeCase(
            String name,
            String source,
            Set<String> msvcrtExports,
            String stdout
    ) {
        return Arguments.of(new TimeCase(name, source, msvcrtExports, stdout));
    }

    record TimeCase(String name, String source, Set<String> msvcrtExports, String stdout) {
        @Override
        public String toString() {
            return name;
        }
    }
}
