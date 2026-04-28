package minic.compiler.ast.decl;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 函数形参 AST 节点。
 *
 * @param name 形参名
 * @param range 形参覆盖的源码范围
 */
public record Parameter(String name, SourceRange range) {
    /**
     * 创建函数形参节点。
     *
     * @param name 形参名
     * @param range 形参覆盖的源码范围
     */
    public Parameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(range, "range");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
