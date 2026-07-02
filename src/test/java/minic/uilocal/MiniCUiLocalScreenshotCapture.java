package minic.uilocal;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import minic.color.ThemeManager;
import minic.settings.MiniCSettings;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Headful JavaFX screenshot fixture used by UIWeb parity verification.
 */
public final class MiniCUiLocalScreenshotCapture {
    private static final Path SETTINGS_FILE = Path.of("config", "settings.json");
    private static final List<Viewport> VIEWPORTS = List.of(
            new Viewport("desktop-1920x1080", 1920, 1080),
            new Viewport("desktop-1366x768", 1366, 768),
            new Viewport("mobile-390x844", 390, 844)
    );
    private static final List<String> STAGES = List.of(
            "source",
            "preprocess",
            "lexer",
            "parser",
            "semantic",
            "ir",
            "codegen",
            "toolchain",
            "execution"
    );
    private static final String WORKFLOW_SOURCE = String.join("\n",
            "// @visual root=node kind=binary-tree label=key",
            "struct Node { int key; struct Node *left; struct Node *right; };",
            "",
            "int inc(int value) {",
            "    int next = value + 1;",
            "    return next;",
            "}",
            "",
            "int main() {",
            "    struct Node node;",
            "    int x = 0;",
            "    node.key = inc(1);",
            "    node.left = NULL;",
            "    node.right = NULL;",
            "    x = node.key;",
            "    x = inc(x);",
            "    x = inc(x);",
            "    return x;",
            "}",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            ""
    );

    private MiniCUiLocalScreenshotCapture() {
    }

    public static void main(String[] args) throws Exception {
        Path outputRoot = Path.of(args.length == 0 ? "uiweb-render-check/parity-report/uilocal" : args[0])
                .toAbsolutePath()
                .normalize();
        SettingsBackup settingsBackup = backupSettings();
        int exitCode = 0;
        try {
            cleanOutputRoot(outputRoot);
            ensureFxStarted();
            for (Viewport viewport : VIEWPORTS) {
                System.out.println("Capturing JavaFX viewport " + viewport.id());
                captureViewport(outputRoot, viewport);
            }
            System.out.println("JavaFX screenshots captured at " + outputRoot);
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.err);
            exitCode = 1;
        } finally {
            restoreSettings(settingsBackup);
            Platform.exit();
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static void captureViewport(Path outputRoot, Viewport viewport) throws Exception {
        FxWorkbench workbench = runFx(() -> openWorkbench(viewport));
        try {
            waitFor("realtime analysis for " + viewport.id(), () -> runFx(() ->
                    workbench.model().realtimeAnalysisProperty().get() != null));
            capture(outputRoot, viewport, "pipeline-before-start", workbench.root());
            capture(outputRoot, viewport, "source-before-start", workbench.root());

            runFx(() -> {
                MiniCCodeEditor editor = codeEditor(workbench.root());
                editor.scrollVerticalBy(160);
                workbench.model().setDebugBreakpoints(List.of(12));
                pulse(workbench.root());
                return null;
            });
            capture(outputRoot, viewport, "source-long-scroll-breakpoint", workbench.root());

            runFx(() -> {
                workbench.model().startSession();
                pulse(workbench.root());
                return null;
            });
            waitFor("pipeline start for " + viewport.id(), () -> runFx(() ->
                    workbench.model().currentStateProperty().get() != null));
            capture(outputRoot, viewport, "pipeline-after-start", workbench.root());

            runFx(() -> {
                workbench.model().runToExecution();
                pulse(workbench.root());
                return null;
            });
            waitFor("execution stage for " + viewport.id(), () -> runFx(() ->
                    workbench.model().currentStateProperty().get() != null
                            && "execution".equals(workbench.model().currentStateProperty().get().currentStage())));
            for (String stage : STAGES) {
                runFx(() -> {
                    workbench.shell().openStageTabsForTesting(stage);
                    pulse(workbench.root());
                    return null;
                });
                capture(outputRoot, viewport, "pipeline-stage-" + stage, workbench.root());
            }

            runFx(() -> {
                selectActivity(workbench.root(), "调试");
                pulse(workbench.root());
                return null;
            });
            capture(outputRoot, viewport, "debug-before-start", workbench.root());
            runFx(() -> {
                workbench.model().setDebugBreakpoints(List.of(12));
                workbench.model().startDebug();
                workbench.model().debugStepInto();
                pulse(workbench.root());
                return null;
            });
            waitFor("debug start for " + viewport.id(), () -> runFx(() ->
                    workbench.model().debugStartedProperty().get()
                            && workbench.model().debugStateProperty().get() != null));
            capture(outputRoot, viewport, "debug-metadata", workbench.root());
            capture(outputRoot, viewport, "debug-source", workbench.root());
            selectDebugView(workbench.root(), "数据结构");
            capture(outputRoot, viewport, "debug-data-structure", workbench.root());
            capture(outputRoot, viewport, "debug-visual-diagram", workbench.root());
            selectDebugView(workbench.root(), "AST");
            capture(outputRoot, viewport, "debug-ast", workbench.root());
            selectDebugView(workbench.root(), "IR");
            capture(outputRoot, viewport, "debug-ir", workbench.root());
            selectDebugView(workbench.root(), "ASM");
            capture(outputRoot, viewport, "debug-asm", workbench.root());

            runFx(() -> {
                selectActivity(workbench.root(), "设置");
                pulse(workbench.root());
                return null;
            });
            capture(outputRoot, viewport, "settings", workbench.root());

            runFx(() -> {
                selectActivity(workbench.root(), "信息");
                pulse(workbench.root());
                return null;
            });
            capture(outputRoot, viewport, "info", workbench.root());

            runFx(() -> {
                selectActivity(workbench.root(), "代码区");
                pulse(workbench.root());
                return null;
            });
            capture(outputRoot, viewport, "bottom-panel-collapsed", workbench.root());
            runFx(() -> {
                clickButton(workbench.root(), ".bottom-toggle");
                pulse(workbench.root());
                return null;
            });
            capture(outputRoot, viewport, "bottom-panel-expanded", workbench.root());
        } finally {
            runFx(() -> {
                workbench.stage().close();
                return null;
            });
        }
    }

    private static FxWorkbench openWorkbench(Viewport viewport) {
        MiniCSettings.load();
        MiniCSettings.setAutoSplitPipelineTabs(true);
        MiniCWorkbenchViewModel model = new MiniCWorkbenchViewModel();
        MiniCWorkbenchShell shell = new MiniCWorkbenchShell(model);
        Parent root = shell.createRoot();
        Scene scene = new Scene(root, viewport.width(), viewport.height());
        ThemeManager.bind(scene);
        Stage stage = new Stage();
        stage.setScene(scene);
        stage.show();
        model.loadSource("03_red_black_tree.mc", WORKFLOW_SOURCE);
        model.submitRealtimeSource("03_red_black_tree.mc", WORKFLOW_SOURCE);
        pulse(root);
        return new FxWorkbench(stage, root, model, shell);
    }

    private static SettingsBackup backupSettings() throws IOException {
        Path normalized = SETTINGS_FILE.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            return new SettingsBackup(normalized, false, null);
        }
        return new SettingsBackup(normalized, true, Files.readAllBytes(normalized));
    }

    private static void restoreSettings(SettingsBackup backup) {
        try {
            if (backup.existed()) {
                Files.createDirectories(backup.path().getParent());
                Files.write(backup.path(), backup.content());
            } else {
                Files.deleteIfExists(backup.path());
            }
        } catch (IOException exception) {
            System.err.println("Failed to restore settings file after screenshot capture: " + exception.getMessage());
        }
    }

    private static void selectActivity(Parent root, String accessibleText) {
        Label item = root.lookupAll(".activity-item").stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .filter(label -> accessibleText.equals(label.getAccessibleText()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing activity item: " + accessibleText));
        item.fireEvent(primaryClick());
        pulse(root);
    }

    private static void selectDebugView(Parent root, String text) throws Exception {
        runFx(() -> {
            Button button = root.lookupAll(".debug-view-button").stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .filter(candidate -> text.equals(candidate.getText()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("missing debug view button: " + text));
            button.fire();
            pulse(root);
            return null;
        });
    }

    private static void clickButton(Parent root, String selector) {
        Button button = root.lookupAll(selector).stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing button: " + selector));
        button.fire();
    }

    private static MiniCCodeEditor codeEditor(Parent root) {
        Node editor = root.lookup(".code-editor");
        if (!(editor instanceof MiniCCodeEditor codeEditor)) {
            throw new IllegalStateException("missing MiniCCodeEditor");
        }
        return codeEditor;
    }

    private static void capture(Path outputRoot, Viewport viewport, String state, Parent root) throws Exception {
        runFx(() -> {
            System.out.println("  " + viewport.id() + "/" + state);
            pulse(root);
            Path file = outputRoot.resolve(viewport.id()).resolve(state + ".png");
            Files.createDirectories(file.getParent());
            WritableImage image = new WritableImage(viewport.width(), viewport.height());
            root.snapshot(new SnapshotParameters(), image);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file.toFile());
            long size = Files.size(file);
            if (size < 8_000L) {
                throw new IOException("screenshot looks blank: " + file + " (" + size + " bytes)");
            }
            return null;
        });
    }

    private static void pulse(Parent root) {
        root.applyCss();
        root.layout();
    }

    private static javafx.scene.input.MouseEvent primaryClick() {
        return new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                0,
                0,
                0,
                0,
                javafx.scene.input.MouseButton.PRIMARY,
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

    private static void cleanOutputRoot(Path outputRoot) throws IOException {
        Path normalized = outputRoot.toAbsolutePath().normalize();
        if (!normalized.endsWith(Path.of("parity-report", "uilocal"))) {
            throw new IOException("refusing to clean unexpected screenshot path: " + normalized);
        }
        if (Files.exists(normalized)) {
            try (var paths = Files.walk(normalized)) {
                for (Path path : paths.sorted((left, right) -> right.compareTo(left)).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(normalized);
    }

    private static void waitFor(String label, CheckedBooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("timed out waiting for " + label);
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

    private static <T> T runFx(Callable<T> callable) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(callable.call());
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
        return result.get();
    }

    private record Viewport(String id, int width, int height) {
        private Viewport {
            Objects.requireNonNull(id, "id");
        }
    }

    private record FxWorkbench(Stage stage, Parent root, MiniCWorkbenchViewModel model, MiniCWorkbenchShell shell) {
    }

    private record SettingsBackup(Path path, boolean existed, byte[] content) {
    }

    @FunctionalInterface
    private interface CheckedBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }
}
