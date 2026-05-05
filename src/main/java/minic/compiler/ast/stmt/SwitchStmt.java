package minic.compiler.ast.stmt;

import minic.compiler.ast.expr.Expression;
import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * switch 语句 AST 节点。
 *
 * @param selector selector 表达式
 * @param cases case/default 分支
 * @param range 源码范围
 */
public record SwitchStmt(Expression selector, List<SwitchCase> cases, SourceRange range) implements Statement {
    /**
     * 创建 switch 语句。
     *
     * @param selector selector 表达式
     * @param cases case/default 分支
     * @param range 源码范围
     */
    public SwitchStmt {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(cases, "cases");
        Objects.requireNonNull(range, "range");
        cases = List.copyOf(cases);
    }
}
