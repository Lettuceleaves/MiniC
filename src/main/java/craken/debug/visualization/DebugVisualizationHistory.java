package craken.debug.visualization;

import craken.debug.Debugger;
import craken.visualization.api.ViewLocation;
import craken.visualization.snapshot.VisualizationSnapshot;
import java.util.*;

/** Stop-index history. It restores presentation only and has no execution or VM memory access API. */
public final class DebugVisualizationHistory implements AutoCloseable {
    public enum Reason { UNKNOWN_CONTEXT, EXPIRED_CONTEXT, CLOSED }
    public static final class HistoryUnavailableException extends IllegalStateException {
        private final Reason reason;
        HistoryUnavailableException(Reason reason,String message) { super(message);this.reason=reason; }
        public Reason reason() { return reason; }
    }
    public record Frame(int contextIndex,boolean accepted,RuntimeEventBatch events,String diagnostic,
                        VisualizationSnapshot snapshot,Map<DebugObjectIdentityRegistry.ObjectKey,ViewLocation> locations) {
        public Frame { locations=Collections.unmodifiableMap(new LinkedHashMap<>(locations)); }
    }
    private final DebugVisualizationAdapter adapter;
    private final RuntimeEventCollector collector;
    private final Runnable releaseDisplay;
    private final int limit;
    private final NavigableMap<Integer,Frame> frames=new TreeMap<>();
    private final Map<Integer,DebugVisualizationAdapter.Checkpoint> checkpoints=new HashMap<>();
    private boolean closed;
    private int maximumIndex=-1;
    private int currentIndex=-1;
    private int expiredThrough=-1;
    public DebugVisualizationHistory(DebugVisualizationAdapter adapter,RuntimeEventCollector collector,Runnable releaseDisplay) {
        this(adapter,collector,releaseDisplay,Integer.MAX_VALUE);
    }
    public DebugVisualizationHistory(DebugVisualizationAdapter adapter,RuntimeEventCollector collector,Runnable releaseDisplay,int limit) {
        this.adapter=Objects.requireNonNull(adapter);this.collector=Objects.requireNonNull(collector);
        this.releaseDisplay=Objects.requireNonNull(releaseDisplay);
        if(limit<1)throw new IllegalArgumentException("History limit must retain current frame");this.limit=limit;
    }
    public synchronized Frame show(Debugger.Context context) {
        requireOpen();Objects.requireNonNull(context);
        if(frames.containsKey(context.index()))return show(context.index());
        if(context.index()<=maximumIndex)throw unavailable(context.index());
        if(context.index()!=context.events().contextIndex())
            throw new IllegalArgumentException("Context and event batch indexes differ");
        if(!frames.isEmpty()&&currentIndex!=frames.lastKey())adapter.restore(checkpoints.get(frames.lastKey()));
        var result=adapter.consume(context);
        var frame=new Frame(context.index(),result.accepted(),result.events(),result.diagnostic(),result.snapshot(),adapter.locations());
        frames.put(context.index(),frame);checkpoints.put(context.index(),adapter.checkpoint());maximumIndex=context.index();currentIndex=context.index();
        while(frames.size()>limit) {
            int removed=frames.firstKey();frames.remove(removed);checkpoints.remove(removed);adapter.forgetResult(removed);expiredThrough=removed;
        }
        return frame;
    }
    public synchronized Frame show(int contextIndex) {
        requireOpen();
        var frame=frames.get(contextIndex);
        if(frame==null)throw unavailable(contextIndex);
        adapter.restore(checkpoints.get(contextIndex));currentIndex=contextIndex;return frame;
    }
    public synchronized VisualizationSnapshot displayedSnapshot() { return adapter.publishedSnapshot(); }
    public synchronized List<Integer> indices() { return List.copyOf(frames.keySet()); }
    private HistoryUnavailableException unavailable(int index) {
        return new HistoryUnavailableException(index<=expiredThrough?Reason.EXPIRED_CONTEXT:Reason.UNKNOWN_CONTEXT,
                (index<=expiredThrough?"Expired context: ":"Unknown context: ")+index);
    }
    private void requireOpen() {
        if(closed)throw new HistoryUnavailableException(Reason.CLOSED,"Visualization history closed");
    }
    @Override public synchronized void close() {
        if(closed)return;
        closed=true;
        Throwable failure=null;
        for(Runnable release:List.<Runnable>of(releaseDisplay,adapter::close,collector::close)) {
            try { release.run(); }
            catch(Throwable thrown) { if(failure==null)failure=thrown;else failure.addSuppressed(thrown); }
        }
        frames.clear();checkpoints.clear();currentIndex=-1;
        if(failure instanceof RuntimeException runtime)throw runtime;
        if(failure instanceof Error error)throw error;
        if(failure!=null)throw new IllegalStateException("Visualization cleanup failed",failure);
    }
}
