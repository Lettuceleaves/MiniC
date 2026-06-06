package minic.uilocal.text;

import java.util.List;

/**
 * Text-level semantic roles shared by labels, rich text, and graph text.
 */
public enum MiniCTextStyleRole {
    BODY("body", "text.body", "ui", "normal", "normal"),
    BODY_MONO("body.mono", "text.body", "mono", "normal", "normal"),
    SECONDARY("secondary", "text.secondary", "ui", "normal", "normal"),
    MUTED("muted", "text.muted", "ui", "normal", "normal"),
    MUTED_ALT("muted.alt", "text.muted_alt", "ui", "normal", "normal"),
    LABEL("label", "text.label", "ui", "normal", "normal"),
    ACTIVE("active", "text.active", "ui", "normal", "normal"),
    EMPHASIS("emphasis", "text.body", "ui", "normal", "italic"),
    PANEL_TITLE("panel.title", "text.panel_title", "ui", "bold", "normal"),
    SECTION_LABEL("section.label", "text.label", "ui", "bold", "normal"),
    STAGE_TITLE("stage.title", "text.stage_title", "ui", "bold", "normal"),
    LINE_NUMBER("line.number", "text.line_number", "mono", "normal", "normal"),
    DIAGNOSTIC_DETAIL("diagnostic.detail", "text.diagnostic_detail", "ui", "normal", "normal"),
    GRAPH_LABEL("graph.label", "graph.label", "mono", "normal", "normal"),
    CODE_PLAIN("code.plain", "text.body", "mono", "normal", "normal", "token-plain"),
    CODE_KEYWORD("code.keyword", "syntax.keyword", "mono", "normal", "normal", "token-keyword"),
    CODE_IDENTIFIER("code.identifier", "text.body", "mono", "normal", "normal", "token-identifier"),
    CODE_STRING("code.string", "syntax.string", "mono", "normal", "normal", "token-string"),
    CODE_LITERAL("code.literal", "syntax.literal", "mono", "normal", "normal", "token-literal"),
    CODE_OPERATOR("code.operator", "text.body", "mono", "normal", "normal", "token-operator"),
    CODE_TYPE("code.type", "syntax.type", "mono", "normal", "normal"),
    CODE_COMMENT("code.comment", "text.muted", "mono", "normal", "italic");

    private final String themeId;
    private final String fallbackColorKey;
    private final String fallbackFontFamily;
    private final String fallbackFontWeight;
    private final String fallbackFontStyle;
    private final List<String> legacyClasses;

    MiniCTextStyleRole(
            String themeId,
            String fallbackColorKey,
            String fallbackFontFamily,
            String fallbackFontWeight,
            String fallbackFontStyle,
            String... legacyClasses
    ) {
        this.themeId = themeId;
        this.fallbackColorKey = fallbackColorKey;
        this.fallbackFontFamily = fallbackFontFamily;
        this.fallbackFontWeight = fallbackFontWeight;
        this.fallbackFontStyle = fallbackFontStyle;
        this.legacyClasses = List.of(legacyClasses);
    }

    public String themeId() {
        return themeId;
    }

    public String cssClass() {
        return "mc-text-" + themeId.replace('.', '-');
    }

    public String fallbackColorKey() {
        return fallbackColorKey;
    }

    public String fallbackFontFamily() {
        return fallbackFontFamily;
    }

    public String fallbackFontWeight() {
        return fallbackFontWeight;
    }

    public String fallbackFontStyle() {
        return fallbackFontStyle;
    }

    public List<String> legacyClasses() {
        return legacyClasses;
    }
}
