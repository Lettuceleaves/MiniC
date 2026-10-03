package minic.compiler;

import minic.compiler.execute.ExecutableRunner;
import minic.compiler.execute.ExecutionResult;
import minic.compiler.link.Linker;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 端到端用例：完整流水线生成 PE 并本机运行；programs() 中的程序还会在调试器中解释执行。 */
final class EndToEndTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("programs")
    void nativeAndDebuggerRunsReturnZero(String name, String source) {
        SourceFile file = new SourceFile(name + ".mc", source);
        ExecutionResult execution = runNative(file, "");
        assertEquals(0, execution.exitCode(), execution::stderr);

        DebugApi api = new DebugApi(file);
        for (int budget = 50_000; api.canNext() && budget > 0; budget--) api.next();
        assertFalse(api.canNext(), "debugger did not complete within the step budget");
        assertEquals(Debugger.Status.COMPLETED, api.current().stop().status(), api.current().stop().error());
        assertEquals(0, api.current().runtime().returnValue().integer());
    }

    @Test
    void linksTheCrtAndExchangesStandardStreams() {
        SourceFile file = new SourceFile("crt.mc", """
                #include <stdlib.h>
                #include <stdio.h>
                int main() {
                    int count = 0;
                    double ratio = 0.0;
                    char word[16];
                    if (scanf("%d %lf %15s", &count, &ratio, word) != 3) return 20;
                    int *values = calloc(count, sizeof(int));
                    if (values == NULL) return 21;
                    int sum = 0;
                    for (int i = 0; i < count; i = i + 1) { values[i] = i + 1; sum += values[i]; }
                    free(values);
                    long long big = 5000000000LL;
                    float half = 1.5f;
                    char tag = 'A';
                    printf("%I64d %.1f %d %s %.2f %d\\n", big, half, tag, word, ratio, abs(-sum));
                    return 0;
                }
                """);
        ExecutionResult execution = runNative(file, "4 1.25 ok\n");
        assertEquals("", execution.stderr());
        assertEquals(0, execution.exitCode());
        assertEquals("5000000000 1.5 65 ok 1.25 10\r\n", execution.stdout());
    }

    @Test
    void passesRegisterAndStackArgumentsThroughStdarg() {
        SourceFile file = new SourceFile("stdarg.mc", """
                #include "stdarg.mh"
                int inspect(int marker, ...) {
                    va_list arguments;
                    va_list copied;
                    va_start(arguments, marker);
                    va_copy(copied, arguments);
                    double real_value = va_arg(arguments, double);
                    int signed_value = va_arg(arguments, int);
                    long long wide_value = va_arg(arguments, long long);
                    int *pointer_value = va_arg(arguments, int *);
                    double copied_value = va_arg(copied, double);
                    va_end(copied);
                    va_end(arguments);
                    return real_value == 2.5 && copied_value == 2.5 && signed_value == -7
                        && wide_value == 5000000000LL && *pointer_value == 91 ? 0 : 1;
                }
                int main(void) {
                    int value = 91;
                    return inspect(0, 2.5, -7, 5000000000LL, &value);
                }
                """);
        ExecutionResult execution = runNative(file, "");
        assertEquals(0, execution.exitCode(), execution::stderr);
    }

    @Test
    void terminatesProgramsThatExceedTheWallClockLimit() {
        SourceFile file = new SourceFile("timeout.mc", "int main() { while (1) {} return 0; }");
        ExecutableRunner runner = new ExecutableRunner(Duration.ofMillis(200));
        ExecutionResult execution = runner.run(file, link(file), "");
        assertEquals("RUN002", runner.errors().getFirst().code());
        assertTrue(execution.exitCodeOptional().isEmpty());
    }

    private static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("cast-and-comma", """
                        int difference(int left, int right) { return left - right; }
                        int main(void) {
                            unsigned long long wide = 0x100000002ULL;
                            int narrowed = (int)wide;
                            int left = 0;
                            int right = 0;
                            int result = (left = 4, right = 1, difference((left, left + 1), right));
                            return narrowed == 2 && result == 4 ? 0 : 1;
                        }
                        """),
                Arguments.of("aggregates", """
                        enum Color { RED = 0x10, GREEN, BLUE = (GREEN << 1) + 2, };
                        struct Node;
                        struct Node { int value; struct Node *next; };
                        union Value { int integer; unsigned long long wide; };
                        struct Container {
                            struct { int x; int y; };
                            union { int whole; unsigned char low; };
                        };
                        int main(void) {
                            struct Node tail = { 2, NULL, };
                            struct Node head = { .next = &tail, .value = 1 };
                            int values[4] = { [3] = 9, [1] = 4, };
                            union Value value = { .wide = 42ULL, };
                            struct Container container = { { 4, 5 }, { 6 } };
                            container.x += 2;
                            if (head.next->value != 2 || values[1] != 4 || values[3] != 9) return 1;
                            if (sizeof(union Value) != 8 || value.wide != 42ULL) return 2;
                            if (container.x != 6 || container.y != 5 || container.low != 6) return 3;
                            return GREEN == 17 && BLUE == 36 ? 0 : 4;
                        }
                        """),
                Arguments.of("globals", """
                        extern int counter;
                        int counter = 4;
                        int values[4] = { 1, 2, [3] = 7 };
                        struct Pair { int left; int right; };
                        struct Pair pair = { .right = 5, .left = 3 };
                        int bump() { counter += pair.left; return counter; }
                        int main() {
                            if (bump() != 7) return 1;
                            values[2] = counter;
                            return values[0] + values[1] + values[2] + values[3] + pair.right == 22 ? 0 : 2;
                        }
                        """),
                Arguments.of("unicode-literals", """
                        int main() {
                            char *utf8 = u8"é";
                            unsigned short *wide = L"你\\u597d";
                            unsigned int *full = U"\\U0001F600";
                            return (unsigned char)utf8[0] == 0xC3 && (unsigned char)utf8[1] == 0xA9
                                && wide[0] == 0x4F60 && wide[1] == 0x597D && full[0] == 0x1F600 ? 0 : 1;
                        }
                        """),
                Arguments.of("integer-types", """
                        short echo_short(short value) { return value; }
                        unsigned long echo_ulong(unsigned long value) { return value; }
                        long long echo_long_long(long long value) { return value; }
                        int sum_short_args(short a, short b, short c, short d, short e, unsigned short f) {
                            return a + b + c + d + e + f;
                        }
                        int main() {
                            unsigned unsigned_int = 4294967295U;
                            unsigned long long all_bits = 18446744073709551615ULL;
                            short values[2ULL];
                            if (sizeof(short) != 2 || sizeof(long) != 4 || sizeof(long long) != 8) return 1;
                            if (sizeof(values) != 4) return 2;
                            if (echo_short(-1234) != -1234 || echo_ulong(4000000000UL) != 4000000000UL) return 3;
                            if (echo_long_long(-5000000000LL) != -5000000000LL) return 4;
                            if (unsigned_int / 2U != 2147483647U || (unsigned_int >> 31) != 1U) return 5;
                            if (all_bits / 3ULL != 6148914691236517205ULL || (all_bits >> 63) != 1ULL) return 6;
                            if (sum_short_args(1, 2, 3, 4, -5, 65535U) != 65540) return 7;
                            if (-1 < 1U || !(-1LL < 1U)) return 8;
                            unsigned short truncated = 65536U;
                            double widened = unsigned_int;
                            return truncated == 0U && widened == 4294967295.0 ? 0 : 9;
                        }
                        """),
                Arguments.of("narrow-stores", """
                        struct FloatPair { float value; float guard; };
                        struct BytePair { char value; char guard; };
                        int main(void) {
                            char characters[3] = { 11, 22, 33 };
                            char *middle = &characters[1];
                            *middle = 52;
                            if (characters[0] != 11 || characters[1] != 52 || characters[2] != 33) return 1;
                            struct BytePair bytes = { 12, 34 };
                            bytes.value = 56;
                            if (bytes.value != 56 || bytes.guard != 34) return 2;
                            short words[3] = { 111, 222, 333 };
                            words[1] = 4660;
                            if (words[0] != 111 || words[1] != 4660 || words[2] != 333) return 3;
                            struct FloatPair pair = { 1.0f, 3.0f };
                            pair.value = 1.5;
                            return pair.value == 1.5f && pair.guard == 3.0f ? 0 : 4;
                        }
                        """),
                Arguments.of("stl-map-and-algorithms", """
                        #include <map>
                        #include <string>
                        #include <vector>
                        #include <algorithm>
                        int main() {
                            // pair<const std::string, int> copies its const class-type member.
                            std::map<std::string, int> counts = {{"b", 2}, {"a", 1}};
                            counts.insert(std::map<std::string, int>::value_type("c", 3));
                            if (counts.size() != 3 || counts.begin()->first != "a" || counts["c"] != 3) return 1;
                            std::vector<int> values = {4, 9, 1, 9};
                            if (std::max_element(values.begin(), values.end()) - values.begin() != 1) return 2;
                            if (*std::min_element(values.begin(), values.end()) != 1) return 3;
                            return std::__gcd(12, 18) == 6 ? 0 : 4;
                        }
                        """)
        );
    }

    private static ExecutionResult runNative(SourceFile file, String standardInput) {
        ExecutableRunner runner = new ExecutableRunner();
        ExecutionResult execution = runner.run(file, link(file), standardInput);
        assertTrue(runner.errors().isEmpty(), runner.errors()::toString);
        return execution;
    }

    private static minic.compiler.link.ExecutableArtifact link(SourceFile file) {
        CompilerApi compiler = new CompilerApi(file);
        Linker linker = compiler.stage(Linker.class);
        compiler.runThrough(linker);
        assertTrue(linker.succeeded(), () -> compiler.stages().stream()
                .flatMap(stage -> stage.errors().stream()).toList().toString());
        return linker.result().executableArtifactOptional().orElseThrow();
    }
}
