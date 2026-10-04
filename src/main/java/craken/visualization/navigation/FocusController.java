package craken.visualization.navigation;

import craken.visualization.api.*;
import craken.visualization.model.*;
import java.util.*;

/** Transaction-local access and focus state; capture precedes reference cleanup. */
public final class FocusController {
    private ViewLocation focus, accessed;
    private AccessKind kind;
    private VisualizationOptions options;
    public FocusController(InteractionState state) {
        focus=state.focus(); accessed=state.accessed(); kind=state.accessKind(); options=state.options();
    }
    public void configure(VisualizationOptions options) { this.options=Objects.requireNonNull(options); }
    public void access(ViewLocation target, AccessKind kind) {
        accessed=target; this.kind=kind; if (options.autoNavigate()) focus=target;
    }
    public void focus(ViewLocation target) { focus=target; accessed=target; kind=AccessKind.READ; }
    public List<ViewLocation> capture(Map<Long,PageModel> pages) {
        var path=new ArrayList<ViewLocation>(); var visited=new HashSet<ViewLocation>();
        for (var at=focus; at!=null && visited.add(at); ) {
            path.add(at); var page=pages.get(at.pageId()); var node=page==null?null:page.nodes().get(at.nodeId());
            at=node==null?null:node.parents().selected();
        }
        return List.copyOf(path);
    }
    public InteractionState finish(Map<Long,PageModel> pages,List<ViewLocation> fallback) {
        if (!live(pages,focus)) focus=fallback.stream().filter(n->live(pages,n)).findFirst().orElse(null);
        if (!live(pages,accessed)) accessed=focus;
        return new InteractionState(focus,accessed,kind,options);
    }
    private static boolean live(Map<Long,PageModel> pages,ViewLocation at) {
        return at!=null && pages.containsKey(at.pageId()) && pages.get(at.pageId()).nodes().containsKey(at.nodeId());
    }
}
