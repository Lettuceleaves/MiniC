package minic.ui;

import minic.color.ThemeCssGenerator;
import minic.compiler.lexer.TokenKind;
import minic.ui.control.MiniCActiveTrackingService;
import minic.ui.control.MiniCControlTargetType;
import minic.ui.control.MiniCViewportAdapter;
import minic.ui.control.MiniCViewportRegistry;
import minic.ui.text.MiniCAssemblyTextHighlighter;
import minic.ui.text.MiniCIrTextHighlighter;
import minic.ui.text.MiniCStyledTextSegment;
import minic.ui.text.MiniCSyntaxTextStyleMapper;
import minic.ui.text.MiniCTextStyleRole;
import minic.ui.text.MiniCTextStyleState;
import minic.ui.text.MiniCTextStyles;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCEditorViewportTextRegressionTest {
    @Test
    void handlesSourceLoaderBreakpointsEditorDiagnosticsTypingAndRealtimeAnalysis() {
        assertThat(MiniCEditorTyping.type("", 0, 0, "{").source()).isEqualTo("{}");
        assertThat(MiniCEditorTyping.backspace("{}", 1, 1).source()).isEmpty();
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
    }
}
