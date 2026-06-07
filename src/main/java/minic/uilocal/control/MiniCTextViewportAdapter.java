package minic.uilocal.control;

import javafx.geometry.Point2D;
import minic.uilocal.MiniCCodeEditor;

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

    @Override
    public boolean isActiveFullyVisible() {
        return editor.isCurrentExecutionFullyVisible();
    }

    @Override
    public void centerActiveIfNeeded() {
        editor.centerCurrentExecutionIfNeeded();
    }

    @Override
    public void centerActive() {
        editor.centerCurrentExecution();
    }
}
