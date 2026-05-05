package minic.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCStageListFactoryTest {
    @Test
    void createsInitialQueuedPipelineStages() {
        List<MiniCStageView> stages = new MiniCStageListFactory().create(null, null, null);

        assertThat(stages)
                .extracting(MiniCStageView::id)
                .containsExactly("source", "preprocess", "lexer", "parser", "semantic", "ir", "codegen", "toolchain", "execution");
        assertThat(stages).allSatisfy(stage -> assertThat(stage.progressPercent()).isBetween(0, 100));
        assertThat(stages.get(1).state()).isEqualTo("done");
    }

    @Test
    void bindsCurrentStageProgressFromViewModelDtos() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("stage.mc", "int main() { return 0; }");
        viewModel.startSession();
        viewModel.next();

        List<MiniCStageView> stages = new MiniCStageListFactory().create(
                viewModel.currentStateProperty().get(),
                viewModel.currentStageDataProperty().get(),
                viewModel.globalDataProperty().get()
        );

        MiniCStageView preprocess = stages.stream()
                .filter(stage -> stage.id().equals("preprocess"))
                .findFirst()
                .orElseThrow();
        assertThat(preprocess.state()).isEqualTo("running");
        assertThat(preprocess.detail()).contains("当前阶段");
        assertThat(preprocess.progressPercent()).isGreaterThanOrEqualTo(0);
    }
}
