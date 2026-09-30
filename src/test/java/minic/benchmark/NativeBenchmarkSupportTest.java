package minic.benchmark;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

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
    void alternatesBuildOrderAndComputesMedianWithoutMutatingSamples() {
        assertEquals(List.of("minic", "g++"), NativeBenchmarkSupport.order(0));
        assertEquals(List.of("g++", "minic"), NativeBenchmarkSupport.order(1));
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
