package minic.runtime.debug.visual.layout;

/**
 * Measured node size in grid units.
 *
 * @param width width in grid units
 * @param height height in grid units
 */
public record NodeMeasure(int width, int height) {
    public NodeMeasure {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
    }
}
