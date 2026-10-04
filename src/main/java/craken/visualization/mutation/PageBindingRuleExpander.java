package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.model.*;
import craken.visualization.model.relation.PageBindingRule;
import java.util.*;
import java.util.function.BiConsumer;

/** Expands one rule, or only the row/column affected by a newly allocated node. */
public final class PageBindingRuleExpander {
    private PageBindingRuleExpander() {}
    public static void expand(PageBindingRule rule, Map<Long, PageModel> pages, ViewLocation added,
                              Set<ViewLocation> deleted, BiConsumer<ViewLocation, ViewLocation> attach) {
        var parent = pages.get(rule.spec().parentPage().pageId());
        var child = pages.get(rule.child().pageId());
        if (parent == null || child == null) return;
        for (var p : parent.nodes().values()) {
            if (deleted.contains(p.location()) || rule.spec().parentNode() != null && !rule.spec().parentNode().equals(p.location())) continue;
            for (var c : child.nodes().values()) {
                if (deleted.contains(c.location())) continue;
                if (added == null || added.equals(p.location()) || added.equals(c.location())) attach.accept(p.location(),c.location());
            }
        }
    }
    public static void validate(long containerId, Map<Long, PageModel> pages, PageRef child, PageBindingRule.Spec spec) {
        CommandValidator.page(containerId,pages,child);
        CommandValidator.page(containerId,pages,spec.parentPage());
        if (child.equals(spec.parentPage())) throw CommandValidator.failure(VisualizationError.Code.INVALID_OWNERSHIP,"Rule must cross pages");
        if (spec.parentNode() != null) CommandValidator.node(containerId,pages,spec.parentNode());
    }
}
