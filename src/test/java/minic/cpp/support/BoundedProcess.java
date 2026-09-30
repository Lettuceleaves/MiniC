package minic.cpp.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Bounds captured output and execution, including cleanup of descendants observed while running.
 * ProcessHandle polling is not OS-level containment: an unobserved child that forks and is
 * reparented between snapshots can escape. Windows Job Objects would be needed for that guarantee.
 */
public final class BoundedProcess {
    private BoundedProcess() {}
    public record Result(int exitCode, String stdout, String stderr, boolean timedOut, boolean outputExceeded) {}
    public static Result run(List<String> command, Path directory, String stdin, Duration timeout, int outputLimit)
            throws IOException, InterruptedException {
        return run(command, directory, stdin, timeout, outputLimit, ignored -> {});
    }

    /** Observation hook lets process fixtures synchronize before their parent exits. */
    public static Result run(List<String> command, Path directory, String stdin, Duration timeout, int outputLimit,
                             Consumer<ProcessHandle> descendantObserved) throws IOException, InterruptedException {
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
        Map<Long, ProcessHandle> observed = new LinkedHashMap<>();
        long started = System.nanoTime();
        boolean cleaned = false;
        try {
            while (process.isAlive() && System.nanoTime() - started < timeout.toNanos()) {
                process.descendants().forEach(child -> {
                    if (observed.putIfAbsent(child.pid(), child) == null) descendantObserved.accept(child);
                });
                long remaining = timeout.toNanos() - (System.nanoTime() - started);
                if (remaining > 0) process.waitFor(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(10)), TimeUnit.NANOSECONDS);
            }
            timedOut = process.isAlive();
            // A normally exited parent can leave a live child holding its pipe handles.
            // Clean observed descendants before waiting for the output readers to reach EOF.
            terminate(process, observed);
            cleaned = true;
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
            try {
                if (!cleaned) terminate(process, observed);
            } finally {
                process.getOutputStream().close();
                process.getInputStream().close();
                process.getErrorStream().close();
            }
        }
    }

    private static Thread daemon(String name, Runnable action) {
        return Thread.ofPlatform().daemon(true).name(name).start(action);
    }

    private static void terminate(Process process, Map<Long, ProcessHandle> observed)
            throws InterruptedException, IOException {
        process.descendants().forEach(child -> observed.putIfAbsent(child.pid(), child));
        // Include descendants of already-observed children even after the direct parent exits.
        for (ProcessHandle child : List.copyOf(observed.values())) {
            child.descendants().forEach(descendant -> observed.putIfAbsent(descendant.pid(), descendant));
        }
        observed.values().forEach(child -> { if (child.isAlive()) child.destroyForcibly(); });
        if (process.isAlive()) process.destroyForcibly();
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            throw new IOException("Child process did not terminate: " + process.pid());
        }
        long started = System.nanoTime();
        for (ProcessHandle child : observed.values()) {
            if (!child.isAlive()) continue;
            long remaining = TimeUnit.SECONDS.toNanos(2) - (System.nanoTime() - started);
            if (remaining <= 0) throw new IOException("Observed descendant did not terminate: " + child.pid());
            try {
                child.onExit().get(remaining, TimeUnit.NANOSECONDS);
            } catch (ExecutionException | TimeoutException failure) {
                throw new IOException("Observed descendant did not terminate: " + child.pid(), failure);
            }
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
