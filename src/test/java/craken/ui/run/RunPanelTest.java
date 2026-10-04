package craken.ui.run;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.embed.swing.SwingNode;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import craken.SourceRange;
import craken.compiler.Diagnostic;
import craken.compiler.link.ExecutableArtifact;
import craken.ui.component.UiStyles;
import craken.ui.component.display.UiProgressBar;
import craken.ui.component.input.UiTextArea;
import craken.ui.interaction.inputoutput.InputOutputPanel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 只初始化 FX 工具包与离屏 Scene；不显示窗口，不启动编译器或原生程序。 */
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
final class RunPanelTest {
    @TempDir Path temporary;

    @BeforeAll
    static void toolkit() throws Exception {
        var started = new CompletableFuture<Void>();
        Runnable ready = () -> { Platform.setImplicitExit(false); started.complete(null); };
        try { Platform.startup(ready); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(ready); }
        started.get(10, TimeUnit.SECONDS);
    }

    @Test
    void initiallyKeepsTheVisibleInputOutputHeaderAboveTheProgressView() throws Exception {
        onFx(() -> {
            var cancellations = new AtomicInteger();
            try (RunPanel panel = panel(cancellations::incrementAndGet)) {
                StackPane view = assertInstanceOf(StackPane.class, panel.getCenter());
                assertEquals("run-progress-view", view.getId());
                UiProgressBar progress = progress(panel);
                assertSame(view, progress.getParent());
                assertEquals(0, progress.getProgress());
                assertEquals(0, progress.getMinWidth());
                assertEquals(0, progress.getMinHeight());
                assertEquals(Double.MAX_VALUE, progress.getMaxWidth());
                assertEquals(Double.MAX_VALUE, progress.getMaxHeight());
                assertEquals("0%", percentage(panel));
                assertNull(panel.lookup("#run-diagnostics"), "compilation does not attach a diagnostics editor");
                HBox toolbar = assertInstanceOf(HBox.class, panel.lookup("#run-cancel").getParent());
                assertSame(toolbar, panel.getTop(), "the title toolbar has its own row above the progress area");
                assertSame(panel, toolbar.getParent());
                assertFalse(view.getChildren().contains(toolbar));
                assertFalse(toolbar.getStyleClass().contains("run-progress-toolbar"));
                Label title = assertInstanceOf(Label.class, panel.lookup("#run-title"));
                assertEquals("输入输出 · current.mc", title.getText());
                assertSame(toolbar, title.getParent());
                assertTrue(title.isVisible());
                assertTrue(title.isManaged());
                Label status = assertInstanceOf(Label.class, panel.lookup("#run-status"));
                assertTrue(status.isVisible());
                assertTrue(status.isManaged());
                Button cancel = assertInstanceOf(Button.class, panel.lookup("#run-cancel"));
                assertFalse(cancel.isDisabled());
                cancel.fire();
                assertEquals(1, cancellations.get());
            }
            return null;
        });
    }

    @Test
    void stageProgressUpdatesTheFractionAndPercentageWithoutRegressing() throws Exception {
        onFx(() -> {
            try (RunPanel panel = panel(() -> { })) {
                panel.progress(new RunCompilation.Progress(2, 8));
                assertEquals(0.25, progress(panel).getProgress());
                assertEquals("25%", percentage(panel));
                panel.progress(new RunCompilation.Progress(6, 8));
                assertEquals(0.75, progress(panel).getProgress());
                assertEquals("75%", percentage(panel));
                panel.progress(new RunCompilation.Progress(2, 8));
                assertEquals(0.75, progress(panel).getProgress());
                assertEquals("75%", percentage(panel));
                panel.progress(new RunCompilation.Progress(0, 8));
                assertEquals(0.75, progress(panel).getProgress());
                assertEquals("75%", percentage(panel));
            }
            return null;
        });
    }

    @ParameterizedTest
    @CsvSource({"800, 200", "600, 150", "600, 64"})
    void progressSkinFillsOnlyTheAreaBelowTheHeaderAndShowsTheActualFraction(int width, int height) throws Exception {
        onFx(() -> {
            int contentHeight = height - 32;
            try (RunPanel panel = panel(() -> { })) {
                panel.progress(new RunCompilation.Progress(4, 8));
                Scene scene = new Scene(panel, width, height);
                UiStyles.install(scene);
                panel.resize(width, height);
                panel.applyCss();
                panel.layout();
                UiProgressBar progress = progress(panel);
                progress.layout();
                Region view = assertInstanceOf(Region.class, panel.getCenter());
                Region track = assertInstanceOf(Region.class, progress.lookup(".track"));
                Region bar = assertInstanceOf(Region.class, progress.lookup(".bar"));
                HBox toolbar = assertInstanceOf(HBox.class, panel.getTop());
                assertEquals(32, toolbar.getHeight(), 0.5);
                Label title = assertInstanceOf(Label.class, panel.lookup("#run-title"));
                assertTrue(title.isVisible());
                assertTrue(title.isManaged());
                assertEquals("输入输出 · current.mc", title.getText());
                assertSame(toolbar, title.getParent());
                assertEquals(width, view.getWidth(), 0.5);
                assertEquals(contentHeight, view.getHeight(), 0.5);
                Rectangle clip = assertInstanceOf(Rectangle.class, view.getClip());
                assertEquals(width, clip.getWidth(), 0.5);
                assertEquals(contentHeight, clip.getHeight(), 0.5,
                        "the progress and percentage must stay clipped below the title even in a short panel");
                assertEquals(0, clip.getX());
                assertEquals(0, clip.getY());
                assertEquals(width, progress.getWidth(), 0.5);
                assertEquals(contentHeight, progress.getHeight(), 0.5,
                        "progress fills the content below the title, not the toolbar or a default 8px strip");
                assertEquals(width, track.getWidth(), 1);
                assertEquals(contentHeight, track.getHeight(), 1);
                assertEquals(contentHeight, bar.getHeight(), 1,
                        "the skin's painted bar must fill the height too, not just the outer control");
                assertEquals(width * 0.5, bar.getWidth(), 1);
                double headerBottom = toolbar.localToScene(toolbar.getBoundsInLocal()).getMaxY();
                assertTrue(view.localToScene(view.getBoundsInLocal()).getMinY() >= headerBottom - 0.5);
                assertTrue(progress.localToScene(progress.getBoundsInLocal()).getMinY() >= headerBottom - 0.5);
                assertTrue(track.localToScene(track.getBoundsInLocal()).getMinY() >= headerBottom - 0.5);
                assertTrue(bar.localToScene(bar.getBoundsInLocal()).getMinY() >= headerBottom - 0.5,
                        "the painted progress must never extend into the title toolbar, even in a short panel");
                assertTrue(title.localToScene(title.getBoundsInLocal()).getMaxY() <= headerBottom + 0.5);
                assertEquals("50%", percentage(panel));
                assertNull(panel.lookup("#run-diagnostics"));
                if (width == 800) {
                    Path screenshot = Path.of("build", "run-progress-check", "progress-half.png").toAbsolutePath();
                    Files.createDirectories(screenshot.getParent());
                    assertTrue(ImageIO.write(SwingFXUtils.fromFXImage(scene.snapshot(null), null),
                            "png", screenshot.toFile()));
                }
            }
            return null;
        });
    }

    @ParameterizedTest
    @EnumSource(Finish.class)
    void failureAndCancellationRestoreDiagnosticsAndIgnoreLateProgress(Finish finish) throws Exception {
        onFx(() -> {
            try (RunPanel panel = panel(() -> { })) {
                UiProgressBar previousProgress = progress(panel);
                panel.progress(new RunCompilation.Progress(4, 8));
                if (finish == Finish.FAILED) {
                    panel.failed("SemanticAnalyzer", List.of(new Diagnostic("PANEL001", Diagnostic.Severity.ERROR,
                            "unknown name", "declare it first", new SourceRange(2, 3, 2, 4))));
                } else panel.cancelled();
                UiTextArea diagnostics = assertInstanceOf(UiTextArea.class, panel.getCenter());
                assertEquals("run-diagnostics", diagnostics.getId());
                assertFalse(diagnostics.isEditable());
                assertTrue(diagnostics.getText().contains(temporary.resolve("current.mc").toString()));
                assertInstanceOf(HBox.class, panel.getTop());
                Label status = assertInstanceOf(Label.class, panel.lookup("#run-status"));
                assertTrue(status.isVisible());
                assertTrue(status.isManaged());
                if (finish == Finish.FAILED) {
                    assertEquals("编译失败（SemanticAnalyzer）", status.getText());
                    assertTrue(diagnostics.getText().contains("PANEL001"));
                    assertTrue(diagnostics.getText().contains("第 2 行，UTF-8 字节位置 3"));
                } else {
                    assertEquals("已取消", status.getText());
                    assertTrue(diagnostics.getText().contains("未启动程序"));
                }
                assertTrue(((Button) panel.lookup("#run-cancel")).isDisabled());
                assertNull(panel.lookup("#run-progress-view"));
                assertNull(panel.lookup("#run-progress"));
                assertNull(panel.artifact());
                panel.progress(new RunCompilation.Progress(8, 8));
                assertSame(diagnostics, panel.getCenter());
                assertEquals(0.5, previousProgress.getProgress(), "late events must not change completed feedback");
                assertNull(panel.lookup("#run-progress-view"));
            }
            return null;
        });
    }

    @Test
    void detachedSuccessfulCompilationSwitchesToInputOutputWithoutLaunchingTheProgram() throws Exception {
        onFx(() -> {
            try (RunPanel panel = panel(() -> { })) {
                UiProgressBar previousProgress = progress(panel);
                ExecutableArtifact executable = new ExecutableArtifact(temporary.resolve("not-launched.exe"));
                panel.progress(new RunCompilation.Progress(6, 8));
                panel.compiled(executable);
                InputOutputPanel inputOutput = assertInstanceOf(InputOutputPanel.class, panel.getCenter());
                assertEquals(temporary, inputOutput.workingDirectory());
                assertEquals(executable.path(), inputOutput.executable());
                assertSame(executable, panel.artifact());
                assertEquals(1, previousProgress.getProgress());
                assertNull(panel.getTop());
                assertNull(panel.lookup("#run-progress-view"));
                assertNull(panel.getScene());
                assertNull(inputOutput.getScene());
                SwingNode surface = assertInstanceOf(SwingNode.class, inputOutput.lookup("#input-output-surface"));
                assertNull(surface.getContent());
                assertFalse(inputOutput.isFinished());
                assertNull(inputOutput.exitCode());
                panel.start();
                panel.activate();
                panel.progress(new RunCompilation.Progress(2, 8));
                assertSame(inputOutput, panel.getCenter());
                assertNull(surface.getContent(), "detached activation must not launch a process");
                assertEquals(1, previousProgress.getProgress());
            }
            return null;
        });
    }

    @Test
    void closingRejectsLateProgressAndSuccessWithoutStartingInputOutput() throws Exception {
        onFx(() -> {
            var cancellations = new AtomicInteger();
            RunPanel panel = panel(cancellations::incrementAndGet);
            try {
                panel.progress(new RunCompilation.Progress(2, 8));
                UiProgressBar progress = progress(panel);
                var originalView = panel.getCenter();
                panel.close();
                panel.close();
                assertTrue(panel.isClosed());
                assertEquals(1, cancellations.get());
                panel.progress(new RunCompilation.Progress(8, 8));
                panel.compiled(new ExecutableArtifact(temporary.resolve("late.exe")));
                panel.failed("late failure", "must be ignored");
                panel.cancelled();
                panel.start();
                panel.activate();
                assertSame(originalView, panel.getCenter());
                assertEquals(0.25, progress.getProgress());
                assertEquals("25%", percentage(panel));
                assertNull(panel.artifact());
                assertNull(panel.lookup("#input-output-surface"));
                assertNull(panel.lookup("#run-diagnostics"));
            } finally { panel.close(); }
            return null;
        });
    }

    private RunPanel panel(Runnable cancel) {
        return new RunPanel(temporary.resolve("current.mc"), cancel, () -> { });
    }

    private static UiProgressBar progress(RunPanel panel) {
        return assertInstanceOf(UiProgressBar.class, panel.lookup("#run-progress"));
    }

    private static String percentage(RunPanel panel) {
        return ((Label) panel.lookup("#run-progress-percentage")).getText();
    }

    private enum Finish { FAILED, CANCELLED }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(20, TimeUnit.SECONDS);
    }
}
