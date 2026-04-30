package minic.compiler.ast.stmt;

import minic.compiler.ast.expr.Expression;
import minic.source.SourceRange;

import java.util.Objects;
import java.util.Optional;

/**
 * if/else 语句 AST 节点。
 *
 * @param condition 条件表达式
 * @param thenBranch 条件为真时执行的语句
 * @param elseBranch 条件为假时执行的语句；无 else 时为 {@code null}
 * @param range 语句覆盖的源码范围
 */
public record IfStmt(Expression condition, Statement thenBranch, Statement elseBranch, SourceRange range)
        implements Statement {
    /**
     * 创建 if/else 语句。
     *
     * @param condition 条件表达式
     * @param thenBranch then 分支语句
     * @param elseBranch else 分支语句；可为 {@code null}
     * @param range 语句源码范围
     */
    public IfStmt {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(thenBranch, "thenBranch");
        Objects.requireNonNull(range, "range");
    }

    /**
     * 返回 else 分支。
     *
     * @return else 分支 Optional
     */
    public Optional<Statement> elseBranchOptional() {
        return Optional.ofNullable(elseBranch);
    }
}
