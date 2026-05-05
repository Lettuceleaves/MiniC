package minic.compiler.ast.expr;

import minic.compiler.type.MiniType;
import minic.source.SourceRange;

import java.util.Objects;
import java.util.Optional;

/**
 * {@code sizeof} 表达式。
 *
 * @param expression 被查询表达式；查询类型时为 {@code null}
 * @param queriedType 被查询类型；查询表达式时为 {@code null}
 * @param range 表达式源码范围
 */
public record SizeofExpr(Expression expression, MiniType queriedType, SourceRange range) implements Expression {
    /**
     * 创建 sizeof 表达式。
     *
     * @param expression 被查询表达式；查询类型时为 {@code null}
     * @param queriedType 被查询类型；查询表达式时为 {@code null}
     * @param range 表达式源码范围
     */
    public SizeofExpr {
        Objects.requireNonNull(range, "range");
        if ((expression == null) == (queriedType == null)) {
            throw new IllegalArgumentException("sizeof must query exactly one expression or type");
        }
    }

    /**
     * 返回被查询表达式。
     *
     * @return 表达式；不存在时为空
     */
    public Optional<Expression> expressionOptional() {
        return Optional.ofNullable(expression);
    }

    /**
     * 返回被查询类型。
     *
     * @return 类型；不存在时为空
     */
    public Optional<MiniType> queriedTypeOptional() {
        return Optional.ofNullable(queriedType);
    }
}
