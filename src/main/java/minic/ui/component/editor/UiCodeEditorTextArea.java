package minic.ui.component.editor;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Objects;
import java.util.function.Consumer;

/** 在 RSyntaxTextArea 完成文本绘制后追加编辑器装饰层。 */
final class UiCodeEditorTextArea extends RSyntaxTextArea {
    private Consumer<Graphics2D> overlayRenderer = graphics -> {
    };

    void setOverlayRenderer(Consumer<Graphics2D> overlayRenderer) {
        this.overlayRenderer = Objects.requireNonNull(overlayRenderer);
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
