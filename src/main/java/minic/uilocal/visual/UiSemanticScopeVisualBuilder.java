package minic.uilocal;

import minic.compiler.SourceFile;
import minic.compiler.semantic.model.Scope;
import minic.compiler.semantic.model.SemanticAction;
import minic.compiler.semantic.model.Symbol;

import java.util.ArrayList;
import java.util.List;

/**
 * 将内部 Scope 树转换为 UI 专用作用域 DTO。
 */
final class UiSemanticScopeVisualBuilder {
    private final SourceFile sourceFile;
    private int nextId;

    UiSemanticScopeVisualBuilder(SourceFile sourceFile) {
        this.sourceFile = sourceFile;
    }

    UiSemanticScopeVisualDto build(Scope globalScope, SemanticAction currentAction) {
        Scope activeScope = currentAction == null ? null : currentAction.scope();
        return buildScope(globalScope, "global scope", globalScope == activeScope, activeScope);
    }

    private UiSemanticScopeVisualDto buildScope(
            Scope scope,
            String fallbackLabel,
            boolean active,
            Scope activeScope
    ) {
        String id = nextId == 0 ? "scope-global" : "scope-" + nextId;
        nextId++;
        List<String> symbols = scope.symbols().stream()
                .map(this::symbolSummary)
                .toList();
        ArrayList<UiSemanticScopeVisualDto> children = new ArrayList<>();
        int childIndex = 0;
        for (Scope child : scope.children()) {
            children.add(buildScope(child, "scope " + childIndex, child == activeScope, activeScope));
            childIndex++;
        }
        return new UiSemanticScopeVisualDto(id, fallbackLabel, symbols, scopeRange(scope), active, children);
    }

    private String symbolSummary(Symbol symbol) {
        String arity = symbol.arity() == null ? "" : "/" + symbol.arity();
        return symbol.kind() + " " + symbol.name() + arity + " : " + symbol.type();
    }

    private UiSourceSpanDto scopeRange(Scope scope) {
        return scope.range()
                .map(range -> UiSourceSpanDto.from(sourceFile, range))
                .or(() -> scope.symbols().stream()
                .findFirst()
                .map(Symbol::declarationRange)
                .map(range -> UiSourceSpanDto.from(sourceFile, range)))
                .orElse(null);
    }
}
