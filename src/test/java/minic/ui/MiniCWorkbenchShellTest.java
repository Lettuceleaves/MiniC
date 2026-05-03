package minic.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCWorkbenchShellTest {
    @Test
    void keepsShellAsUiLayerType() {
        assertThat(MiniCWorkbenchShell.class.getPackageName()).isEqualTo("minic.ui");
        assertThat(MiniCWorkbenchApp.class.getResource("/minic/ui/workbench.css")).isNotNull();
    }
}
