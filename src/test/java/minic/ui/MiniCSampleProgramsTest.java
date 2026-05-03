package minic.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCSampleProgramsTest {
    @Test
    void providesDefaultEditableSourceSample() {
        assertThat(MiniCSamplePrograms.all()).isNotEmpty();
        assertThat(MiniCSamplePrograms.defaultSample().name()).endsWith(".mc");
        assertThat(MiniCSamplePrograms.defaultSample().source()).contains("int main()");
    }
}
