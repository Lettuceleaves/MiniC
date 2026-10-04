package craken.ui.component.editor;

import javax.swing.ScrollPaneLayout;
import java.awt.Container;
import java.awt.Rectangle;

/** 让水平滚动条占满右下角，不保留 JScrollPane 的空白 corner。 */
final class UiCodeEditorScrollPaneLayout extends ScrollPaneLayout {
    @Override
    public void layoutContainer(Container parent) {
        super.layoutContainer(parent);
        if (hsb == null || vsb == null || !hsb.isVisible() || !vsb.isVisible()) {
            return;
        }

        Rectangle horizontal = hsb.getBounds();
        Rectangle vertical = vsb.getBounds();
        if (vertical.x >= horizontal.x) {
            int right = vertical.x + vertical.width;
            hsb.setBounds(
                    horizontal.x,
                    horizontal.y,
                    right - horizontal.x,
                    horizontal.height
            );
        } else {
            int right = horizontal.x + horizontal.width;
            hsb.setBounds(
                    vertical.x,
                    horizontal.y,
                    right - vertical.x,
                    horizontal.height
            );
        }
    }
}
