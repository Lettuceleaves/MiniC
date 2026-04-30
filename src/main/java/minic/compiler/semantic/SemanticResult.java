package minic.compiler.semantic;

import minic.compiler.ast.expr.Expression;
import minic.compiler.type.MiniType;
import minic.diagnostics.Diagnostic;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 语义分析结果。
 *
 * @param globalScope 全局函数作用域
 * @param expressionTypes 表达式类型映射
 * @param diagnostics 语义诊断列表
 */
public record SemanticResult(Scope globalScope, Map<Expression, MiniType> expressionTypes, List<Diagnostic> diagnostics) {
    /**
     * 创建语义分析结果，并防御性复制诊断列表。
     *
     * @param globalScope 全局函数作用域
     * @param expressionTypes 表达式类型映射
     * @param diagnostics 语义诊断列表
     */
    public SemanticResult {
        Objects.requireNonNull(globalScope, "globalScope");
        Objects.requireNonNull(expressionTypes, "expressionTypes");
        Objects.requireNonNull(diagnostics, "diagnostics");
        expressionTypes = Map.copyOf(expressionTypes);
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * 创建不携带表达式类型映射的语义分析结果。
     *
     * @param globalScope 全局函数作用域
     * @param diagnostics 语义诊断列表
     */
    public SemanticResult(Scope globalScope, List<Diagnostic> diagnostics) {
        this(globalScope, Map.of(), diagnostics);
    }

    /**
     * 查询表达式类型。
     *
     * @param expression 表达式节点
     * @return 表达式类型；未记录时为空
     */
    public Optional<MiniType> typeOf(Expression expression) {
        Objects.requireNonNull(expression, "expression");
        return Optional.ofNullable(expressionTypes.get(expression));
    }
}
