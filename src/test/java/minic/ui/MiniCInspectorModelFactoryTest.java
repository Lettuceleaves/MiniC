package minic.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCInspectorModelFactoryTest {
    @Test
    void createsPendingInspectorModel() {
        MiniCInspectorModel model = new MiniCInspectorModelFactory().create(null, null, null);

        assertThat(model.currentState()).contains("阶段: 等待中");
        assertThat(model.currentItem()).contains("等待开始观测会话");
        assertThat(model.accumulatedOutput()).contains("token: 0");
    }

    @Test
    void createsInspectorModelFromDtos() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("inspector.mc", "int main() { return 0; }");
        viewModel.startSession();
        viewModel.next();
        viewModel.next();

        MiniCInspectorModel model = new MiniCInspectorModelFactory().create(
                viewModel.currentStateProperty().get(),
                viewModel.currentStageDataProperty().get(),
                viewModel.globalDataProperty().get()
        );

        assertThat(model.currentState()).contains("阶段: 预编译", "全局步: 1");
        assertThat(model.currentItem()).isNotBlank();
        assertThat(model.accumulatedOutput()).contains("token:");
    }
}
