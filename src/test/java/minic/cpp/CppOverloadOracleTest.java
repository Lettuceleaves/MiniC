package minic.cpp;

import minic.compiler.semantic.cpp.CppOverloadResolver;
import minic.compiler.type.MiniType;
import minic.cpp.support.BoundedProcess;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import static minic.compiler.semantic.cpp.CppOverloadResolver.*;
import static minic.compiler.semantic.cpp.CppValueCategory.*;
import static minic.compiler.type.MiniType.*;
import static minic.compiler.type.MiniType.TypeQualifier.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent G++ oracle for a utility which is deliberately not yet connected to source lookup. */
@Tag("cpp-differential") @Timeout(30)
final class CppOverloadOracleTest {
    @TempDir Path temporary;
    private static MiniType c(MiniType type) { return qualified(type, Set.of(CONST)); }
    private static MiniType v(MiniType type) { return qualified(type, Set.of(VOLATILE)); }
    private static Argument l(MiniType type) { return new Argument(type, LVALUE, false); }
    private static Argument r(MiniType type) { return new Argument(type, PRVALUE, false); }
    private static final MiniType IP = INT.pointerTo(), CP = c(INT).pointerTo();
    private static final MiniType FN = function(INT, List.of(INT)), C = struct("::C");
    private record Parameter(String spelling, MiniType type) {}
    private record Probe(String name, String source, List<Candidate<Integer>> candidates,
                         List<Argument> arguments, Argument receiver, Status expected, int winner) {
        @Override public String toString() { return name; }
    }
    private static Parameter p(String spelling, MiniType type) { return new Parameter(spelling, type); }
    private static Probe probe(String name, String setup, String expression, Argument arg, Status status,
                               Parameter... parameters) {
        var candidates = new ArrayList<Candidate<Integer>>();
        var source = new StringBuilder();
        for (int i=0; i<parameters.length; i++) {
            source.append("int f(").append(parameters[i].spelling).append("){return ").append(i+1).append(";}\n");
            candidates.add(new Candidate<>(i+1, List.of(parameters[i].type), false));
        }
        source.append("int function(int x){return x;}\nint main(){").append(setup)
                .append("return f(").append(expression).append(");}");
        return new Probe(name, source.toString(), candidates, List.of(arg), null, status, 1);
    }
    private static Probe yes(String name, String setup, String expression, Argument arg, Parameter... params) {
        return probe(name, setup, expression, arg, Status.SELECTED, params);
    }
    private static Probe no(String name, String setup, String expression, Argument arg, Parameter... params) {
        return probe(name, setup, expression, arg, Status.NO_VIABLE, params);
    }
    private static Probe tie(String name, String setup, String expression, Argument arg, Parameter... params) {
        return probe(name, setup, expression, arg, Status.AMBIGUOUS, params);
    }
    static Stream<Probe> probes() {
        var ordinary = Stream.of(
            yes("char promotion", "char x=1;", "x", l(CHAR), p("int",INT), p("long",LONG)),
            yes("ushort promotion", "unsigned short x=1;", "x", l(UNSIGNED_SHORT), p("int",INT), p("unsigned",UNSIGNED_INT)),
            yes("float promotion", "float x=1;", "x", l(FLOAT), p("double",DOUBLE), p("int",INT)),
            tie("no width tiebreak", "", "1", r(INT), p("long",LONG), p("long long",LONG_LONG)),
            tie("no unsigned tiebreak", "", "1", r(INT), p("unsigned",UNSIGNED_INT), p("long",LONG)),
            yes("mutable reference", "int x=1;", "x", l(INT), p("int&",INT.referenceTo()), p("const int&",c(INT).referenceTo())),
            tie("value reference ambiguity", "int x=1;", "x", l(INT), p("int",INT), p("const int&",c(INT).referenceTo())),
            tie("prvalue reference ambiguity", "", "1", r(INT), p("int",INT), p("const int&",c(INT).referenceTo())),
            no("mutable ref rejects temporary", "", "1", r(INT), p("int&",INT.referenceTo())),
            no("mutable ref rejects promotion", "short x=1;", "x", l(SHORT), p("int&",INT.referenceTo())),
            yes("const reference promotion", "short x=1;", "x", l(SHORT), p("const int&",c(INT).referenceTo()), p("const long&",c(LONG).referenceTo())),
            no("volatile related cannot copy", "volatile int x=1;", "x", l(v(INT)), p("const int&",c(INT).referenceTo())),
            no("volatile const ref temporary", "", "1", r(INT), p("const volatile int&",c(v(INT)).referenceTo())),
            yes("volatile unrelated may convert", "volatile int x=1;", "x", l(v(INT)), p("const double&",c(DOUBLE).referenceTo())),
            yes("pointer qualifier subset", "int *x=nullptr;", "x", l(IP), p("const int*",CP), p("const volatile int*",c(v(INT)).pointerTo())),
            tie("pointer incomparable qualifiers", "int *x=nullptr;", "x", l(IP), p("const int*",CP), p("volatile int*",v(INT).pointerTo())),
            no("unsafe double pointer", "int **x=nullptr;", "x", l(IP.pointerTo()), p("const int**",CP.pointerTo())),
            yes("safe double pointer", "int **x=nullptr;", "x", l(IP.pointerTo()), p("const int*const*",c(CP).pointerTo())),
            no("unsafe triple pointer", "int ***x=nullptr;", "x", l(IP.pointerTo().pointerTo()), p("const int*const**",c(CP).pointerTo().pointerTo())),
            yes("safe triple pointer", "int ***x=nullptr;", "x", l(IP.pointerTo().pointerTo()), p("const int*const*const*",c(c(CP).pointerTo()).pointerTo())),
            yes("pointer void before bool", "int *x=nullptr;", "x", l(IP), p("void*",VOID.pointerTo()), p("bool",BOOL)),
            yes("pointer void qualifiers", "int *x=nullptr;", "x", l(IP), p("void*",VOID.pointerTo()), p("const void*",c(VOID).pointerTo())),
            no("void to object forbidden", "void *x=nullptr;", "x", l(VOID.pointerTo()), p("int*",IP)),
            no("pointee const removal forbidden", "const int *x=nullptr;", "x", l(CP), p("void*",VOID.pointerTo())),
            no("function pointer not object pointer", "", "function", l(FN), p("void*",VOID.pointerTo())),
            yes("function value decay", "", "function", l(FN), p("int(*)(int)",FN.pointerTo()), p("bool",BOOL)),
            yes("function parameter top cv adjusted", "", "function", l(function(INT,List.of(c(INT)))), p("int(*)(int)",FN.pointerTo()), p("bool",BOOL)),
            tie("function ref and pointer", "", "function", l(FN), p("int(&)(int)",FN.referenceTo()), p("int(*)(int)",FN.pointerTo())),
            yes("array value decay", "int x[3]={};", "x", l(INT.arrayOf(3)), p("int*",IP), p("bool",BOOL)),
            tie("array ref and pointer", "int x[3]={};", "x", l(INT.arrayOf(3)), p("int(&)[3]",INT.arrayOf(3).referenceTo()), p("int*",IP)),
            yes("array reference cv", "int x[3]={};", "x", l(INT.arrayOf(3)), p("int(&)[3]",INT.arrayOf(3).referenceTo()), p("const int(&)[3]",c(INT).arrayOf(3).referenceTo())),
            no("array bound reference mismatch", "int x[3]={};", "x", l(INT.arrayOf(3)), p("int(&)[4]",INT.arrayOf(4).referenceTo())),
            yes("pointer reference temporary", "int *x=nullptr;", "x", l(IP), p("const int*const&",c(CP).referenceTo())),
            no("pointer ref requires actual compatible lvalue", "int *x=nullptr;", "x", l(IP), p("const int*&",CP.referenceTo())),
            yes("pointer reference identity", "int *x=nullptr;", "x", l(IP), p("int*const&",c(IP).referenceTo()), p("const int*",CP)),
            yes("deep versus top cv reference", "int *x=nullptr;", "x", l(IP), p("int*const volatile&",c(v(IP)).referenceTo()), p("const int*const&",c(CP).referenceTo())),
            tie("cross pointer ref value cv", "int *x=nullptr;", "x", l(IP), p("const int*const&",c(CP).referenceTo()), p("volatile int*",v(INT).pointerTo())),
            tie("same qualification ref value", "int *x=nullptr;", "x", l(IP), p("const int*const&",c(CP).referenceTo()), p("const int*",CP)),
            yes("C++17 qualified pointer ref creates temporary", "int *x=nullptr; const int*const& reference=x; if((const void*)&x==(const void*)&reference)return 9;", "x", l(IP), p("const int*const&",c(CP).referenceTo())),
            tie("nullptr cv ambiguity", "", "nullptr", r(NULL), p("int*",IP), p("const int*",CP)),
            tie("nullptr reference value ambiguity", "", "nullptr", r(NULL), p("int*const&",c(IP).referenceTo()), p("const int*",CP)),
            tie("nullptr references ambiguity", "", "nullptr", r(NULL), p("int*const&",c(IP).referenceTo()), p("const int*const&",c(CP).referenceTo())),
            tie("zero cv ambiguity", "", "0", new Argument(INT,PRVALUE,true), p("int*",IP), p("const int*",CP)),
            yes("zero integer identity", "", "0", new Argument(INT,PRVALUE,true), p("int",INT), p("int*",IP)),
            no("nullptr bool forbidden", "", "nullptr", r(NULL), p("bool",BOOL)),
            no("runtime zero not constant", "int x=0;", "x", l(INT), p("int*",IP)),
            yes("cv array typedef", "typedef int A[3];const A x={};", "x", l(c(INT.arrayOf(3))), p("const int*",CP)),
            yes("pointer to cv array", "int (*x)[3]=nullptr;", "x", l(INT.arrayOf(3).pointerTo()), p("const int(*)[3]",c(INT).arrayOf(3).pointerTo())),
            yes("pointer to nested array", "int (*x)[2][3]=nullptr;", "x", l(INT.arrayOf(3).arrayOf(2).pointerTo()), p("const int(*)[2][3]",c(INT).arrayOf(3).arrayOf(2).pointerTo()))
        );
        var special = Stream.of(
            new Probe("no rank sums", "int f(int,int,long){return 1;} int f(long,long,int){return 2;} int main(){return f(1,1,1);}",
                List.of(new Candidate<>(1,List.of(INT,INT,LONG),false),new Candidate<>(2,List.of(LONG,LONG,INT),false)),
                List.of(r(INT),r(INT),r(INT)),null,Status.AMBIGUOUS,0),
            new Probe("ellipsis worse", "int f(double){return 1;} int f(...){return 2;} int main(){return f(1);}",
                List.of(new Candidate<>(1,List.of(DOUBLE),false),new Candidate<>(2,List.of(),true)),List.of(r(INT)),null,Status.SELECTED,1),
            new Probe("unused ellipsis not worse", "int f(){return 1;} int f(...){return 2;} int main(){return f();}",
                List.of(new Candidate<>(1,List.of(),false),new Candidate<>(2,List.of(),true)),List.of(),null,Status.AMBIGUOUS,0),
            new Probe("receiver cv", "struct C{int f(){return 1;}int f()const{return 2;}};int main(){C x;return x.f();}",
                List.of(new Candidate<>(1,List.of(),false,C),new Candidate<>(2,List.of(),false,c(C))),List.of(),l(C),Status.SELECTED,1),
            new Probe("prvalue receiver", "struct C{int f(){return 1;}int f()const{return 2;}};int main(){return C{}.f();}",
                List.of(new Candidate<>(1,List.of(),false,C),new Candidate<>(2,List.of(),false,c(C))),List.of(),r(C),Status.SELECTED,1),
            new Probe("const receiver", "struct C{int f(){return 1;}int f()const{return 2;}};int main(){const C x={};return x.f();}",
                List.of(new Candidate<>(1,List.of(),false,C),new Candidate<>(2,List.of(),false,c(C))),List.of(),l(c(C)),Status.SELECTED,2),
            new Probe("receiver versus parameter", "struct C{int f(long){return 1;}int f(int)const{return 2;}};int main(){C x;return x.f(1);}",
                List.of(new Candidate<>(1,List.of(LONG),false,C),new Candidate<>(2,List.of(INT),false,c(C))),List.of(r(INT)),l(C),Status.AMBIGUOUS,0)
        );
        return Stream.concat(ordinary, special);
    }

    @ParameterizedTest(name="{0}") @MethodSource("probes")
    void agreesWithIndependentCpp17Compiler(Probe probe) throws Exception {
        var source = temporary.resolve("oracle.cpp");
        var executable = temporary.resolve("oracle.exe");
        Files.writeString(source, probe.source);
        var compiler = referenceCompiler();
        var compilation = BoundedProcess.run(List.of(compiler,"-std=c++17","-pedantic-errors","-O0",
                source.toString(),"-o",executable.toString()),temporary,"",Duration.ofSeconds(20),128*1024);
        assertFalse(compilation.timedOut(), compilation.stderr());
        assertFalse(compilation.outputExceeded(), compilation.stderr());
        if (probe.expected == Status.SELECTED) {
            assertEquals(0, compilation.exitCode(), compilation.stderr()+"\n"+probe.source);
            var execution = BoundedProcess.run(List.of(executable.toString()),temporary,"",Duration.ofSeconds(5),4096);
            assertFalse(execution.timedOut()); assertFalse(execution.outputExceeded());
            assertEquals(probe.winner,execution.exitCode(),probe.source);
        } else {
            assertNotEquals(0,compilation.exitCode(),probe.source);
            assertTrue(compilation.stderr().contains("error:"),compilation.stderr());
            if (probe.expected == Status.AMBIGUOUS) assertTrue(compilation.stderr().contains("ambiguous"),compilation.stderr());
        }
        var result = CppOverloadResolver.resolve(probe.candidates,probe.arguments,probe.receiver);
        assertEquals(probe.expected,result.status(),probe.source);
        if (probe.expected == Status.SELECTED) assertEquals(probe.winner,result.winner().identity());
    }
    private static String referenceCompiler() {
        for (String name : List.of("MINIC_CXX","GXX")) {
            var configured = System.getenv(name);
            if (configured != null && !configured.isBlank()) return configured;
        }
        return Files.isRegularFile(Path.of("C:/mingw64/bin/g++.exe")) ? "C:/mingw64/bin/g++.exe" : "g++";
    }
}
