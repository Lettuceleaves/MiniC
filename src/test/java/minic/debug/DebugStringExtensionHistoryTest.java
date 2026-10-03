package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.ir.instruction.ControlInstruction.TrapKind;
import minic.debug.DebugRuntime.RuntimeState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("stdlib-debug")
@Timeout(30)
final class DebugStringExtensionHistoryTest {
    @Test
    void externalAndHeaderOwnedStringCallsReuseCachedHistoryWithoutRepeatingEffects() {
        String source = """
                #include "string.mh"
                int main(void) {
                    char *source = "ab!cde";
                    char destination[7];
                    memset(destination, 0, 7);
                    memccpy(destination, source, '!', 6);
                    char *whole = strdup(source);
                    char *part = strndup(source, 2);
                    int fill = 'x';
                    memset_explicit(destination, fill++, 2);
                    return 0;
                }
                """;
        DebugApi api = new DebugApi(new SourceFile("debug-string-extension-history.mc", source));

        Transition copied = advanceUntil(api, (before, after) ->
                countStackPrefix(before, "616221") < countStackPrefix(after, "616221"));
        assertCachedRoundTrip(api, copied);

        Transition duplicated = advanceUntil(api, (before, after) ->
                before.heap().isEmpty() && after.heap().size() == 1);
        assertCachedRoundTrip(api, duplicated);

        Transition boundedDuplicate = advanceUntil(api, (before, after) ->
                before.heap().size() == 1 && after.heap().size() == 2);
        assertCachedRoundTrip(api, boundedDuplicate);

        Debugger.Context beforeExplicitWrite = advanceToCallInFunction(api, "memset_explicit");
        Debugger.Context afterExplicitWrite = api.next();
        assertCachedRoundTrip(api, new Transition(beforeExplicitWrite, afterExplicitWrite));

        while (api.canNext()) {
            api.next();
        }
        assertEquals(Debugger.Status.COMPLETED, api.current().stop().status(), api.current().stop().error());
        assertEquals(0, api.current().runtime().returnValue().integer());
    }

    private static Transition advanceUntil(
            DebugApi api,
            BiPredicate<RuntimeState, RuntimeState> changed
    ) {
        Debugger.Context before = api.current();
        for (int remaining = 20_000; api.canNext() && remaining > 0; remaining--) {
            Debugger.Context after = api.next();
            if (changed.test(before.runtime(), after.runtime())) {
                return new Transition(before, after);
            }
            before = after;
        }
        return fail("debugger did not reach the expected string side effect: " + api.current().stop());
    }

    private static long countStackPrefix(RuntimeState state, String prefix) {
        return state.stackMemory().stream().filter(block -> block.bytes().startsWith(prefix)).count();
    }

    private static Debugger.Context advanceToCallInFunction(DebugApi api, String function) {
        for (int remaining = 20_000; api.canNext() && remaining > 0; remaining--) {
            Debugger.Context context = api.next();
            if (context.stop().kind() == TrapKind.CALL
                    && context.stop().function().equals(function)) {
                return context;
            }
        }
        return fail("debugger did not reach a call in " + function + ": " + api.current().stop());
    }

    private static void assertCachedRoundTrip(DebugApi api, Transition transition) {
        assertTrue(transition.after().index() > transition.before().index());
        for (int cycle = 0; cycle < 10; cycle++) {
            assertSame(transition.before(), api.previous());
            assertSame(transition.after(), api.next());
        }
    }

    private record Transition(Debugger.Context before, Debugger.Context after) {
    }
}
