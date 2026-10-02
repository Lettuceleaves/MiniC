package minic.ui.component.editor;

import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.Window;
import minic.ui.component.swing.UiSwingNodeSurface;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.reflect.InvocationTargetException;
import java.nio.IntBuffer;

/**
 * SwingNode 异步提交尺寸和像素；连续缩放时，旧滚动条会留在新边界以内。
 * 布局帧先绘制一张当前尺寸的完整 Swing 画面，原生重绘提交后再撤掉覆盖层。
 * 覆盖层不参与布局和拾取，不替换编辑器，也不冻结输入或缩放旧画面。
 */
final class UiEditorResizeSurface {
    private final SwingNode node;
    private final UiCodeEditorScrollPane content;
    private final ImageView surface = new ImageView();
    private BufferedImage buffer;
    private WritableImage image;
    private PixelBuffer<IntBuffer> pixelBuffer;
    private int width;
    private int height;
    private double scaleX;
    private double scaleY;
    private long generation;

    UiEditorResizeSurface(SwingNode node, UiCodeEditorScrollPane content) {
        this.node = node;
        this.content = content;
        surface.setManaged(false);
        surface.setMouseTransparent(true);
        surface.setSmooth(false);
        surface.setVisible(false);
        node.sceneProperty().addListener((observable, previous, current) -> {
            if (current != previous) {
                generation++;
                width = height = 0;
                surface.setVisible(false);
                surface.setImage(null);
                image = null;
                pixelBuffer = null;
                buffer = null;
                if (current == null) SwingUtilities.invokeLater(() -> UiSwingNodeSurface.release(content));
            }
        });
    }

    ImageView view() {
        return surface;
    }

    /** 在父组件的 layoutChildren 中调用，确保画面与 FX 边界进入同一次渲染。 */
    void layout() {
        if (node.getScene() == null || node.getContent() == null) {
            return;
        }
        Window window = node.getScene().getWindow();
        if (window == null || !window.isShowing()) {
            return;
        }
        int nextWidth = (int) node.getLayoutBounds().getWidth();
        int nextHeight = (int) node.getLayoutBounds().getHeight();
        double nextScaleX = window.getRenderScaleX();
        double nextScaleY = window.getRenderScaleY();
        surface.relocate(node.getLayoutX(), node.getLayoutY());
        if (nextWidth == width && nextHeight == height
                && nextScaleX == scaleX && nextScaleY == scaleY) {
            return;
        }
        generation++;
        width = nextWidth;
        height = nextHeight;
        scaleX = nextScaleX;
        scaleY = nextScaleY;
        if (width <= 0 || height <= 0) {
            surface.setVisible(false);
            return;
        }

        int pixelWidth = (int) Math.ceil(width * scaleX);
        int pixelHeight = (int) Math.ceil(height * scaleY);
        // 复用容量，避免每个动画帧分配一对大图；实际显示范围由 viewport 限定。
        if (buffer == null || buffer.getWidth() < pixelWidth || buffer.getHeight() < pixelHeight) {
            int capacityWidth = buffer == null ? pixelWidth : Math.max(pixelWidth, buffer.getWidth() * 6 / 5);
            int capacityHeight = buffer == null ? pixelHeight : Math.max(pixelHeight, buffer.getHeight());
            buffer = new BufferedImage(capacityWidth, capacityHeight, BufferedImage.TYPE_INT_ARGB_PRE);
        }
        Capture capture = new Capture(this, node.getScene(), buffer, width, height, scaleX, scaleY, generation);
        try {
            SwingUtilities.invokeAndWait(capture::paintOnSwingThread);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while painting editor resize", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("failed to paint editor resize", exception.getCause());
        }
        capture.publishOnFxThread();
    }

    private void publish(Capture capture) {
        if (generation != capture.generation() || node.getScene() != capture.scene()
                || node.getContent() != content) {
            return;
        }
        BufferedImage painted = capture.buffer();
        int pixelWidth = (int) Math.ceil(capture.width() * capture.scaleX());
        int pixelHeight = (int) Math.ceil(capture.height() * capture.scaleY());
        if (image == null || image.getWidth() < pixelWidth || image.getHeight() < pixelHeight) {
            pixelBuffer = new PixelBuffer<>(painted.getWidth(), painted.getHeight(),
                    IntBuffer.allocate(painted.getWidth() * painted.getHeight()), PixelFormat.getIntArgbPreInstance());
            image = new WritableImage(pixelBuffer);
            surface.setImage(image);
        }
        // FX 回调内更新独立的呈现缓冲，不让 EDT 与 Prism 并发读写同一数组。
        // 保持 INT_ARGB_PRE，避开普通 WritableImage 的逐像素 int→byte 格式转换。
        // 容量余量不在 viewport 内，无需逐帧清理或复制。
        int[] pixels = ((DataBufferInt) painted.getRaster().getDataBuffer()).getData();
        pixelBuffer.updateBuffer(target -> {
            int[] destination = target.getBuffer().array();
            for (int row = 0; row < pixelHeight; row++) {
                System.arraycopy(pixels, row * painted.getWidth(), destination,
                        row * target.getWidth(), pixelWidth);
            }
            return new Rectangle2D(0, 0, pixelWidth, pixelHeight);
        });
        surface.setViewport(new Rectangle2D(0, 0, pixelWidth, pixelHeight));
        // 保持物理像素比例；非整数 DPI 下也不横向挤压或拉伸字体。
        surface.setFitWidth(pixelWidth / capture.scaleX());
        surface.setFitHeight(pixelHeight / capture.scaleY());
        surface.setVisible(true);
    }

    record Capture(UiEditorResizeSurface owner, Scene scene, BufferedImage buffer,
                   int width, int height, double scaleX, double scaleY, long generation) {
        void paintOnSwingThread() {
            UiCodeEditorScrollPane content = owner.content;
            UiSwingNodeSurface.prepare(content);
            // 所有 SwingNode.resize 已在 FX 布局期间先向 EDT 提交 setBounds。
            java.awt.Window embeddedWindow = SwingUtilities.getWindowAncestor(content);
            if (embeddedWindow != null) embeddedWindow.validate();
            content.paintResizeSnapshot(buffer, width, height, scaleX, scaleY);
            // native blit 和捕获仍在同一 EDT 调用栈；通知队列完成后才交回原生画面。
            SwingUtilities.invokeLater(() -> Platform.runLater(() -> {
                if (owner.generation == generation && owner.node.getScene() == scene) {
                    owner.surface.setVisible(false);
                }
            }));
        }

        void publishOnFxThread() {
            owner.publish(this);
        }
    }
}
