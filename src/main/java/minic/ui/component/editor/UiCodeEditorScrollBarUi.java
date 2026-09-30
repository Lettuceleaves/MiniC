package minic.ui.component.editor;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;

/** VS Code 风格的编辑器滚动条外观，不替换 JScrollBar 的模型与交互行为。 */
final class UiCodeEditorScrollBarUi extends BasicScrollBarUI {
    private final Color backgroundColor;
    private final Color normalThumbColor;
    private final Color hoverColor;
    private final Color activeColor;
    private final Color arrowColor;
    private final int thickness;

    private UiCodeEditorScrollBarUi(
            Color backgroundColor,
            Color normalThumbColor,
            Color hoverColor,
            Color activeColor,
            Color arrowColor,
            int thickness
    ) {
        this.backgroundColor = backgroundColor;
        this.normalThumbColor = normalThumbColor;
        this.hoverColor = hoverColor;
        this.activeColor = activeColor;
        this.arrowColor = arrowColor;
        this.thickness = thickness;
    }

    static void install(JScrollBar scrollBar, Color background, Color muted, Color foreground) {
        Dimension preferredSize = scrollBar.getPreferredSize();
        int thickness = scrollBar.getOrientation() == JScrollBar.VERTICAL
                ? preferredSize.width
                : preferredSize.height;
        if (thickness <= 0) {
            thickness = 17;
        }

        scrollBar.setUI(new UiCodeEditorScrollBarUi(
                background,
                blend(background, muted, 0.42),
                blend(background, muted, 0.64),
                blend(background, foreground, 0.58),
                muted,
                thickness
        ));
        // 更换 UI delegate 后恢复原有外部尺寸；编辑器的 viewport 布局保持不变。
        scrollBar.setPreferredSize(preferredSize);
        scrollBar.setBackground(background);
        scrollBar.setForeground(muted);
        scrollBar.setBorder(BorderFactory.createEmptyBorder());
        scrollBar.setOpaque(true);
    }

    @Override
    protected void configureScrollBarColors() {
        trackColor = backgroundColor;
        thumbColor = normalThumbColor;
        trackHighlightColor = backgroundColor;
        thumbDarkShadowColor = normalThumbColor;
        thumbHighlightColor = normalThumbColor;
        thumbLightShadowColor = normalThumbColor;
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return createArrowButton(orientation);
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return createArrowButton(orientation);
    }

    private JButton createArrowButton(int orientation) {
        BasicArrowButton button = new BasicArrowButton(
                orientation,
                backgroundColor,
                backgroundColor,
                arrowColor,
                backgroundColor
        );
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setFocusable(false);
        button.setOpaque(true);
        button.setPreferredSize(new Dimension(thickness, thickness));
        return button;
    }

    @Override
    protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
        graphics.setColor(backgroundColor);
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
        if (!scrollbar.isEnabled() || bounds.isEmpty()) {
            return;
        }

        Color color = scrollbar.getValueIsAdjusting()
                ? activeColor
                : isThumbRollover() ? hoverColor : normalThumbColor;
        graphics.setColor(color);
        if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
            graphics.fillRect(bounds.x + 2, bounds.y, Math.max(1, bounds.width - 4), bounds.height);
        } else {
            graphics.fillRect(bounds.x, bounds.y + 2, bounds.width, Math.max(1, bounds.height - 4));
        }
    }

    private static Color blend(Color background, Color foreground, double amount) {
        double inverse = 1 - amount;
        return new Color(
                (int) Math.round(background.getRed() * inverse + foreground.getRed() * amount),
                (int) Math.round(background.getGreen() * inverse + foreground.getGreen() * amount),
                (int) Math.round(background.getBlue() * inverse + foreground.getBlue() * amount)
        );
    }
}
