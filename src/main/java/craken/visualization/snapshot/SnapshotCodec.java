package craken.visualization.snapshot;

import craken.visualization.api.*;
import craken.visualization.model.*;
import craken.visualization.model.relation.CompositionLink;
import craken.visualization.mutation.PartPlanner;
import java.util.*;
import static craken.visualization.snapshot.VisualizationSnapshot.*;

/** Converts values and rebuilds derived composition and part indexes. */
public final class SnapshotCodec {
    private SnapshotCodec() {}
    public static VisualizationSnapshot capture(ContainerModel model,HighWater highWater) {
        var pages=new LinkedHashMap<Long,PageState>();
        for (var page:model.pages().values()) {
            var nodes=new LinkedHashMap<Long,NodeState>();
            page.nodes().forEach((id,node)->nodes.put(id,new NodeState(node.location(),node.content(),node.retention(),node.parents(),node.highlights())));
            pages.put(page.ref().pageId(),new PageState(page.ref(),TypeDescription.of(page.type()),nodes,page.anchor(),page.composition(),page.topology(),page.ready()));
        }
        return new VisualizationSnapshot(model.id(),model.root(),pages,model.ownership(),model.pageRules(),model.interaction(),model.sourceVersion(),model.epoch(),model.sourceStep(),highWater);
    }
    public static ContainerModel restore(VisualizationSnapshot snapshot,PageTypeRegistry registry,long version,long epoch) {
        return decode(snapshot,registry,version,epoch);
    }
    public static ContainerModel toDisplayModel(VisualizationSnapshot snapshot) {
        return decode(snapshot,null,snapshot.sourceVersion(),snapshot.epoch());
    }
    private static ContainerModel decode(VisualizationSnapshot snapshot,PageTypeRegistry registry,long version,long epoch) {
        var pages=new LinkedHashMap<Long,PageModel>();
        for (var entry:snapshot.pages().entrySet()) {
            var page=entry.getValue();
            if (entry.getKey()!=page.ref().pageId() || page.ref().containerId()!=snapshot.containerId()) throw new IllegalArgumentException("Invalid page identity");
            PageType type=registry==null?new DisplayType(page.type()):registry.require(page.type().key());
            if (!TypeDescription.of(type).equals(page.type())) throw new IllegalArgumentException("Changed page type definition");
            var nodes=new LinkedHashMap<Long,ViewNode>();
            for (var nodeEntry:page.nodes().entrySet()) {
                var value=nodeEntry.getValue();
                if (nodeEntry.getKey()!=value.location().nodeId() || !value.location().page().equals(page.ref()) || !type.nodeKinds().contains(value.content().kind())) throw new IllegalArgumentException("Invalid node identity or type");
                var children=page.composition().values().stream().filter(link->link.parent().equals(value.location()))
                        .sorted(Comparator.comparingInt(CompositionLink::slot)).map(CompositionLink::child).toList();
                var node=registry==null?new FrozenNode(value,children):type.create(value.location(),value.content(),value.retention(),value.parents()).withChildren(children);
                if (!node.location().equals(value.location()) || !node.content().equals(value.content()) || node.retention()!=value.retention() || !node.parents().equals(value.parents()) || !node.children().equals(children)) throw new IllegalArgumentException("Page factory violated snapshot contract");
                nodes.put(nodeEntry.getKey(),node);
            }
            pages.put(entry.getKey(),new PageModel(page.ref(),type,nodes,page.anchor(),page.composition(),page.topology(),page.ready(),Map.of(),Map.of()));
        }
        var draft=new ContainerModel(snapshot.containerId(),snapshot.root(),pages,version,snapshot.ownership(),snapshot.pageRules(),snapshot.interaction(),epoch,snapshot.sourceStep(),snapshot.sourceVersion());
        SnapshotValidator.validate(draft,registry!=null);
        pages.replaceAll((id,page)->PartPlanner.plan(page));
        return new ContainerModel(draft.id(),draft.root(),pages,version,draft.ownership(),draft.pageRules(),draft.interaction(),epoch,draft.sourceStep(),draft.sourceVersion());
    }
    private record DisplayType(TypeDescription description) implements PageType {
        @Override public String key() { return description.key(); }
        @Override public boolean readyEnabled() { return description.readyEnabled(); }
        @Override public int maximumNesting() { return description.maximumNesting(); }
        @Override public Layout layout() { return description.layout(); }
        @Override public Set<ViewNode.Kind> nodeKinds() { return description.nodeKinds(); }
        @Override public ViewNode create(ViewLocation location,ViewNode.Spec content,ViewNode.Retention retention,ParentSelection parents) {
            return new FrozenNode(new NodeState(location,content,retention,parents,Set.of(location)),List.of());
        }
    }
    private static final class FrozenNode extends ViewNode {
        private final Set<ViewLocation> highlights;
        FrozenNode(NodeState node,List<ViewLocation> children) {
            super(node.location(),node.content(),node.retention(),node.parents(),children); highlights=node.highlights();
        }
        @Override public Set<ViewLocation> highlights() { return highlights; }
        @Override public ViewNode withState(Spec content,ParentSelection parents) { return new FrozenNode(new NodeState(location(),content,retention(),parents,highlights),children()); }
        @Override public ViewNode withChildren(List<ViewLocation> children) { return new FrozenNode(new NodeState(location(),content(),retention(),parents(),highlights),children); }
    }
}
