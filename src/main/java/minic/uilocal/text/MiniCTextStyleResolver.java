package minic.uilocal.text;

import java.util.Collection;

/**
 * Resolves reusable text semantics into concrete JavaFX style classes.
 */
public interface MiniCTextStyleResolver {
    Collection<String> styleClasses(MiniCTextStyleRole role, Collection<MiniCTextStyleState> states);
}
