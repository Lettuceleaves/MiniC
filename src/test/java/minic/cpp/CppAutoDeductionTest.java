package minic.cpp;

import minic.compiler.parser.Parser;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.Statement.VarDeclStmt;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import minic.compiler.type.TypeLayout;
import minic.compiler.ir.manager.IrTypeLowerer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
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
import static org.junit.jupiter.api.Assertions.*;

/** Acceptance sources retained for the final unified compiler run. */
@Tag("cpp-differential") @Timeout(120) @Execution(ExecutionMode.SAME_THREAD)
final class CppAutoDeductionTest {
    @TempDir Path temporary;
    private static final String IO="#include <stdio.h>\n";
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("template-return-deduction", "template<class T>auto add(T a,T b){return a+b;}template<class T>auto alias(T& x)->decltype((x)){return x;}template<class T>decltype(auto) same(T& x){return (x);}template<class T>struct Box{T value;auto get(){return value;}template<class U>auto plus(U x){return value+x;}};int main(){int x=2;alias(x)=7;same(x)=8;Box<int>b={3};printf(\"%d %.1f %d %.1f %llu\\n\",x,add(1.0,2.5),b.get(),b.plus(0.5),sizeof(add(1,2)));return 0;}","8 3.5 3 3.5 4\n"),
        Arguments.of("cv-and-pointer", "int main(){const int x=3;auto a=x;a=4;const auto b=a;auto*p=&x;printf(\"%d %d %d\\n\",a,b,*p);return 0;}","4 4 3\n"),
        Arguments.of("reference-alias", "int main(){int x=3;auto&r=x;r=6;printf(\"%d %d\\n\",x,&r==&x);return 0;}","6 1\n"),
        Arguments.of("array-decay-versus-reference", "int main(){int a[3]={1,2,3};auto p=a;auto&r=a;r[1]=7;printf(\"%llu %llu %d\\n\",sizeof(p),sizeof(r),a[1]);return 0;}","8 12 7\n"),
        Arguments.of("function-decay-versus-reference", "int twice(int x){return x*2;}int main(){auto p=twice;auto&r=twice;printf(\"%d %d\\n\",p(3),r(4));return 0;}","6 8\n"),
        Arguments.of("decltype-name-versus-group", "int main(){int x=1;decltype(x) copy=x;decltype((x))alias=x;copy=4;alias=5;printf(\"%d %d\\n\",x,copy);return 0;}","5 4\n"),
        Arguments.of("decltype-declared-reference", "int main(){int x=1;int&r=x;decltype(r)alias=x;alias=8;printf(\"%d\\n\",x);return 0;}","8\n"),
        Arguments.of("decltype-member-declared-cv", "struct Box{int n;};int main(){const Box b={3};decltype(b.n)n=4;n=5;printf(\"%d\\n\",n);return 0;}","5\n"),
        Arguments.of("decltype-expression-is-unevaluated", "int main(){int x=1;decltype(++x)r=x;r+=2;printf(\"%d\\n\",x);return 0;}","3\n"),
        Arguments.of("decltype-incomplete-call-result", "struct Box;Box make();typedef decltype(make()) Result;int main(){Result*p=nullptr;printf(\"%d\\n\",p==nullptr);return 0;}","1\n"),
        Arguments.of("direct-versus-copy-list", "#include <initializer_list>\nint main(){auto one{4};auto list={1,2,3};printf(\"%llu %llu %d\\n\",sizeof(one),list.size(),list.begin()[2]);return 0;}","4 3 3\n"),
        Arguments.of("const-auto-list-reference", "#include <initializer_list>\nint main(){const auto&r={2,5};printf(\"%llu %d\\n\",r.size(),r.begin()[1]);return 0;}","2 5\n"),
        Arguments.of("global-and-static-auto", "auto global=4;int next(){static auto n=2;return ++n;}int main(){printf(\"%d %d\\n\",global,next());return 0;}","4 3\n"),
        Arguments.of("deduced-value-and-void-returns", "int g;auto set(){g=3;}auto value(int x){if(x)return 4;return 5;}int main(){set();printf(\"%d %d\\n\",g,value(1));return 0;}","3 4\n"),
        Arguments.of("recursive-after-first-return", "auto factorial(int n){if(n==0)return 1;return n*factorial(n-1);}int main(){printf(\"%d\\n\",factorial(5));return 0;}","120\n"),
        Arguments.of("deduced-reference-return", "auto&ref(int&x){return x;}int main(){int x=2;ref(x)=7;printf(\"%d\\n\",x);return 0;}","7\n"),
        Arguments.of("decltype-auto-return", "decltype(auto)ref(int&x){return(x);}decltype(auto)copy(int x){return x;}int main(){int x=2;ref(x)=7;auto y=copy(x);y=8;printf(\"%d %d\\n\",x,y);return 0;}","7 8\n"),
        Arguments.of("method-return-deduction", "struct Box{int n;auto get()const{return n;}auto&ref(){return n;}};int main(){Box b={3};b.ref()=9;printf(\"%d\\n\",b.get());return 0;}","9\n"),
        Arguments.of("trailing-return-parameter-scope", "auto ref(int&x)->decltype((x)){return x;}int main(){int x=1;ref(x)=8;printf(\"%d\\n\",x);return 0;}","8\n"),
        Arguments.of("const-method-trailing-return", "struct Box{int n;auto get()const->decltype(n){return n;}};int main(){Box b={3};printf(\"%d\\n\",b.get());return 0;}","3\n"),
        Arguments.of("out-of-line-deduced-method", "struct Box{int n;auto get();};auto Box::get(){return n;}int main(){Box b={4};printf(\"%d\\n\",b.get());return 0;}","4\n"),
        Arguments.of("prototype-before-deduced-definition", "auto get();auto get(){return 3LL;}auto get();int main(){printf(\"%llu %lld\\n\",sizeof(get()),get());return 0;}","8 3\n"),
        Arguments.of("template-method-auto-result", "template<class T>struct Box{T n;auto get(){return n;}};int main(){Box<long long>b={4};printf(\"%llu %lld\\n\",sizeof(b.get()),b.get());return 0;}","8 4\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void matchesReferenceCpp(String name,String source,String expected)throws Exception{agree(temporary,name,IO+source,expected);}
    static Stream<Arguments> invalidPrograms(){return Stream.of(
        Arguments.of("missing-initializer","int main(){auto x; // bad\nreturn 0;}"),
        Arguments.of("self-before-deduction","int main(){auto x=x; // bad\nreturn 0;}"),
        Arguments.of("const-auto-write","int main(){const auto x=1;x=2; // bad\nreturn 0;}"),
        Arguments.of("mutable-ref-to-prvalue","int main(){auto&r=1; // bad\nreturn 0;}"),
        Arguments.of("multiple-direct-list-elements","int main(){auto x{1,2}; // bad\nreturn 0;}"),
        Arguments.of("heterogeneous-copy-list","#include <initializer_list>\nint main(){auto x={1,2.0}; // bad\nreturn 0;}"),
        Arguments.of("decltype-auto-copy-list","int main(){decltype(auto)x={1}; // bad\nreturn 0;}"),
        Arguments.of("mixed-return-types","auto f(int x){if(x)return 1;return 1.0; // bad\n}int main(){return 0;}"),
        Arguments.of("call-before-return-deduction","auto f();int main(){return f(); // bad\n}auto f(){return 1;}"),
        Arguments.of("recursive-before-return-deduction","auto f(int x){return x?f(x-1):0; // bad\n}int main(){return 0;}"),
        Arguments.of("ordinary-auto-parameter","int f(auto x){return x;} // bad\nint main(){return 0;}")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("invalidPrograms")
    void rejectsUndeducibleOrInconsistentTypes(String name,String source)throws Exception{reject(temporary,name,source);}
    @Test void decltypeAutoCannotHaveAdditionalCvQualifiers(){
        // N4659 [dcl.type.auto.deduct]/5; G++ 8.1 accepts this invalid declaration.
        // https://timsong-cpp.github.io/cppwp/n4659/dcl.type.auto.deduct#5
        var api=compiler("int main(){const decltype(auto)x=1;return 0;}");
        var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d->d.code().equals("CPP004")
                && d.message().contains("decltype(auto) cannot have additional")),()->semantic.errors().toString());
    }
    @Test void sourcePlaceholdersRemainSourceOnly(){
        var api=compiler("int main(){auto value=3;decltype((value))alias=value;return alias-3;}");
        var parser=stage(api,Parser.class);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(parser.succeeded(),()->parser.errors().toString());assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        assertTrue(nodes(parser.result().program()).stream().filter(VarDeclStmt.class::isInstance).map(VarDeclStmt.class::cast).anyMatch(n->n.type().containsPlaceholder()));
        assertNull(AstChildren.firstCppSyntax(semantic.semanticResult().program()));
        assertFalse(TypeLayout.hasFixedLayout(MiniType.AUTO));
        assertThrows(IllegalArgumentException.class,()->IrTypeLowerer.lower(MiniType.AUTO.pointerTo()));
    }
}
