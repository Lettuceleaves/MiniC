package craken.ui.component;

import javafx.geometry.Insets;
import javafx.scene.layout.Region;
import javafx.scene.transform.Shear;

/**
 * Craken UI 组件契约。
 *
 * <p>组件的尺寸、约束、padding、透明度和空间变换直接保存在组件继承的
 * JavaFX 属性中。该契约只补充 Region 没有直接 setter 的少量几何能力，
 * 不创建独立几何对象，也不保存组件默认值。</p>
 */
public interface UiComponent {
    Object RADIUS_KEY = new Object();
    Object BORDER_WIDTH_KEY = new Object();
    Object SHEAR_KEY = new Object();

    default void setRadius(double value) {
        region().getProperties().put(RADIUS_KEY, requireNonNegative(value, "radius"));
        updateInlineGeometryStyle();
    }

    default double getRadius() {
        return (double) region().getProperties().getOrDefault(RADIUS_KEY, 0d);
    }

    default void setBorderWidth(double value) {
        setBorderWidth(value, value, value, value);
    }

    default void setBorderWidth(double top, double right, double bottom, double left) {
        Insets width = new Insets(
                requireNonNegative(top, "borderWidth.top"),
                requireNonNegative(right, "borderWidth.right"),
                requireNonNegative(bottom, "borderWidth.bottom"),
                requireNonNegative(left, "borderWidth.left")
        );
        region().getProperties().put(BORDER_WIDTH_KEY, width);
        updateInlineGeometryStyle();
    }

    default Insets getBorderWidth() {
        return (Insets) region().getProperties().getOrDefault(BORDER_WIDTH_KEY, Insets.EMPTY);
    }

    default void setSkew(double x, double y) {
        Region node = region();
        Shear shear = (Shear) node.getProperties().computeIfAbsent(SHEAR_KEY, ignored -> new Shear());
        shear.setX(requireFinite(x, "skewX"));
        shear.setY(requireFinite(y, "skewY"));
        if ((x != 0 || y != 0) && !node.getTransforms().contains(shear)) {
            node.getTransforms().add(shear);
        } else if (x == 0 && y == 0) {
            node.getTransforms().remove(shear);
        }
    }

    default double getSkewX() {
        return shear().getX();
    }

    default double getSkewY() {
        return shear().getY();
    }

    private Region region() {
        if (this instanceof Region value) {
            return value;
        }
        throw new IllegalStateException("UiComponent implementations must extend Region");
    }

    private Shear shear() {
        return (Shear) region().getProperties().computeIfAbsent(SHEAR_KEY, ignored -> new Shear());
    }

    private void updateInlineGeometryStyle() {
        Region node = region();
        double radius = getRadius();
        StringBuilder style = new StringBuilder()
                .append("-fx-background-radius: ").append(radius)
                .append("; -fx-border-radius: ").append(radius).append(';');
        Insets border = (Insets) node.getProperties().get(BORDER_WIDTH_KEY);
        if (border != null) {
            style.append(" -fx-border-width: ")
                    .append(border.getTop()).append(' ')
                    .append(border.getRight()).append(' ')
                    .append(border.getBottom()).append(' ')
                    .append(border.getLeft()).append(';');
        }
        node.setStyle(style.toString());
    }

    private static double requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }
}
