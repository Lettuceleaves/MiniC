package minic.cpp;

import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppDifferentialHarness.Backend;
import minic.cpp.support.CppDifferentialHarness.Outcome;
import minic.cpp.support.CppDifferentialHarness.Status;
import minic.cpp.support.ProcessProbe;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential")
@Timeout(60)
final class CppDifferentialHarnessTest {
    @TempDir Path temporary;

    @Test void acceptsEqualResultsAndOnlyNormalizesCrLf() {
        var report = CppDifferentialHarness.compare(List.of(
                ok(Backend.MINIC_NATIVE, "42\r\n", "diagnostic\r\n"),
                ok(Backend.MINIC_DEBUG, "42\n", "diagnostic\n"),
                ok(Backend.GXX, "42\n", "diagnostic\n")));
        assertTrue(report.passed(), report::describe);
    }

    @Test void detectsStdoutAndStderrMismatchWithoutTrimmingWhitespace() {
        var report = CppDifferentialHarness.compare(List.of(
                ok(Backend.MINIC_NATIVE, "42 \n", "warning"),
                ok(Backend.MINIC_DEBUG, "42\n", ""),
                ok(Backend.GXX, "42\n", "")));
        assertFalse(report.passed());
        assertTrue(report.describe().contains("MINIC_NATIVE stdout mismatch"), report::describe);
        assertTrue(report.describe().contains("MINIC_NATIVE stderr mismatch"), report::describe);
    }

    @Test void rejectsNonzeroExitsEvenIfEveryBackendAgrees() {
        var report = CppDifferentialHarness.compare(List.of(
                new Outcome(Backend.MINIC_NATIVE, Status.NONZERO_EXIT, 9, "", "", ""),
                new Outcome(Backend.MINIC_DEBUG, Status.NONZERO_EXIT, 9, "", "", ""),
                new Outcome(Backend.GXX, Status.NONZERO_EXIT, 9, "", "", "")));
        assertFalse(report.passed());
        assertEquals(3, report.issues().size());
        assertTrue(report.describe().contains("NONZERO_EXIT"));
    }

    @Test void rejectsIncompleteOrDuplicatedBackendReports() {
        assertThrows(IllegalArgumentException.class, () -> CppDifferentialHarness.compare(List.of(
                ok(Backend.GXX, "", ""))));
        assertThrows(IllegalArgumentException.class, () -> CppDifferentialHarness.compare(List.of(
                ok(Backend.GXX, "", ""), ok(Backend.GXX, "", ""), ok(Backend.MINIC_DEBUG, "", ""))));
    }

    @Test void compilerConfigurationUsesExplicitPrecedenceAndNoShellParsing() {
        assertEquals("C:/tools with spaces/g++.exe", CppDifferentialHarness.referenceCompiler(Map.of(
                "MINIC_CXX", "C:/tools with spaces/g++.exe", "GXX", "fallback")));
        assertEquals("fallback", CppDifferentialHarness.referenceCompiler(Map.of("GXX", "fallback")));
        assertEquals("g++", CppDifferentialHarness.referenceCompiler(Map.of()));
    }

    @Test void processPreservesIndependentStreamsAndFixedInput() throws Exception {
        var result = BoundedProcess.run(ProcessProbe.command("echo", "argument with spaces"),
                temporary, "fixed input\n", Duration.ofSeconds(10), 4096);
        assertEquals(0, result.exitCode());
        assertEquals("argument with spaces:fixed input\n", result.stdout());
        assertEquals("separate stderr", result.stderr());
        assertFalse(result.timedOut());
        assertFalse(result.outputExceeded());
    }

    @Test void processDetectsNonzeroExitAndMissingExecutable() throws Exception {
        assertEquals(17, BoundedProcess.run(ProcessProbe.command("exit", "17"),
                temporary, "", Duration.ofSeconds(10), 4096).exitCode());
        assertThrows(java.io.IOException.class, () -> BoundedProcess.run(
                List.of(temporary.resolve("missing-program.exe").toString()),
                temporary, "", Duration.ofSeconds(1), 4096));
    }

    @Test void processTimeoutAlsoBoundsBlockedStdinWriter() throws Exception {
        long started = System.nanoTime();
        var result = BoundedProcess.run(ProcessProbe.command("sleep", "60000"),
                temporary, "x".repeat(1_000_000), Duration.ofMillis(700), 4096);
        assertTrue(result.timedOut());
        assertTrue(Duration.ofNanos(System.nanoTime() - started).toSeconds() < 8,
                "Timeout must include writing stdin and draining child streams");
    }

    @Test void processCapsBothStreamsWithoutDeadlock() throws Exception {
        var result = BoundedProcess.run(ProcessProbe.command("flood", "100000"),
                temporary, "", Duration.ofSeconds(10), 1024);
        assertTrue(result.outputExceeded());
        assertTrue(result.stdout().length() <= 1024);
        assertTrue(result.stderr().length() <= 1024);
        assertFalse(result.timedOut());
    }

    @Test void sharedSourceAndStdinAgreeAcrossAllThreeBackends() throws Exception {
        var report = harness().run("fixed-input", """
                #include <stdio.h>
                int main(void) {
                    int n = getchar() - '0';
                    int total = 0;
                    for (int i = 1; i <= n; i++) total += i * i;
                    printf("sum=%d\\n", total);
                    return 0;
                }
                """, "6\n");
        assertTrue(report.passed(), report::describe);
        assertEquals("sum=91\n", report.outcomes().get(Backend.GXX).stdout().replace("\r\n", "\n"));
    }

    @Test void invalidSourceIsCompileFailureForEveryBackend() throws Exception {
        var report = harness().run("syntax-error", "int main(void) { return ; broken syntax }", "");
        assertFalse(report.passed());
        for (var outcome : report.outcomes().values()) {
            assertEquals(Status.COMPILE_ERROR, outcome.status(), report::describe);
            assertFalse(outcome.diagnostics().isBlank(), report::describe);
        }
    }

    @Test void deliberatelyWrongProgramOutputIsReportedAsMismatch() throws Exception {
        // Deliberate fixture fault: the same source changes output by compiler identity.
        var report = harness().run("injected-wrong-output", """
                #include <stdio.h>
                int main(void) {
                #ifdef __GNUC__
                    puts("expected");
                #else
                    puts("wrong");
                #endif
                    return 0;
                }
                """, "");
        for (var outcome : report.outcomes().values()) assertEquals(Status.OK, outcome.status(), report::describe);
        assertFalse(report.passed());
        assertEquals(2, report.issues().size(), report::describe);
        assertTrue(report.issues().stream().allMatch(issue -> issue.endsWith("stdout mismatch")), report::describe);
    }

    @Test void compilationDeadlineIsDistinctFromProgramTimeout() throws Exception {
        var harness = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()),
                new CppDifferentialHarness.Limits(Duration.ofNanos(1), Duration.ofNanos(1), 100, 65536));
        var report = harness.run("compile-deadline", "int main(void) { return 0; }", "");
        for (var outcome : report.outcomes().values()) {
            assertEquals(Status.COMPILE_TIMEOUT, outcome.status(), report::describe);
        }
    }

    @Test void actualNonzeroProgramCannotPassDifferentialComparison() throws Exception {
        var report = harness().run("bad-exit", "int main(void) { return 7; }", "");
        assertFalse(report.passed());
        for (var outcome : report.outcomes().values()) {
            assertEquals(Status.NONZERO_EXIT, outcome.status(), report::describe);
            assertEquals(7, outcome.exitCode(), report::describe);
        }
    }

    @Test void explicitExitStatusIsPreservedAcrossEveryBackend() throws Exception {
        var report = harness().run("explicit-exit", """
                #include <stdlib.h>
                int main(void) { exit(7); return 0; }
                """, "");
        assertFalse(report.passed());
        for (var outcome : report.outcomes().values()) {
            assertEquals(Status.NONZERO_EXIT, outcome.status(), report::describe);
            assertEquals(7, outcome.exitCode(), report::describe);
        }
    }

    @Test void missingReferenceCompilerIsToolFailureNotSourceRejection() throws Exception {
        var harness = new CppDifferentialHarness(temporary,
                temporary.resolve("missing-gxx.exe").toString(), CppDifferentialHarness.Limits.defaults());
        var report = harness.run("missing-tool", "int main(void) { return 0; }", "");
        assertEquals(Status.TOOL_ERROR, report.outcomes().get(Backend.GXX).status());
        assertFalse(report.passed());
    }

    @Test void infiniteProgramHasBoundedNativeDebugAndReferenceExecution() throws Exception {
        var harness = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()),
                new CppDifferentialHarness.Limits(Duration.ofSeconds(20), Duration.ofSeconds(2), 100, 65536));
        var report = harness.run("infinite-loop", "int main(void) { while (1) {} return 0; }", "");
        assertFalse(report.passed());
        for (var outcome : report.outcomes().values()) {
            assertEquals(Status.RUN_TIMEOUT, outcome.status(), report::describe);
        }
    }

    private CppDifferentialHarness harness() {
        return new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()), CppDifferentialHarness.Limits.defaults());
    }

    private static Outcome ok(Backend backend, String stdout, String stderr) {
        return new Outcome(backend, Status.OK, 0, stdout, stderr, "");
    }
}
