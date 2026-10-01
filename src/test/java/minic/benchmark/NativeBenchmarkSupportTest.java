package minic.benchmark;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import minic.compiler.ir.optimize.OptimizationLevel;

import static org.junit.jupiter.api.Assertions.*;

@Tag("native-perf-contract")
final class NativeBenchmarkSupportTest {
    @TempDir Path temporary;

    @Test
    void rejectsIncorrectOrAmbiguousChecksums() {
        assertEquals(42L, NativeBenchmarkSupport.checksum("checksum=42\r\n"));
        assertThrows(IllegalArgumentException.class, () -> NativeBenchmarkSupport.checksum(""));
        assertThrows(IllegalArgumentException.class, () -> NativeBenchmarkSupport.checksum("42"));
        assertThrows(IllegalArgumentException.class, () -> NativeBenchmarkSupport.checksum("checksum=1\nchecksum=2"));
        assertThrows(IllegalStateException.class, () -> NativeBenchmarkSupport.requireChecksum("checksum=42", 43));
    }

    @Test
    void rotatesThreeBuildsAndComputesMedianWithoutMutatingSamples() {
        List<String> builds = List.of("minic-baseline", "minic-optimized", "g++");
        assertEquals(builds, NativeBenchmarkSupport.order(0));
        assertEquals(List.of("minic-optimized", "g++", "minic-baseline"), NativeBenchmarkSupport.order(1));
        for (String build : builds) {
            for (int position = 0; position < builds.size(); position++) {
                int count = 0;
                for (int repetition = 0; repetition < 6; repetition++) {
                    assertEquals(java.util.Set.copyOf(builds), java.util.Set.copyOf(NativeBenchmarkSupport.order(repetition)));
                    if (NativeBenchmarkSupport.order(repetition).get(position).equals(build)) count++;
                }
                assertEquals(2, count, "Every build must occur twice at every position across six rotations");
            }
        }
        List<Long> samples = List.of(40L, 10L, 30L, 20L);
        assertEquals(25.0, NativeBenchmarkSupport.median(samples));
        assertEquals(List.of(40L, 10L, 30L, 20L), samples);
        assertThrows(IllegalArgumentException.class, () -> NativeBenchmarkSupport.median(List.of()));
    }

    @Test
    void escapesReportsWithoutLosingMetadata() {
        assertEquals("\"C:\\\\a\\\"b\\n\\t\\u0001\"", NativeBenchmarkSupport.json("C:\\a\"b\n\t\u0001"));
        assertEquals("\"x,\"\"y\"\"\n\"", NativeBenchmarkSupport.csv("x,\"y\"\n"));
    }

    @Test
    void compilerReportPreservesTheActualModeAndArbitraryPassNames() {
        String name = "fold,\"constants\"\nsecond line";
        String encoded = Base64.getEncoder().encodeToString(name.getBytes(StandardCharsets.UTF_8));
        String output = "artifact=C:/program.exe\ncompile_ns=42\noptimization_level=OPTIMIZED\npass_count=1\npass_0=" + encoded + "\n";
        var compiled = NativeBenchmarkSupport.compilation(output);
        assertEquals(OptimizationLevel.OPTIMIZED, compiled.level());
        assertEquals(List.of(name), compiled.passNames());
        assertEquals(42, compiled.compilerPipelineNanos());
        assertEquals(Path.of("C:/program.exe"), compiled.artifact());
        assertThrows(UnsupportedOperationException.class, () -> compiled.passNames().clear());
        assertThrows(IllegalArgumentException.class, () -> NativeBenchmarkSupport.compilation(output.replace("pass_count=1", "pass_count=-1")));
        assertThrows(IllegalArgumentException.class, () -> NativeBenchmarkSupport.compilation(output + "optimization_level=BASELINE\n"));
        assertThrows(IllegalArgumentException.class, () -> NativeBenchmarkSupport.compilation(output.replace("OPTIMIZED", "BASELINE")));
        assertThrows(IllegalArgumentException.class, () -> NativeBenchmarkSupport.compilation(output.replace("compile_ns=42", "compile_ns=-1")));
    }

    @Test
    void reportsBuildIdentitySeparateFromCompileAndExecutionSamples() throws Exception {
        var report = new NativeBenchmarkReport();
        report.builds.add(new NativeBenchmarkReport.Build("array-scan", "minic-baseline", "BASELINE", List.of(),
                "source-hash", "baseline.exe", "baseline-hash"));
        report.builds.add(new NativeBenchmarkReport.Build("array-scan", "minic-optimized", "OPTIMIZED", List.of("test-pass"),
                "source-hash", "optimized.exe", "optimized-hash"));
        report.builds.add(new NativeBenchmarkReport.Build("array-scan", "g++", null, List.of(),
                "source-hash", "gxx.exe", "gxx-hash"));
        for (String build : NativeBenchmarkSupport.order(0)) {
            report.samples.add(new NativeBenchmarkReport.Sample("array-scan", build, "compile", 0, 3, 2, 1,
                    999, build.equals("g++") ? null : 555L, "", "source-hash"));
            report.samples.add(new NativeBenchmarkReport.Sample("array-scan", build, "measurement", 0, 3, 2, 1,
                    10, null, "30662", "source-hash"));
        }
        report.write(temporary, 15);
        String json = Files.readString(temporary.resolve("report.json"));
        assertTrue(json.contains("\"schemaVersion\":2"));
        assertTrue(json.contains("\"optimizationLevel\":\"BASELINE\",\"passNames\":[]"));
        assertTrue(json.contains("\"optimizationLevel\":\"OPTIMIZED\",\"passNames\":[\"test-pass\"]"));
        assertTrue(json.contains("\"artifactSha256\":\"optimized-hash\""));
        assertEquals(3, report.summaries(15).size());
        assertEquals(7, Files.readAllLines(temporary.resolve("samples.csv")).size());
    }

    @Test
    void checksumOracleIsIndependentAndSeedSensitive() {
        assertEquals(30662, NativeBenchmarkSupport.expectedChecksum("array-scan", 3, 2, 1));
        assertEquals(38230, NativeBenchmarkSupport.expectedChecksum("function-calls", 3, 2, 1));
        assertEquals(15328, NativeBenchmarkSupport.expectedChecksum("memory-copy", 3, 2, 1));
        assertNotEquals(NativeBenchmarkSupport.expectedChecksum("array-scan", 20, 3, 1),
                NativeBenchmarkSupport.expectedChecksum("array-scan", 20, 3, 2));
    }

    @Test
    void reportsOnlyMeasurementsInSummariesAndPreservesFailureMetadata() throws Exception {
        var report = new NativeBenchmarkReport();
        report.metadata.put("compiler", "g++ \"test\"");
        report.samples.add(new NativeBenchmarkReport.Sample("array-scan", "minic", "compile", 0,
                3, 2, 1, 999, 555L, "", "hash"));
        report.samples.add(new NativeBenchmarkReport.Sample("array-scan", "minic", "warmup", 0,
                3, 2, 1, 999, null, "30662", "hash"));
        report.samples.add(new NativeBenchmarkReport.Sample("array-scan", "minic", "measurement", 0,
                3, 2, 1, 10, null, "30662", "hash"));
        report.samples.add(new NativeBenchmarkReport.Sample("array-scan", "minic", "measurement", 1,
                3, 2, 1, 30, null, "30662", "hash"));
        var summary = report.summaries(15).getFirst();
        assertEquals(20.0, summary.get("medianProcessWallNanos"));
        assertEquals(2, summary.get("sampleCount"));
        assertEquals(true, summary.get("shortSampleWarning"));
        assertEquals(10.0, summary.get("medianAbsoluteDeviationNanos"));
        report.errors.add("checksum mismatch");
        report.write(temporary, 15);
        String json = Files.readString(temporary.resolve("report.json"));
        assertTrue(json.contains("\"status\":\"failed\""));
        assertTrue(json.contains("\"compilerPipelineNanos\":555"));
        assertTrue(json.contains(NativeBenchmarkSupport.json("g++ \"test\"")));
        assertEquals(5, Files.readAllLines(temporary.resolve("samples.csv")).size());
    }

    @Test
    void rejectsBadConfigurationAndExistingReports() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> NativeBenchmarkMain.Config.parse(new String[]{"--size=0"}));
        assertThrows(IllegalArgumentException.class, () -> NativeBenchmarkMain.Config.parse(new String[]{"--random=true"}));
        assertThrows(IllegalArgumentException.class, () -> NativeBenchmarkMain.Config.parse(new String[]{"--size=16777216", "--rounds=1000000"}));
        assertThrows(IllegalArgumentException.class, () -> NativeBenchmarkMain.Config.parse(new String[]{"--size=1", "--size=2"}));
        Files.writeString(temporary.resolve("report.json"), "previous result");
        assertThrows(IllegalArgumentException.class, () -> NativeBenchmarkMain.Config.parse(
                new String[]{"--output=" + temporary}));
    }

    @Test
    void terminatesTimeoutAndBoundsBothOutputStreams() throws Exception {
        var timedOut = NativeBenchmarkSupport.run(child("sleep"), Path.of("."), "", Duration.ofMillis(300), 1024);
        assertTrue(timedOut.timedOut());
        assertThrows(IllegalStateException.class, timedOut::requireSuccess);
        var flooded = NativeBenchmarkSupport.run(child("flood"), Path.of("."), "", Duration.ofSeconds(10), 256);
        assertTrue(flooded.outputTruncated());
        assertTrue(flooded.stdout().length() <= 256);
        assertTrue(flooded.stderr().length() <= 256);
        assertThrows(IllegalStateException.class, flooded::requireSuccess);
        var failed = NativeBenchmarkSupport.run(child("fail"), Path.of("."), "", Duration.ofSeconds(10), 1024);
        assertEquals(7, failed.exitCode());
        assertThrows(IllegalStateException.class, failed::requireSuccess);
    }

    private static List<String> child(String mode) {
        return List.of(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-cp", System.getProperty("java.class.path"), ProcessFixture.class.getName(), mode);
    }

    public static final class ProcessFixture {
        public static void main(String[] args) throws Exception {
            switch (args[0]) {
                case "sleep" -> Thread.sleep(60_000);
                case "flood" -> { System.out.print("x".repeat(100_000)); System.err.print("y".repeat(100_000)); }
                case "fail" -> System.exit(7);
                default -> throw new IllegalArgumentException();
            }
        }
    }
}
