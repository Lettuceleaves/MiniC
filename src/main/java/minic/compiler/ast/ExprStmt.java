package minic.compiler.ast;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 表达式语句 AST 节点。
 *
 * @param expressionRange 表达式源码范围
 * @param range 表达式语句覆盖的源码范围
 */
public record ExprStmt(SourceRange expressionRange, SourceRange range) implements Statement {
    /**
     * 创建表达式语句节点。
     *
     * @param expressionRange 表达式源码范围
     * @param range 表达式语句覆盖的源码范围
     */
    public ExprStmt {
        Objects.requireNonNull(expressionRange, "expressionRange");
        Objects.requireNonNull(range, "range");
    }
}
