package minic.runtime.debug.visual;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisualEventTest {
    @Test
    void createsNodeAndEdgeEventsAndFiltersBySnapshot() {
        VisualEvent node = VisualEvent.nodeCreated(2, "avl", "1", "10");
        VisualEvent edge = VisualEvent.edgeSet(4, "avl", "left", "1", "2");
        VisualEvent meta = VisualEvent.metaSet(5, "avl", "1", "height", "3");

        assertThat(node.type()).isEqualTo(VisualEventType.NODE_CREATED);
        assertThat(node.nodeId()).isEqualTo("1");
        assertThat(edge.type()).isEqualTo(VisualEventType.EDGE_SET);
        assertThat(edge.fromId()).isEqualTo("1");
        assertThat(edge.toId()).isEqualTo("2");
        assertThat(meta.type()).isEqualTo(VisualEventType.META_SET);
        assertThat(meta.metadata()).containsExactly(Map.entry("height", "3"));
        assertThat(List.of(node, edge).stream()
                .filter(event -> event.snapshotId() <= 2)
                .toList()).containsExactly(node);
    }

    @Test
    void defensivelyCopiesMetadata() {
        java.util.LinkedHashMap<String, String> metadata = new java.util.LinkedHashMap<>();
        metadata.put("height", "3");
        VisualEvent event = new VisualEvent(
                1,
                VisualEventType.META_SET,
                "avl",
                "1",
                "",
                "",
                "height",
                "3",
                "",
                metadata
        );

        metadata.put("height", "4");

        assertThat(event.metadata()).containsExactly(Map.entry("height", "3"));
        assertThatThrownBy(() -> event.metadata().put("color", "red"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
