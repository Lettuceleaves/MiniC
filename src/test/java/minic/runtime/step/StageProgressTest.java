package minic.runtime.step;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageProgressTest {
    @Test
    void representsKnownUnknownAndCompletedProgress() {
        StageProgress known = new StageProgress(2, 5, false);
        StageProgress unknown = StageProgress.unknownTotal(3);
        StageProgress completed = StageProgress.completed(4);

        assertThat(known.hasKnownTotal()).isTrue();
        assertThat(unknown.hasKnownTotal()).isFalse();
        assertThat(completed.completedSteps()).isEqualTo(4);
        assertThat(completed.totalSteps()).isEqualTo(4);
        assertThat(completed.completed()).isTrue();
    }

    @Test
    void rejectsInvalidProgressValues() {
        assertThatThrownBy(() -> new StageProgress(-1, 1, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StageProgress(0, -2, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StageProgress(2, 1, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
