package craken.ui.component.editor;

/** 编辑器字体缩放状态：与 VS Code 一致，每级 10%，范围 50%～300%。由 EDT 持有。 */
final class UiEditorZoom {
    private double level;

    double level() {
        return level;
    }

    double factor() {
        return 1 + level * 0.1;
    }

    boolean setLevel(double requested) {
        if (!Double.isFinite(requested)) {
            throw new IllegalArgumentException("editor zoom level must be finite");
        }
        double next = Math.max(-5, Math.min(20, requested));
        if (next == level) {
            return false;
        }
        level = next;
        return true;
    }
}
