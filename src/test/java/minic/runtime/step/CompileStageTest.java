package minic.runtime.step;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompileStageTest {
    @Test
    void definesStableCompileStageIds() {
        assertThat(CompileStage.values())
                .extracting(CompileStage::id)
                .containsExactly("source", "lexer", "parser", "semantic", "ir", "codegen", "toolchain", "execution");
        assertThat(CompileStage.fromId("LEXER")).isEqualTo(CompileStage.LEXER);
    }

    @Test
    void rejectsUnknownStageIds() {
        assertThatThrownBy(() -> CompileStage.fromId("debugger"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompileStage.fromId(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
