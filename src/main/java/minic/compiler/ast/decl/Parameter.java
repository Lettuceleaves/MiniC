package minic.compiler.ast.decl;

import minic.compiler.type.MiniType;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 函数形参 AST 节点。
 *
 * @param name 形参名
 * @param type 形参类型
 * @param range 形参覆盖的源码范围
 */
public record Parameter(String name, MiniType type, SourceRange range) {
    /**
     * 创建函数形参节点。
     *
     * @param name 形参名
     * @param type 形参类型
     * @param range 形参覆盖的源码范围
     */
    public Parameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(range, "range");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    /**
     * 创建 int 类型形参。
     *
     * @param name 形参名
     * @param range 形参覆盖的源码范围
     */
    public Parameter(String name, SourceRange range) {
        this(name, MiniType.INT, range);
    }
}
