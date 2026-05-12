package minic.ui;

import minic.uiapi.UiControlResultDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCWorkbenchControllerTest {
    @Test
    void nextRefreshesWorkbenchDtoState() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        MiniCWorkbenchController controller = new MiniCWorkbenchController(viewModel);
        controller.startDefaultSession();

        long before = viewModel.currentStateProperty().get().globalStepIndex();
        UiControlResultDto result = controller.next();

        assertThat(result.outcome()).isEqualTo("ADVANCED");
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("preprocess");
        assertThat(viewModel.currentStateProperty().get().globalStepIndex()).isEqualTo(before);
        assertThat(viewModel.currentStageDataProperty().get().accumulatedOutput()).isEmpty();
        assertThat(viewModel.globalDataProperty().get().preprocessSummary()).isEmpty();
    }

    @Test
    void nextStageJumpsToFollowingStage() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        MiniCWorkbenchController controller = new MiniCWorkbenchController(viewModel);
        controller.startDefaultSession();

        UiControlResultDto result = controller.nextStage();

        assertThat(result.outcome()).isEqualTo("ADVANCED");
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("preprocess");
    }
}
