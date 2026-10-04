package craken.visualization.model.relation;

import craken.visualization.api.ViewLocation;

public record TopologyEdge(long id, ViewLocation a, ViewLocation b, Direction direction) {
    public enum Direction {
        NONE, FORWARD, BACKWARD, BOTH;
        public Direction reversed() {
            return switch (this) { case FORWARD -> BACKWARD; case BACKWARD -> FORWARD; default -> this; };
        }
    }
}
