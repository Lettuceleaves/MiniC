package minic.compiler.ir;

import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
final class ControlFlowNestingTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("programs")
    void nativeAndDebugUseTheNearestEligibleControlFlowTarget(String name, String body, int expected) {
        var source = new SourceFile("control-flow-" + name + "-" + UUID.randomUUID() + ".mc",
                "int main() {\n" + body + "\n}");
        var debug = new DebugApi(source);
        int steps = 0;
        while (debug.canNext() && steps++ < 1000) debug.next();
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop().error());

        var compiler = new CompilerApi(source);
        compiler.run();
        var execution = compiler.stages().stream().filter(ExecutableRunner.class::isInstance)
                .map(ExecutableRunner.class::cast).findFirst().orElseThrow();
        assertTrue(execution.succeeded(), () -> compiler.stages().stream().flatMap(s -> s.errors().stream()).toList().toString());
        assertAll(
                () -> assertEquals(expected, debug.current().runtime().termination().status(), "debug exit"),
                () -> assertEquals(expected, execution.result().exitCode(), "native exit")
        );
    }

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("switch-in-for", """
                        int result = 0;
                        for (int i = 0; i < 3; i++) {
                            switch (i) {
                                case 1: result += 2; break;
                                default: result += 1; break;
                            }
                            result += 3;
                        }
                        return result;
                        """, 13),
                Arguments.of("switch-in-while", """
                        int result = 0; int i = 0;
                        while (i < 3) {
                            i++;
                            switch (i) { default: result += 2; break; }
                            result += 3;
                        }
                        return result;
                        """, 15),
                Arguments.of("switch-in-do", """
                        int result = 0; int i = 0;
                        do {
                            i++;
                            switch (i) { default: result += 2; break; }
                            result += 3;
                        } while (i < 3);
                        return result;
                        """, 15),
                Arguments.of("loop-in-switch", """
                        int result = 0;
                        switch (1) {
                            case 1:
                                for (int i = 0; i < 3; i++) { result++; break; }
                                result += 6;
                                break;
                            default: result = 99;
                        }
                        return result;
                        """, 7),
                Arguments.of("continue-through-switch", """
                        int result = 0;
                        for (int i = 0; i < 3; i++) {
                            switch (i) {
                                case 1: continue;
                                default: result++; break;
                            }
                            result += 3;
                        }
                        return result;
                        """, 8),
                Arguments.of("continue-nearest-nested-loop", """
                        int result = 0;
                        for (int outer = 0; outer < 2; outer++) {
                            for (int inner = 0; inner < 3; inner++) {
                                switch (inner) { case 1: continue; default: result++; break; }
                                result++;
                            }
                            result += 5;
                        }
                        return result;
                        """, 18),
                Arguments.of("nested-switch", """
                        int result = 0;
                        switch (1) {
                            case 1:
                                switch (2) { case 2: result += 2; break; default: result = 99; }
                                result += 3;
                                break;
                            default: result = 98;
                        }
                        return result;
                        """, 5),
                Arguments.of("nested-switch-in-loop", """
                        int result = 0;
                        for (int i = 0; i < 2; i++) {
                            switch (1) {
                                case 1:
                                    switch (2) { case 2: result += 2; break; }
                                    result += 3;
                                    break;
                            }
                            result++;
                        }
                        return result;
                        """, 12)
        );
    }
}
