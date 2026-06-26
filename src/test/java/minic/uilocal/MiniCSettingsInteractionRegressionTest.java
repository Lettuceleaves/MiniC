package minic.uilocal;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.stage.Stage;
import minic.color.ThemeCssGenerator;
import minic.settings.MiniCSettings;
import minic.settings.MiniCSettingsPane;
import minic.uilocal.control.MiniCWorkbenchControlHub;
import minic.uiapi.UiSourceSpanDto;
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
            assertThat(staticDoubleField(MiniCVisualPane.class, "MIN_AST_ZOOM")).isEqualTo(0.05);
            assertThat(staticDoubleField(MiniCDebugPane.class, "MIN_AST_ZOOM")).isEqualTo(0.05);

            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "light"
                    }
                    """, StandardCharsets.UTF_8);
            MiniCSettings.load();
            assertThat(MiniCSettings.uiScale()).isEqualTo(1.0);
            assertThat(MiniCSettings.graphZoomStep()).isEqualTo(0.025);
            assertThat(MiniCSettings.graphZoomAnchor()).isEqualTo("mouse");
            assertThat(MiniCSettings.autoSplitPipelineTabs()).isFalse();
            assertThat(Files.readString(SETTINGS_FILE, StandardCharsets.UTF_8))
                    .contains("\"uiScale\": 1.0")
                    .contains("\"graphZoomStep\": 0.025")
                    .contains("\"graphZoomAnchor\": \"mouse\"")
                    .contains("\"autoSplitPipelineTabs\": \"false\"");

            MiniCSettings.setUiScale(MiniCSettings.maxUiScale() * 4);
            assertThat(MiniCSettings.uiScale()).isEqualTo(MiniCSettings.maxUiScale());
            MiniCSettings.setUiScale(MiniCSettings.minUiScale() / 4);
            assertThat(MiniCSettings.uiScale()).isEqualTo(MiniCSettings.minUiScale());

            MiniCSettings.setUiScale(1.25);
            String scaledCss = ThemeCssGenerator.generate();
            assertThat(scaledCss)
                    .contains("-fx-font-size: 16.25px")
                    .contains("-fx-pref-width: 60px");
            assertThat(scaledCss).doesNotContain("{{");

            MiniCSettings.setGraphZoomStep(MiniCSettings.maxGraphZoomStep() * 4);
            assertThat(MiniCSettings.graphZoomStep()).isEqualTo(MiniCSettings.maxGraphZoomStep());
            assertThat(Files.readString(SETTINGS_FILE, StandardCharsets.UTF_8))
                    .contains("\"graphZoomStep\": " + MiniCSettings.maxGraphZoomStep());
            MiniCSettings.setAutoSplitPipelineTabs(true);
            assertThat(MiniCSettings.autoSplitPipelineTabs()).isTrue();
            assertThat(Files.readString(SETTINGS_FILE, StandardCharsets.UTF_8))
                    .contains("\"autoSplitPipelineTabs\": \"true\"");

            Files.deleteIfExists(KEY_BINDINGS_FILE);
            MiniCKeyBindingConfig config = MiniCKeyBindingConfig.loadDefault();
            assertThat(config.actions()).contains(
                    MiniCWorkbenchControlHub.VIEWPORT_ZOOM_IN,
                    MiniCWorkbenchControlHub.VIEWPORT_ZOOM_OUT,
                    MiniCWorkbenchControlHub.VIEWPORT_SCROLL_UP,
                    MiniCWorkbenchControlHub.VIEWPORT_SCROLL_DOWN,
                    MiniCWorkbenchControlHub.VIEWPORT_SCROLL_LEFT,
                    MiniCWorkbenchControlHub.VIEWPORT_SCROLL_RIGHT,
                    MiniCWorkbenchControlHub.VIEWPORT_CENTER_ACTIVE,
                    MiniCWorkbenchControlHub.DEBUG_START,
                    MiniCWorkbenchControlHub.DEBUG_RUN_TO_END,
                    MiniCWorkbenchControlHub.DEBUG_RUN_TO_BREAKPOINT,
                    MiniCWorkbenchControlHub.DEBUG_STEP_OVER,
                    MiniCWorkbenchControlHub.DEBUG_STEP_INTO,
                    MiniCWorkbenchControlHub.DEBUG_BACK_TO_BREAKPOINT,
                    MiniCWorkbenchControlHub.DEBUG_STEP_BACK_OVER,
                    MiniCWorkbenchControlHub.DEBUG_STEP_BACK,
                    MiniCWorkbenchControlHub.COMPILER_NEXT,
                    MiniCWorkbenchControlHub.COMPILER_NEXT_STAGE,
                    MiniCWorkbenchControlHub.COMPILER_RUN_TO_EXECUTION,
                    MiniCWorkbenchControlHub.COMPILER_PLAY,
                    MiniCWorkbenchControlHub.COMPILER_PLAY_FAST,
                    MiniCWorkbenchControlHub.COMPILER_PAUSE,
                    MiniCWorkbenchControlHub.SETTINGS_THEME_NEXT,
                    MiniCWorkbenchControlHub.SETTINGS_THEME_PREVIOUS,
                    MiniCWorkbenchControlHub.SETTINGS_FRAME_INTERVAL_INCREASE,
                    MiniCWorkbenchControlHub.SETTINGS_FRAME_INTERVAL_DECREASE
            );
            assertThat(MiniCKeyBindingConfig.conflictingAction(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_OUT, "Ctrl+="))
                    .contains(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_IN);
            assertThat(MiniCKeyBindingConfig.isReserved("Enter")).isTrue();
            assertThat(MiniCKeyBindingConfig.isReserved("Esc")).isTrue();
            assertThat(MiniCKeyBindingConfig.normalizeCombo("Ctrl+Alt+MouseLeft")).isEqualTo("Ctrl+Alt+MouseLeft");
            assertThat(MiniCKeyBindingConfig.normalizeCombo("Shift+WheelDown")).isEqualTo("Shift+WheelDown");
            assertThat(MiniCKeyBindingConfig.normalizeCombo("A+Shift+WheelDown")).isEqualTo("Shift+A+WheelDown");
            assertThat(MiniCKeyBindingConfig.normalizeCombo("Control+Alt+WheelUp")).isEqualTo("Ctrl+Alt+WheelUp");

            MiniCKeyBindingConfig.setKeys("ast.zoom.out", List.of("Ctrl+Alt+M"));
            assertThat(config.matches(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_OUT, key(KeyCode.M, true, true, false))).isTrue();
            assertThat(config.matches("ast.zoom.out", key(KeyCode.M, true, true, false))).isTrue();

            MiniCKeyBindingConfig.setKeys(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_DOWN, List.of("A+Shift+WheelDown"));
            assertThat(config.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_DOWN, scroll(-120, false, false, true), java.util.Set.of(KeyCode.A))).isTrue();
            assertThat(config.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_DOWN, scroll(-120, false, false, true))).isFalse();
            assertThat(config.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_DOWN, scroll(120, false, false, true), java.util.Set.of(KeyCode.A))).isFalse();
            assertThat(Files.readString(KEY_BINDINGS_FILE, StandardCharsets.UTF_8))
                    .contains("\"action\": \"" + MiniCWorkbenchControlHub.VIEWPORT_ZOOM_OUT + "\"")
                    .contains("\"Ctrl+Alt+M\"")
                    .contains("\"Shift+A+WheelDown\"");
        } finally {
            restore(SETTINGS_FILE, originalSettings);
            restore(KEY_BINDINGS_FILE, originalKeyBindings);
            MiniCSettings.load();
            MiniCKeyBindingConfig.loadDefault();
        }
    }

    @Test
    void persistsPipelineLayoutState() throws Exception {
        String originalSettings = backup(SETTINGS_FILE);
        try {
            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "dark",
                      "pipelineLeftSidebarCollapsed": "true",
                      "pipelineRightSidebarCollapsed": "false",
                      "compilerControlsDock": "FLOATING",
                      "compilerControlsFloatingX": 42,
                      "compilerControlsFloatingY": 56,
                      "compilerControlsFloatingWidth": 360,
                      "compilerControlsFloatingHeight": 144
                    }
                    """, StandardCharsets.UTF_8);
            MiniCSettings.load();

            assertThat(MiniCSettings.pipelineLeftSidebarCollapsed()).isTrue();
            assertThat(MiniCSettings.pipelineRightSidebarCollapsed()).isFalse();
            assertThat(MiniCSettings.compilerControlsDock()).isEqualTo("FLOATING");
            assertThat(MiniCSettings.compilerControlsFloatingRect())
                    .isEqualTo(new MiniCSettings.FloatingRect(42, 56, 360, 144));

            MiniCSettings.setPipelineLeftSidebarCollapsed(false);
            MiniCSettings.setPipelineRightSidebarCollapsed(true);
            MiniCSettings.setCompilerControlsDock("LEFT_PIPELINE_BOTTOM");
            MiniCSettings.setCompilerControlsFloatingRect(new MiniCSettings.FloatingRect(8, 12, 280, 96));

            assertThat(MiniCSettings.pipelineLeftSidebarCollapsed()).isFalse();
            assertThat(MiniCSettings.pipelineRightSidebarCollapsed()).isTrue();
            assertThat(MiniCSettings.compilerControlsDock()).isEqualTo("LEFT_PIPELINE_BOTTOM");
            assertThat(MiniCSettings.compilerControlsFloatingRect())
                    .isEqualTo(new MiniCSettings.FloatingRect(8, 12, 280, 96));
            assertThat(Files.readString(SETTINGS_FILE, StandardCharsets.UTF_8))
                    .contains("\"pipelineLeftSidebarCollapsed\": \"false\"")
                    .contains("\"pipelineRightSidebarCollapsed\": \"true\"")
                    .contains("\"compilerControlsDock\": \"LEFT_PIPELINE_BOTTOM\"")
                    .contains("\"compilerControlsFloatingX\": 8.0")
                    .contains("\"compilerControlsFloatingY\": 12.0")
                    .contains("\"compilerControlsFloatingWidth\": 280.0")
                    .contains("\"compilerControlsFloatingHeight\": 96.0");

            MiniCSettings.setCompilerControlsDock("unknown");
            assertThat(MiniCSettings.compilerControlsDock()).isEqualTo("RIGHT_METADATA_TOP");
        } finally {
            restore(SETTINGS_FILE, originalSettings);
            MiniCSettings.load();
        }
    }

    @Test
    void settingsPaneCapturesOverridesAndRejectsConflicts() throws Exception {
        ensureFxStarted();
        String originalSettings = backup(SETTINGS_FILE);
        String original = backup(KEY_BINDINGS_FILE);
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        try {
            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "light"
                    }
                    """, StandardCharsets.UTF_8);
            MiniCSettings.load();
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

            Button zoomOut = lookupButton(paneRef.get(), "keybinding:" + MiniCWorkbenchControlHub.VIEWPORT_ZOOM_OUT);
            Slider uiScale = lookupSlider(paneRef.get(), "setting:uiScale");
            CheckBox autoSplit = lookupCheckBox(paneRef.get(), "setting:autoSplitPipelineTabs");
            assertThat(uiScale.getValue()).isEqualTo(1.0);
            runFx(() -> uiScale.setValue(1.25));
            assertThat(MiniCSettings.uiScale()).isEqualTo(1.25);
            assertThat(Files.readString(SETTINGS_FILE, StandardCharsets.UTF_8)).contains("\"uiScale\": 1.25");
            assertThat(autoSplit.isSelected()).isFalse();
            runFx(() -> autoSplit.setSelected(true));
            assertThat(MiniCSettings.autoSplitPipelineTabs()).isTrue();
            assertThat(Files.readString(SETTINGS_FILE, StandardCharsets.UTF_8))
                    .contains("\"autoSplitPipelineTabs\": \"true\"");
            assertThat(lookupButton(paneRef.get(), "keybinding:" + MiniCWorkbenchControlHub.DEBUG_STEP_OVER)).isNotNull();
            assertThat(lookupButton(paneRef.get(), "keybinding:" + MiniCWorkbenchControlHub.COMPILER_NEXT)).isNotNull();
            assertThat(lookupButton(paneRef.get(), "keybinding:" + MiniCWorkbenchControlHub.SETTINGS_FRAME_INTERVAL_INCREASE))
                    .isNotNull();
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

            runFx(() -> zoomOut.fireEvent(key(KeyCode.ESCAPE, false, false, false)));

            assertThat(zoomOut.getStyleClass()).doesNotContain("key-binding-conflict", "key-binding-capturing");
            assertThat(zoomOut.getText()).contains("Ctrl+-");
            assertThat(Files.exists(KEY_BINDINGS_FILE)).isFalse();

            runFx(() -> {
                zoomOut.fire();
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

            runFx(() -> {
                zoomOut.fire();
                zoomOut.fireEvent(key(KeyCode.A, true, false, true));
                zoomOut.fireEvent(scroll(-120, true, false, true));
                zoomOut.fireEvent(key(KeyCode.ENTER, false, false, false));
            });

            assertThat(zoomOut.getStyleClass()).doesNotContain("key-binding-conflict");
            assertThat(zoomOut.getText()).contains("Ctrl+Shift+A+WheelDown");
            assertThat(Files.readString(KEY_BINDINGS_FILE, StandardCharsets.UTF_8)).contains("\"Ctrl+Shift+A+WheelDown\"");
        } finally {
            if (stageRef.get() != null) {
                runFx(() -> stageRef.get().close());
            }
            restore(KEY_BINDINGS_FILE, original);
            restore(SETTINGS_FILE, originalSettings);
            MiniCSettings.load();
            MiniCKeyBindingConfig.loadDefault();
        }
    }

    @Test
    void workbenchShellRoutesConfiguredCompilerShortcutThroughControlHub() throws Exception {
        ensureFxStarted();
        String originalSettings = backup(SETTINGS_FILE);
        String original = backup(KEY_BINDINGS_FILE);
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        try {
            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "dark",
                      "uiScale": 1.25
                    }
                    """, StandardCharsets.UTF_8);
            MiniCSettings.load();
            Files.deleteIfExists(KEY_BINDINGS_FILE);
            MiniCKeyBindingConfig.loadDefault();
            MiniCKeyBindingConfig.setKeys(MiniCWorkbenchControlHub.COMPILER_NEXT, List.of("A+WheelDown"));
            AtomicReference<String> beforeStage = new AtomicReference<>();
            AtomicReference<String> afterStage = new AtomicReference<>();
            AtomicReference<Double> rootScale = new AtomicReference<>();
            AtomicReference<Double> rootScaleAfterChange = new AtomicReference<>();

            runFx(() -> {
                MiniCWorkbenchViewModel model = new MiniCWorkbenchViewModel();
                MiniCWorkbenchShell shell = new MiniCWorkbenchShell(model);
                model.loadSource("shortcut.mc", "int main() { return 0; }");
                model.startSession();
                Parent root = shell.createRoot();
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 900, 640));
                stage.show();
                stageRef.set(stage);
                rootScale.set(root.getScaleX());
                MiniCSettings.setUiScale(1.1);
                rootScaleAfterChange.set(root.getScaleX());
                beforeStage.set(model.currentStateProperty().get().currentStage());

                root.fireEvent(key(KeyCode.A, false, false, false));
                root.fireEvent(scroll(-120, false, false, false));

                afterStage.set(model.currentStateProperty().get().currentStage());
            });

            assertThat(beforeStage.get()).isEqualTo("source");
            assertThat(afterStage.get()).isNotEqualTo("source");
            assertThat(rootScale.get()).isEqualTo(1.0);
            assertThat(rootScaleAfterChange.get()).isEqualTo(1.0);
        } finally {
            if (stageRef.get() != null) {
                runFx(() -> stageRef.get().close());
            }
            restore(KEY_BINDINGS_FILE, original);
            restore(SETTINGS_FILE, originalSettings);
            MiniCSettings.load();
            MiniCKeyBindingConfig.loadDefault();
        }
    }

    @Test
    void debugPaneRoutesConfiguredDebuggerShortcutThroughControlHub() throws Exception {
        ensureFxStarted();
        String original = backup(KEY_BINDINGS_FILE);
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        try {
            Files.deleteIfExists(KEY_BINDINGS_FILE);
            MiniCKeyBindingConfig.loadDefault();
            MiniCKeyBindingConfig.setKeys(MiniCWorkbenchControlHub.DEBUG_STEP_INTO, List.of("Ctrl+A+WheelUp"));
            AtomicReference<Long> beforeStep = new AtomicReference<>();
            AtomicReference<Long> afterStep = new AtomicReference<>();

            runFx(() -> {
                MiniCWorkbenchViewModel model = new MiniCWorkbenchViewModel();
                model.loadSource("debug-shortcut.mc", """
                        int main() {
                            int x = 1;
                            return x;
                        }
                        """);
                model.startDebug();
                MiniCDebugPane pane = new MiniCDebugPane(model);
                Stage stage = new Stage();
                stage.setScene(new Scene(pane, 900, 640));
                stage.show();
                stageRef.set(stage);
                beforeStep.set(model.debugStateProperty().get().currentSnapshot().visibleStepIndex());

                pane.fireEvent(key(KeyCode.A, true, false, false));
                pane.fireEvent(scroll(120, true, false, false));

                afterStep.set(model.debugStateProperty().get().currentSnapshot().visibleStepIndex());
            });

            assertThat(afterStep.get()).isGreaterThan(beforeStep.get());
        } finally {
            if (stageRef.get() != null) {
                runFx(() -> stageRef.get().close());
            }
            restore(KEY_BINDINGS_FILE, original);
            MiniCKeyBindingConfig.loadDefault();
        }
    }

    @Test
    void hoverInspectorSourceSnippetKeepsRangeMaskWhileUsingSyntaxTextRoles() throws Exception {
        ensureFxStarted();
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        try {
            runFx(() -> {
                String source = """
                        int main() {
                            return 7;
                        }
                        """;
                int returnStart = source.indexOf("return");
                MiniCHoverInspector inspector = new MiniCHoverInspector();
                MiniCBottomPanel panel = new MiniCBottomPanel(inspector);
                Stage stage = new Stage();
                stage.setScene(new Scene(panel, 720, 260));
                stage.show();
                stageRef.set(stage);

                inspector.show(new MiniCHoverInspectorContent(
                        "Token RETURN",
                        List.of("类型: RETURN"),
                        source,
                        new UiSourceSpanDto("snippet.mc", returnStart, returnStart + "return".length(), 2, 5, 2, 11),
                        "return 会把值交给调用者。"
                ));
                panel.applyCss();
                panel.layout();

                List<Label> sourceChars = panel.lookupAll(".hover-source-text").stream()
                        .filter(Label.class::isInstance)
                        .map(Label.class::cast)
                        .toList();
                assertThat(sourceChars).anySatisfy(label -> {
                    assertThat(label.getText()).isEqualTo("i");
                    assertThat(label.getStyleClass()).contains("mc-text-code-type");
                });
                assertThat(sourceChars).anySatisfy(label -> {
                    assertThat(label.getText()).isEqualTo("m");
                    assertThat(label.getStyleClass()).contains("mc-text-code-function");
                });
                assertThat(sourceChars).anySatisfy(label -> {
                    assertThat(label.getText()).isEqualTo("7");
                    assertThat(label.getStyleClass()).contains("mc-text-code-literal");
                });
                assertThat(sourceChars).anySatisfy(label -> {
                    assertThat(label.getText()).isEqualTo("{");
                    assertThat(label.getStyleClass()).contains("mc-text-code-punctuation");
                });
                assertThat(sourceChars).anySatisfy(label -> {
                    assertThat(label.getText()).isEqualTo("r");
                    assertThat(label.getStyleClass()).contains("masked", "mc-text-code-control");
                });
            });
        } finally {
            if (stageRef.get() != null) {
                runFx(() -> stageRef.get().close());
            }
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

    private static Slider lookupSlider(MiniCSettingsPane pane, String accessibleText) {
        return pane.lookupAll(".slider").stream()
                .filter(Slider.class::isInstance)
                .map(Slider.class::cast)
                .filter(slider -> accessibleText.equals(slider.getAccessibleText()))
                .findFirst()
                .orElseThrow();
    }

    private static CheckBox lookupCheckBox(MiniCSettingsPane pane, String accessibleText) {
        return pane.lookupAll(".check-box").stream()
                .filter(CheckBox.class::isInstance)
                .map(CheckBox.class::cast)
                .filter(checkBox -> accessibleText.equals(checkBox.getAccessibleText()))
                .findFirst()
                .orElseThrow();
    }

    private static KeyEvent key(KeyCode code, boolean control, boolean alt, boolean shift) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, control, alt, false);
    }

    private static double staticDoubleField(Class<?> type, String name) throws Exception {
        java.lang.reflect.Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(null);
    }

    private static ScrollEvent scroll(double deltaY, boolean control, boolean alt, boolean shift) {
        return new ScrollEvent(
                ScrollEvent.SCROLL,
                0,
                0,
                0,
                0,
                shift,
                control,
                alt,
                false,
                false,
                false,
                0,
                deltaY,
                0,
                deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE,
                0,
                ScrollEvent.VerticalTextScrollUnits.NONE,
                0,
                0,
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
