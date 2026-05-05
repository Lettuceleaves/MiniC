package minic.compiler.preprocess;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 预编译阶段记录的一条对象宏摘要。
 *
 * @param name 宏名称
 * @param replacement 宏替换文本
 * @param sourceRange 宏定义来源范围
 * @param defined 是否处于已定义状态
 */
public record MacroSummary(
        String name,
        String replacement,
        SourceRange sourceRange,
        boolean defined
) {
    /**
     * 创建对象宏摘要。
     *
     * @param name 宏名称
     * @param replacement 宏替换文本
     * @param sourceRange 宏定义来源范围
     * @param defined 是否处于已定义状态
     */
    public MacroSummary {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(replacement, "replacement");
        Objects.requireNonNull(sourceRange, "sourceRange");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
