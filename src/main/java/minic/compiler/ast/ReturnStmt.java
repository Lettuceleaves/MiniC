package minic.compiler.ast;

import minic.source.SourceRange;

import java.util.Objects;
import java.util.Optional;

/**
 * return 语句 AST 节点。
 *
 * @param expressionRange 返回表达式源码范围；不存在时为 {@code null}
 * @param range return 语句覆盖的源码范围
 */
public record ReturnStmt(SourceRange expressionRange, SourceRange range) implements Statement {
    /**
     * 创建 return 语句节点。
     *
     * @param expressionRange 返回表达式源码范围；不存在时为 {@code null}
     * @param range return 语句覆盖的源码范围
     */
    public ReturnStmt {
        Objects.requireNonNull(range, "range");
    }

    /**
     * 以 {@link Optional} 形式返回返回表达式范围。
     *
     * @return 返回表达式范围；不存在时为空
     */
    public Optional<SourceRange> expressionRangeOptional() {
        return Optional.ofNullable(expressionRange);
    }
}
