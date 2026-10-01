package minic.cpp;

import minic.compiler.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Source programs are retained for the final unified run; this slice only compiles their Java tests. */
@Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppDefaultedDeletedTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("multiple-defaulted-copy-overloads","""
            struct Item{int value;Item():value(1){}Item(Item&):value(2){}Item(const Item&):value(3){}
            Item& operator=(Item&){value=4;return *this;}Item& operator=(const Item&){value=5;return *this;}};
            struct Box{Item item;Box()=default;Box(Box&)=default;Box(const Box&)=default;
            Box& operator=(Box&)=default;Box& operator=(const Box&)=default;};
            int main(){Box first;const Box fixed;Box a(first);Box b(fixed);Box c;Box d;c=first;d=fixed;
            printf("%d %d %d %d\\n",a.item.value,b.item.value,c.item.value,d.item.value);return 0;}
            ""","2 3 4 5\n"),
        Arguments.of("return-static-is-not-moved","""
            struct Box{int value;Box(int v):value(v){}Box(const Box&)=default;Box(Box&& other):value(other.value){other.value=0;}};
            Box read(){static Box source(7);return source;}
            int main(){Box first=read();Box second=read();printf("%d %d\\n",first.value,second.value);return 0;}
            ""","7 7\n"),
        Arguments.of("all-defaulted","""
            struct Box{int value;Box()=default;Box(const Box&)=default;Box(Box&&)=default;
            Box& operator=(const Box&)=default;Box& operator=(Box&&)=default;~Box()=default;};
            int main(){Box first{};first.value=7;Box second(first);Box third((Box&&)second);
            Box fourth{};fourth=first;fourth=(Box&&)third;printf("%d %d\\n",fourth.value,first.value);return 0;}
            ""","7 7\n"),
        Arguments.of("defaulted-move-only-member","""
            int moved=0;struct Item{int value;Item():value(5){}Item(const Item&)=delete;
            Item(Item&& other):value(other.value){other.value=0;moved++;}
            Item& operator=(const Item&)=delete;Item& operator=(Item&& other){value=other.value;other.value=0;moved++;return *this;}};
            struct Pair{Item first;Item second;Pair()=default;Pair(const Pair&)=default;Pair(Pair&&)=default;
            Pair& operator=(const Pair&)=default;Pair& operator=(Pair&&)=default;};
            int main(){Pair first;Pair second((Pair&&)first);Pair third;third=(Pair&&)second;
            printf("%d %d %d\\n",third.first.value,first.second.value,moved);return 0;}
            ""","5 0 4\n"),
        Arguments.of("overload-deleted-loser","""
            int choose(int)=delete;int choose(double value){return 4;}
            struct Box{int read(int)=delete;int read(double value){return 7;}};
            int main(){Box value;printf("%d %d\\n",choose(1.5),value.read(2.5));return 0;}
            ""","4 7\n"),
        Arguments.of("template-deleted-loser","""
            template<class T>int choose(T)=delete;int choose(int value){return value;}
            struct Box{template<class T>int read(T)=delete;int read(int value){return value+1;}};
            int main(){Box value;printf("%d %d\\n",choose(4),value.read(5));return 0;}
            ""","4 6\n"),
        Arguments.of("out-of-line-defaulted","""
            int copies=0;struct Item{int value;Item():value(8){}Item(const Item& other):value(other.value){copies++;}
            Item& operator=(const Item& other){value=other.value;copies++;return *this;}};
            struct Box{Item item;Box();Box(const Box&);Box& operator=(const Box&);~Box();};
            Box::Box()=default;Box::Box(const Box&)=default;Box& Box::operator=(const Box&)=default;Box::~Box()=default;
            int main(){Box first;Box second(first);Box third;third=second;printf("%d %d\\n",third.item.value,copies);return 0;}
            ""","8 2\n"),
        Arguments.of("mutable-copy-signature","""
            int copies=0;struct Item{int value;Item():value(3){}Item(Item& other):value(other.value){copies++;}};
            struct Box{Item item;Box()=default;Box(Box&)=default;};
            int main(){Box first;Box second(first);printf("%d %d\\n",second.item.value,copies);return 0;}
            ""","3 1\n"),
        Arguments.of("cxx17-aggregate-defaulted-and-deleted","""
            struct Defaulted{int value;Defaulted()=default;};struct Deleted{int value;Deleted()=delete;};
            int main(){Defaulted first={3};Deleted second={7};printf("%d %d\\n",first.value,second.value);return 0;}
            ""","3 7\n"),
        Arguments.of("defaulted-constructor-value-zero","""
            struct Box{int value;Box()=default;};int main(){Box value=Box();printf("%d\\n",value.value);return 0;}
            ""","0\n"),
        Arguments.of("dependent-defaulted-copy","""
            template<class T>struct Box{T value;Box()=default;Box(const Box&)=default;Box(Box&&)=default;
            Box& operator=(const Box&)=default;Box& operator=(Box&&)=default;};
            int main(){Box<int> first={9};Box<int> second(first);Box<int> third{};third=(Box<int>&&)second;
            printf("%d\\n",third.value);return 0;}
            ""","9\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void standardSpecialMembersAgree(String name,String source,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM)
                .run(name,"#include <stdio.h>\n"+source,"");
        assertTrue(report.passed(),report::describe);
        assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
    }
    @ParameterizedTest @ValueSource(strings={
        "int f(int)=delete;int f(double){return 2;}int main(){return f(1);}",
        "int f(int)=delete;int main(){int(*pointer)(int)=f;return 0;}",
        "int f()=delete;int main(){return sizeof(f());}",
        "int f();int f()=delete;int main(){return 0;}",
        "int f()=default;int main(){return 0;}",
        "struct Box{void f()=default;};int main(){return 0;}",
        "struct Box{Box(int value=0)=default;};int main(){return 0;}",
        "struct Box{void operator=(const Box&)=default;};int main(){return 0;}",
        "struct Box{Box()=delete;};int main(){Box value;return 0;}",
        "struct Item{Item(){}Item(const Item&)=delete;};struct Box{Item item;Box()=default;Box(const Box&)=default;};int main(){Box first;Box second(first);return 0;}",
        "struct Box{Box(){}Box& operator=(const Box&)=delete;};int main(){Box first;Box second;first=second;return 0;}",
        "struct Box{~Box()=delete;};int main(){Box value;return 0;}",
        "struct Box{int& alias;Box()=default;};int main(){Box value;return 0;}",
        "struct Box{const int value;Box();};Box::Box()=default;int main(){return 0;}",
        "template<class T>int f(T)=delete;int main(){return f(1);}",
        "struct Box{template<class T>Box(T)=default;};int main(){return 0;}"
    })
    void deletionAndInvalidDefaultingAreDiagnosed(String source)throws Exception{
        Path input=temporary.resolve("invalid.cpp");Files.writeString(input,source);
        var reference=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()).toString(),
                "-std=c++17","-pedantic-errors","-fsyntax-only",input.toString()),temporary,"",Duration.ofSeconds(20),65536);
        assertFalse(reference.timedOut());assertFalse(reference.outputExceeded());assertNotEquals(0,reference.exitCode());
        var api=new CompilerApi(new SourceFile("invalid-special-member.cpp",source),LanguageMode.CPP17_ALGORITHM);
        var semantic=api.stages().stream().filter(SemanticAnalyzer.class::isInstance).map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        api.runThrough(semantic);
        assertTrue(api.stages().stream().anyMatch(stage->!stage.errors().isEmpty()));
    }
}
