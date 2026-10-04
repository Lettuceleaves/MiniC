package craken.ui.component.editor;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.function.Consumer;

/** 在 RSyntaxTextArea 完成文本绘制后追加编辑器装饰层。 */
final class UiCodeEditorTextArea extends RSyntaxTextArea {
    private Graphics2D fontMetricsGraphics;
    private Consumer<Graphics2D> overlayRenderer = graphics -> {
    };

    void setOverlayRenderer(Consumer<Graphics2D> overlayRenderer) {
        this.overlayRenderer = Objects.requireNonNull(overlayRenderer);
    }

    @Override
    public void setFont(Font font) {
        Graphics available = super.getGraphics();
        if (available != null) {
            available.dispose();
            super.setFont(font);
        } else if (isDisplayable()) {
            // 隐藏 Tab 或首次绘制前，SwingNode 有 peer 却没有 Graphics。
            // RSyntaxTextArea.setFont 仍会刷新字形度量：仅在这次调用内提供离屏
            // 度量上下文，不替代实际绘制，结束立即释放。
            fontMetricsGraphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
            try {
                super.setFont(font);
            } finally {
                fontMetricsGraphics.dispose();
                fontMetricsGraphics = null;
            }
        } else {
            super.setFont(font);
        }
    }

    @Override
    public Graphics getGraphics() {
        return fontMetricsGraphics != null ? fontMetricsGraphics : super.getGraphics();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D overlay = (Graphics2D) graphics.create();
        try {
            overlayRenderer.accept(overlay);
        } finally {
            overlay.dispose();
        }
    }
}
