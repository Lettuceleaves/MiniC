package minic.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import minic.settings.MiniCSettings;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCBottomPanelScaleRegressionTest {
    private static final Path SETTINGS_FILE = Path.of("config", "settings.json");

    @Test
    void bottomPanelChromeHeightTracksGlobalUiScale() throws Exception {
        ensureFxStarted();
        String originalSettings = backup(SETTINGS_FILE);
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        try {
            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "dark",
                      "uiScale": 1.5
                    }
                    """, StandardCharsets.UTF_8);
            MiniCSettings.load();

            runFx(() -> {
                MiniCHoverInspector inspector = new MiniCHoverInspector();
                MiniCBottomPanel panel = new MiniCBottomPanel(inspector);
                Stage stage = new Stage();
                stage.setScene(new Scene(panel, 640, 360));
                stage.show();
                stageRef.set(stage);

                assertThat(panel.getPrefHeight()).isEqualTo(36.0);
                assertThat(panel.getChildren().get(0).isManaged()).isFalse();

                inspector.show(new MiniCHoverInspectorContent(
                        "Token",
                        List.of("kind=IDENT"),
                        "int main() { return 0; }",
                        null,
                        "说明"
                ));

                assertThat(panel.getPrefHeight()).isEqualTo(318.0);
                assertThat(panel.getChildren().get(0).isManaged()).isTrue();

                MiniCSettings.setUiScale(1.25);
                assertThat(panel.getPrefHeight()).isEqualTo(265.0);
            });
        } finally {
            if (stageRef.get() != null) {
                runFx(() -> stageRef.get().close());
            }
            restore(SETTINGS_FILE, originalSettings);
            MiniCSettings.load();
        }
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
