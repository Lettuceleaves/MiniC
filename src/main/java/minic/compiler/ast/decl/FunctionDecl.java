package minic.compiler.ast.decl;

import minic.compiler.ast.stmt.BlockStmt;
import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 函数声明/定义 AST 节点。
 *
 * @param name 函数名
 * @param parameters 形参列表
 * @param body 函数体 block；声明节点为 {@code null}
 * @param external 是否为外部函数声明
 * @param range 函数声明覆盖的源码范围
 */
public record FunctionDecl(String name, List<Parameter> parameters, BlockStmt body, boolean external, SourceRange range) {
    /**
     * 创建函数声明节点，并防御性复制形参列表。
     *
     * @param name 函数名
     * @param parameters 形参列表
     * @param body 函数体 block；声明节点为 {@code null}
     * @param external 是否为外部函数声明
     * @param range 函数声明覆盖的源码范围
     */
    public FunctionDecl {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(range, "range");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        parameters = List.copyOf(parameters);
    }

    /**
     * 创建不携带函数体的函数声明节点。
     *
     * @param name 函数名
     * @param parameters 形参列表
     * @param range 函数声明覆盖的源码范围
     */
    public FunctionDecl(String name, List<Parameter> parameters, SourceRange range) {
        this(name, parameters, null, false, range);
    }

    /**
     * 创建不携带函数体的函数声明节点。
     *
     * @param name 函数名
     * @param parameters 形参列表
     * @param external 是否为外部函数声明
     * @param range 函数声明覆盖的源码范围
     */
    public FunctionDecl(String name, List<Parameter> parameters, boolean external, SourceRange range) {
        this(name, parameters, null, external, range);
    }

    /**
     * 返回函数体；纯声明节点返回空 Optional。
     *
     * @return 函数体 Optional
     */
    public Optional<BlockStmt> bodyOptional() {
        return Optional.ofNullable(body);
    }

    /**
     * 判断当前节点是否为带函数体的定义。
     *
     * @return 有函数体时为 {@code true}
     */
    public boolean hasBody() {
        return body != null;
    }
}
