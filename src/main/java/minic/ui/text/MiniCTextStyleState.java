package minic.ui.text;

import java.util.List;

/**
 * Text style overlays that can be composed with any text role.
 */
public enum MiniCTextStyleState {
    ACTIVE("active", "text.active", null, "active"),
    SELECTED("selected", "text.active", null, "selected"),
    FOCUSED("focused", "text.active", null, "focus"),
    HOT("hot", "text.active", null, "hot"),
    DIAGNOSTIC("diagnostic", null, null, "diagnostic"),
    DEBUG_EXECUTION("debug.execution", "text.active", "background.running", "debug-execution-range");

    private final String themeId;
    private final String fallbackColorKey;
    private final String fallbackBackgroundKey;
    private final List<String> legacyClasses;

    MiniCTextStyleState(
            String themeId,
            String fallbackColorKey,
            String fallbackBackgroundKey,
            String... legacyClasses
    ) {
        this.themeId = themeId;
        this.fallbackColorKey = fallbackColorKey;
        this.fallbackBackgroundKey = fallbackBackgroundKey;
        this.legacyClasses = List.of(legacyClasses);
    }

    public String themeId() {
        return themeId;
    }

    public String cssClass() {
        return "mc-text-state-" + themeId.replace('.', '-');
    }

    public String fallbackColorKey() {
        return fallbackColorKey;
    }

    public String fallbackBackgroundKey() {
        return fallbackBackgroundKey;
    }

    public List<String> legacyClasses() {
        return legacyClasses;
    }
}
