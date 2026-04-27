package minic.compiler.ast;

import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * block 语句 AST 节点。
 *
 * @param statements block 内部语句列表
 * @param range block 覆盖的源码范围
 */
public record BlockStmt(List<Statement> statements, SourceRange range) implements Statement {
    /**
     * 创建 block 语句节点，并防御性复制语句列表。
     *
     * @param statements block 内部语句列表
     * @param range block 覆盖的源码范围
     */
    public BlockStmt {
        Objects.requireNonNull(statements, "statements");
        Objects.requireNonNull(range, "range");
        statements = List.copyOf(statements);
    }
}
