package minic.ui;

import javafx.application.Application;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCWorkbenchAppTest {
    @Test
    void definesJavaFxApplicationEntryPoint() {
        assertThat(Application.class).isAssignableFrom(MiniCWorkbenchApp.class);
        assertThat(MiniCWorkbenchApp.TITLE).isEqualTo("MiniC Visual Workbench");
        assertThat(MiniCWorkbenchApp.DEFAULT_WIDTH).isGreaterThanOrEqualTo(960);
        assertThat(MiniCWorkbenchApp.DEFAULT_HEIGHT).isGreaterThanOrEqualTo(600);
    }
}
