package minic.source;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceRangeTest {
    @Test
    void usesHalfOpenOffsets() {
        SourceFile sourceFile = new SourceFile("range.mc", "abcdef");
        SourceRange range = new SourceRange(sourceFile, 1, 4);

        assertThat(range.text()).isEqualTo("bcd");
        assertThat(range.startPosition()).isEqualTo(new SourcePosition(1, 1, 2));
        assertThat(range.endPosition()).isEqualTo(new SourcePosition(4, 1, 5));
    }

    @Test
    void rejectsInvalidRanges() {
        SourceFile sourceFile = new SourceFile("invalid-range.mc", "abc");

        assertThatThrownBy(() -> new SourceRange(sourceFile, -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceRange(sourceFile, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceRange(sourceFile, 1, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
