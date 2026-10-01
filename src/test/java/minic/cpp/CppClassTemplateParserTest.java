package minic.cpp;

import minic.compiler.*;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Source template syntax only. Instantiation and executable members are a separate slice. */
@Tag("cpp-frontend") @Timeout(30) @Execution(ExecutionMode.SAME_THREAD)
final class CppClassTemplateParserTest {
    @TempDir Path temporary;

    @ParameterizedTest @ValueSource(strings = {
        "template<typename T> struct Box {T value;T get()const{return value;}void set(T next){value=next;}}; Box<int> one;Box<double> two;",
        "template<class T> class Box {public:T value;T* pointer;const T& get()const{return value;}};Box<int> one;",
        "template<typename T> struct Box {Box* next;Box<T>* other;T value;};Box<int>* head;",
        "namespace N {template<class T> struct Box {T value;};} N::Box<int> first;::N::Box<double> second;",
        "template<class T> struct Box {T value;};typedef Box<int> IntBox;IntBox value;",
        "template<class T> struct Box {T value;};int use(){Box<int> value={3};return sizeof(Box<int>)+value.value;}",
        "template<class T> struct Box {T values[2];T* address(){return values;}};Box<unsigned long long> value;",
        "typedef int Number;template<class T>struct Box{Number number;T value;};namespace N{typedef double Number;Box<int> value;}",
        "template<class T>struct Box{T value;};Box<int*> pointer;Box<const int> constant={3};",
        "template<class T>struct Box{T value;Box(T next):value(next){} ~Box(){} };Box<int>* pointer;"
    })
    void sourceTemplatesParseAndRetainTheirRanges(String text) throws Exception {
        assertTrue(reference(text), "C++17 oracle rejected a positive fixture");
        Parser parser = parse(text, LanguageMode.CPP17_ALGORITHM);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        assertTrue(parser.result().program().structs().isEmpty(), "generic primary must not enter core record indexes");
        assertNotNull(AstChildren.firstCppSyntax(parser.result().program()));
        assertFalse(parser.result().program().declarations().isEmpty());
    }

    @Test void templateParameterScopeEndsBeforeFollowingDeclarations() {
        Parser parser = parse("typedef double T;template<class T>struct Box{T value;};T after;", LanguageMode.CPP17_ALGORITHM);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        assertEquals(minic.compiler.type.MiniType.DOUBLE, parser.result().program().globals().getFirst().type());
    }

    @ParameterizedTest @ValueSource(strings={"int main(){return 0;}","Box<int>* pointer;int main(){return 0;}","int main(){Box<int> value={3};return value.value;}"})
    void classTemplateSourceIsConsumedBeforeTheCoreStages(String suffix) {
        var api = new CompilerApi(new SourceFile("template.cpp", "template<class T>struct Box{T value;};"+suffix), LanguageMode.CPP17_ALGORITHM);
        var semantic = api.stages().stream().filter(SemanticAnalyzer.class::isInstance).map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        api.runThrough(semantic);
        assertTrue(api.stages().stream().filter(Parser.class::isInstance).allMatch(s -> s.succeeded()));
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        assertNull(AstChildren.firstCppSyntax(semantic.result().program()));
    }

    @ParameterizedTest @ValueSource(strings = {
        "template<int N>struct Box{};", "template<class... T>struct Box{};",
        "template<class T>T identity(T value){return value;}",
        "template<class T>struct Box{union{T value;};};",
        "template<class T>struct Box{typename T::type value;};"
    })
    void unfinishedTemplateFormsHaveExplicitUnsupportedDiagnostics(String text) throws Exception {
        assertTrue(reference(text));
        Parser parser = parse(text, LanguageMode.CPP17_ALGORITHM);
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("CPP001")), () -> parser.errors().toString());
    }

    @ParameterizedTest @ValueSource(strings = {
        "template<class T>struct Box{T value;};Box value;",
        "template<class T>struct Box{T value;};Box<> value;",
        "template<class T>struct Box{T value;};Box<1> value;",
        "template<class T>struct Box{T value;};T escaped;"
    })
    void invalidTemplateUsesHaveSourceDiagnostics(String text) throws Exception {
        assertFalse(reference(text));
        Parser parser = parse(text, LanguageMode.CPP17_ALGORITHM);
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("PAR001")), () -> parser.errors().toString());
    }

    @Test void cTemplateAndTypenameIdentifiersAreUnchanged() {
        var parser = parse("int template(int typename){return typename;}int main(){return template(0);}", LanguageMode.C);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
    }

    @ParameterizedTest @ValueSource(strings = {
        "template<class T>struct Box{T value;};int main(){return Box<int>{3}.value;}",
        "template<class T>struct Box{T value;Box(T n):value(n){}};int main(){return Box<int>(3).value;}",
        "template<class T>struct Box{T value;};struct Box<int>* pointer;",
        "template<class T>struct Box;template<typename U>struct Box{U value;};Box<int> value;"
    })
    void templateIdsWorkInConstructionAndElaboratedTypeContexts(String text) throws Exception {
        assertTrue(reference(text));
        var parser=parse(text,LanguageMode.CPP17_ALGORITHM);
        assertTrue(parser.succeeded(),()->parser.errors().toString());
    }

    @Test void templatesInNestedNamespacesCanHideAnOuterOrdinaryClass() throws Exception {
        String text="struct Box; namespace N {template<class T>struct Box{T value;};} N::Box<int>* one;";
        assertTrue(reference(text));
        var parser=parse(text,LanguageMode.CPP17_ALGORITHM);
        assertTrue(parser.succeeded(),()->parser.errors().toString());
    }

    @Test void classNameCannotRedeclareItsTemplateParameter() throws Exception {
        String text="template<class Box>struct Box{};";
        assertFalse(reference(text));
        assertFalse(parse(text,LanguageMode.CPP17_ALGORITHM).succeeded());
    }

    @Test void nondependentAliasesRemainDefinitionTypesAndFailedTemplateParsingRestoresScope() {
        var parser=parse("typedef int Number;template<class T>struct Box{Number number;T value;};namespace N{typedef double Number;Box<int>* one;}",LanguageMode.CPP17_ALGORITHM);
        assertTrue(parser.succeeded(),()->parser.errors().toString());
        var template=(minic.compiler.parser.node.ClassTemplateDecl)parser.result().program().declarations().get(1);
        assertEquals(minic.compiler.type.MiniType.INT,template.record().fields().getFirst().type());
        var broken=parse("typedef double T;template<class T>struct Broken{typename T::value field;};T retained;",LanguageMode.CPP17_ALGORITHM);
        assertFalse(broken.succeeded());
        assertEquals(minic.compiler.type.MiniType.DOUBLE,broken.result().program().globals().getFirst().type());
    }

    @ParameterizedTest @ValueSource(strings = {
        "template<class T>struct Box{T value;};template<>struct Box<int>{int value;};",
        "template<class T>struct Box{T value;};template<class T>struct Box<T*>{T* value;};",
        "template<class T>struct Box{T get();};template<class T>T Box<T>::get(){return T();}"
    })
    void specializationAndOutOfLineTemplatesRemainExplicitBoundaries(String text) throws Exception {
        assertTrue(reference(text));
        var parser=parse(text,LanguageMode.CPP17_ALGORITHM);
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(d->d.code().equals("CPP001")),()->parser.errors().toString());
    }

    @ParameterizedTest @ValueSource(strings = {
        "struct Box;template<class T>struct Box{T value;};",
        "template<class T>struct Box{T value;};struct Box;",
        "template<class T>struct Box{int T;};",
        "template<class T>struct Box{int get(int T){return T;}};",
        "template<class T>struct Box{int get(){int T=3;return T;}};"
    })
    void ordinaryClassConflictsAndParameterShadowingAreRejected(String text) throws Exception {
        assertFalse(reference(text));
        var parser=parse(text,LanguageMode.CPP17_ALGORITHM);
        assertFalse(parser.succeeded());
    }

    @Test void templateAstRetainsParameterAndInstanceIdentitiesAndOriginalMemberRanges() {
        String text="template<class T>struct Box{T value;Box* next;T get()const{return value;}};Box<int> one;Box<double> two;";
        var parser=parse(text,LanguageMode.CPP17_ALGORITHM);
        assertTrue(parser.succeeded(),()->parser.errors().toString());
        var program=parser.result().program();
        var template=assertInstanceOf(minic.compiler.parser.node.ClassTemplateDecl.class,program.declarations().getFirst());
        var parameter=template.parameters().getFirst();
        assertEquals("T",parameter.name());assertEquals("::Box",parameter.type().owner());assertEquals(0,parameter.type().index());
        assertEquals(parameter.type(),template.record().fields().getFirst().type());
        var self=assertInstanceOf(minic.compiler.type.MiniType.TemplateIdType.class,template.record().fields().get(1).type().pointee());
        assertEquals("::Box",self.templateName());assertEquals(List.of(parameter.type()),self.arguments());
        var method=((MethodMember)template.record().cppInfo().members().get(2)).method();
        assertEquals(parameter.type(),method.returnType());
        var source=new SourceFile("template.cpp",text);
        assertEquals("T",source.text(parameter.range()));
        assertEquals("T get()const{return value;}",source.text(method.range()));
        assertEquals("template<class T>struct Box{T value;Box* next;T get()const{return value;}};",source.text(template.range()));
        assertNotEquals(program.globals().get(0).type(),program.globals().get(1).type());
        assertSame(template.record(),AstChildren.of(template).getFirst());
    }

    private boolean reference(String text) throws Exception {
        Path file = temporary.resolve("reference.cpp");Files.writeString(file,text);
        var result=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-pedantic-errors","-fsyntax-only",file.toString()),temporary,"",Duration.ofSeconds(10),65_536);
        assertFalse(result.timedOut());assertFalse(result.outputExceeded());return result.exitCode()==0;
    }
    private static Parser parse(String text,LanguageMode mode) {
        var lexer=new Lexer(new SourceFile("template.cpp",text),mode);var tokens=lexer.lex();assertTrue(lexer.succeeded());
        var parser=new Parser(tokens.tokens(),mode,true);parser.parse();return parser;
    }
}
