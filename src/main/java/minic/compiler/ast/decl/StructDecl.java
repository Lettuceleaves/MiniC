package minic.compiler.ast.decl;

import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * 结构体声明 AST 节点。
 *
 * @param name 结构体名
 * @param fields 字段声明列表
 * @param range 结构体声明覆盖的源码范围
 */
public record StructDecl(String name, List<StructField> fields, SourceRange range) {
    /**
     * 创建结构体声明节点，并防御性复制字段列表。
     *
     * @param name 结构体名
     * @param fields 字段声明列表
     * @param range 结构体声明覆盖的源码范围
     */
    public StructDecl {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(range, "range");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        fields = List.copyOf(fields);
    }
}
