package craken.visualization.acceptance;

import craken.visualization.api.*;
import craken.visualization.model.*;
import craken.visualization.support.ModelFixture;
import org.junit.jupiter.api.*;
import java.util.*;
import static craken.visualization.api.VisualizationCommand.*;
import static org.junit.jupiter.api.Assertions.*;

/** The oracle computes root reachability, independently of production reference counts. */
@Tag("visualization-model")
class OwnershipSequenceAcceptanceTest {
    @Test void randomSharingDetachReparentAndDeletionMatchIndependentReachability() {
        for (long seed : new long[]{0x613L,0xC23L,20261005L}) run(seed);
    }
    private void run(long seed) {
        var random=new Random(seed); var history=new ArrayList<String>();
        try(var f=new ModelFixture()) {
            var nodes=new ArrayList<ViewLocation>(); nodes.add(f.r); nodes.add(f.root("other"));
            var roots=new LinkedHashSet<>(nodes); var incoming=new LinkedHashMap<ViewLocation,Set<ViewLocation>>();
            for (var root:roots) incoming.put(root,new LinkedHashSet<>());
            var pages=List.of(f.page(f.r),f.page(f.r),f.page(f.r));
            for (int i=0;i<30;i++) {
                var parent=nodes.get(i%2); var n=f.node(pages.get(i%3),parent,"node-"+i);
                nodes.add(n); incoming.put(n,new LinkedHashSet<>(Set.of(parent)));
            }
            var rank=new HashMap<ViewLocation,Integer>(); for (int i=0;i<nodes.size();i++) rank.put(nodes.get(i),i);
            for (int step=0;step<200;step++) {
                var owned=incoming.keySet().stream().filter(n->!roots.contains(n)).toList();
                if (owned.isEmpty()) break;
                var target=owned.get(random.nextInt(owned.size()));
                var oldParents=new ArrayList<>(incoming.get(target));
                var candidates=incoming.keySet().stream().filter(p->rank.get(p)<rank.get(target) && !p.page().equals(target.page())).toList();
                var parent=candidates.get(random.nextInt(candidates.size()));
                var selected=oldParents.get(random.nextInt(oldParents.size()));
                var commands=new ArrayList<VisualizationCommand>();
                int operation=random.nextInt(10);
                if (operation<4) {
                    commands.add(new AttachOwnership(parent,target)); incoming.get(target).add(parent);
                } else if (operation<7) {
                    commands.add(new DetachOwnership(selected,target)); incoming.get(target).remove(selected);
                } else if (operation<9) {
                    commands.add(new DetachOwnership(selected,target)); commands.add(new AttachOwnership(parent,target));
                    incoming.get(target).remove(selected); incoming.get(target).add(parent);
                } else {
                    commands.add(new DeleteNode(new OperationPath(selected,target))); incoming.remove(target);
                    incoming.values().forEach(parents->parents.remove(target));
                }
                history.add(commands.toString());
                var result=f.session.modify(new MutationBatch(commands,"seed="+seed+";step="+step));
                assertTrue(result.succeeded(),()->"Seed "+seed+" commands="+history+" error="+result.error());
                var reachable=new LinkedHashSet<>(roots); boolean changed;
                do {
                    changed=false;
                    for (var entry:incoming.entrySet()) if (!reachable.contains(entry.getKey())
                            && entry.getValue().stream().anyMatch(reachable::contains)) changed|=reachable.add(entry.getKey());
                } while(changed);
                incoming.keySet().retainAll(reachable); incoming.values().forEach(parents->parents.retainAll(reachable));
                var actual=new LinkedHashMap<ViewLocation,ViewNode>();
                f.session.model().pages().values().forEach(page->page.nodes().values().forEach(node->actual.put(node.location(),node)));
                assertEquals(incoming.keySet(),actual.keySet(),"seed="+seed+" commands="+history);
                for (var entry:actual.entrySet()) {
                    assertEquals(incoming.get(entry.getKey()),Set.copyOf(entry.getValue().parents().parents()));
                    if (!roots.contains(entry.getKey())) assertTrue(incoming.get(entry.getKey()).contains(entry.getValue().parents().selected()));
                }
            }
        }
    }
}
