package minic.ui;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import minic.settings.MiniCSettings;
import minic.settings.MiniCSettingsPane;
import minic.ui.control.MiniCWorkbenchControlHub;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCSettingsShortcutAndScrollRegressionTest {
    private static final Path SETTINGS_FILE = Path.of("config", "settings.json");
    private static final Path KEY_BINDINGS_FILE = Path.of("config", "keybindings.json");

    @Test
    void settingsPageUsesPersistentSideScrollPaneInWorkbench() throws Exception {
        ensureFxStarted();
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicReference<ScrollPane> settingsScroll = new AtomicReference<>();
        try {
            runFx(() -> {
                MiniCWorkbenchShell shell = new MiniCWorkbenchShell(new MiniCWorkbenchViewModel());
                Parent root = shell.createRoot();
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 720, 360));
                stage.show();
                stageRef.set(stage);

                Label settingsButton = root.lookupAll(".activity-item").stream()
                        .filter(Label.class::isInstance)
                        .map(Label.class::cast)
                        .filter(label -> "设置".equals(label.getAccessibleText()))
                        .findFirst()
                        .orElseThrow();
                settingsButton.fireEvent(primaryClick());

                ScrollPane scroll = (ScrollPane) ((BorderPane) root).getCenter();
                settingsScroll.set(scroll);
            });

            assertThat(settingsScroll.get().getStyleClass()).contains("settings-scroll");
            assertThat(settingsScroll.get().getVbarPolicy()).isEqualTo(ScrollPane.ScrollBarPolicy.ALWAYS);
            assertThat(settingsScroll.get().getHbarPolicy()).isEqualTo(ScrollPane.ScrollBarPolicy.NEVER);
            assertThat(settingsScroll.get().isFitToWidth()).isTrue();
            assertThat(settingsScroll.get().getContent()).isInstanceOf(MiniCSettingsPane.class);
        } finally {
            if (stageRef.get() != null) {
                runFx(() -> stageRef.get().close());
            }
        }
    }

    @Test
    void globalUiScaleCanBeBoundAndTriggeredFromWorkbenchShortcuts() throws Exception {
        ensureFxStarted();
        String originalSettings = backup(SETTINGS_FILE);
        String originalKeyBindings = backup(KEY_BINDINGS_FILE);
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        try {
            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "dark",
                      "uiScale": 1.0
                    }
                    """, StandardCharsets.UTF_8);
            MiniCSettings.load();
            Files.deleteIfExists(KEY_BINDINGS_FILE);
            MiniCKeyBindingConfig config = MiniCKeyBindingConfig.loadDefault();
            assertThat(config.actions()).contains(
                    MiniCWorkbenchControlHub.SETTINGS_UI_SCALE_INCREASE,
                    MiniCWorkbenchControlHub.SETTINGS_UI_SCALE_DECREASE
            );

            MiniCKeyBindingConfig.setKeys(MiniCWorkbenchControlHub.SETTINGS_UI_SCALE_INCREASE, List.of("Ctrl+Alt+U"));
            MiniCKeyBindingConfig.setKeys(MiniCWorkbenchControlHub.SETTINGS_UI_SCALE_DECREASE, List.of("Ctrl+Alt+Shift+U"));

            runFx(() -> {
                MiniCWorkbenchShell shell = new MiniCWorkbenchShell(new MiniCWorkbenchViewModel());
                Parent root = shell.createRoot();
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 720, 360));
                stage.show();
                stageRef.set(stage);

                root.fireEvent(key(KeyCode.U, true, true, false));
                assertThat(MiniCSettings.uiScale()).isEqualTo(1.05);

                root.fireEvent(key(KeyCode.U, true, true, true));
                assertThat(MiniCSettings.uiScale()).isEqualTo(1.0);
            });
        } finally {
            if (stageRef.get() != null) {
                runFx(() -> stageRef.get().close());
            }
            restore(SETTINGS_FILE, originalSettings);
            restore(KEY_BINDINGS_FILE, originalKeyBindings);
            MiniCSettings.load();
            MiniCKeyBindingConfig.loadDefault();
        }
    }

    private static KeyEvent key(KeyCode code, boolean control, boolean alt, boolean shift) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, control, alt, false);
    }

    private static MouseEvent primaryClick() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                0,
                0,
                0,
                0,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                null
        );
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
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.setImplicitExit(false);
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
