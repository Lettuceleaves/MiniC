package minic.cpp;

import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppDifferentialHarness.Status;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the probes, without advertising unimplemented C++ syntax as supported. */
@Tag("stl-contract")
@Timeout(60)
final class CppProbeContractTest {
    @TempDir Path temporary;

    @Test
    void explicitProbeOperationsAgreeAcrossNativeDebugAndReference() throws Exception {
        var report = harness().run("probe-balanced", source("common_probe.cpp"), "0");
        assertTrue(report.passed(), report::describe);
        for (var outcome : report.outcomes().values()) {
            assertEquals("live=0 created=3 destroyed=3 copies=1 moves=1 allocations=2 frees=2 "
                    + "comparisons=15 double_destroy=0 double_free=0 copy_alias=0 "
                    + "object_leaks=0 allocation_leaks=0 errors=0\n", normalize(outcome.stdout()));
        }
    }

    @ParameterizedTest(name = "fault {0}: {1}")
    @CsvSource({
            "1, double_destroy=1",
            "2, object_leaks=1",
            "3, copy_alias=1",
            "4, allocation_leaks=1",
            "5, double_free=1"
    })
    void faultInjectionIsDetectedWithoutExecutingUndefinedBehavior(String input, String expected) throws Exception {
        var report = harness().run("probe-fault-" + input, source("common_probe.cpp"), input);
        assertFalse(report.passed(), report::describe);
        for (var outcome : report.outcomes().values()) {
            assertEquals(Status.NONZERO_EXIT, outcome.status(), report::describe);
            assertEquals(1, outcome.exitCode(), report::describe);
            assertTrue(outcome.stdout().contains(expected), report::describe);
            assertEquals("", outcome.stderr(), report::describe);
        }
        assertEquals(1, report.outcomes().values().stream().map(outcome -> normalize(outcome.stdout())).distinct().count(),
                report::describe);
    }

    @Test
    void realCppObjectsValidateTheReferenceLifecycleAndReservedAllocationContract() throws Exception {
        Path input = temporary.resolve("tracked-reference.cpp");
        Path executable = temporary.resolve("tracked-reference.exe");
        Files.writeString(input, source("tracked_reference.cpp"), StandardCharsets.UTF_8);
        var compile = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                        "-std=c++17", "-O2", "-Wall", "-Wextra", input.toString(), "-o", executable.toString()),
                temporary, "", Duration.ofSeconds(20), 65536);
        assertFalse(compile.timedOut(), compile::stderr);
        assertFalse(compile.outputExceeded(), compile::stderr);
        assertEquals(0, compile.exitCode(), compile::stderr);

        var execution = BoundedProcess.run(List.of(executable.toString()), temporary, "", Duration.ofSeconds(5), 4096);
        assertFalse(execution.timedOut());
        assertFalse(execution.outputExceeded());
        assertEquals(0, execution.exitCode(), execution::stderr);
        assertEquals("live=0 created=4 destroyed=4 copies=1 moves=1 allocations=3 frees=3 "
                + "allocator_live=0 reserve_extra=0 comparison_ok=1 errors=0\n", normalize(execution.stdout()));
        assertEquals("", execution.stderr());
    }

    private CppDifferentialHarness harness() {
        return new CppDifferentialHarness(temporary, CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults());
    }

    private static String source(String name) throws IOException {
        return resource(name).replace("#include \"probe_support.mh\"", resource("probe_support.mh"));
    }

    private static String resource(String name) throws IOException {
        try (var stream = CppProbeContractTest.class.getResourceAsStream("/cpp/probes/" + name)) {
            if (stream == null) throw new IOException("Missing probe fixture: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n");
    }
}
