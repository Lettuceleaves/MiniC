package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.ir.instruction.ControlInstruction.TrapKind;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("stdlib-debug")
@Timeout(30)
final class DebugLocaleHistoryTest {
    @Test
    void historyNavigationDoesNotRepeatLocaleCallsOrReallocateStaticState() {
        String source = """
                #include "locale.mh"
                int main(void) {
                    setlocale(LC_ALL, NULL);
                    setlocale(LC_NUMERIC, "C");
                    localeconv();
                    return 0;
                }
                """;
        DebugApi api = new DebugApi(new SourceFile("debug-locale-history.mc", source));

        Debugger.Context beforeQuery = advanceToCall(api);
        Debugger.Context afterQuery = api.next();
        assertEquals(1, afterQuery.runtime().localeCalls());
        assertEquals(0, afterQuery.runtime().localeSetCalls());
        long localeName = afterQuery.runtime().localeNamePointer();
        assertCachedRoundTrip(api, beforeQuery, afterQuery);

        Debugger.Context beforeSelection = advanceToCall(api);
        Debugger.Context afterSelection = api.next();
        assertEquals(2, afterSelection.runtime().localeCalls());
        assertEquals(1, afterSelection.runtime().localeSetCalls());
        assertEquals(localeName, afterSelection.runtime().localeNamePointer());
        assertCachedRoundTrip(api, beforeSelection, afterSelection);

        Debugger.Context beforeConvention = advanceToCall(api);
        Debugger.Context afterConvention = api.next();
        assertEquals(1, afterConvention.runtime().localeConventionCalls());
        long convention = afterConvention.runtime().localeConventionPointer();
        assertCachedRoundTrip(api, beforeConvention, afterConvention);
        assertEquals(convention, api.current().runtime().localeConventionPointer());
    }

    private static Debugger.Context advanceToCall(DebugApi api) {
        for (int remaining = 10_000; api.canNext() && remaining > 0; remaining--) {
            Debugger.Context context = api.next();
            if (context.stop().kind() == TrapKind.CALL) {
                return context;
            }
        }
        return fail("debugger did not reach a locale call: " + api.current().stop());
    }

    private static void assertCachedRoundTrip(
            DebugApi api,
            Debugger.Context before,
            Debugger.Context after
    ) {
        for (int cycle = 0; cycle < 10; cycle++) {
            assertSame(before, api.previous());
            assertSame(after, api.next());
        }
    }
}
