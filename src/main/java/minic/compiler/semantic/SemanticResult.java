package minic.compiler.semantic;

import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Declaration.Program;
import minic.compiler.semantic.model.Scope;
import minic.compiler.semantic.model.SemanticAction;
import minic.compiler.semantic.model.Symbol;
import minic.compiler.semantic.model.StructLayout;
import minic.compiler.type.MiniType;
import minic.compiler.Stage;
import minic.SourceRange;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 语义分析结果。
 *
 * @param program 当前完整 AST
 * @param globalScope 语义分析内部使用的全局作用域
 * @param scopeSnapshot 供逐步可视化使用的不可变作用域快照
 * @param expressionTypes 表达式类型映射
 * @param structLayouts 结构体布局映射
 * @param action 本次 step 执行的语义动作；阶段结束空步骤时为空
 */
public record SemanticResult(
        Program program,
        Scope globalScope,
        ScopeSnapshot scopeSnapshot,
        Map<Expression, MiniType> expressionTypes,
        Map<String, StructLayout> structLayouts,
        SemanticAction action
) implements Stage.Context {
    /**
     * 创建语义分析结果，并防御性复制映射。
     *
     * @param program 当前完整 AST
     * @param globalScope 语义分析内部使用的全局作用域
     * @param scopeSnapshot 不可变作用域快照
     * @param expressionTypes 表达式类型映射
     * @param structLayouts 结构体布局映射
     * @param action 当前语义动作
     */
    public SemanticResult {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(globalScope, "globalScope");
        Objects.requireNonNull(scopeSnapshot, "scopeSnapshot");
        Objects.requireNonNull(expressionTypes, "expressionTypes");
        Objects.requireNonNull(structLayouts, "structLayouts");
        expressionTypes = java.util.Collections.unmodifiableMap(
                new java.util.IdentityHashMap<>(expressionTypes)
        );
        structLayouts = Map.copyOf(structLayouts);
    }

    /** 返回本次 step 的语义动作。 */
    public Optional<SemanticAction> actionOptional() {
        return Optional.ofNullable(action);
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

    /**
     * 查询结构体布局。
     *
     * @param name 结构体名
     * @return 结构体布局；不存在时为空
     */
    public Optional<StructLayout> structLayout(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(structLayouts.get(name));
    }

    /**
     * 作用域树的不可变快照。不保留 parent 反向引用，避免循环；
     * UI 可以从根节点递归展示符号和子作用域。
     */
    public record ScopeSnapshot(
            SourceRange range,
            List<Symbol> symbols,
            List<ScopeSnapshot> children
    ) {
        public ScopeSnapshot {
            Objects.requireNonNull(symbols, "symbols");
            Objects.requireNonNull(children, "children");
            symbols = List.copyOf(symbols);
            children = List.copyOf(children);
        }

        public static ScopeSnapshot from(Scope scope) {
            Objects.requireNonNull(scope, "scope");
            return new ScopeSnapshot(
                    scope.range().orElse(null),
                    scope.symbols(),
                    scope.children().stream().map(ScopeSnapshot::from).toList()
            );
        }

        public Optional<SourceRange> rangeOptional() {
            return Optional.ofNullable(range);
        }
    }
}
