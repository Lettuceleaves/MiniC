package minic.benchmark;

import minic.cpp.support.BoundedProcess;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Test-tool utilities; no benchmark process or timing is part of the product compiler. */
final class NativeBenchmarkSupport {
    private NativeBenchmarkSupport() {}

    static long checksum(String output) {
        String text = output.strip();
        if (!text.matches("checksum=[0-9]+")) throw new IllegalArgumentException("invalid checksum output: " + text);
        return Long.parseUnsignedLong(text.substring("checksum=".length()));
    }

    static void requireChecksum(String output, long expected) {
        long actual = checksum(output);
        if (actual != expected) throw new IllegalStateException("checksum mismatch: expected "
                + Long.toUnsignedString(expected) + ", got " + Long.toUnsignedString(actual));
    }

    static List<String> order(int repetition) {
        return repetition % 2 == 0 ? List.of("minic", "g++") : List.of("g++", "minic");
    }

    static double median(List<Long> values) {
        if (values.isEmpty()) throw new IllegalArgumentException("samples must not be empty");
        var sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : sorted.get(mid - 1) / 2.0 + sorted.get(mid) / 2.0;
    }

    static String json(String text) {
        StringBuilder result = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (c < 32) result.append(String.format("\\u%04x", (int)c));
                    else result.append(c);
                }
            }
        }
        return result.append('"').toString();
    }

    static String csv(String text) { return '"' + text.replace("\"", "\"\"") + '"'; }

    /** Independently computes the mathematical workloads without executing either compiled program. */
    static long expectedChecksum(String workload, int size, int rounds, int seed) {
        if (size < 1 || rounds < 1 || seed < 0) throw new IllegalArgumentException("invalid workload input");
        if (workload.equals("function-calls")) {
            long state = seed % 65521L;
            for (int r = 0; r < rounds; r++) {
                for (int i = 0; i < size; i++) state = (state * 17 + (i & 255) + 31) % 65521;
            }
            return state;
        }
        if (!workload.equals("array-scan") && !workload.equals("memory-copy")) {
            throw new IllegalArgumentException("unknown workload: " + workload);
        }
        int[] values = new int[size];
        long state = seed % 65521L;
        for (int i = 0; i < size; i++) {
            state = (state * 17 + 31) % 65521;
            values[i] = (int) state;
        }
        long result = 0;
        if (workload.equals("array-scan")) {
            for (int r = 0; r < rounds; r++) {
                for (int i = 0; i < size; i++) {
                    values[i] = (values[i] + (i & 255) + (r & 255)) & 65535;
                    result += values[i];
                }
            }
        } else {
            // memcpy preserves all elements: only the one explicitly changed per round matters.
            for (int r = 0; r < rounds; r++) {
                int i = r % size;
                values[i] = (values[i] + (r & 255) + 1) & 65535;
            }
            for (int value : values) result += value;
        }
        return result;
    }

    record ProcessResult(int exitCode, String stdout, String stderr, boolean timedOut,
                         boolean outputTruncated, long wallNanos) {
        void requireSuccess() {
            if (timedOut || outputTruncated || exitCode != 0) {
                throw new IllegalStateException("process failed: exit=" + exitCode + ", timeout=" + timedOut
                        + ", outputTruncated=" + outputTruncated + "\nstdout: " + stdout + "\nstderr: " + stderr);
            }
        }
    }

    /** Wall time includes process start, stdin, execution and output drain; it is not CPU/kernel time. */
    static ProcessResult run(List<String> command, Path directory, String stdin, Duration timeout, int outputLimit)
            throws Exception {
        long started = System.nanoTime();
        var result = BoundedProcess.run(command, directory, stdin, timeout, outputLimit);
        return new ProcessResult(result.exitCode(), result.stdout(), result.stderr(), result.timedOut(),
                result.outputExceeded(), System.nanoTime() - started);
    }
}