package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.semantic.SemanticAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Source lookup, redeclaration identity, and contextual address selection around the rank utility. */
@Timeout(90)
class CppOverloadBindingTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("using-declaration-is-a-snapshot", """
                        namespace A { int pick(int n){return 1;} }
                        using A::pick;
                        namespace A { int pick(double n){return 2;} }
                        int before(){return pick(1.5);}
                        namespace B { using namespace A; int after(){return pick(1.5);} }
                        int main(){printf("%d %d\\n",before(),B::after());return 0;}
                        """, "1 2\n"),
                Arguments.of("directives-diamond-deduplicates-entities", """
                        namespace A {int pick(int n){return 1;}}
                        namespace B {using namespace A;int pick(double n){return 2;}}
                        namespace C {using namespace A;}
                        using namespace B; using namespace C;
                        int main(){printf("%d %d\\n",pick(3),pick(3.5));return 0;}
                        """, "1 2\n"),
                Arguments.of("reference-pointer-distinct-source-abi", """
                        int pick(int &n){n+=2;return 1;} int pick(int *n){*n+=3;return 2;}
                        int &value(int &n){return n;} double &value(double &n){return n;}
                        int main(){int n=1;double d=2.5;int a=pick(n);int b=pick(&n);value(n)=9;value(d)=4.5;
                            printf("%d %d %d %.1f\\n",a,b,n,d);return 0;}
                        """, "1 2 9 4.5\n"),
                Arguments.of("bool-char-and-short-source-types", """
                        int pick(bool n){return 1;} int pick(char n){return 2;}
                        int pick(short n){return 3;} int pick(int n){return 4;}
                        int main(){printf("%d %d %d %d\\n",pick(1<2),pick('x'),pick(1?(short)2:(short)3),pick(4));return 0;}
                        """, "1 2 3 4\n"),
                Arguments.of("argument-evaluation-once-and-glvalue-reference", """
                        int calls; int next(){calls+=1;return calls;}
                        int pick(int n){return n;} int pick(double n){return 20;}
                        int touch(int &n){n+=3;return n;} int touch(const int &n){return 90;}
                        int main(){int n=1;int a=pick(next());int b=touch(n=4);
                            printf("%d %d %d %d\\n",a,b,calls,n);return 0;}
                        """, "1 7 1 7\n"),
                Arguments.of("selected-undefined-only", """
                        int pick(int n); int pick(double n){return 2;}
                        int main(){printf("%d\\n",pick(2.5));return 0;}
                        """, "2\n"),
                Arguments.of("late-overload-not-visible-in-earlier-body", """
                        int pick(int n){return 1;} int before(){return pick(2.5);}
                        int pick(double n){return 2;}
                        int main(){printf("%d %d\\n",before(),pick(2.5));return 0;}
                        """, "1 2\n"),
                Arguments.of("target-context-assignment-address-and-return", """
                        int pick(int n){return n+1;} int pick(double n){return 8;}
                        int (*factory())(int){return pick;}
                        int main(){int (*pointer)(int)=&pick;pointer=(pick);
                            printf("%d %d\\n",pointer(3),factory()(5));return 0;}
                        """, "4 6\n"),
                Arguments.of("member-using-class-and-parameter-shadow", """
                        int pick(int n){return 50;} int pick(double n){return 60;}
                        struct Box {int pick(int n){return 1;}int pick(double n) const{return 2;}
                            int run(){return pick(3);}int shadow(int (*pick)(int)){return pick(4);}};
                        int standalone(int n){return n+3;}
                        int main(){Box b={};const Box c={};printf("%d %d %d\\n",b.run(),c.pick(3),b.shadow(standalone));return 0;}
                        """, "1 2 7\n")
        );
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void executesLikeCpp17(String name, String source, String expected) throws Exception {
        CppReferenceTest.agree(temporary, name, "#include <stdio.h>\n" + source, expected);
    }

    static Stream<Arguments> rejected() {
        return Stream.of(
                Arguments.of("private-best-candidate", "struct Box{private:int pick(int n){return 1;}public:int pick(double n){return 2;}};\nint main(){Box b={};return b.pick(1);}// bad\n"),
                Arguments.of("return-top-cv-redeclaration", "const int pick(int n);\nint pick(int n){return n;}// bad\nint main(){return 0;}\n"),
                Arguments.of("return-pointee-cv-redeclaration", "int *pick(int n);\nconst int *pick(int n){return 0;}// bad\nint main(){return 0;}\n"),
                Arguments.of("duplicate-definition-through-top-cv", "int pick(int n){return n;}\nint pick(const int n){return n;}// bad\nint main(){return 0;}\n"),
                Arguments.of("namespace-candidates-same-signature", "namespace A{int pick(int n){return 1;}} namespace B{int pick(int n){return 2;}}using namespace A;using namespace B;\nint main(){return pick(1);}// bad\n"),
                Arguments.of("local-using-hides-outer-overloads", "int pick(int n){return 1;}namespace N{int pick(int *n){return 2;}}\nint main(){using N::pick;return pick(1);}// bad\n"),
                Arguments.of("overload-address-no-target", "int pick(int n){return 1;}int pick(double n){return 2;}\nint main(){&pick;return 0;}// bad\n"),
                Arguments.of("no-matching-contextual-address", "int pick(int n){return 1;}int pick(double n){return 2;}\nint main(){int (*pointer)(char)=pick;return 0;}// bad\n"),
                Arguments.of("data-name-conflicts-with-functions", "int pick(int n){return 1;}int pick(double n){return 2;}\nint pick=3;// bad\nint main(){return 0;}\n"),
                Arguments.of("const-member-qualifier-is-signature", "struct Box{int pick(int n)const;int pick(double n);};\nint Box::pick(int n){return 0;}// bad\nint main(){return 0;}\n")
        );
    }

    @ParameterizedTest(name="{0}") @MethodSource("rejected")
    void rejectsWithoutDiscardingConflictingDeclarations(String name, String source) throws Exception {
        CppReferenceTest.reject(temporary, name, source);
    }

    @Test void prototypeAndDefinitionShareCoreIdentityWhileOverloadsKeepDistinctSourceOrigins() {
        String source = "int pick(const int n); int pick(double n){return 2;} int pick(int n){return n;} int main(){return pick(1);}";
        var api = new CompilerApi(new SourceFile("overload-origin.cpp", source), LanguageMode.CPP17_ALGORITHM);
        var semantic = api.stages().stream().filter(SemanticAnalyzer.class::isInstance).map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        api.runThrough(semantic);
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        var parser = api.stages().stream().filter(Parser.class::isInstance).map(Parser.class::cast).findFirst().orElseThrow();
        var originals = parser.result().program().functions().stream().filter(f -> f.name().equals("pick")).toList();
        var cores = originals.stream().map(f -> assertInstanceOf(FunctionDecl.class, semantic.semanticResult().sourceToCore().get(f))).toList();
        assertEquals(cores.get(0).name(), cores.get(2).name());
        assertNotEquals(cores.get(0).name(), cores.get(1).name());
        for (int i=0; i<originals.size(); i++) assertEquals(originals.get(i).range(), cores.get(i).range());
        assertEquals(3, cores.stream().distinct().count());
    }

    @Test void contextualFunctionDesignatorWrappersRetainSeparateSourceOrigins() {
        String source = "namespace N{int pick(int n){return n;}int pick(double n){return 2;}}"
                + "int main(){int (*pointer)(int)=((&N::pick));int (&reference)(double)=(&N::pick);return pointer(reference(1.0));}";
        var api = CppReferenceTest.compiler(source);
        var semantic = CppReferenceTest.stage(api, SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        var result = semantic.semanticResult();
        var wrappers = CppReferenceTest.nodes(result.sourceProgram()).stream().filter(node ->
                node instanceof minic.compiler.parser.node.Expression.QualifiedNameExpr
                        || node instanceof minic.compiler.parser.node.Expression.GroupingExpr
                        || node instanceof minic.compiler.parser.node.Expression.UnaryExpr).toList();
        assertEquals(7, wrappers.size());
        var mapped = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<minic.compiler.parser.node.AstNode, Boolean>());
        for (var original : wrappers) {
            var core = result.sourceToCore().get(original);
            assertNotNull(core);
            assertEquals(original.range(), core.range());
            assertTrue(mapped.add(core), "Each source wrapper must retain its own reverse origin");
        }
    }
}
