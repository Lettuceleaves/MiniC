package minic.ui.run;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingNode;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import minic.SourceRange;
import minic.compiler.Diagnostic;
import minic.compiler.SourceFile;
import minic.compiler.link.ExecutableArtifact;
import minic.ui.editor.EditorArea;
import minic.ui.editor.EditorFile;
import minic.ui.frame.AppFrame;
import minic.ui.interaction.InteractionArea;
import minic.ui.interaction.InteractionItem;
import minic.ui.interaction.terminal.TerminalPanel;
import minic.ui.interaction.inputoutput.InputOutputPanel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** 仅初始化工具包；不创建 Scene/Stage，不启动终端或实际编译器。 */
@EnabledIfSystemProperty(named = "minic.ui.test", matches = "true")
final class RunControllerTest {
    @TempDir Path directory;

    @BeforeAll
    static void toolkit() throws Exception {
        var started = new CompletableFuture<Void>();
        Runnable ready = () -> {
            Platform.setImplicitExit(false);
            started.complete(null);
        };
        try {
            Platform.startup(ready);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(ready);
        }
        started.get(10, TimeUnit.SECONDS);
    }

    @Test
    void disablesRunWithoutAnActiveFileAndTracksSelectionAvailability() throws Exception {
        Path sourcePath = source("availability.mc", "int main() { return 0; }");
        try (var compiler = new FakeCompiler(); var ui = fixture(compiler)) {
            onFx(() -> {
                assertNull(ui.editors.activeFile());
                assertTrue(ui.runButton().isDisabled());
                ui.runButton().fire();
                ui.controller.runActiveFile();
                assertEquals(1, ui.interactions.items().size());

                EditorFile file = ui.editors.openFile(sourcePath);
                assertFalse(ui.runButton().isDisabled());
                assertTrue(ui.editors.closeFile(file));
                assertTrue(ui.runButton().isDisabled());
                assertEquals(0, compiler.calls.get());
                assertNotStarted((TerminalPanel) ui.interactions.activeItem().content());
                return null;
            });
        }
    }

    @Test
    void capturesTheSelectedUnsavedBufferAtClickTimeAndCompilesOffBothUiThreads() throws Exception {
        String saved = "int main() { return 1; }\n";
        String clicked = "int main() { return 42; }\n";
        Path firstPath = source("first.mc", saved);
        Path secondPath = source("second.mc", "int main() { return 2; }\n");
        try (var compiler = new FakeCompiler(); var ui = fixture(compiler)) {
            RunPanel panel = onFx(() -> {
                EditorFile first = ui.editors.openFile(firstPath);
                EditorFile second = ui.editors.openFile(secondPath);
                ui.editors.select(first);
                first.editor().setSource(clicked);
                ui.runButton().fire();
                RunPanel run = ui.activeRun();

                assertTrue(ui.runButton().isDisabled());
                ui.runButton().fire();
                ui.controller.runActiveFile();
                assertEquals(2, ui.interactions.items().size(), "repeated clicks must not add another run");
                ui.editors.select(second);
                second.editor().setSource("int main() { return 99; }\n");
                first.editor().setSource("int main() { return 77; }\n");
                assertSame(second, ui.editors.activeFile());
                assertTrue(first.isDirty());
                return run;
            });
            Invocation invocation = compiler.next();

            assertEquals(new SourceFile(firstPath.toString(), clicked), invocation.source);
            assertEquals(directory.resolve("build").resolve("minic-runs"), invocation.outputRoot);
            assertFalse(invocation.fxThread);
            assertFalse(invocation.swingThread);
            assertEquals("minic-compile-run", invocation.thread.getName());
            assertEquals(1, compiler.calls.get());
            assertEquals(saved, Files.readString(firstPath));
            assertEquals("int main() { return 2; }\n", Files.readString(secondPath));

            complete(ui, invocation, failure());
            onFx(() -> {
                assertNull(panel.artifact());
                assertInstanceOf(TextArea.class, panel.getCenter());
                assertFalse(ui.runButton().isDisabled());
                return null;
            });
            assertEquals(saved, Files.readString(firstPath), "finishing a run must not save the editor");
        }
    }

    @Test
    void completedStagesUpdateOnlyTheirOwnProgressViewOnTheFxThread() throws Exception {
        Path sourcePath = source("progress.mc", "int main() { return 0; }");
        try (var compiler = new FakeCompiler(); var ui = fixture(compiler)) {
            RunPanel panel = start(ui, sourcePath);
            Invocation invocation = compiler.next();
            var wrongThreadUpdates = new AtomicInteger();
            ProgressBar bar = onFx(() -> {
                var progress = assertInstanceOf(ProgressBar.class, panel.lookup("#run-progress"));
                assertEquals(0, progress.getProgress());
                assertNull(panel.lookup("#run-diagnostics"));
                progress.progressProperty().addListener((observable, old, current) -> {
                    if (!Platform.isFxApplicationThread()) wrongThreadUpdates.incrementAndGet();
                });
                ui.interactions.addItem(new InteractionItem<>("其他面板", new Label("keep selected")));
                return progress;
            });
            invocation.onProgress.accept(new RunCompilation.Progress(1, 8));
            onFx(() -> { assertEquals(0.125, bar.getProgress()); return null; });
            invocation.onProgress.accept(new RunCompilation.Progress(6, 8));
            onFx(() -> {
                assertEquals(0.75, bar.getProgress());
                assertEquals("75%", ((Label) panel.lookup("#run-progress-percentage")).getText());
                assertEquals("其他面板", ui.interactions.activeItem().title());
                return null;
            });
            complete(ui, invocation, success(artifact("progress.exe")));
            invocation.onProgress.accept(new RunCompilation.Progress(2, 8));
            onFx(() -> {
                assertEquals(1, bar.getProgress());
                assertInstanceOf(InputOutputPanel.class, panel.getCenter());
                assertEquals("其他面板", ui.interactions.activeItem().title());
                assertEquals(0, wrongThreadUpdates.get());
                return null;
            });
        }
    }

    @Test
    void runButtonAndPublicIoFactoryShareOneStableNumberSequence() throws Exception {
        Path sourcePath = source("numbered.mc", "int main() { return 0; }");
        try (var compiler = new FakeCompiler(); var ui = fixture(compiler)) {
            onFx(() -> {
                var direct = ui.interactions.newInputOutput(directory, directory.resolve("direct.exe"));
                assertEquals("IO 1", direct.title());
                ui.editors.openFile(sourcePath);
                ui.runButton().fire();
                assertEquals("IO 2", ui.interactions.activeItem().title(), "number is assigned before compilation");
                ui.interactions.closeItem(direct);
                return null;
            });
            complete(ui, compiler.next(), failure());
            onFx(() -> {
                var previous = ui.interactions.activeItem();
                assertEquals("IO 2", previous.title(), "completion must keep the same list name");
                ui.runButton().fire();
                assertEquals("IO 3", ui.interactions.activeItem().title());
                assertEquals("IO 2", previous.title());
                assertEquals("IO 4", ui.interactions.newInputOutput(directory, directory.resolve("next.exe")).title());
                return null;
            });
            complete(ui, compiler.next(), success(artifact("numbered.exe")));
        }
    }

    @Test
    void createsInputOutputForTheReturnedArtifactWithoutLaunchingItWhenDetached() throws Exception {
        Path sourcePath = source("success.mc", "int main() { return 0; }");
        ExecutableArtifact executable = artifact("success.exe");
        try (var compiler = new FakeCompiler(); var ui = fixture(compiler)) {
            RunPanel panel = start(ui, sourcePath);

            complete(ui, compiler.next(), success(executable));

            onFx(() -> {
                assertSame(executable, panel.artifact());
                InputOutputPanel terminal = assertInstanceOf(InputOutputPanel.class, panel.getCenter());
                assertEquals(sourcePath.getParent(), terminal.workingDirectory());
                assertNotStarted(terminal);
                panel.activate();
                assertNotStarted(terminal);
                assertFalse(ui.runButton().isDisabled());
                assertNull(ui.frame.getScene());
                return null;
            });
        }
    }

    @Test
    void showsFailureDiagnosticsWithoutReusingAnEarlierSuccessfulArtifact() throws Exception {
        Path sourcePath = source("failure.mc", "int main() { return 0; }");
        ExecutableArtifact previousArtifact = artifact("previous.exe");
        try (var compiler = new FakeCompiler(); var ui = fixture(compiler)) {
            RunPanel previous = start(ui, sourcePath);
            complete(ui, compiler.next(), success(previousArtifact));
            RunPanel failed = onFx(() -> {
                ui.runButton().fire();
                return ui.activeRun();
            });

            complete(ui, compiler.next(), failure());

            onFx(() -> {
                assertNotSame(previous, failed);
                assertSame(previousArtifact, previous.artifact());
                assertNotStarted(assertInstanceOf(InputOutputPanel.class, previous.getCenter()));
                assertNull(failed.artifact());
                TextArea diagnostics = assertInstanceOf(TextArea.class, failed.getCenter());
                assertTrue(diagnostics.getText().contains("RUNTEST001"));
                assertTrue(diagnostics.getText().contains("unknown variable"));
                assertTrue(diagnostics.getText().contains("declare the variable"));
                assertTrue(diagnostics.getText().contains("第 2 行，UTF-8 字节位置 3"));
                assertTrue(((Label) failed.lookup("#run-status")).getText().contains("SemanticAnalyzer"));
                assertTrue(((Button) failed.lookup("#run-cancel")).isDisabled());
                assertFalse(ui.runButton().isDisabled());
                return null;
            });
        }
    }

    @ParameterizedTest
    @EnumSource(AbortAction.class)
    void cancellingOrClosingAPanelInterruptsTheWorkerAndIgnoresItsLateResult(AbortAction abort) throws Exception {
        Path sourcePath = source("cancel.mc", "int main() { return 0; }");
        try (var compiler = new FakeCompiler(); var ui = fixture(compiler)) {
            RunPanel abandoned = start(ui, sourcePath);
            Invocation first = compiler.next();
            var initialContent = onFx(abandoned::getCenter);
            ProgressBar oldProgress = onFx(() -> (ProgressBar) abandoned.lookup("#run-progress"));
            first.onProgress.accept(new RunCompilation.Progress(2, 8));
            onFx(() -> { assertEquals(0.25, oldProgress.getProgress()); return null; });
            RunPanel next = onFx(() -> {
                InteractionItem<?> item = ui.interactions.activeItem();
                if (abort == AbortAction.CANCEL_BUTTON) {
                    ((Button) abandoned.lookup("#run-cancel")).fire();
                    assertFalse(abandoned.isClosed());
                    assertEquals("已取消", ((Label) abandoned.lookup("#run-status")).getText());
                } else {
                    assertTrue(ui.interactions.closeItem(item));
                    assertTrue(item.isClosed());
                    assertTrue(abandoned.isClosed());
                    assertFalse(ui.interactions.items().contains(item));
                }
                assertFalse(ui.runButton().isDisabled(), "a cancelled request must release the run command");
                ui.runButton().fire();
                assertTrue(ui.runButton().isDisabled());
                return ui.activeRun();
            });
            assertTrue(first.interrupted.await(10, TimeUnit.SECONDS), "cancellation must interrupt the worker");

            first.onProgress.accept(new RunCompilation.Progress(7, 8));
            first.complete(success(artifact("late.exe")));
            // 单线程 worker 开始第二次调用前，已经为第一次调用排入 finish；下一次 onFx 因而位于它之后。
            Invocation second = compiler.next();
            onFx(() -> {
                assertNull(abandoned.artifact(), "late success must not start the abandoned run");
                if (abort == AbortAction.CANCEL_BUTTON) assertInstanceOf(TextArea.class, abandoned.getCenter());
                else assertSame(initialContent, abandoned.getCenter());
                assertEquals(0.25, oldProgress.getProgress(), "late progress must not update cancelled/closed runs");
                assertEquals(0, ((ProgressBar) next.lookup("#run-progress")).getProgress(),
                        "late progress from an old run must not update the new run");
                assertNotSame(abandoned, next);
                assertNull(next.artifact());
                assertTrue(ui.runButton().isDisabled(), "an older completion must not release the newer request");
                return null;
            });

            ExecutableArtifact nextArtifact = artifact("next.exe");
            complete(ui, second, success(nextArtifact));
            onFx(() -> {
                assertSame(nextArtifact, next.artifact());
                assertNotStarted(assertInstanceOf(InputOutputPanel.class, next.getCenter()));
                assertFalse(ui.runButton().isDisabled());
                return null;
            });
            assertEquals(2, compiler.calls.get());
        }
    }

    @Test
    void closingTheApplicationInterruptsCompilationAndRejectsLateSuccessAndNewRuns() throws Exception {
        Path sourcePath = source("shutdown.mc", "int main() { return 0; }");
        try (var compiler = new FakeCompiler(); var ui = fixture(compiler)) {
            RunPanel panel = start(ui, sourcePath);
            Invocation invocation = compiler.next();
            onFx(() -> {
                ui.controller.close();
                ui.interactions.close();
                assertTrue(panel.isClosed());
                assertTrue(ui.runButton().isDisabled());
                ui.controller.runActiveFile();
                ui.runButton().fire();
                return null;
            });
            assertTrue(invocation.interrupted.await(10, TimeUnit.SECONDS));

            invocation.complete(success(artifact("after-close.exe")));
            invocation.thread.join(TimeUnit.SECONDS.toMillis(10));
            assertFalse(invocation.thread.isAlive(), "shutdown must release the compilation worker");
            // worker 退出之前 finish 已入 FX 队列，因此该检查确定在迟到回调之后。
            onFx(() -> {
                assertNull(panel.artifact());
                assertInstanceOf(TextArea.class, panel.getCenter());
                assertTrue(ui.interactions.items().isEmpty());
                assertTrue(ui.runButton().isDisabled());
                assertEquals(1, compiler.calls.get());
                return null;
            });
        }
    }

    private Fixture fixture(FakeCompiler compiler) throws Exception {
        return onFx(() -> new Fixture(directory, compiler));
    }

    private Path source(String name, String text) throws Exception {
        return Files.writeString(directory.resolve(name), text).toRealPath();
    }

    private ExecutableArtifact artifact(String name) {
        return new ExecutableArtifact(directory.resolve("fake-output").resolve(name));
    }

    private static RunPanel start(Fixture ui, Path sourcePath) throws Exception {
        return onFx(() -> {
            ui.editors.openFile(sourcePath);
            ui.runButton().fire();
            return ui.activeRun();
        });
    }

    private static RunCompilation.Outcome success(ExecutableArtifact artifact) {
        return new RunCompilation.Outcome(artifact, List.of(), "");
    }

    private static RunCompilation.Outcome failure() {
        Diagnostic diagnostic = new Diagnostic("RUNTEST001", Diagnostic.Severity.ERROR,
                "unknown variable", "declare the variable", new SourceRange(2, 3, 2, 4));
        return new RunCompilation.Outcome(null, List.of(diagnostic), "SemanticAnalyzer");
    }

    private static void complete(Fixture ui, Invocation invocation, RunCompilation.Outcome outcome) throws Exception {
        CompletableFuture<Void> available = onFx(() -> {
            var ready = new CompletableFuture<Void>();
            Button button = ui.runButton();
            if (!button.isDisabled()) {
                ready.complete(null);
            } else {
                button.disabledProperty().addListener(new ChangeListener<>() {
                    @Override
                    public void changed(ObservableValue<? extends Boolean> observable, Boolean old, Boolean disabled) {
                        if (!disabled) {
                            button.disabledProperty().removeListener(this);
                            ready.complete(null);
                        }
                    }
                });
            }
            return ready;
        });
        invocation.complete(outcome);
        available.get(10, TimeUnit.SECONDS);
        onFx(() -> null); // availability is updated before panel content; let that same callback finish.
    }

    private static void assertNotStarted(TerminalPanel terminal) {
        assertNull(terminal.getScene());
        SwingNode surface = assertInstanceOf(SwingNode.class, terminal.lookup("#terminal-surface"));
        assertNull(surface.getContent(), "a detached run panel must not create a terminal session");
    }

    private static void assertNotStarted(InputOutputPanel inputOutput) {
        assertNull(inputOutput.getScene());
        SwingNode surface = assertInstanceOf(SwingNode.class, inputOutput.lookup("#input-output-surface"));
        assertNull(surface.getContent(), "a detached input/output panel must not start the program");
        assertFalse(inputOutput.isFinished());
        assertNull(inputOutput.exitCode());
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    private enum AbortAction { CANCEL_BUTTON, CLOSE_ITEM }

    private static final class Fixture implements AutoCloseable {
        final EditorArea editors = new EditorArea();
        final InteractionArea interactions;
        final AppFrame frame;
        final RunController controller;

        Fixture(Path directory, RunController.Compiler compiler) {
            interactions = new InteractionArea(directory);
            frame = new AppFrame(editors, new Region(), interactions, editors.tabBar());
            controller = new RunController(editors, frame, interactions, compiler);
        }

        Button runButton() {
            return assertInstanceOf(Button.class, frame.lookup("#app-run-button"));
        }

        RunPanel activeRun() {
            return assertInstanceOf(RunPanel.class, interactions.activeItem().content());
        }

        @Override
        public void close() throws Exception {
            onFx(() -> {
                controller.close();
                interactions.close();
                editors.setCloseDecisionHandler(file -> EditorArea.CloseChoice.DISCARD);
                assertTrue(editors.closeAll());
                return null;
            });
        }
    }

    /** 后端故意容忍中断以模拟无法立即取消的步骤，由测试控制迟到结果。 */
    private static final class FakeCompiler implements RunController.Compiler, AutoCloseable {
        final AtomicInteger calls = new AtomicInteger();
        private final BlockingQueue<Invocation> pending = new LinkedBlockingQueue<>();
        private final List<Invocation> all = new CopyOnWriteArrayList<>();

        @Override
        public RunCompilation.Outcome compile(SourceFile source, Path outputRoot,
                                              Consumer<RunCompilation.Progress> onProgress) throws Exception {
            Invocation invocation = new Invocation(source, outputRoot, onProgress);
            calls.incrementAndGet();
            all.add(invocation);
            pending.add(invocation);
            while (true) {
                try {
                    return invocation.result.get(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    invocation.interrupted.countDown();
                }
            }
        }

        Invocation next() throws InterruptedException {
            Invocation invocation = pending.poll(10, TimeUnit.SECONDS);
            assertNotNull(invocation, "the requested compilation must start");
            return invocation;
        }

        @Override
        public void close() throws InterruptedException {
            for (Invocation invocation : all) invocation.complete(failure());
            for (Invocation invocation : all) invocation.thread.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    private static final class Invocation {
        final SourceFile source;
        final Path outputRoot;
        final Thread thread = Thread.currentThread();
        final boolean fxThread = Platform.isFxApplicationThread();
        final boolean swingThread = SwingUtilities.isEventDispatchThread();
        final CountDownLatch interrupted = new CountDownLatch(1);
        final CompletableFuture<RunCompilation.Outcome> result = new CompletableFuture<>();
        final Consumer<RunCompilation.Progress> onProgress;

        Invocation(SourceFile source, Path outputRoot, Consumer<RunCompilation.Progress> onProgress) {
            this.source = source;
            this.outputRoot = outputRoot;
            this.onProgress = onProgress;
        }

        void complete(RunCompilation.Outcome outcome) {
            result.complete(outcome);
        }
    }
}
