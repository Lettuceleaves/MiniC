package craken.visualization.model;

import craken.visualization.api.*;
import java.util.Objects;

public record InteractionState(ViewLocation focus, ViewLocation accessed, AccessKind accessKind, VisualizationOptions options) {
    public static final InteractionState EMPTY = new InteractionState(null,null,null,VisualizationOptions.DEFAULT);
    public InteractionState { Objects.requireNonNull(options); }
}
