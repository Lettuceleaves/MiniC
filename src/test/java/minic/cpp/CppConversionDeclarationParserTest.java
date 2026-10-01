package minic.cpp;

import minic.compiler.*;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement.ReturnStmt;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.*;
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

/** Conversion declarations retain source metadata and bind to methods with explicit receiver ABI. */
@Tag("cpp-frontend") @Timeout(30) @Execution(ExecutionMode.SAME_THREAD)
final class CppConversionDeclarationParserTest {
    @TempDir Path temporary;

    @ParameterizedTest @ValueSource(strings = {
            "explicit Value();", "explicit Value(int);", "explicit Value(int, int);",
            "explicit Value(int):value(7) {}", "Value(int):value(7) {}",
            "operator bool() const;", "explicit operator bool() const {return value != 0;}",
            "operator int&() {return value;}", "operator const int*() const {return &value;}",
            "operator unsigned long long() const;", "operator void();",
            "operator Value() const;", "operator bool(void) const;"
    })
    void validExplicitConstructorsAndConversionsParseWithoutChangingOrdinaryMembers(String declaration) throws Exception {
        String text = "struct Value {" + declaration + " int ordinary(){return value;} int value;};";
        referenceAccepts(text);
        var record = successful(text).result().program().structs().getFirst();
        assertEquals(3, record.cppInfo().members().size());
        assertEquals("ordinary", ((MethodMember) record.cppInfo().members().get(1)).method().name());
        var source = new SourceFile("conversions.cpp", text);
        var first = record.cppInfo().members().getFirst();
        assertEquals(declaration, source.text(first.range()));
        if (first instanceof ConstructorMember constructor) {
            assertEquals(declaration.startsWith("explicit"), constructor.explicitSpecifier());
            assertEquals("Value", source.text(constructor.nameRange()));
            if (declaration.contains("int")) assertEquals(MiniType.INT, constructor.parameters().getFirst().type());
        } else {
            var method = assertInstanceOf(MethodMember.class, first);
            var name = method.method().conversionName();
            assertNotNull(name);
            assertNull(method.method().operatorName());
            assertEquals(method.method().returnType(), name.targetType());
            assertEquals(declaration.startsWith("explicit"), name.explicitSpecifier());
            assertEquals(declaration.contains(") const"), method.constQualified());
            assertEquals(method.nameRange(), name.range());
            assertTrue(source.text(name.range()).startsWith("operator "));
            assertFalse(source.text(name.range()).contains("("));
            assertTrue(method.method().parameters().isEmpty());
        }
    }

    @Test void qualifiedConversionDefinitionAndUnnamedConstructorDefinitionParse() throws Exception {
        String text = """
                namespace N { struct Value { explicit Value(int); operator bool() const; int value; }; }
                N::Value::Value(int):value(3) {}
                N::Value::operator bool() const { return value != 0; }
                """;
        referenceAccepts(text);
        var program = successful(text).result().program();
        assertInstanceOf(OutOfLineConstructorDecl.class, program.declarations().get(1));
        var conversion = assertInstanceOf(OutOfLineMethodDecl.class, program.declarations().get(2));
        assertTrue(conversion.constQualified());
        assertNotNull(conversion.method().body());
        assertEquals(List.of("N", "Value", "operator bool"), conversion.qualifiedName().segments());
        assertEquals(MiniType.BOOL, conversion.method().conversionName().targetType());
        assertFalse(conversion.method().conversionName().explicitSpecifier());
        var source = new SourceFile("conversions.cpp", text);
        assertEquals("operator bool", source.text(conversion.nameRange()));
        assertEquals("N::Value::operator bool", source.text(conversion.qualifiedName().range()));
        assertEquals("N::Value::operator bool() const { return value != 0; }", source.text(conversion.range()));
        assertInstanceOf(BinaryExpr.class, ((ReturnStmt) conversion.method().body().statements().getFirst()).expression());
    }

    @Test void aliasesPointersAndReferencesStopBeforeTheConversionParameterList() throws Exception {
        String text = """
                typedef int Fn(int); typedef int Row[2];
                namespace N { typedef unsigned long long Count; }
                struct Value { operator N::Count(); operator Fn*(); operator Row&(); operator int* const*(); };
                """;
        referenceAccepts(text);
        var members = successful(text).result().program().structs().getFirst().cppInfo().members();
        assertEquals(4, members.size());
        assertEquals(MiniType.UNSIGNED_LONG_LONG, ((MethodMember) members.get(0)).method().returnType());
        assertTrue(((MethodMember) members.get(1)).method().returnType().pointee().isFunction());
        assertEquals(MiniType.INT.arrayOf(2).referenceTo(), ((MethodMember) members.get(2)).method().returnType());
        assertEquals(MiniType.qualified(MiniType.INT.pointerTo(), java.util.Set.of(MiniType.TypeQualifier.CONST)).pointerTo(),
                ((MethodMember) members.get(3)).method().returnType());
    }

    @Test void qualifiedPointerReferenceTargetsUseTheOwningScopeAndRestoreIt() throws Exception {
        String text = """
                typedef long long Number;
                namespace N { typedef int Number; struct Value { operator Number*(); operator const Value&() const; int value; }; }
                N::Value::operator Number*() { return &value; }
                N::Value::operator const Value&() const { return *this; }
                Number after;
                """;
        referenceAccepts(text);
        var program = successful(text).result().program();
        var pointer = (OutOfLineMethodDecl) program.declarations().get(2);
        assertEquals(MiniType.INT.pointerTo(), pointer.method().returnType());
        var reference = (OutOfLineMethodDecl) program.declarations().get(3);
        assertTrue(reference.method().returnType().isReference());
        assertTrue(reference.method().returnType().referent().isConstQualified());
        assertEquals(MiniType.LONG_LONG, program.globals().getFirst().type());
    }

    @Test void unnamedConstructorDefinitionsStillExecuteWithNamedParameterPositionsIntact() throws Exception {
        String text = """
                #include <stdio.h>
                struct Value { Value(int, int named):value(named+2) {} int value; };
                struct Other { Other(int, int named); int value; };
                Other::Other(int, int named):value(named+3) {}
                int main(){Value a(99,4);Other b(88,5);printf("%d %d\\n",a.value,b.value);return 0;}
                """;
        var report = new CppDifferentialHarness(temporary, CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(), LanguageMode.CPP17_ALGORITHM).run("unnamed-constructors", text, "");
        assertTrue(report.passed(), report::describe);
        report.outcomes().values().forEach(outcome -> assertEquals("6 8\n", outcome.stdout().replace("\r\n", "\n")));
    }

    @ParameterizedTest @ValueSource(strings = {
            "static operator bool();", "operator bool(int);", "operator bool(...);",
            "operator bool() const const;", "int operator bool();", "explicit int value;",
            "explicit int ordinary();", "explicit(false) Value();",
            "static Value();", "Value() const;", "Value() volatile;", "Value() &;",
            "operator int[2]();", "operator int& const();", "operator void&();"
    })
    void invalidConversionOrExplicitDeclarationsAreParserErrorsAndRecoveryKeepsTheNextMember(String declaration) throws Exception {
        String text = "struct Value {" + declaration + " int retained;}; int after(){return 0;}";
        assertFalse(reference(text));
        var parser = parse(text, LanguageMode.CPP17_ALGORITHM);
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("PAR001")), () -> parser.errors().toString());
        assertTrue(parser.result().program().structs().getFirst().fields().stream().anyMatch(f -> f.name().equals("retained")));
        assertTrue(parser.result().program().functions().stream().anyMatch(f -> f.name().equals("after")));
    }

    @ParameterizedTest @ValueSource(strings = {"typedef int Target[2];", "typedef int Target(int);"})
    void aliasesCannotHideForbiddenConversionTargets(String alias) throws Exception {
        String text = alias + "struct Value {operator Target(); int retained;};";
        assertFalse(reference(text));
        var parser = parse(text, LanguageMode.CPP17_ALGORITHM);
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("PAR001")));
    }

    @Test void conversionTypeIdCannotDefineAClass() throws Exception {
        String text = "struct Value {operator struct Inner {int data;}(); int retained;}; int after(){return 0;}";
        assertFalse(reference(text));
        var parser = parse(text, LanguageMode.CPP17_ALGORITHM);
        assertFalse(parser.succeeded(), "conversion-type-id permits type-specifiers, not a class definition");
        assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("PAR001")), () -> parser.errors().toString());
        assertTrue(parser.result().program().functions().stream().anyMatch(f -> f.name().equals("after")));
    }

    @ParameterizedTest @ValueSource(strings = {
            "struct Value{explicit Value();};explicit Value::Value(){}",
            "struct Value{operator bool() const;};explicit Value::operator bool() const{return true;}",
            "struct Value{operator bool();};Value::operator bool();"})
    void explicitCannotBeRepeatedOnOutOfLineDefinitionsAndDefinitionsRequireABody(String text) throws Exception {
        assertFalse(reference(text));
        var parser = parse(text, LanguageMode.CPP17_ALGORITHM);
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("PAR001")), () -> parser.errors().toString());
    }

    @ParameterizedTest @ValueSource(strings = {
            "operator bool() volatile;", "operator bool() const volatile;", "operator bool() &;"})
    void validDeferredGrammarRemainsExplicitlyUnsupported(String declaration) throws Exception {
        String text = "struct Value{" + declaration + "int retained;};";
        referenceAccepts(text);
        var parser = parse(text, LanguageMode.CPP17_ALGORITHM);
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("CPP001")), () -> parser.errors().toString());
    }

    @Test void duplicateExplicitSpecifierRemainsReservedAndRecoveryIsBounded() throws Exception {
        String text = "struct Value{explicit explicit Value();int retained;};int after(){return 0;}";
        assertFalse(reference(text));
        var parser = parse(text, LanguageMode.CPP17_ALGORITHM);
        assertFalse(parser.succeeded());
        var source = new SourceFile("conversions.cpp", text);
        assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("CPP001")
                && source.text(d.range()).equals("explicit")), () -> parser.errors().toString());
        assertTrue(parser.result().program().structs().getFirst().fields().stream().anyMatch(f -> f.name().equals("retained")));
        assertTrue(parser.result().program().functions().stream().anyMatch(f -> f.name().equals("after")));
    }

    @ParameterizedTest @ValueSource(strings = {"operator auto(){return 1;}", "operator int&&();"})
    void deducedAndRvalueReferenceConversionsRetainTargetsAndBind(String declaration) throws Exception {
        String text = "struct Value{" + declaration + "int retained;};int main(){return 0;}";
        referenceAccepts(text);
        var parser = successful(text);
        var method = ((MethodMember) parser.result().program().structs().getFirst().cppInfo().members().getFirst()).method();
        assertNotNull(method.conversionName());
        if (declaration.contains("auto")) assertTrue(method.returnType().containsAuto());
        else assertTrue(method.returnType().isRvalueReference());
        var semantic = new SemanticAnalyzer(parser.result().program()); semantic.analyze();
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
    }

    @ParameterizedTest @ValueSource(strings = {
            "struct Value{operator bool() const{return true;}};",
            "struct Value{explicit operator bool() const;};Value::operator bool() const{return true;}",
            "struct Value{operator int*();};int value;Value::operator int*(){return &value;}"})
    void parsedConversionMethodsBindWithTheirDeclaredSourceSemantics(String declaration) {
        String text = declaration + "int main(){return 0;}";
        successful(text);
        var compiler = new CompilerApi(new SourceFile("guard.cpp", text), LanguageMode.CPP17_ALGORITHM);
        var semantic = compiler.stages().stream().filter(SemanticAnalyzer.class::isInstance).map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        compiler.runThrough(semantic);
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        assertTrue(semantic.semanticResult().program().functions().stream().anyMatch(function -> function.parameters().size() == 1),
                "conversion methods retain an implicit receiver in their lowered signature");
    }

    @Test void cIdentifiersExplicitAndOperatorStillBehaveAsOrdinaryNames() {
        var parser = parse("int explicit(int operator){return operator;}int main(){return explicit(0);}", LanguageMode.C);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        assertNull(parser.result().program().functions().getFirst().conversionName());
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
        var lexer = new Lexer(new SourceFile("conversions.cpp", text), mode);
        var tokens = lexer.lex(); assertTrue(lexer.succeeded(), () -> lexer.errors().toString());
        var parser = new Parser(tokens.tokens(), mode, true); parser.parse(); return parser;
    }
}
