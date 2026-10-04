package craken.debug.visualization;

import java.util.ArrayList;

/** Test-only fault injection into internal event storage, not a VM callback. */
public final class FailingEventCollectors {
    private FailingEventCollectors() {}

    public static RuntimeEventCollector failsOnAppend() {
        return new RuntimeEventCollector(true, new ArrayList<>() {
            @Override public boolean add(RuntimeEvent event) { throw new IllegalStateException("injected event storage failure"); }
        });
    }
}
