package minic.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCInspectorModelFactoryTest {
    @Test
    void createsPendingInspectorModel() {
        MiniCInspectorModel model = new MiniCInspectorModelFactory().create(null, null, null);

        assertThat(model.currentState()).contains("stage: pending");
        assertThat(model.currentItem()).contains("等待开始观测会话");
        assertThat(model.accumulatedOutput()).contains("tokens: 0");
    }

    @Test
    void createsInspectorModelFromDtos() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("inspector.mc", "int main() { return 0; }");
        viewModel.startSession();
        viewModel.next();

        MiniCInspectorModel model = new MiniCInspectorModelFactory().create(
                viewModel.currentStateProperty().get(),
                viewModel.currentStageDataProperty().get(),
                viewModel.globalDataProperty().get()
        );

        assertThat(model.currentState()).contains("stage: lexer", "globalStep: 1");
        assertThat(model.currentItem()).isNotBlank();
        assertThat(model.accumulatedOutput()).contains("tokens:");
    }
}
