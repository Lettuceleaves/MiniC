package craken.debug.visualization;

import java.util.List;
import java.util.Objects;

/** Immutable events since the previous source stop, including the initialization interval. */
public record RuntimeEventBatch(int contextIndex, boolean monitored, boolean complete,
                                List<RuntimeEvent> events, String diagnostic) {
    public RuntimeEventBatch {
        if (contextIndex < 0) throw new IllegalArgumentException("contextIndex must not be negative");
        events = List.copyOf(events);
        diagnostic = Objects.requireNonNullElse(diagnostic, "");
        if (complete && !diagnostic.isEmpty()) throw new IllegalArgumentException("Complete batch has a diagnostic");
    }

    public static RuntimeEventBatch unmonitored(int contextIndex) {
        return new RuntimeEventBatch(contextIndex, false, true, List.of(), "");
    }
}
