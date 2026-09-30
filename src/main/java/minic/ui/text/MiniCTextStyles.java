package minic.ui.text;

import javafx.scene.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Entry point for shared text style resolution.
 */
public final class MiniCTextStyles {
    private static final MiniCTextStyleResolver DEFAULT_RESOLVER = new DefaultResolver();

    private MiniCTextStyles() {}

    public static MiniCTextStyleResolver defaultResolver() {
        return DEFAULT_RESOLVER;
    }

    public static Collection<String> classes(MiniCTextStyleRole role, MiniCTextStyleState... states) {
        return DEFAULT_RESOLVER.styleClasses(role, Arrays.asList(states));
    }

    public static Collection<String> stateClasses(MiniCTextStyleState... states) {
        LinkedHashSet<String> classes = new LinkedHashSet<>();
        for (MiniCTextStyleState state : states) {
            classes.add(state.cssClass());
            classes.addAll(state.legacyClasses());
        }
        return List.copyOf(classes);
    }

    public static void addStateClasses(Collection<String> target, MiniCTextStyleState... states) {
        target.addAll(stateClasses(states));
    }

    public static void apply(Node node, MiniCTextStyleRole role, MiniCTextStyleState... states) {
        node.getStyleClass().addAll(classes(role, states));
    }

    private static final class DefaultResolver implements MiniCTextStyleResolver {
        @Override
        public Collection<String> styleClasses(MiniCTextStyleRole role, Collection<MiniCTextStyleState> states) {
            LinkedHashSet<String> classes = new LinkedHashSet<>();
            classes.add(role.cssClass());
            classes.addAll(role.legacyClasses());
            for (MiniCTextStyleState state : states == null ? List.<MiniCTextStyleState>of() : states) {
                classes.add(state.cssClass());
                classes.addAll(state.legacyClasses());
            }
            return new ArrayList<>(classes);
        }
    }
}
