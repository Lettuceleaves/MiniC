package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.semantic.cpp.CppOverloadResolver;
import minic.compiler.semantic.cpp.CppValueCategory;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Model/parser contracts, saved without executing any tests during feature assembly. */
final class CppNoexceptModelTest {
    private static final MiniType.FunctionType THROWING=new MiniType.FunctionType(MiniType.INT,List.of(),false);
    private static final MiniType.FunctionType SAFE=THROWING.withExceptionSpecification(MiniType.ExceptionSpecification.NON_THROWING);
    @Test void functionTypesKeepExceptionSpecificationWithoutChangingParameters() {
        assertNotEquals(THROWING,SAFE);
        assertEquals(THROWING.parameterTypes(),SAFE.parameterTypes());
        assertEquals(THROWING.returnType(),SAFE.returnType());
        assertTrue(SAFE.exceptionSpecification().nonThrowing());
        assertFalse(THROWING.exceptionSpecification().specified());
        assertTrue(SAFE.toString().contains("noexcept"));
        assertThrows(IllegalArgumentException.class,()->new MiniType.ExceptionSpecification(false,true,null));
    }
    @Test void onlySafeToThrowingFunctionPointerConversionIsStandard() {
        var safe=new CppOverloadResolver.Argument(SAFE.pointerTo(),CppValueCategory.PRVALUE,false);
        var throwing=new CppOverloadResolver.Argument(THROWING.pointerTo(),CppValueCategory.PRVALUE,false);
        assertTrue(CppOverloadResolver.standardViable(safe,THROWING.pointerTo()));
        assertFalse(CppOverloadResolver.standardViable(throwing,SAFE.pointerTo()));
        assertTrue(CppOverloadResolver.standardViable(safe,SAFE.pointerTo()));
        assertFalse(CppOverloadResolver.standardViable(new CppOverloadResolver.Argument(SAFE.pointerTo().pointerTo(),CppValueCategory.PRVALUE,false),THROWING.pointerTo().pointerTo()));
    }
    @Test void exactSafePointerOverloadWinsOverTheFunctionPointerConversion() {
        var candidates=List.of(new CppOverloadResolver.Candidate<>("throwing",List.of(THROWING.pointerTo()),false),new CppOverloadResolver.Candidate<>("safe",List.of(SAFE.pointerTo()),false));
        var selected=CppOverloadResolver.resolve(candidates,List.of(new CppOverloadResolver.Argument(SAFE.pointerTo(),CppValueCategory.PRVALUE,false)),null);
        assertEquals(CppOverloadResolver.Status.SELECTED,selected.status());
        assertEquals("safe",selected.winner().identity());
    }
    @Test void functionReferenceBindingMayDropButNeverAddNoexcept() {
        assertTrue(CppOverloadResolver.standardViable(new CppOverloadResolver.Argument(SAFE,CppValueCategory.LVALUE,false),THROWING.referenceTo()));
        assertFalse(CppOverloadResolver.standardViable(new CppOverloadResolver.Argument(THROWING,CppValueCategory.LVALUE,false),SAFE.referenceTo()));
    }
    @Test void parserPreservesConditionalOperandAndNoexceptExpressionRanges() {
        var source=new SourceFile("noexcept.cpp","int f(int value) noexcept(sizeof(value)==sizeof(int)); int main(){return noexcept(f(1));}");
        var program=parse(source,LanguageMode.CPP17_ALGORITHM);
        var f=program.functions().getFirst();
        assertTrue(f.exceptionSpecification().specified());
        assertNotNull(f.exceptionSpecification().condition());
        assertEquals("sizeof(value)==sizeof(int)",source.text(f.exceptionSpecification().condition().range()));
        var query=(CppNoexceptExpr)nodes(program).stream().filter(CppNoexceptExpr.class::isInstance).findFirst().orElseThrow();
        assertEquals("noexcept(f(1))",source.text(query.range()));
        assertEquals("f(1)",source.text(query.operand().range()));
        assertSame(query.operand(),AstChildren.of(query).getFirst());
        assertSame(query,AstChildren.firstCppSyntax(query));
    }
    @Test void parserRetainsClassSpecialMemberAndLambdaSpecifications() {
        var program=parse(new SourceFile("record.cpp","struct A{A()noexcept{}~A()noexcept(false){}int f()const noexcept{return 1;}};int main(){auto f=[]()noexcept{return 2;};return f();}"),LanguageMode.CPP17_ALGORITHM);
        var all=nodes(program);
        var constructor=(ConstructorMember)all.stream().filter(ConstructorMember.class::isInstance).findFirst().orElseThrow();
        var destructor=(DestructorMember)all.stream().filter(DestructorMember.class::isInstance).findFirst().orElseThrow();
        var lambda=(CppLambdaExpr)all.stream().filter(CppLambdaExpr.class::isInstance).findFirst().orElseThrow();
        assertTrue(constructor.exceptionSpecification().nonThrowing());
        assertNotNull(destructor.exceptionSpecification().condition());
        assertTrue(lambda.exceptionSpecification().nonThrowing());
    }
    @Test void cKeepsNoexceptAsAnOrdinaryIdentifier() {
        var program=parse(new SourceFile("ordinary.c","int noexcept(int value){return value;}int main(){return noexcept(1);}"),LanguageMode.C);
        assertTrue(nodes(program).stream().noneMatch(CppNoexceptExpr.class::isInstance));
        assertFalse(program.functions().getFirst().exceptionSpecification().specified());
    }
    @Test void malformedNoexceptOperandsDoNotCrashTheParser() {
        for(String text:List.of("int f()noexcept();","int main(){return noexcept();}","int f()noexcept(1;")) {
            var parser=new Parser(new Lexer(new SourceFile("bad.cpp",text),LanguageMode.CPP17_ALGORITHM));
            assertDoesNotThrow(parser::parse);assertFalse(parser.succeeded(),text);
        }
    }
    private static Program parse(SourceFile source,LanguageMode mode) {
        var parser=new Parser(new Lexer(source,mode));parser.parse();assertTrue(parser.succeeded(),()->parser.errors().toString());return parser.result().program();
    }
    private static List<AstNode> nodes(AstNode node) {var result=new ArrayList<AstNode>();collect(node,result,Collections.newSetFromMap(new IdentityHashMap<>()));return result;}
    private static void collect(AstNode node,List<AstNode> nodes,Set<AstNode> seen){if(node==null||!seen.add(node))return;nodes.add(node);for(var child:AstChildren.of(node))collect(child,nodes,seen);}
}
