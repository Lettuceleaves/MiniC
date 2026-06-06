package minic.uilocal.control;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;

public final class MiniCActiveTrackingService {
    private final Supplier<? extends Collection<? extends MiniCViewportAdapter>> activeAdapters;

    public MiniCActiveTrackingService(Supplier<? extends Collection<? extends MiniCViewportAdapter>> activeAdapters) {
        this.activeAdapters = Objects.requireNonNull(activeAdapters, "activeAdapters");
    }

    public void trackActiveViewports() {
        Collection<? extends MiniCViewportAdapter> adapters = activeAdapters.get();
        if (adapters == null || adapters.isEmpty()) {
            return;
        }
        for (MiniCViewportAdapter adapter : adapters) {
            if (adapter != null) {
                adapter.centerActiveIfNeeded();
            }
        }
    }
}
