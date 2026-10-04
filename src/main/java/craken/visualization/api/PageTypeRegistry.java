package craken.visualization.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import craken.visualization.model.*;

public final class PageTypeRegistry {
    private final Map<String, PageType> types = new LinkedHashMap<>();
    public synchronized PageType register(PageType type) {
        Objects.requireNonNull(type, "type");
        var key = type.key();
        PageType previous = types.get(key);
        if (previous == type) return previous;
        if (previous instanceof Registered registered && definition(registered).equals(definition(type))
                && registered.readyEnabled() == type.readyEnabled() && registered.maximumNesting() == type.maximumNesting()
                && registered.layout() == type.layout() && registered.nodeKinds().equals(type.nodeKinds()))
            return previous;
        if (previous != null)
            throw new IllegalArgumentException("Type key already registered: " + type.key());
        var frozen = new Registered(type, key, type.readyEnabled(), type.maximumNesting(), type.layout(),
                Set.copyOf(type.nodeKinds()), type.nestingPolicy());
        if (key == null || key.isBlank() || frozen.maximumNesting < 0 || frozen.maximumNesting > 1
                || frozen.nodeKinds.isEmpty()) throw new IllegalArgumentException("Invalid page type");
        Objects.requireNonNull(frozen.layout); Objects.requireNonNull(frozen.nestingPolicy);
        types.put(key, frozen);
        return frozen;
    }
    private static PageType definition(PageType type) {
        while (type instanceof Registered registered) type = registered.source;
        return type;
    }
    public synchronized PageType require(String key) {
        PageType type = types.get(key);
        if (type == null) throw new IllegalArgumentException("Unknown page type: " + key);
        return type;
    }
    /** Metadata is captured once; extension callbacks must themselves be pure. */
    private record Registered(PageType source, String key, boolean readyEnabled, int maximumNesting,
                              PageType.Layout layout, Set<ViewNode.Kind> nodeKinds,
                              PageType.SemanticNestingPolicy nestingPolicy) implements PageType {
        @Override public ViewNode create(ViewLocation location, ViewNode.Spec content, ViewNode.Retention retention, ParentSelection parents) {
            return source.create(location, content, retention, parents);
        }
        @Override public ViewNode copy(ViewNode node) { return source.copy(node); }
    }
}
