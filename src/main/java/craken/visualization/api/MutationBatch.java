package craken.visualization.api;

import java.util.List;
import java.util.Objects;

public record MutationBatch(List<VisualizationCommand> commands, String sourceStep) {
    public MutationBatch {
        commands = List.copyOf(commands);
        sourceStep = Objects.requireNonNullElse(sourceStep, "");
    }
    public static MutationBatch of(VisualizationCommand... commands) { return new MutationBatch(List.of(commands), ""); }
}
