package minic.ui;

import javafx.geometry.Point2D;
import minic.ui.control.MiniCControlTargetType;
import minic.ui.control.MiniCViewportAdapter;
import minic.ui.control.MiniCViewportRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MiniCViewportRegistryTest {
    @Test
    void resolvesTargetsByHoverPinnedAndBusinessPriority() {
        MiniCViewportRegistry registry = new MiniCViewportRegistry();
        FakeAdapter business = new FakeAdapter(MiniCControlTargetType.STAGE);
        FakeAdapter text = new FakeAdapter(MiniCControlTargetType.TEXT);
        FakeAdapter graph = new FakeAdapter(MiniCControlTargetType.GRAPH);

        assertThat(registry.currentTarget()).isEmpty();

        registry.businessActive(business);
        assertThat(registry.currentTarget()).containsSame(business);

        registry.pin(text);
        assertThat(registry.currentTarget()).containsSame(text);

        registry.hover(graph);
        assertThat(registry.currentTarget()).containsSame(graph);

        registry.clearHover(graph);
        assertThat(registry.currentTarget()).containsSame(text);

        registry.clearPinned(text);
        assertThat(registry.currentTarget()).containsSame(business);

        registry.clearBusinessActive(business);
        assertThat(registry.currentTarget()).isEmpty();
    }

    @Test
    void clearsOnlyMatchingHoverAndPinnedTargets() {
        MiniCViewportRegistry registry = new MiniCViewportRegistry();
        FakeAdapter text = new FakeAdapter(MiniCControlTargetType.TEXT);
        FakeAdapter graph = new FakeAdapter(MiniCControlTargetType.GRAPH);

        registry.pin(text);
        registry.hover(graph);

        registry.clearHover(text);
        assertThat(registry.currentTarget()).containsSame(graph);

        registry.clearPinned(graph);
        registry.clearHover(graph);
        assertThat(registry.currentTarget()).containsSame(text);
    }

    @Test
    void noneTargetsDoNotReplacePinnedViewport() {
        MiniCViewportRegistry registry = new MiniCViewportRegistry();
        FakeAdapter text = new FakeAdapter(MiniCControlTargetType.TEXT);

        registry.pin(text);
        registry.hover(MiniCViewportAdapter.noop());
        registry.pin(MiniCViewportAdapter.noop());
        registry.businessActive(MiniCViewportAdapter.noop());

        assertThat(registry.currentTarget()).containsSame(text);
    }

    @Test
    void noopAdapterSafelyIgnoresUnsupportedOperations() {
        MiniCViewportAdapter adapter = MiniCViewportAdapter.noop();

        assertThat(adapter.type()).isEqualTo(MiniCControlTargetType.NONE);
        assertThat(adapter.canZoom()).isFalse();
        assertThat(adapter.canScrollVertical()).isFalse();
        assertThat(adapter.canScrollHorizontal()).isFalse();
        assertThat(adapter.canPan()).isFalse();
        assertThat(adapter.isActiveFullyVisible()).isTrue();
        assertThatCode(() -> {
            adapter.zoomAt(Point2D.ZERO, 1.0);
            adapter.scrollVertical(10.0);
            adapter.scrollHorizontal(-5.0);
            adapter.pan(2.0, 3.0);
            adapter.centerActiveIfNeeded();
            adapter.centerActive();
        }).doesNotThrowAnyException();
    }

    @Test
    void centerActiveIfNeededOnlyCentersWhenActiveIsNotFullyVisible() {
        FakeAdapter visible = new FakeAdapter(MiniCControlTargetType.TEXT, true);
        FakeAdapter hidden = new FakeAdapter(MiniCControlTargetType.GRAPH, false);

        visible.centerActiveIfNeeded();
        hidden.centerActiveIfNeeded();

        assertThat(visible.centerCalls()).isZero();
        assertThat(hidden.centerCalls()).isOne();
    }

    private static final class FakeAdapter implements MiniCViewportAdapter {
        private final MiniCControlTargetType type;
        private final boolean activeFullyVisible;
        private int centerCalls;

        private FakeAdapter(MiniCControlTargetType type) {
            this(type, true);
        }

        private FakeAdapter(MiniCControlTargetType type, boolean activeFullyVisible) {
            this.type = type;
            this.activeFullyVisible = activeFullyVisible;
        }

        @Override
        public MiniCControlTargetType type() {
            return type;
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
