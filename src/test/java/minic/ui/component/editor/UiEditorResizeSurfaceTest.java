package minic.ui.component.editor;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.event.Event;
import javafx.geometry.Point3D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import minic.ui.component.UiStyles;
import minic.ui.display.DisplayArea;
import minic.ui.editor.EditorArea;
import minic.ui.frame.AppFrame;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** 需要桌面：以 -Dminic.ui.test=true 显式运行，普通编译器测试不启动窗口。 */
@EnabledIfSystemProperty(named = "minic.ui.test", matches = "true")
final class UiEditorResizeSurfaceTest {
    @BeforeAll
    static void startToolkit() throws Exception {
        var started = new CompletableFuture<Void>();
        Runnable ready = () -> {
            Platform.setImplicitExit(false);
            started.complete(null);
        };
        try {
            Platform.startup(ready);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(ready);
        }
        started.get(10, TimeUnit.SECONDS);
    }

    @ParameterizedTest(name = "visible editor render scale={0}")
    @ValueSource(doubles = {1.0, 1.25, 1.5, 2.0})
    @Timeout(20)
    void keepsThePaintedScrollbarAtTheBoundaryAndAcceptsInputDuringResize(double scale) throws Exception {
        verifyResize(false, scale);
    }

    @Test
    @Timeout(20)
    void synchronizesBothSplitEditorsAtFractionalRenderScale() throws Exception {
        verifyResize(true, 1.5);
    }

    private void verifyResize(boolean splitAtFractionalScale, double scale) throws Exception {
        var finished = new CompletableFuture<Void>();
        var fixture = new java.util.concurrent.atomic.AtomicReference<Fixture>();
        Platform.runLater(() -> {
            try {
                Fixture created = new Fixture(finished, splitAtFractionalScale, scale);
                fixture.set(created);
                created.checked(created::start);
            } catch (Throwable failure) {
                finished.completeExceptionally(failure);
            }
        });
        Throwable testFailure = null;
        try {
            finished.get(15, TimeUnit.SECONDS);
        } catch (Exception | Error problem) {
            testFailure = problem;
            throw problem;
        } finally {
            var cleaned = new CompletableFuture<Void>();
            Platform.runLater(() -> {
                Fixture created = fixture.get();
                if (created == null) cleaned.complete(null);
                else created.close(null).whenComplete((ignored, problem) -> {
                    if (problem == null) cleaned.complete(null);
                    else cleaned.completeExceptionally(problem);
                });
            });
            try {
                cleaned.get(3, TimeUnit.SECONDS);
            } catch (Exception | Error cleanupFailure) {
                if (testFailure == null) throw cleanupFailure;
                testFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    private static final class Fixture {
        private final CompletableFuture<Void> finished;
        private final EditorArea area = createArea();
        private final Region interactionArea = createInteractionPlaceholder();
        private final AppFrame frame = new AppFrame(area, new DisplayArea(), interactionArea, area.tabBar());
        private final Scene scene = new Scene(frame, 1280, 800);
        private final Stage stage = new Stage(StageStyle.UNDECORATED);
        private final SwingNode node = (SwingNode) area.editor().getChildrenUnmodifiable().getFirst();
        private final WritableImage pixels = new WritableImage(1600, 800);
        private final List<Double> layoutMillis = new ArrayList<>();
        private final List<UiCodeEditor> editors = new ArrayList<>();
        private final AtomicInteger maxScroll = new AtomicInteger();
        private final AtomicInteger horizontalScroll = new AtomicInteger();
        private boolean sampling;
        private boolean snapshotPending;
        private boolean checkingHorizontalScroll;
        private int samples;
        private int horizontalSamples;
        private int liveContentChanges;
        private int nativeStableSamples;
        private int thumbColor;
        private long layoutStarted;
        private Throwable failure;
        private final CompletableFuture<Void> cleanup = new CompletableFuture<>();
        private boolean closing;
        private Throwable cleanupFailure;
        private Rectangle contentPatch;
        private VisualChange visualChange;
        private String nativeEditBefore;

        private static Region createInteractionPlaceholder() {
            // This fixture verifies editor rendering/input, not terminal startup.
            // Preserve the real interaction area's layout constraints without a
            // second Swing surface, external shell, or delayed focus request.
            Region placeholder = new Region();
            placeholder.setMinSize(0, 0);
            placeholder.setPrefHeight(200);
            placeholder.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            placeholder.getStyleClass().add("interaction-area");
            return placeholder;
        }

        private static EditorArea createArea() {
            try {
                var file = java.nio.file.Files.createTempFile("minic-resize-", ".c");
                file.toFile().deleteOnExit();
                StringBuilder source = new StringBuilder();
                for (int line = 0; line < 120; line++) {
                    // Different visible rows make a viewport change observable in
                    // text pixels, rather than only in the scrollbar model.
                    source.append("int marker_").append(String.format("%03d", line))
                            .append(" = ").append(line).append("; // ")
                            .append("resize ".repeat(40)).append('\n');
                }
                java.nio.file.Files.writeString(file, source);
                EditorArea area = new EditorArea();
                area.openFile(file);
                return area;
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }

        Fixture(CompletableFuture<Void> finished, boolean splitAtFractionalScale, double scale) {
            this.finished = finished;
            editors.add(area.editor());
            if (splitAtFractionalScale) {
                var second = new UiCodeEditor(area.editor().text());
                area.workspace().primaryPane().splitRight(second);
                editors.add(second);
            }
            stage.setForceIntegerRenderScale(false);
            stage.setRenderScaleX(scale);
            stage.setRenderScaleY(scale);
        }

        void start() {
            UiStyles.install(scene);
            stage.setScene(scene);
            stage.setOnCloseRequest(event -> {
                event.consume();
                close(new AssertionError("fixture window closed before completion"));
            });
            stage.setOnHidden(event -> {
                if (!closing) Platform.runLater(() -> close(
                        new AssertionError("fixture window hidden before completion")));
            });
            stage.setX(-10000);
            stage.setY(-10000);
            stage.show();
            scene.addPreLayoutPulseListener(() -> layoutStarted = System.nanoTime());
            scene.addPostLayoutPulseListener(this::sample);
            after(700, () -> {
                area.editor().requestEditorFocus();
                var initial = scene.snapshot(null);
                UiCodeEditor primary = editors.getFirst();
                var bounds = primary.localToScene(primary.getLayoutBounds());
                thumbColor = initial.getPixelReader().getArgb((int) bounds.getMaxX() - 9,
                        (int) bounds.getMinY() + 45);
                assertNotEquals(0xff0d1117, thumbColor, "sample is the thumb, not editor background");
                sampling = true;
                SwingUtilities.invokeAndWait(() -> {
                    JScrollPane scrollPane = (JScrollPane) node.getContent();
                    scrollPane.getVerticalScrollBar().addAdjustmentListener(event ->
                            maxScroll.accumulateAndGet(event.getValue(), Math::max));
                    scrollPane.getHorizontalScrollBar().addAdjustmentListener(event ->
                            horizontalScroll.set(event.getValue()));
                    Rectangle viewport = SwingUtilities.convertRectangle(scrollPane.getViewport(),
                            new Rectangle(0, 0, scrollPane.getViewport().getWidth(),
                                    scrollPane.getViewport().getHeight()), scrollPane);
                    // Exclude the gutter, caret and scrollbars; width animation
                    // does not itself change this fixed left-hand text patch.
                    contentPatch = new Rectangle(viewport.x + 48, viewport.y + 3, 96, 48);
                });
                frame.collapseDisplayArea();
                after(65, () -> expectLiveContentChange("typed text", () -> type("z"),
                        () -> textComponent().getText().startsWith("z")));
                after(360, () -> {
                    assertTrue(area.editor().text().startsWith("z"), "typing is not blocked by the surface");
                    frame.expandDisplayArea();
                    after(100, () -> {
                        frame.restoreDefaultDisplayAreaWidth();
                        after(65, () -> expectLiveContentChange("wheel viewport", () -> Event.fireEvent(node,
                                new ScrollEvent(ScrollEvent.SCROLL, 200, 150, 200, 150,
                                        false, false, false, false, false, false, 0, -40, 0, -40,
                                        ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                                        ScrollEvent.VerticalTextScrollUnits.LINES, -3, 0,
                                        new PickResult(node, new Point3D(200, 150, 0), 0))),
                                () -> maxScroll.get() > 0));
                    });
                    after(460, () -> {
                        assertTrue(maxScroll.get() > 0, "wheel input reaches the live Swing editor");
                        assertEquals(2, liveContentChanges, "both updates must be visible before animation completion");
                        SwingUtilities.invokeAndWait(() -> {
                            for (UiCodeEditor editor : editors) {
                                SwingNode swing = (SwingNode) editor.getChildrenUnmodifiable().getFirst();
                                JScrollPane scrollPane = (JScrollPane) swing.getContent();
                                scrollPane.getHorizontalScrollBar().setValue(180);
                                assertTrue(scrollPane.getHorizontalScrollBar().getValue() > 0,
                                        "fixture must already be horizontally scrolled before resizing");
                            }
                        });
                        checkingHorizontalScroll = true;
                        stage.setWidth(1440);
                        after(150, () -> {
                            stage.setWidth(1040);
                            after(160, () -> {
                                frame.expandDisplayArea();
                                after(340, () -> {
                                    frame.restoreDefaultDisplayAreaWidth();
                                    after(360, this::verifyNativeHandoff);
                                });
                            });
                        });
                    });
                });
            });
        }

        private void sample() {
            if (!sampling || snapshotPending || failure != null || area.editor().getWidth() < 160) return;
            layoutMillis.add((System.nanoTime() - layoutStarted) / 1_000_000.0);
            snapshotPending = true;
            scene.snapshot(result -> {
                snapshotPending = false;
                checked(() -> {
                    if (!sampling || area.editor().getWidth() < 160) return;
                    for (UiCodeEditor editor : editors) {
                        if (editor.getWidth() < 160) continue;
                        SwingNode swing = (SwingNode) editor.getChildrenUnmodifiable().getFirst();
                        // SwingNode may retain a wider native backing surface.
                        // Only UiCodeEditor's clipped bounds are user-visible.
                        var bounds = editor.localToScene(editor.getLayoutBounds());
                        int expectedWidth = (int) Math.max(0, editor.getWidth()
                                - editor.getInsets().getLeft() - editor.getInsets().getRight());
                        int expectedHeight = (int) Math.max(0, editor.getHeight()
                                - editor.getInsets().getTop() - editor.getInsets().getBottom());
                        SwingUtilities.invokeAndWait(() -> {
                            JScrollPane scrollPane = (JScrollPane) swing.getContent();
                            assertEquals(expectedWidth, scrollPane.getWidth(),
                                    "Swing content width must follow the current visible FX editor, not host capacity");
                            assertEquals(expectedHeight, scrollPane.getHeight(),
                                    "Swing content height must follow the current visible FX editor");
                        });
                        int thumbPixels = 0;
                        for (int y = (int) bounds.getMinY() + 18; y < bounds.getMaxY() - 35; y++) {
                            if (result.getImage().getPixelReader().getArgb((int) bounds.getMaxX() - 9, y)
                                    == thumbColor) thumbPixels++;
                        }
                        assertTrue(thumbPixels >= 10,
                                "painted scrollbar must meet the current FX boundary (frame " + samples + ")");
                        samples++;
                    }
                    if (checkingHorizontalScroll && horizontalScroll.get() > 0 && isAnimating()) {
                        horizontalSamples++;
                    }
                    checkLiveContentChange(result.getImage());
                });
                return null;
            }, pixels);
        }

        private JTextComponent textComponent() {
            return (JTextComponent) ((JScrollPane) node.getContent()).getViewport().getView();
        }

        private void type(String character) {
            Event.fireEvent(node, new KeyEvent(KeyEvent.KEY_TYPED, character, character,
                    KeyCode.UNDEFINED, false, false, false, false));
        }

        private void expectLiveContentChange(String description, Runnable input, BooleanSupplier applied) {
            assertNull(visualChange, "previous content update must have been painted");
            scene.snapshot(result -> {
                checked(() -> {
                    assertTrue(isAnimating(), description + " must be injected during animation");
                    VisualChange expected = new VisualChange(description, signature(result.getImage()));
                    visualChange = expected;
                    input.run();
                    awaitSwingInput(expected, applied, 0);
                    after(180, () -> assertNotSame(expected, visualChange,
                            description + " must update text pixels during the animation, not after it"));
                });
                return null;
            }, null);
        }

        private void awaitSwingInput(VisualChange expected, BooleanSupplier applied, int attempt) {
            // SwingNode forwards input asynchronously. A bounded queue/paint poll
            // avoids assuming the first FX snapshot already contains the edit.
            SwingUtilities.invokeLater(() -> {
                try {
                    if (applied.getAsBoolean()) {
                        expected.applied = true;
                    } else {
                        Platform.runLater(() -> checked(() -> {
                            if (visualChange != expected) return;
                            assertTrue(attempt < 10, expected.description + " did not reach Swing");
                            after(12, () -> awaitSwingInput(expected, applied, attempt + 1));
                        }));
                    }
                } catch (Throwable problem) {
                    Platform.runLater(() -> checked(() -> { throw new AssertionError(problem); }));
                }
            });
        }

        private void checkLiveContentChange(Image image) {
            VisualChange expected = visualChange;
            if (expected == null || !expected.applied) return;
            if (signature(image) != expected.before) {
                assertTrue(isAnimating(), expected.description + " must become visible before animation ends");
                liveContentChanges++;
                visualChange = null;
            }
        }

        private boolean isAnimating() {
            try {
                // Observe the genuine animation; no duplicate timer can silently
                // extend the deadline past the actual 240ms width animation.
                var controllerField = AppFrame.class.getDeclaredField("displayWidthController");
                controllerField.setAccessible(true);
                Object controller = controllerField.get(frame);
                var animationField = controller.getClass().getDeclaredField("animation");
                animationField.setAccessible(true);
                javafx.animation.Animation animation = (javafx.animation.Animation) animationField.get(controller);
                return animation != null && animation.getStatus() == javafx.animation.Animation.Status.RUNNING;
            } catch (ReflectiveOperationException problem) {
                throw new AssertionError("cannot observe the width animation", problem);
            }
        }

        private long signature(Image image) {
            var origin = node.localToScene(contentPatch.x, contentPatch.y);
            int left = (int) Math.round(origin.getX());
            int top = (int) Math.round(origin.getY());
            UiCodeEditor primary = editors.getFirst();
            var visible = primary.localToScene(primary.getLayoutBounds());
            assertTrue(left >= visible.getMinX() && top >= visible.getMinY()
                            && left + contentPatch.width <= visible.getMaxX()
                            && top + contentPatch.height <= visible.getMaxY(),
                    "fixed text patch must remain inside the visible editor");
            long hash = 0xcbf29ce484222325L;
            for (int y = top; y < top + contentPatch.height; y++) {
                for (int x = left; x < left + contentPatch.width; x++) {
                    hash = (hash ^ image.getPixelReader().getArgb(x, y)) * 0x100000001b3L;
                }
            }
            return hash;
        }

        private void verifyNativeHandoff() throws Exception {
            assertTrue(horizontalSamples >= 2,
                    "verify scrollbar alignment during animation with a nonzero horizontal viewport: " + horizontalSamples);
            checkingHorizontalScroll = false;
            assertNativeSurfaceHidden();
            stage.requestFocus();
            editors.getFirst().requestEditorFocus();
            SwingUtilities.invokeAndWait(() -> {
                JTextComponent text = textComponent();
                text.setCaretPosition(0);
                JScrollPane scrollPane = (JScrollPane) node.getContent();
                scrollPane.getHorizontalScrollBar().setValue(0);
                scrollPane.getVerticalScrollBar().setValue(0);
            });
            after(90, () -> awaitNativeFocus(0));
        }

        private void awaitNativeFocus(int attempt) throws Exception {
            if (!nativeFocusReady()) {
                retryNativeFocus(attempt);
                return;
            }
            scene.snapshot(result -> {
                checked(() -> {
                    // Snapshot completion is asynchronous too: do not dispatch a
                    // synthetic AWT key after the offscreen test window lost focus.
                    if (!nativeFocusReady()) {
                        retryNativeFocus(attempt);
                        return;
                    }
                    assertNativeSurfaceHidden();
                    long before = signature(result.getImage());
                    nativeEditBefore = nativeEditDiagnostics(before, before);
                    type("Q");
                    awaitNativeEdit(before, 0);
                });
                return null;
            }, null);
        }

        private boolean nativeFocusReady() throws Exception {
            boolean[] swingFocused = new boolean[1];
            SwingUtilities.invokeAndWait(() -> swingFocused[0] = textComponent().isFocusOwner());
            return stage.isFocused() && node.isFocused() && swingFocused[0];
        }

        private void retryNativeFocus(int attempt) throws Exception {
            if (attempt >= 25) {
                String details = nativeEditDiagnostics(0, 0);
                System.err.println("Native handoff focus prerequisite failure (hashes not sampled): " + details);
                fail("focus prerequisite failed before native-handoff input: "
                        + "Stage, SwingNode and Swing text component must all own focus\n" + details);
            }
            stage.requestFocus();
            editors.getFirst().requestEditorFocus();
            after(20, () -> awaitNativeFocus(attempt + 1));
        }

        private void awaitNativeEdit(long before, int attempt) {
            after(25, () -> scene.snapshot(result -> {
                checked(() -> {
                    assertNativeSurfaceHidden();
                    long current = signature(result.getImage());
                    if (area.editor().text().startsWith("Qz") && current != before) {
                        verifyStableNativePixels(current, 0);
                    } else {
                        if (attempt >= 12) {
                            String details = "before input: " + nativeEditBefore
                                    + "\nafter bounded retries: " + nativeEditDiagnostics(before, current);
                            System.err.println("Native handoff pixel failure:\n" + details);
                            fail("editing after native handoff must update visible text pixels\n" + details);
                        }
                        awaitNativeEdit(before, attempt + 1);
                    }
                });
                return null;
            }, null));
        }

        private String nativeEditDiagnostics(long before, long current) throws Exception {
            StringBuilder state = new StringBuilder();
            state.append("editors=").append(editors.size())
                    .append(", beforeHash=").append(Long.toUnsignedString(before, 16))
                    .append(", currentHash=").append(Long.toUnsignedString(current, 16))
                    .append(", surfaceVisible=").append(editors.getFirst()
                            .getChildrenUnmodifiable().get(1).isVisible())
                    .append(", nodeFocused=").append(node.isFocused())
                    .append(", stageFocused=").append(stage.isFocused())
                    .append(", sceneFocusOwner=").append(scene.getFocusOwner())
                    .append(", activeIsPrimary=").append(area.editor() == editors.getFirst())
                    .append(", nodeVisible=").append(node.isVisible())
                    .append(", nodeSize=").append(node.getLayoutBounds().getWidth())
                    .append('x').append(node.getLayoutBounds().getHeight())
                    .append(", visibleEditorSize=").append(editors.getFirst().getWidth())
                    .append('x').append(editors.getFirst().getHeight())
                    .append(", renderScale=").append(stage.getRenderScaleX())
                    .append('x').append(stage.getRenderScaleY())
                    .append(", patch=").append(contentPatch)
                    .append(", patchSceneOrigin=").append(node.localToScene(contentPatch.x, contentPatch.y));
            SwingUtilities.invokeAndWait(() -> {
                JTextComponent text = textComponent();
                JScrollPane scrollPane = (JScrollPane) node.getContent();
                String source = text.getText();
                var position = scrollPane.getViewport().getViewPosition();
                int visibleOffset = text.viewToModel2D(new java.awt.Point(position.x + 48, position.y + 3));
                int safeOffset = Math.max(0, Math.min(source.length(), visibleOffset));
                state.append(", modelPrefix='").append(printable(source.substring(0, Math.min(64, source.length()))))
                        .append("', firstQ=").append(source.indexOf('Q'))
                        .append(", caret=").append(text.getCaretPosition())
                        .append(", selection=").append(text.getSelectionStart()).append(':').append(text.getSelectionEnd())
                        .append(", swingFocusOwner=").append(text.isFocusOwner())
                        .append(", swingShowing=").append(text.isShowing())
                        .append(", editable=").append(text.isEditable())
                        .append(", horizontal=").append(scrollPane.getHorizontalScrollBar().getValue())
                        .append(", vertical=").append(scrollPane.getVerticalScrollBar().getValue())
                        .append(", swingContentSize=").append(scrollPane.getWidth())
                        .append('x').append(scrollPane.getHeight())
                        .append(", viewportPosition=").append(position)
                        .append(", viewportBounds=").append(scrollPane.getViewport().getBounds())
                        .append(", patchStartModelOffset=").append(visibleOffset)
                        .append(", patchText='").append(printable(source.substring(safeOffset,
                                Math.min(source.length(), safeOffset + 32)))).append('\'');
            });
            return state.toString();
        }

        private static String printable(String text) {
            return text.replace("\r", "\\r").replace("\n", "\\n");
        }

        private void verifyStableNativePixels(long expected, int sample) {
            after(35, () -> scene.snapshot(result -> {
                checked(() -> {
                    assertNativeSurfaceHidden();
                    assertEquals(expected, signature(result.getImage()),
                            "native handoff must not flash back to stale pre-edit pixels");
                    nativeStableSamples++;
                    if (sample < 3) verifyStableNativePixels(expected, sample + 1);
                    else finish();
                });
                return null;
            }, null));
        }

        private void assertNativeSurfaceHidden() {
            for (UiCodeEditor editor : editors) {
                assertFalse(editor.getChildrenUnmodifiable().get(1).isVisible(),
                        "native painting must remain in control after animation settles");
            }
        }

        private void finish() {
            sampling = false;
            assertTrue(samples >= 12, "inspect resize frames and native-paint handoff frames: " + samples);
            assertEquals(2, liveContentChanges, "both in-animation content changes were painted");
            assertEquals(4, nativeStableSamples, "inspect several native frames after continuing to edit");
            for (UiCodeEditor editor : editors) {
                var surface = editor.getChildrenUnmodifiable().get(1);
                assertFalse(surface.isVisible(), "native painting takes over after resize settles");
                assertTrue(surface.isMouseTransparent(), "surface never intercepts editing input");
                assertFalse(surface.isManaged(), "surface never influences layout");
            }
            layoutMillis.sort(Double::compareTo);
            System.out.printf("Editor resize: %d pixel checks; layout median %.2fms, p95 %.2fms%n",
                    samples, layoutMillis.get(layoutMillis.size() / 2),
                    layoutMillis.get((int) (layoutMillis.size() * .95)));
            close(null);
        }

        private CompletableFuture<Void> close(Throwable problem) {
            if (!Platform.isFxApplicationThread()) throw new IllegalStateException("cleanup must start on FX");
            if (problem != null) {
                if (failure == null) failure = problem;
                else if (failure != problem) failure.addSuppressed(problem);
            }
            if (closing) return cleanup;
            closing = true;
            sampling = false;
            // Drain pending editor EDT work before JavaFX 21 queues the
            // WINDOW_HIDDEN native-handle callbacks; never race fixture teardown.
            SwingUtilities.invokeLater(() -> Platform.runLater(() -> {
                try {
                    stage.close();
                } catch (Throwable closeFailure) {
                    recordCleanupFailure(closeFailure);
                }
                // The test is not finished until the editor window-hide work
                // has crossed both queues too; the next fixture must not race it.
                SwingUtilities.invokeLater(() -> Platform.runLater(() -> {
                    if (cleanupFailure != null) {
                        if (failure == null) failure = cleanupFailure;
                        else if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
                        cleanup.completeExceptionally(cleanupFailure);
                    } else {
                        cleanup.complete(null);
                    }
                    if (failure == null) finished.complete(null);
                    else finished.completeExceptionally(failure);
                }));
            }));
            return cleanup;
        }

        private void recordCleanupFailure(Throwable problem) {
            if (cleanupFailure == null) cleanupFailure = problem;
            else if (cleanupFailure != problem) cleanupFailure.addSuppressed(problem);
        }

        private static final class VisualChange {
            final String description;
            final long before;
            volatile boolean applied;

            VisualChange(String description, long before) {
                this.description = description;
                this.before = before;
            }
        }

        private void after(double millis, Checked action) {
            var delay = new PauseTransition(Duration.millis(millis));
            delay.setOnFinished(event -> checked(action));
            delay.play();
        }

        private void checked(Checked action) {
            if (failure != null || closing) return;
            try {
                action.run();
            } catch (Throwable problem) {
                close(problem);
            }
        }
    }

    private interface Checked {
        void run() throws Exception;
    }
}
