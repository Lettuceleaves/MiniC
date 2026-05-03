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
        assertThat(viewModel.currentStateProperty().get().globalStepIndex()).isEqualTo(before + 1);
        assertThat(viewModel.currentStageDataProperty().get().accumulatedOutput()).isNotEmpty();
        assertThat(viewModel.globalDataProperty().get().tokenSummary()).isNotEmpty();
    }
}
