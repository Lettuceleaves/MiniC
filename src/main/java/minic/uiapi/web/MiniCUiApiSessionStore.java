package minic.uiapi.web;

import minic.uiapi.MiniCDebugApi;
import minic.uiapi.MiniCObservationApi;

import java.time.Duration;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local opaque session registry for browser UIAPI transport.
 */
public final class MiniCUiApiSessionStore {
    private static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(30);

    private final Duration idleTimeout;
    private final Map<String, Entry<MiniCObservationApi>> observationSessions = new ConcurrentHashMap<>();
    private final Map<String, Entry<MiniCDebugApi>> debugSessions = new ConcurrentHashMap<>();

    public MiniCUiApiSessionStore() {
        this(DEFAULT_IDLE_TIMEOUT);
    }

    MiniCUiApiSessionStore(Duration idleTimeout) {
        if (idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("idleTimeout must be positive");
        }
        this.idleTimeout = idleTimeout;
    }

    public String createObservationSession() {
        cleanupExpired();
        String id = newSessionId();
        observationSessions.put(id, new Entry<>(new MiniCObservationApi()));
        return id;
    }

    public MiniCObservationApi observationSession(String id) {
        return session(observationSessions, id, "observation");
    }

    public String createDebugSession() {
        cleanupExpired();
        String id = newSessionId();
        debugSessions.put(id, new Entry<>(new MiniCDebugApi()));
        return id;
    }

    public MiniCDebugApi debugSession(String id) {
        return session(debugSessions, id, "debug");
    }

    public void removeDebugSession(String id) {
        debugSessions.remove(id);
    }

    private <T> T session(Map<String, Entry<T>> sessions, String id, String kind) {
        cleanupExpired();
        Entry<T> entry = sessions.get(id);
        if (entry == null) {
            throw new SessionNotFoundException(kind, id);
        }
        entry.touch();
        return entry.value();
    }

    private void cleanupExpired() {
        long now = System.nanoTime();
        long timeoutNanos = idleTimeout.toNanos();
        removeExpired(observationSessions, now, timeoutNanos);
        removeExpired(debugSessions, now, timeoutNanos);
    }

    private <T> void removeExpired(Map<String, Entry<T>> sessions, long now, long timeoutNanos) {
        sessions.entrySet().removeIf(entry -> now - entry.getValue().lastAccessNanos() > timeoutNanos);
    }

    private String newSessionId() {
        return UUID.randomUUID().toString();
    }

    private static final class Entry<T> {
        private final T value;
        private volatile long lastAccessNanos = System.nanoTime();

        private Entry(T value) {
            this.value = value;
        }

        private T value() {
            return value;
        }

        private long lastAccessNanos() {
            return lastAccessNanos;
        }

        private void touch() {
            lastAccessNanos = System.nanoTime();
        }
    }

    public static final class SessionNotFoundException extends NoSuchElementException {
        public SessionNotFoundException(String kind, String id) {
            super(kind + " session not found: " + id);
        }
    }
}
