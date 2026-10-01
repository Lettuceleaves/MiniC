package minic.cpp;

import minic.SourceRange;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.semantic.manager.ExpressionTypeProbe;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class CppExpressionTypeProbeTest {
    static final SourceRange R=new SourceRange(1,0,1,4);
    @Test void constAssignmentFailsWithoutChangingTheSymbolEnvironment(){
        var type=MiniType.qualified(MiniType.INT,Set.of(MiniType.TypeQualifier.CONST));
        var symbols=Map.of("x",type);
        var expression=new AssignmentExpr(new NameExpr("x",R),TokenType.EQUAL,new IntegerLiteralExpr(1,"1",R),R);
        assertFalse(ExpressionTypeProbe.analyze(expression,symbols,List.of()).valid());assertEquals(type,symbols.get("x"));
    }
    @Test void unevaluatedFunctionCallNeedsNoDefinitionAndMayReturnVoid(){
        var call=new CallExpr(new NameExpr("declaredOnly",R),List.of(),R);
        var result=ExpressionTypeProbe.analyze(call,Map.of("declaredOnly",MiniType.function(MiniType.VOID,List.of(),false)),List.of());
        assertTrue(result.valid(),()->result.diagnostics().toString());assertEquals(MiniType.VOID,result.type());
    }
    @Test void invalidBuiltinOperandsAreValidatedRatherThanGuessed(){
        var expression=new BinaryExpr(new NameExpr("x",R),TokenType.PERCENT,new IntegerLiteralExpr(2,"2",R),R);
        assertFalse(ExpressionTypeProbe.analyze(expression,Map.of("x",MiniType.DOUBLE),List.of()).valid());
        assertTrue(ExpressionTypeProbe.analyze(expression,Map.of("x",MiniType.INT),List.of()).valid());
    }
}
