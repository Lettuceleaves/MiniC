package minic.runtime.debug.visual;

import minic.source.SourceFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @visual 注释协议解析器。
 */
public final class VisualAnnotationParser {
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern SIMPLE_VALUE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*|-?[0-9]+|true|false");
    private static final Pattern VISUAL_PATH = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(->[A-Za-z_][A-Za-z0-9_]*)*|-?[0-9]+|true|false|null");
    private static final List<String> SUPPORTED_LAYOUTS = List.of("natural", "unidirectional");

    /**
     * 解析源码中的 @visual 注释。
     *
     * @param sourceFile 源码文件
     * @return 解析结果
     */
    public VisualAnnotationParseResult parse(SourceFile sourceFile) {
        ParsedVisualAnnotations parsed = parseAll(sourceFile);
        return new VisualAnnotationParseResult(parsed.annotations(), parsed.specs(), parsed.warnings());
    }

    /**
     * 解析源码中的简化 @visual DSL 声明。
     *
     * @param sourceFile 源码文件
     * @return 规范化视觉声明
     */
    public List<VisualSpec> specs(SourceFile sourceFile) {
        return parse(sourceFile).specs();
    }

    private ParsedVisualAnnotations parseAll(SourceFile sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        ArrayList<VisualAnnotation> annotations = new ArrayList<>();
        ArrayList<VisualSpec> specs = new ArrayList<>();
        ArrayList<String> warnings = new ArrayList<>();
        String[] lines = sourceFile.content().replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            parseLine(lines[index], index + 1, annotations, specs, warnings);
        }
        return new ParsedVisualAnnotations(annotations, specs, warnings);
    }

    private void parseLine(
            String line,
            int lineNumber,
            ArrayList<VisualAnnotation> annotations,
            ArrayList<VisualSpec> specs,
            ArrayList<String> warnings
    ) {
        int comment = line.indexOf("//");
        if (comment < 0) {
            return;
        }
        String text = line.substring(comment + 2).trim();
        if (text.startsWith("@style")) {
            parseStyleLine(text, lineNumber, specs, warnings);
            return;
        }
        if (!text.startsWith("@visual")) {
            return;
        }
        String[] parts = text.split("\\s+");
        if (parts.length < 2 && (parts[0].equals("@visual") || parts[0].equals("@visual-map"))) {
            warnings.add("第 " + lineNumber + " 行 @visual 声明缺少类型");
            return;
        }
        String directive = parts[0];
        if (directive.equals("@visual") && parts.length >= 2 && parts[1].contains("=")) {
            LinkedHashMap<String, String> attributes = parseAttributes(parts, 1, lineNumber, warnings);
            VisualSpec spec = parseSpec(attributes, lineNumber, warnings);
            if (spec != null) {
                specs.add(spec);
            }
            return;
        }
        String structureType = directive.equals("@visual") || directive.equals("@visual-map") ? parts[1] : "graph";
        int attributeStart = directive.equals("@visual") || directive.equals("@visual-map") ? 2 : 1;
        LinkedHashMap<String, String> attributes = parseAttributes(parts, attributeStart, lineNumber, warnings);
        if (!validate(directive, structureType, attributes, lineNumber, warnings)) {
            return;
        }
        annotations.add(new VisualAnnotation(
                directive,
                structureType,
                name(directive, attributes),
                lineNumber,
                attributes
        ));
    }

    private void parseStyleLine(
            String text,
            int lineNumber,
            ArrayList<VisualSpec> specs,
            ArrayList<String> warnings
    ) {
        String[] parts = text.split("\\s+");
        if (!parts[0].equals("@style")) {
            return;
        }
        if (specs.isEmpty()) {
            warnings.add("第 " + lineNumber + " 行 @style 必须跟在 @visual 之后");
            return;
        }
        LinkedHashMap<String, String> attributes = parseAttributes(parts, 1, lineNumber, warnings);
        String type = attributes.get("type");
        if (type == null || type.isBlank()) {
            warnings.add("第 " + lineNumber + " 行 @style 缺少 type");
            return;
        }
        if (attributes.keySet().stream().anyMatch(key -> !key.equals("type") && !key.equals("template"))) {
            warnings.add("第 " + lineNumber + " 行 @style 只允许 type 和 template");
            return;
        }
        if (!attributes.containsKey("template") || attributes.get("template").isBlank()) {
            warnings.add("第 " + lineNumber + " 行 @style 缺少 template");
            return;
        }
        if (!specs.getLast().styleRules().isEmpty()) {
            warnings.add("第 " + lineNumber + " 行每个 @visual 只允许一条 @style");
            return;
        }
        VisualStyleRule rule = new VisualStyleRule(
                type,
                attributes.get("template"),
                lineNumber
        );
        specs.set(specs.size() - 1, specs.getLast().withStyleRule(rule));
    }

    private LinkedHashMap<String, String> parseAttributes(
            String[] parts,
            int attributeStart,
            int lineNumber,
            ArrayList<String> warnings
    ) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        for (int i = attributeStart; i < parts.length; i++) {
            int split = parts[i].indexOf('=');
            if (split <= 0 || split == parts[i].length() - 1) {
                warnings.add("第 " + lineNumber + " 行忽略非法属性：" + parts[i]);
                continue;
            }
            attributes.put(parts[i].substring(0, split), parts[i].substring(split + 1));
        }
        return attributes;
    }

    private VisualSpec parseSpec(
            Map<String, String> attributes,
            int lineNumber,
            ArrayList<String> warnings
    ) {
        List<String> roots = parseRoots(attributes.get("roots"), lineNumber, warnings);
        String root = attributes.get("root");
        if (root == null && !roots.isEmpty()) {
            root = roots.getFirst();
        }
        if (root == null) {
            warnings.add("第 " + lineNumber + " 行 @visual 缺少 root 或 roots");
            return null;
        }
        if (!VARIABLE_NAME.matcher(root).matches()) {
            warnings.add("第 " + lineNumber + " 行 root 只允许变量名：" + root);
            return null;
        }
        String layout = attributes.getOrDefault("layout", "natural");
        if (!SUPPORTED_LAYOUTS.contains(layout)) {
            warnings.add("第 " + lineNumber + " 行 layout 不支持：" + layout);
            return null;
        }
        String rawKind = attributes.getOrDefault("kind", "auto");
        VisualKind kind = VisualKind.parse(rawKind).orElseGet(() -> {
            warnings.add("第 " + lineNumber + " 行 kind 不支持：" + rawKind);
            return VisualKind.AUTO;
        });
        return new VisualSpec(
                attributes.getOrDefault("name", root),
                root,
                kind,
                attributes,
                VisualSpec.parseFields(attributes.get("fields")),
                lineNumber
        );
    }

    private List<String> parseRoots(String value, int lineNumber, ArrayList<String> warnings) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String normalized = value.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        ArrayList<String> roots = new ArrayList<>();
        for (String root : normalized.split(",")) {
            String trimmed = root.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!VARIABLE_NAME.matcher(trimmed).matches()) {
                warnings.add("第 " + lineNumber + " 行 roots 只允许变量名：" + trimmed);
                return List.of();
            }
            roots.add(trimmed);
        }
        if (roots.isEmpty()) {
            warnings.add("第 " + lineNumber + " 行 roots 至少需要一个变量名");
        }
        return roots;
    }

    private boolean validate(
            String directive,
            String structureType,
            Map<String, String> attributes,
            int lineNumber,
            ArrayList<String> warnings
    ) {
        if (directive.equals("@visual")) {
            if (!structureType.equals("graph") && !structureType.equals("array") && !structureType.equals("composite")) {
                warnings.add("第 " + lineNumber + " 行 @visual 类型不支持：" + structureType);
                return false;
            }
            String root = attributes.get("root");
            if (root != null && !VARIABLE_NAME.matcher(root).matches()) {
                warnings.add("第 " + lineNumber + " 行 root 只允许变量名：" + root);
                return false;
            }
            return true;
        }
        if (directive.equals("@visual-node")) {
            return validateSimple(attributes, "id", lineNumber, warnings);
        }
        if (directive.equals("@visual-edge")) {
            return validateSimple(attributes, "from", lineNumber, warnings)
                    && validateSimple(attributes, "to", lineNumber, warnings);
        }
        if (directive.equals("@visual-map")) {
            return validateVisualMap(structureType, attributes, lineNumber, warnings);
        }
        warnings.add("第 " + lineNumber + " 行 @visual 指令不支持：" + directive);
        return false;
    }

    private boolean validateVisualMap(
            String mapType,
            Map<String, String> attributes,
            int lineNumber,
            ArrayList<String> warnings
    ) {
        if (!mapType.equals("node") && !mapType.equals("edge") && !mapType.equals("meta")) {
            warnings.add("第 " + lineNumber + " 行 @visual-map 类型不支持：" + mapType);
            return false;
        }
        if (mapType.equals("node")) {
            return validateVisualPath(attributes, "id", lineNumber, warnings);
        }
        if (mapType.equals("edge")) {
            return validateVisualPath(attributes, "from", lineNumber, warnings)
                    && validateVisualPath(attributes, "to", lineNumber, warnings);
        }
        return validateVisualPath(attributes, "node", lineNumber, warnings)
                && validateSimple(attributes, "key", lineNumber, warnings)
                && validateVisualPath(attributes, "value", lineNumber, warnings);
    }

    private boolean validateVisualPath(
            Map<String, String> attributes,
            String key,
            int lineNumber,
            ArrayList<String> warnings
    ) {
        String value = attributes.get(key);
        if (value == null) {
            warnings.add("第 " + lineNumber + " 行缺少 " + key);
            return false;
        }
        if (!VISUAL_PATH.matcher(value).matches()) {
            warnings.add("第 " + lineNumber + " 行 " + key + " 只允许变量名、字面值或 -> 字段路径：" + value);
            return false;
        }
        return true;
    }

    private boolean validateSimple(
            Map<String, String> attributes,
            String key,
            int lineNumber,
            ArrayList<String> warnings
    ) {
        String value = attributes.get(key);
        if (value == null) {
            warnings.add("第 " + lineNumber + " 行缺少 " + key);
            return false;
        }
        if (!SIMPLE_VALUE.matcher(value).matches()) {
            warnings.add("第 " + lineNumber + " 行 " + key + " 只允许简单变量名或字面值：" + value);
            return false;
        }
        return true;
    }

    private String name(String directive, Map<String, String> attributes) {
        if (directive.equals("@visual-node") || directive.equals("@visual-edge") || directive.equals("@visual-map")) {
            return attributes.getOrDefault("graph", "default");
        }
        return attributes.getOrDefault("name", attributes.getOrDefault("root", "visual"));
    }

    private record ParsedVisualAnnotations(
            List<VisualAnnotation> annotations,
            List<VisualSpec> specs,
            List<String> warnings
    ) {
        private ParsedVisualAnnotations {
            annotations = List.copyOf(annotations);
            specs = List.copyOf(specs);
            warnings = List.copyOf(warnings);
        }
    }
}
