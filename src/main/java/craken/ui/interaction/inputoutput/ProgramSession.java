package craken.ui.interaction.inputoutput;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * A single native user program attached directly to ConPTY, without a command shell.
 * The view is the sole reader of its output. Natural exit deliberately leaves the
 * output streams open until {@link #close()}, so a late terminal attachment can
 * still drain a short-lived program's complete output.
 */
public final class ProgramSession implements AutoCloseable {
    public enum State { NEW, STARTING, RUNNING, STOPPING, EXITED, FAILED, CLOSED }

    public interface Listener {
        /** Attach the sole output reader here; this callback precedes onExit. */
        default void onStarted(PtyProcess process) { }
        default void onStateChanged(State state) { }
        default void onExit(int exitCode) { }
        default void onError(Throwable error) { }
    }

    private final Path workingDirectory;
    private final Path executable;
    private final Listener listener;
    private final Object lifecycleLock = new Object();
    private final CompletableFuture<PtyProcess> launched = new CompletableFuture<>();
    private final ExecutorService input = executor("craken-program-input");
    // Serialize callbacks without holding a lock across view code. In particular,
    // a delayed onStarted callback cannot block close() on either UI event thread.
    private final ExecutorService events = executor("craken-program-events");
    private volatile State state = State.NEW;
    private volatile boolean closing;
    private volatile PtyProcess process;
    private volatile WinSize size = new WinSize(100, 24);
    private boolean exitReported;

    public ProgramSession(Path workingDirectory, Path executable, Listener listener) {
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory")
                .toAbsolutePath().normalize();
        this.executable = Objects.requireNonNull(executable, "executable").toAbsolutePath().normalize();
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public Path workingDirectory() { return workingDirectory; }
    public Path executable() { return executable; }
    public State state() { return state; }

    /** Starts at most once and never waits for process creation or view callbacks. */
    public void start() {
        synchronized (lifecycleLock) {
            if (state != State.NEW || closing) return;
            changeState(State.STARTING);
            Thread launcher = new Thread(this::run, "craken-program-process");
            launcher.setDaemon(true);
            launcher.start();
        }
    }

    /** Queues UTF-8 text, escape sequences, or control characters as raw input. */
    public boolean write(String text) {
        return write(Objects.requireNonNull(text, "text").getBytes(StandardCharsets.UTF_8));
    }

    /** Copies and queues bytes in order; no input is accepted after the program exits. */
    public boolean write(byte[] bytes) {
        byte[] copy = Objects.requireNonNull(bytes, "bytes").clone();
        synchronized (lifecycleLock) {
            PtyProcess current = process;
            if (closing || state != State.RUNNING || current == null || !current.isAlive()) return false;
            return enqueueInput(() -> {
                if (closing || state != State.RUNNING || !current.isAlive()) return;
                try {
                    current.getOutputStream().write(copy);
                    current.getOutputStream().flush();
                } catch (IOException error) {
                    if (!closing && current.isAlive()) reportError(error);
                }
            });
        }
    }

    /** Remembers the pre-launch size and asynchronously resizes a running console. */
    public void resize(int columns, int rows) {
        if (columns < 1 || rows < 1) return;
        WinSize requested = new WinSize(columns, rows);
        synchronized (lifecycleLock) {
            if (closing || state == State.EXITED || state == State.FAILED) return;
            size = requested;
            PtyProcess current = process;
            if (current == null || !current.isAlive()) return;
            enqueueInput(() -> {
                if (closing || !current.isAlive()) return;
                try {
                    current.setWinSize(requested);
                } catch (RuntimeException error) {
                    if (!closing && current.isAlive()) reportError(error);
                }
            });
        }
    }

    /** Nonblocking and idempotent; cleans up only this session's process tree. */
    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (closing) return;
            closing = true;
            if (state == State.NEW) launched.complete(null);
            changeState(State.STOPPING);
            input.shutdownNow();
            Thread cleanup = new Thread(this::cleanup, "craken-program-close");
            // Complete native cleanup even when this is the last application view.
            cleanup.setDaemon(false);
            cleanup.start();
        }
    }

    private void run() {
        PtyProcess started = null;
        try {
            if (closing) return;
            if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
                throw new IOException("Program input/output requires Windows");
            }
            if (!Files.isDirectory(workingDirectory)) {
                throw new IOException("Program working directory does not exist: " + workingDirectory);
            }
            if (!Files.isRegularFile(executable)) {
                throw new IOException("Program executable does not exist: " + executable);
            }
            var environment = new HashMap<>(System.getenv());
            environment.put("TERM", "xterm-256color");
            environment.put("COLORTERM", "truecolor");
            WinSize initialSize = size;
            started = new PtyProcessBuilder(commandLine().toArray(String[]::new))
                    .setDirectory(workingDirectory.toString())
                    .setEnvironment(environment)
                    .setInitialColumns(initialSize.getColumns())
                    .setInitialRows(initialSize.getRows())
                    .setUseWinConPty(true)
                    .setWindowsAnsiColorEnabled(true)
                    .start();
            process = started;
            launched.complete(started);
            synchronized (lifecycleLock) {
                if (closing) return;
                if (!size.equals(initialSize) && started.isAlive()) started.setWinSize(size);
                changeState(State.RUNNING);
                PtyProcess connected = started;
                event(() -> listener.onStarted(connected));
            }
            started.waitFor();
            synchronized (lifecycleLock) {
                if (!closing) {
                    changeState(State.EXITED);
                    reportExit(started);
                }
            }
        } catch (IOException | InterruptedException | RuntimeException | LinkageError error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            synchronized (lifecycleLock) {
                if (!closing) {
                    reportError(error);
                    changeState(State.FAILED);
                }
            }
            if (started != null && !closing) {
                terminateTree(started);
                closeStreams(started);
                reportExit(started);
            }
        } finally {
            launched.complete(started);
            input.shutdownNow();
        }
    }

    /** A single literal argv entry, never an executable embedded inside a shell command. */
    List<String> commandLine() { return List.of(executable.toString()); }

    private void cleanup() {
        PtyProcess started = null;
        try {
            started = launched.join();
            if (started != null) terminateTree(started);
        } catch (RuntimeException error) {
            reportError(error);
        } finally {
            if (started != null) {
                closeStreams(started);
                reportExit(started);
            }
            synchronized (lifecycleLock) {
                changeState(State.CLOSED);
                events.shutdown();
            }
        }
    }

    private boolean enqueueInput(Runnable action) {
        try {
            input.execute(action);
            return true;
        } catch (RejectedExecutionException stopped) {
            return false;
        }
    }

    private void changeState(State next) {
        // Callers serialize lifecycle transitions; callbacks remain ordered on events.
        state = next;
        event(() -> listener.onStateChanged(next));
    }

    private void reportExit(PtyProcess started) {
        synchronized (lifecycleLock) {
            if (exitReported || started.isAlive()) return;
            exitReported = true;
            int code = started.exitValue();
            event(() -> listener.onExit(code));
        }
    }

    private void reportError(Throwable error) { event(() -> listener.onError(error)); }

    private void event(Runnable callback) {
        try {
            events.execute(() -> {
                try {
                    callback.run();
                } catch (RuntimeException ignored) {
                    // A disposed or faulty view must not strand the native process.
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Native input can fail while final cleanup is shutting down callbacks.
        }
    }

    private static ExecutorService executor(String name) {
        return Executors.newSingleThreadExecutor(action -> {
            Thread thread = new Thread(action, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void terminateTree(PtyProcess started) {
        if (!started.isAlive()) return;
        // PtyProcess does not implement toHandle(); its own native PID is authoritative.
        List<ProcessHandle> descendants = ProcessHandle.of(started.pid())
                .map(handle -> new ArrayList<>(handle.descendants().toList())).orElseGet(ArrayList::new);
        started.destroyForcibly();
        for (ProcessHandle child : descendants.reversed()) child.destroyForcibly();
        try {
            started.waitFor(3, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            for (ProcessHandle child : descendants) {
                if (child.isAlive()) child.onExit().get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ignored) {
            // Termination has been requested; do not block application exit indefinitely.
        }
    }

    private static void closeStreams(PtyProcess started) {
        try { started.getOutputStream().close(); } catch (IOException ignored) { }
        try { started.getInputStream().close(); } catch (IOException ignored) { }
        try { started.getErrorStream().close(); } catch (IOException ignored) { }
    }
}
