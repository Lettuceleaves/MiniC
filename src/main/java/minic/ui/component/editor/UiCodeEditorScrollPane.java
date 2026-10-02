package minic.ui.component.editor;

import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/** 连续缩放时，将同一次编辑器绘制同时提交给 JavaFX 覆盖层和 Swing 原生画面。 */
class UiCodeEditorScrollPane extends RTextScrollPane {
    // 只在同一个 EDT 调用栈内有效；普通输入、滚动和光标重绘不使用旧快照。
    private BufferedImage nativeResizeSnapshot;
    private double snapshotScaleX;
    private double snapshotScaleY;

    UiCodeEditorScrollPane(RTextArea textArea, boolean lineNumbers) {
        super(textArea, lineNumbers);
    }

    void paintResizeSnapshot(BufferedImage buffer, int width, int height,
                             double scaleX, double scaleY) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("editor resize painting must run on the Swing EDT");
        }
        Graphics2D graphics = buffer.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setColor(getBackground());
            graphics.fillRect(0, 0, (int) Math.ceil(width * scaleX), (int) Math.ceil(height * scaleY));
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.scale(scaleX, scaleY);
            graphics.clipRect(0, 0, width, height);
            // 绕过本类的临时快照分支；不透明内容本帧只完整绘制一次组件树。
            super.paint(graphics);
        } finally {
            graphics.dispose();
        }

        // 透明内容须走原生组件绘制，避免把快照预填背景再次叠到承载窗上。
        nativeResizeSnapshot = isOpaque() && getBackground() != null && getBackground().getAlpha() == 255
                ? buffer : null;
        snapshotScaleX = scaleX;
        snapshotScaleY = scaleY;
        try {
            // 保留 SwingNode 的原生脏区和像素提交协议；不透明内容仅复制图像。
            paintImmediately(0, 0, width, height);
        } finally {
            nativeResizeSnapshot = null;
        }
    }

    @Override
    public void paint(Graphics graphics) {
        if (nativeResizeSnapshot == null) {
            super.paint(graphics);
            return;
        }
        Graphics2D target = (Graphics2D) graphics.create();
        try {
            target.clipRect(0, 0, getWidth(), getHeight());
            if (isOpaque() && getBackground().getAlpha() == 255) target.setComposite(AlphaComposite.Src);
            // 抵消已存在的 DPI 变换，不把 ceil 后的物理像素压缩回整数逻辑尺寸。
            target.drawImage(nativeResizeSnapshot,
                    AffineTransform.getScaleInstance(1 / snapshotScaleX, 1 / snapshotScaleY), null);
        } finally {
            target.dispose();
        }
    }
}
