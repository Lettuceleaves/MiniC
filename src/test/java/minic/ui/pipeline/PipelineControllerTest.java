package minic.ui.pipeline;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import minic.ui.component.UiStyles;
import minic.ui.display.DisplayArea;
import minic.ui.editor.EditorArea;
import minic.ui.editor.EditorFile;
import minic.ui.frame.AppFrame;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** 真实编译只推进到语义分析；不会生成 native 产物或运行程序。 */
@EnabledIfSystemProperty(named = "minic.ui.test", matches = "true")
final class PipelineControllerTest {
    @TempDir Path directory;

    @BeforeAll
    static void toolkit() throws Exception {
        var started = new CompletableFuture<Void>();
        Runnable ready = () -> {
            Platform.setImplicitExit(false);
            started.complete(null);
        };
        try { Platform.startup(ready); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(ready); }
        started.get(10, TimeUnit.SECONDS);
    }

    @Test
    void noFileDisablesTheIconAndOpeningDoesNotChangeTheWorkspaceOrCreateASession() throws Exception {
        try (Fixture ui = fixture()) {
            var originalContent = onFx(() -> {
                Scene scene = new Scene(ui.frame, 1280, 800);
                UiStyles.install(scene);
                ui.frame.resize(1280, 800);
                ui.frame.applyCss();
                ui.frame.layout();
                assertEquals(AppFrame.DEFAULT_DISPLAY_WIDTH, ui.display.getWidth(), 2);
                assertTrue(ui.icon().isDisabled());
                assertFalse(ui.icon().isSelected());
                Button run = assertInstanceOf(Button.class, ui.frame.lookup("#app-run-button"));
                assertTrue(run.isDisabled());
                assertEquals(run.getOpacity(), ui.icon().getOpacity(), 0.001,
                        "unavailable pipeline and run commands must share the same disabled appearance");
                assertTrue(ui.icon().getOpacity() < 1);
                return ui.display.getChildren().getFirst();
            });
            animate(ui, () -> {
                ui.icon().fire();
                ui.controller.open();
            });
            onFx(() -> {
                assertEquals(AppFrame.DEFAULT_DISPLAY_WIDTH, ui.display.getWidth(), 2,
                        "a command without a source must not start a display-width animation");
                assertSame(originalContent, ui.display.getChildren().getFirst());
                assertNull(ui.display.lookup("#pipeline-panel"));
                assertFalse(ui.icon().isSelected());
                ui.frame.selectWorkspaceTab(AppFrame.WorkspaceTab.SETTINGS);
                ui.icon().fire();
                ui.controller.open();
                assertEquals(AppFrame.WorkspaceTab.SETTINGS, ui.frame.selectedWorkspaceTab(),
                        "opening without a file must not switch away from the current workspace");
                assertSame(originalContent, ui.display.getChildren().getFirst());
                ui.controller.close();
                assertTrue(ui.icon().isDisabled());
                assertFalse(ui.icon().isSelected());
                return null;
            });
            assertFalse(Files.exists(directory.resolve("build/minic-pipelines")));
        }
    }

    @Test
    void repeatedIconClicksCollapseAndRestoreTheWorkbenchAndRapidClicksHonorTheFinalState() throws Exception {
        Path source = Files.writeString(directory.resolve("toggle.mc"), "int main() { return 0; }\n");
        try (Fixture ui = fixture()) {
            onFx(() -> {
                ui.editors.openFile(source);
                Scene scene = new Scene(ui.frame, 1280, 800);
                UiStyles.install(scene);
                ui.frame.resize(1280, 800);
                ui.frame.applyCss();
                ui.frame.layout();
                return null;
            });
            animate(ui, () -> {
                ui.icon().fire();
                assertTrue(ui.icon().isSelected());
            });
            PipelinePanel original = onFx(() -> {
                assertExpanded(ui);
                ui.split().setDividerPositions(0.35);
                ui.frame.layout();
                return ui.panel();
            });
            double divider = onFx(() -> ui.split().getDividerPositions()[0]);

            animate(ui, () -> {
                ui.icon().fire();
                assertFalse(ui.icon().isSelected());
            });
            onFx(() -> {
                assertFalse(ui.icon().isSelected());
                assertTrue(ui.display.getWidth() <= 2,
                        "the second click must fully collapse the display, not restore the default width");
                assertSame(original, ui.panel());
                return null;
            });

            animate(ui, () -> {
                ui.icon().fire();
                assertTrue(ui.icon().isSelected());
            });
            onFx(() -> {
                assertExpanded(ui);
                assertSame(original, ui.panel());
                assertEquals(divider, ui.split().getDividerPositions()[0], 0.005,
                        "closing and reopening the workbench must preserve the user's input/output split");
                return null;
            });

            animate(ui, () -> {
                ui.icon().fire();
                ui.icon().fire();
                ui.icon().fire();
                assertFalse(ui.icon().isSelected());
            });
            onFx(() -> {
                assertFalse(ui.icon().isSelected());
                assertTrue(ui.display.getWidth() <= 2, "the last rapid click requested a collapsed display");
                return null;
            });
            animate(ui, () -> {
                ui.icon().fire();
                ui.icon().fire();
                ui.icon().fire();
                assertTrue(ui.icon().isSelected());
            });
            onFx(() -> {
                assertExpanded(ui);
                assertSame(original, ui.panel());
                assertEquals(divider, ui.split().getDividerPositions()[0], 0.005);
                return null;
            });
        }
    }

    @Test
    void fileAvailabilityTracksSelectionAndStaysDisabledAfterTheControllerCloses() throws Exception {
        Path firstPath = Files.writeString(directory.resolve("first.mc"), "int main() { return 1; }\n");
        Path secondPath = Files.writeString(directory.resolve("second.mc"), "int main() { return 2; }\n");
        try (Fixture ui = fixture()) {
            onFx(() -> {
                assertNull(ui.editors.activeFile());
                assertTrue(ui.icon().isDisabled());
                EditorFile first = ui.editors.openFile(firstPath);
                assertSame(first, ui.editors.activeFile());
                assertFalse(ui.icon().isDisabled());
                EditorFile second = ui.editors.openFile(secondPath);
                assertSame(second, ui.editors.activeFile());
                assertFalse(ui.icon().isDisabled());
                ui.editors.select(first);
                assertSame(first, ui.editors.activeFile());
                assertFalse(ui.icon().isDisabled());
                assertTrue(ui.editors.closeFile(first));
                assertSame(second, ui.editors.activeFile());
                assertFalse(ui.icon().isDisabled(), "closing one file must retain availability while another is active");
                assertTrue(ui.editors.closeFile(second));
                assertNull(ui.editors.activeFile());
                assertTrue(ui.icon().isDisabled(), "closing the final source file must disable the command immediately");

                ui.editors.openFile(firstPath);
                assertFalse(ui.icon().isDisabled());
                var originalContent = ui.display.getChildren().getFirst();
                ui.controller.close();
                assertTrue(ui.icon().isDisabled());
                ui.editors.openFile(secondPath);
                assertTrue(ui.icon().isDisabled(), "a detached availability listener must not reactivate a closed controller");
                assertTrue(ui.editors.closeAll());
                ui.editors.openFile(firstPath);
                assertTrue(ui.icon().isDisabled());
                ui.icon().fire();
                ui.controller.open();
                assertFalse(ui.icon().isSelected());
                assertSame(originalContent, ui.display.getChildren().getFirst());
                return null;
            });
            assertFalse(Files.exists(directory.resolve("build/minic-pipelines")),
                    "availability changes alone must not prepare a compilation session");
        }
    }

    @Test
    void stepsAdvanceRealCompilationAndReopeningPreservesProgressAndPaneSizes() throws Exception {
        Path source = Files.writeString(directory.resolve("steps.mc"),
                "int main() {\n    return 0;\n}\n");
        try (Fixture ui = fixture()) {
            perform(ui, () -> {
                ui.editors.openFile(source);
                ui.icon().fire();
                assertTrue(ui.step().isDisabled(), "preparation must not publish inline on the FX thread");
                return null;
            });
            onFx(() -> {
                assertEquals("已执行 0 步", ui.status().getText());
                assertEquals(0, ui.stages().getSelectionModel().getSelectedIndex());
                ui.split().setDividerPositions(0.35);
                return null;
            });
            perform(ui, () -> { ui.step().fire(); return null; });
            onFx(() -> {
                assertEquals("已执行 1 步", ui.status().getText());
                assertEquals(0.35, ui.split().getDividerPositions()[0], 0.001);
                return null;
            });
            perform(ui, () -> { ui.stage().fire(); return null; });
            onFx(() -> {
                assertEquals(PipelineSession.Status.COMPLETED, ui.stages().getItems().get(0).status());
                assertEquals(PipelineSession.Status.CURRENT, ui.stages().getItems().get(1).status());
                ui.stages().getSelectionModel().select(0);
                assertEquals("查看已完成阶段 · 预处理", ui.status().getText());
                ui.stages().getSelectionModel().select(7);
                assertEquals(0, ui.stages().getSelectionModel().getSelectedIndex());
                PipelinePanel panel = ui.panel();
                String status = ui.status().getText();
                assertTrue(ui.icon().isSelected());
                ui.icon().fire();
                assertFalse(ui.icon().isSelected());
                assertSame(panel, ui.panel());
                assertEquals(status, ui.status().getText());
                assertEquals(0, ui.stages().getSelectionModel().getSelectedIndex());
                assertEquals(0.35, ui.split().getDividerPositions()[0], 0.001);
                ui.icon().fire();
                assertTrue(ui.icon().isSelected());
                assertSame(panel, ui.panel());
                assertEquals(status, ui.status().getText());
                assertEquals(0, ui.stages().getSelectionModel().getSelectedIndex());
                assertEquals(0.35, ui.split().getDividerPositions()[0], 0.001);
                return null;
            });
            assertEquals(1, sessionCount());
            perform(ui, () -> { ui.step().fire(); return null; });
            onFx(() -> {
                assertEquals(1, ui.stages().getSelectionModel().getSelectedIndex(),
                        "advancing while reviewing history must resume the actual current stage");
                assertEquals(PipelineSession.Status.COMPLETED, ui.stages().getItems().get(0).status());
                return null;
            });
        }
    }

    @Test
    void editingUnsavedContentStartsANewSessionAndUsesTheCapturedBuffer() throws Exception {
        String saved = "int main() { return 0; }\n";
        Path source = Files.writeString(directory.resolve("unsaved.mc"), saved);
        try (Fixture ui = fixture()) {
            EditorFile file = onFx(() -> ui.editors.openFile(source));
            perform(ui, () -> { ui.icon().fire(); return null; });
            perform(ui, () -> { ui.stage().fire(); return null; });
            perform(ui, () -> {
                ui.icon().fire();
                assertFalse(ui.icon().isSelected());
                file.editor().setSource("int main() { return missing_pipeline_name; }\n");
                assertTrue(file.isDirty());
                ui.icon().fire();
                assertTrue(ui.icon().isSelected());
                assertTrue(ui.step().isDisabled());
                return null;
            });
            onFx(() -> {
                assertEquals("已执行 0 步", ui.status().getText());
                assertEquals(PipelineSession.Status.CURRENT, ui.stages().getItems().get(0).status());
                assertEquals(PipelineSession.Status.PENDING, ui.stages().getItems().get(1).status());
                return null;
            });
            assertEquals(2, sessionCount());
            for (int index = 0; index < 4; index++) {
                perform(ui, () -> { ui.stage().fire(); return null; });
            }
            onFx(() -> {
                assertEquals(PipelineSession.Status.FAILED, ui.stages().getItems().get(3).status(),
                        "semantic analysis must read the invalid unsaved buffer, not the valid disk file");
                assertTrue(ui.status().getText().startsWith("编译失败："));
                assertTrue(ui.step().isDisabled());
                assertTrue(ui.stage().isDisabled());
                ui.stages().getSelectionModel().select(0);
                assertEquals(0, ui.stages().getSelectionModel().getSelectedIndex());
                return null;
            });
            assertEquals(saved, Files.readString(source));
        }
    }

    private Fixture fixture() throws Exception {
        return onFx(() -> new Fixture(directory));
    }

    private long sessionCount() throws Exception {
        try (var sessions = Files.list(directory.resolve("build/minic-pipelines"))) {
            return sessions.count();
        }
    }

    private static void animate(Fixture ui, Runnable action) throws Exception {
        CompletableFuture<Void> finished = onFx(() -> {
            action.run();
            var settled = new CompletableFuture<Void>();
            PauseTransition wait = new PauseTransition(Duration.millis(400));
            wait.setOnFinished(event -> settled.complete(null));
            wait.play();
            return settled;
        });
        finished.get(10, TimeUnit.SECONDS);
        onFx(() -> {
            ui.frame.applyCss();
            ui.frame.layout();
            return null;
        });
    }

    private static void assertExpanded(Fixture ui) {
        assertTrue(ui.icon().isSelected());
        SplitPane content = assertInstanceOf(SplitPane.class, ui.frame.lookup(".app-content"));
        assertTrue(ui.display.getWidth() >= content.getWidth() - 5,
                "the selected pipeline icon must expand the display to the left edge");
    }

    private static void perform(Fixture ui, Callable<Void> action) throws Exception {
        CompletableFuture<Void> available = onFx(() -> {
            action.call();
            var ready = new CompletableFuture<Void>();
            Runnable check = () -> {
                if (!ui.step().isDisabled() || ui.status().getText().startsWith("编译失败：")) ready.complete(null);
            };
            ChangeListener<Object> changed = (observable, previous, current) -> check.run();
            ui.step().disabledProperty().addListener(changed);
            ui.status().textProperty().addListener(changed);
            ready.whenComplete((ignored, failure) -> {
                ui.step().disabledProperty().removeListener(changed);
                ui.status().textProperty().removeListener(changed);
            });
            check.run();
            return ready;
        });
        available.get(20, TimeUnit.SECONDS);
        onFx(() -> null); // let the publishing callback finish updating status after enabling the controls.
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(20, TimeUnit.SECONDS);
    }

    private static final class Fixture implements AutoCloseable {
        final EditorArea editors = new EditorArea();
        final DisplayArea display = new DisplayArea();
        final AppFrame frame = new AppFrame(editors, display, new Region(), editors.tabBar());
        final PipelineController controller;

        Fixture(Path directory) { controller = new PipelineController(editors, frame, display, directory); }

        ToggleButton icon() { return assertInstanceOf(ToggleButton.class, frame.lookup("#app-pipeline-button")); }
        PipelinePanel panel() { return assertInstanceOf(PipelinePanel.class, display.getChildren().getFirst()); }
        Button step() { return assertInstanceOf(Button.class, panel().lookup("#pipeline-next-step")); }
        Button stage() { return assertInstanceOf(Button.class, panel().lookup("#pipeline-next-stage")); }
        Label status() { return assertInstanceOf(Label.class, panel().lookup("#pipeline-status")); }
        SplitPane split() { return assertInstanceOf(SplitPane.class, panel().lookup("#pipeline-io-split")); }

        @SuppressWarnings("unchecked")
        ListView<PipelineSession.StageView> stages() {
            return (ListView<PipelineSession.StageView>) assertInstanceOf(ListView.class,
                    panel().lookup("#pipeline-stage-list"));
        }

        @Override
        public void close() throws Exception {
            onFx(() -> {
                controller.close();
                editors.setCloseDecisionHandler(file -> EditorArea.CloseChoice.DISCARD);
                assertTrue(editors.closeAll());
                return null;
            });
        }
    }
}
