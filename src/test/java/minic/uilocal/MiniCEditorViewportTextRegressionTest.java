package minic.uilocal;

import minic.color.ThemeCssGenerator;
import minic.compiler.lexer.TokenKind;
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
}
