package craken.ui.interaction.inputoutput;

import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.TerminalOutputStream;
import com.jediterm.terminal.TtyBasedArrayDataStream;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.emulator.mouse.MouseFormat;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalSelection;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import craken.compiler.SourceFile;
import craken.ui.run.RunCompilation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Real Craken artifacts, directly attached to ConPTY without a shell wrapper. */
@EnabledOnOs(OS.WINDOWS)
final class ProgramSessionTest {
    @TempDir Path temporary;

    @Test
    void commandIsASingleLiteralExecutableAndSessionCanCloseBeforeStart() throws Exception {
        Path executable = temporary.resolve("user's & $value; `literal").resolve("program.exe");
        try (Probe probe = new Probe(temporary, executable)) {
            assertEquals(List.of(executable.toAbsolutePath().normalize().toString()), probe.session.commandLine());
            assertEquals(temporary.toAbsolutePath().normalize(), probe.session.workingDirectory());
            assertEquals(executable.toAbsolutePath().normalize(), probe.session.executable());
            assertFalse(probe.session.write("not started"));
            assertTimeout(Duration.ofSeconds(1), probe.session::close);
            probe.await(() -> probe.states.contains(ProgramSession.State.CLOSED));
            probe.session.start();
            assertEquals(ProgramSession.State.CLOSED, probe.session.state());
            assertNull(probe.process);
            assertTrue(probe.errors.isEmpty());
        }
    }

    @Test
    void quickExitRetainsCompleteOutputUntilTheReaderAttaches() throws Exception {
        Path executable = compile("""
                #include <stdio.h>
                int main() {
                    puts("FIRST_NATIVE_OUTPUT");
                    int i = 0;
                    while (i < 80) { printf("line-%d\\n", i); i = i + 1; }
                    puts("FINAL_NATIVE_OUTPUT");
                    return 37;
                }
                """);
        CountDownLatch attach = new CountDownLatch(1);
        try (Probe probe = new Probe(temporary, executable, attach)) {
            probe.session.start();
            probe.await(() -> probe.session.state() == ProgramSession.State.EXITED);
            assertFalse(probe.session.write("after exit"));
            assertTrue(probe.exitCodes.isEmpty(), "onExit must not overtake a pending onStarted callback");
            attach.countDown();
            probe.awaitLine("FIRST_NATIVE_OUTPUT");
            probe.awaitLine("line-79");
            probe.awaitLine("FINAL_NATIVE_OUTPUT");
            probe.await(() -> probe.exitCodes.size() == 1 && probe.outputFinished);
            assertEquals(List.of(37), probe.exitCodes);
            assertTrue(probe.errors.isEmpty(), probe.errors.toString());
            assertEquals(List.of(ProgramSession.State.STARTING, ProgramSession.State.RUNNING,
                    ProgramSession.State.EXITED), probe.states);
            assertTrue(probe.callbackThreads.stream().allMatch(name -> name.equals("craken-program-events")));
        } finally {
            attach.countDown();
        }
    }

    @Test
    void standardInputAndResizingStayAttachedToTheActualProgram() throws Exception {
        Path executable = compile("""
                #include <stdio.h>
                int main() {
                    int value = 0;
                    puts("READY_FOR_INPUT");
                    scanf("%d", &value);
                    printf("READ_VALUE=%d\\n", value);
                    return value;
                }
                """);
        try (Probe probe = new Probe(temporary, executable)) {
            probe.session.resize(180, 40);
            probe.session.start();
            probe.awaitLine("READY_FOR_INPUT");
            assertTrue(probe.process.isAlive());
            assertEquals(new WinSize(180, 40), probe.process.getWinSize());
            probe.session.resize(120, 32);
            probe.await(() -> {
                try { return new WinSize(120, 32).equals(probe.process.getWinSize()); }
                catch (IOException error) { throw new AssertionError(error); }
            });
            assertTrue(probe.session.write("23\r"));
            probe.awaitLine("READ_VALUE=23");
            probe.await(() -> probe.exitCodes.size() == 1);
            assertEquals(List.of(23), probe.exitCodes);
            assertEquals(ProgramSession.State.EXITED, probe.session.state());
            assertFalse(probe.session.write("late\r"));
            assertTrue(probe.errors.isEmpty(), probe.errors.toString());
        }
    }

    @Test
    void utf8SourceOutputAndInteractiveInputRoundTrip() throws Exception {
        Path executable = compile("""
                #include <stdio.h>
                int main() {
                    char word[64];
                    puts("等待中文输入");
                    scanf("%63s", word);
                    printf("收到：%s\\n", word);
                    return 0;
                }
                """);
        try (Probe probe = new Probe(temporary, executable)) {
            probe.session.start();
            probe.awaitLine("等待中文输入");
            assertTrue(probe.session.write("你好世界\r"));
            probe.awaitLine("收到：你好世界");
            probe.await(() -> probe.exitCodes.size() == 1);
            assertEquals(List.of(0), probe.exitCodes);
            assertTrue(probe.errors.isEmpty(), probe.errors.toString());
        }
    }

    @Test
    void specialCharactersInAnExecutablePathAreLiteralAndWorkingDirectoryIsRespected() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("work ' & $value; `literal"));
        Files.writeString(root.resolve("before.txt"), "owned test marker");
        Path built = compile("""
                #include <stdio.h>
                int main() {
                    int result = rename("before.txt", "after.txt");
                    puts("LITERAL_PATH_RAN");
                    return result;
                }
                """);
        Path executable = Files.copy(built, root.resolve("program ' & $value; `literal.exe"));
        try (Probe probe = new Probe(root, executable)) {
            probe.session.start();
            probe.awaitLine("LITERAL_PATH_RAN");
            probe.await(() -> probe.exitCodes.size() == 1);
            assertEquals(List.of(0), probe.exitCodes);
            assertFalse(Files.exists(root.resolve("before.txt")));
            assertEquals("owned test marker", Files.readString(root.resolve("after.txt")));
            assertFalse(probe.output().contains("PowerShell"));
            assertTrue(probe.errors.isEmpty(), probe.errors.toString());
        }
    }

    @Test
    void closeIsNonblockingAndTerminatesOnlyThisOwnedProgram() throws Exception {
        Path executable = waitingProgram();
        try (Probe first = new Probe(temporary, executable); Probe second = new Probe(temporary, executable)) {
            first.session.start();
            second.session.start();
            first.awaitLine("WAITING_NATIVE_INPUT");
            second.awaitLine("WAITING_NATIVE_INPUT");
            long ownPid = first.process.pid();
            assertTimeout(Duration.ofSeconds(1), first.session::close);
            first.session.close();
            first.await(() -> first.states.contains(ProgramSession.State.CLOSED));
            assertFalse(ProcessHandle.of(ownPid).map(ProcessHandle::isAlive).orElse(false));
            assertTrue(second.process.isAlive(), "closing an item must not terminate another session");
            assertEquals(1, first.exitCodes.size());
            assertFalse(first.session.write("closed"));
            assertTrue(first.errors.isEmpty(), first.errors.toString());
        }
    }

    @Test
    void closingDuringStartupDoesNotLeaveAProcessOrRestartTheSession() throws Exception {
        Path executable = waitingProgram();
        for (int attempt = 0; attempt < 6; attempt++) {
            try (Probe probe = new Probe(temporary, executable)) {
                probe.session.start();
                assertTimeout(Duration.ofSeconds(1), probe.session::close);
                probe.await(() -> probe.states.contains(ProgramSession.State.CLOSED));
                probe.session.start();
                assertEquals(ProgramSession.State.CLOSED, probe.session.state());
                assertTrue(probe.process == null || !probe.process.isAlive());
                assertEquals(ProgramSession.State.CLOSED, probe.states.getLast());
                assertFalse(probe.session.write("closed"));
                assertTrue(probe.errors.isEmpty(), probe.errors.toString());
            }
        }
    }

    @Test
    void delayedViewCallbackDoesNotBlockClosingTheProgram() throws Exception {
        Path executable = waitingProgram();
        CountDownLatch attach = new CountDownLatch(1);
        try (Probe probe = new Probe(temporary, executable, attach)) {
            probe.session.start();
            probe.await(() -> probe.process != null);
            assertTimeout(Duration.ofSeconds(1), probe.session::close);
            probe.await(() -> probe.session.state() == ProgramSession.State.CLOSED);
            assertFalse(probe.process.isAlive());
            attach.countDown();
            probe.await(() -> probe.states.contains(ProgramSession.State.CLOSED));
        } finally {
            attach.countDown();
        }
    }

    @Test
    void missingExecutableOrDirectoryReportsFailureWithoutLaunching() throws Exception {
        Path executable = compile("int main() { return 0; }");
        for (boolean missingExecutable : List.of(true, false)) {
            try (Probe probe = new Probe(missingExecutable ? temporary : temporary.resolve("missing-dir"),
                    missingExecutable ? temporary.resolve("missing.exe") : executable)) {
                probe.session.start();
                probe.await(() -> probe.session.state() == ProgramSession.State.FAILED && !probe.errors.isEmpty());
                assertEquals(1, probe.errors.size());
                assertNull(probe.process);
                assertFalse(probe.session.write("failed"));
                assertTrue(probe.exitCodes.isEmpty());
            }
        }
    }

    private Path waitingProgram() throws Exception {
        return compile("""
                #include <stdio.h>
                int main() {
                    puts("WAITING_NATIVE_INPUT");
                    while (1) { getchar(); }
                    return 0;
                }
                """);
    }

    private Path compile(String text) throws Exception {
        var outcome = new RunCompilation().compile(new SourceFile(temporary.resolve("program.mc").toString(), text),
                temporary.resolve("artifacts"));
        assertTrue(outcome.succeeded(), () -> outcome.failedStage() + ": " + outcome.diagnostics());
        return outcome.artifact().path();
    }

    private static final class Probe implements ProgramSession.Listener, AutoCloseable {
        final ProgramSession session;
        final StyleState style = new StyleState();
        final TerminalTextBuffer buffer = new TerminalTextBuffer(180, 40, style, 1_000);
        final JediTerminal terminal = new JediTerminal(new QuietDisplay(), buffer, style);
        final List<ProgramSession.State> states = new CopyOnWriteArrayList<>();
        final List<Integer> exitCodes = new CopyOnWriteArrayList<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final List<String> callbackThreads = new CopyOnWriteArrayList<>();
        final CountDownLatch attach;
        volatile PtyProcess process;
        volatile boolean outputFinished;

        Probe(Path root, Path executable) { this(root, executable, new CountDownLatch(0)); }

        Probe(Path root, Path executable, CountDownLatch attach) {
            this.attach = attach;
            session = new ProgramSession(root, executable, this);
            session.resize(180, 40);
            terminal.setTerminalOutput(new TerminalOutputStream() {
                @Override public void sendBytes(byte[] response, boolean userInput) { session.write(response); }
                @Override public void sendString(String response, boolean userInput) { session.write(response); }
            });
        }

        @Override public void onStarted(PtyProcess process) {
            callbackThreads.add(Thread.currentThread().getName());
            this.process = process;
            signal();
            try {
                if (!attach.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("reader attachment timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                errors.add(interrupted);
                return;
            }
            if (session.state() == ProgramSession.State.CLOSED) return;
            var connector = new ProcessTtyConnector(process, StandardCharsets.UTF_8) {
                @Override public String getName() { return "native test program"; }
                @Override public boolean isConnected() { return !outputFinished; }
            };
            var emulator = new JediEmulator(new TtyBasedArrayDataStream(connector), terminal);
            Thread reader = new Thread(() -> {
                try {
                    while (emulator.hasNext()) {
                        emulator.next();
                        signal();
                    }
                } catch (IOException error) {
                    if (process.isAlive() && session.state() == ProgramSession.State.RUNNING) errors.add(error);
                } finally {
                    outputFinished = true;
                    signal();
                }
            }, "craken-program-test-reader");
            reader.setDaemon(true);
            reader.start();
        }

        @Override public void onStateChanged(ProgramSession.State state) {
            callbackThreads.add(Thread.currentThread().getName());
            states.add(state);
            signal();
        }

        @Override public void onExit(int exitCode) {
            callbackThreads.add(Thread.currentThread().getName());
            exitCodes.add(exitCode);
            signal();
        }

        @Override public void onError(Throwable error) {
            callbackThreads.add(Thread.currentThread().getName());
            errors.add(error);
            signal();
        }

        private synchronized void signal() { notifyAll(); }

        String output() {
            buffer.lock();
            try {
                StringBuilder text = new StringBuilder();
                for (var line : buffer.getHistoryLinesStorage()) text.append(line.getText()).append('\n');
                for (var line : buffer.getScreenLinesStorage()) text.append(line.getText()).append('\n');
                return text.toString().replace("\uE000", "").replace("\u0000", "");
            } finally {
                buffer.unlock();
            }
        }

        void awaitLine(String line) throws InterruptedException {
            await(() -> output().lines().map(String::stripTrailing).anyMatch(line::equals));
        }

        void await(BooleanSupplier ready) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (System.nanoTime() < deadline) {
                if (ready.getAsBoolean()) return;
                synchronized (this) { wait(25); }
            }
            fail("Timed out. States: " + states + "\nErrors: " + errors + "\nOutput:\n" + output());
        }

        @Override public void close() throws InterruptedException {
            attach.countDown();
            session.close();
            await(() -> states.contains(ProgramSession.State.CLOSED));
        }
    }

    private static final class QuietDisplay implements TerminalDisplay {
        @Override public void setCursor(int x, int y) { }
        @Override public void setCursorShape(CursorShape shape) { }
        @Override public void beep() { }
        @Override public void scrollArea(int top, int size, int dy) { }
        @Override public void setCursorVisible(boolean visible) { }
        @Override public void useAlternateScreenBuffer(boolean alternate) { }
        @Override public String getWindowTitle() { return "native test program"; }
        @Override public void setWindowTitle(String title) { }
        @Override public TerminalSelection getSelection() { return null; }
        @Override public void terminalMouseModeSet(MouseMode mode) { }
        @Override public void setMouseFormat(MouseFormat format) { }
        @Override public boolean ambiguousCharsAreDoubleWidth() { return false; }
    }
}
