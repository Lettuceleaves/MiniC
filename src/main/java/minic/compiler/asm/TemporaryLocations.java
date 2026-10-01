package minic.compiler.asm;

import minic.compiler.ir.value.IrValue.IrTemporary;

import java.util.Map;
import java.util.Objects;

/** Immutable temporary homes. Parameters, source locals and initialization flags keep their own frame slots. */
final class TemporaryLocations {
    private final Map<String, Integer> stackOffsets;
    private final Map<String, ValueLocation> overrides;

    private TemporaryLocations(FrameLayout frame, Map<String, ValueLocation> overrides) {
        stackOffsets = Map.copyOf(Objects.requireNonNull(frame, "frame").temporaryOffsets());
        this.overrides = Map.copyOf(overrides);
        for (String name : this.overrides.keySet()) {
            if (!stackOffsets.containsKey(name)) throw new IllegalArgumentException("unknown temporary: " + name);
        }
    }

    static TemporaryLocations allStack(FrameLayout frame) { return new TemporaryLocations(frame, Map.of()); }
    static TemporaryLocations withOverrides(FrameLayout frame, Map<String, ValueLocation> overrides) {
        return new TemporaryLocations(frame, overrides);
    }

    ValueLocation location(IrTemporary temporary) {
        Objects.requireNonNull(temporary, "temporary");
        ValueLocation override = overrides.get(temporary.name());
        if (override != null) {
            if (override.type() != temporary.type()) throw new IllegalArgumentException("temporary type mismatch: " + temporary.name());
            return override;
        }
        Integer offset = stackOffsets.get(temporary.name());
        if (offset == null) throw new IllegalArgumentException("unknown temporary: " + temporary.name());
        return new ValueLocation.StackSlot(temporary.type(), offset);
    }
}
