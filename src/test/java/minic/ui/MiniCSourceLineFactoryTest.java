package minic.ui;

import minic.uiapi.UiSourceRangeDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCSourceLineFactoryTest {
    @Test
    void splitsSourceIntoNumberedLines() {
        List<MiniCSourceLine> lines = new MiniCSourceLineFactory().create("int main() {\n    return 0;\n}", null);

        assertThat(lines)
                .extracting(MiniCSourceLine::lineNumber)
                .containsExactly(1, 2, 3);
        assertThat(lines.get(1).text()).contains("return");
        assertThat(lines).noneMatch(MiniCSourceLine::focused);
    }

    @Test
    void marksLineIntersectingCurrentSourceRange() {
        String source = "int main() {\n    return 0;\n}";
        int start = source.indexOf("return");
        UiSourceRangeDto range = new UiSourceRangeDto("main.mc", start, start + "return".length());

        List<MiniCSourceLine> lines = new MiniCSourceLineFactory().create(source, range);

        assertThat(lines.get(0).focused()).isFalse();
        assertThat(lines.get(1).focused()).isTrue();
        assertThat(lines.get(2).focused()).isFalse();
    }
}
