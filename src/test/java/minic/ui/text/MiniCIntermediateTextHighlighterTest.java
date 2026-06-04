package minic.ui.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCIntermediateTextHighlighterTest {
    @Test
    void highlightsIrOperationsValuesAndLiterals() {
        MiniCIrTextHighlighter highlighter = new MiniCIrTextHighlighter();

        assertThat(highlighter.highlight("%1 = call printf(format, 42)"))
                .anySatisfy(segment -> assertThat(segment)
                        .extracting(MiniCStyledTextSegment::text, MiniCStyledTextSegment::role)
                        .containsExactly("%1", MiniCTextStyleRole.CODE_IDENTIFIER))
                .anySatisfy(segment -> assertThat(segment)
                        .extracting(MiniCStyledTextSegment::text, MiniCStyledTextSegment::role)
                        .containsExactly("call", MiniCTextStyleRole.CODE_KEYWORD))
                .anySatisfy(segment -> assertThat(segment)
                        .extracting(MiniCStyledTextSegment::text, MiniCStyledTextSegment::role)
                        .containsExactly("42", MiniCTextStyleRole.CODE_LITERAL));
    }

    @Test
    void highlightsAssemblyMnemonicsRegistersLabelsAndComments() {
        MiniCAssemblyTextHighlighter highlighter = new MiniCAssemblyTextHighlighter();

        assertThat(highlighter.highlight("mov rcx, OFFSET FLAT:$str0 ; format string"))
                .anySatisfy(segment -> assertThat(segment)
                        .extracting(MiniCStyledTextSegment::text, MiniCStyledTextSegment::role)
                        .containsExactly("mov", MiniCTextStyleRole.CODE_KEYWORD))
                .anySatisfy(segment -> assertThat(segment)
                        .extracting(MiniCStyledTextSegment::text, MiniCStyledTextSegment::role)
                        .containsExactly("rcx", MiniCTextStyleRole.CODE_IDENTIFIER))
                .anySatisfy(segment -> assertThat(segment)
                        .extracting(MiniCStyledTextSegment::text, MiniCStyledTextSegment::role)
                        .containsExactly("$str0", MiniCTextStyleRole.CODE_TYPE))
                .anySatisfy(segment -> assertThat(segment)
                        .extracting(MiniCStyledTextSegment::text, MiniCStyledTextSegment::role)
                        .containsExactly("; format string", MiniCTextStyleRole.CODE_COMMENT));
    }
}
