package minic.uiapi;

import minic.compiler.semantic.Scope;
import minic.compiler.semantic.SemanticAction;
import minic.compiler.semantic.Symbol;

import java.util.ArrayList;
import java.util.List;

/**
 * 将内部 Scope 树转换为 UI 专用作用域 DTO。
 */
final class UiSemanticScopeVisualBuilder {
    private int nextId;

    UiSemanticScopeVisualDto build(Scope globalScope, SemanticAction currentAction) {
        return buildScope(globalScope, "global scope", currentAction == null || currentAction.subject().contains("main"), currentAction);
    }

    private UiSemanticScopeVisualDto buildScope(
            Scope scope,
            String fallbackLabel,
            boolean active,
            SemanticAction currentAction
    ) {
        String id = nextId == 0 ? "scope-global" : "scope-" + nextId;
        nextId++;
        List<String> symbols = scope.symbols().stream()
                .map(this::symbolSummary)
                .toList();
        ArrayList<UiSemanticScopeVisualDto> children = new ArrayList<>();
        int childIndex = 0;
        for (Scope child : scope.children()) {
            children.add(buildScope(child, "scope " + childIndex, childActive(child, currentAction), currentAction));
            childIndex++;
        }
        return new UiSemanticScopeVisualDto(id, fallbackLabel, symbols, scopeRange(scope), active, children);
    }

    private boolean childActive(Scope scope, SemanticAction currentAction) {
        if (currentAction == null) {
            return false;
        }
        return scope.symbols().stream().anyMatch(symbol -> symbol.name().equals(currentAction.subject()));
    }

    private String symbolSummary(Symbol symbol) {
        String arity = symbol.arity() == null ? "" : "/" + symbol.arity();
        return symbol.kind() + " " + symbol.name() + arity + " : " + symbol.type();
    }

    private UiSourceSpanDto scopeRange(Scope scope) {
        return scope.symbols().stream()
                .findFirst()
                .map(Symbol::declarationRange)
                .map(UiSourceSpanDto::from)
                .orElse(null);
    }
}
