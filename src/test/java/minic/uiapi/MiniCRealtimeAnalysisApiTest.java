package minic.uiapi;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCRealtimeAnalysisApiTest {
    private static final String VALID_SOURCE = "int main() { return 0; }";

    @Test
    void checksForInvalidationAfterEveryCompilerStage() {
        AtomicInteger invalidationChecks = new AtomicInteger();

        var result = new MiniCRealtimeAnalysisApi().analyzeInterruptibly(
                "test.mc",
                VALID_SOURCE,
                1,
                () -> {
                    invalidationChecks.incrementAndGet();
                    return false;
                }
        );

        assertThat(result).isPresent();
        assertThat(invalidationChecks).hasValue(6);
    }

    @Test
    void abandonsAnalysisWhenSourceChangesBetweenStages() {
        AtomicInteger invalidationChecks = new AtomicInteger();

        var result = new MiniCRealtimeAnalysisApi().analyzeInterruptibly(
                "test.mc",
                VALID_SOURCE,
                1,
                () -> invalidationChecks.incrementAndGet() >= 3
        );

        assertThat(result).isEmpty();
        assertThat(invalidationChecks).hasValue(3);
    }
}
