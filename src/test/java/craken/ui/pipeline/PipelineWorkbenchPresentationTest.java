package craken.ui.pipeline;

import craken.compiler.CompilerApi;
import craken.compiler.SourceFile;
import craken.ui.component.UiStyles;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class PipelineWorkbenchPresentationTest {
    @TempDir Path directory;

    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        Runnable start = () -> { Platform.setImplicitExit(false); ready.complete(null); };
        try { Platform.startup(start); } catch (IllegalStateException running) { Platform.runLater(start); }
        ready.get(10, TimeUnit.SECONDS);
    }

    @Test void stageBoundaryNamesTheActualDisplayedFrameAndTheNextStageSeparately() throws Exception {
        try (var session = session("int main(){return 7;}")) {
            session.nextStage(() -> false);
            var snapshot = session.snapshot();
            assertEquals(1, snapshot.currentStageIndex());
            assertEquals(0, snapshot.visualization().stageIndex());
            onFx(() -> {
                try (var panel = panel()) {
                    panel.show(snapshot);
                    assertTrue(label(panel, "pipeline-frame-title").getText().contains("预处理"));
                    assertTrue(label(panel, "pipeline-frame-detail").getText().contains("词法分析尚未开始"));
                    assertEquals("输入 · 源码", label(panel, "pipeline-input-title").getText());
                    assertEquals("输出 · 预处理结果", label(panel, "pipeline-output-title").getText());
                    assertFalse(button(panel, "pipeline-next-step").isDisabled());
                }
                return null;
            });
        }
    }

    @Test void actualStepMetadataAndReturnFromHistoryDoNotExecuteACompilerStep() throws Exception {
        try (var session = session("int main(){return 7;}")) {
            session.nextStage(() -> false);
            session.nextStep(() -> false);
            var live = session.snapshot();
            onFx(() -> {
                try (var panel = panel()) {
                    panel.setOnSelectStage(index -> { assertTrue(session.selectStage(index)); panel.show(session.snapshot()); });
                    panel.show(live);
                    assertTrue(label(panel, "pipeline-frame-title").getText().contains("词法分析"));
                    assertTrue(label(panel, "pipeline-frame-title").getText().contains("第 1 步"));
                    assertTrue(label(panel, "pipeline-frame-detail").getText().contains(live.visualization().operation()));
                    assertFalse(button(panel, "pipeline-return-current").isVisible());
                    session.selectStage(0); panel.show(session.snapshot());
                    assertTrue(button(panel, "pipeline-return-current").isVisible());
                    assertTrue(label(panel, "pipeline-frame-title").getText().contains("回看"));
                    button(panel, "pipeline-return-current").fire();
                    assertEquals(1, session.snapshot().selectedStageIndex());
                    assertEquals(live.stepCount(), session.snapshot().stepCount());
                    assertSame(live.visualization(), panel.visualization());
                }
                return null;
            });
        }
    }

    @Test void fullDiagnosticIsSelectableAndScrollableAndNewSourceClearsIt() throws Exception {
        try (var session = session("int main(){return missing;}")) {
            for (int i = 0; i < 4; i++) session.nextStage(() -> false);
            assertTrue(session.snapshot().failed());
            var error = session.snapshot().stages().get(3).error();
            onFx(() -> {
                try (var panel = panel()) {
                    panel.show(session.snapshot());
                    var diagnostics = assertInstanceOf(TextArea.class, panel.lookup("#pipeline-diagnostics-text"));
                    var pane = assertInstanceOf(TitledPane.class, panel.lookup("#pipeline-diagnostics"));
                    assertTrue(pane.isVisible()); assertTrue(pane.isExpanded());
                    assertEquals(error, diagnostics.getText());
                    assertFalse(diagnostics.isEditable()); assertTrue(diagnostics.isWrapText());
                    diagnostics.selectAll(); assertEquals(error, diagnostics.getSelectedText());
                    assertTrue(button(panel, "pipeline-next-step").isDisabled());
                    session.selectStage(0); panel.show(session.snapshot());
                    assertEquals(error, diagnostics.getText());
                    assertTrue(pane.getText().contains("语义分析"), "history must identify the stage that actually failed");
                    panel.preparing("replacement.mc");
                    assertFalse(pane.isVisible()); assertFalse(pane.isManaged());
                    assertEquals("", diagnostics.getText());
                    assertTrue(label(panel, "pipeline-frame-title").getText().contains("等待"));
                    assertNull(label(panel, "pipeline-frame-title").getTooltip());
                    assertNull(label(panel, "pipeline-frame-detail").getTooltip());
                }
                return null;
            });
        }
    }

    @Test void pendingProjectionShowsBothFailureAndRetainedFrameWithoutDisablingRetry() throws Exception {
        try (var session = session("int main(){return 7;}")) {
            session.nextStep(() -> false);
            var saved = session.snapshot();
            var pending = new PipelineSession.Snapshot(saved.stages(), saved.selectedStageIndex(), saved.currentStageIndex(),
                    saved.stepCount() + 1, true, false, false, saved.visualization(), true, "projection failed\nretry available");
            onFx(() -> {
                try (var panel = panel()) {
                    panel.show(pending);
                    assertTrue(label(panel, "pipeline-frame-detail").getText().contains("保留"));
                    assertTrue(((TextArea) panel.lookup("#pipeline-diagnostics-text")).getText().contains("projection failed\nretry available"));
                    assertFalse(button(panel, "pipeline-next-step").isDisabled());
                    panel.show(saved);
                    assertFalse(panel.lookup("#pipeline-diagnostics").isVisible());
                }
                return null;
            });
        }
    }

    private PipelineSession session(String source) {
        return new PipelineSession(new CompilerApi(new SourceFile("presentation.mc", source), directory));
    }
    private static PipelinePanel panel() {
        var panel = new PipelinePanel(); var scene = new Scene(panel, 1180, 720); UiStyles.install(scene);
        panel.resize(1180, 720); panel.applyCss(); panel.layout(); return panel;
    }
    private static Label label(PipelinePanel panel, String id) { return assertInstanceOf(Label.class, panel.lookup("#" + id), id); }
    private static Button button(PipelinePanel panel, String id) { return assertInstanceOf(Button.class, panel.lookup("#" + id), id); }
    private static <T> T onFx(Callable<T> task) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> { try { result.complete(task.call()); } catch (Throwable failure) { result.completeExceptionally(failure); } });
        return result.get(20, TimeUnit.SECONDS);
    }
}
