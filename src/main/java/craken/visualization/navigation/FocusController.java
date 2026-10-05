package craken.visualization.navigation;

import craken.visualization.api.*;
import craken.visualization.model.*;
import java.util.*;
import java.util.function.Function;

/** Transaction-local access and focus state; capture precedes reference cleanup. */
public final class FocusController {
    private ViewLocation focus, accessed;
    private AccessKind kind;
    private VisualizationOptions options;
    private List<ViewLocation> fallback = List.of();
    private boolean rememberFocusPath = true;
    public FocusController(InteractionState state) {
        focus=state.focus(); accessed=state.accessed(); kind=state.accessKind(); options=state.options();
    }
    public void configure(VisualizationOptions options) { this.options=Objects.requireNonNull(options); }
    public void access(ViewLocation target, AccessKind kind) {
        accessed=target; this.kind=kind;
        rememberFocusPath |= options.autoNavigate() || Objects.equals(focus,target);
        if (options.autoNavigate()) focus=target;
    }
    public void focus(ViewLocation target) { focus=target; accessed=target; kind=AccessKind.READ; rememberFocusPath=true; }
    public void clear() { focus=null; accessed=null; kind=null; rememberFocusPath=true; }
    /** Preserve the old focus chain when an operation only selects a different ancestor branch. */
    public void remember(Map<Long,PageModel> pages) {
        remember(location -> { var page = pages.get(location.pageId()); return page == null ? null : page.nodes().get(location.nodeId()); });
    }
    public void remember(Function<ViewLocation, ViewNode> lookup) {
        if (rememberFocusPath) fallback = capture(lookup);
        rememberFocusPath = false;
    }
    public List<ViewLocation> capture(Map<Long,PageModel> pages) {
        return capture(location -> { var page = pages.get(location.pageId()); return page == null ? null : page.nodes().get(location.nodeId()); });
    }
    public List<ViewLocation> capture(Function<ViewLocation, ViewNode> lookup) {
        var path=new ArrayList<ViewLocation>(); var visited=new HashSet<ViewLocation>();
        for (var at=focus; at!=null && visited.add(at); ) {
            path.add(at); var node = lookup.apply(at);
            at=node==null?null:node.parents().selected();
        }
        return List.copyOf(path);
    }
    public InteractionState finish(Map<Long,PageModel> pages) {
        return finish(location -> { var page = pages.get(location.pageId()); return page == null ? null : page.nodes().get(location.nodeId()); });
    }
    public InteractionState finish(Function<ViewLocation, ViewNode> lookup) {
        if (!live(lookup,focus)) focus=fallback.stream().filter(n->live(lookup,n)).findFirst().orElse(null);
        if (!live(lookup,accessed)) accessed=focus;
        return new InteractionState(focus,accessed,kind,options);
    }
    private static boolean live(Function<ViewLocation, ViewNode> lookup, ViewLocation at) {
        return at != null && lookup.apply(at) != null;
    }
}
