package craken.ui.editor.realtime;

import craken.compiler.Diagnostic;
import craken.ui.component.editor.UiCodeEditor;
import javafx.application.Platform;
import javafx.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-ui")
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
final class RealtimeSyntaxControllerTest {
    @BeforeAll static void toolkit() throws Exception {
        var ready = new CountDownLatch(1);
        Runnable start = () -> { Platform.setImplicitExit(false); ready.countDown(); };
        try { Platform.startup(start); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(start); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @Test void brokenSourcePublishesDiagnosticsAndFixingClearsThem() throws Exception {
        var editor = onFx(UiCodeEditor::new);
        onFx(() -> new Scene(editor, 900, 500));
        var controller = onFx(() -> new RealtimeSyntaxController(editor, "live.mc"));
        try {
            var published = new CopyOnWriteArrayList<List<RealtimeDiagnostic>>();
            onFx(() -> {
                controller.setOnDiagnosticsChanged(published::add);
                return null;
            });
            onFx(() -> { editor.setSource("int main() {\n    return 1\n}"); return null; });
            awaitFx(() -> !controller.diagnostics().isEmpty());
            onFx(() -> {
                assertFalse(published.isEmpty());
                RealtimeDiagnostic first = published.getLast().getFirst();
                assertEquals(3, first.range().startLine());
                assertTrue(first.message().contains("期望 ';'"), first.message());
                assertFalse(first.solution().isBlank(), first.solution());
                assertEquals(Diagnostic.Severity.ERROR, first.severity());
                assertFalse(controller.diagnostics().isEmpty());
                return null;
            });

            onFx(() -> { editor.setSource("int main() {\n    return 1;\n}"); return null; });
            awaitFx(() -> controller.diagnostics().isEmpty());
            onFx(() -> {
                assertFalse(published.isEmpty());
                assertTrue(published.getLast().isEmpty(), "fixing the source must publish an empty list");
                return null;
            });
        } finally {
            onFx(() -> { controller.close(); editor.dispose(); return null; });
        }
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(20, TimeUnit.SECONDS);
    }

    private static void awaitFx(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (true) {
            boolean reached = onFx(condition::getAsBoolean);
            if (reached) return;
            if (System.nanoTime() > deadline) fail("condition not reached in time");
            Thread.sleep(20);
        }
    }
}
