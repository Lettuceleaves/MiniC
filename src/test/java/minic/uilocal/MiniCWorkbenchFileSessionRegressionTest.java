package minic.uilocal;

import minic.settings.MiniCSettings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCWorkbenchFileSessionRegressionTest {
    private static final Path SETTINGS_FILE = Path.of("config", "settings.json");

    @Test
    void restoresPersistedOpenFilesInDecimalOrderOnStartup() throws Exception {
        String originalSettings = backup(SETTINGS_FILE);
        Path workspace = Files.createTempDirectory("minic-workbench-restore");
        Path first = workspace.resolve("first.mc").toAbsolutePath().normalize();
        Path second = workspace.resolve("second.mc").toAbsolutePath().normalize();
        try {
            Files.writeString(first, "int first() { return 1; }", StandardCharsets.UTF_8);
            Files.writeString(second, "int second() { return 2; }", StandardCharsets.UTF_8);
            MiniCSettings.load();
            MiniCSettings.setOpenFiles(List.of(
                    new MiniCSettings.OpenFileState(second, new BigDecimal("2")),
                    new MiniCSettings.OpenFileState(first, new BigDecimal("1"))
            ));

            MiniCWorkbenchShell shell = new MiniCWorkbenchShell(new MiniCWorkbenchViewModel());

            assertThat(shell.documentPathsForTesting()).containsExactly(first, second);
            assertThat(shell.documentNamesForTesting()).containsExactly("first.mc", "second.mc");
        } finally {
            restore(SETTINGS_FILE, originalSettings);
            MiniCSettings.load();
        }
    }

    @Test
    void reordersTabsByPersistingMovedDocumentDecimalOrder() throws Exception {
        String originalSettings = backup(SETTINGS_FILE);
        Path workspace = Files.createTempDirectory("minic-workbench-reorder");
        Path first = workspace.resolve("first.mc").toAbsolutePath().normalize();
        Path second = workspace.resolve("second.mc").toAbsolutePath().normalize();
        Path third = workspace.resolve("third.mc").toAbsolutePath().normalize();
        try {
            Files.writeString(first, "int first() { return 1; }", StandardCharsets.UTF_8);
            Files.writeString(second, "int second() { return 2; }", StandardCharsets.UTF_8);
            Files.writeString(third, "int third() { return 3; }", StandardCharsets.UTF_8);
            MiniCSettings.load();
            MiniCSettings.setOpenFiles(List.of(
                    new MiniCSettings.OpenFileState(first, new BigDecimal("1")),
                    new MiniCSettings.OpenFileState(second, new BigDecimal("2")),
                    new MiniCSettings.OpenFileState(third, new BigDecimal("3"))
            ));
            MiniCWorkbenchShell shell = new MiniCWorkbenchShell(new MiniCWorkbenchViewModel());

            shell.reorderDocumentTabsForTesting(2, 0);

            assertThat(shell.documentPathsForTesting()).containsExactly(third, first, second);
            assertThat(MiniCSettings.openFiles())
                    .extracting(MiniCSettings.OpenFileState::path)
                    .containsExactly(third, first, second);
            assertThat(MiniCSettings.openFiles().getFirst().order())
                    .isLessThan(MiniCSettings.openFiles().get(1).order());
        } finally {
            restore(SETTINGS_FILE, originalSettings);
            MiniCSettings.load();
        }
    }

    @Test
    void saveAsWritesCurrentDocumentAndPersistsDialogDirectoryAndOpenFile() throws Exception {
        String originalSettings = backup(SETTINGS_FILE);
        Path workspace = Files.createTempDirectory("minic-workbench-save-as");
        Path saved = workspace.resolve("saved.mc").toAbsolutePath().normalize();
        try {
            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "dark"
                    }
                    """, StandardCharsets.UTF_8);
            MiniCSettings.load();
            MiniCWorkbenchViewModel model = new MiniCWorkbenchViewModel();
            MiniCWorkbenchShell shell = new MiniCWorkbenchShell(model);
            model.loadSource("untitled-1.mc", "int main() { return 7; }");

            shell.saveDocumentAsForTesting(saved);

            assertThat(Files.readString(saved, StandardCharsets.UTF_8)).isEqualTo("int main() { return 7; }");
            assertThat(shell.documentPathsForTesting()).containsExactly(saved);
            assertThat(MiniCSettings.lastFileDialogDirectory()).contains(workspace.toAbsolutePath().normalize());
            assertThat(MiniCSettings.openFiles())
                    .extracting(MiniCSettings.OpenFileState::path)
                    .containsExactly(saved);
        } finally {
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
}
