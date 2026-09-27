package minic.uilocal;

import minic.uiapi.UiRealtimeAnalysisDto;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCRealtimeAnalyzerTest {
    @Test
    void analyzesOnlyLatestRequestAfterQuietPeriod() throws Exception {
        List<String> analyzedSources = new CopyOnWriteArrayList<>();
        CountDownLatch delivered = new CountDownLatch(1);

        try (MiniCRealtimeAnalyzer analyzer = new MiniCRealtimeAnalyzer(
                result -> delivered.countDown(),
                (name, source, version, invalidated) -> {
                    analyzedSources.add(source);
                    return Optional.of(result(name, source, version));
                },
                Runnable::run,
                Duration.ofMillis(300)
        )) {
            analyzer.submit("test.mc", "first");
            Thread.sleep(75);
            analyzer.submit("test.mc", "second");
            Thread.sleep(75);
            analyzer.submit("test.mc", "latest");

            assertThat(delivered.await(150, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(delivered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(analyzedSources).containsExactly("latest");
        }
    }

    @Test
    void performsAnalysisOnDedicatedBackgroundThread() throws Exception {
        Thread submitThread = Thread.currentThread();
        AtomicReference<Thread> analysisThread = new AtomicReference<>();
        CountDownLatch delivered = new CountDownLatch(1);

        try (MiniCRealtimeAnalyzer analyzer = new MiniCRealtimeAnalyzer(
                result -> delivered.countDown(),
                (name, source, version, invalidated) -> {
                    analysisThread.set(Thread.currentThread());
                    return Optional.of(result(name, source, version));
                },
                Runnable::run,
                Duration.ZERO
        )) {
            analyzer.submit("test.mc", "int main() { return 0; }");

            assertThat(delivered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(analysisThread.get()).isNotSameAs(submitThread);
            assertThat(analysisThread.get().getName()).isEqualTo("minic-realtime-analyzer");
        }
    }

    @Test
    void dropsStaleResultAndKeepsLatestRequestWhileAnalysisIsRunning() throws Exception {
        List<String> analyzedSources = new CopyOnWriteArrayList<>();
        List<String> deliveredSources = new CopyOnWriteArrayList<>();
        CountDownLatch firstAnalysisStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstAnalysis = new CountDownLatch(1);
        CountDownLatch latestDelivered = new CountDownLatch(1);

        try (MiniCRealtimeAnalyzer analyzer = new MiniCRealtimeAnalyzer(
                result -> {
                    deliveredSources.add(result.sourceText());
                    latestDelivered.countDown();
                },
                (name, source, version, invalidated) -> {
                    analyzedSources.add(source);
                    if ("first".equals(source)) {
                        firstAnalysisStarted.countDown();
                        await(releaseFirstAnalysis);
                    }
                    return invalidated.getAsBoolean()
                            ? Optional.empty()
                            : Optional.of(result(name, source, version));
                },
                Runnable::run,
                Duration.ZERO
        )) {
            analyzer.submit("test.mc", "first");
            assertThat(firstAnalysisStarted.await(2, TimeUnit.SECONDS)).isTrue();

            analyzer.submit("test.mc", "second");
            analyzer.submit("test.mc", "latest");
            releaseFirstAnalysis.countDown();

            assertThat(latestDelivered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(analyzedSources).containsExactly("first", "latest");
            assertThat(deliveredSources).containsExactly("latest");
        }
    }

    private static UiRealtimeAnalysisDto result(String name, String source, long version) {
        return new UiRealtimeAnalysisDto(name, source, List.of(), List.of(), version);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
