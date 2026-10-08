package craken.ui.interaction.cases;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 一次用例运行：把预置文本像终端键入一样写入程序标准输入，并流式收集标准输出与标准错误。
 *
 * <p>启动、写入与读取都在后台线程完成，回调可能来自任意线程；调用者负责把界面更新
 * 切换到 JavaFX 线程并丢弃迟到回调。</p>
 *
 * <p>预置输入写完保持打开，不自动发送 EOF：程序继续读取时会等待，与真实终端一致；
 * {@link #sendEof()} 模拟终端里按 Ctrl+Z 回车，让等待输入的程序读到 EOF。
 * {@link #close()} 幂等且不等待进程结束，会结束整棵子进程树，之后不再报告退出或失败。</p>
 */
final class CaseRun implements AutoCloseable {
    /** 输出与结束回调；{@code exited} 与 {@code failed} 最多报告一次。 */
    interface Listener {
        void stdout(String text);

        void stderr(String text);

        /** 程序退出，{@code exitCode} 为退出码。 */
        void exited(int exitCode);

        /** 启动或运行失败，{@code message} 为原因。 */
        void failed(String message);
    }

    private final Path workingDirectory;
    private final Path executable;
    private final String input;
    private final Listener listener;
    private final Object lock = new Object();
    private final Object inputLock = new Object();
    private Process process;
    private boolean started;
    private boolean closing;
    private boolean eofRequested;
    private boolean finished;

    CaseRun(Path workingDirectory, Path executable, String input, Listener listener) {
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory")
                .toAbsolutePath().normalize();
        this.executable = Objects.requireNonNull(executable, "executable").toAbsolutePath().normalize();
        this.input = Objects.requireNonNull(input, "input");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /** 启动运行；重复调用只生效一次，启动失败通过 {@code failed} 报告。 */
    void start() {
        synchronized (lock) {
            if (started || closing) return;
            started = true;
        }
        Thread thread = new Thread(this::execute, "craken-case-run");
        thread.setDaemon(true);
        thread.start();
    }

    /** 结束这次运行拥有的整棵进程树；不阻塞调用线程，也不再回调。 */
    @Override
    public void close() {
        Process target;
        synchronized (lock) {
            if (closing) return;
            closing = true;
            target = process;
            process = null;
        }
        synchronized (inputLock) {
            inputLock.notifyAll();
        }
        if (target != null) terminate(target);
    }

    /**
     * 模拟终端里按 Ctrl+Z 回车：关闭标准输入，让仍在等待输入的程序读到 EOF。
     * 预置输入写完之前请求会等写入完成后生效，不会截断已经敲入的内容。
     */
    void sendEof() {
        synchronized (inputLock) {
            if (eofRequested) return;
            eofRequested = true;
            inputLock.notifyAll();
        }
    }

    private void execute() {
        Process startedProcess;
        try {
            ProcessBuilder builder = new ProcessBuilder(executable.toString());
            builder.directory(workingDirectory.toFile());
            startedProcess = builder.start();
        } catch (IOException | RuntimeException | LinkageError error) {
            failed("无法启动程序：" + describe(error));
            return;
        }
        synchronized (lock) {
            if (closing) {
                // close() 在进程创建完成前到达：直接丢弃刚启动的进程。
                terminate(startedProcess);
                return;
            }
            process = startedProcess;
        }
        try {
            // 两路输出与输入各占一个后台线程，程序等待输入时不会阻塞读取，反之也一样。
            Thread stdout = pump(startedProcess.getInputStream(), listener::stdout);
            Thread stderr = pump(startedProcess.getErrorStream(), listener::stderr);
            Thread stdin = new Thread(() -> feedInput(startedProcess), "craken-case-input");
            stdin.setDaemon(true);
            stdout.start();
            stderr.start();
            stdin.start();
            int exitCode = startedProcess.waitFor();
            synchronized (inputLock) {
                finished = true;
                inputLock.notifyAll();
            }
            // 等读取线程把剩余输出派发完，退出状态才会排在它们后面。
            stdout.join(TimeUnit.SECONDS.toMillis(5));
            stderr.join(TimeUnit.SECONDS.toMillis(5));
            stdin.join(TimeUnit.SECONDS.toMillis(5));
            synchronized (lock) {
                if (closing) return;
                process = null;
            }
            listener.exited(exitCode);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException | LinkageError error) {
            failed(describe(error));
        } finally {
            closeStreams(startedProcess);
        }
    }

    private void feedInput(Process target) {
        try (OutputStream stream = target.getOutputStream()) {
            stream.write(preparedInput().getBytes(StandardCharsets.UTF_8));
            stream.flush();
            synchronized (inputLock) {
                // 模拟终端：输入写完也不发送 EOF，等显式 EOF 或本轮结束再关闭标准输入。
                while (!eofRequested && !finished && !closing) inputLock.wait(250);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // 程序提前结束、被停止或显式 EOF 都会中断写入与等待，这不是运行失败。
        }
    }

    /** 非空且缺少行尾时补一个换行：等价于用户在终端敲完这一行后按回车。 */
    private String preparedInput() {
        if (input.isEmpty() || input.endsWith("\n") || input.endsWith("\r")) return input;
        return input + "\n";
    }

    private static Thread pump(InputStream source, Consumer<String> sink) {
        Thread thread = new Thread(() -> {
            char[] buffer = new char[8192];
            try (Reader reader = new InputStreamReader(source, StandardCharsets.UTF_8)) {
                int count;
                while ((count = reader.read(buffer)) >= 0) {
                    if (count > 0) sink.accept(new String(buffer, 0, count));
                }
            } catch (IOException ignored) {
                // 停止或退出会中断读取；结束状态由 exited/failed 或调用方的 close 决定。
            }
        }, "craken-case-output");
        thread.setDaemon(true);
        return thread;
    }

    private void failed(String message) {
        synchronized (lock) {
            if (closing) return;
            process = null;
        }
        listener.failed(message);
    }

    private static String describe(Throwable error) {
        return Objects.toString(error.getMessage(), error.getClass().getSimpleName());
    }

    private static void terminate(Process target) {
        if (!target.isAlive()) return;
        List<ProcessHandle> descendants = new ArrayList<>(target.descendants().toList());
        target.destroyForcibly();
        for (ProcessHandle child : descendants.reversed()) child.destroyForcibly();
    }

    private static void closeStreams(Process target) {
        try { target.getOutputStream().close(); } catch (IOException ignored) { }
        try { target.getInputStream().close(); } catch (IOException ignored) { }
        try { target.getErrorStream().close(); } catch (IOException ignored) { }
    }
}
