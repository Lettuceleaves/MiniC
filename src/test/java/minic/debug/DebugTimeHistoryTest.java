package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.ir.instruction.ControlInstruction.TrapKind;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("stdlib-debug")
@Timeout(30)
final class DebugTimeHistoryTest {
    @Test
    void historyNavigationDoesNotReadOrAdvanceTheTimeSourceAgain() {
        String source = """
                extern long clock(void);
                extern long long time(long long *destination);
                int main(void) {
                    long ticks = clock();
                    long long now = 0;
                    time(&now);
                    return ticks == 321 && now == 1234567890LL ? 0 : 1;
                }
                """;
        CountingTimeSource timeSource = new CountingTimeSource();
        DebugApi api = new DebugApi(new Debugger(
                new SourceFile("debug-time-history.mc", source),
                "",
                timeSource
        ));

        Debugger.Context beforeClock = advanceToCall(api);
        Debugger.Context afterClock = api.next();
        assertEquals(1, timeSource.clockCalls);
        assertEquals(1, afterClock.runtime().clockReads());
        assertCachedRoundTrip(api, beforeClock, afterClock);
        assertEquals(1, timeSource.clockCalls);

        Debugger.Context beforeTime = advanceToCall(api);
        Debugger.Context afterTime = api.next();
        assertEquals(1, timeSource.timeCalls);
        assertEquals(1, afterTime.runtime().timeReads());
        assertCachedRoundTrip(api, beforeTime, afterTime);
        assertEquals(1, timeSource.timeCalls);
    }

    private static Debugger.Context advanceToCall(DebugApi api) {
        for (int remaining = 10_000; api.canNext() && remaining > 0; remaining--) {
            Debugger.Context context = api.next();
            if (context.stop().kind() == TrapKind.CALL) {
                return context;
            }
        }
        return fail("debugger did not reach a time call: " + api.current().stop());
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

    private static final class CountingTimeSource implements DebugTimeSource {
        private int clockCalls;
        private int timeCalls;

        @Override
        public long clockTicks() {
            clockCalls++;
            return 321;
        }

        @Override
        public long epochSeconds() {
            timeCalls++;
            return 1_234_567_890L;
        }

        @Override
        public ZoneId localZone() {
            return ZoneId.of("UTC");
        }
    }
}
