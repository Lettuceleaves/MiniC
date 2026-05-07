package minic.runtime.debug.visual;

import minic.source.SourceFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    /**
     * 解析源码中的 @visual 注释。
     *
     * @param sourceFile 源码文件
     * @return 解析结果
     */
    public VisualAnnotationParseResult parse(SourceFile sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        ArrayList<VisualAnnotation> annotations = new ArrayList<>();
        ArrayList<String> warnings = new ArrayList<>();
        String[] lines = sourceFile.content().replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            parseLine(lines[index], index + 1, annotations, warnings);
        }
        return new VisualAnnotationParseResult(annotations, warnings);
    }

    private void parseLine(
            String line,
            int lineNumber,
            ArrayList<VisualAnnotation> annotations,
            ArrayList<String> warnings
    ) {
        int comment = line.indexOf("//");
        if (comment < 0) {
            return;
        }
        String text = line.substring(comment + 2).trim();
        if (!text.startsWith("@visual")) {
            return;
        }
        String[] parts = text.split("\\s+");
        if (parts.length < 2 && (parts[0].equals("@visual") || parts[0].equals("@visual-map"))) {
            warnings.add("第 " + lineNumber + " 行 @visual 声明缺少类型");
            return;
        }
        String directive = parts[0];
        String structureType = directive.equals("@visual") || directive.equals("@visual-map") ? parts[1] : "graph";
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        int attributeStart = directive.equals("@visual") || directive.equals("@visual-map") ? 2 : 1;
        for (int i = attributeStart; i < parts.length; i++) {
            int split = parts[i].indexOf('=');
            if (split <= 0 || split == parts[i].length() - 1) {
                warnings.add("第 " + lineNumber + " 行忽略非法属性：" + parts[i]);
                continue;
            }
            attributes.put(parts[i].substring(0, split), parts[i].substring(split + 1));
        }
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
}
