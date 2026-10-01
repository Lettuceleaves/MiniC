package minic.cpp;

import minic.SourceRange;
import minic.compiler.LanguageMode;
import minic.compiler.ir.IrLowerer;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class CppOperatorAstTest {
    private static final SourceRange RANGE = new SourceRange(2, 0, 2, 32);
    private static final SourceRange NAME = new SourceRange(2, 4, 2, 14);

    @ParameterizedTest @EnumSource(OperatorName.Kind.class)
    void operatorIdentityHasOneCanonicalSpellingAndKeepsSourceRange(OperatorName.Kind kind) {
        var name = new OperatorName(kind, NAME);
        var function = new FunctionDecl(name.spelling(), MiniType.INT, List.of(), false, null, false, false, RANGE, name);
        assertSame(kind, name.kind());
        assertSame(NAME, name.range());
        assertEquals("operator" + (kind.allocation() ? " " : "") + kind.symbol(), function.name());
        assertSame(name, function.operatorName());
        assertThrows(IllegalArgumentException.class, () -> new FunctionDecl("ordinary", MiniType.INT, List.of(), false, null, false, false, RANGE, name));
    }

    @Test void legacyConstructorsKeepOrdinaryFunctionsAndCOperatorIdentifierUnchanged() {
        assertNull(new FunctionDecl("ordinary", MiniType.INT, List.of(), false, null, false, false, RANGE).operatorName());
        assertNull(new FunctionDecl("operator", MiniType.INT, List.of(), false, null, false, RANGE).operatorName());
        assertThrows(NullPointerException.class, () -> new OperatorName(null, NAME));
        assertThrows(NullPointerException.class, () -> new OperatorName(OperatorName.Kind.ADD, null));
    }

    @Test void sourceOperatorCannotBypassBindingThroughCoreOrFlatIndexes() {
        var name = new OperatorName(OperatorName.Kind.ADD, NAME);
        var function = new FunctionDecl(name.spelling(), MiniType.INT, List.of(), false, null, false, false, RANGE, name);
        var hidden = new Program(List.of(), List.of(), List.of(), List.of(), List.of(function), List.of(), LanguageMode.C, RANGE);
        assertSame(function, AstChildren.firstCppSyntax(function));
        assertSame(function, AstChildren.firstCppSyntax(hidden));
        var semantic = new SemanticAnalyzer(hidden); semantic.analyze();
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(error -> error.code().equals("CPP002")));
        assertThrows(IllegalArgumentException.class, () -> new IrLowerer().lower(hidden));
        assertThrows(IllegalArgumentException.class, () -> new IrLowerer(hidden, Map.of(), Map.of()));
    }
}
