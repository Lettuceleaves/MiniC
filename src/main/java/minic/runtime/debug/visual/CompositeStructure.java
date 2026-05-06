package minic.runtime.debug.visual;

import java.util.List;
import java.util.Objects;

/**
 * 混合结构基元占位模型。
 */
public record CompositeStructure(
        String id,
        String name,
        String kind,
        String primaryPartId,
        List<String> partIds,
        List<VisualDecorator> decorators,
        List<VisualValidator> validators
) implements VisualStructure {
    public CompositeStructure {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(primaryPartId, "primaryPartId");
        Objects.requireNonNull(partIds, "partIds");
        Objects.requireNonNull(decorators, "decorators");
        Objects.requireNonNull(validators, "validators");
        if (id.isBlank() || name.isBlank() || kind.isBlank() || primaryPartId.isBlank()) {
            throw new IllegalArgumentException("composite id, name, kind and primaryPartId must not be blank");
        }
        partIds = List.copyOf(partIds);
        decorators = List.copyOf(decorators);
        validators = List.copyOf(validators);
    }

    @Override
    public VisualStructureType type() {
        return VisualStructureType.COMPOSITE;
    }

    @Override
    public String summary() {
        return "composite " + name + " kind=" + kind + " parts=" + partIds.size();
    }
}
