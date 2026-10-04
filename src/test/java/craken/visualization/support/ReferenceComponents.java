package craken.visualization.support;

import java.util.*;

/** Independent test oracle: rebuild connectivity only from the test's edge pairs. */
public final class ReferenceComponents {
    private ReferenceComponents() {}
    public static Set<Set<Long>> compute(Set<Long> vertices, List<long[]> pairs) {
        var remaining = new HashSet<>(vertices);
        var result = new HashSet<Set<Long>>();
        while (!remaining.isEmpty()) {
            long seed = remaining.iterator().next();
            var found = new HashSet<Long>();
            found.add(seed);
            boolean changed;
            do {
                changed = false;
                for (long[] pair : pairs) if (found.contains(pair[0]) || found.contains(pair[1])) {
                    changed |= found.add(pair[0]);
                    changed |= found.add(pair[1]);
                }
            } while (changed);
            remaining.removeAll(found);
            result.add(Set.copyOf(found));
        }
        return result;
    }
}
