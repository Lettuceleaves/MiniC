package minic.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCBottomPanelModelFactoryTest {
    @Test
    void createsWaitingModelBeforeSessionStarts() {
        MiniCBottomPanelModel model = new MiniCBottomPanelModelFactory().create(null, null);

        assertThat(model.problems()).containsExactly("OK  暂无 diagnostics");
        assertThat(model.output()).containsExactly("等待开始观测会话");
        assertThat(model.terminal()).containsExactly("PS> minic observe <source.mc>");
    }

    @Test
    void createsOutputAndTerminalFromCurrentStage() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("bottom.mc", "int main() { return 0; }");
        viewModel.startSession();
        viewModel.next();

        MiniCBottomPanelModel model = new MiniCBottomPanelModelFactory().create(
                viewModel.currentStageDataProperty().get(),
                viewModel.globalDataProperty().get()
        );

        assertThat(model.problems()).containsExactly("OK  暂无 diagnostics");
        assertThat(model.output()).isNotEmpty();
        assertThat(model.terminal()).first().asString().contains("preprocess");
    }
}
