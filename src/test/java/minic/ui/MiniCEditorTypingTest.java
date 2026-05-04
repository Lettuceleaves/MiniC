package minic.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCEditorTypingTest {
    @Test
    void typingOpeningBraceProducesOnlyBracePair() {
        MiniCEditorTyping.EditResult result = MiniCEditorTyping.type("", 0, 0, "{");

        assertThat(result.source()).isEqualTo("{}");
        assertThat(result.selectionStart()).isEqualTo(1);
        assertThat(result.selectionEnd()).isEqualTo(1);
    }

    @Test
    void typingOpeningBraceWrapsSelection() {
        MiniCEditorTyping.EditResult result = MiniCEditorTyping.type("abc", 0, 3, "{");

        assertThat(result.source()).isEqualTo("{abc}");
        assertThat(result.selectionStart()).isEqualTo(4);
    }

    @Test
    void backspaceBetweenEmptyBracePairDeletesBothBraces() {
        MiniCEditorTyping.EditResult result = MiniCEditorTyping.backspace("{}", 1, 1);

        assertThat(result.source()).isEmpty();
        assertThat(result.selectionStart()).isZero();
        assertThat(result.selectionEnd()).isZero();
    }

    @Test
    void backspaceBetweenNonEmptyBracesDeletesOnlyPreviousCharacter() {
        MiniCEditorTyping.EditResult result = MiniCEditorTyping.backspace("{x}", 2, 2);

        assertThat(result.source()).isEqualTo("{}");
        assertThat(result.selectionStart()).isEqualTo(1);
    }
}
