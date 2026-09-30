package minic.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Raw observations are saved as well as summaries; short process timings are explicitly flagged. */
final class NativeBenchmarkReport {
    final Map<String, Object> metadata = new LinkedHashMap<>();
    final List<Sample> samples = new ArrayList<>();
    final List<String> errors = new ArrayList<>();

    record Sample(String workload, String compiler, String phase, int repetition, int size, int rounds,
                  int seed, long processWallNanos, Long compilerPipelineNanos, String checksum, String sourceSha256) {
        Map<String, Object> fields() {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("workload", workload);
            fields.put("compiler", compiler);
            fields.put("phase", phase);
            fields.put("repetition", repetition);
            fields.put("size", size);
            fields.put("rounds", rounds);
            fields.put("seed", seed);
            fields.put("processWallNanos", processWallNanos);
            fields.put("compilerPipelineNanos", compilerPipelineNanos);
            fields.put("checksum", checksum);
            fields.put("sourceSha256", sourceSha256);
            return fields;
        }
    }

    void write(Path output, long minSampleNanos) throws IOException {
        Files.createDirectories(output);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1);
        report.put("status", errors.isEmpty() ? "passed-correctness" : "failed");
        report.put("metadata", metadata);
        report.put("errors", errors);
        report.put("samples", samples.stream().map(Sample::fields).toList());
        report.put("summaries", summaries(minSampleNanos));
        Files.writeString(output.resolve("report.json"), encode(report) + "\n");
        List<String> columns = List.of("workload", "compiler", "phase", "repetition", "size", "rounds", "seed",
                "processWallNanos", "compilerPipelineNanos", "checksum", "sourceSha256");
        StringBuilder csv = new StringBuilder(String.join(",", columns)).append('\n');
        for (Sample sample : samples) {
            var fields = sample.fields();
            csv.append(columns.stream().map(c -> NativeBenchmarkSupport.csv(
                    fields.get(c) == null ? "" : fields.get(c).toString())).collect(Collectors.joining(","))).append('\n');
        }
        Files.writeString(output.resolve("samples.csv"), csv.toString());
    }

    List<Map<String, Object>> summaries(long minSampleNanos) {
        Map<String, List<Sample>> groups = new LinkedHashMap<>();
        samples.stream().filter(s -> s.phase().equals("measurement")).forEach(s ->
                groups.computeIfAbsent(s.workload() + ":" + s.compiler(), ignored -> new ArrayList<>()).add(s));
        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : groups.values()) {
            List<Long> times = entry.stream().map(Sample::processWallNanos).toList();
            double median = NativeBenchmarkSupport.median(times);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("workload", entry.getFirst().workload());
            row.put("compiler", entry.getFirst().compiler());
            row.put("sampleCount", times.size());
            row.put("medianProcessWallNanos", median);
            row.put("minProcessWallNanos", times.stream().mapToLong(Long::longValue).min().orElseThrow());
            row.put("maxProcessWallNanos", times.stream().mapToLong(Long::longValue).max().orElseThrow());
            row.put("medianAbsoluteDeviationNanos", NativeBenchmarkSupport.median(
                    times.stream().map(t -> (long)Math.abs(t - median)).toList()));
            row.put("shortSampleWarning", times.stream().anyMatch(t -> t < minSampleNanos));
            result.add(row);
        }
        return result;
    }

    static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return NativeBenchmarkSupport.json(text);
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> fields) {
            return fields.entrySet().stream().map(e -> NativeBenchmarkSupport.json(e.getKey().toString())
                    + ":" + encode(e.getValue())).collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof List<?> items) return items.stream().map(NativeBenchmarkReport::encode)
                .collect(Collectors.joining(",", "[", "]"));
        throw new IllegalArgumentException("unsupported report field: " + value.getClass());
    }
}
