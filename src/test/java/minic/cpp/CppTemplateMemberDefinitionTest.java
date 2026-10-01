package minic.cpp;

import minic.compiler.*;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.type.MiniType;
import minic.cpp.support.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Acceptance corpus. Saved and Java-compiled; execution belongs to the unified verification. */
@Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppTemplateMemberDefinitionTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("method-layout-and-renamed-parameter", """
            template<class T>struct Box{T value;T get()const;void set(T);};
            template<class U>U Box<U>::get()const{return value;}
            template<class U>void Box<U>::set(U next){value=next;}
            int main(){Box<int> a={2};Box<double>b={1.5};a.set(7);b.set(2.5);printf("%d %.1f %d %d\\n",a.get(),b.get(),(int)sizeof(a),(int)sizeof(b));return 0;}
            ""","7 2.5 4 8\n"),
        Arguments.of("constructor-destructor-and-this", """
            int total=0;template<class T>struct Box{T value;Box(T);~Box();T get()const;};
            template<class U>Box<U>::Box(U v):value(v){}
            template<class U>Box<U>::~Box(){total+=(int)value;}
            template<class U>U Box<U>::get()const{return this->value;}
            int main(){{Box<int>a(3);Box<double>b(4.0);printf("%d %.1f\\n",a.get(),b.get());}printf("%d\\n",total);return 0;}
            ""","3 4.0\n7\n"),
        Arguments.of("injected-class-and-member-alias", """
            template<class T>struct Box{typedef T value_type;T value;Box* next;Box& link(Box*);value_type read()const;};
            template<class U>Box<U>& Box<U>::link(Box* other){next=other;return *this;}
            template<class U>typename Box<U>::value_type Box<U>::read()const{return value;}
            int main(){Box<int>a={2,0};Box<int>b={9,0};printf("%d %d\\n",a.link(&b).read(),a.next->read());return 0;}
            ""","2 9\n"),
        Arguments.of("static-storage-per-specialization", """
            template<class T>struct Counter{static int value;static int next();};
            template<class U>int Counter<U>::value=2;
            template<class U>int Counter<U>::next(){return ++value;}
            int main(){int a=Counter<int>::next();int b=Counter<double>::next();int c=Counter<int>::next();printf("%d %d %d %d\\n",a,b,c,Counter<double>::value);return 0;}
            ""","3 3 4 3\n"),
        Arguments.of("constant-static-odr-address", """
            template<class T,T V>struct Constant{static const T value=V;};
            template<class U,U N>const U Constant<U,N>::value;
            int main(){const int*p=&Constant<int,7>::value;const int*q=&Constant<int,8>::value;printf("%d %d %d\\n",*p,*q,p!=q);return 0;}
            ""","7 8 1\n"),
        Arguments.of("definition-point-lookup", """
            int pick(int){return 3;}
            namespace N{template<class T>struct Box{int read();};}
            template<class U>int N::Box<U>::read(){return pick(1);}
            int pick(double){return 9;}
            int main(){N::Box<int> b;printf("%d\\n",b.read());return 0;}
            ""","3\n"),
        Arguments.of("unused-dependent-bodies-and-static-init", """
            template<class T>struct Box{static int unused;int invalid();int good();};
            template<class U>int Box<U>::unused=U::missing;
            template<class U>int Box<U>::invalid(){return U::missing;}
            template<class U>int Box<U>::good(){return 5;}
            int main(){Box<int>b;printf("%d\\n",b.good());return 0;}
            ""","5\n"),
        Arguments.of("definition-after-first-use", """
            template<class T>struct Box{static int count;T read(T);};
            int use(){Box<int>b;return b.read(4)+Box<int>::count;}
            template<class U>U Box<U>::read(U value){return value+1;}
            template<class U>int Box<U>::count=3;
            int main(){printf("%d\\n",use());return 0;}
            ""","8\n"),
        Arguments.of("partial-specialization-members", """
            template<class T>struct Box{int read();};
            template<class T>struct Box<T*>{int read();};
            template<class T>int Box<T>::read(){return 1;}
            template<class U>int Box<U*>::read(){return 2;}
            int main(){Box<int>a;Box<int*>b;printf("%d %d\\n",a.read(),b.read());return 0;}
            ""","1 2\n"),
        Arguments.of("partial-member-type-scope", """
            template<class T>struct Box{typedef T type;T read(type);};
            template<class T>struct Box<T*>{typedef T type;T read(type);};
            template<class U>U Box<U>::read(type value){return value;}
            template<class U>U Box<U*>::read(type value){return value+1;}
            int main(){Box<int>a;Box<int*>b;printf("%d %d\\n",a.read(2),b.read(3));return 0;}
            ""","2 4\n"),
        Arguments.of("pack-owner", """
            template<class... T>struct Count{static int size();};
            template<class... U>int Count<U...>::size(){return sizeof...(U);}
            int main(){printf("%d %d\\n",Count<>::size(),Count<int,double>::size());return 0;}
            ""","0 2\n"),
        Arguments.of("conversion-and-noexcept", """
            template<class T>struct Box{T value;operator T()const noexcept;T get()const noexcept(sizeof(T)==4);};
            template<class U>Box<U>::operator U()const noexcept{return value;}
            template<class U>U Box<U>::get()const noexcept(sizeof(U)==4){return value;}
            int main(){Box<int>a={7};printf("%d %d %d\\n",(int)a,a.get(),noexcept(a.get()));return 0;}
            ""","7 7 1\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void memberDefinitionsExecutePerConcreteOwner(String name,String source,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run(name,"#include <stdio.h>\n"+source,"");
        assertTrue(report.passed(),report::describe);assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
    }
    @ParameterizedTest @ValueSource(strings={
        "template<class T>struct Box{int read();};template<class T>double Box<T>::read(){return 1;}int main(){Box<int>b;return b.read();}",
        "template<class T>struct Box{int read()const;};template<class T>int Box<T>::read(){return 1;}int main(){Box<int>b;return b.read();}",
        "template<class T>struct Box{int read();};template<class T>int Box<T>::read(){return 1;}template<class U>int Box<U>::read(){return 2;}int main(){Box<int>b;return b.read();}",
        "template<class T>struct Box{static int value;};template<class T>double Box<T>::value=1;int main(){return Box<int>::value;}",
        "template<class T>struct Box{int read();};namespace Other{template<class T>int Box<T>::read(){return 1;}}int main(){Box<int>b;return b.read();}",
        "template<class T>struct Box{T read();};template<class U>U Box<U>::read(){return U::missing;}int main(){Box<int>b;return b.read();}",
        "template<class T>struct Box{int read()noexcept;};template<class U>int Box<U>::read()noexcept(false){return 1;}int main(){Box<int>b;return b.read();}",
        "template<class T>struct Box{int read();};template<class U>int Box<U>::read(){return unknown_name;}int main(){return 0;}"
    })
    void rejectsInvalidDefinitions(String source)throws Exception{
        Path file=temporary.resolve("invalid.cpp");Files.writeString(file,source);
        var reference=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()).toString(),"-std=c++17","-pedantic-errors","-fsyntax-only",file.toString()),temporary,"",Duration.ofSeconds(20),65536);
        assertFalse(reference.timedOut());assertFalse(reference.outputExceeded());assertNotEquals(0,reference.exitCode());
        var api=new CompilerApi(new SourceFile("invalid-member.cpp",source),LanguageMode.CPP17_ALGORITHM);
        var semantic=api.stages().stream().filter(minic.compiler.semantic.SemanticAnalyzer.class::isInstance).findFirst().orElseThrow();api.runThrough(semantic);
        assertTrue(api.stages().stream().anyMatch(stage->!stage.errors().isEmpty()));
    }
    @Test void ownerAndSourceRangesRemainStructured(){
        String text="template<class T>struct Box{T read()const;};template<class U>U Box<U>::read()const{return U(3);}";
        var source=new SourceFile("member.cpp",text);var lexer=new Lexer(source,LanguageMode.CPP17_ALGORITHM);var tokens=lexer.lex();
        var parser=new Parser(tokens.tokens(),LanguageMode.CPP17_ALGORITHM,true);parser.parse();assertTrue(parser.succeeded(),()->parser.errors().toString());
        var declaration=assertInstanceOf(CppTemplateMemberDefinition.class,parser.result().program().declarations().getLast());
        assertEquals("::Box",declaration.ownerType().templateName());assertEquals("U",declaration.parameters().getFirst().name());
        var method=assertInstanceOf(OutOfLineMethodDecl.class,declaration.declaration());
        assertEquals("read",source.text(method.nameRange()));assertEquals("template<class U>U Box<U>::read()const{return U(3);}",source.text(declaration.range()));
        assertEquals(declaration.parameters().getFirst().type(),method.method().returnType());assertNotNull(AstChildren.firstCppSyntax(declaration));
    }
}
