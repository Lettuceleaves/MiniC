package minic.uilocal;

import javafx.application.Platform;
import minic.uiapi.MiniCRealtimeAnalysisApi;
import minic.uiapi.UiRealtimeAnalysisDto;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 后台实时 lexer/parser/semantic 分析器。
 */
public final class MiniCRealtimeAnalyzer implements AutoCloseable {
    private final BlockingQueue<Request> queue = new LinkedBlockingQueue<>();
    private final ResultSink resultSink;
    private final MiniCRealtimeAnalysisApi api = new MiniCRealtimeAnalysisApi();
    private volatile boolean running = true;
    private Thread worker;
    private long nextVersion;

    /**
     * 创建实时分析器。
     *
     * @param resultSink 结果回调
     */
    public MiniCRealtimeAnalyzer(ResultSink resultSink) {
        this.resultSink = Objects.requireNonNull(resultSink, "resultSink");
    }

    /**
     * 提交一次编辑输入。
     *
     * @param sourceName 源码名称
     * @param sourceText 源码文本
     */
    public void submit(String sourceName, String sourceText) {
        ensureStarted();
        queue.offer(new Request(sourceName, sourceText, ++nextVersion));
    }

    @Override
    public void close() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
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
        while (running) {
            try {
                Request request = queue.take();
                Request latest = drainLatest(request);
                UiRealtimeAnalysisDto result = api.analyze(latest.sourceName(), latest.sourceText(), latest.version());
                Platform.runLater(() -> resultSink.accept(result));
            } catch (InterruptedException exception) {
                if (!running) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private Request drainLatest(Request request) {
        Request latest = request;
        Request next;
        while ((next = queue.poll()) != null) {
            latest = next;
        }
        return latest;
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

    private record Request(String sourceName, String sourceText, long version) {
        private Request {
            Objects.requireNonNull(sourceName, "sourceName");
            Objects.requireNonNull(sourceText, "sourceText");
        }
    }
}
