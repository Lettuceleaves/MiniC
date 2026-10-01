package minic.cpp.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Pure in-memory lifecycle tests: these handles never identify operating-system processes. */
@Timeout(5)
final class BoundedProcessCleanupTest {
    @Test void exitedParentNeedsNoProcessSnapshot() throws Exception {
        var parent = new FakeProcess(false);
        parent.handle.snapshotFailure = new RuntimeException("snapshot not available");
        BoundedProcess.terminate(parent, new LinkedHashMap<>());
        assertEquals(0, parent.handle.snapshots);
        assertEquals(0, parent.handle.destroyCalls);
    }

    @Test void exitedParentStillCleansObservedLiveChildAndItsDescendants() throws Exception {
        var parent = new FakeProcess(false);
        parent.handle.snapshotFailure = new RuntimeException("snapshot not available");
        var child = new FakeHandle(2, true);
        var grandchild = new FakeHandle(3, true);
        child.descendants = List.of(grandchild);
        var observed = new LinkedHashMap<Long, ProcessHandle>();
        observed.put(child.pid(), child);
        BoundedProcess.terminate(parent, observed);
        assertEquals(0, parent.handle.snapshots);
        assertEquals(1, child.snapshots);
        assertEquals(1, child.destroyCalls);
        assertEquals(1, grandchild.destroyCalls);
        assertFalse(child.isAlive());
        assertFalse(grandchild.isAlive());
    }

    @Test void exitedObservedChildNeedsNoProcessSnapshot() throws Exception {
        var parent = new FakeProcess(false);
        var child = new FakeHandle(2, false);
        child.snapshotFailure = new RuntimeException("snapshot not available");
        var observed = new LinkedHashMap<Long, ProcessHandle>();
        observed.put(child.pid(), child);
        BoundedProcess.terminate(parent, observed);
        assertEquals(0, child.snapshots);
        assertEquals(0, child.destroyCalls);
    }

    @Test void snapshotFailureStillCleansKnownHandlesAndPreservesTheFailure() {
        var parent = new FakeProcess(true);
        var snapshotFailure = new RuntimeException("snapshot not available");
        parent.handle.snapshotFailure = snapshotFailure;
        var child = new FakeHandle(2, true);
        var observed = new LinkedHashMap<Long, ProcessHandle>();
        observed.put(child.pid(), child);
        assertSame(snapshotFailure, assertThrows(RuntimeException.class,
                () -> BoundedProcess.terminate(parent, observed)));
        assertFalse(parent.isAlive(), "Snapshot failure must not bypass known-process cleanup");
        assertFalse(child.isAlive());
        assertEquals(1, parent.handle.snapshots, "A failed snapshot must not be retried during cleanup");
        assertEquals(1, parent.handle.destroyCalls);
        assertEquals(1, child.destroyCalls);
    }

    private static final class FakeProcess extends Process {
        final FakeHandle handle;
        FakeProcess(boolean alive) { handle = new FakeHandle(1, alive); }
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return exitValue(); }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return !isAlive(); }
        @Override public int exitValue() {
            if (isAlive()) throw new IllegalThreadStateException("Fake process remains alive");
            return 0;
        }
        @Override public void destroy() { handle.destroyForcibly(); }
        @Override public Process destroyForcibly() { destroy(); return this; }
        @Override public boolean isAlive() { return handle.isAlive(); }
        @Override public long pid() { return handle.pid(); }
        @Override public ProcessHandle toHandle() { return handle; }
        @Override public Stream<ProcessHandle> descendants() { return handle.descendants(); }
    }

    private static final class FakeHandle implements ProcessHandle {
        final long id;
        boolean alive;
        int snapshots;
        int destroyCalls;
        RuntimeException snapshotFailure;
        List<ProcessHandle> descendants = List.of();
        FakeHandle(long id, boolean alive) { this.id = id; this.alive = alive; }
        @Override public long pid() { return id; }
        @Override public Optional<ProcessHandle> parent() { return Optional.empty(); }
        @Override public Stream<ProcessHandle> children() { return descendants.stream(); }
        @Override public Stream<ProcessHandle> descendants() {
            snapshots++;
            if (snapshotFailure != null) throw snapshotFailure;
            return descendants.stream();
        }
        @Override public Info info() { throw new UnsupportedOperationException("No OS process"); }
        @Override public CompletableFuture<ProcessHandle> onExit() {
            if (alive) throw new AssertionError("Cleanup must terminate the handle before awaiting it");
            return CompletableFuture.completedFuture(this);
        }
        @Override public boolean supportsNormalTermination() { return true; }
        @Override public boolean destroy() { return destroyForcibly(); }
        @Override public boolean destroyForcibly() { destroyCalls++; alive = false; return true; }
        @Override public boolean isAlive() { return alive; }
        @Override public int compareTo(ProcessHandle other) { return Long.compare(id, other.pid()); }
    }
}
