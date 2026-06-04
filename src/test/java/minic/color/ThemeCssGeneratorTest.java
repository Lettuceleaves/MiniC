package minic.color;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeCssGeneratorTest {
    @Test
    void debugSourceEditorScrollBarCssKeepsRightSideHandleVisible() {
        String css = ThemeCssGenerator.generate();

        assertThat(css).contains(".debug-source-editor-scroll .scroll-bar:vertical");
        assertThat(css).contains("-fx-pref-width: 12px");
        assertThat(css).contains(".debug-source-editor-scroll .scroll-bar:vertical .thumb");
    }

    @Test
    void compilerPipelineScrollBarCssMatchesDebuggerHandleStyle() {
        String css = ThemeCssGenerator.generate();

        assertThat(css).contains(".source-editor-scroll .scroll-bar:vertical");
        assertThat(css).contains(".stage-flow-scroll .scroll-bar:vertical");
        assertThat(css).contains(".visual-scroll .scroll-bar:vertical");
        assertThat(css).contains(".source-editor-scroll .scroll-bar:vertical .thumb");
        assertThat(css).contains(".stage-flow-scroll .scroll-bar:vertical .thumb");
        assertThat(css).contains(".visual-scroll .scroll-bar:vertical .thumb");
    }

    @Test
    void generatesReusableTextStyleCssForRoleStateAndFontAttributes() {
        String css = ThemeCssGenerator.generate();

        assertThat(css).contains(".mc-text-code-keyword");
        assertThat(css).contains(".mc-text-state-debug-execution");
        assertThat(css).contains("-fx-font-family: Consolas, \"Courier New\", monospace");
        assertThat(css).contains("-fx-font-weight: bold");
        assertThat(css).contains("-fx-font-style: italic");
    }
}
