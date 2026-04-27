package minic.compiler.ast;

import minic.source.SourceRange;

import java.util.Objects;
import java.util.Optional;

/**
 * 局部变量声明语句 AST 节点。
 *
 * @param name 变量名
 * @param initializerRange 初始化表达式源码范围；不存在时为 {@code null}
 * @param range 变量声明语句覆盖的源码范围
 */
public record VarDeclStmt(String name, SourceRange initializerRange, SourceRange range) implements Statement {
    /**
     * 创建局部变量声明语句节点。
     *
     * @param name 变量名
     * @param initializerRange 初始化表达式源码范围；不存在时为 {@code null}
     * @param range 变量声明语句覆盖的源码范围
     */
    public VarDeclStmt {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(range, "range");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    /**
     * 以 {@link Optional} 形式返回初始化表达式范围。
     *
     * @return 初始化表达式范围；不存在时为空
     */
    public Optional<SourceRange> initializerRangeOptional() {
        return Optional.ofNullable(initializerRange);
    }
}
