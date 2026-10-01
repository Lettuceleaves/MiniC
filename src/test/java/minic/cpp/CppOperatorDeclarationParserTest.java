package minic.cpp;

import minic.compiler.*;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.OperatorName;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement.ReturnStmt;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Declaration syntax only. Operator selection and execution belong to later binding slices. */
@Tag("cpp-frontend") @Timeout(30) @Execution(ExecutionMode.SAME_THREAD)
final class CppOperatorDeclarationParserTest {
    @TempDir Path temporary;

    static Stream<Arguments> members() { return Stream.of(
            Arguments.of("[]", "int& operator[](int index)", 1, false),
            Arguments.of("*", "int operator*() const", 0, true),
            Arguments.of("->", "int* operator->()", 0, false),
            Arguments.of("+", "Value operator+(const Value& right) const", 1, true),
            Arguments.of("-", "Value operator-(int amount) const", 1, true),
            Arguments.of("==", "bool operator==(const Value& right) const", 1, true),
            Arguments.of("<", "bool operator<(const Value& right) const", 1, true),
            Arguments.of("++", "Value& operator++()", 0, false),
            Arguments.of("++", "Value operator++(int)", 1, false),
            Arguments.of("()", "int operator()(int first,int second) const", 2, true),
            Arguments.of("=", "Value& operator=(const Value& other)", 1, false),
            Arguments.of("+=", "Value& operator+=(int amount)", 1, false),
            Arguments.of("--", "Value operator--(int)", 1, false),
            Arguments.of("<<", "Value operator<<(int amount)", 1, false)); }

    @ParameterizedTest(name = "member {1}") @MethodSource("members")
    void memberOperatorPrototypesKeepNamesParametersCvAndRanges(String operator, String declaration, int count, boolean constant) throws Exception {
        String text = "// 中文\nstruct Value {\n" + declaration + ";\nint ordinary();\n};";
        referenceAccepts(text);
        var parser = successful(text);
        var record = parser.result().program().structs().getFirst();
        var member = (MethodMember) record.cppInfo().members().getFirst();
        assertEquals("operator" + operator, member.method().name());
        assertEquals(operator, member.method().operatorName().kind().symbol());
        assertEquals(member.nameRange(), member.method().operatorName().range());
        assertEquals(count, member.method().parameters().size());
        assertEquals(constant, member.constQualified());
        var source = new SourceFile("operators.cpp", text);
        assertEquals("operator" + operator, source.text(member.nameRange()));
        assertEquals(declaration + ";", source.text(member.range()));
        assertNull(member.method().body());
        assertEquals("ordinary", ((MethodMember) record.cppInfo().members().get(1)).method().name());
    }

    static Stream<Arguments> freeOperators() { return Stream.of(
            Arguments.of("+", "Value operator+(Value left,Value right){return left;}", 2),
            Arguments.of("-", "Value operator-(Value value){return value;}", 1),
            Arguments.of("*", "int operator*(Value value){return 3;}", 1),
            Arguments.of("==", "bool operator==(Value left,Value right){return true;}", 2),
            Arguments.of("<", "bool operator<(Value left,Value right){return false;}", 2),
            Arguments.of("++", "Value& operator++(Value& value){return value;}", 1),
            Arguments.of("++", "Value operator++(Value& value,int){return value;}", 2)); }

    @ParameterizedTest(name = "free {1}") @MethodSource("freeOperators")
    void freeOperatorDefinitionsRetainBodiesAndPostfixDummyParameter(String operator, String declaration, int count) throws Exception {
        String text = "struct Value {int value;};" + declaration;
        referenceAccepts(text);
        var function = successful(text).result().program().functions().getFirst();
        assertEquals("operator" + operator, function.name());
        assertEquals(operator, function.operatorName().kind().symbol());
        assertEquals(count, function.parameters().size());
        assertNotNull(function.body());
        assertEquals(declaration, new SourceFile("operators.cpp", text).text(function.range()));
        if (count == 2 && operator.equals("++")) {
            assertEquals("int", new SourceFile("operators.cpp", text).text(function.parameters().getLast().range()));
        }
    }

    @Test void qualifiedDefinitionsAndDeferredBodiesRetainTheirExactOwnerAndSource() throws Exception {
        String text = """
                namespace N {
                struct Value {
                    int& operator [ ] (int index);
                    int operator()(int item) const;
                    Value operator++(int) { return *this; }
                    int operator*() const { return later; }
                    int later;
                    int data[2];
                };
                }
                int& N::Value::operator[](int index) { return data[index]; }
                int N::Value::operator()(int item) const { return item; }
                """;
        referenceAccepts(text);
        var program = successful(text).result().program();
        var source = new SourceFile("operators.cpp", text);
        var namespace = (NamespaceDecl) program.declarations().getFirst();
        var record = (StructDecl) namespace.declarations().getFirst();
        var first = (MethodMember) record.cppInfo().members().getFirst();
        assertEquals("operator[]", first.method().name());
        assertEquals(first.nameRange(), first.method().operatorName().range());
        assertEquals("operator [ ]", source.text(first.nameRange()));
        var postfix = ((MethodMember) record.cppInfo().members().get(2)).method();
        assertEquals(1, postfix.parameters().size());
        assertEquals(OperatorName.Kind.INCREMENT, postfix.operatorName().kind());
        assertInstanceOf(UnaryExpr.class, ((ReturnStmt) postfix.body().statements().getFirst()).expression());
        var dereference = ((MethodMember) record.cppInfo().members().get(3)).method();
        assertEquals("later", assertInstanceOf(NameExpr.class, ((ReturnStmt) dereference.body().statements().getFirst()).expression()).name());
        var definition = (OutOfLineMethodDecl) program.declarations().get(1);
        assertEquals(List.of("N", "Value", "operator[]"), definition.qualifiedName().segments());
        assertEquals("operator[]", source.text(definition.nameRange()));
        assertEquals(definition.nameRange(), definition.method().operatorName().range());
        assertEquals("N::Value::operator[]", source.text(definition.qualifiedName().range()));
        var call = (OutOfLineMethodDecl) program.declarations().get(2);
        assertTrue(call.constQualified());
        assertEquals("operator()", call.method().name());
    }

    @Test void alternativeTokensAreCanonicalWhileTheirWrittenRangesRemainUnchanged() throws Exception {
        String text = "struct Value {Value operator bitand(Value other); bool operator not_eq(Value other);};";
        referenceAccepts(text);
        var members = successful(text).result().program().structs().getFirst().cppInfo().members();
        var source = new SourceFile("operators.cpp", text);
        assertEquals("operator&", ((MethodMember) members.getFirst()).method().name());
        assertEquals("operator bitand", source.text(((MethodMember) members.getFirst()).nameRange()));
        assertEquals("operator!=", ((MethodMember) members.get(1)).method().name());
    }

    @Test void cppUnnamedOrdinaryFunctionParametersAreAcceptedButCBehaviorIsPreserved() throws Exception {
        String text = "int ordinary(int){return 2;}struct Value{int method(int){return 3;}};";
        referenceAccepts(text);
        assertTrue(successful(text).result().program().functions().getFirst().hasBody());
        var parser = parse("int ordinary(int){return 2;}", LanguageMode.C);
        assertFalse(parser.succeeded());
        var legacy = parse("int operator(int value){return value+1;}", LanguageMode.C);
        assertTrue(legacy.succeeded(), () -> legacy.errors().toString());
        assertEquals("operator", legacy.result().program().functions().getFirst().name());
    }

    @ParameterizedTest @ValueSource(strings = {".", ".*", "?:", "::", "(int)", "[int]"})
    void malformedOrNonOverloadableNamesHaveParserDiagnostics(String name) throws Exception {
        String text = "struct Value {int operator" + name + "(int x); int after();};";
        assertFalse(reference(text));
        var parser = parse(text, LanguageMode.CPP17_ALGORITHM);
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(error -> error.code().equals("PAR001")), () -> parser.errors().toString());
    }

    @ParameterizedTest @ValueSource(strings = {
            "unsigned long long operator \"\" _sample(unsigned long long value);",
            "void* operator new(unsigned long long size);",
            "void operator delete(void* pointer);"})
    void literalAndAllocationOperatorsStayExplicitlyUnsupported(String text) {
        var parser = parse(text, LanguageMode.CPP17_ALGORITHM);
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(error -> error.code().equals("CPP001")), () -> parser.errors().toString());
    }

    @ParameterizedTest @ValueSource(strings = {
            "struct Value {int operator*() const{return 3;}};int main(){return 0;}",
            "struct Value{int field;};int operator+(Value left,Value right){return 3;}int main(){return 0;}",
            "struct Value{int operator*() const;};int Value::operator*() const{return 3;}int main(){return 0;}"})
    void parsedOperatorsAreRejectedAtBindingUntilOperatorSemanticsExist(String text) {
        successful(text);
        var compiler = new CompilerApi(new SourceFile("guard.cpp", text), LanguageMode.CPP17_ALGORITHM);
        var semantic = compiler.stages().stream().filter(SemanticAnalyzer.class::isInstance).map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        compiler.runThrough(semantic);
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(error -> error.code().equals("CPP005")), () -> semantic.errors().toString());
        semantic.errors().stream().filter(error -> error.code().equals("CPP005")).forEach(error ->
                assertTrue(new SourceFile("guard.cpp", text).text(error.range()).startsWith("operator")));
    }

    @ParameterizedTest @EnumSource(OperatorName.Kind.class)
    void everyPunctuationOperatorHasItsDistinctIdentity(OperatorName.Kind kind) throws Exception {
        String signature = switch (kind) {
            case CALL -> "int operator()(int a,int b)";
            case SUBSCRIPT -> "int operator[](int index)";
            case MEMBER_ACCESS -> "Value* operator->()";
            case BIT_NOT, LOGICAL_NOT -> "int operator" + kind.symbol() + "()";
            case INCREMENT, DECREMENT -> "Value& operator" + kind.symbol() + "()";
            default -> "Value operator" + kind.symbol() + "(Value right)";
        };
        String text = "struct Value {" + signature + ";};";
        referenceAccepts(text);
        var member = (MethodMember) successful(text).result().program().structs().getFirst().cppInfo().members().getFirst();
        assertEquals(kind, member.method().operatorName().kind());
    }

    @ParameterizedTest @ValueSource(strings = {
            "typedef int operator+(Value);",
            "int ordinary(int (operator+)(Value));",
            "int main(){typedef int operator+(Value);return 0;}",
            "int main(){return sizeof(int (operator+)(Value));}"})
    void operatorNamesCannotDisappearIntoTypedefParameterOrTypeId(String declaration) throws Exception {
        String text = "struct Value{int value;};" + declaration;
        assertFalse(reference(text));
        var parser = parse(text, LanguageMode.CPP17_ALGORITHM);
        assertFalse(parser.succeeded(), "operator metadata must never be dropped by a non-function declaration path");
    }

    @Test void unnamedOrdinaryCppParametersStillExecuteWithoutChangingNamedArguments() throws Exception {
        String text = """
                #include <stdio.h>
                int ordinary(int,int named){return named+2;}
                struct Value{int method(int,int named){return named+3;}};
                int main(){Value value={};printf("%d %d\\n",ordinary(99,4),value.method(88,5));return 0;}
                """;
        var report = new CppDifferentialHarness(temporary, CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(), LanguageMode.CPP17_ALGORITHM).run("unnamed-cpp", text, "");
        assertTrue(report.passed(), report::describe);
        report.outcomes().values().forEach(outcome -> assertEquals("6 8\n", outcome.stdout().replace("\r\n", "\n")));
    }

    private void referenceAccepts(String text) throws Exception { assertTrue(reference(text)); }
    private boolean reference(String text) throws Exception {
        Path input = temporary.resolve("reference.cpp"); Files.writeString(input, text);
        var result = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()), "-std=c++17", "-pedantic-errors",
                "-fsyntax-only", input.toString()), temporary, "", Duration.ofSeconds(10), 65_536);
        assertFalse(result.timedOut()); assertFalse(result.outputExceeded()); return result.exitCode() == 0;
    }
    private static Parser successful(String text) {
        var parser = parse(text, LanguageMode.CPP17_ALGORITHM);
        assertTrue(parser.succeeded(), () -> parser.errors().toString()); return parser;
    }
    private static Parser parse(String text, LanguageMode mode) {
        var lexer = new Lexer(new SourceFile("operators.cpp", text), mode);
        var tokens = lexer.lex(); assertTrue(lexer.succeeded(), () -> lexer.errors().toString());
        var parser = new Parser(tokens.tokens(), mode, true); parser.parse(); return parser;
    }
}
