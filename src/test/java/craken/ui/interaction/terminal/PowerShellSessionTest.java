package craken.ui.interaction.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.RequestOrigin;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Real ConPTY/PowerShell with a headless JediTerm emulator as the sole output reader. */
@EnabledOnOs(OS.WINDOWS)
final class PowerShellSessionTest {
    @TempDir Path temporary;

    @Test
    void fixedExecutableIsOneEncodedStartupArgumentWithALiteralAbsolutePath() throws Exception {
        Path executable = temporary.resolve("user's build & $value; `literal").resolve("program.exe");
        try (var probe = new Probe(temporary, executable)) {
            List<String> command = probe.session.commandLine();
            assertEquals(List.of("-NoLogo", "-NoProfile", "-NoExit", "-EncodedCommand"), command.subList(1, 5));
            assertEquals(6, command.size());
            assertTrue(command.getLast().matches("[A-Za-z0-9+/]+={0,2}"));
            String script = new String(Base64.getDecoder().decode(command.getLast()), StandardCharsets.UTF_16LE);
            assertTrue(script.startsWith("& '" + executable.toAbsolutePath().normalize().toString()
                    .replace("'", "''") + "'; "));
            assertTrue(script.endsWith("Write-Host ('[Craken] Process exited with code {0}' -f $LASTEXITCODE)"));
        }
        try (var probe = new Probe(temporary)) {
            assertEquals(List.of("-NoLogo", "-NoProfile"), probe.session.commandLine().subList(1, 3));
            assertEquals(3, probe.session.commandLine().size(), "ordinary shells must retain their original startup");
        }
    }

    @Test
    void executableStartsBeforePromptWithLiteralPathAndReportsItsExitCode() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("run directory with spaces")).toRealPath();
        Path executable = Files.copy(Path.of(System.getenv("SystemRoot"), "System32", "cmd.exe"),
                root.resolve("program'; Write-Output INJECTED; '‘curly’ ‚lower‛.exe"));
        try (var probe = new Probe(root, executable)) {
            probe.startProcess();
            probe.await(() -> probe.currentLine().endsWith(">"));
            probe.command("echo NATIVE_STARTED");
            probe.awaitLine("NATIVE_STARTED");
            probe.command("echo WORKING=%CD%");
            probe.awaitLine("WORKING=" + root);
            probe.command("exit 23");
            probe.awaitLine("[Craken] Process exited with code 23");
            probe.awaitPrompt();
            assertEquals(PowerShellSession.State.RUNNING, probe.session.state(), "the shell stays interactive after the program exits");
            probe.command("Write-Output 'AFTER_PROGRAM'");
            probe.awaitLine("AFTER_PROGRAM");
            assertFalse(probe.output().lines().anyMatch(line -> line.strip().equals("INJECTED")));
            assertTrue(probe.errors.isEmpty(), probe.errors.toString());
        }
    }

    @Test
    void nativeExecutableKeepsInteractiveStandardInputConnected() throws Exception {
        // Keep cmd.exe beside its installed MUI resources: a renamed copy can run echo,
        // but set /p may fail to format its prompt on localized Windows installations.
        Path executable = Path.of(System.getenv("SystemRoot"), "System32", "cmd.exe");
        try (var probe = new Probe(temporary, executable)) {
            probe.startProcess();
            probe.await(() -> probe.currentLine().endsWith(">"));
            probe.command("set /p answer=APP_INPUT:");
            probe.awaitLine("APP_INPUT:");
            probe.command("native answer");
            probe.command("echo ANSWER=%answer%");
            probe.awaitLine("ANSWER=native answer");
            probe.command("exit 0");
            probe.awaitLine("[Craken] Process exited with code 0");
            probe.awaitPrompt();
            assertTrue(probe.errors.isEmpty(), probe.errors.toString());
        }
    }

    @Test
    void closingExecutableSessionTerminatesItsNativeProgram() throws Exception {
        Path executable = Path.of(System.getenv("SystemRoot"), "System32", "cmd.exe");
        try (var probe = new Probe(temporary, executable)) {
            probe.startProcess();
            probe.await(() -> probe.currentLine().endsWith(">"));
            long shellPid = probe.process.pid();
            List<ProcessHandle> children = ProcessHandle.of(shellPid).orElseThrow().descendants().toList();
            assertFalse(children.isEmpty(), "the program must run as a child of the session shell");
            assertTrue(children.stream().anyMatch(ProcessHandle::isAlive));
            assertTimeout(Duration.ofSeconds(1), probe.session::close);
            probe.await(() -> probe.session.state() == PowerShellSession.State.CLOSED);
            assertFalse(alive(shellPid));
            assertTrue(children.stream().noneMatch(ProcessHandle::isAlive), "native program was left running");
            assertTrue(probe.errors.isEmpty(), probe.errors.toString());
        }
    }

    @Test
    void missingExecutableReportsFailureBeforeStartingTheShell() throws Exception {
        try (var probe = new Probe(temporary, temporary.resolve("missing.exe"))) {
            probe.session.start();
            probe.await(() -> probe.session.state() == PowerShellSession.State.FAILED);
            assertEquals(1, probe.errors.size());
            assertTrue(probe.errors.getFirst().getMessage().contains("executable"));
            assertNull(probe.process);
        }
    }

    @Test
    void persistsDirectoryAndVariablesAndSupportsUtf8AndInteractiveInput() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("项目 with spaces")).toRealPath();
        Path child = Files.createDirectories(root.resolve("子目录 next"));
        try (var probe = new Probe(root)) {
            assertFalse(probe.session.write("ignored"));
            probe.start();
            probe.session.start();
            probe.command("$kept = 41; Write-Output ('PWD=' + (Get-Location).Path); "
                    + "Write-Output ('UTF8=' + [string]'你好世界')");
            probe.awaitLine("PWD=" + root);
            probe.awaitLine("UTF8=你好世界");
            probe.command("Set-Location -LiteralPath '子目录 next'");
            probe.awaitPrompt();
            probe.command("$kept++; Write-Output ('STATE=' + $kept); Write-Output ('PWD=' + (Get-Location).Path)");
            probe.awaitLine("STATE=42");
            probe.awaitLine("PWD=" + child);

            probe.command("Write-Output 'READ_READY'; $answer = Read-Host 'Name'; Write-Output ('READ=' + $answer)");
            probe.awaitLine("READ_READY");
            probe.await(() -> probe.output().contains("Name:"));
            probe.command("输入 中文");
            probe.awaitLine("READ=输入 中文");

            probe.command("cmd /v:on /c 'set /p value=NativeInput: & echo NATIVE=!value!'");
            probe.await(() -> probe.currentLine().startsWith("NativeInput:"));
            probe.command("native answer");
            probe.await(() -> probe.output().contains("NATIVE=native answer"));

            probe.command("exit 7");
            probe.await(() -> probe.session.state() == PowerShellSession.State.EXITED);
            assertEquals(List.of(7), probe.exitCodes);
            assertFalse(probe.session.write("too late"));
            assertTrue(probe.errors.isEmpty(), probe.errors.toString());
            assertTrue(probe.callbackThreads.stream().allMatch(name -> name.startsWith("craken-powershell-")));
            assertEquals(1, probe.states.stream().filter(state -> state == PowerShellSession.State.RUNNING).count());
        }
    }

    @Test
    void consoleResizesAndAnsiAlternateScreenReturnsToTheShell() throws Exception {
        try (var probe = new Probe(temporary)) {
            probe.start();
            assertEquals(new WinSize(180, 40), probe.process.getWinSize());
            probe.resize(110, 30);
            probe.await(() -> {
                try { return new WinSize(110, 30).equals(probe.process.getWinSize()); }
                catch (IOException error) { throw new AssertionError(error); }
            });
            probe.command("Write-Output ('COLUMNS=' + $Host.UI.RawUI.WindowSize.Width); "
                    + "Write-Output ('ROWS=' + $Host.UI.RawUI.WindowSize.Height)");
            probe.awaitLine("COLUMNS=110");
            probe.awaitLine("ROWS=30");

            probe.command("[Console]::Write([string][char]27 + '[?1049h' + [char]27 + '[2J' + [char]27 + '[H' + 'ALT_BUFFER'); "
                    + "Start-Sleep -Seconds 1; [Console]::Write([string][char]27 + '[?1049l'); Write-Output 'BACK_FROM_ALT'");
            probe.await(() -> probe.buffer.isUsingAlternateBuffer() && probe.output().contains("ALT_BUFFER"));
            probe.awaitLine("BACK_FROM_ALT");
            assertFalse(probe.buffer.isUsingAlternateBuffer(), "returning from a full-screen program must restore the main screen");
            assertTrue(probe.errors.isEmpty(), probe.errors.toString());
        }
    }

    @Test
    void closeIsNonblockingAndTerminatesShellAndChildProcess() throws Exception {
        try (var probe = new Probe(temporary)) {
            probe.start();
            probe.command("Write-Output ('SHELL=' + $PID); "
                    + "$child = Start-Process powershell.exe -WindowStyle Hidden -PassThru "
                    + "-ArgumentList '-NoLogo', '-NoProfile', '-Command', 'Start-Sleep -Seconds 60'; "
                    + "Write-Output ('CHILD=' + $child.Id); Wait-Process -Id $child.Id");
            probe.await(() -> probe.number("CHILD") != null);
            long shellPid = probe.number("SHELL");
            long childPid = probe.number("CHILD");
            assertTrue(alive(shellPid));
            assertTrue(alive(childPid));
            assertTimeout(Duration.ofSeconds(1), probe.session::close);
            probe.session.close();
            probe.await(() -> probe.session.state() == PowerShellSession.State.CLOSED);
            assertFalse(alive(shellPid), "shell was left running");
            assertFalse(alive(childPid), "child process was left running");
            assertFalse(probe.session.write("ignored"));
            assertEquals(1, probe.exitCodes.size());
            assertTrue(probe.errors.isEmpty(), probe.errors.toString());
        }
    }

    @Test
    void missingWorkingDirectoryReportsFailureAndCanBeClosed() throws Exception {
        try (var probe = new Probe(temporary.resolve("missing"))) {
            probe.session.start();
            probe.await(() -> probe.session.state() == PowerShellSession.State.FAILED);
            assertEquals(1, probe.errors.size());
            assertTrue(probe.errors.getFirst().getMessage().contains("working directory"));
            assertTrue(probe.exitCodes.isEmpty());
            assertFalse(probe.session.write("ignored"));
            assertNull(probe.process);
        }
    }

    @Test
    void closingBeforeOrDuringStartupDoesNotStartAnotherSession() throws Exception {
        for (boolean startFirst : List.of(false, true)) {
            try (var probe = new Probe(temporary)) {
                if (startFirst) probe.session.start();
                probe.session.close();
                probe.await(() -> probe.session.state() == PowerShellSession.State.CLOSED);
                probe.session.start();
                assertEquals(PowerShellSession.State.CLOSED, probe.session.state());
                assertFalse(probe.session.write("ignored"));
                assertEquals(PowerShellSession.State.CLOSED, probe.states.getLast());
                assertTrue(probe.errors.isEmpty(), probe.errors.toString());
            }
        }
    }

    private static boolean alive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    private static final class Probe implements PowerShellSession.Listener, AutoCloseable {
        final PowerShellSession session;
        final StyleState style = new StyleState();
        final TerminalTextBuffer buffer = new TerminalTextBuffer(180, 40, style);
        final JediTerminal terminal = new JediTerminal(new QuietDisplay(), buffer, style);
        final List<PowerShellSession.State> states = new CopyOnWriteArrayList<>();
        final List<Integer> exitCodes = new CopyOnWriteArrayList<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final List<String> callbackThreads = new CopyOnWriteArrayList<>();
        volatile PtyProcess process;

        Probe(Path root) {
            this(root, null);
        }

        Probe(Path root, Path executable) {
            session = new PowerShellSession(root, executable, this);
            session.resize(180, 40);
            terminal.setTerminalOutput(new TerminalOutputStream() {
                @Override public void sendBytes(byte[] response, boolean userInput) { session.write(response); }
                @Override public void sendString(String response, boolean userInput) { session.write(response); }
            });
        }

        void start() throws InterruptedException {
            startProcess();
            awaitPrompt();
        }

        void startProcess() throws InterruptedException {
            session.start();
            await(() -> process != null && session.state() == PowerShellSession.State.RUNNING);
        }

        void command(String command) { assertTrue(session.write(command + "\r")); }

        void resize(int columns, int rows) {
            terminal.resize(new TermSize(columns, rows), RequestOrigin.User);
            session.resize(columns, rows);
        }

        @Override public void onStarted(PtyProcess process) {
            callbackThreads.add(Thread.currentThread().getName());
            this.process = process;
            var connector = new ProcessTtyConnector(process, StandardCharsets.UTF_8) {
                @Override public String getName() { return "test PowerShell"; }
            };
            var emulator = new JediEmulator(new TtyBasedArrayDataStream(connector), terminal);
            Thread reader = new Thread(() -> {
                try {
                    while (emulator.hasNext()) {
                        emulator.next();
                        signal();
                    }
                } catch (IOException error) {
                    if (process.isAlive() && session.state() == PowerShellSession.State.RUNNING) errors.add(error);
                    signal();
                }
            }, "craken-powershell-test-emulator");
            reader.setDaemon(true);
            reader.start();
            signal();
        }

        @Override public void onStateChanged(PowerShellSession.State state) {
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

        String currentLine() {
            buffer.lock();
            try {
                return buffer.getLine(terminal.getCursorY() - 1).getText()
                        .replace("\uE000", "").replace("\u0000", "").stripTrailing();
            } finally {
                buffer.unlock();
            }
        }

        private Long number(String key) {
            var matcher = Pattern.compile("(?m)^" + key + "=(\\d+)\\s*$").matcher(output());
            return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
        }

        void awaitPrompt() throws InterruptedException { await(() -> currentLine().matches("PS .*>")); }

        void awaitLine(String line) throws InterruptedException {
            await(() -> output().lines().map(String::stripTrailing).anyMatch(line::equals));
        }

        void await(BooleanSupplier ready) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (System.nanoTime() < deadline) {
                if (ready.getAsBoolean()) return;
                synchronized (this) { wait(25); }
            }
            fail("Timed out. States: " + new ArrayList<>(states)
                    + "\nErrors: " + errors + "\nOutput:\n" + output());
        }

        @Override public void close() throws InterruptedException {
            session.close();
            await(() -> session.state() == PowerShellSession.State.CLOSED);
        }
    }

    private static final class QuietDisplay implements TerminalDisplay {
        @Override public void setCursor(int x, int y) { }
        @Override public void setCursorShape(CursorShape shape) { }
        @Override public void beep() { }
        @Override public void scrollArea(int top, int size, int dy) { }
        @Override public void setCursorVisible(boolean visible) { }
        @Override public void useAlternateScreenBuffer(boolean alternate) { }
        @Override public String getWindowTitle() { return "test PowerShell"; }
        @Override public void setWindowTitle(String title) { }
        @Override public TerminalSelection getSelection() { return null; }
        @Override public void terminalMouseModeSet(MouseMode mode) { }
        @Override public void setMouseFormat(MouseFormat format) { }
        @Override public boolean ambiguousCharsAreDoubleWidth() { return false; }
    }
}
