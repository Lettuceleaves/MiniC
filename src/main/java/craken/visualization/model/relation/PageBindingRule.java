package craken.visualization.model.relation;

import craken.visualization.api.*;
import java.util.Objects;

/** A live rule contributes its own source to each effective ownership binding. */
public record PageBindingRule(long id, PageRef child, Spec spec) {
    public PageBindingRule { if (id <= 0) throw new IllegalArgumentException("Invalid rule ID"); Objects.requireNonNull(child); Objects.requireNonNull(spec); }
    public String source() { return "rule:" + id; }
    public record Spec(PageRef parentPage, ViewLocation parentNode) {
        public Spec {
            Objects.requireNonNull(parentPage);
            if (parentNode != null && !parentNode.page().equals(parentPage)) throw new IllegalArgumentException("Mismatched rule parent");
        }
        public static Spec node(ViewLocation node) { return new Spec(node.page(), node); }
        public static Spec page(PageRef page) { return new Spec(page, null); }
    }
}
