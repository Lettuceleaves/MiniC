package minic.ui.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCSyntaxTextStyleMapperTest {
    @Test
    void mapsTokenKindsToReusableCodeTextRoles() {
        MiniCSyntaxTextStyleMapper mapper = new MiniCSyntaxTextStyleMapper();

        assertThat(mapper.roleFor("INT")).isEqualTo(MiniCTextStyleRole.CODE_KEYWORD);
        assertThat(mapper.roleFor("STRING_LITERAL")).isEqualTo(MiniCTextStyleRole.CODE_STRING);
        assertThat(mapper.roleFor("INTEGER_LITERAL")).isEqualTo(MiniCTextStyleRole.CODE_LITERAL);
        assertThat(mapper.roleFor("IDENTIFIER")).isEqualTo(MiniCTextStyleRole.CODE_IDENTIFIER);
        assertThat(mapper.roleFor("PLUS")).isEqualTo(MiniCTextStyleRole.CODE_OPERATOR);
    }

    @Test
    void emitsDiagnosticStateWhenTokenOverlapsDiagnostic() {
        MiniCSyntaxTextStyleMapper mapper = new MiniCSyntaxTextStyleMapper();

        assertThat(mapper.statesFor(true)).containsExactly(MiniCTextStyleState.DIAGNOSTIC);
        assertThat(mapper.statesFor(false)).isEmpty();
    }
}
