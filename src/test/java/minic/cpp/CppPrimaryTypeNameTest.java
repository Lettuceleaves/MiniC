package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A failed declaration probe must not reinterpret a type name as an id-expression. */
@Timeout(30) @Execution(ExecutionMode.SAME_THREAD)
final class CppPrimaryTypeNameTest {
    @TempDir Path temporary;

    @ParameterizedTest @ValueSource(strings = {
            "struct Value {}; int main(){return Value;}",
            "typedef int Value; int consume(int); int main(){return consume(Value);}",
            "namespace A {typedef int T;} using A::T; int main(){return T;}",
            "struct Value{}; int ordinary(int (operator+)(Value));",
            "struct Value{}; int main(){return sizeof(int (operator+)(Value));}"})
    void aKnownTypeCannotStandAloneAsAnExpression(String text) throws Exception {
        assertFalse(reference(text));
        var parser = parse(text);
        assertFalse(parser.succeeded(), "known type name must not become a value expression");
        assertTrue(parser.errors().stream().anyMatch(error -> error.code().equals("PAR001")),
                () -> parser.errors().toString());
    }

    @ParameterizedTest @ValueSource(strings = {
            "struct Value{int n;}; int operator+(Value a){return a.n;} typedef int (*Function)(Value); "
                    + "int invoke(Function target=operator+){return target(Value{2});} "
                    + "int main(){Function target=Function(operator+);return invoke(target)-2;}",
            "struct Value{int n;}; int operator+(Value a){return a.n;} "
                    + "int main(){return operator+(Value{3})-3;}",
            "struct Value{}; int main(){int Value=4; return Value-4;}",
            "typedef int Value; int read(int Value){return Value;} int main(){return read(0);}",
            "struct Value{}; int main(){return sizeof(int (*)(Value))>0?0:1;}",
            "template<class T,class U> struct Pair{T first;U second;Pair(T a,U b):first(a),second(b){}}; "
                    + "int main(){Pair<int,int> value(int(1),int(2));return value.first+value.second-3;}"})
    void valuesOperatorFunctionNamesFunctionTypesAndConstructionRemainValid(String text) throws Exception {
        assertTrue(reference(text));
        var parser = parse(text);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        var compiler = new CompilerApi(new SourceFile("primary-type-name.cpp", text), LanguageMode.CPP17_ALGORITHM);
        var semantic = compiler.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        compiler.runThrough(semantic);
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
    }

    private boolean reference(String text) throws Exception {
        Path input = temporary.resolve("reference.cpp");
        Files.writeString(input, text);
        var result = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                "-std=c++17", "-pedantic-errors", "-fsyntax-only", input.toString()), temporary,
                "", Duration.ofSeconds(10), 65536);
        assertFalse(result.timedOut()); assertFalse(result.outputExceeded());
        return result.exitCode() == 0;
    }

    private static Parser parse(String text) {
        var lexer = new Lexer(new SourceFile("primary-type-name.cpp", text), LanguageMode.CPP17_ALGORITHM);
        var tokens = lexer.lex();
        assertTrue(lexer.succeeded(), () -> lexer.errors().toString());
        var parser = new Parser(tokens.tokens(), LanguageMode.CPP17_ALGORITHM, true);
        parser.parse(); return parser;
    }
}
