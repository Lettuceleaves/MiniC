package craken.visualization.snapshot;

import craken.visualization.api.*;
import craken.visualization.model.*;
import craken.visualization.model.relation.*;
import craken.visualization.mutation.*;
import craken.visualization.navigation.NavigationResolver;
import java.util.*;

/** Rejects inconsistent snapshots before the live model or ID allocators are changed. */
final class SnapshotValidator {
    private SnapshotValidator() {}
    static void validate(ContainerModel model,boolean validateTypePolicy) {
        if (model.id()<=0 || model.version()<0 || model.epoch()<0) fail("Invalid container identity/version");
        if (model.root()==null ? !model.pages().isEmpty() : !model.pages().containsKey(model.root().pageId()) || model.root().containerId()!=model.id()) fail("Invalid root page");
        var relationIds=new HashSet<Long>();
        for (var page:model.pages().values()) {
            if (!page.nodes().keySet().containsAll(page.ready())) fail("Dangling READY node");
            if (!page.type().readyEnabled() && !page.ready().isEmpty()) fail("READY disabled");
            if (page.anchor()!=null) model.node(page.anchor());
            for (var node:page.nodes().values()) {
                if (node.retention()==ViewNode.Retention.ROOT && !page.ref().equals(model.root())) fail("ROOT node outside root page");
                for (var h:node.highlights()) { if (!h.page().equals(page.ref())) fail("Cross-page highlight"); model.node(h); }
            }
            var slots=new HashSet<String>();
            for (var entry:page.composition().entrySet()) {
                var link=entry.getValue(); var parent=model.node(link.parent()); var child=model.node(link.child());
                if (entry.getKey()!=link.child().nodeId() || !link.parent().page().equals(page.ref()) || !link.child().page().equals(page.ref()) || link.slot()<0 || !slots.add(link.parent().nodeId()+":"+link.slot())) fail("Invalid composition slot");
                if (link.semanticIncrement()<0 || link.semanticIncrement()>1 || validateTypePolicy && link.semanticIncrement()!=page.type().nestingPolicy().increment(parent,child)) fail("Invalid semantic increment");
            }
            CompositionStore.validate(page,page.composition());
            for (var entry:page.topology().entrySet()) {
                var edge=entry.getValue(); model.node(edge.a()); model.node(edge.b());
                if (entry.getKey()!=edge.id() || edge.id()<=0 || !relationIds.add(edge.id()) || !edge.a().page().equals(page.ref()) || !edge.b().page().equals(page.ref()) || edge.direction()==null) fail("Invalid topology edge");
            }
        }
        for (var entry:model.ownership().entrySet()) {
            var binding=entry.getValue();
            if (!entry.getKey().equals(binding.key()) || binding.id()<=0 || !relationIds.add(binding.id()) || binding.sources().isEmpty()) fail("Invalid ownership identity");
            CommandValidator.ownership(model.node(binding.key().pre()),model.node(binding.key().nxt()));
            for (var source:binding.sources()) {
                if (source.isBlank()) fail("Empty ownership source");
                if (source.startsWith("rule:")) {
                    var rule=model.pageRules().get(Long.parseLong(source.substring(5)));
                    if (rule==null || !rule.child().equals(binding.key().nxt().page()) || !rule.spec().parentPage().equals(binding.key().pre().page())
                            || rule.spec().parentNode()!=null && !rule.spec().parentNode().equals(binding.key().pre())) fail("Invalid rule provenance");
                }
            }
        }
        var store=new OwnershipStore(model.ownership(),new MonotonicIds());
        OwnershipDagValidator.validate(model.pages(),store);
        for (var entry:model.pageRules().entrySet()) {
            var rule=entry.getValue();
            if (entry.getKey()!=rule.id() || !relationIds.add(rule.id())) fail("Duplicate rule identity");
            PageBindingRuleExpander.validate(model.id(),model.pages(),rule.child(),rule.spec());
            PageBindingRuleExpander.expand(rule,model.pages(),null,Set.of(),(pre,nxt)-> {
                var binding=model.ownership().get(new OwnershipBinding.Key(pre,nxt));
                if (binding==null || !binding.sources().contains(rule.source())) fail("Missing materialized rule source");
            });
        }
        if (model.focus()!=null) model.node(model.focus());
        if (model.interaction().accessed()!=null) model.node(model.interaction().accessed());
        NavigationResolver.resolve(model);
    }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
