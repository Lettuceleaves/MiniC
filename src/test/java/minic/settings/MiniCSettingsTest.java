package minic.settings;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MiniCSettingsTest {
    private static final Path SETTINGS_FILE = Path.of("config", "settings.json");

    @Test
    void loadPersistsMissingDefaultControlSettings() throws IOException {
        String original = Files.exists(SETTINGS_FILE)
                ? Files.readString(SETTINGS_FILE, StandardCharsets.UTF_8)
                : null;
        try {
            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "light"
                    }
                    """, StandardCharsets.UTF_8);

            MiniCSettings.load();

            String persisted = Files.readString(SETTINGS_FILE, StandardCharsets.UTF_8);
            assertThat(MiniCSettings.theme()).isEqualTo("light");
            assertThat(MiniCSettings.frameIntervalMillis()).isEqualTo(1000);
            assertThat(MiniCSettings.graphZoomStep()).isCloseTo(0.025, within(0.0001));
            assertThat(MiniCSettings.graphZoomAnchor()).isEqualTo("mouse");
            assertThat(persisted)
                    .contains("\"theme\": \"light\"")
                    .contains("\"frameInterval\": 1000")
                    .contains("\"graphZoomStep\": 0.025")
                    .contains("\"graphZoomAnchor\": \"mouse\"");
        } finally {
            if (original == null) {
                Files.deleteIfExists(SETTINGS_FILE);
            } else {
                Files.writeString(SETTINGS_FILE, original, StandardCharsets.UTF_8);
            }
            MiniCSettings.load();
        }
    }
}
