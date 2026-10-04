package craken.visualization.layout;

import org.junit.jupiter.api.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static craken.visualization.layout.LayoutRequest.*;
import static craken.visualization.layout.LayoutFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-layout")
final class LayoutCoordinatorTest {
    @Test void closeReleasesEveryResourceAfterAnErrorAndPreservesTheFirstFailure() {
        var first = new AssertionError("first resource"); var later = new IllegalStateException("later resource"); var released = new AtomicInteger();
        var coordinator = new LayoutCoordinator(Map.of(Kind.GRAPH, new ArrayLayout()), Runnable::run, 2,
                () -> { throw first; }, () -> { throw first; }, () -> { throw later; }, released::incrementAndGet);
        assertSame(first, assertThrows(AssertionError.class, coordinator::close));
        assertEquals(1, released.get(), "An Error must not skip the remaining registered resources");
        assertEquals(List.of(later), Arrays.asList(first.getSuppressed()));
        assertTrue(coordinator.isClosed()); assertEquals(0, coordinator.activeRequestCount());
        coordinator.close(); assertEquals(1, released.get());
    }
    private static LayoutRequest input(long geometryVersion, long epoch) {
        var original = request(Kind.GRAPH, List.of(node(1, 64, 48)), List.of(), Hints.defaults());
        return new LayoutRequest(new Stamp(original.stamp().page(), 1, geometryVersion, 0, epoch, 800), original.kind(), original.units(), original.links(), original.hints(), Map.of());
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(2);
        assertTrue(condition.getAsBoolean(), "Coordinator condition timed out");
    }
    @Test void queuedOldPublicationCannotOverwriteANewerTicketAndCacheIsRetagged() throws Exception {
        var callbacks = new ConcurrentLinkedQueue<Runnable>(); var calls = new AtomicInteger(); var accepted = new ArrayList<LayoutResult>();
        LayoutEngine engine = (r, token) -> { calls.incrementAndGet(); return new ArrayLayout().layout(r, token); };
        try (var coordinator = new LayoutCoordinator(Map.of(Kind.GRAPH, engine), callbacks::add)) {
            var key = new LayoutCoordinator.Key(1, 1);
            coordinator.submit(key, input(1, 1), accepted::add, error -> fail(error));
            await(() -> callbacks.size() == 1);
            coordinator.submit(key, input(2, 1), accepted::add, error -> fail(error));
            await(() -> callbacks.size() == 2);
            callbacks.forEach(Runnable::run);
            assertEquals(1, accepted.size()); assertEquals(2, accepted.getFirst().stamp().geometryVersion());
            assertEquals(1, calls.get(), "Only stamp changes must reuse exact geometry");
            assertEquals(0, coordinator.activeRequestCount());
        }
    }
    @Test void epochChangesInvalidateGeometryAndCloseSuppressesQueuedCallbacks() throws Exception {
        var callbacks = new ConcurrentLinkedQueue<Runnable>(); var calls = new AtomicInteger(); var accepted = new AtomicInteger(); var resourceClosed = new AtomicInteger();
        var coordinator = new LayoutCoordinator(Map.of(Kind.GRAPH, (LayoutEngine)(r, token) -> { calls.incrementAndGet(); return new ArrayLayout().layout(r, token); }), callbacks::add, 2, resourceClosed::incrementAndGet);
        coordinator.submit(new LayoutCoordinator.Key(1, 1), input(1, 1), r -> accepted.incrementAndGet(), error -> fail(error));
        await(() -> callbacks.size() == 1); callbacks.poll().run();
        coordinator.submit(new LayoutCoordinator.Key(1, 1), input(1, 2), r -> accepted.incrementAndGet(), error -> fail(error));
        await(() -> callbacks.size() == 1); coordinator.close(); callbacks.poll().run(); coordinator.close();
        assertEquals(1, accepted.get()); assertEquals(2, calls.get()); assertEquals(1, resourceClosed.get());
        assertTrue(coordinator.isClosed()); assertEquals(0, coordinator.activeRequestCount());
    }
    @Test void neverRunsMoreNativeJobsThanItsConfiguredLimit() throws Exception {
        var running = new AtomicInteger(); var peak = new AtomicInteger(); var started = new CountDownLatch(2); var release = new Semaphore(0);
        LayoutEngine engine = (r, token) -> {
            int active = running.incrementAndGet(); peak.accumulateAndGet(active, Math::max); started.countDown();
            try { release.acquireUninterruptibly(); return new ArrayLayout().layout(r, token); } finally { running.decrementAndGet(); }
        };
        try (var coordinator = new LayoutCoordinator(Map.of(Kind.GRAPH, engine), Runnable::run, 2)) {
            for (int i = 1; i <= 8; i++) coordinator.submit(new LayoutCoordinator.Key(i, 1), input(1, 1), r -> {}, error -> fail(error));
            assertTrue(started.await(5, TimeUnit.SECONDS)); assertEquals(2, peak.get());
            coordinator.cancelOccurrence(8); release.release(8); await(() -> coordinator.activeRequestCount() == 0);
            assertTrue(peak.get() <= 2);
        } finally { release.release(8); }
    }
    @Test void malformedEngineStampIsRejectedAndNeverCached() throws Exception {
        var failure = new CompletableFuture<LayoutException>();
        try (var coordinator = new LayoutCoordinator(Map.of(Kind.GRAPH, (LayoutEngine)(r, token) -> new ArrayLayout().layout(input(99, 99))), Runnable::run)) {
            coordinator.submit(new LayoutCoordinator.Key(1, 1), input(1, 1), result -> fail("Bad stamp accepted"), failure::complete);
            assertEquals(LayoutException.Code.INVALID_RESULT, failure.get(5, TimeUnit.SECONDS).code());
            assertEquals(0, coordinator.cachedResultCount());
        }
    }
    @Test void aMatchingStampWithMissingGeometryIsRejectedBeforeTheUiReceivesIt() throws Exception {
        var failure = new CompletableFuture<LayoutException>();
        try (var coordinator = new LayoutCoordinator(Map.of(Kind.GRAPH, (LayoutEngine)(r, token) ->
                new LayoutResult(r.stamp(), Map.of(), Map.of(), new Rect(0, 0, 0, 0), "broken", "1")), Runnable::run)) {
            coordinator.submit(new LayoutCoordinator.Key(1, 1), input(1, 1), result -> fail("Incomplete geometry accepted"), failure::complete);
            assertEquals(LayoutException.Code.INVALID_RESULT, failure.get(5, TimeUnit.SECONDS).code());
        }
    }
    @Test void geometryCacheIsBoundedAndDiagnosticsAreImmutable() throws Exception {
        try (var coordinator = new LayoutCoordinator(Map.of(Kind.GRAPH, new ArrayLayout()), Runnable::run)) {
            for (int i = 1; i <= 70; i++) {
                var base = input(i, 1); var changed = new LayoutRequest(base.stamp(), Kind.GRAPH,
                        List.of(node(1, 64 + i, 48)), List.of(), base.hints(), Map.of());
                coordinator.submit(new LayoutCoordinator.Key(i, 1), changed, result -> {}, error -> fail(error));
            }
            await(() -> coordinator.activeRequestCount() == 0); assertEquals(64, coordinator.cachedResultCount());
            var statistics = coordinator.diagnostics(); assertEquals(70, statistics.submittedRequests());
            assertThrows(UnsupportedOperationException.class, () -> statistics.engineRuns().clear());
        }
    }
}
