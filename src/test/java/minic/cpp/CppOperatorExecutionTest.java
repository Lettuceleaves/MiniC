package minic.cpp;

import minic.compiler.*;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppOperatorExecutionTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs() { return Stream.of(
        Arguments.of("iterator", """
            struct Cursor { int* pointer;
              int& operator*(){return *pointer;} int& operator[](int index){return pointer[index];}
              Cursor& operator++(){++pointer;return *this;}
              Cursor operator++(int){Cursor old=*this;++pointer;return old;}
              Cursor& operator--(){--pointer;return *this;}
              bool operator==(const Cursor& other)const{return pointer==other.pointer;}
            };
            int main(){int a[4]={2,4,6,8};Cursor c={a};c[1]=9;int x=*c++;
              int y=*++c;int z=*--c;Cursor other={a+1};
              printf("%d %d %d %d %d\\n",x,y,z,c==other,a[1]);return 0;}
            """, "2 6 9 1 9\n"),
        Arguments.of("callable", """
            struct Compare { int operator()(int a,int b)const{return a<b;}
              int operator()(int a){return a+10;} int operator()(long long a)const{return (int)a+20;} };
            int main(){Compare f={};const Compare cf={};
              printf("%d %d %d\\n",f(3),cf(3LL),cf(2,5));return 0;}
            """, "13 23 1\n"),
        Arguments.of("member-arithmetic", """
            struct Value {int number;Value operator+(int amount)const{Value v={number+amount};return v;}
              int operator-()const{return -number;} int operator!()const{return number==0?9:4;}
              int operator<(const Value& other)const{return number<other.number?7:0;}
            };
            int main(){Value a={3};Value b=a+4;printf("%d %d %d %d\\n",b.number,-a,!a,a<b);return 0;}
            """, "7 -3 4 7\n"),
        Arguments.of("free-adl", """
            namespace N {struct Value{int number;};
              int operator+(int left,const Value& right){return left+right.number;}
              int operator-(const Value& value){return -value.number;}
              bool operator==(const Value& left,const Value& right){return left.number==right.number;}
              Value& operator++(Value& value){++value.number;return value;}
              Value operator++(Value& value,int){Value old=value;++value.number;return old;}
            }
            int main(){N::Value a={4};N::Value old=a++;N::Value b={6};++a;
              printf("%d %d %d %d\\n",2+a,-a,a==b,old.number);return 0;}
            """, "8 -6 1 4\n"),
        Arguments.of("member-free-selection", """
            struct Value {int number;int operator+(long long amount)const{return number+(int)amount+100;}};
            int operator+(const Value& value,int amount){return value.number+amount+200;}
            int main(){Value v={3};printf("%d %d\\n",v+2,v+2LL);return 0;}
            """, "205 105\n"),
        Arguments.of("receiver-order", """
            int log=0;struct Value {int values[2];int& operator[](int i){log=log*10+3;return values[i];}
              int operator()(int x){log=log*10+3;return values[0]+x;}};
            Value shared={{7,8}};Value& receiver(){log=log*10+1;return shared;}
            int argument(){log=log*10+2;return 0;}
            int main(){int a=receiver()[argument()];int first=log;log=0;int b=receiver()(argument());
              printf("%d %d %d %d\\n",a,first,b,log);return 0;}
            """, "7 123 7 123\n"),
        Arguments.of("out-of-line-and-const", """
            namespace N {struct Value {int number;int& operator[](int);const int& operator[](int)const;
              int operator()(int)const;};}
            int& N::Value::operator[](int){return number;}
            const int& N::Value::operator[](int)const{return number;}
            int N::Value::operator()(int amount)const{return number+amount;}
            int main(){N::Value v={3};v[0]=8;const N::Value c={4};printf("%d %d %d\\n",v[0],c[0],c(2));return 0;}
            """, "8 4 6\n"),
        Arguments.of("builtin-control", """
            struct Value{int number;};int main(){int a[2]={3,4};int* p=a;int x=*p++;int y=1;
              printf("%d %d %d %d\\n",x,*p,++y,3+4);return 0;}
            """, "3 4 2 7\n"),
        Arguments.of("remaining-arithmetic", """
            struct Number {int value;
              int operator-(int x)const{return value-x;} int operator*(int x)const{return value*x;}
              int operator/(int x)const{return value/x;} int operator%(int x)const{return value%x;}
              int operator^(int x)const{return value^x;} int operator&(int x)const{return value&x;}
              int operator|(int x)const{return value|x;} int operator<<(int x)const{return value<<x;}
              int operator>>(int x)const{return value>>x;} bool operator!=(int x)const{return value!=x;}
              bool operator<=(int x)const{return value<=x;} bool operator>=(int x)const{return value>=x;}
              bool operator>(int x)const{return value>x;} int operator+()const{return value+100;}
              int operator~()const{return ~value;} int operator&()const{return 77;}
            };
            int main(){Number n={12};printf("%d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d\\n",
              n-3,n*3,n/3,n%5,n^3,n&3,n|3,n<<1,n>>2,n!=12,n<=12,n>=11,n>11,+n,~n,&n);return 0;}
            """, "9 36 4 2 15 0 15 24 3 0 1 1 1 112 -13 77\n"),
        Arguments.of("temporary-and-postfix-decrement", """
            struct Number{int value;int operator()(){return value;}Number operator--(int){Number old=*this;--value;return old;}};
            Number make(){Number result={9};return result;}
            int main(){Number n={4};Number old=n--;printf("%d %d %d\\n",make()(),old.value,n.value);return 0;}
            """, "9 4 3\n"),
        Arguments.of("ignore-unrelated-member-and-using-adl", """
            namespace N{struct A{int value;};int operator+(const A& a,const A& b){return a.value+b.value;}}
            namespace Alias{using namespace N;}
            struct B{int operator+(int amount){return amount+100;}int sum(N::A a,N::A b){return a+b;}};
            int main(){using namespace Alias;N::A a={2};N::A b={5};B owner={};
              printf("%d %d\\n",owner.sum(a,b),a+b);return 0;}
            """, "7 7\n"),
        Arguments.of("prototype-unevaluated", """
            struct Value{int operator()(int)const;};
            int main(){Value value={};printf("%llu\\n",(unsigned long long)sizeof(value(2)));return 0;}
            """, "4\n"),
        Arguments.of("pointer-associated-namespace", """
            namespace Other {struct Addend{int amount;};}
            namespace N {struct Value{int number;};int operator+(Value* pointer,Other::Addend addend){return pointer->number+addend.amount;}}
            int main(){N::Value value={8};Other::Addend addend={3};printf("%d\\n",&value+addend);return 0;}
            """, "11\n")
    ); }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void supportedOperatorsAgreeWithGxxAndSourceDebug(String name,String program,String expected) throws Exception {
        var report = new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run(name,"#include <stdio.h>\n"+program,"");
        assertEquals(CppDifferentialHarness.Status.OK,report.outcomes().get(CppDifferentialHarness.Backend.GXX).status(),report::describe);
        assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"),report::describe);
        assertTrue(report.passed(),report::describe);
    }

    @ParameterizedTest @ValueSource(strings={
        "struct Value{int operator<(const Value&)const;};int main(){Value a={};Value b={};return a<b;} // bad",
        "class Value{int operator()(int){return 3;}};int main(){Value a={};return a(2);} // bad",
        "struct Value{int& operator[](int){return number;}int number;};int main(){const Value a={3};return a[0];} // bad",
        "struct Value{int operator+(int){return 1;}};int operator+(Value&,int){return 2;}int main(){Value a={};return a+1;} // bad",
        "struct Value{int operator<(int,int);};int main(){return 0;} // bad",
        "struct Value{Value operator++(long long);};int main(){return 0;} // bad",
        "int operator+(int,int){return 0;}int main(){return 0;} // bad",
        "struct Value{};int operator[](Value,int){return 0;}int main(){return 0;} // bad",
        "struct Value{};int operator()(Value,int){return 0;}int main(){return 0;} // bad",
        "struct Value{const int& operator[](int)const{return number;}int number;};int main(){Value value={3};value[0]=5;return 0;} // bad",
        "struct Value;int operator+(Value,int);int main(){Value* pointer=(Value*)0;return *pointer+1;} // bad"
    })
    void invalidDeclarationsAccessAndAmbiguityFailBeforeLowering(String text) throws Exception {
        Path file=temporary.resolve("invalid.cpp");Files.writeString(file,text);
        var result=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-pedantic-errors",
                "-fsyntax-only",file.toString()),temporary,"",Duration.ofSeconds(15),65_536);
        assertFalse(result.timedOut());assertFalse(result.outputExceeded());
        // Undefined called functions are diagnosed at link time by C++, so that case uses a full build.
        if(text.startsWith("struct Value{int operator<(const")) {
            result=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17",file.toString(),
                    "-o",temporary.resolve("invalid.exe").toString()),temporary,"",Duration.ofSeconds(15),65_536);
        }
        assertNotEquals(0,result.exitCode(),result::stderr);
        var api=new CompilerApi(new SourceFile("invalid.cpp",text),LanguageMode.CPP17_ALGORITHM);
        var parser=api.stages().stream().filter(Parser.class::isInstance).map(Parser.class::cast).findFirst().orElseThrow();
        var semantic=api.stages().stream().filter(SemanticAnalyzer.class::isInstance).map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        api.runThrough(semantic);assertTrue(parser.succeeded(),()->parser.errors().toString());
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d->d.code().equals("CPP004") || d.code().equals("SEM001")
                || text.startsWith("struct Value;") && d.message().contains("类型仍不完整")),()->semantic.errors().toString());
    }

    @Test void debugHistoryRetainsOperatorFunctionAndParameterNames() {
        String source="""
                #include <stdio.h>
                struct Value {int number;int& operator[](int index){return number;}};
                int main(){Value value={7};printf("%d\\n",value[0]);return 0;}
                """;
        var debug=new DebugApi(new SourceFile("operator-history.cpp",source),"",LanguageMode.CPP17_ALGORITHM);
        var history=new ArrayList<Debugger.Context>();history.add(debug.current());
        while(debug.canNext() && history.size()<1000)history.add(debug.next());
        assertFalse(debug.canNext());assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals("7\n",debug.current().runtime().stdout().replace("\r\n","\n"));
        assertTrue(history.stream().anyMatch(context->context.runtime().stack().stream()
                .anyMatch(frame->frame.function().equals("Value::operator[]") && frame.parameters().containsKey("index"))));
        for(int index=history.size()-2;index>=0;index--)assertSame(history.get(index),debug.previous());
        for(int index=1;index<history.size();index++)assertSame(history.get(index),debug.next());
    }

    @ParameterizedTest @ValueSource(strings={"Value& operator=(const Value&);", "Value& operator+=(int);",
            "bool operator&&(const Value&)const;", "int operator,(int);", "Value* operator->();", "int operator->*(int);"})
    void laterOperatorFamiliesKeepTheirExplicitExecutionGuard(String declaration) throws Exception {
        String text="struct Value{"+declaration+"};int main(){return 0;}";
        Path file=temporary.resolve("later.cpp");Files.writeString(file,text);
        var reference=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-pedantic-errors",
                "-fsyntax-only",file.toString()),temporary,"",Duration.ofSeconds(15),65_536);
        assertEquals(0,reference.exitCode(),reference::stderr);
        var api=new CompilerApi(new SourceFile("later.cpp",text),LanguageMode.CPP17_ALGORITHM);
        var semantic=api.stages().stream().filter(SemanticAnalyzer.class::isInstance).map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        api.runThrough(semantic);
        assertTrue(semantic.errors().stream().anyMatch(error->error.code().equals("CPP005")),()->semantic.errors().toString());
    }

    @Test void existingOptimizedBackendKeepsReferenceResultsAndFunctorCalls() throws Exception {
        String text="""
                #include <stdio.h>
                struct Value{int number;int& operator[](int){return number;}int operator()(int x)const{return number+x;}};
                int main(){Value value={2};value[0]=7;printf("%d %d\\n",value[0],value(3));return 0;}
                """;
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM,OptimizationLevel.OPTIMIZED).run("optimized-operators",text,"");
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(outcome->assertEquals("7 10\n",outcome.stdout().replace("\r\n","\n")));
    }
}
