package craken.visualization.api;

import craken.visualization.mutation.ModelChangeSet;
import java.util.List;
import craken.visualization.model.ViewNode;

public record MutationResult(long version, List<ViewLocation> created, ModelChangeSet change, VisualizationError error,
                             List<Long> createdRules, List<ReadValue> reads) {
    public record ReadValue(int commandIndex, OperationPath path, AccessKind kind, ViewNode.Spec content) {}
    public MutationResult { created = List.copyOf(created); createdRules = List.copyOf(createdRules); reads = List.copyOf(reads); }
    public MutationResult(long version, List<ViewLocation> created, ModelChangeSet change, VisualizationError error, List<Long> createdRules) {
        this(version, created, change, error, createdRules, List.of());
    }
    public MutationResult(long version, List<ViewLocation> created, ModelChangeSet change, VisualizationError error) {
        this(version, created, change, error, List.of());
    }
    public boolean succeeded() { return error == null; }
    public MutationResult requireSuccess() {
        if (error != null) throw new IllegalStateException(error.code() + ": " + error.message());
        return this;
    }
    public static MutationResult failed(long version, VisualizationError error) {
        return new MutationResult(version, List.of(), null, error);
    }
}
