package minic.ui.interaction.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A persistent, interactive PowerShell attached to a Windows pseudo console.
 * The terminal emulator owns the only reader of the process output stream. All
 * startup, input, resize and shutdown work runs outside the UI event threads.
 */
public final class PowerShellSession implements AutoCloseable {
    public enum State { NEW, STARTING, RUNNING, STOPPING, EXITED, FAILED, CLOSED }

    public interface Listener {
        /** Attach the terminal connector here; do not read this stream in a second consumer. */
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
    private final ExecutorService input = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "minic-powershell-input");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean exitReported = new AtomicBoolean();
    private volatile State state = State.NEW;
    private volatile boolean closing;
    private volatile PtyProcess process;
    private volatile WinSize size = new WinSize(100, 24);

    public PowerShellSession(Path workingDirectory, Listener listener) {
        this(workingDirectory, null, listener);
    }

    /** Runs a fixed executable before the interactive prompt; its input remains attached to ConPTY. */
    public PowerShellSession(Path workingDirectory, Path executable, Listener listener) {
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory")
                .toAbsolutePath().normalize();
        this.executable = executable == null ? null : executable.toAbsolutePath().normalize();
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public Path workingDirectory() { return workingDirectory; }
    public State state() { return state; }

    /** Starts once and returns immediately. Use a new session to restart after exit. */
    public void start() {
        synchronized (lifecycleLock) {
            if (state != State.NEW || closing) return;
            state = State.STARTING;
            background("minic-powershell-process", this::run);
        }
    }

    /** Queues raw UTF-8 input, including terminal escape sequences and control characters. */
    public boolean write(String text) {
        return write(Objects.requireNonNull(text, "text").getBytes(StandardCharsets.UTF_8));
    }

    /** Queues raw bytes in order without blocking either JavaFX or Swing. */
    public boolean write(byte[] bytes) {
        byte[] copy = Objects.requireNonNull(bytes, "bytes").clone();
        synchronized (lifecycleLock) {
            if (state != State.RUNNING || closing) return false;
            return enqueue(() -> {
                if (closing) return;
                try {
                    process.getOutputStream().write(copy);
                    process.getOutputStream().flush();
                } catch (IOException error) {
                    if (!closing && process.isAlive()) reportError(error);
                }
            });
        }
    }

    /** Remembers dimensions before startup and delivers later changes to the native console. */
    public void resize(int columns, int rows) {
        if (columns < 1 || rows < 1) return;
        WinSize requested = new WinSize(columns, rows);
        synchronized (lifecycleLock) {
            if (closing) return;
            size = requested;
            if (process == null || !process.isAlive()) return;
            enqueue(() -> {
                if (closing || !process.isAlive()) return;
                try {
                    process.setWinSize(requested);
                } catch (RuntimeException error) {
                    if (!closing && process.isAlive()) reportError(error);
                }
            });
        }
    }

    /** Nonblocking and idempotent. Terminates this shell and its descendant processes. */
    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (closing) return;
            closing = true;
            if (state == State.NEW) launched.complete(null);
            input.shutdownNow();
            Thread cleanup = new Thread(() -> {
                changeState(State.STOPPING);
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
                    changeState(State.CLOSED);
                }
            }, "minic-powershell-close");
            // Give bounded process cleanup a chance to finish when the app exits.
            cleanup.setDaemon(false);
            cleanup.start();
        }
    }

    private void run() {
        PtyProcess started = null;
        try {
            synchronized (lifecycleLock) {
                if (closing) return;
                notifySafely(() -> listener.onStateChanged(State.STARTING));
            }
            if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
                throw new IOException("PowerShell terminal requires Windows");
            }
            if (!Files.isDirectory(workingDirectory)) {
                throw new IOException("Terminal working directory does not exist: " + workingDirectory);
            }
            if (executable != null && !Files.isRegularFile(executable)) {
                throw new IOException("Run executable does not exist: " + executable);
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
                if (!size.equals(initialSize)) started.setWinSize(size);
                changeState(State.RUNNING);
                PtyProcess connected = started;
                notifySafely(() -> listener.onStarted(connected));
            }
            started.waitFor();
            reportExit(started);
            synchronized (lifecycleLock) {
                if (!closing) changeState(State.EXITED);
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
            }
        } finally {
            launched.complete(started);
            input.shutdownNow();
        }
    }

    private boolean enqueue(Runnable action) {
        try {
            input.execute(action);
            return true;
        } catch (RejectedExecutionException stopped) {
            return false;
        }
    }

    /** Encoded startup avoids typing a command into a shell whose prompt may not be ready yet. */
    List<String> commandLine() {
        if (executable == null) return List.of(powerShellExecutable(), "-NoLogo", "-NoProfile");
        // PowerShell recognizes typographic single quotes as delimiters as well. Double
        // every recognized quote; $, backticks, ampersands and semicolons stay literal.
        String literalPath = executable.toString().replace("'", "''").replace("\u2018", "\u2018\u2018")
                .replace("\u2019", "\u2019\u2019").replace("\u201a", "\u201a\u201a").replace("\u201b", "\u201b\u201b");
        String command = "& '" + literalPath + "'; "
                + "Write-Host; Write-Host ('[MiniC] Process exited with code {0}' -f $LASTEXITCODE)";
        String encoded = Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_16LE));
        return List.of(powerShellExecutable(), "-NoLogo", "-NoProfile", "-NoExit", "-EncodedCommand", encoded);
    }

    private static String powerShellExecutable() {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot != null) {
            Path path = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            if (Files.isRegularFile(path)) return path.toString();
        }
        return "powershell.exe";
    }

    private void changeState(State next) {
        synchronized (lifecycleLock) {
            state = next;
            notifySafely(() -> listener.onStateChanged(next));
        }
    }

    private void reportExit(PtyProcess started) {
        if (!started.isAlive() && exitReported.compareAndSet(false, true)) {
            int exitCode = started.exitValue();
            notifySafely(() -> listener.onExit(exitCode));
        }
    }

    private void reportError(Throwable error) {
        notifySafely(() -> listener.onError(error));
    }

    private static void notifySafely(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException ignored) {
            // A disposed view or faulty listener must not strand a native process.
        }
    }

    private static void background(String name, Runnable runnable) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        thread.start();
    }

    private static void terminateTree(PtyProcess started) {
        if (!started.isAlive()) return;
        // PtyProcess does not implement Process.toHandle(); obtain its native PID explicitly.
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
            // Termination has been requested; do not block application shutdown indefinitely.
        }
    }

    private static void closeStreams(PtyProcess started) {
        try { started.getOutputStream().close(); } catch (IOException ignored) { }
        try { started.getInputStream().close(); } catch (IOException ignored) { }
        try { started.getErrorStream().close(); } catch (IOException ignored) { }
    }
}
