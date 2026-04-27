package minic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {
    @Test
    void returnsProjectName() {
        assertThat(Main.name()).isEqualTo("MiniC");
    }
}
