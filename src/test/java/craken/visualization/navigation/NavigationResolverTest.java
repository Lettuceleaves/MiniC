package craken.visualization.navigation;

import craken.visualization.api.*;
import craken.visualization.model.*;
import craken.visualization.model.node.PointViewNode;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class NavigationResolverTest {
    @Test void deepPathIsIterativeAndDoesNotDeduplicatePages() {
        var p=new PageRef(1,1); var q=new PageRef(1,2);
        var left=new LinkedHashMap<Long,ViewNode>(); var right=new LinkedHashMap<Long,ViewNode>();
        ViewLocation previous=null;
        for (int i=1;i<=10_000;i++) {
            var at=new ViewLocation(1,i%2==0?2:1,i);
            var node=new PointViewNode(at,ViewNode.Spec.point("n"),previous==null?ViewNode.Retention.ROOT:ViewNode.Retention.OWNED,
                    previous==null?ParentSelection.ROOT:ParentSelection.ROOT.add(previous));
            (i%2==0?right:left).put((long)i,node); previous=at;
        }
        var model=new ContainerModel(1,p,Map.of(1L,new PageModel(p,BuiltinPageTypes.point(),left,null),2L,new PageModel(q,BuiltinPageTypes.point(),right,null)),1,Map.of(),Map.of(),
                new InteractionState(previous,previous,AccessKind.READ,VisualizationOptions.DEFAULT));
        assertEquals(10_000,NavigationResolver.resolve(model).occurrences().size());
    }
    @Test void overriddenHighlightIsEvaluatedPerOccurrence() {
        var p=new PageRef(1,1); var a=new ViewLocation(1,1,1); var b=new ViewLocation(1,1,2);
        var custom=new PointViewNode(a,ViewNode.Spec.point("a"),ViewNode.Retention.ROOT,ParentSelection.ROOT) {
            @Override public Set<ViewLocation> highlights() { return Set.of(a,b); }
        };
        var other=new PointViewNode(b,ViewNode.Spec.point("b"),ViewNode.Retention.ROOT,ParentSelection.ROOT);
        var model=new ContainerModel(1,p,Map.of(1L,new PageModel(p,BuiltinPageTypes.point(),Map.of(1L,custom,2L,other),null)),1,Map.of(),Map.of(),
                new InteractionState(a,a,AccessKind.READ,VisualizationOptions.DEFAULT));
        assertEquals(Set.of(a,b),NavigationResolver.resolve(model).occurrences().getFirst().highlights());
    }
    @Test void corruptCycleIsReportedWithoutInfiniteTraversal() {
        var p=new PageRef(1,1); var a=new ViewLocation(1,1,1);
        var node=new PointViewNode(a,ViewNode.Spec.point("a"),ViewNode.Retention.OWNED,ParentSelection.ROOT.add(a));
        var model=new ContainerModel(1,p,Map.of(1L,new PageModel(p,BuiltinPageTypes.point(),Map.of(1L,node),null)),1,Map.of(),Map.of(),
                new InteractionState(a,a,AccessKind.READ,VisualizationOptions.DEFAULT));
        assertThrows(IllegalArgumentException.class,()->NavigationResolver.resolve(model));
    }
}
