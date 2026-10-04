package craken.visualization.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class PageTypeRegistry {
    private final Map<String, PageType> types = new LinkedHashMap<>();
    public synchronized void register(PageType type) {
        Objects.requireNonNull(type, "type");
        if (type.key() == null || type.key().isBlank() || type.maximumNesting() < 0 || type.maximumNesting() > 1)
            throw new IllegalArgumentException("Invalid page type");
        PageType previous = types.putIfAbsent(type.key(), type);
        if (previous != null && previous != type && !previous.equals(type))
            throw new IllegalArgumentException("Type key already registered: " + type.key());
    }
    public synchronized PageType require(String key) {
        PageType type = types.get(key);
        if (type == null) throw new IllegalArgumentException("Unknown page type: " + key);
        return type;
    }
}
