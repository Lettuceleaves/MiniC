package minic.compiler.semantic;

import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScopeTest {
    @Test
    void definesAndResolvesLocalSymbols() {
        Scope scope = new Scope();
        Symbol symbol = symbol("main", SymbolKind.FUNCTION);

        assertThat(scope.define(symbol)).isTrue();

        assertThat(scope.resolve("main")).contains(symbol);
        assertThat(scope.resolveLocal("main")).contains(symbol);
    }

    @Test
    void rejectsDuplicateDefinitionsInSameScope() {
        Scope scope = new Scope();
        Symbol first = symbol("x", SymbolKind.VARIABLE);
        Symbol duplicate = symbol("x", SymbolKind.VARIABLE);

        assertThat(scope.define(first)).isTrue();
        assertThat(scope.define(duplicate)).isFalse();

        assertThat(scope.resolve("x")).contains(first);
    }

    @Test
    void resolvesSymbolsFromParentScope() {
        Scope parent = new Scope();
        Scope child = new Scope(parent);
        Symbol symbol = symbol("x", SymbolKind.VARIABLE);

        parent.define(symbol);

        assertThat(child.parent()).contains(parent);
        assertThat(child.resolve("x")).contains(symbol);
        assertThat(child.resolveLocal("x")).isEmpty();
    }

    @Test
    void allowsChildScopeToShadowParentSymbol() {
        Scope parent = new Scope();
        Scope child = new Scope(parent);
        Symbol outer = symbol("x", SymbolKind.VARIABLE);
        Symbol inner = symbol("x", SymbolKind.VARIABLE);

        parent.define(outer);
        assertThat(child.define(inner)).isTrue();

        assertThat(child.resolve("x")).contains(inner);
        assertThat(parent.resolve("x")).contains(outer);
    }

    @Test
    void rejectsBlankSymbolNames() {
        SourceFile sourceFile = new SourceFile("symbol.mc", "x");
        SourceRange range = new SourceRange(sourceFile, 0, 1);

        assertThatThrownBy(() -> new Symbol(" ", SymbolKind.VARIABLE, range))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Symbol symbol(String name, SymbolKind kind) {
        SourceFile sourceFile = new SourceFile(name + ".mc", name);
        return new Symbol(name, kind, new SourceRange(sourceFile, 0, name.length()));
    }
}
