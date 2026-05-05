package minic.compiler.ast.stmt;

import minic.compiler.ast.expr.Expression;
import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * switch 的一个 case/default 分支。
 *
 * @param value case 表达式；default 分支为 {@code null}
 * @param statements 分支语句列表
 * @param range 源码范围
 */
public record SwitchCase(Expression value, List<Statement> statements, SourceRange range) {
    /**
     * 创建 switch 分支。
     *
     * @param value case 表达式；default 分支为 {@code null}
     * @param statements 分支语句列表
     * @param range 源码范围
     */
    public SwitchCase {
        Objects.requireNonNull(statements, "statements");
        Objects.requireNonNull(range, "range");
        statements = List.copyOf(statements);
    }

    /**
     * 返回 case 表达式。
     *
     * @return case 表达式；default 为空
     */
    public Optional<Expression> valueOptional() {
        return Optional.ofNullable(value);
    }

    /**
     * 是否为 default 分支。
     *
     * @return default 分支返回 true
     */
    public boolean defaultCase() {
        return value == null;
    }
}
