package minic.compiler.ast;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 名称引用表达式。
 *
 * @param name 名称文本
 * @param range 表达式源码范围
 */
public record NameExpr(String name, SourceRange range) implements Expression {
    /**
     * 创建名称引用表达式。
     *
     * @param name 名称文本
     * @param range 表达式源码范围
     */
    public NameExpr {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(range, "range");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
