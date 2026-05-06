package minic.runtime.debug.visual;

import java.util.List;
import java.util.Objects;

/**
 * 图结构基元占位模型。
 */
public record GraphStructure(
        String id,
        String name,
        String kind,
        String layoutHint,
        List<VisualDecorator> decorators,
        List<VisualValidator> validators
) implements VisualStructure {
    public GraphStructure {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(layoutHint, "layoutHint");
        Objects.requireNonNull(decorators, "decorators");
        Objects.requireNonNull(validators, "validators");
        if (id.isBlank() || name.isBlank() || kind.isBlank() || layoutHint.isBlank()) {
            throw new IllegalArgumentException("graph id, name, kind and layoutHint must not be blank");
        }
        decorators = List.copyOf(decorators);
        validators = List.copyOf(validators);
    }

    @Override
    public VisualStructureType type() {
        return VisualStructureType.GRAPH;
    }

    @Override
    public String summary() {
        return "graph " + name + " kind=" + kind + " layout=" + layoutHint;
    }
}
