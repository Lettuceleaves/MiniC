package craken.ui.interaction.diagnostics;

import craken.SourceRange;
import craken.compiler.Diagnostic;
import craken.ui.component.UiStyles;
import craken.ui.editor.realtime.RealtimeDiagnostic;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-ui")
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
final class RealtimeDiagnosticsPanelTest {
    @BeforeAll
    static void toolkit() throws Exception {
        var started = new CompletableFuture<Void>();
        Runnable ready = () -> { Platform.setImplicitExit(false); started.complete(null); };
        try { Platform.startup(ready); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(ready); }
        started.get(10, TimeUnit.SECONDS);
    }

    @Test
    void rendersSortedDiagnosticsWithLineReasonAdviceAndCounts() throws Exception {
        onFx(() -> {
            var panel = new RealtimeDiagnosticsPanel("ERR 1");
            var scene = new Scene(panel, 900, 260);
            UiStyles.install(scene);
            var error = diagnostic("PAR001", Diagnostic.Severity.ERROR, "期望 ';'", "在语句结尾补充分号",
                    new SourceRange(3, 12, 3, 12));
            var warning = diagnostic("SEMW01", Diagnostic.Severity.WARNING, "未使用的变量", "删除或使用该变量",
                    new SourceRange(1, 5, 1, 6));

            panel.update(List.of(error, warning));
            panel.applyCss();
            panel.layout();

            assertEquals("ERR 1", panel.titleText());
            assertEquals("1 个错误 · 1 个警告", panel.statusText());
            assertEquals(2, panel.rowCount());
            VBox rows = rows(panel);
            List<String> text = rows.getChildren().stream().filter(Label.class::isInstance)
                    .map(node -> ((Label) node).getText()).toList();
            assertTrue(text.get(0).startsWith("第 1 行"), text.get(0));
            assertTrue(text.get(0).contains("警告"), text.get(0));
            assertTrue(text.get(1).startsWith("第 3 行"), text.get(1));
            assertTrue(text.get(1).contains("期望 ';'"), text.get(1));
            assertTrue(text.get(1).contains("建议"), text.get(1));
            assertFalse(panel.lookup("#realtime-diagnostics-empty").isVisible());

            panel.update(List.of());
            assertEquals(0, panel.rowCount());
            assertEquals("无问题", panel.statusText());
            assertTrue(panel.lookup("#realtime-diagnostics-empty").isVisible());

            panel.close();
            panel.update(List.of(error));
            assertEquals(0, panel.rowCount(), "a closed panel must ignore later updates");
            assertEquals("已关闭", panel.statusText());
        });
    }

    private static RealtimeDiagnostic diagnostic(String code, Diagnostic.Severity severity, String message,
                                                 String solution, SourceRange range) {
        return new RealtimeDiagnostic(code, severity, message, solution, range);
    }

    private static VBox rows(RealtimeDiagnosticsPanel panel) {
        Node viewport = panel.lookup(".realtime-diagnostics-viewport");
        return assertInstanceOf(VBox.class, viewport instanceof javafx.scene.control.ScrollPane scroll
                ? scroll.getContent() : viewport);
    }

    private static void onFx(Runnable action) throws Exception {
        var result = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try { action.run(); result.complete(null); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        result.get(20, TimeUnit.SECONDS);
    }
}
