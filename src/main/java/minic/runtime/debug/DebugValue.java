package minic.runtime.debug;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Debugger 运行时值。
 *
 * @param kind 值类别
 * @param typeName 类型名
 * @param summary 稳定摘要
 * @param pointerTarget 指针目标地址；非指针时为 {@code null}
 * @param elements 数组元素
 * @param fields 结构体字段
 */
public record DebugValue(
        DebugValueKind kind,
        String typeName,
        String summary,
        DebugVirtualAddress pointerTarget,
        List<DebugValueElement> elements,
        List<DebugValueField> fields
) {
    /**
     * 创建 Debug 值。
     */
    public DebugValue {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(typeName, "typeName");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(elements, "elements");
        Objects.requireNonNull(fields, "fields");
        if (typeName.isBlank()) {
            throw new IllegalArgumentException("typeName must not be blank");
        }
        elements = List.copyOf(elements);
        fields = List.copyOf(fields);
    }

    /**
     * 创建 int 值。
     *
     * @param value 值
     * @return Debug 值
     */
    public static DebugValue intValue(int value) {
        return scalar(DebugValueKind.INT, "int", Integer.toString(value));
    }

    /**
     * 创建 long 值。
     *
     * @param value 值
     * @return Debug 值
     */
    public static DebugValue longValue(long value) {
        return scalar(DebugValueKind.LONG, "long", Long.toString(value));
    }

    /**
     * 创建 char 值。
     *
     * @param value 值
     * @return Debug 值
     */
    public static DebugValue charValue(char value) {
        return scalar(DebugValueKind.CHAR, "char", "'" + value + "'");
    }

    /**
     * 创建 bool 值。
     *
     * @param value 值
     * @return Debug 值
     */
    public static DebugValue boolValue(boolean value) {
        return scalar(DebugValueKind.BOOL, "bool", Boolean.toString(value));
    }

    /**
     * 创建指针值。
     *
     * @param typeName 指针类型名
     * @param target 目标地址
     * @return Debug 值
     */
    public static DebugValue pointerValue(String typeName, DebugVirtualAddress target) {
        Objects.requireNonNull(target, "target");
        return new DebugValue(DebugValueKind.POINTER, typeName, target.display(), target, List.of(), List.of());
    }

    /**
     * 创建数组值。
     *
     * @param typeName 数组类型名
     * @param elements 元素
     * @return Debug 值
     */
    public static DebugValue arrayValue(String typeName, List<DebugValueElement> elements) {
        return new DebugValue(DebugValueKind.ARRAY, typeName, "array[" + elements.size() + "]", null, elements, List.of());
    }

    /**
     * 创建结构体值。
     *
     * @param typeName 结构体类型名
     * @param fields 字段
     * @return Debug 值
     */
    public static DebugValue structValue(String typeName, List<DebugValueField> fields) {
        return new DebugValue(DebugValueKind.STRUCT, typeName, typeName + "{" + fields.size() + " fields}", null, List.of(), fields);
    }

    /**
     * 创建 null 值。
     *
     * @param typeName 类型名
     * @return Debug 值
     */
    public static DebugValue nullValue(String typeName) {
        return new DebugValue(DebugValueKind.NULL, typeName, "null", null, List.of(), List.of());
    }

    /**
     * 创建未初始化值。
     *
     * @param typeName 类型名
     * @return Debug 值
     */
    public static DebugValue uninitialized(String typeName) {
        return new DebugValue(DebugValueKind.UNINITIALIZED, typeName, "<uninitialized>", null, List.of(), List.of());
    }

    private static DebugValue scalar(DebugValueKind kind, String typeName, String summary) {
        return new DebugValue(kind, typeName, summary, null, List.of(), List.of());
    }

    /**
     * 返回指针目标地址。
     *
     * @return 指针目标地址 Optional
     */
    public Optional<DebugVirtualAddress> pointerTargetOptional() {
        return Optional.ofNullable(pointerTarget);
    }
}
