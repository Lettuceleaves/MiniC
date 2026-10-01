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

/** Compiled for the final unified acceptance run; copy/move counts avoid optional NRVO. */
@Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppMoveSemanticsTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("auto-and-decltype-xvalue","""
            int pick(int& value){return 1;}int pick(int&& value){return 2;}
            auto&& preserve(int& value){return value;}
            decltype(auto) xvalue(int& value){return (int&&)value;}
            int main(){int value=4;auto&& alias=value;alias=7;decltype((int&&)value) other=(int&&)value;
            printf("%d %d %d %d\\n",pick(alias),pick(preserve(value)),pick(xvalue(value)),value);return 0;}
            ""","1 1 2 7\n"),
        Arguments.of("rvalue-reference-braced-list","""
            struct Box{int value;Box(int v):value(v){}};
            int read(Box&& value){return value.value;}int zero(int&& value){return value;}
            int main(){printf("%d %d\\n",read({8}),zero({}));return 0;}
            ""","8 0\n"),
        Arguments.of("named-reference-and-return-category","""
            int pick(int& v){return 1;} int pick(const int& v){return 3;} int pick(int&& v){return 2;}
            int&& as_rvalue(int& value){return (int&&)value;}
            int main(){int v=4;const int c=5;int&& named=as_rvalue(v);named=8;
            printf("%d %d %d %d\\n",pick(named),pick(as_rvalue(v)),pick(c),v);return 0;}
            ""","1 2 3 8\n"),
        Arguments.of("forwarding-and-collapse","""
            template<class T>struct Remove{typedef T type;};
            template<class T>struct Remove<T&>{typedef T type;};
            template<class T>struct Remove<T&&>{typedef T type;};
            template<class T>T&& forward(typename Remove<T>::type& value){return (T&&)value;}
            int pick(int& value){return 1;}int pick(const int& value){return 2;}int pick(int&& value){return 3;}
            template<class T>int relay(T&& value){return pick(forward<T>(value));}
            int main(){int value=5;const int fixed=7;printf("%d %d %d\\n",relay(value),relay(fixed),relay(8));return 0;}
            ""","1 2 3\n"),
        Arguments.of("user-move-construction-and-assignment","""
            int moves=0;int copies=0;
            struct Box{int value;Box(int v):value(v){}Box(const Box& b):value(b.value){copies++;}
            Box(Box&& b):value(b.value){b.value=0;moves++;}
            Box& operator=(const Box& b){value=b.value;copies++;return *this;}
            Box& operator=(Box&& b){value=b.value;b.value=0;moves++;return *this;}};
            int main(){Box first(5);Box second((Box&&)first);Box third(1);third=(Box&&)second;
            printf("%d %d %d %d %d\\n",first.value,second.value,third.value,moves,copies);return 0;}
            ""","0 0 5 2 0\n"),
        Arguments.of("implicit-array-member-move","""
            int moves=0;
            struct Item{int value;Item():value(0){}Item(const Item& i):value(i.value){}
            Item(Item&& i):value(i.value){i.value=0;moves++;}
            Item& operator=(const Item& i){value=i.value;return *this;}
            Item& operator=(Item&& i){value=i.value;i.value=0;moves++;return *this;}};
            struct Group{Item values[2];};
            int main(){Group first;first.values[0].value=3;first.values[1].value=7;
            Group second((Group&&)first);Group third;third=(Group&&)second;
            printf("%d %d %d %d %d\\n",first.values[0].value,second.values[1].value,third.values[0].value,third.values[1].value,moves);return 0;}
            ""","0 0 3 7 4\n"),
        Arguments.of("destructor-suppresses-implicit-move","""
            int copies=0;int moves=0;
            struct Item{int value;Item():value(4){}Item(const Item& i):value(i.value){copies++;}
            Item(Item&& i):value(i.value){i.value=0;moves++;}};
            struct Outer{Item item;~Outer(){}};
            int main(){Outer first;Outer second((Outer&&)first);printf("%d %d %d %d\\n",first.item.value,second.item.value,copies,moves);return 0;}
            ""","4 4 1 0\n"),
        Arguments.of("move-only-return-local","""
            struct Box{int value;Box(int v):value(v){}Box(Box&& b):value(b.value){b.value=0;}
            private:Box(const Box&);};
            Box make(){Box value(9);return value;}
            int main(){Box value=make();printf("%d\\n",value.value);return 0;}
            ""","9\n"),
        Arguments.of("rvalue-reference-member-preserves-alias","""
            struct Alias{int&& value;};
            int main(){int value=6;Alias first={(int&&)value};Alias second((Alias&&)first);
            second.value=8;printf("%d %d\\n",value,first.value);return 0;}
            ""","8 8\n"),
        Arguments.of("const-xvalue-copies","""
            int copies=0;int moves=0;
            struct Box{int value;Box(int v):value(v){}Box(const Box& b):value(b.value){copies++;}
            Box(Box&& b):value(b.value){moves++;}};
            int main(){const Box first(3);Box second((const Box&&)first);printf("%d %d %d\\n",second.value,copies,moves);return 0;}
            ""","3 1 0\n"),
        Arguments.of("field-and-conditional-xvalues","""
            struct Box{int value;};int pick(int& value){return 1;}int pick(int&& value){return 2;}
            int main(){Box first={3};Box second={7};printf("%d %d %d\\n",pick(first.value),pick(((Box&&)first).value),pick(true?(int&&)first.value:(int&&)second.value));return 0;}
            ""","1 2 2\n"),
        Arguments.of("move-defaulted-tail","""
            struct Box{int value;Box(int v):value(v){}Box(Box&& b,int extra=2):value(b.value+extra){b.value=0;}};
            int main(){Box first(5);Box second((Box&&)first);printf("%d %d\\n",first.value,second.value);return 0;}
            ""","0 7\n"),
        Arguments.of("deleted-implicit-move-falls-back-to-copy","""
            int copies=0;struct Item{int value;Item():value(4){}Item(const Item& other):value(other.value){copies++;}
            private:Item(Item&&);};struct Outer{Item item;};
            int main(){Outer first;Outer second((Outer&&)first);printf("%d %d\\n",second.item.value,copies);return 0;}
            ""","4 1\n"),
        Arguments.of("reference-lifetime-and-single-evaluation","""
            int calls=0;int destroyed=0;struct Box{int value;Box(int v):value(v){}~Box(){destroyed++;}};
            Box&& view(Box& value){calls++;return (Box&&)value;}
            int main(){int result=0;{Box value(9);Box&& alias=view(value);alias.value=4;result=value.value;}
            printf("%d %d %d\\n",result,calls,destroyed);return 0;}
            ""","4 1 1\n"),
        Arguments.of("rvalue-reference-temporary-lifetime","""
            int destroyed=0;struct Box{int value;Box(int v):value(v){}~Box(){destroyed++;}};
            int main(){int observed=0;{Box&& alias=Box(6);observed=alias.value+destroyed;}
            printf("%d %d\\n",observed,destroyed);return 0;}
            ""","6 1\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void movesAndValueCategoriesAgreeAcrossBackends(String name,String source,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM)
                .run(name,"#include <stdio.h>\n"+source,"");
        assertTrue(report.passed(),report::describe);
        assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
    }
    @ParameterizedTest @ValueSource(strings={
        "int main(){int value=1;int&& alias=value;return alias;}",
        "struct Box{int value;};int main(){Box value={1};Box&& alias={value};return alias.value;}",
        "int main(){int value=1;int& alias=(int&&)value;return alias;}",
        "int main(){int value=1;(int&&)value=4;return value;}",
        "int main(){int value=1;int* pointer=&((int&&)value);return *pointer;}",
        "int main(){const int value=1;int&& alias=(const int&&)value;return alias;}",
        "struct Box{Box(){}Box(Box&&){}};int main(){Box first;Box second(first);return 0;}",
        "struct Box{Box(){}Box& operator=(Box&& other){return *this;}};int main(){Box first;Box second(first);return 0;}",
        "struct Alias{int&& value;};int main(){int value=1;Alias first={(int&&)value};Alias second(first);return 0;}"
    })
    void invalidReferencesAndDeletedCopiesAreDiagnosed(String source)throws Exception{
        Path input=temporary.resolve("invalid.cpp");Files.writeString(input,source);
        var reference=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()).toString(),
                "-std=c++17","-pedantic-errors","-fsyntax-only",input.toString()),temporary,"",Duration.ofSeconds(20),65536);
        assertFalse(reference.timedOut());assertFalse(reference.outputExceeded());assertNotEquals(0,reference.exitCode());
        var api=new CompilerApi(new SourceFile("invalid-move.cpp",source),LanguageMode.CPP17_ALGORITHM);
        var semantic=api.stages().stream().filter(SemanticAnalyzer.class::isInstance).map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        api.runThrough(semantic);
        assertTrue(api.stages().stream().anyMatch(stage->!stage.errors().isEmpty()));
    }
}
