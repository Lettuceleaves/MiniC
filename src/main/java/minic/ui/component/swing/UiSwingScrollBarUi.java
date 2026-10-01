package minic.ui.component.swing;

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
import java.util.function.BiConsumer;

/** 编辑器与终端共用的滚动条外观，不替换 JScrollBar 的模型与交互行为。 */
public final class UiSwingScrollBarUi extends BasicScrollBarUI {
    private final Color backgroundColor;
    private final Color normalThumbColor;
    private final Color hoverColor;
    private final Color activeColor;
    private final Color arrowColor;
    private final int thickness;
    private final BiConsumer<Graphics, Rectangle> trackDecoration;

    private UiSwingScrollBarUi(
            Color backgroundColor,
            Color normalThumbColor,
            Color hoverColor,
            Color activeColor,
            Color arrowColor,
            int thickness,
            BiConsumer<Graphics, Rectangle> trackDecoration
    ) {
        this.backgroundColor = backgroundColor;
        this.normalThumbColor = normalThumbColor;
        this.hoverColor = hoverColor;
        this.activeColor = activeColor;
        this.arrowColor = arrowColor;
        this.thickness = thickness;
        this.trackDecoration = trackDecoration;
    }

    public static void install(JScrollBar scrollBar, Color background, Color muted, Color foreground) {
        install(scrollBar, background, muted, foreground, null);
    }

    /** 附加标记绘制于轨道之上、滑块之下，例如终端的搜索结果位置。 */
    public static void install(JScrollBar scrollBar, Color background, Color muted, Color foreground,
                               BiConsumer<Graphics, Rectangle> trackDecoration) {
        Dimension preferredSize = scrollBar.getPreferredSize();
        int thickness = scrollBar.getOrientation() == JScrollBar.VERTICAL
                ? preferredSize.width
                : preferredSize.height;
        if (thickness <= 0) {
            thickness = 17;
        }

        scrollBar.setUI(new UiSwingScrollBarUi(
                background,
                blend(background, muted, 0.42),
                blend(background, muted, 0.64),
                blend(background, foreground, 0.58),
                muted,
                thickness,
                trackDecoration
        ));
        // 更换 UI delegate 后恢复原有外部尺寸，保留调用者的布局。
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
        ) {
            @Override
            public void paint(Graphics graphics) {
                // BasicArrowButton 只填充内部，EmptyBorder 留下的外圈也必须覆盖。
                Color previous = graphics.getColor();
                graphics.setColor(getBackground());
                graphics.fillRect(0, 0, getWidth(), getHeight());
                graphics.setColor(previous);
                super.paint(graphics);
            }
        };
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
        if (trackDecoration != null && !bounds.isEmpty()) {
            Graphics overlay = graphics.create();
            try {
                overlay.clipRect(bounds.x, bounds.y, bounds.width, bounds.height);
                trackDecoration.accept(overlay, bounds);
            } finally {
                overlay.dispose();
            }
        }
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
