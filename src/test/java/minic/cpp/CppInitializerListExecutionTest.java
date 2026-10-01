package minic.cpp;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.CppInitializer;
import minic.compiler.parser.node.Expression.MaterializeExpr;
import minic.compiler.parser.node.Expression.TemporaryLifetime;
import minic.compiler.semantic.SemanticAnalyzer;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.stream.Stream;
import static minic.cpp.CppReferenceTest.*;

/** Unified acceptance suite: list overload selection, readonly storage and backing-array lifetime. */
@Tag("cpp-differential") @Execution(ExecutionMode.SAME_THREAD) @Timeout(120)
final class CppInitializerListExecutionTest {
    @TempDir Path temporary;
    private static final String HEADERS = "#include <stdio.h>\n#include <initializer_list>\n";
    private static final String ITEM = """
            int trace;
            struct Item { int value;
              Item(int n):value(n){trace=trace*10+n;}
              Item(const Item& other):value(other.value){trace=trace*10+9;}
              ~Item(){trace=trace*10+value;}
            };
            """;

    static Stream<Arguments> programs() { return Stream.of(
        Arguments.of("named-and-empty-lists", """
            int main(){std::initializer_list<int> a={1,2,3};std::initializer_list<int> b={};
              printf("%llu %d %d %llu %d\\n",a.size(),a.begin()[0],a.end()[-1],b.size(),b.begin()==b.end());return 0;}
            """, "3 1 3 0 1\n"),
        Arguments.of("braced-call-argument", """
            int sum(std::initializer_list<int> values){int n=0;for(const int*p=values.begin();p!=values.end();++p)n+=*p;return n;}
            int main(){printf("%d %d\\n",sum({2,3,4}),sum({}));return 0;}
            """, "9 0\n"),
        Arguments.of("initializer-list-overload-preferred", """
            int choose(int n){return 1;}int choose(std::initializer_list<int> values){return 2;}
            int main(){printf("%d %d\\n",choose({3}),choose(3));return 0;}
            """, "2 1\n"),
        Arguments.of("initializer-list-element-ranking", """
            int choose(std::initializer_list<int> values){return 1;}
            int choose(std::initializer_list<double> values){return 2;}
            int main(){printf("%d %d\\n",choose({1,2}),choose({1.0,2.0}));return 0;}
            """, "1 2\n"),
        Arguments.of("list-constructor-first-phase", """
            struct Box{int n;Box(int a,int b):n(a*10+b){}
              Box(std::initializer_list<int> xs):n(100){for(const int*p=xs.begin();p!=xs.end();++p)n+=*p;}};
            int main(){Box a{1,2};Box b(1,2);printf("%d %d\\n",a.n,b.n);return 0;}
            """, "103 12\n"),
        Arguments.of("empty-braces-use-default-constructor", """
            struct Box{int n;Box():n(1){}Box(std::initializer_list<int> xs):n(2){}};
            int main(){Box a{};Box b{3};printf("%d %d\\n",a.n,b.n);return 0;}
            """, "1 2\n"),
        Arguments.of("explicit-list-constructor-direct-init", """
            struct Box{int n;explicit Box(std::initializer_list<int> xs):n(xs.begin()[0]){}};
            int main(){Box a{4};printf("%d\\n",a.n);return 0;}
            """, "4\n"),
        Arguments.of("copy-shares-backing-storage", """
            int main(){std::initializer_list<int> a={4,5};std::initializer_list<int> b=a;
              printf("%d %llu %d\\n",a.begin()==b.begin(),b.size(),b.begin()[1]);return 0;}
            """, "1 2 5\n"),
        Arguments.of("nested-lists", """
            int main(){std::initializer_list<std::initializer_list<int>> a={{1,2},{3,4,5}};
              printf("%llu %d %llu\\n",a.size(),a.begin()[0].begin()[1],a.begin()[1].size());return 0;}
            """, "2 2 3\n"),
        Arguments.of("named-backing-array-destruction", ITEM + """
            int main(){{std::initializer_list<Item> values={Item(1),Item(2)};printf("%d %d\\n",trace,values.begin()[1].value);}
              printf("%d\\n",trace);return 0;}
            """, "12 2\n1221\n"),
        Arguments.of("call-backing-array-full-expression", ITEM + """
            void consume(std::initializer_list<Item> values){printf("%d %llu\\n",trace,values.size());}
            int main(){consume({Item(1),Item(2)});printf("%d\\n",trace);return 0;}
            """, "12 2\n1221\n"),
        Arguments.of("returned-list-does-not-extend-call-backing", ITEM + """
            std::initializer_list<Item> identity(std::initializer_list<Item> values){return values;}
            int main(){std::initializer_list<Item> values=identity({Item(1)});printf("%d\\n",trace);return 0;}
            """, "11\n"),
        Arguments.of("reference-to-list-extends-storage", ITEM + """
            int main(){{const std::initializer_list<Item>& values={Item(1),Item(2)};printf("%d %llu\\n",trace,values.size());}
              printf("%d\\n",trace);return 0;}
            """, "12 2\n1221\n"),
        Arguments.of("functional-list-prvalue-lifetime", ITEM + """
            int main(){{std::initializer_list<Item> values=std::initializer_list<Item>{Item(1)};printf("%d\\n",trace);}
              printf("%d\\n",trace);return 0;}
            """, "1\n11\n"),
        Arguments.of("global-and-local-static-storage", """
            std::initializer_list<int> global={7,8};
            const int* get(){static std::initializer_list<int> local={9,10};return local.begin();}
            int main(){const int*p=get();printf("%d %d %d\\n",global.begin()[1],p[1],p==get());return 0;}
            """, "8 10 1\n"),
        Arguments.of("initializer-list-element-user-conversion", """
            struct Number{int n;Number(int v):n(v){}operator int()const{return n;}};
            int main(){std::initializer_list<int> values={Number(4),Number(5)};
              printf("%d %d\\n",values.begin()[0],values.begin()[1]);return 0;}
            """, "4 5\n"),
        Arguments.of("list-elements-sequenced-once", """
            int trace;int next(int n){trace=trace*10+n;return n;}
            int main(){std::initializer_list<int> xs={next(1),next(2),next(3)};
              printf("%d %d\\n",trace,xs.begin()[2]);return 0;}
            """, "123 3\n"),
        Arguments.of("reference-list-single-existing-object", """
            int main(){std::initializer_list<int> a={3};std::initializer_list<int>&r={a};
              printf("%d %d\\n",&r==&a,r.begin()[0]);return 0;}
            """, "1 3\n"),
        Arguments.of("assignment-retains-whole-brace-list", """
            struct Box{int sum;Box():sum(0){}Box&operator=(std::initializer_list<int>x){
              sum=x.begin()[0]+x.begin()[1];return *this;}};
            int main(){Box x;x={3,4};printf("%d\\n",x.sum);return 0;}
            """, "7\n"),
        Arguments.of("braced-subscript-argument", """
            struct Box{int operator[](std::initializer_list<int>x){return x.begin()[0]+x.begin()[1];}};
            int main(){Box x;printf("%d\\n",x[{3,4}]);return 0;}
            """, "7\n"),
        Arguments.of("const-array-reference-from-braces", """
            int main(){const int(&values)[3]={1,2};printf("%d %d %d\\n",values[0],values[1],values[2]);return 0;}
            """, "1 2 0\n"),
        Arguments.of("default-list-of-incomplete-type", """
            struct Item;int main(){std::initializer_list<Item> xs=std::initializer_list<Item>();printf("%llu\\n",xs.size());return 0;}
            """, "0\n"),
        Arguments.of("other-namespace-name-is-ordinary-class", """
            namespace other{template<class T>struct initializer_list{T value;initializer_list(T n):value(n){}};}
            int main(){other::initializer_list<int> x{6};printf("%d\\n",x.value);return 0;}
            """, "6\n")
    ); }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void followsCpp17OnAllBackends(String name,String source,String expected)throws Exception {
        agree(temporary,name,HEADERS+source,expected);
    }

    static Stream<Arguments> invalidPrograms(){return Stream.of(
        Arguments.of("backing-elements-are-const", "int main(){std::initializer_list<int> x={1};*x.begin()=2; // bad\nreturn 0;}"),
        Arguments.of("narrowing-element", "int main(){std::initializer_list<int> x={1.5}; // bad\nreturn 0;}"),
        Arguments.of("no-element-conversion", "struct X{};int main(){std::initializer_list<int> x={X()}; // bad\nreturn 0;}"),
        Arguments.of("ambiguous-list-overload", "int f(std::initializer_list<float> x){return 1;}int f(std::initializer_list<double> x){return 2;}int main(){return f({1}); // bad\n}"),
        Arguments.of("nonconst-reference-to-list", "void f(std::initializer_list<int>&x){}int main(){f({1}); // bad\nreturn 0;}"),
        Arguments.of("explicit-copy-list-constructor", "struct X{explicit X(std::initializer_list<int> x){}};int main(){X x={1,2}; // bad\nreturn 0;}")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("invalidPrograms")
    void preservesRequiredLanguageErrors(String name,String source)throws Exception {
        reject(temporary,name,HEADERS+source);
    }
    @Test void emptyBracesOfIncompleteElementUseValueInitializationBeforeListBackingRules() throws Exception {
        // N4659 [dcl.init.list]/3.4 precedes 3.5: an empty list uses the
        // default constructor. G++ 8 incorrectly tries to form const Item[0]
        // for this spelling, so keep the normative {} test independent of that
        // oracle; the equivalent () spelling remains a three-backend case.
        String source=HEADERS+"struct Item;int main(){std::initializer_list<Item> xs{};printf(\"%llu\\n\",xs.size());return 0;}";
        var ir=compiler(source).runToIr();
        var file=new minic.compiler.SourceFile("empty-incomplete-list.cpp",source);
        var debug=minic.debug.DebugApi.fromIr(file,ir,"");
        for(int steps=0;debug.canNext()&&steps<2000;steps++)debug.next();
        assertFalse(debug.canNext());
        assertEquals(minic.debug.Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals("0\n",debug.current().runtime().stdout().replace("\r\n","\n"));
        var assembler=new minic.compiler.asm.Assembler(ir);
        var object=new minic.compiler.obj.ObjBuilder(file,assembler,temporary,"empty-list");
        var linker=new minic.compiler.link.Linker(file,object,temporary,"empty-list");
        new minic.compiler.CompilerApi(java.util.List.of(assembler,object,linker)).runThrough(linker);
        assertTrue(linker.succeeded(),()->linker.errors().toString());
        var nativeResult=minic.cpp.support.BoundedProcess.run(java.util.List.of(temporary.resolve("empty-list.exe").toString()),
                temporary,"",java.time.Duration.ofSeconds(10),65536);
        assertFalse(nativeResult.timedOut());assertFalse(nativeResult.outputExceeded());
        assertEquals(0,nativeResult.exitCode(),nativeResult::stderr);
        assertEquals("0\n",nativeResult.stdout().replace("\r\n","\n"));
    }

    @Test void backingStorageIsConstScopedAndKeepsSourceMappingWithoutHeap(){
        var api=compiler(HEADERS+"int main(){std::initializer_list<int> values={1,2};return values.begin()[1]-2;}");
        var parser=stage(api,Parser.class);var semantic=stage(api,SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(parser.succeeded(),()->parser.errors().toString());
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        var result=semantic.semanticResult();
        var arrays=nodes(result.program()).stream().filter(MaterializeExpr.class::isInstance).map(MaterializeExpr.class::cast)
                .filter(temporary->temporary.type().isArray()).toList();
        assertFalse(arrays.isEmpty());
        assertTrue(arrays.stream().allMatch(temporary->temporary.type().elementType().isConstQualified()
                &&temporary.lifetime().kind()==TemporaryLifetime.Kind.REFERENCE_SCOPE));
        var list=nodes(parser.result().program()).stream().filter(CppInitializer.class::isInstance).map(CppInitializer.class::cast)
                .filter(initializer->initializer.kind()==CppInitializer.Kind.COPY_LIST&&initializer.arguments().size()==2).findFirst().orElseThrow();
        assertNotNull(result.sourceToCore().get(list));assertEquals(list.range(),result.sourceToCore().get(list).range());
        var ir=api.runToIr();assertTrue(ir.externalFunctionNames().stream().noneMatch(name->name.equals("malloc")||name.equals("free")));
    }
    @Test void constructorMemberCannotKeepTemporaryListBackingArray(){
        var api=compiler(HEADERS+"struct X{std::initializer_list<int> values;X():values{1,2}{}};int main(){X x;return 0;}");
        var parser=stage(api,Parser.class);var semantic=stage(api,SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(parser.succeeded(),()->parser.errors().toString());assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(error->error.message().contains("backing array")),()->semantic.errors().toString());
    }

}
