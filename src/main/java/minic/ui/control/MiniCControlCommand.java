package minic.ui.control;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public record MiniCControlCommand(
        String id,
        String label,
        BooleanSupplier enabled,
        Runnable action) {
    public MiniCControlCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(enabled, "enabled");
        Objects.requireNonNull(action, "action");
    }
}
