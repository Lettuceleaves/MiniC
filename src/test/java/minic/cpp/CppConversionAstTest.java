package minic.cpp;

import minic.SourceRange;
import minic.compiler.LanguageMode;
import minic.compiler.ir.IrLowerer;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class CppConversionAstTest {
    private static final SourceRange RANGE = new SourceRange(2, 0, 2, 36);
    private static final SourceRange NAME = new SourceRange(2, 8, 2, 21);

    @Test void conversionMetadataRetainsItsTargetExplicitnessAndWrittenRange() {
        var name = new ConversionName(MiniType.BOOL, true, NAME);
        var method = new FunctionDecl(name.spelling(), MiniType.BOOL, List.of(), false, null, false, false, RANGE, null, name);
        assertSame(name, method.conversionName());
        assertEquals("operator bool", method.name());
        assertTrue(name.explicitSpecifier());
        assertSame(NAME, name.range());
        assertThrows(IllegalArgumentException.class, () -> new FunctionDecl("ordinary", MiniType.BOOL, List.of(), false, null, false, false, RANGE, null, name));
        assertThrows(IllegalArgumentException.class, () -> new FunctionDecl(name.spelling(), MiniType.INT, List.of(), false, null, false, false, RANGE, null, name));
        assertThrows(IllegalArgumentException.class, () -> new FunctionDecl("operator+", MiniType.BOOL, List.of(), false, null, false, false, RANGE,
                new OperatorName(OperatorName.Kind.ADD, NAME), name));
        assertThrows(NullPointerException.class, () -> new ConversionName(null, false, NAME));
        assertThrows(NullPointerException.class, () -> new ConversionName(MiniType.BOOL, false, null));
    }

    @Test void allLegacyFunctionAndConstructorConstructorsKeepTheirOriginalDefaults() {
        assertNull(new FunctionDecl("ordinary", MiniType.INT, List.of(), false, null, false, RANGE).conversionName());
        assertNull(new FunctionDecl("ordinary", MiniType.INT, List.of(), false, null, false, false, RANGE).conversionName());
        var operator = new OperatorName(OperatorName.Kind.ADD, NAME);
        assertNull(new FunctionDecl(operator.spelling(), MiniType.INT, List.of(), false, null, false, false, RANGE, operator).conversionName());
        assertFalse(new ConstructorMember("Value", List.of(), false, List.of(), null, NAME, RANGE).explicitSpecifier());
        assertTrue(new ConstructorMember("Value", List.of(), false, List.of(), null, NAME, RANGE, true).explicitSpecifier());
    }

    @Test void conversionCannotBypassBindingThroughCoreOrFlatProgramIndexes() {
        var name = new ConversionName(MiniType.BOOL, false, NAME);
        var method = new FunctionDecl(name.spelling(), MiniType.BOOL, List.of(), false, null, false, false, RANGE, null, name);
        var hidden = new Program(List.of(), List.of(), List.of(), List.of(), List.of(method), List.of(), LanguageMode.C, RANGE);
        assertSame(method, AstChildren.firstCppSyntax(method));
        assertSame(method, AstChildren.firstCppSyntax(hidden));
        var semantic = new SemanticAnalyzer(hidden); semantic.analyze();
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(error -> error.code().equals("CPP002")));
        assertThrows(IllegalArgumentException.class, () -> new IrLowerer().lower(hidden));
        assertThrows(IllegalArgumentException.class, () -> new IrLowerer(hidden, Map.of(), Map.of()));
        var reference = new ConversionName(MiniType.INT.referenceTo(), false, NAME);
        var referenceMethod = new FunctionDecl(reference.spelling(), reference.targetType(), List.of(), false, null,
                false, false, RANGE, null, reference);
        assertSame(referenceMethod, AstChildren.firstReferenceSyntax(referenceMethod));
    }
}
