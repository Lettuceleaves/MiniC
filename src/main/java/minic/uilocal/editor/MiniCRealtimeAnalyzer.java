package minic.uilocal;

import javafx.application.Platform;
import minic.uiapi.MiniCRealtimeAnalysisApi;
import minic.uiapi.UiRealtimeAnalysisDto;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * 后台实时 lexer/parser/semantic 分析器。
 */
public final class MiniCRealtimeAnalyzer implements AutoCloseable {
    private static final Duration DEFAULT_DEBOUNCE = Duration.ofMillis(300);

    private final AtomicReference<Request> latestRequest = new AtomicReference<>();
    private final AtomicLong sourceVersion = new AtomicLong();
    private final ResultSink resultSink;
    private final AnalysisEngine analysisEngine;
    private final ResultDispatcher resultDispatcher;
    private final long debounceMillis;
    private volatile boolean running = true;
    private volatile Thread worker;
    private long nextVersion;

    /**
     * 创建实时分析器。
     *
     * @param resultSink 结果回调
     */
    public MiniCRealtimeAnalyzer(ResultSink resultSink) {
        this(
                resultSink,
                new MiniCRealtimeAnalysisApi()::analyzeInterruptibly,
                Platform::runLater,
                DEFAULT_DEBOUNCE
        );
    }

    MiniCRealtimeAnalyzer(
            ResultSink resultSink,
            AnalysisEngine analysisEngine,
            ResultDispatcher resultDispatcher,
            Duration debounce
    ) {
        this.resultSink = Objects.requireNonNull(resultSink, "resultSink");
        this.analysisEngine = Objects.requireNonNull(analysisEngine, "analysisEngine");
        this.resultDispatcher = Objects.requireNonNull(resultDispatcher, "resultDispatcher");
        Objects.requireNonNull(debounce, "debounce");
        if (debounce.isNegative()) {
            throw new IllegalArgumentException("debounce must not be negative");
        }
        debounceMillis = debounce.toMillis();
    }

    /**
     * 提交一次编辑输入。
     *
     * @param sourceName 源码名称
     * @param sourceText 源码文本
     */
    public synchronized void submit(String sourceName, String sourceText) {
        if (!running) {
            return;
        }
        long version = ++nextVersion;
        Request request = new Request(sourceName, sourceText, version, monotonicMillis());
        latestRequest.set(request);
        sourceVersion.set(version);
        ensureStarted();
        LockSupport.unpark(worker);
    }

    @Override
    public synchronized void close() {
        running = false;
        latestRequest.set(null);
        LockSupport.unpark(worker);
    }

    private synchronized void ensureStarted() {
        if (worker != null) {
            return;
        }
        worker = new Thread(this::runLoop, "minic-realtime-analyzer");
        worker.setDaemon(true);
        worker.start();
    }

    private void runLoop() {
        long handledVersion = 0;
        while (running) {
            if (sourceVersion.get() == handledVersion) {
                parkUntilSourceChanges(handledVersion);
                continue;
            }
            Request latest = awaitLatestAfterQuietPeriod();
            if (latest == null) {
                continue;
            }
            long analyzingVersion = latest.version();
            BooleanSupplier invalidated = () -> !running || sourceVersion.get() != analyzingVersion;
            Optional<UiRealtimeAnalysisDto> completed = analysisEngine.analyze(
                    latest.sourceName(),
                    latest.sourceText(),
                    analyzingVersion,
                    invalidated
            );
            if (completed.isEmpty() || invalidated.getAsBoolean()) {
                continue;
            }
            UiRealtimeAnalysisDto result = completed.orElseThrow();
            handledVersion = analyzingVersion;
            latestRequest.compareAndSet(latest, null);
            resultDispatcher.dispatch(() -> {
                if (running && result.version() == sourceVersion.get()) {
                    resultSink.accept(result);
                }
            });
        }
    }

    private Request awaitLatestAfterQuietPeriod() {
        while (running) {
            long observedVersion = sourceVersion.get();
            Request latest = latestRequest.get();
            if (latest == null || latest.version() != observedVersion) {
                Thread.onSpinWait();
                continue;
            }
            long elapsedMillis = monotonicMillis() - latest.submittedAtMillis();
            long remainingMillis = debounceMillis - elapsedMillis;
            if (remainingMillis > 0) {
                LockSupport.parkNanos(this, TimeUnit.MILLISECONDS.toNanos(remainingMillis));
                continue;
            }
            if (sourceVersion.get() == observedVersion && latestRequest.get() == latest) {
                return latest;
            }
        }
        return null;
    }

    private void parkUntilSourceChanges(long handledVersion) {
        while (running && sourceVersion.get() == handledVersion) {
            LockSupport.park(this);
        }
    }

    private static long monotonicMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    /**
     * 实时分析结果回调。
     */
    @FunctionalInterface
    public interface ResultSink {
        /**
         * 接收分析结果。
         *
         * @param result 分析结果
         */
        void accept(UiRealtimeAnalysisDto result);
    }

    @FunctionalInterface
    interface AnalysisEngine {
        Optional<UiRealtimeAnalysisDto> analyze(
                String sourceName,
                String sourceText,
                long version,
                BooleanSupplier invalidated
        );
    }

    @FunctionalInterface
    interface ResultDispatcher {
        void dispatch(Runnable action);
    }

    private record Request(String sourceName, String sourceText, long version, long submittedAtMillis) {
        private Request {
            Objects.requireNonNull(sourceName, "sourceName");
            Objects.requireNonNull(sourceText, "sourceText");
        }
    }
}
