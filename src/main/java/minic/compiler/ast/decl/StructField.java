package minic.compiler.ast.decl;

import minic.compiler.type.MiniType;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 结构体字段声明 AST 节点。
 *
 * @param name 字段名
 * @param type 字段类型
 * @param range 字段声明覆盖的源码范围
 */
public record StructField(String name, MiniType type, SourceRange range) {
    /**
     * 创建结构体字段声明节点。
     *
     * @param name 字段名
     * @param type 字段类型
     * @param range 字段声明覆盖的源码范围
     */
    public StructField {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(range, "range");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
