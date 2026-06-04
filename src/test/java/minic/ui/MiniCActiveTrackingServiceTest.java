package minic.ui;

import minic.ui.control.MiniCActiveTrackingService;
import minic.ui.control.MiniCControlTargetType;
import minic.ui.control.MiniCViewportAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCActiveTrackingServiceTest {
    @Test
    void centersOnlyAdaptersWhoseActiveItemIsNotFullyVisible() {
        FakeAdapter visible = new FakeAdapter(true);
        FakeAdapter hidden = new FakeAdapter(false);
        MiniCActiveTrackingService service = new MiniCActiveTrackingService(() -> List.of(visible, hidden));

        service.trackActiveViewports();

        assertThat(visible.centerCalls()).isZero();
        assertThat(hidden.centerCalls()).isOne();
    }

    @Test
    void ignoresMissingAdaptersSafely() {
        MiniCActiveTrackingService service = new MiniCActiveTrackingService(() -> null);

        service.trackActiveViewports();
    }

    private static final class FakeAdapter implements MiniCViewportAdapter {
        private final boolean activeFullyVisible;
        private int centerCalls;

        private FakeAdapter(boolean activeFullyVisible) {
            this.activeFullyVisible = activeFullyVisible;
        }

        @Override
        public MiniCControlTargetType type() {
            return MiniCControlTargetType.TEXT;
        }

        @Override
        public boolean isActiveFullyVisible() {
            return activeFullyVisible;
        }

        @Override
        public void centerActive() {
            centerCalls++;
        }

        private int centerCalls() {
            return centerCalls;
        }
    }
}
