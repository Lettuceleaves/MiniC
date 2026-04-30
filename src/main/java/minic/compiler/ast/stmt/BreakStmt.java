package minic.compiler.ast.stmt;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * break 语句 AST 节点。
 *
 * @param range 语句覆盖的源码范围
 */
public record BreakStmt(SourceRange range) implements Statement {
    /**
     * 创建 break 语句。
     *
     * @param range 语句源码范围
     */
    public BreakStmt {
        Objects.requireNonNull(range, "range");
    }
}
