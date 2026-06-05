package minic.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCWorkbenchRegressionTest {
    @Test
    void startsShellControllerViewModelDocumentsAndPipelineModes() {
        MiniCWorkbenchViewModel model = new MiniCWorkbenchViewModel();
        model.loadSource("workbench.mc", "int main() { return 0; }");
        model.startSession();

        assertThat(model.sourceNameProperty().get()).isEqualTo("workbench.mc");
        assertThat(model.sessionStartedProperty().get()).isTrue();
        assertThat(model.currentStateProperty().get().currentStage()).isEqualTo("source");
        assertThat(model.currentStageDataProperty().get().accumulatedOutput()).isNotEmpty();
    }

    @Test
    void drivesWorkbenchControlsPlaybackExecutionInputAndSelectedVisualStages() {
        MiniCWorkbenchViewModel model = new MiniCWorkbenchViewModel();
        model.loadSource("controls.mc", "int main() { return 0; }");
        model.startSession();

        assertThat(model.next().outcome()).isIn("ADVANCED", "STAGE_COMPLETED");
        assertThat(model.nextStage().outcome()).isEqualTo("ADVANCED");
        assertThat(model.play().outcome()).isEqualTo("ADVANCED");
        assertThat(model.tick().outcome()).isIn("ADVANCED", "STAGE_COMPLETED");
        assertThat(model.pause().outcome()).isEqualTo("ADVANCED");
        assertThat(model.runToExecution().outcome()).isIn("ADVANCED", "STAGE_COMPLETED", "CANNOT_ADVANCE");
    }

    @Test
    void routesCommandsActiveTrackingAndViewportOperationsThroughControlHub() {
        MiniCWorkbenchViewModel model = new MiniCWorkbenchViewModel();
        model.loadSource("viewport.mc", "int main() { return 0; }");
        model.startSession();

        model.saveViewportState("source", 0.25, 0.75);

        assertThat(model.viewportState("source").hvalue()).isEqualTo(0.25);
        assertThat(model.viewportState("source").vvalue()).isEqualTo(0.75);
        assertThat(model.canNextControl()).isTrue();
        assertThat(model.canPlayControl()).isTrue();
        assertThat(model.canPlayFastControl()).isTrue();
    }

    @Test
    void rendersDebugPaneSourceIrAsmAstMetadataAndDataStructures() {
        MiniCWorkbenchViewModel model = new MiniCWorkbenchViewModel();
        model.loadSource("debug-ui.mc", """
                int main() {
                    int x = 1;
                    return x;
                }
                """);

        model.startDebug();

        assertThat(model.debugStartedProperty().get()).isTrue();
        assertThat(model.debugStateProperty().get()).isNotNull();
        assertThat(model.debugMetadataViewProperty().get().timeline()).isNotEmpty();
        assertThat(model.debugAstViewProperty().get().root()).isNotNull();
        assertThat(model.debugIrViewProperty().get().lines()).isNotEmpty();
        assertThat(model.debugAsmViewProperty().get().lines()).isNotEmpty();
        assertThat(model.debugDataStructureViewProperty().get().processSpace()).isNotNull();
    }
}
