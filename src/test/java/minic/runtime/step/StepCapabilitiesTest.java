package minic.runtime.step;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StepCapabilitiesTest {
    @Test
    void forwardOnlyEnablesCurrentForwardControlsAndReservesReverseControls() {
        StepCapabilities capabilities = StepCapabilities.forwardOnly();

        assertThat(capabilities.canNext()).isTrue();
        assertThat(capabilities.canPlay()).isTrue();
        assertThat(capabilities.canPlayFast()).isTrue();
        assertThat(capabilities.canPause()).isTrue();
        assertThat(capabilities.canPrevious()).isFalse();
        assertThat(capabilities.canReversePlay()).isFalse();
    }

    @Test
    void exposesUnsupportedResultsForReservedReverseControls() {
        StepCapabilities capabilities = StepCapabilities.forwardOnly();

        assertThat(capabilities.previousUnsupported(CompileStage.PARSER).outcome())
                .isEqualTo(StepOutcome.UNSUPPORTED);
        assertThat(capabilities.reversePlayUnsupported(CompileStage.PARSER).outcome())
                .isEqualTo(StepOutcome.UNSUPPORTED);
        assertThat(StepCapabilities.none().canNext()).isFalse();
    }
}
