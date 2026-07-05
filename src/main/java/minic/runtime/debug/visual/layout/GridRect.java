package minic.runtime.debug.visual.layout;

/**
 * Discrete grid rectangle.
 *
 * @param x left grid coordinate
 * @param y top grid coordinate
 * @param width width in grid units
 * @param height height in grid units
 */
public record GridRect(int x, int y, int width, int height) {
    public GridRect {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
    }

    public GridPoint top() {
        return new GridPoint(x + width / 2, y);
    }

    public GridPoint right() {
        return new GridPoint(x + width, y + height / 2);
    }

    public GridPoint bottom() {
        return new GridPoint(x + width / 2, y + height);
    }

    public GridPoint left() {
        return new GridPoint(x, y + height / 2);
    }
}
