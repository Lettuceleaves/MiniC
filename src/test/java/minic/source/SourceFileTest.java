package minic.source;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceFileTest {
    @Test
    void mapsSingleLineOffsetsToOneBasedPositions() {
        SourceFile sourceFile = new SourceFile("single.mc", "abc");

        assertThat(sourceFile.positionAt(0)).isEqualTo(new SourcePosition(0, 1, 1));
        assertThat(sourceFile.positionAt(1)).isEqualTo(new SourcePosition(1, 1, 2));
        assertThat(sourceFile.positionAt(3)).isEqualTo(new SourcePosition(3, 1, 4));
    }

    @Test
    void mapsMultiLineOffsetsToOneBasedPositions() {
        SourceFile sourceFile = new SourceFile("multi.mc", "ab\ncde\n");

        assertThat(sourceFile.positionAt(0)).isEqualTo(new SourcePosition(0, 1, 1));
        assertThat(sourceFile.positionAt(2)).isEqualTo(new SourcePosition(2, 1, 3));
        assertThat(sourceFile.positionAt(3)).isEqualTo(new SourcePosition(3, 2, 1));
        assertThat(sourceFile.positionAt(6)).isEqualTo(new SourcePosition(6, 2, 4));
        assertThat(sourceFile.positionAt(7)).isEqualTo(new SourcePosition(7, 3, 1));
    }

    @Test
    void rejectsOffsetsOutsideContent() {
        SourceFile sourceFile = new SourceFile("invalid.mc", "abc");

        assertThatThrownBy(() -> sourceFile.positionAt(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sourceFile.positionAt(4))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
