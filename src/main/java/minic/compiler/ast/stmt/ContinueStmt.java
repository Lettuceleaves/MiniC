package minic.compiler.ast.stmt;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * continue 语句 AST 节点。
 *
 * @param range 语句覆盖的源码范围
 */
public record ContinueStmt(SourceRange range) implements Statement {
    /**
     * 创建 continue 语句。
     *
     * @param range 语句源码范围
     */
    public ContinueStmt {
        Objects.requireNonNull(range, "range");
    }
}
