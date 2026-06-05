package minic.web;

import minic.uiapi.MiniCObservationApi;
import minic.web.dto.WebSessionDtos.SessionCreatedResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MiniCWebSessionRegistryTest {
    @Test
    void createsUniqueUrlSafeSessionIds() {
        MiniCWebSessionRegistry registry = new MiniCWebSessionRegistry();
        Set<String> ids = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < 1000; i++) {
            SessionCreatedResponse created = registry.createCompileSession("main.mc", "int main() { return 0; }");
            assertThat(created.sessionId()).matches("[A-Za-z0-9_-]+");
            assertThat(ids.add(created.sessionId())).isTrue();
        }

        assertThat(ids).hasSize(1000);
    }

    @Test
    void closedSessionsCannotBeFetched() {
        MiniCWebSessionRegistry registry = new MiniCWebSessionRegistry();
        SessionCreatedResponse created = registry.createCompileSession("main.mc", "int main() { return 0; }");

        assertThat(registry.closeCompileSession(created.sessionId()).closed()).isTrue();

        assertThatThrownBy(() -> registry.queryCompileSession(created.sessionId(), MiniCObservationApi::currentState))
                .isInstanceOf(MiniCWebSessionRegistry.SessionNotFoundException.class)
                .hasMessageContaining(created.sessionId());
    }

    @Test
    void commandsAgainstSameSessionExecuteSerially() throws Exception {
        MiniCWebSessionRegistry registry = new MiniCWebSessionRegistry();
        String sessionId = registry.createCompileSession("main.mc", "int main() { return 0; }").sessionId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger activeCommands = new AtomicInteger();
        AtomicInteger overlaps = new AtomicInteger();
        CountDownLatch firstCommandEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstCommand = new CountDownLatch(1);

        try {
            Future<Integer> first = executor.submit(command(registry, sessionId, activeCommands, overlaps,
                    firstCommandEntered, releaseFirstCommand));
            assertThat(firstCommandEntered.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Integer> second = executor.submit(command(registry, sessionId, activeCommands, overlaps,
                    new CountDownLatch(0), new CountDownLatch(0)));
            Thread.sleep(100);
            assertThat(second.isDone()).isFalse();

            releaseFirstCommand.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(overlaps).hasValue(0);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void differentSessionsExecuteIndependently() throws Exception {
        MiniCWebSessionRegistry registry = new MiniCWebSessionRegistry();
        String firstSessionId = registry.createCompileSession("a.mc", "int main() { return 0; }").sessionId();
        String secondSessionId = registry.createCompileSession("b.mc", "int main() { return 1; }").sessionId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothCommandsEntered = new CountDownLatch(2);
        CountDownLatch releaseCommands = new CountDownLatch(1);
        AtomicInteger activeCommands = new AtomicInteger();
        AtomicInteger maxActiveCommands = new AtomicInteger();

        try {
            Future<Integer> first = executor.submit(parallelCommand(registry, firstSessionId, activeCommands,
                    maxActiveCommands, bothCommandsEntered, releaseCommands));
            Future<Integer> second = executor.submit(parallelCommand(registry, secondSessionId, activeCommands,
                    maxActiveCommands, bothCommandsEntered, releaseCommands));

            assertThat(bothCommandsEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(maxActiveCommands).hasValue(2);

            releaseCommands.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void closeWaitsForRunningCommandBeforeRemovingSession() throws Exception {
        MiniCWebSessionRegistry registry = new MiniCWebSessionRegistry();
        String sessionId = registry.createCompileSession("main.mc", "int main() { return 0; }").sessionId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch commandEntered = new CountDownLatch(1);
        CountDownLatch releaseCommand = new CountDownLatch(1);

        try {
            Future<Integer> command = executor.submit(() -> registry.commandCompileSession(sessionId, api -> {
                commandEntered.countDown();
                await(releaseCommand);
                return 1;
            }));
            assertThat(commandEntered.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> close = executor.submit(() -> registry.closeCompileSession(sessionId));
            Thread.sleep(100);
            assertThat(close.isDone()).isFalse();

            releaseCommand.countDown();

            assertThat(command.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(close.get(5, TimeUnit.SECONDS)).isNotNull();
            assertThatThrownBy(() -> registry.commandCompileSession(sessionId, api -> 2))
                    .isInstanceOf(MiniCWebSessionRegistry.SessionNotFoundException.class);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void idleSessionsExpire() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-06-06T00:00:00Z"));
        MiniCWebSessionRegistry registry = new MiniCWebSessionRegistry(now::get);
        String compileSessionId = registry.createCompileSession("main.mc", "int main() { return 0; }").sessionId();
        String debugSessionId = registry.createDebugSession("debug.mc", "int main() { return 0; }").sessionId();

        now.set(Instant.parse("2026-06-06T01:00:00Z"));
        assertThat(registry.expireIdleSessions(Duration.ofMinutes(30))).isEqualTo(2);

        assertThatThrownBy(() -> registry.queryCompileSession(compileSessionId, api -> api))
                .isInstanceOf(MiniCWebSessionRegistry.SessionNotFoundException.class);
        assertThatThrownBy(() -> registry.queryDebugSession(debugSessionId, api -> api))
                .isInstanceOf(MiniCWebSessionRegistry.SessionNotFoundException.class);
    }

    @Test
    void idleExpiryDoesNotRemoveRunningSession() throws Exception {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-06-06T00:00:00Z"));
        MiniCWebSessionRegistry registry = new MiniCWebSessionRegistry(now::get);
        String sessionId = registry.createCompileSession("main.mc", "int main() { return 0; }").sessionId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch commandEntered = new CountDownLatch(1);
        CountDownLatch releaseCommand = new CountDownLatch(1);

        try {
            now.set(Instant.parse("2026-06-06T01:00:00Z"));
            Future<Integer> command = executor.submit(() -> registry.commandCompileSession(sessionId, api -> {
                commandEntered.countDown();
                await(releaseCommand);
                return 1;
            }));
            assertThat(commandEntered.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Integer> expired = executor.submit(() -> registry.expireIdleSessions(Duration.ofMinutes(30)));
            Thread.sleep(100);
            assertThat(expired.isDone()).isFalse();

            releaseCommand.countDown();

            assertThat(command.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(expired.get(5, TimeUnit.SECONDS)).isZero();
            Boolean sessionStillAvailable = registry.queryCompileSession(sessionId, api -> Boolean.TRUE);
            assertThat(sessionStillAvailable).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void publicApiDoesNotExposeInternalRuntimeSessions() {
        for (Method method : MiniCWebSessionRegistry.class.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            assertThat(method.toGenericString())
                    .doesNotContain("CompileObservation" + "Session")
                    .doesNotContain("minic.runtime.debug." + "Debug" + "Session");
        }
    }

    private static Callable<Integer> command(
            MiniCWebSessionRegistry registry,
            String sessionId,
            AtomicInteger activeCommands,
            AtomicInteger overlaps,
            CountDownLatch entered,
            CountDownLatch release
    ) {
        return () -> registry.commandCompileSession(sessionId, api -> {
            if (activeCommands.incrementAndGet() > 1) {
                overlaps.incrementAndGet();
            }
            entered.countDown();
            await(release);
            activeCommands.decrementAndGet();
            return 1;
        });
    }

    private static Callable<Integer> parallelCommand(
            MiniCWebSessionRegistry registry,
            String sessionId,
            AtomicInteger activeCommands,
            AtomicInteger maxActiveCommands,
            CountDownLatch entered,
            CountDownLatch release
    ) {
        return () -> registry.commandCompileSession(sessionId, api -> {
            int active = activeCommands.incrementAndGet();
            maxActiveCommands.accumulateAndGet(active, Math::max);
            entered.countDown();
            await(release);
            activeCommands.decrementAndGet();
            return 1;
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for latch", exception);
        }
    }
}
