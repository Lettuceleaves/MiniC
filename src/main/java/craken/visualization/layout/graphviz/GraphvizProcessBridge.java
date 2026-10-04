package craken.visualization.layout.graphviz;

import craken.visualization.layout.CancellationToken;
import craken.visualization.layout.LayoutException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import static craken.visualization.layout.LayoutException.Code.*;

public final class GraphvizProcessBridge implements AutoCloseable {
    public record Output(String stdout, String stderr, Duration elapsed) {}
    private final Path root;
    private final long timeoutNanos;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Process> active = ConcurrentHashMap.newKeySet();
    private volatile String version;
    public GraphvizProcessBridge(Path runtimeRoot, Duration timeout) {
        root = Objects.requireNonNull(runtimeRoot).toAbsolutePath().normalize();
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Timeout must be positive");
        timeoutNanos = timeout.toNanos();
    }
    public Output execute(String dot, CancellationToken cancellation) {
        return run(List.of("-Tplain-ext"), Objects.requireNonNull(dot), Objects.requireNonNull(cancellation));
    }
    public String version() { return version(CancellationToken.NONE); }
    public synchronized String version(CancellationToken cancellation) {
        cancellation.check();
        if (closed.get()) throw new LayoutException(CANCELLED, "Graphviz bridge closed");
        if (version == null) {
            var result = run(List.of("-V"), "", cancellation);
            var match = Pattern.compile("graphviz version ([0-9]+\\.[0-9]+\\.[0-9]+)").matcher(result.stderr + result.stdout);
            if (!match.find() || !match.group(1).equals("16.1.0"))
                throw new LayoutException(RUNTIME_UNAVAILABLE, "Runtime is not locked Graphviz 16.1.0: " + result.stderr);
            version = match.group(1);
        }
        return version;
    }
    private Output run(List<String> arguments, String dot, CancellationToken cancellation) {
        cancellation.check();
        if (closed.get()) throw new LayoutException(CANCELLED, "Graphviz bridge closed");
        Path executable = root.resolve("bin/neato.exe");
        if (!Files.isRegularFile(executable)) throw new LayoutException(RUNTIME_UNAVAILABLE, "Missing bundled neato: " + executable);
        long started = System.nanoTime(); Process process = null;
        var workers = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var command = new ArrayList<String>(); command.add(executable.toString()); command.addAll(arguments);
            var builder = new ProcessBuilder(command).directory(root.toFile());
            builder.environment().put("GVBINDIR", root.resolve("bin").toString());
            process = builder.start(); active.add(process);
            if (closed.get()) throw new LayoutException(CANCELLED, "Graphviz bridge closed during start");
            var child = process;
            var stdout = workers.submit(() -> read(child.getInputStream(), 16 * 1024 * 1024, true));
            var stderr = workers.submit(() -> read(child.getErrorStream(), 64 * 1024, false));
            var input = workers.submit(() -> {
                try (var writer = new OutputStreamWriter(child.getOutputStream(), StandardCharsets.UTF_8)) { writer.write(dot); }
                return null;
            });
            while (true) {
                checkDeadline(started, cancellation);
                if (stdout.isDone()) checked(stdout);
                if (process.waitFor(10, TimeUnit.MILLISECONDS)) break;
            }
            String out = await(stdout, started, cancellation), err = await(stderr, started, cancellation);
            if (process.exitValue() != 0) throw new LayoutException(PROCESS_FAILED, "neato exit " + process.exitValue() + ": " + err);
            await(input, started, cancellation);
            return new Output(out, err, Duration.ofNanos(System.nanoTime() - started));
        } catch (LayoutException error) { throw error; }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new LayoutException(CANCELLED, "Graphviz interrupted", interrupted);
        } catch (IOException error) { throw new LayoutException(RUNTIME_UNAVAILABLE, "Cannot start bundled neato", error); }
        finally {
            if (process != null) {
                if (process.isAlive()) process.destroyForcibly();
                closeStream(process.getOutputStream()); closeStream(process.getInputStream()); closeStream(process.getErrorStream());
                try { process.waitFor(2, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                active.remove(process);
            }
            workers.shutdownNow();
        }
    }
    private void checkDeadline(long started, CancellationToken cancellation) {
        cancellation.check();
        if (closed.get()) throw new LayoutException(CANCELLED, "Graphviz bridge closed");
        if (System.nanoTime() - started >= timeoutNanos) throw new LayoutException(TIMEOUT, "Graphviz layout timed out");
    }
    private <T> T await(Future<T> future, long started, CancellationToken cancellation) throws InterruptedException {
        while (!future.isDone()) {
            checkDeadline(started, cancellation);
            try { return future.get(10, TimeUnit.MILLISECONDS); }
            catch (TimeoutException retry) { /* The deadline covers stream draining as well as native execution. */ }
            catch (ExecutionException error) { throw streamFailure(error); }
        }
        checkDeadline(started, cancellation);
        return checked(future);
    }
    private static String read(InputStream stream, int limit, boolean rejectOverflow) throws IOException {
        try (stream; var output = new ByteArrayOutputStream()) {
            var buffer = new byte[8192]; boolean truncated = false; int count;
            while ((count = stream.read(buffer)) >= 0) {
                int remaining = limit - output.size();
                output.write(buffer, 0, Math.min(count, remaining));
                if (count > remaining) {
                    if (rejectOverflow) throw new LayoutException(INVALID_RESULT, "Graphviz output exceeds limit");
                    truncated = true;
                }
            }
            return output.toString(StandardCharsets.UTF_8) + (truncated ? "\n[truncated]" : "");
        }
    }
    private static <T> T checked(Future<T> future) throws InterruptedException {
        try { return future.get(); }
        catch (ExecutionException error) { throw streamFailure(error); }
    }
    private static LayoutException streamFailure(ExecutionException error) {
        if (error.getCause() instanceof LayoutException layout) return layout;
        return new LayoutException(PROCESS_FAILED, "Graphviz stream failure", error.getCause());
    }
    private static void closeStream(Closeable stream) { try { stream.close(); } catch (IOException ignored) {} }
    public int activeProcessCount() { return active.size(); }
    @Override public void close() { closed.set(true); active.forEach(Process::destroyForcibly); }
}
