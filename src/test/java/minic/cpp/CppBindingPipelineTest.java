package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Declaration.Program;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Statement.ReturnStmt;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.ir.IrLowerer;
import minic.compiler.ir.IrResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-frontend")
class CppBindingPipelineTest {
    @Test void languageModeSurvivesStandaloneAstAndLegacyConstructorsDefaultToC() {
        Program source = parse("int main(){return 0;}");
        assertEquals(LanguageMode.CPP17_ALGORITHM, source.languageMode());
        Program legacy = new Program(source.structs(), source.enums(), source.typedefs(),
                source.globals(), source.functions(), source.declarations(), source.range());
        assertEquals(LanguageMode.C, legacy.languageMode());
    }

    @Test void originalExpressionsKeepTypeQueriesAndIndependentIrUsesBoundProgram() {
        Program source = parse("namespace A {int x=3;} int main(){return A::x;}");
        Expression original = ((ReturnStmt) source.functions().getFirst().body().statements().getFirst()).expression();
        var semantic = new SemanticAnalyzer(source);
        var result = semantic.analyze();
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        assertSame(source, result.sourceProgram());
        assertNotSame(source, result.program());
        assertTrue(result.typeOf(original).isPresent());
        assertEquals(original.range(), result.sourceToCore().get(original).range());
        var ir = new IrLowerer(source, result).lower();
        assertTrue(ir.displayNames().containsValue("A::x"));
        assertEquals(1, ir.globalData().size());
        assertNotNull(ir.findFunction("main").orElseThrow());
        var repeated = new IrLowerer().lower(source, result);
        assertEquals(ir.functions(), repeated.functions());
        assertEquals(ir.displayNames(), repeated.displayNames());
        assertArrayEquals(ir.globalData().getFirst().bytes(), repeated.globalData().getFirst().bytes());
        assertTrue(result.scopeSnapshot().symbols().stream().anyMatch(s -> s.name().equals("A::x")));
        var lowerer = new IrLowerer(source, result);
        boolean sourceReturnObserved = false;
        while (lowerer.canNext()) {
            lowerer.step();
            if (lowerer.currentResult().currentAstNode() == source.functions().getFirst().body().statements().getFirst()) {
                sourceReturnObserved = true;
            }
        }
        assertTrue(sourceReturnObserved, "IR step context must refer to the parser's source node");
    }

    @Test void cppDeclarationOrderAppliesEvenWithoutNamespaceSyntax() {
        var semantic = new SemanticAnalyzer(parse("int main(){return later();} int later(){return 1;}"));
        semantic.analyze();
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d -> d.code().equals("CPP003")));
    }

    @Test void rawCppAstCannotBypassNameBindingThroughLegacyIrEntry() {
        var source = parse("namespace A {int x=3;} int main(){return 0;}");
        assertThrows(IllegalArgumentException.class, () -> new IrLowerer().lower(source));
        assertThrows(IllegalArgumentException.class, () -> new IrLowerer(source, Map.of(), Map.of()));
    }

    @Test void irDisplayNamesAreImmutableAndPreserveLocalSlotSuffixes() {
        var names = new java.util.HashMap<>(Map.of("symbol7", "A::f", "symbol8", "x"));
        var ir = new IrResult(List.of(), List.of(), List.of(), Set.of(), Set.of(), Map.of(), null, "", names);
        names.clear();
        assertEquals("A::f", ir.displayName("symbol7"));
        assertEquals("x#3", ir.displayName("symbol8#3"));
        assertEquals("temp9", ir.displayName("temp9"));
        assertThrows(UnsupportedOperationException.class, () -> ir.displayNames().clear());
    }

    @Test void semanticDiagnosticsAndStepSubjectsUseSourceNames() {
        var semantic = new SemanticAnalyzer(parse("namespace A {int f(){int local=\"bad\";return 0;}} int main(){return A::f();}"));
        semantic.analyze();
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d -> d.message().contains("local")));
        assertTrue(semantic.errors().stream().noneMatch(d -> d.message().contains("minicCppSymbol")));
        var good = new SemanticAnalyzer(parse("namespace A {int f(){return 1;}} int main(){return A::f();}"));
        var result = good.analyze();
        var lowerer = new IrLowerer(result.sourceProgram(), result);
        boolean observed = false;
        while (lowerer.canNext()) {
            lowerer.step();
            String subject = lowerer.currentResult().currentSubject();
            assertFalse(subject.contains("minicCppSymbol"), subject);
            observed |= subject.contains("A::f");
        }
        assertTrue(observed);
    }

    private static Program parse(String text) {
        var lexer = new Lexer(new SourceFile("pipeline.cpp", text), LanguageMode.CPP17_ALGORITHM);
        var parser = new Parser(lexer.lex().tokens(), LanguageMode.CPP17_ALGORITHM, true);
        parser.parse();
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        return parser.result().program();
    }
}
