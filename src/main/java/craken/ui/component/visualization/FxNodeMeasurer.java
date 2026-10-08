package craken.ui.component.visualization;

import craken.visualization.api.ViewLocation;
import craken.visualization.layout.LayoutRequest;
import craken.visualization.model.ViewNode;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import java.util.*;

public final class FxNodeMeasurer {
    private final ViewNodeRenderer renderer;
    public FxNodeMeasurer() { this(new ViewNodeRenderer()); }
    public FxNodeMeasurer(Font font) { this(new ViewNodeRenderer(font)); }
    public FxNodeMeasurer(ViewNodeRenderer renderer) { this.renderer = Objects.requireNonNull(renderer); }
    public LayoutRequest.Unit measure(ViewNode owner, Map<Long, ViewNode> pageNodes, VisualizationTheme theme, Set<ViewLocation> highlights) {
        ViewNodeRenderer.requireFxThread();
        return measured(renderer.render(owner, pageNodes, theme, highlights));
    }
    public LayoutRequest.Unit measure(ViewNode owner, Map<Long, ViewNode> pageNodes, VisualizationTheme theme,
                                      Set<ViewLocation> highlights, ViewNodeRenderer.ArrayStyle style) {
        ViewNodeRenderer.requireFxThread();
        return measured(renderer.render(owner, pageNodes, theme, highlights, style));
    }
    public LayoutRequest.Unit measure(ViewNode owner, Map<Long, ViewNode> pageNodes,
                                      Map<ViewLocation, VisualizationTheme> themes, Set<ViewLocation> highlights) {
        ViewNodeRenderer.requireFxThread();
        return measured(renderer.render(owner, pageNodes, themes, highlights));
    }
    private static LayoutRequest.Unit measured(ViewNodeRenderer.RenderedNode rendered) {
        // The measurement scene and its fresh nodes never enter the domain model or a visible host.
        new Scene(rendered.view(), rendered.unit().size().width(), rendered.unit().size().height());
        rendered.view().applyCss(); rendered.view().resize(rendered.unit().size().width(), rendered.unit().size().height()); rendered.view().layout();
        if (Math.abs(rendered.view().getWidth() - rendered.unit().size().width()) > 1e-7
                || Math.abs(rendered.view().getHeight() - rendered.unit().size().height()) > 1e-7)
            throw new IllegalStateException("FX measurement differs from the emitted pure Java geometry: " + rendered.view().getWidth() + "x" + rendered.view().getHeight());
        return rendered.unit();
    }
}
