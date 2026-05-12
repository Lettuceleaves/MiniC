package minic.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCVisualModelFactoryTest {
    @Test
    void createsPendingVisualItemBeforeSessionStarts() {
        List<MiniCVisualItem> items = new MiniCVisualModelFactory().create(null, null);

        assertThat(items).containsExactly(new MiniCVisualItem("等待开始观测会话", true));
    }

    @Test
    void usesCurrentItemAsHotVisualItem() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("visual.mc", "int main() { return 0; }");
        viewModel.startSession();
        viewModel.next();
        viewModel.next();

        List<MiniCVisualItem> items = new MiniCVisualModelFactory().create(
                viewModel.currentStageDataProperty().get(),
                viewModel.globalDataProperty().get()
        );

        assertThat(items).isNotEmpty();
        assertThat(items.getFirst().hot()).isTrue();
        assertThat(items).anySatisfy(item -> assertThat(item.label()).contains("out int main()"));
    }
}
