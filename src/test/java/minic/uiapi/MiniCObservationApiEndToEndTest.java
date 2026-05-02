package minic.uiapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCObservationApiEndToEndTest {
    @Test
    void runsUiFacadeWorkflowFromLoadedSourceThroughForwardPlaybackControls() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource(
                "ui-e2e.mc",
                """
                        extern int puts(int *text);
                        int main() {
                            return puts("ok");
                        }
                        """
        );
        api.startSession();

        assertThat(api.currentState().currentStage()).isEqualTo("lexer");
        assertThat(api.currentStageData().accumulatedOutput()).isEmpty();

        UiControlResultDto first = api.next();
        UiStageDataDto afterFirst = api.currentStageData();

        assertThat(first.outcome()).isEqualTo("ADVANCED");
        assertThat(afterFirst.stage()).isEqualTo("lexer");
        assertThat(afterFirst.accumulatedOutput()).contains("EXTERN extern");
        assertThat(api.currentState().globalStepIndex()).isEqualTo(1);

        api.play();
        UiControlResultDto playTick = api.tick();

        assertThat(playTick.outcome()).isEqualTo("ADVANCED");
        assertThat(api.currentState().playbackMode()).isEqualTo("PLAYING");
        assertThat(api.currentState().frameIntervalMillis()).isEqualTo(1000);
        assertThat(api.currentState().globalStepIndex()).isEqualTo(2);

        api.playFast();
        UiControlResultDto fastTick = api.tick();

        assertThat(fastTick.outcome()).isEqualTo("ADVANCED");
        assertThat(api.currentState().playbackMode()).isEqualTo("FAST_PLAYING");
        assertThat(api.currentState().frameIntervalMillis()).isEqualTo(500);
        assertThat(api.currentState().globalStepIndex()).isEqualTo(3);

        assertThat(api.pause().outcome()).isEqualTo("ADVANCED");
        assertThat(api.currentState().playbackMode()).isEqualTo("PAUSED");

        assertThat(api.previous().outcome()).isEqualTo("UNSUPPORTED");
        assertThat(api.reversePlay().outcome()).isEqualTo("UNSUPPORTED");

        int guard = 0;
        while (api.currentState().canNext() && guard++ < 1000) {
            api.next();
        }

        assertThat(api.currentState().currentStage()).isEqualTo("codegen");
        assertThat(api.currentState().canNext()).isFalse();
        assertThat(api.globalData().tokenSummary()).isNotEmpty();
        assertThat(api.globalData().astSummary()).isNotEmpty();
        assertThat(api.globalData().semanticSummary()).isNotEmpty();
        assertThat(api.globalData().irSummary()).isNotEmpty();
        assertThat(api.globalData().assemblySummary()).contains("END");
    }
}
