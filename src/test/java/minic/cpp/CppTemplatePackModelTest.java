package minic.cpp;

import minic.SourceRange;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.semantic.cpp.*;
import minic.compiler.type.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.UnaryOperator;
import static org.junit.jupiter.api.Assertions.*;

/** Structural contracts queued for final execution; all expectations are independent of runtime. */
final class CppTemplatePackModelTest {
    static final SourceRange R=new SourceRange(3,2,3,8);
    static final MiniType.TemplateParameterType A=new MiniType.TemplateParameterType("::Owner",0);
    static final MiniType.TemplateParameterType B=new MiniType.TemplateParameterType("::Owner",1);
    static final List<ClassTemplateDecl.Parameter> PARAMETERS=List.of(new ClassTemplateDecl.TypeParameter("A",A,null,true,R));
    static CppTemplateSubstitution substitution(List<TemplateArgument> values) {
        return new CppTemplateSubstitution(Map.of(),Map.of(),Map.of(A,values),PARAMETERS,"::Owner","::Concrete");
    }
    @Test void emptyPackAndHeterogeneousForwardingDeducedWithCategories() {
        var signature=List.<MiniType>of(new MiniType.PackExpansionType(A.rvalueReferenceTo()));
        var empty=CppFunctionTemplateDeduction.deduceShapes(PARAMETERS,signature,List.of(),List.of(),UnaryOperator.identity(),UnaryOperator.identity(),TemplateValues::evaluate,t->null);
        assertNotNull(empty);assertEquals(List.of(),empty.packs().get(A));
        var binding=CppFunctionTemplateDeduction.deduceShapes(PARAMETERS,signature,List.of(
                new CppOverloadResolver.Argument(MiniType.INT,CppValueCategory.LVALUE,false),
                new CppOverloadResolver.Argument(MiniType.DOUBLE,CppValueCategory.PRVALUE,false)),List.of(),UnaryOperator.identity(),UnaryOperator.identity(),TemplateValues::evaluate,t->null);
        assertNotNull(binding);assertEquals(List.of(new TemplateArgument.Type(MiniType.INT.referenceTo()),new TemplateArgument.Type(MiniType.DOUBLE)),binding.packs().get(A));
    }
    @Test void classTrailingPatternDeducesAllAndZeroElements() {
        var pattern=List.<TemplateArgument>of(new TemplateArgument.Expansion(new TemplateArgument.Type(A.pointerTo())));
        var actual=List.<TemplateArgument>of(new TemplateArgument.Type(MiniType.INT.pointerTo()),new TemplateArgument.Type(MiniType.DOUBLE.pointerTo()));
        var binding=CppTemplateDeduction.match(pattern,actual,PARAMETERS,UnaryOperator.identity());
        assertNotNull(binding);assertEquals(List.of(new TemplateArgument.Type(MiniType.INT),new TemplateArgument.Type(MiniType.DOUBLE)),binding.packs().get(A));
        assertEquals(List.of(),CppTemplateDeduction.match(pattern,List.of(),PARAMETERS,UnaryOperator.identity()).packs().get(A));
    }
    @Test void expandingCallPreservesGroupsInExplicitEvaluationPermutation() {
        var parameter=new Parameter("values",new MiniType.PackExpansionType(A),R);
        var call=new CallExpr(new NameExpr("target",R),List.of(new CppPackExpansionExpr(new NameExpr("values",R),R),new IntegerLiteralExpr(7,"7",R)),List.of(1,0),R);
        var body=new Statement.BlockStmt(List.of(new Statement.ReturnStmt(call,R)),R);
        var function=new FunctionDecl("use",MiniType.INT,List.of(parameter),false,body,false,R);
        var substitution=substitution(List.of(new TemplateArgument.Type(MiniType.INT),new TemplateArgument.Type(MiniType.DOUBLE)));
        var copy=substitution.instantiate(function);
        assertEquals(List.of("values$pack0","values$pack1"),copy.parameters().stream().map(Parameter::name).toList());
        var expanded=(CallExpr)((Statement.ReturnStmt)copy.body().statements().getFirst()).expression();
        assertEquals(List.of(2,0,1),expanded.argumentEvaluationOrder());
        assertEquals(3,expanded.arguments().size());assertEquals(R,expanded.range());
        assertSame(function,substitution.origins().get(copy));
    }
    @Test void queryTypeListExpandsAndClearsSourcePackFlags() {
        var query=new CppTypeQueryExpr(CppTypeQueryExpr.Kind.CONSTRUCTIBLE,List.of(new CppTypeQueryExpr.TypeArgument(MiniType.struct("Box"),R),new CppTypeQueryExpr.TypeArgument(A,true,R)),R,R);
        var copy=(CppTypeQueryExpr)substitution(List.of(new TemplateArgument.Type(MiniType.INT),new TemplateArgument.Type(MiniType.DOUBLE))).expression(query);
        assertEquals(List.of(MiniType.struct("Box"),MiniType.INT,MiniType.DOUBLE),copy.arguments().stream().map(CppTypeQueryExpr.TypeArgument::type).toList());
        assertTrue(copy.arguments().stream().noneMatch(CppTypeQueryExpr.TypeArgument::packExpansion));assertEquals(R,copy.arguments().getLast().range());
    }
    @Test void emptyTemplateIdIsAnIdentityAndSizeofPackIsUnsignedConstant() {
        var type=new MiniType.TemplateIdType("::Tuple",List.of(new TemplateArgument.Expansion(new TemplateArgument.Type(A))));
        assertEquals(new MiniType.TemplateIdType("::Tuple",List.<TemplateArgument>of()),substitution(List.of()).type(type));
        var size=(IntegerConstantExpr)substitution(List.of()).expression(new CppSizeofPackExpr("A",R));
        assertEquals(0,size.value());assertEquals(MiniType.UNSIGNED_LONG_LONG,size.type());assertEquals(R,size.range());
    }
    @Test void simultaneousPacksRejectDifferentLengths() {
        var parameters=List.<ClassTemplateDecl.Parameter>of(new ClassTemplateDecl.TypeParameter("A",A,null,true,R),new ClassTemplateDecl.TypeParameter("B",B,null,true,R));
        var substitution=new CppTemplateSubstitution(Map.of(),Map.of(),Map.of(A,List.of(new TemplateArgument.Type(MiniType.INT)),B,List.of()),parameters,"::Owner","::Concrete");
        var pattern=new MiniType.TemplateIdType("::Pair",List.of(new TemplateArgument.Type(A),new TemplateArgument.Type(B)));
        var type=new MiniType.TemplateIdType("::Tuple",List.of(new TemplateArgument.Expansion(new TemplateArgument.Type(pattern))));
        assertThrows(IllegalArgumentException.class,()->substitution.type(type));
    }
    @Test void parameterPackContextDoesNotLeakBetweenFunctions() {
        var pack=new Parameter("values",new MiniType.PackExpansionType(A),R);
        var one=new FunctionDecl("one",MiniType.INT,List.of(pack),false,new Statement.BlockStmt(List.of(new Statement.ReturnStmt(new CppSizeofPackExpr("values",R),R)),R),false,R);
        var substitution=substitution(List.of(new TemplateArgument.Type(MiniType.INT)));
        substitution.instantiate(one);
        assertInstanceOf(CppSizeofPackExpr.class,substitution.expression(new CppSizeofPackExpr("values",R)));
    }
}
