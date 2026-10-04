package craken.debug.visualization;

/** Neutral event destination; the VM itself connects only to its internal collector. */
@FunctionalInterface
public interface RuntimeEventSink {
    void accept(RuntimeEvent event);
}
