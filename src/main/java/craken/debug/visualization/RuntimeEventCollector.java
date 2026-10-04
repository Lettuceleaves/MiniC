package craken.debug.visualization;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongFunction;
import java.util.function.Supplier;

/**
 * Runtime-thread collector, with one fresh instance per VM session.
 * Collection failures suspend this interval without failing the VM. Not thread-safe.
 */
public final class RuntimeEventCollector implements RuntimeEventSink, AutoCloseable {
    private final boolean enabled;
    private List<RuntimeEvent> pending;
    private long nextSequence = 1;
    private String diagnostic = "";
    private int suppressionDepth;
    private boolean closed;

    public RuntimeEventCollector() {
        this(true, new ArrayList<>());
    }

    RuntimeEventCollector(boolean enabled, List<RuntimeEvent> storage) {
        this.enabled = enabled;
        pending = Objects.requireNonNull(storage, "storage");
    }

    public static RuntimeEventCollector disabled() {
        return new RuntimeEventCollector(false, new ArrayList<>());
    }

    public boolean isRecording() {
        return enabled && !closed && diagnostic.isEmpty() && suppressionDepth == 0;
    }

    /** Factory evaluation and storage stay inside the exception boundary. Disabled calls create no events. */
    public void record(LongFunction<? extends RuntimeEvent> factory) {
        if (!isRecording()) return;
        try {
            if (nextSequence == Long.MAX_VALUE) throw new IllegalStateException("Event sequence exhausted");
            RuntimeEvent event = Objects.requireNonNull(factory.apply(nextSequence), "event");
            accept(event);
        } catch (RuntimeException failure) {
            diagnostic = failure.getClass().getSimpleName() + ": " + Objects.toString(failure.getMessage(), "");
        }
    }

    @Override
    public void accept(RuntimeEvent event) {
        if (!isRecording()) return;
        try {
            Objects.requireNonNull(event, "event");
            if (nextSequence == Long.MAX_VALUE || event.sequence() != nextSequence)
                throw new IllegalArgumentException("Unexpected event sequence: " + event.sequence());
            if (!pending.add(event)) throw new IllegalStateException("Event storage rejected an event");
            nextSequence++;
        } catch (RuntimeException failure) {
            diagnostic = failure.getClass().getSimpleName() + ": " + Objects.toString(failure.getMessage(), "");
        }
    }

    public <T> T withoutEvents(Supplier<T> action) {
        suppressionDepth++;
        try { return action.get(); }
        finally { suppressionDepth--; }
    }

    public RuntimeEventBatch drain(int contextIndex) {
        if (contextIndex < 0) throw new IllegalArgumentException("contextIndex must not be negative");
        if (!enabled || closed) return RuntimeEventBatch.unmonitored(contextIndex);
        List<RuntimeEvent> events;
        try { events = List.copyOf(pending); }
        catch (RuntimeException failure) {
            diagnostic = failure.getClass().getSimpleName() + ": " + Objects.toString(failure.getMessage(), "");
            events = List.of();
        }
        String failure = diagnostic;
        pending = new ArrayList<>();
        diagnostic = "";
        return new RuntimeEventBatch(contextIndex, true, failure.isEmpty(), events, failure);
    }
    /** Detaches the VM observation outlet. Subsequent VM calls use the no-operation path. */
    @Override public void close() {
        if (closed) return;
        closed=true;pending=new ArrayList<>();diagnostic="";
    }
}
