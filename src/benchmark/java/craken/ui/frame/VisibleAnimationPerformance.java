package craken.ui.frame;

import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import craken.ui.component.UiStyles;
import craken.ui.component.editor.UiCodeEditor;
import craken.ui.component.layout.UiWorkspace;
import craken.ui.display.DisplayArea;
import craken.ui.interaction.InteractionArea;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Diagnostic only: measures FX post-layout pulse cadence, not GPU/DWM present FPS.
 * Reproducible headful benchmark of the production editor without experimental switches.
 * No screenshots, per-frame console output, forced pulses, or file lifecycle work.
 * Default: app-with-terminal (real editor and PowerShell/JediTerm interaction area).
 * Arguments: --empty (both areas FX-only), --editor-only (FX interaction placeholder),
 * --offscreen, --split, --scale=1.5.
 * Timing controls: -Dprobe.warmup=6 -Dprobe.measured=18 -Dprobe.initialDelay=800.
 * Optional -Dprobe.renderTiming=true also observes completed JavaFX render jobs.
 * It requires --add-exports=javafx.graphics/com.sun.javafx.perf=ALL-UNNAMED.
 */
public final class VisibleAnimationPerformance {
    private static final int WARMUP = Integer.getInteger("probe.warmup", 6);
    private static final int MEASURED = Integer.getInteger("probe.measured", 18);
    private static final boolean RENDER_TIMING = Boolean.getBoolean("probe.renderTiming");
    private static final CompletableFuture<Void> DONE = new CompletableFuture<>();
    private static volatile VisibleAnimationPerformance instance;
    private final List<Sample> samples = new ArrayList<>();
    private final ConcurrentLinkedQueue<Long> renderedTimestamps = new ConcurrentLinkedQueue<>();
    private final java.util.Set<String> renderThreads = ConcurrentHashMap.newKeySet();
    private final Options options;
    private final DisplayArea display = new DisplayArea();
    private Stage stage;
    private Scene scene;
    private AppFrame frame;
    private InteractionArea interactionArea;
    private SplitPane content;
    private Sample active;
    private long layoutStarted;
    private int iteration;
    private boolean closed;
    private final CompletableFuture<Void> uiCleanup = new CompletableFuture<>();
    private boolean cleanupStarted;
    private Throwable cleanupFailure;

    private VisibleAnimationPerformance(Options options) {
        this.options = options;
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> DONE.completeExceptionally(failure));
        int exitCode = 0;
        boolean cleanupCompleted = false;
        try {
            Options options = Options.parse(args);
            Platform.startup(() -> {
                try {
                    Platform.setImplicitExit(false);
                    instance = new VisibleAnimationPerformance(options);
                    instance.start();
                } catch (Throwable failure) {
                    DONE.completeExceptionally(failure);
                }
            });
            DONE.get(120, TimeUnit.SECONDS);
        } catch (Throwable failure) {
            failure.printStackTrace();
            exitCode = 1;
        } finally {
            try {
                shutdownOwnedResources();
                cleanupCompleted = true;
            } catch (Throwable failure) {
                failure.printStackTrace();
                exitCode = 1;
            }
            Platform.exit();
        }
        // Only terminate the EDT after our terminal has actually finished cleanup.
        if (cleanupCompleted) System.exit(exitCode);
        else System.err.println("Cleanup incomplete: not forcing JVM exit over a live terminal cleanup task.");
    }

    private static void shutdownOwnedResources() throws Exception {
        VisibleAnimationPerformance probe = instance;
        if (probe == null) return;
        var closedOnFx = new CompletableFuture<Void>();
        Platform.runLater(() -> probe.closeOwnedUi().whenComplete((ignored, failure) -> {
            if (failure == null) closedOnFx.complete(null);
            else closedOnFx.completeExceptionally(failure);
        }));
        closedOnFx.get(10, TimeUnit.SECONDS);
        // The UI future includes both EDT barriers. Now await this JVM's
        // asynchronous PowerShellSession.close workers before System.exit.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (!thread.getName().equals("craken-powershell-close")) continue;
            long remaining = deadline - System.nanoTime();
            if (remaining > 0) thread.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
            if (thread.isAlive()) throw new IllegalStateException("terminal cleanup did not finish before exit");
        }
    }

    private CompletableFuture<Void> closeOwnedUi() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("cleanup must start on FX");
        if (cleanupStarted) return uiCleanup;
        cleanupStarted = true;
        closed = true;
        active = null;
        try {
            if (interactionArea != null) interactionArea.close();
        } catch (Throwable failure) {
            recordCleanupFailure(failure);
        }
        // JavaFX 21's WINDOW_HIDDEN callback dereferences lwFrame later on the
        // EDT without another null check. Finish terminal scene-detach disposal
        // before hiding the Stage, rather than racing it in the same FX turn.
        javax.swing.SwingUtilities.invokeLater(() -> Platform.runLater(() -> {
            try {
                if (stage != null) stage.close();
            } catch (Throwable failure) {
                recordCleanupFailure(failure);
            }
            // Also finish the ordinary editor SwingNode's window-hide callbacks
            // and their return-to-FX work before reporting cleanup complete.
            javax.swing.SwingUtilities.invokeLater(() -> Platform.runLater(() -> {
                if (cleanupFailure == null) uiCleanup.complete(null);
                else uiCleanup.completeExceptionally(cleanupFailure);
            }));
        }));
        return uiCleanup;
    }

    private void recordCleanupFailure(Throwable failure) {
        if (cleanupFailure == null) cleanupFailure = failure;
        else if (cleanupFailure != failure) cleanupFailure.addSuppressed(failure);
    }

    private void completeAfterCleanup(Throwable failure) {
        closeOwnedUi().whenComplete((ignored, cleanupProblem) -> {
            Throwable problem = failure;
            if (problem == null) problem = cleanupProblem;
            else if (cleanupProblem != null && cleanupProblem != problem) problem.addSuppressed(cleanupProblem);
            if (problem == null) DONE.complete(null);
            else DONE.completeExceptionally(problem);
        });
    }

    private static Region placeholder(String background) {
        Region placeholder = new Region();
        placeholder.setMinSize(0, 0);
        placeholder.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        placeholder.setStyle("-fx-background-color: " + background + ";");
        return placeholder;
    }

    private void start() {
        Node editorSlot;
        if (options.empty) {
            editorSlot = placeholder("#0d1117");
        } else {
            String source = ("int marker = 123; // " + "resize ".repeat(40) + "\n").repeat(120);
            UiCodeEditor editor = new UiCodeEditor(source);
            UiWorkspace workspace = new UiWorkspace(editor);
            if (options.split) workspace.primaryPane().splitRight(new UiCodeEditor(source));
            editorSlot = workspace;
        }
        StackPane tabBar = new StackPane();
        tabBar.setMinHeight(36);
        tabBar.setPrefHeight(36);
        tabBar.setMaxHeight(36);
        tabBar.setStyle("-fx-background-color: #161b22;");
        Node interactionSlot;
        if (options.empty || options.editorOnly) {
            Region placeholder = placeholder("#161b22");
            placeholder.setPrefHeight(200);
            interactionSlot = placeholder;
        } else {
            interactionArea = new InteractionArea();
            interactionSlot = interactionArea;
        }
        frame = new AppFrame(editorSlot, display, interactionSlot, tabBar);
        scene = new Scene(frame, 1280, 800);
        scene.setFill(Color.TRANSPARENT);
        UiStyles.install(scene);
        stage = new Stage(StageStyle.TRANSPARENT);
        stage.setTitle("Craken — animation performance probe");
        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(600);
        if (options.scale != null) {
            stage.setForceIntegerRenderScale(false);
            stage.setRenderScaleX(options.scale);
            stage.setRenderScaleY(options.scale);
        }
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(frame.widthProperty());
        clip.heightProperty().bind(frame.heightProperty());
        clip.arcWidthProperty().bind(Bindings.when(stage.maximizedProperty().or(stage.fullScreenProperty()))
                .then(0.0).otherwise(16.0));
        clip.arcHeightProperty().bind(clip.arcWidthProperty());
        frame.setClip(clip);
        stage.setX(options.offscreen ? -10000 : 80);
        stage.setY(options.offscreen ? -10000 : 80);
        stage.setOnCloseRequest(event -> {
            event.consume();
            completeAfterCleanup(new IllegalStateException("probe window closed before completion"));
        });
        stage.setOnHidden(event -> {
            if (!closed) Platform.runLater(() -> completeAfterCleanup(
                    new IllegalStateException("probe window hidden before completion")));
        });
        scene.addPreLayoutPulseListener(() -> layoutStarted = System.nanoTime());
        scene.addPostLayoutPulseListener(() -> guarded(this::sample));
        if (RENDER_TIMING) installRenderTiming();
        stage.show();
        after(Integer.getInteger("probe.initialDelay", 800), () -> {
            content = (SplitPane) frame.lookup(".app-horizontal-split");
            if (content == null) throw new IllegalStateException("content SplitPane is missing");
            System.out.printf("FX POST-LAYOUT CADENCE ONLY: mode=%s%s, offscreen=%s, scene=%.0fx%.0f, "
                            + "outputScale=%.3fx%.3f, renderScale=%.3fx%.3f, targetDuration=240ms%n",
                    options.mode(), !options.empty && options.split ? "+split" : "", options.offscreen,
                    scene.getWidth(), scene.getHeight(), stage.getOutputScaleX(), stage.getOutputScaleY(),
                    stage.getRenderScaleX(), stage.getRenderScaleY());
            if (RENDER_TIMING) System.out.println("RENDER TIMING: PerformanceTracker frameRendered callback; "
                    + "completed render jobs, NOT GPU completion, pixel-upload completion or display/present FPS.");
            next();
        });
    }

    private void installRenderTiming() {
        try {
            Class<?> trackerClass = Class.forName("com.sun.javafx.perf.PerformanceTracker");
            Object tracker = trackerClass.getMethod("getSceneTracker", Scene.class).invoke(null, scene);
            if (tracker == null) throw new IllegalStateException("JavaFX did not provide a scene performance tracker");
            Runnable previous = (Runnable) trackerClass.getMethod("getOnRenderedFrameTask").invoke(tracker);
            Runnable listener = () -> {
                // Runs on the render-completion thread. Never touch scene graph,
                // FX-only sample state, or perform logging in this callback.
                long completed = System.nanoTime();
                renderedTimestamps.add(completed);
                renderThreads.add(Thread.currentThread().getName());
                if (previous != null) previous.run();
            };
            trackerClass.getMethod("setOnRenderedFrameTask", Runnable.class).invoke(tracker, listener);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Render timing needs JavaFX PerformanceTracker and "
                    + "--add-exports=javafx.graphics/com.sun.javafx.perf=ALL-UNNAMED", failure);
        }
    }

    private void next() {
        if (iteration == WARMUP + MEASURED) {
            finish();
            return;
        }
        int action = iteration % 3;
        String name = switch (action) { case 0 -> "collapse"; case 1 -> "expand"; default -> "default"; };
        double dividerWidth = content.lookupAll(".split-pane-divider").stream()
                .filter(node -> node.getParent() == content)
                .mapToDouble(node -> node.getLayoutBounds().getWidth()).findFirst().orElse(0);
        double maximum = content.getWidth() - content.getInsets().getLeft()
                - content.getInsets().getRight() - dividerWidth;
        double target = switch (action) { case 0 -> 0; case 1 -> maximum; default -> AppFrame.DEFAULT_DISPLAY_WIDTH; };
        active = new Sample(iteration + 1, name, target, System.nanoTime());
        iteration++;
        switch (action) {
            case 0 -> frame.collapseDisplayArea();
            case 1 -> frame.expandDisplayArea();
            default -> frame.restoreDefaultDisplayAreaWidth();
        }
        observeTimelineCompletion(active);
    }

    private void observeTimelineCompletion(Sample sample) {
        // Diagnostic-only observation: no production accessor, handler replacement,
        // timer or additional pulse is added to the implementation under measurement.
        try {
            var controllerField = AppFrame.class.getDeclaredField("displayWidthController");
            controllerField.setAccessible(true);
            Object controller = controllerField.get(frame);
            var animationField = DisplayPaneWidthController.class.getDeclaredField("animation");
            animationField.setAccessible(true);
            Timeline timeline = (Timeline) animationField.get(controller);
            if (timeline == null) throw new IllegalStateException("expected an active width animation");
            timeline.statusProperty().addListener((observable, previous, current) -> {
                if (current == Animation.Status.STOPPED) sample.timelineCompleted = System.nanoTime();
            });
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("unable to observe width-animation completion", failure);
        }
    }

    private void sample() {
        Sample current = active;
        if (current == null) return;
        long now = System.nanoTime();
        current.timestamps.add(now);
        current.layoutMillis.add((now - layoutStarted) / 1_000_000.0);
        long elapsed = now - current.started;
        if (elapsed > 2_000_000_000L) {
            throw new AssertionError("animation did not reach target: " + current.action
                    + ", expected=" + current.target + ", width=" + display.getWidth());
        }
        // Include the final layout of the genuine Timeline completion pulse. Pixel
        // rounding must not end sampling early, and idle pulses must not enter it.
        if (current.timelineCompleted != 0 && Math.abs(display.getWidth() - current.target) <= 1.0) {
            current.completed = now;
            current.finalWidth = display.getWidth();
            if (current.iteration > WARMUP) samples.add(current);
            active = null;
            // This idle gap is explicitly outside all interval/frame counts.
            after(90, this::next);
        }
    }

    private void finish() {
        List<Double> intervals = new ArrayList<>();
        List<Double> layouts = new ArrayList<>();
        double intervalTotal = 0;
        int frames = 0;
        for (Sample sample : samples) {
            List<Double> localIntervals = sample.intervals();
            intervals.addAll(localIntervals);
            layouts.addAll(sample.layoutMillis);
            double localTotal = localIntervals.stream().mapToDouble(Double::doubleValue).sum();
            intervalTotal += localTotal;
            frames += sample.timestamps.size();
            System.out.printf("animation=%02d action=%-8s frames=%2d intervals=%2d fps=%6.2f "
                            + "firstFrame=%.2fms timelineCompletion=%.2fms finalLayout=%.2fms width=%.1f intervalP95=%.2fms max=%.2fms%n",
                    sample.iteration - WARMUP, sample.action, sample.timestamps.size(), localIntervals.size(),
                    localTotal == 0 ? 0 : localIntervals.size() * 1000.0 / localTotal,
                    (sample.timestamps.getFirst() - sample.started) / 1_000_000.0,
                    (sample.timelineCompleted - sample.started) / 1_000_000.0,
                    (sample.completed - sample.started) / 1_000_000.0, sample.finalWidth,
                    percentile(localIntervals, .95), percentile(localIntervals, 1));
            System.out.printf("WINDOW animation=%02d startNanos=%d endNanos=%d%n",
                    sample.iteration - WARMUP, sample.started, sample.completed);
        }
        long over25 = intervals.stream().filter(value -> value > 25).count();
        long over33 = intervals.stream().filter(value -> value > 33.333333).count();
        System.out.printf("SUMMARY animations=%d warmup=%d frames=%d intervals=%d effectivePulseFPS=%.3f "
                        + "intervalMs[p50=%.3f,p95=%.3f,p99=%.3f,max=%.3f] over25ms=%d over33.333ms=%d%n",
                samples.size(), WARMUP, frames, intervals.size(), intervals.size() * 1000.0 / intervalTotal,
                percentile(intervals, .5), percentile(intervals, .95), percentile(intervals, .99),
                percentile(intervals, 1), over25, over33);
        System.out.printf("LAYOUT ms[p50=%.3f,p95=%.3f,p99=%.3f,max=%.3f]%n",
                percentile(layouts, .5), percentile(layouts, .95), percentile(layouts, .99), percentile(layouts, 1));
        if (RENDER_TIMING) printRenderTiming();
        System.out.println("Intervals are adjacent post-layout timestamps inside the same animation; "
                + "first-frame latency and completion are reported separately. No GPU/DWM present measurement.");
        completeAfterCleanup(null);
    }

    private void printRenderTiming() {
        // The last animation ended at least the normal 90ms gap ago. Read a
        // thread-safe snapshot, then classify by immutable animation windows on
        // the FX thread. Render callbacks never race ArrayList/sample mutation.
        List<Long> rendered = renderedTimestamps.stream().sorted().toList();
        List<Double> intervals = new ArrayList<>();
        int frameCount = 0;
        double intervalTotal = 0;
        for (Sample sample : samples) {
            List<Long> local = rendered.stream()
                    .filter(time -> time >= sample.started && time <= sample.completed).toList();
            List<Double> localIntervals = intervalsOf(local);
            intervals.addAll(localIntervals);
            frameCount += local.size();
            double localTotal = localIntervals.stream().mapToDouble(Double::doubleValue).sum();
            intervalTotal += localTotal;
            double firstAfterLayoutEnd = rendered.stream().filter(time -> time > sample.completed)
                    .findFirst().map(time -> (time - sample.completed) / 1_000_000.0).orElse(Double.NaN);
            System.out.printf("RENDER animation=%02d action=%-8s renderDoneFrames=%2d renderIntervals=%2d "
                            + "renderDoneFPS=%.3f intervalMs[p50=%.3f,p95=%.3f,p99=%.3f,max=%.3f] "
                            + "firstRenderAfterLayoutEndMs=%.3f%n",
                    sample.iteration - WARMUP, sample.action, local.size(), localIntervals.size(),
                    localTotal == 0 ? Double.NaN : localIntervals.size() * 1000.0 / localTotal,
                    percentile(localIntervals, .5), percentile(localIntervals, .95),
                    percentile(localIntervals, .99), percentile(localIntervals, 1), firstAfterLayoutEnd);
        }
        System.out.printf("RENDER_SUMMARY animations=%d renderDoneFrames=%d renderIntervals=%d renderDoneFPS=%.3f "
                        + "intervalMs[p50=%.3f,p95=%.3f,p99=%.3f,max=%.3f] over25ms=%d over33.333ms=%d threads=%s%n",
                samples.size(), frameCount, intervals.size(),
                intervalTotal == 0 ? Double.NaN : intervals.size() * 1000.0 / intervalTotal,
                percentile(intervals, .5), percentile(intervals, .95), percentile(intervals, .99),
                percentile(intervals, 1), intervals.stream().filter(value -> value > 25).count(),
                intervals.stream().filter(value -> value > 33.333333).count(), renderThreads);
        System.out.println("RENDER SCOPE: callback completion timestamps within each WINDOW startNanos/endNanos; "
                + "intervals never bridge idle gaps. Renders completing after a layout window are excluded from its FPS; "
                + "firstRenderAfterLayoutEndMs is diagnostic timing, not a claimed matching final frame. "
                + "Render-job completion is not GPU/DWM present or displayed-frame timing.");
    }

    private static List<Double> intervalsOf(List<Long> timestamps) {
        List<Double> result = new ArrayList<>();
        for (int index = 1; index < timestamps.size(); index++) {
            result.add((timestamps.get(index) - timestamps.get(index - 1)) / 1_000_000.0);
        }
        return result;
    }

    private static double percentile(List<Double> values, double quantile) {
        if (values.isEmpty()) return Double.NaN;
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return sorted[Math.max(0, Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * quantile) - 1))];
    }

    private void after(int millis, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(millis));
        pause.setOnFinished(event -> guarded(action));
        pause.play();
    }

    private void guarded(Runnable action) {
        if (closed || DONE.isDone()) return;
        try {
            action.run();
        } catch (Throwable failure) {
            completeAfterCleanup(failure);
        }
    }

    private static final class Sample {
        final int iteration;
        final String action;
        final double target;
        final long started;
        final List<Long> timestamps = new ArrayList<>(20);
        final List<Double> layoutMillis = new ArrayList<>(20);
        long completed;
        long timelineCompleted;
        double finalWidth;

        Sample(int iteration, String action, double target, long started) {
            this.iteration = iteration;
            this.action = action;
            this.target = target;
            this.started = started;
        }

        List<Double> intervals() {
            return intervalsOf(timestamps);
        }
    }

    private record Options(boolean empty, boolean editorOnly, boolean offscreen, boolean split, Double scale) {
        String mode() {
            return empty ? "empty-fx-only" : editorOnly ? "editor-only" : "app-with-terminal";
        }

        static Options parse(String[] args) {
            boolean empty = false, editorOnly = false, offscreen = false, split = false;
            Double scale = null;
            for (String argument : args) {
                if (argument.equals("--empty")) empty = true;
                else if (argument.equals("--editor-only")) editorOnly = true;
                else if (argument.equals("--offscreen")) offscreen = true;
                else if (argument.equals("--split")) split = true;
                else if (argument.startsWith("--scale=")) scale = Double.parseDouble(argument.substring(8));
                else throw new IllegalArgumentException("unknown argument " + argument + "; " + Arrays.toString(args));
            }
            if (scale != null && (!Double.isFinite(scale) || scale <= 0)) {
                throw new IllegalArgumentException("render scale must be positive and finite");
            }
            return new Options(empty, editorOnly, offscreen, split, scale);
        }
    }
}
