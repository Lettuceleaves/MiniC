package minic.cpp.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class BoundedProcess {
    private BoundedProcess() {}
    public record Result(int exitCode, String stdout, String stderr, boolean timedOut, boolean outputExceeded) {}
    public static Result run(List<String> command, Path directory, String stdin, Duration timeout, int outputLimit)
            throws IOException, InterruptedException {
        if (timeout.isZero() || timeout.isNegative() || outputLimit < 1) {
            throw new IllegalArgumentException("timeout and outputLimit must be positive");
        }
        Process process = new ProcessBuilder(List.copyOf(command)).directory(directory.toFile()).start();
        Capture stdout = new Capture(process.getInputStream(), outputLimit);
        Capture stderr = new Capture(process.getErrorStream(), outputLimit);
        Thread outReader = daemon("child-stdout", stdout);
        Thread errReader = daemon("child-stderr", stderr);
        Thread writer = daemon("child-stdin", () -> {
            try (var input = process.getOutputStream()) {
                input.write(stdin.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // A child may exit without consuming all input; exit/timeout determines the result.
            }
        });
        boolean timedOut;
        try {
            timedOut = !process.waitFor(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (timedOut) terminate(process);
            // Stream consumers run concurrently, including while writing a large stdin payload.
            outReader.join(1000);
            errReader.join(1000);
            writer.join(1000);
            if (outReader.isAlive() || errReader.isAlive() || writer.isAlive()) {
                throw new IOException("Child stream did not close after process termination");
            }
            if (!timedOut && (stdout.failure != null || stderr.failure != null)) {
                throw new IOException("Failed to capture child output", stdout.failure != null ? stdout.failure : stderr.failure);
            }
            return new Result(timedOut ? -1 : process.exitValue(), stdout.text(), stderr.text(), timedOut,
                    stdout.exceeded || stderr.exceeded);
        } finally {
            if (process.isAlive()) terminate(process);
        }
    }

    private static Thread daemon(String name, Runnable action) {
        return Thread.ofPlatform().daemon(true).name(name).start(action);
    }

    private static void terminate(Process process) throws InterruptedException {
        process.descendants().forEach(child -> { if (child.isAlive()) child.destroyForcibly(); });
        process.destroyForcibly();
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Child process did not terminate: " + process.pid());
        }
    }

    private static final class Capture implements Runnable {
        private final InputStream input;
        private final int limit;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private volatile boolean exceeded;
        private volatile IOException failure;

        private Capture(InputStream input, int limit) { this.input = input; this.limit = limit; }

        @Override public void run() {
            try (input) {
                byte[] bytes = new byte[8192];
                for (int count; (count = input.read(bytes)) != -1;) {
                    synchronized (buffer) {
                        int accepted = Math.min(count, limit - buffer.size());
                        buffer.write(bytes, 0, accepted);
                        if (accepted != count) exceeded = true;
                    }
                    // Continue draining beyond the cap; neither pipe may block the other.
                }
            } catch (IOException error) { failure = error; }
        }

        private String text() { synchronized (buffer) { return buffer.toString(StandardCharsets.UTF_8); } }
    }
}
