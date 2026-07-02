package minic.uilocal;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.SplitPane;

import minic.settings.MiniCSettings;
import minic.uilocal.control.MiniCWorkbenchControlHub;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void startingPipelineCreatesReusableBeforeAfterTabsWithoutStealingFocusWhenAutoSplitDisabled() throws Exception {
        String originalSettings = backup(SETTINGS_FILE);
        try {
            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "dark",
                      "autoSplitPipelineTabs": "false"
                    }
                    """, StandardCharsets.UTF_8);
            MiniCSettings.load();
            MiniCWorkbenchViewModel model = new MiniCWorkbenchViewModel();
            MiniCWorkbenchShell shell = new MiniCWorkbenchShell(model);
            model.loadSource("pipeline-tabs.mc", "int main() { return 0; }");
            model.startSession();

            assertThat(shell.workspaceTabTitlesForTesting())
                    .containsExactly("pipeline-tabs.mc", "pipeline-tabs.mc before", "pipeline-tabs.mc after");
            assertThat(shell.activeLeftWorkspaceTabTitleForTesting()).isEqualTo("pipeline-tabs.mc");
            assertThat(shell.activeRightWorkspaceTabTitleForTesting()).isEmpty();

            shell.openStageTabsForTesting("lexer");
            shell.openStageTabsForTesting("parser");

            assertThat(shell.workspaceTabTitlesForTesting())
                    .containsExactly("pipeline-tabs.mc", "pipeline-tabs.mc before", "pipeline-tabs.mc after");
            assertThat(shell.activeLeftWorkspaceTabTitleForTesting()).isEqualTo("pipeline-tabs.mc");
            assertThat(shell.activeRightWorkspaceTabTitleForTesting()).isEmpty();
        } finally {
            restore(SETTINGS_FILE, originalSettings);
            MiniCSettings.load();
        }
    }

    @Test
    void startingPipelineSplitsReusableAfterTabRightWhenAutoSplitEnabled() throws Exception {
        String originalSettings = backup(SETTINGS_FILE);
        try {
            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "dark",
                      "autoSplitPipelineTabs": "true"
                    }
                    """, StandardCharsets.UTF_8);
            MiniCSettings.load();
            MiniCWorkbenchViewModel model = new MiniCWorkbenchViewModel();
            MiniCWorkbenchShell shell = new MiniCWorkbenchShell(model);
            model.loadSource("pipeline-tabs.mc", "int main() { return 0; }");
            model.startSession();

            assertThat(shell.workspaceTabTitlesForTesting())
                    .containsExactly("pipeline-tabs.mc", "pipeline-tabs.mc before", "pipeline-tabs.mc after");
            assertThat(shell.activeLeftWorkspaceTabTitleForTesting()).isEqualTo("pipeline-tabs.mc before");
            assertThat(shell.activeRightWorkspaceTabTitleForTesting()).contains("pipeline-tabs.mc after");

            shell.openStageTabsForTesting("lexer");
            shell.openStageTabsForTesting("parser");

            assertThat(shell.workspaceTabTitlesForTesting())
                    .containsExactly("pipeline-tabs.mc", "pipeline-tabs.mc before", "pipeline-tabs.mc after");
            assertThat(shell.activeLeftWorkspaceTabTitleForTesting()).isEqualTo("pipeline-tabs.mc before");
            assertThat(shell.activeRightWorkspaceTabTitleForTesting()).contains("pipeline-tabs.mc after");
        } finally {
            restore(SETTINGS_FILE, originalSettings);
            MiniCSettings.load();
        }
    }

    @Test
    void executionResultStaysVisibleBeforeNextAdvanceClosesReusableBeforeAfterTabs() throws Exception {
        String originalSettings = backup(SETTINGS_FILE);
        try {
            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "dark",
                      "autoSplitPipelineTabs": "true"
                    }
                    """, StandardCharsets.UTF_8);
            MiniCSettings.load();
            MiniCWorkbenchViewModel model = new MiniCWorkbenchViewModel();
            MiniCWorkbenchShell shell = new MiniCWorkbenchShell(model);
            model.loadSource("pipeline-tabs.mc", "int main() { return 0; }");
            model.startSession();
            model.runToExecution();

            model.next();

            assertThat(model.currentStateProperty().get().canNext()).isTrue();
            assertThat(shell.workspaceTabTitlesForTesting())
                    .containsExactly("pipeline-tabs.mc", "pipeline-tabs.mc before", "pipeline-tabs.mc after");

            model.next();

            assertThat(shell.workspaceTabTitlesForTesting())
                    .containsExactly("pipeline-tabs.mc");
            assertThat(shell.activeLeftWorkspaceTabTitleForTesting()).isEqualTo("pipeline-tabs.mc");
            assertThat(shell.activeRightWorkspaceTabTitleForTesting()).isEmpty();
            assertThat(model.sessionStartedProperty().get()).isFalse();
            assertThat(model.currentStateProperty().get()).isNull();
            assertThat(model.currentStageDataProperty().get()).isNull();
            assertThat(model.globalDataProperty().get()).isNull();
            assertThat(model.lastControlResultProperty().get()).isNull();
        } finally {
            restore(SETTINGS_FILE, originalSettings);
            MiniCSettings.load();
        }
    }

    @Test
    void switchingFromSourceTabToBeforeTabDoesNotResetPipelineProgress() throws Exception {
        ensureFxStarted();
        String originalSettings = backup(SETTINGS_FILE);
        try {
            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "dark",
                      "autoSplitPipelineTabs": "true"
                    }
                    """, StandardCharsets.UTF_8);
            MiniCSettings.load();
            MiniCWorkbenchViewModel model = new MiniCWorkbenchViewModel();
            MiniCWorkbenchShell shell = new MiniCWorkbenchShell(model);
            String sourceId = "source:" + System.identityHashCode(model);
            String beforeId = sourceId + ":pipeline:before";

            runFx(() -> {
                model.loadSource("pipeline-tabs.mc", "int main() { return 0; }");
                shell.createRoot();
                model.startSession();
                switchWorkspaceTab(shell, sourceId);
                assertThat(model.currentStateProperty().get()).isNotNull();

                switchWorkspaceTab(shell, beforeId);

                assertThat(model.currentStateProperty().get()).isNotNull();
                assertThat(model.currentStateProperty().get().currentStage()).isEqualTo("source");
            });
        } finally {
            restore(SETTINGS_FILE, originalSettings);
            MiniCSettings.load();
        }
    }

    @Test
    void advancingPipelineDoesNotRebuildWorkspaceContent() throws Exception {
        ensureFxStarted();
        String originalSettings = backup(SETTINGS_FILE);
        try {
            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "dark",
                      "autoSplitPipelineTabs": "true"
                    }
                    """, StandardCharsets.UTF_8);
            MiniCSettings.load();
            MiniCWorkbenchViewModel model = new MiniCWorkbenchViewModel();
            MiniCWorkbenchShell shell = new MiniCWorkbenchShell(model);

            runFx(() -> {
                model.loadSource("pipeline-tabs.mc", "int main() { return 0; }");
                shell.createRoot();
                model.startSession();
                Node beforeContent = primaryWorkspaceContent(shell);

                model.next();

                assertThat(primaryWorkspaceContent(shell)).isSameAs(beforeContent);
                assertThat(shell.workspaceTabTitlesForTesting())
                        .containsExactly("pipeline-tabs.mc", "pipeline-tabs.mc before", "pipeline-tabs.mc after");
                assertThat(shell.activeLeftWorkspaceTabTitleForTesting()).isEqualTo("pipeline-tabs.mc before");
            });
        } finally {
            restore(SETTINGS_FILE, originalSettings);
            MiniCSettings.load();
        }
    }

    @Test
    void switchingWorkspaceTabsWithinDocumentDoesNotRebuildWorkbenchShell() throws Exception {
        ensureFxStarted();
        String originalSettings = backup(SETTINGS_FILE);
        try {
            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "dark",
                      "autoSplitPipelineTabs": "true"
                    }
                    """, StandardCharsets.UTF_8);
            MiniCSettings.load();
            MiniCWorkbenchViewModel model = new MiniCWorkbenchViewModel();
            MiniCWorkbenchShell shell = new MiniCWorkbenchShell(model);
            String sourceId = "source:" + System.identityHashCode(model);

            runFx(() -> {
                model.loadSource("pipeline-tabs.mc", "int main() { return 0; }");
                shell.createRoot();
                model.startSession();
                Node sidebar = workbenchBodyChild(shell, 0);
                Node inspector = workbenchBodyChild(shell, 2);

                switchWorkspaceTab(shell, sourceId);

                assertThat(workbenchBodyChild(shell, 0)).isSameAs(sidebar);
                assertThat(workbenchBodyChild(shell, 2)).isSameAs(inspector);
                assertThat(shell.activeLeftWorkspaceTabTitleForTesting()).isEqualTo("pipeline-tabs.mc");
            });
        } finally {
            restore(SETTINGS_FILE, originalSettings);
            MiniCSettings.load();
        }
    }

    @Test
    void compilerControlsAreSeparateFromMetadataInspector() throws Exception {
        ensureFxStarted();
        runFx(() -> {
            MiniCWorkbenchViewModel model = new MiniCWorkbenchViewModel();
            MiniCWorkbenchControlHub controlHub = new MiniCWorkbenchControlHub();
            MiniCInspectorView inspector = new MiniCInspectorView(model, controlHub);
            MiniCCompilerControlsView controls = new MiniCCompilerControlsView(model, controlHub);

            assertThat(inspector.lookupAll(".inspector-control-button")).isEmpty();
            assertThat(controls.lookupAll(".inspector-control-button")).hasSize(6);
        });
    }

    @Test
    void pipelineLayoutDocksCompilerControlsAndCollapsesSidebars() throws Exception {
        ensureFxStarted();
        String originalSettings = backup(SETTINGS_FILE);
        try {
            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "dark",
                      "compilerControlsDock": "RIGHT_METADATA_TOP",
                      "pipelineLeftSidebarCollapsed": "false",
                      "pipelineRightSidebarCollapsed": "false"
                    }
                    """, StandardCharsets.UTF_8);
            MiniCSettings.load();
            MiniCWorkbenchViewModel model = new MiniCWorkbenchViewModel();
            MiniCWorkbenchShell shell = new MiniCWorkbenchShell(model);

            runFx(() -> {
                model.loadSource("layout.mc", "int main() { return 0; }");
                Parent root = shell.createRoot();
                model.startSession();

                assertThat(root.lookupAll(".right-metadata-controls")).hasSize(1);
                assertThat(model.currentStateProperty().get()).isNotNull();

                shell.setCompilerControlsDockForTesting("LEFT_PIPELINE_BOTTOM");
                assertThat(root.lookupAll(".left-pipeline-controls")).hasSize(1);
                assertThat(MiniCSettings.compilerControlsDock()).isEqualTo("LEFT_PIPELINE_BOTTOM");

                shell.setCompilerControlsDockForTesting("FLOATING");
                assertThat(root.lookupAll(".floating-compiler-controls")).hasSize(1);
                assertThat(MiniCSettings.compilerControlsDock()).isEqualTo("FLOATING");

                shell.setPipelineLeftSidebarCollapsedForTesting(true);
                shell.setPipelineRightSidebarCollapsedForTesting(true);

                assertThat(root.lookupAll(".pipeline-sidebar-rail")).hasSize(1);
                assertThat(root.lookupAll(".metadata-sidebar-rail")).hasSize(1);
                assertThat(MiniCSettings.pipelineLeftSidebarCollapsed()).isTrue();
                assertThat(MiniCSettings.pipelineRightSidebarCollapsed()).isTrue();
                assertThat(model.currentStateProperty().get()).isNotNull();
            });
        } finally {
            restore(SETTINGS_FILE, originalSettings);
            MiniCSettings.load();
        }
    }

    @Test
    void workspaceTabsMoveLeftAndReflowWhenLeftGroupBecomesEmpty() throws Exception {
        ensureFxStarted();
        String originalSettings = backup(SETTINGS_FILE);
        try {
            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "dark",
                      "autoSplitPipelineTabs": "true"
                    }
                    """, StandardCharsets.UTF_8);
            MiniCSettings.load();
            MiniCWorkbenchViewModel model = new MiniCWorkbenchViewModel();
            MiniCWorkbenchShell shell = new MiniCWorkbenchShell(model);
            String sourceId = "source:" + System.identityHashCode(model);
            String beforeId = sourceId + ":pipeline:before";
            String afterId = sourceId + ":pipeline:after";

            runFx(() -> {
                model.loadSource("split-tabs.mc", "int main() { return 0; }");
                shell.createRoot();
                model.startSession();

                assertThat(shell.activeRightWorkspaceTabTitleForTesting()).contains("split-tabs.mc after");

                shell.moveWorkspaceTabLeftForTesting(afterId);
                assertThat(shell.activeRightWorkspaceTabTitleForTesting()).isEmpty();
                assertThat(shell.activeLeftWorkspaceTabTitleForTesting()).isEqualTo("split-tabs.mc after");

                shell.splitWorkspaceTabRightForTesting(sourceId);
                shell.splitWorkspaceTabRightForTesting(beforeId);
                shell.splitWorkspaceTabRightForTesting(afterId);

                assertThat(shell.activeRightWorkspaceTabTitleForTesting()).isEmpty();
                assertThat(shell.activeLeftWorkspaceTabTitleForTesting()).isEqualTo("split-tabs.mc after");
            });
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

    private static void switchWorkspaceTab(MiniCWorkbenchShell shell, String id) throws Exception {
        Method method = MiniCWorkbenchShell.class.getDeclaredMethod("switchWorkspaceTab", String.class, boolean.class);
        method.setAccessible(true);
        method.invoke(shell, id, false);
    }

    private static Node primaryWorkspaceContent(MiniCWorkbenchShell shell) throws Exception {
        Field field = MiniCWorkbenchShell.class.getDeclaredField("workspaceSplit");
        field.setAccessible(true);
        SplitPane split = (SplitPane) field.get(shell);
        return ((javafx.scene.layout.Pane) split.getItems().getFirst()).getChildren().get(1);
    }

    private static Node workbenchBodyChild(MiniCWorkbenchShell shell, int index) throws Exception {
        Field field = MiniCWorkbenchShell.class.getDeclaredField("body");
        field.setAccessible(true);
        return ((javafx.scene.layout.Pane) field.get(shell)).getChildren().get(index);
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
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("timed out starting JavaFX");
        }
    }

    private static void runFx(ThrowingRunnable runnable) throws Exception {
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
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("timed out on JavaFX thread");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
