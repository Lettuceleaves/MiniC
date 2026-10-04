package craken.visualization.api;

import craken.visualization.mutation.ModelChangeSet;
import java.util.List;

public record MutationResult(long version, List<ViewLocation> created, ModelChangeSet change, VisualizationError error) {
    public MutationResult { created = List.copyOf(created); }
    public boolean succeeded() { return error == null; }
    public MutationResult requireSuccess() {
        if (error != null) throw new IllegalStateException(error.code() + ": " + error.message());
        return this;
    }
    public static MutationResult failed(long version, VisualizationError error) {
        return new MutationResult(version, List.of(), null, error);
    }
}
