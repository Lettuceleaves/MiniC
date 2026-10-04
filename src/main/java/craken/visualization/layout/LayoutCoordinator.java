package craken.visualization.layout;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import craken.visualization.api.PageRef;
import craken.visualization.layout.graphviz.GraphvizProcessBridge;

public final class LayoutCoordinator implements AutoCloseable {
    public record Key(long occurrenceId, long partId) {
        public Key { if (occurrenceId <= 0 || partId <= 0) throw new IllegalArgumentException("Invalid occurrence/part identity"); }
    }
    public record Diagnostics(long submittedRequests, long cacheHits, Map<LayoutRequest.Kind, Long> engineRuns,
                              int activeRequests, int activeWorkers, int activeNativeProcesses,
                              long lastQueueDelayNanos, long lastPublicationHandlerNanos, boolean closed) {
        public Diagnostics { engineRuns = Map.copyOf(engineRuns); }
    }
    private record GeometryKey(PageRef page, long part, long epoch, LayoutRequest.Kind kind,
                               List<LayoutRequest.Unit> units, List<LayoutRequest.Link> links, LayoutRequest.Hints hints,
                               long measurement, double width, Map<craken.visualization.api.ViewLocation, LayoutRequest.Point> previous) {
        static GeometryKey of(LayoutRequest request) {
            var stamp = request.stamp(); return new GeometryKey(stamp.page(), stamp.partId(), stamp.epoch(), request.kind(),
                    request.units(), request.links(), request.hints(), stamp.measurementVersion(), stamp.availableWidth(), request.previousPositions());
        }
    }
    private static final class Ticket {
        final long id; final LayoutRequest request; final GeometryKey geometry; final AtomicBoolean cancelled = new AtomicBoolean();
        volatile Consumer<LayoutResult> success; volatile Consumer<LayoutException> failure; volatile Future<?> future;
        volatile long completedAt;
        Ticket(long id, LayoutRequest request, Consumer<LayoutResult> success, Consumer<LayoutException> failure) {
            this.id = id; this.request = request; geometry = GeometryKey.of(request); this.success = success; this.failure = failure;
        }
        void cancel() { cancelled.set(true); if (future != null) future.cancel(true); }
    }
    private final Map<LayoutRequest.Kind, LayoutEngine> engines;
    private final Consumer<Runnable> dispatcher;
    private final ExecutorService workers;
    private final List<AutoCloseable> resources;
    private final Map<Key, Ticket> active = new HashMap<>();
    private final LinkedHashMap<GeometryKey, LayoutResult> cache = new LinkedHashMap<>(16, .75f, true);
    private final Map<LayoutRequest.Kind, Long> engineRuns = new EnumMap<>(LayoutRequest.Kind.class);
    private final AtomicInteger activeWorkers = new AtomicInteger();
    private long sequence, submissions, cacheHits, lastQueueDelayNanos, lastPublicationHandlerNanos; private boolean closed;
    public LayoutCoordinator(Map<LayoutRequest.Kind, LayoutEngine> engines, Consumer<Runnable> dispatcher) { this(engines, dispatcher, 2); }
    public LayoutCoordinator(Map<LayoutRequest.Kind, LayoutEngine> engines, Consumer<Runnable> dispatcher, int parallelism, AutoCloseable... resources) {
        if (parallelism <= 0) throw new IllegalArgumentException("Parallelism must be positive");
        this.engines = Map.copyOf(engines); this.dispatcher = Objects.requireNonNull(dispatcher); this.resources = List.of(resources);
        workers = Executors.newFixedThreadPool(parallelism, Thread.ofPlatform().daemon(true).name("visualization-layout-", 0).factory());
    }
    public synchronized long submit(Key key, LayoutRequest request, Consumer<LayoutResult> success, Consumer<LayoutException> failure) {
        if (closed) throw new IllegalStateException("Layout coordinator closed");
        Objects.requireNonNull(key); Objects.requireNonNull(request); Objects.requireNonNull(success); Objects.requireNonNull(failure);
        submissions++;
        var previous = active.get(key); var geometry = GeometryKey.of(request);
        if (previous != null && previous.request.stamp().equals(request.stamp()) && previous.geometry.equals(geometry)) {
            previous.success = success; previous.failure = failure; return previous.id;
        }
        if (previous != null) previous.cancel();
        var ticket = new Ticket(++sequence, request, success, failure); active.put(key, ticket);
        var cached = cache.get(geometry);
        if (cached != null) {
            cacheHits++; ticket.completedAt = System.nanoTime();
            var result = new LayoutResult(request.stamp(), cached.nodeBounds(), cached.edgePaths(), cached.contentBounds(), cached.engine(), cached.engineVersion());
            dispatcher.accept(() -> publish(key, ticket, result, null));
        } else ticket.future = workers.submit(() -> compute(key, ticket));
        return ticket.id;
    }
    private void compute(Key key, Ticket ticket) {
        activeWorkers.incrementAndGet();
        try {
            synchronized (this) {
                if (closed || ticket.cancelled.get() || active.get(key) != ticket) return;
                engineRuns.merge(ticket.request.kind(), 1L, Long::sum);
            }
            var engine = engines.get(ticket.request.kind());
            if (engine == null) throw new LayoutException(LayoutException.Code.RUNTIME_UNAVAILABLE, "No registered layout engine: " + ticket.request.kind());
            var result = engine.layout(ticket.request, ticket.cancelled::get);
            if (!result.stamp().equals(ticket.request.stamp())) throw new LayoutException(LayoutException.Code.INVALID_RESULT, "Engine returned a different layout stamp");
            var expectedMembers = new HashMap<craken.visualization.api.ViewLocation, LayoutRequest.Rect>();
            ticket.request.units().forEach(u -> u.members().forEach(m -> expectedMembers.put(m.node(), m.bounds())));
            var expectedEdges = new HashSet<Long>(); ticket.request.links().forEach(e -> expectedEdges.add(e.id()));
            if (!result.nodeBounds().keySet().equals(expectedMembers.keySet()) || !result.edgePaths().keySet().equals(expectedEdges))
                throw new LayoutException(LayoutException.Code.INVALID_RESULT, "Engine returned incomplete node/edge geometry");
            for (var member : expectedMembers.entrySet()) {
                var actual = result.nodeBounds().get(member.getKey()); var measured = member.getValue();
                if (Math.abs(actual.width() - measured.width()) > 1e-7 || Math.abs(actual.height() - measured.height()) > 1e-7)
                    throw new LayoutException(LayoutException.Code.INVALID_RESULT, "Engine changed a measured member size");
            }
            synchronized (this) {
                if (closed || ticket.cancelled.get() || active.get(key) != ticket) return;
                cache.put(ticket.geometry, result);
                while (cache.size() > 64) cache.remove(cache.keySet().iterator().next());
            }
            ticket.completedAt = System.nanoTime(); dispatcher.accept(() -> publish(key, ticket, result, null));
        } catch (LayoutException error) {
            if (!ticket.cancelled.get()) dispatcher.accept(() -> publish(key, ticket, null, error));
        } catch (RuntimeException error) {
            dispatcher.accept(() -> publish(key, ticket, null, new LayoutException(LayoutException.Code.INVALID_RESULT, "Layout engine failed", error)));
        } finally { activeWorkers.decrementAndGet(); }
    }
    private synchronized void publish(Key key, Ticket ticket, LayoutResult result, LayoutException failure) {
        if (closed || ticket.cancelled.get() || active.get(key) != ticket) return;
        active.remove(key); long entered = System.nanoTime();
        lastQueueDelayNanos = ticket.completedAt == 0 ? 0 : entered - ticket.completedAt;
        try { if (failure == null) ticket.success.accept(result); else ticket.failure.accept(failure); }
        finally { lastPublicationHandlerNanos = System.nanoTime() - entered; }
    }
    public synchronized void cancel(Key key) { var ticket = active.remove(key); if (ticket != null) ticket.cancel(); }
    public synchronized void cancelOccurrence(long occurrenceId) {
        active.keySet().stream().filter(k -> k.occurrenceId() == occurrenceId).toList().forEach(this::cancel);
    }
    public synchronized int activeRequestCount() { return active.size(); }
    public synchronized int cachedResultCount() { return cache.size(); }
    public synchronized boolean isClosed() { return closed; }
    public synchronized Diagnostics diagnostics() {
        int processes = resources.stream().filter(r -> r instanceof GraphvizProcessBridge).mapToInt(r -> ((GraphvizProcessBridge)r).activeProcessCount()).sum();
        return new Diagnostics(submissions, cacheHits, engineRuns, active.size(), activeWorkers.get(), processes, lastQueueDelayNanos, lastPublicationHandlerNanos, closed);
    }
    @Override public synchronized void close() {
        if (closed) return; closed = true; active.values().forEach(Ticket::cancel); active.clear(); cache.clear(); workers.shutdownNow();
        Throwable failure = null;
        for (var resource : resources) try { resource.close(); } catch (Exception | Error error) {
            if (failure == null) failure = error instanceof Error ? error : new IllegalStateException("Cannot close layout resource", error);
            else if (error != failure) failure.addSuppressed(error);
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
    }
}
