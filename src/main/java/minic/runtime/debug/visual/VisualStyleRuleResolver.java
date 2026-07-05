package minic.runtime.debug.visual;

import minic.runtime.debug.memory.TypedMemoryField;
import minic.runtime.debug.memory.TypedMemoryNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Resolves optional @style rules into renderer-neutral visual metadata.
 */
final class VisualStyleRuleResolver {
    void apply(
            VisualSpec spec,
            TypedMemoryNode node,
            String displayType,
            LinkedHashMap<String, String> metadata,
            List<String> warnings
    ) {
        for (VisualStyleRule rule : spec.styleRules()) {
            if (!matchesType(rule, node, displayType)) {
                continue;
            }
            applyTemplate(spec, rule, node, metadata, warnings);
        }
    }

    private boolean matchesType(VisualStyleRule rule, TypedMemoryNode node, String displayType) {
        String expected = canonicalType(rule.type());
        return expected.equals(canonicalType(displayType)) || expected.equals(canonicalType(node.typeName()));
    }

    private void applyTemplate(
            VisualSpec spec,
            VisualStyleRule rule,
            TypedMemoryNode node,
            LinkedHashMap<String, String> metadata,
            List<String> warnings
    ) {
        if (rule.template().equals("red-black")) {
            applyRedBlackTemplate(rule, node, metadata, warnings);
            return;
        }
        warnings.add("visual " + spec.name() + " 第 " + rule.line() + " 行 @style template 不支持：" + rule.template());
    }

    private void applyRedBlackTemplate(
            VisualStyleRule rule,
            TypedMemoryNode node,
            LinkedHashMap<String, String> metadata,
            List<String> warnings
    ) {
        String fieldName = "color";
        String value = fieldValue(node, fieldName);
        if (value.isBlank()) {
            warnings.add("第 " + rule.line() + " 行 red-black style 未找到字段：" + fieldName);
            return;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (isBlack(normalized)) {
            metadata.put("visual-style-template", "red-black");
            metadata.put("visual-style-source", fieldName);
            metadata.put("visual-style-value", value);
            metadata.put("visual-fill", "#1f2329");
            metadata.put("visual-stroke", "#aeb7c5");
            metadata.put("visual-text-fill", "#f8fafc");
            metadata.put("visual-style-class", "debug-tree-node-black");
            return;
        }
        if (isRed(normalized)) {
            metadata.put("visual-style-template", "red-black");
            metadata.put("visual-style-source", fieldName);
            metadata.put("visual-style-value", value);
            metadata.put("visual-fill", "#8f2633");
            metadata.put("visual-stroke", "#f1a1aa");
            metadata.put("visual-text-fill", "#fff5f5");
            metadata.put("visual-style-class", "debug-tree-node-red");
        }
    }

    private String fieldValue(TypedMemoryNode node, String fieldName) {
        return node.fields().stream()
                .filter(field -> field.name().equals(fieldName))
                .findFirst()
                .map(TypedMemoryField::value)
                .map(TypedMemoryNode::valueSummary)
                .orElse("");
    }

    private boolean isBlack(String value) {
        return value.equals("0") || value.equals("black") || value.equals("false");
    }

    private boolean isRed(String value) {
        return value.equals("1") || value.equals("red") || value.equals("true");
    }

    private String canonicalType(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return "";
        }
        String normalized = typeName.trim()
                .replaceAll("\\bstruct\\s+", "")
                .replaceAll("\\[[^]]*]", "")
                .replace("*", "")
                .replaceAll("\\s+", " ")
                .trim();
        int space = normalized.indexOf(' ');
        return space < 0 ? normalized : normalized.substring(space + 1).trim().toLowerCase(Locale.ROOT).equals("const")
                ? normalized.substring(0, space)
                : normalized;
    }
}
