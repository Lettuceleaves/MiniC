package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.semantic.cpp.CppNameBinder;
import minic.compiler.semantic.cpp.CppTemplateSubstitution;
import minic.compiler.type.MiniType;
import minic.compiler.type.TemplateValues;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Saved for the final unified acceptance run; these tests have not been executed while features are assembled. */
final class CppTypeQueryTest {
    @Test void preservesOrderedSourceTypeArgumentsAndRanges() {
        var source = new SourceFile("query.cpp", "int main(){return __is_constructible(const int (&)[3], int (&&)[3]);}");
        Program program = parse(source, LanguageMode.CPP17_ALGORITHM);
        var query = queries(program).getFirst();
        assertEquals(CppTypeQueryExpr.Kind.CONSTRUCTIBLE, query.kind());
        assertEquals("__is_constructible", source.text(query.nameRange()));
        assertEquals("__is_constructible(const int (&)[3], int (&&)[3])", source.text(query.range()));
        assertEquals(List.of("const int (&)[3]", "int (&&)[3]"), query.arguments().stream().map(a -> source.text(a.range())).toList());
        assertTrue(query.arguments().getFirst().type().isReference());
        assertTrue(query.arguments().get(1).type().isRvalueReference());
        assertSame(query, AstChildren.firstCppSyntax(program));
        assertSame(query.arguments().getFirst(), AstChildren.of(query).getFirst());
        assertSame(query.arguments().getFirst(), AstChildren.firstReferenceSyntax(query));
        assertThrows(UnsupportedOperationException.class, () -> query.arguments().clear());
    }

    @ParameterizedTest @ValueSource(strings = {
            "__is_constructible()", "__is_assignable(int)", "__is_convertible(int,int,int)",
            "__is_constructible(int,)", "__is_assignable(1,int)" })
    void malformedTypeQueriesDiagnoseWithoutThrowing(String expression) {
        var source = new SourceFile("bad-query.cpp", "int main(){return " + expression + ";}");
        var parser = new Parser(new Lexer(source, LanguageMode.CPP17_ALGORITHM));
        assertDoesNotThrow(parser::parse);
        assertFalse(parser.succeeded());
    }

    @Test void typeQueryArrayBoundWaitsForSemanticContext() {
        Program program = parse(new SourceFile("bound-query.cpp", "int values[__is_constructible(int) ? 3 : 1]; int main(){return 0;}"), LanguageMode.CPP17_ALGORITHM);
        var result = CppNameBinder.bind(program);
        assertTrue(result.diagnostics().isEmpty(), result.diagnostics()::toString);
        assertEquals(3, result.program().globals().getFirst().type().arrayLength());
    }

    @Test void anUnexpandedPackKeepsItsShapeAndIsNeverSilentlyTreatedAsOneType() {
        Program program = parse(new SourceFile("pack-query.cpp", "int main(){return __is_constructible(int,int...);}"), LanguageMode.CPP17_ALGORITHM);
        assertTrue(queries(program).getFirst().arguments().get(1).packExpansion());
        assertTrue(CppNameBinder.bind(program).diagnostics().stream().anyMatch(d -> d.code().equals("CPP005")));
    }

    @Test void cKeepsOrdinaryIdentifierNamespace() {
        Program program = parse(new SourceFile("query.c", "int __is_assignable(int x,int y){return x+y;} int main(){return __is_assignable(1,2);}"), LanguageMode.C);
        assertTrue(queries(program).isEmpty());
        assertNull(AstChildren.firstCppSyntax(program));
    }

    static Stream<Arguments> scalarQueries() {
        return Stream.of(
                Arguments.of("__is_constructible(int)", true),
                Arguments.of("__is_constructible(const int)", true),
                Arguments.of("__is_constructible(int,double)", true),
                Arguments.of("__is_constructible(int,double,double)", false),
                Arguments.of("__is_constructible(void)", false),
                Arguments.of("__is_constructible(int,void)", false),
                Arguments.of("__is_constructible(int&)", false),
                Arguments.of("__is_constructible(int&,int&)", true),
                Arguments.of("__is_constructible(int&,double&)", false),
                Arguments.of("__is_constructible(int&,const int&)", false),
                Arguments.of("__is_constructible(const int&,double)", true),
                Arguments.of("__is_constructible(const int&,volatile int&)", false),
                Arguments.of("__is_constructible(const double&,volatile int&)", true),
                Arguments.of("__is_constructible(int&&,int)", true),
                Arguments.of("__is_constructible(int&&,int&)", false),
                Arguments.of("__is_constructible(const int* const&,int*&)", true),
                Arguments.of("__is_constructible(const int**,int**)", false),
                Arguments.of("__is_constructible(const int* const*,int**)", true),
                Arguments.of("__is_constructible(int*,int)", false),
                Arguments.of("__is_constructible(bool,decltype(nullptr))", true),
                Arguments.of("__is_constructible(int[2])", true),
                Arguments.of("__is_constructible(int[2],int)", false),
                Arguments.of("__is_constructible(int())", false),
                Arguments.of("__is_assignable(int,int)", false),
                Arguments.of("__is_assignable(int&&,int)", false),
                Arguments.of("__is_assignable(int&,double)", true),
                Arguments.of("__is_assignable(const int&,int)", false),
                Arguments.of("__is_assignable(volatile int&,int)", true),
                Arguments.of("__is_assignable(int*&,void*)", false),
                Arguments.of("__is_assignable(void*&,int*)", true),
                Arguments.of("__is_assignable(int(&)[3],int(&)[3])", false),
                Arguments.of("__is_assignable(void,int)", false),
                Arguments.of("__is_convertible(void,void)", true),
                Arguments.of("__is_convertible(int,void)", false),
                Arguments.of("__is_convertible(void,int)", false),
                Arguments.of("__is_convertible(int,double)", true),
                Arguments.of("__is_convertible(void*,int*)", false),
                Arguments.of("__is_convertible(int*,void*)", true),
                Arguments.of("__is_convertible(decltype(nullptr),bool)", false),
                Arguments.of("__is_convertible(decltype(nullptr),int*)", true),
                Arguments.of("__is_convertible(int[3],const int*)", true),
                Arguments.of("__is_convertible(int, int[3])", false),
                Arguments.of("__is_convertible(int(),int(*)())", true),
                Arguments.of("__is_constructible(int(&)(),int())", true),
                Arguments.of("__is_convertible(int(),int(&)())", true));
    }

    @ParameterizedTest @MethodSource("scalarQueries")
    void queriesRealScalarInitializationAndExpressionCategories(String expression, boolean expected) {
        assertQuery("", expression, expected);
    }

    static Stream<Arguments> classQueries() {
        return Stream.of(
                Arguments.of("struct A{};", "__is_constructible(A)", true),
                Arguments.of("struct A{int x;};", "__is_constructible(A,int)", false),
                Arguments.of("struct A{const int x;};", "__is_constructible(A)", false),
                Arguments.of("struct A{int& x;};", "__is_constructible(A)", false),
                Arguments.of("union A{const int x; int y;};", "__is_constructible(A)", true),
                Arguments.of("union A{const int x; const int y;};", "__is_constructible(A)", false),
                Arguments.of("struct Member{int x;}; struct A{const Member member;};", "__is_constructible(A)", false),
                Arguments.of("struct Member{Member();}; struct A{const Member member;};", "__is_constructible(A)", true),
                Arguments.of("class A{A();};", "__is_constructible(A)", false),
                Arguments.of("struct A{explicit A(int);};", "__is_constructible(A,int)", true),
                Arguments.of("struct A{explicit A(int);};", "__is_convertible(int,A)", false),
                Arguments.of("struct A{A(int);};", "__is_convertible(int,A)", true),
                Arguments.of("struct A{A(int,int=0);};", "__is_constructible(A,int)", true),
                Arguments.of("struct A{A(int*);};", "__is_constructible(A,void*)", false),
                Arguments.of("struct A{A(long);A(double);};", "__is_constructible(A,int)", false),
                Arguments.of("class A{A(int);public:A(double);};", "__is_constructible(A,int)", false),
                Arguments.of("class A{~A();};", "__is_constructible(A)", false),
                Arguments.of("class A{~A();};", "__is_constructible(A&,A&)", true),
                Arguments.of("struct A{operator int();};", "__is_convertible(A,int)", true),
                Arguments.of("struct A{explicit operator int();};", "__is_convertible(A,int)", false),
                Arguments.of("struct A{explicit operator int();};", "__is_constructible(int,A)", true),
                Arguments.of("class A{operator int();};", "__is_convertible(A,int)", false),
                Arguments.of("struct A{operator int&();};", "__is_constructible(int&,A)", true),
                Arguments.of("struct A{operator int();};", "__is_constructible(int&,A)", false),
                Arguments.of("struct A{int x;};", "__is_assignable(A&,const A&)", true),
                Arguments.of("struct A{const int x;};", "__is_assignable(A&,const A&)", false),
                Arguments.of("struct A{int& x;};", "__is_assignable(A&,const A&)", false),
                Arguments.of("struct A{void operator=(int);};", "__is_assignable(A,int)", true),
                Arguments.of("class A{void operator=(int);};", "__is_assignable(A&,int)", false),
                Arguments.of("struct A{void operator=(int) const;};", "__is_assignable(const A&,int)", true),
                Arguments.of("struct A{void operator=(long);void operator=(double);};", "__is_assignable(A&,int)", false),
                Arguments.of("struct A{operator int();};", "__is_assignable(int&,A)", true),
                Arguments.of("struct A{explicit operator int();};", "__is_assignable(int&,A)", false),
                Arguments.of("struct A{A(const A&);};", "__is_constructible(A,A&)", true),
                Arguments.of("class A{A(const A&);};", "__is_convertible(A&,A)", false),
                Arguments.of("class A{A(const A&);}; struct B{operator A&();};", "__is_convertible(B,A)", false),
                Arguments.of("class A{A(const A&);}; struct B{operator A();};", "__is_convertible(B,A)", true));
    }

    @ParameterizedTest @MethodSource("classQueries")
    void queriesSelectBeforeCheckingAccessAndDoNotOdrUseDeclarations(String prefix, String expression, boolean expected) {
        assertQuery(prefix, expression, expected);
    }

    @Test void queriesCannotBorrowCurrentClassAccessAndDoNotLoseItAfterward() {
        var program = parse(new SourceFile("access-query.cpp", """
                class A{ A(); int secret; public: int test(){
                    bool answer=__is_constructible(A); secret=3; return answer;
                }}; int main(){return 0;}
                """), LanguageMode.CPP17_ALGORITHM);
        var result = CppNameBinder.bind(program);
        assertTrue(result.diagnostics().isEmpty(), result.diagnostics()::toString);
        assertFalse(assertInstanceOf(BoolLiteralExpr.class, result.sourceToCore().get(queries(program).getFirst())).value());
    }

    @Test void incompleteObjectArgumentsAreDiagnosedButIncompleteReferenceBindingsRemainValid() {
        assertQuery("struct A;", "__is_constructible(A&,A&)", true);
        Program program = parse(new SourceFile("incomplete-query.cpp", "struct A; int main(){return __is_constructible(A);}"), LanguageMode.CPP17_ALGORITHM);
        assertTrue(CppNameBinder.bind(program).diagnostics().stream().anyMatch(d -> d.code().equals("CPP004")));
    }

    @Test void dependentTypeArgumentsSubstituteStructurallyAndWaitForSemanticEvaluation() {
        var program = parse(new SourceFile("template-query.cpp", "template<class T> struct Query{static const bool value=__is_constructible(T,int&);}; int main(){return 0;}"), LanguageMode.CPP17_ALGORITHM);
        ClassTemplateDecl template = assertInstanceOf(ClassTemplateDecl.class, program.declarations().getFirst());
        var parameter = template.parameters().getFirst().type();
        CppTypeQueryExpr original = queries(template.record()).getFirst();
        assertTrue(TemplateValues.dependent(original));
        assertTrue(TemplateValues.requiresSemanticContext(original));
        var substitution = new CppTemplateSubstitution(Map.of(parameter, MiniType.INT.referenceTo()), "Query", "QueryInt");
        CppTypeQueryExpr instance = queries(substitution.instantiate(template.record())).getFirst();
        assertEquals(MiniType.INT.referenceTo(), instance.arguments().getFirst().type());
        assertFalse(TemplateValues.dependent(instance));
        assertSame(original.range(), instance.range());
        assertSame(original, substitution.origins().get(instance));
    }

    @Test void falseQueryDoesNotInstantiateInvalidUnusedConstructorTemplateBody() {
        assertQuery("struct A { template<class T> A(T value){ value.no_such_member(); } };", "__is_constructible(A,int)", true);
    }

    private static void assertQuery(String prefix, String expression, boolean expected) {
        var source = new SourceFile("type-query.cpp", prefix + " int main(){return " + expression + ";}");
        Program program = parse(source, LanguageMode.CPP17_ALGORITHM);
        var query = queries(program).getFirst();
        var result = CppNameBinder.bind(program);
        assertTrue(result.diagnostics().isEmpty(), result.diagnostics()::toString);
        var core = assertInstanceOf(BoolLiteralExpr.class, result.sourceToCore().get(query));
        assertEquals(expected, core.value(), expression);
        assertEquals(query.range(), core.range());
        assertNull(AstChildren.firstCppSyntax(result.program()));
    }

    private static Program parse(SourceFile source, LanguageMode mode) {
        var parser = new Parser(new Lexer(source, mode));
        Program program = parser.parse().program();
        assertTrue(parser.succeeded(), parser.errors()::toString);
        return program;
    }

    private static List<CppTypeQueryExpr> queries(AstNode node) {
        List<CppTypeQueryExpr> result = new ArrayList<>();
        if (node instanceof CppTypeQueryExpr query) result.add(query);
        for (AstNode child : AstChildren.of(node)) result.addAll(queries(child));
        return result;
    }
}
