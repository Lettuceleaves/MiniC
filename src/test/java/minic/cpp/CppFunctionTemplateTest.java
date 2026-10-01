package minic.cpp;

import minic.compiler.*;
import minic.cpp.support.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Function/member template acceptance cases, retained for the final unified run. */
@Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppFunctionTemplateTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("list-template-deduction-and-defaults","""
            #include <initializer_list>
            template<class T>int length(std::initializer_list<T> xs){return (int)xs.size();}
            template<class T,int N>int extent(const T (&xs)[N]){return N;}
            template<class T>int nested(std::initializer_list<std::initializer_list<T>> xs){return (int)xs.size();}
            int defaultList(std::initializer_list<int> xs={1,2,3}){return (int)xs.size();}
            struct Box{int n;Box(int value=7):n(value){}template<class T>Box(std::initializer_list<T> xs,int extra=4):n((int)xs.size()+extra){}};
            int main(){Box a{1,2};Box b{};printf("%d %d %d %d %d %d\\n",length({1,2,3}),extent({4,5}),nested({{1,2},{3}}),defaultList(),a.n,b.n);return 0;}
            ""","3 2 2 3 6 7\n"),
        Arguments.of("deduced-and-explicit","""
            template<class T>T maximum(T a,T b){return a<b?b:a;}
            int main(){printf("%d %.1f %.1f\\n",maximum(3,7),maximum(2.5,1.5),maximum<double>(2,4));return 0;}
            ""","7 2.5 4.0\n"),
        Arguments.of("reference-and-pointer-qualification","""
            template<class T>T& identity(T& value){return value;}
            template<class T>T read(const T* value){return *value;}
            int main(){int value=4;identity(value)=9;printf("%d %d\\n",value,read(&value));return 0;}
            ""","9 9\n"),
        Arguments.of("array-bound-deduction","""
            template<class T,int N>int length(const T (&values)[N]){return N;}
            int main(){int values[3]={2,4,6};double other[2]={1.5,2.5};printf("%d %d\\n",length(values),length(other));return 0;}
            ""","3 2\n"),
        Arguments.of("overload-ordering","""
            template<class T>int pick(T value){return 1;}
            template<class T>int pick(T* value){return 2;}
            template<class T>int pick(const T* value){return 3;}
            int pick(int value){return 4;}
            int main(){int value=1;const int c=2;printf("%d %d %d %d\\n",pick(value),pick(&value),pick(&c),pick(1.5));return 0;}
            ""","4 2 3 1\n"),
        Arguments.of("member-templates","""
            template<class T>struct Box{T value;template<class U>U convert(U bias) const{return (U)value+bias;}template<class U>static U twice(U value){return value+value;}};
            int main(){Box<int> value={3};printf("%.1f %d\\n",value.convert<double>(0.5),Box<int>::twice(4));return 0;}
            ""","3.5 8\n"),
        Arguments.of("constructor-template","""
            template<class T>struct Box{T value;Box(T input):value(input){}template<class U>Box(const Box<U>& other):value((T)other.value){}};
            int main(){Box<int> first(7);Box<double> second(first);printf("%.1f\\n",second.value);return 0;}
            ""","7.0\n"),
        Arguments.of("defaults-and-definition-scope","""
            int amount=3;int plain(int input,int extra=amount){return input+extra;}
            template<class T=int>T add(T first,T second=5){return first+second;}
            int main(){int amount=90;printf("%d %d %.1f\\n",plain(2),add(4),add<double>(1.5));return 0;}
            ""","5 9 6.5\n"),
        Arguments.of("pointer-default-argument","""
            template<class T>int present(T value,const T* pointer=0){return pointer?*pointer:value;}
            int main(){int value=9;printf("%d %d\\n",present(3),present(3,&value));return 0;}
            ""","3 9\n"),
        Arguments.of("sfinae-return","""
            template<bool B,class T=int>struct Enable{};template<class T>struct Enable<true,T>{typedef T type;};
            template<class T>typename Enable<(sizeof(T)==4),int>::type width(T value){return 4;}
            int width(...){return 0;}
            int main(){printf("%d %d\\n",width(2),width(2.0));return 0;}
            ""","4 0\n"),
        Arguments.of("sfinae-default-template-parameter","""
            template<bool B,class T=int>struct Enable{};template<class T>struct Enable<true,T>{typedef T type;};
            template<class T,typename Enable<(sizeof(T)==4),int>::type=0>int select(T value){return 7;}
            int select(...){return 2;}
            int main(){printf("%d %d\\n",select(1),select(1.0));return 0;}
            ""","7 2\n"),
        Arguments.of("layout-value-arguments","""
            struct Record{double x;int y;};
            template<unsigned long long N,unsigned long long A>struct Layout{char bytes[N];unsigned long long size(){return N;}unsigned long long alignment(){return A;}};
            template<class T>unsigned long long layout(){Layout<sizeof(T),alignof(T)> result;return result.size()*10+result.alignment();}
            int main(){printf("%llu %llu\\n",layout<int>(),layout<Record>());return 0;}
            ""","44 168\n"),
        Arguments.of("definition-lookup-and-adl","""
            int chosen(int value){return 1;}
            template<class T>int call(T value){return chosen(value);}
            int chosen(double value){return 2;}
            namespace A{struct Value{int n;};int chosen(Value value){return value.n;}}
            int main(){A::Value value={8};printf("%d %d\\n",call(1.5),call(value));return 0;}
            ""","1 8\n"),
        Arguments.of("function-address","""
            template<class T>T advance(T value){return value+1;}
            template<class T>T origin(){return 6;}
            int main(){int(*first)(int)=advance;double(*second)(double)=&advance<double>;int(*third)()=origin;printf("%d %.1f %d\\n",first(3),second(1.5),third());return 0;}
            ""","4 2.5 6\n"),
        Arguments.of("recursive-template-and-late-definition","""
            template<class T>T sum(T value);
            int before(){return sum(4);}
            template<class U>U sum(U value){return value?value+sum<U>(value-1):0;}
            int main(){printf("%d\\n",before());return 0;}
            ""","10\n"),
        Arguments.of("unused-dependent-body","""
            template<class T>int unused(T value){return value.missing();}
            template<class T>struct Box{T value;template<class U>int unused(U value){return value.missing();}int get(){return value;}};
            int main(){Box<int> value={5};printf("%d\\n",value.get());return 0;}
            ""","5\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void genericFunctionsAgreeAcrossBackends(String name,String source,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM)
                .run(name,"#include <stdio.h>\n"+source,"");
        assertTrue(report.passed(),report::describe);
        assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
    }
    @ParameterizedTest @ValueSource(strings={
        "#include <initializer_list>\ntemplate<class T>int length(std::initializer_list<T> xs){return (int)xs.size();}int main(){return length({1,2.0});}",
        "#include <initializer_list>\ntemplate<class T>int length(std::initializer_list<T> xs){return (int)xs.size();}int main(){return length({});}",
        "template<class T>T both(T a,T b){return a+b;}int main(){return both(1,2.0);}",
        "template<class T>int missing(){return 1;}int main(){return missing();}",
        "template<class T>int body(T value){return value.missing();}int main(){return body(1);}",
        "struct Box{private:template<class T>int get(T value){return value;}};int main(){Box box;return box.get(3);}",
        "int f(int x=1);int f(int x=2){return x;}int main(){return f();}",
        "int f(int x=1,int y){return x+y;}int main(){return f();}",
        "template<class T>int f(T value){return never_declared;}int main(){return 0;}"
    })
    void invalidDeductionAndSelectedBodiesAreDiagnosed(String source)throws Exception{
        Path input=temporary.resolve("invalid.cpp");java.nio.file.Files.writeString(input,source);
        var reference=BoundedProcess.run(java.util.List.of(CppDifferentialHarness.referenceCompiler(System.getenv()).toString(),"-std=c++17","-pedantic-errors","-fsyntax-only",input.toString()),temporary,"",java.time.Duration.ofSeconds(20),64*1024);
        assertFalse(reference.timedOut());assertFalse(reference.outputExceeded());assertNotEquals(0,reference.exitCode());
        var api=new CompilerApi(new SourceFile("invalid-function-template.cpp",source),LanguageMode.CPP17_ALGORITHM);
        var semantic=api.stages().stream().filter(minic.compiler.semantic.SemanticAnalyzer.class::isInstance).map(minic.compiler.semantic.SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        api.runThrough(semantic);
        assertTrue(api.stages().stream().anyMatch(stage->!stage.errors().isEmpty()));
    }
}
