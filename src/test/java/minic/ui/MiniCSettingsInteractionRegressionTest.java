package minic.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import minic.settings.MiniCSettings;
import minic.settings.MiniCSettingsPane;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCSettingsInteractionRegressionTest {
    private static final Path SETTINGS_FILE = Path.of("config", "settings.json");
    private static final Path KEY_BINDINGS_FILE = Path.of("config", "keybindings.json");

    @Test
    void persistsGraphZoomSensitivityAndKeyBindingsImmediately() throws Exception {
        String originalSettings = backup(SETTINGS_FILE);
        String originalKeyBindings = backup(KEY_BINDINGS_FILE);
        try {
            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "light"
                    }
                    """, StandardCharsets.UTF_8);
            MiniCSettings.load();
            assertThat(MiniCSettings.graphZoomStep()).isEqualTo(0.025);
            assertThat(MiniCSettings.graphZoomAnchor()).isEqualTo("mouse");
            assertThat(Files.readString(SETTINGS_FILE, StandardCharsets.UTF_8))
                    .contains("\"graphZoomStep\": 0.025")
                    .contains("\"graphZoomAnchor\": \"mouse\"");

            MiniCSettings.setGraphZoomStep(MiniCSettings.maxGraphZoomStep() * 4);
            assertThat(MiniCSettings.graphZoomStep()).isEqualTo(MiniCSettings.maxGraphZoomStep());
            assertThat(Files.readString(SETTINGS_FILE, StandardCharsets.UTF_8))
                    .contains("\"graphZoomStep\": " + MiniCSettings.maxGraphZoomStep());

            Files.deleteIfExists(KEY_BINDINGS_FILE);
            MiniCKeyBindingConfig config = MiniCKeyBindingConfig.loadDefault();
            assertThat(MiniCKeyBindingConfig.conflictingAction("ast.zoom.out", "Ctrl+=")).contains("ast.zoom.in");
            assertThat(MiniCKeyBindingConfig.isReserved("Enter")).isTrue();
            assertThat(MiniCKeyBindingConfig.normalizeCombo("Ctrl+Alt+MouseLeft")).isEqualTo("Ctrl+Alt+MouseLeft");

            MiniCKeyBindingConfig.setKeys("ast.zoom.out", List.of("Ctrl+Alt+M"));
            assertThat(config.matches("ast.zoom.out", key(KeyCode.M, true, true, false))).isTrue();
            assertThat(Files.readString(KEY_BINDINGS_FILE, StandardCharsets.UTF_8))
                    .contains("\"action\": \"ast.zoom.out\"")
                    .contains("\"Ctrl+Alt+M\"");
        } finally {
            restore(SETTINGS_FILE, originalSettings);
            restore(KEY_BINDINGS_FILE, originalKeyBindings);
            MiniCSettings.load();
            MiniCKeyBindingConfig.loadDefault();
        }
    }

    @Test
    void settingsPaneCapturesOverridesAndRejectsConflicts() throws Exception {
        ensureFxStarted();
        String original = backup(KEY_BINDINGS_FILE);
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        try {
            Files.deleteIfExists(KEY_BINDINGS_FILE);
            AtomicReference<MiniCSettingsPane> paneRef = new AtomicReference<>();

            runFx(() -> {
                MiniCSettingsPane pane = new MiniCSettingsPane();
                Stage stage = new Stage();
                stage.setScene(new Scene(pane, 640, 480));
                stage.show();
                paneRef.set(pane);
                stageRef.set(stage);
            });

            Button zoomOut = lookupButton(paneRef.get(), "keybinding:ast.zoom.out");
            runFx(() -> {
                zoomOut.fire();
                zoomOut.fireEvent(key(KeyCode.EQUALS, true, false, false));
                zoomOut.fireEvent(key(KeyCode.ENTER, false, false, false));
            });

            assertThat(zoomOut.getStyleClass()).contains("key-binding-conflict");
            assertThat(paneRef.get().lookupAll(".key-binding-warning").stream()
                    .map(node -> node instanceof javafx.scene.control.Label label ? label.getText() : "")
                    .anyMatch(text -> text.contains("键位冲突"))).isTrue();
            assertThat(Files.exists(KEY_BINDINGS_FILE)).isFalse();

            runFx(() -> {
                zoomOut.fireEvent(key(KeyCode.CONTROL, true, false, false));
                zoomOut.fireEvent(key(KeyCode.ENTER, false, false, false));
            });

            assertThat(paneRef.get().lookupAll(".key-binding-warning").stream()
                    .map(node -> node instanceof javafx.scene.control.Label label ? label.getText() : "")
                    .anyMatch(text -> text.contains("普通按键"))).isTrue();
            assertThat(Files.exists(KEY_BINDINGS_FILE)).isFalse();

            runFx(() -> {
                zoomOut.fireEvent(key(KeyCode.M, true, true, false));
                zoomOut.fireEvent(key(KeyCode.ENTER, false, false, false));
            });

            assertThat(zoomOut.getStyleClass()).doesNotContain("key-binding-conflict");
            assertThat(Files.readString(KEY_BINDINGS_FILE, StandardCharsets.UTF_8)).contains("\"Ctrl+Alt+M\"");
        } finally {
            if (stageRef.get() != null) {
                runFx(() -> stageRef.get().close());
            }
            restore(KEY_BINDINGS_FILE, original);
            MiniCKeyBindingConfig.loadDefault();
        }
    }

    private static Button lookupButton(MiniCSettingsPane pane, String accessibleText) {
        return pane.lookupAll(".button").stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> accessibleText.equals(button.getAccessibleText()))
                .findFirst()
                .orElseThrow();
    }

    private static KeyEvent key(KeyCode code, boolean control, boolean alt, boolean shift) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, control, alt, false);
    }

    private static String backup(Path path) throws Exception {
        return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
    }

    private static void restore(Path path, String original) throws Exception {
        if (original == null) {
            Files.deleteIfExists(path);
            return;
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, original, StandardCharsets.UTF_8);
    }

    private static void ensureFxStarted() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(latch::countDown);
        }
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private static void runFx(Runnable runnable) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}
