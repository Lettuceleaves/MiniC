package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.ir.instruction.ControlInstruction.TrapKind;
import minic.debug.DebugRuntime.TerminationKind;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("stdlib-debug")
@Timeout(30)
final class DebugTerminationHistoryTest {
    @ParameterizedTest(name = "{0} terminates once with status {1}")
    @MethodSource("terminationCases")
    void terminationCreatesOneCachedFinalContext(
            String function,
            int expectedStatus,
            String expectedReason,
            String invocation
    ) {
        String source = """
                #include "stdlib.mh"
                #include "stdio.mh"
                int main() {
                    %s;
                    printf("after\\n");
                    return 99;
                }
                """.formatted(invocation);
        DebugApi api = new DebugApi(new SourceFile("debug-" + function + ".mc", source));

        Debugger.Context beforeTermination = advanceToCall(api);
        Debugger.Context terminated = api.next();

        assertEquals(beforeTermination.index() + 1, terminated.index());
        assertEquals(Debugger.Status.COMPLETED, terminated.stop().status());
        assertEquals(TerminationKind.EXITED, terminated.runtime().termination().kind());
        assertEquals(expectedStatus, terminated.runtime().termination().status());
        assertEquals(expectedReason, terminated.runtime().termination().detail());
        assertEquals("", terminated.runtime().stdout());
        assertFalse(api.canNext());

        for (int cycle = 0; cycle < 10; cycle++) {
            assertSame(beforeTermination, api.previous());
            assertSame(terminated, api.next());
            assertEquals("", api.current().runtime().stdout());
            assertEquals(expectedStatus, api.current().runtime().termination().status());
        }
    }

    private static Debugger.Context advanceToCall(DebugApi api) {
        for (int remaining = 10_000; api.canNext() && remaining > 0; remaining--) {
            Debugger.Context context = api.next();
            if (context.stop().kind() == TrapKind.CALL) {
                return context;
            }
        }
        return fail("debugger did not reach the termination call: " + api.current().stop());
    }

    private static Stream<Arguments> terminationCases() {
        return Stream.of(
                Arguments.of("exit", -27, "exit", "exit(-27)"),
                Arguments.of("_Exit", 42, "_Exit", "_Exit(42)"),
                Arguments.of("abort", 3, "abort", "abort()")
        );
    }
}
