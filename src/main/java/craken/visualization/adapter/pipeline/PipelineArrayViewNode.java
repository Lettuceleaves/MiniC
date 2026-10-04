package craken.visualization.adapter.pipeline;

import craken.visualization.api.ViewLocation;
import craken.visualization.model.*;
import craken.visualization.model.node.ArrayViewNode;
import java.util.List;

/** Pipeline sequence semantics stay in the adapter rather than the generic core. */
public final class PipelineArrayViewNode extends ArrayViewNode {
    public PipelineArrayViewNode(ViewLocation location, Spec spec, Retention retention, ParentSelection parents) {
        this(location, spec, retention, parents, List.of());
    }
    private PipelineArrayViewNode(ViewLocation location, Spec spec, Retention retention, ParentSelection parents,
            List<ViewLocation> children) { super(location, spec, retention, parents, children); }
    @Override public ViewNode withState(Spec spec, ParentSelection parents) {
        return new PipelineArrayViewNode(location(), spec, retention(), parents, children());
    }
    @Override public ViewNode withChildren(List<ViewLocation> children) {
        return new PipelineArrayViewNode(location(), content(), retention(), parents(), children);
    }
}
