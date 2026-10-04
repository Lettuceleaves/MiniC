package craken.compiler.semantic;

import craken.compiler.parser.node.Expression;
import craken.compiler.parser.node.AstNode;
import craken.compiler.parser.node.Declaration.Program;
import craken.compiler.semantic.model.Scope;
import craken.compiler.semantic.model.SemanticAction;
import craken.compiler.semantic.model.Symbol;
import craken.compiler.semantic.model.StructLayout;
import craken.compiler.type.CrakenType;
import craken.compiler.Stage;
import craken.SourceRange;

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
        Map<Expression, CrakenType> expressionTypes,
        Map<String, StructLayout> structLayouts,
        SemanticAction action,
        Program sourceProgram,
        Map<AstNode, AstNode> sourceToCore,
        Map<String, String> displayNames
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
        Objects.requireNonNull(sourceProgram, "sourceProgram");
        sourceToCore = java.util.Collections.unmodifiableMap(new java.util.IdentityHashMap<>(sourceToCore));
        displayNames = Map.copyOf(displayNames);
    }

    public SemanticResult(Program program, Scope globalScope, ScopeSnapshot scopeSnapshot,
                          Map<Expression, CrakenType> expressionTypes, Map<String, StructLayout> structLayouts,
                          SemanticAction action) {
        this(program, globalScope, scopeSnapshot, expressionTypes, structLayouts, action, program, Map.of(), Map.of());
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
    public Optional<CrakenType> typeOf(Expression expression) {
        Objects.requireNonNull(expression, "expression");
        CrakenType type = expressionTypes.get(expression);
        if (type == null && sourceToCore.get(expression) instanceof Expression core) {
            type = expressionTypes.get(core);
        }
        return Optional.ofNullable(type);
    }

    /**
     * 查询结构体布局。
     *
     * @param name 结构体名
     * @return 结构体布局；不存在时为空
     */
    public Optional<StructLayout> structLayout(String name) {
        Objects.requireNonNull(name, "name");
        StructLayout layout = structLayouts.get(name);
        if (layout != null) return Optional.of(layout);
        // Name binding gives records internal names; accept the source spelling too.
        return displayNames.entrySet().stream()
                .filter(entry -> entry.getValue().equals(name) && structLayouts.containsKey(entry.getKey()))
                .map(entry -> structLayouts.get(entry.getKey()))
                .findFirst();
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
            return from(scope, Map.of());
        }

        public static ScopeSnapshot from(Scope scope, Map<String, String> displayNames) {
            Objects.requireNonNull(scope, "scope");
            return new ScopeSnapshot(
                    scope.range().orElse(null),
                    scope.symbols().stream().map(symbol -> new Symbol(
                            displayNames.getOrDefault(symbol.name(), symbol.name()), symbol.kind(),
                            symbol.declarationRange(), symbol.type(), symbol.arity())).toList(),
                    scope.children().stream().map(child -> from(child, displayNames)).toList()
            );
        }

        public Optional<SourceRange> rangeOptional() {
            return Optional.ofNullable(range);
        }
    }
}
