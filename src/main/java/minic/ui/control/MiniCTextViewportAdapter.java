package minic.ui.control;

import javafx.geometry.Point2D;
import minic.ui.MiniCCodeEditor;

import java.util.Objects;

public final class MiniCTextViewportAdapter implements MiniCViewportAdapter {
    private final MiniCCodeEditor editor;

    public MiniCTextViewportAdapter(MiniCCodeEditor editor) {
        this.editor = Objects.requireNonNull(editor, "editor");
    }

    @Override
    public MiniCControlTargetType type() {
        return MiniCControlTargetType.TEXT;
    }

    @Override
    public boolean canZoom() {
        return true;
    }

    @Override
    public void zoomAt(Point2D localPoint, double delta) {
        editor.zoomDisplayBy(delta);
    }

    @Override
    public boolean canScrollVertical() {
        return true;
    }

    @Override
    public void scrollVertical(double delta) {
        editor.scrollVerticalBy(delta);
    }

}
