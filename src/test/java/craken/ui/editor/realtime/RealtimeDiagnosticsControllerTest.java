package craken.ui.editor.realtime;

import craken.ui.editor.EditorArea;
import craken.ui.editor.EditorFile;
import craken.ui.interaction.InteractionArea;
import craken.ui.interaction.InteractionItem;
import craken.ui.interaction.diagnostics.RealtimeDiagnosticsPanel;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-ui")
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
final class RealtimeDiagnosticsControllerTest {
    @TempDir Path directory;

    @BeforeAll
    static void toolkit() throws Exception {
        var started = new CompletableFuture<Void>();
        Runnable ready = () -> { Platform.setImplicitExit(false); started.complete(null); };
        try { Platform.startup(ready); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(ready); }
        started.get(10, TimeUnit.SECONDS);
    }

    @Test
    void openingAFileBindsAnErrorTabAndClosingItsLastViewUnbindsIt() throws Exception {
        Path path = Files.writeString(directory.resolve("broken.c"), "int main() {\n    return 1\n}");
        var editorsRef = new AtomicReference<EditorArea>();
        var interactionsRef = new AtomicReference<InteractionArea>();
        var bindingRef = new AtomicReference<RealtimeDiagnosticsController>();
        var fileRef = new AtomicReference<EditorFile>();
        onFx(() -> {
            editorsRef.set(new EditorArea());
            interactionsRef.set(new InteractionArea(directory));
            bindingRef.set(new RealtimeDiagnosticsController(editorsRef.get(), interactionsRef.get()));
        });
        try {
            onFx(() -> assertEquals(1, interactionsRef.get().items().size(),
                    "the interaction bar starts with one terminal"));
            onFx(() -> {
                try { fileRef.set(editorsRef.get().openFile(path)); }
                catch (java.io.IOException failure) { throw new RuntimeException(failure); }
            });
            assertEquals(2, onFx(() -> interactionsRef.get().items().size()));
            InteractionItem<RealtimeDiagnosticsPanel> tab = onFx(() -> findErrTab(interactionsRef.get()));
            assertNotNull(tab);
            assertEquals("ERR 1", tab.title());
            RealtimeDiagnosticsPanel panel = tab.content();

            awaitFx(() -> !panel.diagnostics().isEmpty());
            onFx(() -> {
                assertTrue(panel.diagnostics().stream().anyMatch(item -> item.message().contains("期望 ';'")));
                assertTrue(panel.statusText().contains("错误"), panel.statusText());
                assertTrue(panel.rowCount() > 0);
            });

            onFx(() -> fileRef.get().editor().setSource("int main() {\n    return 1;\n}"));
            awaitFx(() -> panel.diagnostics().isEmpty());
            onFx(() -> assertEquals("无问题", panel.statusText()));

            onFx(() -> editorsRef.get().setCloseDecisionHandler(ignored -> EditorArea.CloseChoice.DISCARD));
            assertTrue(onFx(() -> editorsRef.get().closeFile(fileRef.get())));
            onFx(() -> {
                assertEquals(1, interactionsRef.get().items().size(),
                        "closing the last view removes its error tab");
                assertTrue(tab.isClosed());
            });
        } finally {
            onFx(() -> {
                bindingRef.get().close();
                interactionsRef.get().close();
            });
        }
    }

    private static InteractionItem<RealtimeDiagnosticsPanel> findErrTab(InteractionArea interactions) {
        for (InteractionItem<?> item : interactions.items()) {
            if (item.content() instanceof RealtimeDiagnosticsPanel) {
                @SuppressWarnings("unchecked")
                InteractionItem<RealtimeDiagnosticsPanel> tab = (InteractionItem<RealtimeDiagnosticsPanel>) item;
                return tab;
            }
        }
        return null;
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(30, TimeUnit.SECONDS);
    }

    private static void onFx(Runnable action) throws Exception {
        onFx(() -> { action.run(); return null; });
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
