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
}
