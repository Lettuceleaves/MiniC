package minic.runtime.debug.visual;

import java.util.List;
import java.util.Objects;

/**
 * 连续空间/表格基元占位模型。
 */
public record ArrayStructure(
        String id,
        String name,
        String kind,
        String layoutHint,
        int dimensions,
        List<VisualDecorator> decorators,
        List<VisualValidator> validators
) implements VisualStructure {
    public ArrayStructure {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(layoutHint, "layoutHint");
        Objects.requireNonNull(decorators, "decorators");
        Objects.requireNonNull(validators, "validators");
        if (id.isBlank() || name.isBlank() || kind.isBlank() || layoutHint.isBlank()) {
            throw new IllegalArgumentException("array id, name, kind and layoutHint must not be blank");
        }
        if (dimensions < 1) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        decorators = List.copyOf(decorators);
        validators = List.copyOf(validators);
    }

    @Override
    public VisualStructureType type() {
        return VisualStructureType.ARRAY;
    }

    @Override
    public String summary() {
        return "array " + name + " kind=" + kind + " dimensions=" + dimensions + " layout=" + layoutHint;
    }
}
