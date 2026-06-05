package minic.web;

import minic.uiapi.MiniCDebugApi;
import minic.uiapi.MiniCObservationApi;
import minic.web.dto.WebSessionDtos.SessionClosedResponse;
import minic.web.dto.WebSessionDtos.SessionCreatedResponse;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Owns web-facing compile and debug session lifecycles.
 */
public final class MiniCWebSessionRegistry {
    private static final int SESSION_ID_BYTES = 18;

    private final ConcurrentMap<String, CompileEntry> compileSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, DebugEntry> debugSessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Supplier<Instant> clock;

    public MiniCWebSessionRegistry() {
        this(Instant::now);
    }

    MiniCWebSessionRegistry(Supplier<Instant> clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SessionCreatedResponse createCompileSession(String sourceName, String sourceText) {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource(normalizeSourceName(sourceName), normalizeSourceText(sourceText));
        CompileEntry entry = new CompileEntry(nextSessionId(), api, now());
        while (compileSessions.putIfAbsent(entry.sessionId(), entry) != null) {
            entry = new CompileEntry(nextSessionId(), api, now());
        }
        return new SessionCreatedResponse(entry.sessionId(), entry.version());
    }

    public SessionCreatedResponse createDebugSession(String sourceName, String sourceText) {
        MiniCDebugApi api = new MiniCDebugApi();
        api.loadSource(normalizeSourceName(sourceName), normalizeSourceText(sourceText));
        DebugEntry entry = new DebugEntry(nextSessionId(), api, now());
        while (debugSessions.putIfAbsent(entry.sessionId(), entry) != null) {
            entry = new DebugEntry(nextSessionId(), api, now());
        }
        return new SessionCreatedResponse(entry.sessionId(), entry.version());
    }

    public void updateCompileSource(String sessionId, String sourceName, String sourceText) {
        commandCompileSession(sessionId, api -> {
            api.loadSource(normalizeSourceName(sourceName), normalizeSourceText(sourceText));
            return null;
        });
    }

    public void updateDebugSource(String sessionId, String sourceName, String sourceText) {
        commandDebugSession(sessionId, api -> {
            api.loadSource(normalizeSourceName(sourceName), normalizeSourceText(sourceText));
            return null;
        });
    }

    public void startCompileSession(String sessionId) {
        commandCompileSession(sessionId, api -> {
            api.startSession();
            return null;
        });
    }

    public void startDebugSession(String sessionId) {
        commandDebugSession(sessionId, api -> {
            api.startDebug();
            return null;
        });
    }

    public <T> T queryCompileSession(String sessionId, Function<MiniCObservationApi, T> action) {
        CompileEntry entry = requireCompileSession(sessionId);
        synchronized (entry.lock()) {
            ensureActive(compileSessions, sessionId, entry);
            entry.touch(now());
            return action.apply(entry.api());
        }
    }

    public <T> T commandCompileSession(String sessionId, Function<MiniCObservationApi, T> action) {
        CompileEntry entry = requireCompileSession(sessionId);
        synchronized (entry.lock()) {
            ensureActive(compileSessions, sessionId, entry);
            entry.touch(now());
            T result = action.apply(entry.api());
            entry.incrementVersion();
            entry.touch(now());
            return result;
        }
    }

    public <T> T queryDebugSession(String sessionId, Function<MiniCDebugApi, T> action) {
        DebugEntry entry = requireDebugSession(sessionId);
        synchronized (entry.lock()) {
            ensureActive(debugSessions, sessionId, entry);
            entry.touch(now());
            return action.apply(entry.api());
        }
    }

    public <T> T commandDebugSession(String sessionId, Function<MiniCDebugApi, T> action) {
        DebugEntry entry = requireDebugSession(sessionId);
        synchronized (entry.lock()) {
            ensureActive(debugSessions, sessionId, entry);
            entry.touch(now());
            T result = action.apply(entry.api());
            entry.incrementVersion();
            entry.touch(now());
            return result;
        }
    }

    public long compileVersion(String sessionId) {
        return requireCompileSession(sessionId).version();
    }

    public long debugVersion(String sessionId) {
        return requireDebugSession(sessionId).version();
    }

    public SessionClosedResponse closeCompileSession(String sessionId) {
        CompileEntry entry = requireCompileSession(sessionId);
        synchronized (entry.lock()) {
            ensureActive(compileSessions, sessionId, entry);
            long version = entry.close();
            compileSessions.remove(sessionId, entry);
            return new SessionClosedResponse(sessionId, version, true);
        }
    }

    public SessionClosedResponse closeDebugSession(String sessionId) {
        DebugEntry entry = requireDebugSession(sessionId);
        synchronized (entry.lock()) {
            ensureActive(debugSessions, sessionId, entry);
            long version = entry.close();
            debugSessions.remove(sessionId, entry);
            return new SessionClosedResponse(sessionId, version, true);
        }
    }

    public int expireIdleSessions(Duration idleTimeout) {
        Objects.requireNonNull(idleTimeout, "idleTimeout");
        if (idleTimeout.isNegative()) {
            throw new IllegalArgumentException("idleTimeout must not be negative");
        }
        Instant cutoff = now().minus(idleTimeout);
        return expireIdleCompileSessions(cutoff) + expireIdleDebugSessions(cutoff);
    }

    private int expireIdleCompileSessions(Instant cutoff) {
        int expired = 0;
        for (CompileEntry entry : compileSessions.values()) {
            synchronized (entry.lock()) {
                if (compileSessions.get(entry.sessionId()) == entry
                        && !entry.closed()
                        && !entry.lastAccess().isAfter(cutoff)) {
                    entry.close();
                    if (compileSessions.remove(entry.sessionId(), entry)) {
                        expired++;
                    }
                }
            }
        }
        return expired;
    }

    private int expireIdleDebugSessions(Instant cutoff) {
        int expired = 0;
        for (DebugEntry entry : debugSessions.values()) {
            synchronized (entry.lock()) {
                if (debugSessions.get(entry.sessionId()) == entry
                        && !entry.closed()
                        && !entry.lastAccess().isAfter(cutoff)) {
                    entry.close();
                    if (debugSessions.remove(entry.sessionId(), entry)) {
                        expired++;
                    }
                }
            }
        }
        return expired;
    }

    private static <T, E extends SessionEntry<T>> void ensureActive(
            ConcurrentMap<String, E> sessions,
            String sessionId,
            E entry
    ) {
        if (entry.closed() || sessions.get(sessionId) != entry) {
            throw new SessionNotFoundException(sessionId);
        }
    }

    private CompileEntry requireCompileSession(String sessionId) {
        CompileEntry entry = compileSessions.get(sessionId);
        if (entry == null) {
            throw new SessionNotFoundException(sessionId);
        }
        return entry;
    }

    private DebugEntry requireDebugSession(String sessionId) {
        DebugEntry entry = debugSessions.get(sessionId);
        if (entry == null) {
            throw new SessionNotFoundException(sessionId);
        }
        return entry;
    }

    private String nextSessionId() {
        byte[] bytes = new byte[SESSION_ID_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Instant now() {
        return clock.get();
    }

    private static String normalizeSourceName(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            return "main.mc";
        }
        return sourceName;
    }

    private static String normalizeSourceText(String sourceText) {
        return sourceText == null ? "" : sourceText;
    }

    public static final class SessionNotFoundException extends RuntimeException {
        public SessionNotFoundException(String sessionId) {
            super("web session not found: " + sessionId);
        }
    }

    private abstract static class SessionEntry<T> {
        private final String sessionId;
        private final T api;
        private final Object lock = new Object();
        private final AtomicLong version = new AtomicLong();
        private final AtomicReference<Instant> lastAccess;
        private boolean closed;

        private SessionEntry(String sessionId, T api, Instant createdAt) {
            this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
            this.api = Objects.requireNonNull(api, "api");
            lastAccess = new AtomicReference<>(Objects.requireNonNull(createdAt, "createdAt"));
        }

        final String sessionId() {
            return sessionId;
        }

        final T api() {
            return api;
        }

        final Object lock() {
            return lock;
        }

        final long version() {
            return version.get();
        }

        final long incrementVersion() {
            return version.incrementAndGet();
        }

        final long close() {
            closed = true;
            return incrementVersion();
        }

        final boolean closed() {
            return closed;
        }

        final Instant lastAccess() {
            return lastAccess.get();
        }

        final void touch(Instant at) {
            lastAccess.set(at);
        }
    }

    private static final class CompileEntry extends SessionEntry<MiniCObservationApi> {
        private CompileEntry(String sessionId, MiniCObservationApi api, Instant createdAt) {
            super(sessionId, api, createdAt);
        }
    }

    private static final class DebugEntry extends SessionEntry<MiniCDebugApi> {
        private DebugEntry(String sessionId, MiniCDebugApi api, Instant createdAt) {
            super(sessionId, api, createdAt);
        }
    }
}
