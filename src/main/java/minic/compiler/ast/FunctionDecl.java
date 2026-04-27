package minic.compiler.ast;

import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * 函数声明 AST 节点。
 *
 * @param name 函数名
 * @param parameters 形参列表
 * @param body 函数体 block
 * @param range 函数声明覆盖的源码范围
 */
public record FunctionDecl(String name, List<Parameter> parameters, BlockStmt body, SourceRange range) {
    /**
     * 创建函数声明节点，并防御性复制形参列表。
     *
     * @param name 函数名
     * @param parameters 形参列表
     * @param body 函数体 block
     * @param range 函数声明覆盖的源码范围
     */
    public FunctionDecl {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(range, "range");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        parameters = List.copyOf(parameters);
    }

    /**
     * 创建暂不携带函数体的函数声明节点。
     *
     * <p>该构造方法用于早期测试和迁移场景，会使用函数范围构造一个空 block。</p>
     *
     * @param name 函数名
     * @param parameters 形参列表
     * @param range 函数声明覆盖的源码范围
     */
    public FunctionDecl(String name, List<Parameter> parameters, SourceRange range) {
        this(name, parameters, new BlockStmt(List.of(), range), range);
    }
}
