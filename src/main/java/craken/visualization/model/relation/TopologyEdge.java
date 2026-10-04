package craken.visualization.model.relation;

import craken.visualization.api.ViewLocation;
import craken.visualization.style.EdgeStyle;
import java.util.Objects;

public record TopologyEdge(long id, ViewLocation a, ViewLocation b, Direction direction, String aPort, String bPort, EdgeStyle style) {
    public TopologyEdge {
        requirePort(aPort); requirePort(bPort); Objects.requireNonNull(style);
    }
    public TopologyEdge(long id, ViewLocation a, ViewLocation b, Direction direction) { this(id, a, b, direction, "node", "node", EdgeStyle.DEFAULT); }
    public static void requirePort(String key) { if (key == null || key.isBlank()) throw new IllegalArgumentException("Blank topology port"); }
    public enum Direction {
        NONE, FORWARD, BACKWARD, BOTH;
        public Direction reversed() {
            return switch (this) { case FORWARD -> BACKWARD; case BACKWARD -> FORWARD; default -> this; };
        }
    }
}
