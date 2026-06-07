package minic.uilocal;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;
import minic.color.ThemeCssGenerator;
import minic.compiler.lexer.TokenKind;
import minic.settings.MiniCSettings;
import minic.uilocal.control.MiniCActiveTrackingService;
import minic.uilocal.control.MiniCControlTargetType;
import minic.uilocal.control.MiniCViewportAdapter;
import minic.uilocal.control.MiniCViewportRegistry;
import minic.uilocal.text.MiniCAssemblyTextHighlighter;
import minic.uilocal.text.MiniCExplanationTextHighlighter;
import minic.uilocal.text.MiniCIrTextHighlighter;
import minic.uilocal.text.MiniCStyledTextSegment;
import minic.uilocal.text.MiniCSyntaxTextStyleMapper;
import minic.uilocal.text.MiniCTextStyleRole;
import minic.uilocal.text.MiniCTextStyleState;
import minic.uilocal.text.MiniCTextStyles;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCEditorViewportTextRegressionTest {
    private static final Path SETTINGS_FILE = Path.of("config", "settings.json");

    @Test
    void handlesSourceLoaderBreakpointsEditorDiagnosticsTypingAndRealtimeAnalysis() {
        assertThat(MiniCEditorTyping.type("", 0, 0, "{").source()).isEqualTo("{}");
        assertThat(MiniCEditorTyping.backspace("{}", 1, 1).source()).isEmpty();
    }

    @Test
    void textViewportZoomScalesDisplayAndPersistsWithoutChangingFontSize() throws Exception {
        ensureFxStarted();
        String originalSettings = backup(SETTINGS_FILE);
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicReference<MiniCCodeEditor> editorRef = new AtomicReference<>();
        AtomicReference<Node> sourceNodeRef = new AtomicReference<>();
        try {
            Files.writeString(SETTINGS_FILE, """
                    {
                      "theme": "dark",
                      "editorDisplayScale": 1.0
                    }
                    """, StandardCharsets.UTF_8);
            MiniCSettings.load();

            runFx(() -> {
                MiniCCodeEditor editor = new MiniCCodeEditor();
                editor.setText("int main() {\\n    return 0;\\n}\\n");
                Stage stage = new Stage();
                stage.setScene(new Scene(editor, 640, 360));
                stage.show();
                editor.applyCss();
                editor.layout();
                Node sourceNode = editor.lookup(".source-editor");

                stageRef.set(stage);
                editorRef.set(editor);
                sourceNodeRef.set(sourceNode);
            });

            runFx(() -> {
                MiniCCodeEditor editor = editorRef.get();
                Node sourceNode = sourceNodeRef.get();
                assertThat(sourceNode).isNotNull();

                double fontSize = editor.editorFontSizeForTesting();
                double scaleY = sourceNode.getScaleY();
                editor.viewportAdapter().zoomAt(Point2D.ZERO, 1.0);

                assertThat(editor.editorFontSizeForTesting()).isEqualTo(fontSize);
                assertThat(sourceNode.getScaleY()).isGreaterThan(scaleY);
            });

            assertThat(Files.readString(SETTINGS_FILE, StandardCharsets.UTF_8))
                    .contains("\"editorDisplayScale\": 1.0833333333333333");

            MiniCSettings.load();
            runFx(() -> assertThat(new MiniCCodeEditor().editorDisplayScaleForTesting())
                    .isEqualTo(1.0833333333333333));
        } finally {
            if (stageRef.get() != null) {
                runFx(() -> stageRef.get().close());
            }
            restore(SETTINGS_FILE, originalSettings);
            MiniCSettings.load();
        }
    }

    @Test
    void controlsTextGraphAndScrollPaneViewportsWithStableActiveTracking() {
        MiniCViewportRegistry registry = new MiniCViewportRegistry();
        MiniCViewportAdapter adapter = () -> MiniCControlTargetType.TEXT;

        registry.hover(adapter);

        assertThat(registry.currentTarget()).contains(adapter);
        assertThat(adapter.canZoom()).isFalse();
        new MiniCActiveTrackingService(() -> registry.currentTarget().stream().toList()).trackActiveViewports();
        assertThat(registry.currentTarget()).contains(adapter);
    }

    @Test
    void resolvesReusableTextStylesSyntaxDiagnosticsThemeCssIrAndAssemblyHighlighting() {
        MiniCSyntaxTextStyleMapper mapper = new MiniCSyntaxTextStyleMapper();

        assertThat(mapper.roleFor(TokenKind.INT.name())).isEqualTo(MiniCTextStyleRole.CODE_KEYWORD);
        assertThat(mapper.styleClassesFor(TokenKind.IDENTIFIER.name(), true))
                .contains("mc-text-code-identifier", "mc-text-state-diagnostic");
        assertThat(MiniCTextStyles.defaultResolver().styleClasses(MiniCTextStyleRole.CODE_STRING,
                java.util.List.of(MiniCTextStyleState.ACTIVE))).contains("mc-text-code-string", "mc-text-state-active");
        assertThat(ThemeCssGenerator.generate()).contains(".mc-text-code-keyword", "-fx-font-weight");
    }

    @Test
    void rendersStyledIrAndAssemblyRowsInVisualPaneAndDebugPane() {
        assertThat(new MiniCIrTextHighlighter().highlight("  %1 = add %2, 3"))
                .extracting(MiniCStyledTextSegment::role)
                .contains(MiniCTextStyleRole.CODE_KEYWORD, MiniCTextStyleRole.CODE_IDENTIFIER, MiniCTextStyleRole.CODE_LITERAL);
        assertThat(new MiniCAssemblyTextHighlighter().highlight("main: mov rax, 1 ; comment"))
                .extracting(MiniCStyledTextSegment::role)
                .contains(MiniCTextStyleRole.CODE_TYPE, MiniCTextStyleRole.CODE_KEYWORD,
                        MiniCTextStyleRole.CODE_IDENTIFIER, MiniCTextStyleRole.CODE_COMMENT);
        assertThat(new MiniCExplanationTextHighlighter().highlight("说明: return %1 == 3，并写入 rax。"))
                .extracting(MiniCStyledTextSegment::role)
                .contains(MiniCTextStyleRole.BODY, MiniCTextStyleRole.CODE_KEYWORD,
                        MiniCTextStyleRole.CODE_IDENTIFIER, MiniCTextStyleRole.CODE_LITERAL,
                        MiniCTextStyleRole.CODE_OPERATOR);
        assertThat(new MiniCExplanationTextHighlighter().highlight("plain words 只是说明"))
                .extracting(MiniCStyledTextSegment::role)
                .containsOnly(MiniCTextStyleRole.BODY);
        assertThat(new MiniCExplanationTextHighlighter().highlight("读取 values[0] 后跳转到 .L1"))
                .extracting(MiniCStyledTextSegment::role)
                .contains(MiniCTextStyleRole.CODE_IDENTIFIER, MiniCTextStyleRole.CODE_LITERAL,
                        MiniCTextStyleRole.CODE_OPERATOR, MiniCTextStyleRole.CODE_TYPE);
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
