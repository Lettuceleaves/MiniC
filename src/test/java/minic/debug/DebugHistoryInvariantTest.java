package minic.debug;

import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.ir.instruction.ControlInstruction.TrapKind;
import minic.debug.DebugRuntime.RuntimeState;
import minic.debug.DebugRuntime.TerminationKind;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("stdlib-debug")
@Timeout(30)
final class DebugHistoryInvariantTest {
    @Test
    void movingBackwardAndForwardDoesNotRepeatSystemLibrarySideEffects() {
        String source = """
                #include "stdlib.mh"
                #include "stdio.mh"
                int main() {
                    int value = 0;
                    scanf("%d", &value);
                    int *memory = malloc(sizeof(int));
                    *memory = value;
                    printf("value=%d\\n", *memory);
                    free(memory);
                    return value == 7 ? 0 : 1;
                }
                """;
        DebugApi api = new DebugApi(new SourceFile("debug-history.mc", source), "7\n");

        RuntimeState initial = api.current().runtime();
        assertEquals(0, initial.stdinCursor());
        assertEquals("", initial.stderr());
        assertEquals(0, initial.errno());
        assertEquals(1, initial.randomState());
        assertEquals(TerminationKind.RUNNING, initial.termination().kind());

        Debugger.Context beforeScanf = advanceToNextCall(api);
        Debugger.Context afterScanf = api.next();
        assertEquals(0, beforeScanf.runtime().stdinCursor());
        assertEquals(1, afterScanf.runtime().stdinCursor());
        assertCachedRoundTrip(api, beforeScanf, afterScanf);

        Debugger.Context beforeMalloc = advanceToNextCall(api);
        Debugger.Context afterMalloc = api.next();
        assertEquals(0, beforeMalloc.runtime().heap().size());
        assertEquals(1, afterMalloc.runtime().heap().size());
        assertCachedRoundTrip(api, beforeMalloc, afterMalloc);

        Debugger.Context beforePrintf = advanceToNextCall(api);
        Debugger.Context afterPrintf = api.next();
        assertEquals("", beforePrintf.runtime().stdout());
        assertEquals("value=7\n", afterPrintf.runtime().stdout());
        assertCachedRoundTrip(api, beforePrintf, afterPrintf);

        Debugger.Context beforeFree = advanceToNextCall(api);
        Debugger.Context afterFree = api.next();
        assertEquals(1, beforeFree.runtime().heap().size());
        assertEquals(0, afterFree.runtime().heap().size());
        assertCachedRoundTrip(api, beforeFree, afterFree);

        int remaining = 10_000;
        while (api.canNext() && remaining-- > 0) {
            api.next();
        }
        if (api.canNext()) {
            fail("debugger did not complete within the step budget");
        }
        assertEquals(Debugger.Status.COMPLETED, api.current().stop().status());
        assertEquals(TerminationKind.RETURNED, api.current().runtime().termination().kind());
        assertEquals(0, api.current().runtime().termination().status());
    }

    @Test
    void snapshotIncludesLibraryStateAndDoesNotChangeAfterCapture() {
        SourceFile source = new SourceFile("runtime-state.mc", "int main() { return 0; }");
        var pipeline = new CompilerApi(source);
        DebugRuntime runtime = new DebugRuntime(
                new DebugProgram(source, pipeline.runToIr()),
                "input"
        );

        runtime.readInputCharacter();
        runtime.appendError("first");
        runtime.setErrno(34);
        runtime.seedRandom(1234);
        RuntimeState captured = runtime.snapshot();

        runtime.readInputCharacter();
        runtime.appendError(" second");
        runtime.setErrno(99);
        runtime.nextRandom();
        runtime.terminate(3, "test exit");

        assertEquals(1, captured.stdinCursor());
        assertEquals("first", captured.stderr());
        assertEquals(34, captured.errno());
        assertEquals(1234, captured.randomState());
        assertEquals(TerminationKind.RUNNING, captured.termination().kind());

        RuntimeState terminated = runtime.snapshot();
        assertEquals(2, terminated.stdinCursor());
        assertEquals("first second", terminated.stderr());
        assertEquals(99, terminated.errno());
        assertEquals(TerminationKind.EXITED, terminated.termination().kind());
        assertEquals(3, terminated.termination().status());
        assertEquals("test exit", terminated.termination().detail());
    }

    @Test
    void failedExternalCallIsRecordedInTheTerminationSnapshot() {
        String source = """
                extern int unavailable_debug_function();
                int main() { return unavailable_debug_function(); }
                """;
        DebugApi api = new DebugApi(new SourceFile("debug-failure.mc", source));

        int remaining = 1_000;
        while (api.canNext() && remaining-- > 0) {
            api.next();
        }

        assertEquals(Debugger.Status.FAILED, api.current().stop().status());
        assertEquals(TerminationKind.FAILED, api.current().runtime().termination().kind());
        assertEquals(api.current().stop().error(), api.current().runtime().termination().detail());
    }

    @Test
    void strtokAndStrerrorLibraryStateParticipatesInHistoryNavigation() {
        String source = """
                #include "string.mh"
                int main() {
                    char value[4];
                    memcpy(value, "a,b", 4);
                    char *token = strtok(value, ",");
                    char *message = strerror(22);
                    return token[0] == 'a' && message[0] != 0 ? 0 : 1;
                }
                """;
        DebugApi api = new DebugApi(new SourceFile("debug-string-history.mc", source));

        advanceToNextCall(api);
        api.next();
        Debugger.Context beforeStrtok = advanceToNextCall(api);
        Debugger.Context afterStrtok = api.next();
        assertEquals(0, beforeStrtok.runtime().strtokCursor());
        assertNotEquals(0, afterStrtok.runtime().strtokCursor());
        assertCachedRoundTrip(api, beforeStrtok, afterStrtok);

        Debugger.Context beforeStrerror = advanceToNextCall(api);
        Debugger.Context afterStrerror = api.next();
        assertEquals("", beforeStrerror.runtime().strerrorMessage());
        assertEquals(0, beforeStrerror.runtime().libraryMemory().size());
        assertEquals("Invalid argument", afterStrerror.runtime().strerrorMessage());
        assertEquals(1, afterStrerror.runtime().libraryMemory().size());
        assertCachedRoundTrip(api, beforeStrerror, afterStrerror);
    }

    @Test
    void randomAndErrnoSideEffectsAreNotRepeatedWhenHistoryMoves() {
        String source = """
                #include "stdlib.mh"
                #include "errno.mh"
                int main() {
                    srand(7);
                    int random_value = rand();
                    char *end = NULL;
                    errno = 0;
                    long parsed = strtol("2147483648", &end, 10);
                    return random_value >= 0 && parsed == 2147483647 && errno == ERANGE ? 0 : 1;
                }
                """;
        DebugApi api = new DebugApi(new SourceFile("debug-stdlib-state-history.mc", source));

        Debugger.Context beforeSrand = advanceToNextCall(api);
        Debugger.Context afterSrand = api.next();
        assertEquals(1, beforeSrand.runtime().randomState());
        assertEquals(7, afterSrand.runtime().randomState());
        assertCachedRoundTrip(api, beforeSrand, afterSrand);

        Debugger.Context beforeRand = advanceToNextCall(api);
        Debugger.Context afterRand = api.next();
        assertNotEquals(beforeRand.runtime().randomState(), afterRand.runtime().randomState());
        assertCachedRoundTrip(api, beforeRand, afterRand);

        Debugger.Context beforeErrno = advanceToNextCall(api);
        Debugger.Context afterErrno = api.next();
        assertEquals(0, beforeErrno.runtime().libraryMemory().size());
        assertEquals(1, afterErrno.runtime().libraryMemory().size());
        assertCachedRoundTrip(api, beforeErrno, afterErrno);

        Debugger.Context beforeStrtol = advanceToNextCall(api);
        Debugger.Context afterStrtol = api.next();
        assertEquals(0, beforeStrtol.runtime().errno());
        assertEquals(34, afterStrtol.runtime().errno());
        assertCachedRoundTrip(api, beforeStrtol, afterStrtol);
    }

    @Test
    void mathErrnoSideEffectIsStableAcrossHistoryNavigation() {
        String source = """
                #include "math.mh"
                #include "errno.mh"
                int main() {
                    errno = 0;
                    double value = exp(1000.0);
                    return value > 1.0e308 && errno == ERANGE ? 0 : 1;
                }
                """;
        DebugApi api = new DebugApi(new SourceFile("debug-math-history.mc", source));

        advanceToNextCall(api);
        api.next();
        Debugger.Context beforeExp = advanceToNextCall(api);
        Debugger.Context afterExp = api.next();

        assertEquals(0, beforeExp.runtime().errno());
        assertEquals(34, afterExp.runtime().errno());
        assertCachedRoundTrip(api, beforeExp, afterExp);
    }

    private static Debugger.Context advanceToNextCall(DebugApi api) {
        for (int remaining = 10_000; api.canNext() && remaining > 0; remaining--) {
            Debugger.Context context = api.next();
            if (context.stop().kind() == TrapKind.CALL) {
                return context;
            }
        }
        return fail("debugger did not reach the next call trap: " + api.current().stop());
    }

    private static void assertCachedRoundTrip(
            DebugApi api,
            Debugger.Context before,
            Debugger.Context after
    ) {
        for (int cycle = 0; cycle < 10; cycle++) {
            assertSame(before, api.previous());
            assertSame(after, api.next());
            assertEquals(after.runtime(), api.current().runtime());
        }
    }
}
