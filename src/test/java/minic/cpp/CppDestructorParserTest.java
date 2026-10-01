package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.lexer.Lexer;
import minic.compiler.ir.IrLowerer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** C++17 destruction syntax is retained until lifetime binding is implemented. */
@Timeout(10)
class CppDestructorParserTest {
    @Test void destructorMetadataKeepsAccessAndFieldOrderWithoutAnOrdinaryMethodProjection() {
        var parser = successful("class Box { int first; protected: ~Box(); public: int last; };");
        var record = parser.result().program().structs().getFirst();
        assertEquals(List.of("FieldMember", "AccessLabel", "DestructorMember", "AccessLabel", "FieldMember"),
                record.cppInfo().members().stream().map(n -> n.getClass().getSimpleName()).toList());
        assertEquals(RecordKey.CLASS, record.cppInfo().key());
        var destructor = (DestructorMember) record.cppInfo().members().get(2);
        assertEquals("Box", destructor.name()); assertNull(destructor.body());
        assertEquals(List.of("first", "last"), record.fields().stream().map(StructField::name).toList());
        assertSame(record.fields().getLast(), ((FieldMember) record.cppInfo().members().getLast()).field());
        assertTrue(parser.result().program().functions().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> record.cppInfo().members().clear());
    }

    @Test void utf8NamesBodyAndQualifiedNameRangesRetainExactSource() {
        String text = "// 中文前缀\nnamespace N { struct Box { ~Box(); int value; }; }\n::N::Box::~Box(){ this->value = 1; return; }";
        var program = successful(text).result().program();
        var out = (OutOfLineDestructorDecl) program.declarations().getLast();
        var source = new SourceFile("destructor.cpp", text);
        assertTrue(out.qualifiedName().global());
        assertEquals(List.of("N", "Box", "~Box"), out.qualifiedName().segments());
        assertEquals("::N::Box::~Box", source.text(out.qualifiedName().range()));
        assertEquals("~Box", source.text(out.nameRange()));
        assertEquals("~Box", source.text(out.destructor().nameRange()));
        assertEquals("::N::Box::~Box(){ this->value = 1; return; }", source.text(out.range()));
        assertEquals("{ this->value = 1; return; }", source.text(out.destructor().body().range()));
        assertSame(out.destructor(), AstChildren.of(out).getFirst());
        assertSame(out.destructor().body(), AstChildren.of(out.destructor()).getFirst());
        assertTrue(nodes(out).stream().anyMatch(ThisExpr.class::isInstance));
        assertSame(out, AstChildren.firstCppSyntax(out));
        assertSame(out.destructor(), AstChildren.firstCppSyntax(out.destructor()));
    }

    @Test void destructorBodiesSeeAllFieldsAndKeepEachLocalScopeSeparate() {
        var program = successful("typedef int Later; struct Box { ~Box(){ typedef char Local; value=sizeof(Later); } "
                + "int method(){ return sizeof(Later); } int value; char Later; }; Later after;").result().program();
        var destructor = (DestructorMember) program.structs().getFirst().cppInfo().members().getFirst();
        var query = nodes(destructor).stream().filter(SizeofExpr.class::isInstance).map(SizeofExpr.class::cast).findFirst().orElseThrow();
        assertNull(query.queriedType());
        assertEquals("Later", ((NameExpr) ungroup(query.expression())).name());
        assertEquals(MiniType.INT, program.globals().getFirst().type());
    }

    @Test void destructorLocalTypeDoesNotLeakIntoDeferredMethodsOrOuterScope() {
        var program = successful("typedef int Local; struct Box { ~Box(){ typedef char Local; Local value; } "
                + "int method(){ Local local; return sizeof(local); } }; Local after;").result().program();
        var method = (MethodMember) program.structs().getFirst().cppInfo().members().get(1);
        assertEquals(MiniType.INT, ((VarDeclStmt) method.method().body().statements().getFirst()).type());
        assertEquals(MiniType.INT, program.globals().getFirst().type());
    }

    @Test void outOfLineBodyUsesOwnerNamespaceThenRestoresTheDefinitionScope() {
        var program = successful("typedef long long Size; namespace N { typedef char Size; struct Box { ~Box(); }; } "
                + "N::Box::~Box(){ Size value; } Size after;").result().program();
        var out = (OutOfLineDestructorDecl) program.declarations().get(2);
        assertEquals(MiniType.CHAR, ((VarDeclStmt) out.destructor().body().statements().getFirst()).type());
        assertEquals(MiniType.LONG_LONG, program.globals().getFirst().type());
    }

    @Test void malformedDeferredDestructorCannotConsumeLaterMembersOrDeclarations() {
        var parser = parse("struct Box { ~Box(){ int broken = ; } int retained; int good(){ return 3; } }; int after(){return 0;}");
        assertFalse(parser.succeeded());
        var record = parser.result().program().structs().getFirst();
        assertTrue(record.fields().stream().anyMatch(f -> f.name().equals("retained")));
        assertNotNull(((MethodMember) record.cppInfo().members().getLast()).method().body());
        assertAfter(parser);
    }

    @ParameterizedTest @ValueSource(strings = {"N::Box::~Box() const {}", "N::Box::~Box(int Size) {}", "N::Box::~Box(){ int broken = ; }", "N::Box::~Missing() {}"})
    void erroneousOutOfLineDefinitionsRestoreScopesAndRecoverAtTheNextDeclaration(String declaration) {
        var parser = parse("typedef long long Size; namespace N { typedef char Size; struct Box { ~Box(); }; } "
                + declaration + " Size afterValue; int after(){return 0;}");
        assertFalse(parser.succeeded());
        assertEquals(MiniType.LONG_LONG, parser.result().program().globals().getFirst().type());
        assertAfter(parser);
    }

    @Test void aDestructorCannotUseATypedefSpellingAfterTheTilde() {
        var parser = parse("struct Box { ~Box(); }; typedef Box Alias; Alias::~Alias(){} int after(){return 0;}");
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(d -> d.message().contains("所属类")));
        assertAfter(parser);
    }

    @Test void destructorNodesValidateTheirSourceOnlyIdentity() {
        var parser = successful("struct Box { ~Box(); }; Box::~Box(){}");
        var out = (OutOfLineDestructorDecl) parser.result().program().declarations().getLast();
        var destructor = out.destructor();
        assertThrows(IllegalArgumentException.class, () -> new DestructorMember("", null, destructor.nameRange(), destructor.range()));
        assertThrows(NullPointerException.class, () -> new DestructorMember("Box", null, null, destructor.range()));
        assertThrows(IllegalArgumentException.class, () -> new OutOfLineDestructorDecl(
                new QualifiedName(false, List.of("Box", "other"), out.qualifiedName().range()), destructor, out.nameRange()));
    }

    @Test void standaloneOutOfLineNodeCannotBypassCModeOrCoreIrGuards() {
        var parsed = successful("struct Box { ~Box(); }; Box::~Box(){}").result().program();
        var out = (OutOfLineDestructorDecl) parsed.declarations().getLast();
        var source = new Program(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(out), LanguageMode.C, out.range());
        var semantic = new SemanticAnalyzer(source); semantic.analyze();
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d -> d.code().equals("CPP002") && d.range().equals(out.range())), () -> semantic.errors().toString());
        assertThrows(IllegalArgumentException.class, () -> new IrLowerer(source, Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new IrLowerer().lower(parsed));
    }

    @Test void unusedDestructorBindsItsBodyAndPreservesSourceIdentity() {
        var program = successful("struct Box { ~Box(){ int value = 1; } }; int main(){ return 0; }").result().program();
        var destructor = (DestructorMember) program.structs().getFirst().cppInfo().members().getFirst();
        var semantic = new SemanticAnalyzer(program); semantic.analyze();
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        var bound = assertInstanceOf(FunctionDecl.class, semantic.semanticResult().sourceToCore().get(destructor));
        assertEquals(destructor.range(), bound.range());
        assertNotNull(bound.body());
        assertEquals(1, destructor.body().statements().size());
        assertSame(destructor, program.structs().getFirst().cppInfo().members().getFirst());
    }

    @ParameterizedTest @ValueSource(strings = {"void Box::~Box() {}", "int Box::~Box();"})
    void outOfLineDestructorsCannotDeclareAReturnType(String declaration) {
        var parser = parse("struct Box { ~Box(); }; " + declaration + " int after(){return 0;}");
        assertFalse(parser.succeeded());
        assertAfter(parser);
    }

    @ParameterizedTest @ValueSource(strings = {
            "struct Box { ~Box(); };",
            "struct Box { ~Box() {} };",
            "struct Box { ~Box(void) {} };",
            "class Box { ~Box() {} public: int value; };",
            "struct Box { private: ~Box() {} public: int value; };",
            "struct Box { protected: ~Box(); };",
            "struct Box { ~Box(); int value; }; Box::~Box(){ this->value = 1; }",
            "namespace N { struct Box { ~Box(); }; } N::Box::~Box() {}",
            "namespace N { struct Box { ~Box(); }; Box::~Box() {} }",
            "namespace N { struct Box { ~Box(); }; } ::N::Box::~Box() {}",
            "struct Box { ~Box(); }; typedef Box Alias; Alias::~Box() {}",
            "typedef int Later; struct Box { ~Box(){ value = sizeof(Later); } int value; char Later; };"
    })
    void validDestructorSyntaxBindsToOrdinaryCoreFunctions(String declaration) {
        var parser = successful(declaration + " int after(){return 0;} int main(){return after();}");
        assertAfter(parser);
        var semantic = new SemanticAnalyzer(parser.result().program()); semantic.analyze();
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        for (var source : nodes(parser.result().program())) {
            if (source instanceof DestructorMember destructor) {
                var core = assertInstanceOf(FunctionDecl.class, semantic.semanticResult().sourceToCore().get(destructor));
                assertEquals(destructor.range(), core.range());
                assertEquals(destructor.body() != null, core.body() != null);
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {
            "~Box(int value) {}", "~Box(...) {}", "~Box() const {}", "~Box() volatile {}",
            "~Box() & {}", "~Box() && {}", "~Other() {}", "void ~Box() {}", "int ~Box();",
            "~Box():value(1) {}"
    })
    void invalidDestructorHeadersCannotConsumeFollowingMembers(String member) {
        var parser = parse("struct Box { " + member + " int retained; }; int after(){return 0;}");
        assertFalse(parser.succeeded());
        assertTrue(parser.result().program().structs().getFirst().fields().stream().anyMatch(f -> f.name().equals("retained")), () -> parser.errors().toString());
        assertAfter(parser);
    }

    @ParameterizedTest @ValueSource(strings = {"~Box() = default;", "~Box() = delete;", "~Box() noexcept {}"})
    void supportedDestructorSpecifiersRetainMetadataAndBind(String member) {
        var parser = successful("struct Box { " + member + " int retained; }; int after(){return 0;} int main(){return after();}");
        var destructor = parser.result().program().structs().getFirst().cppInfo().members().stream()
                .filter(DestructorMember.class::isInstance).map(DestructorMember.class::cast).findFirst().orElseThrow();
        assertEquals(member.contains("default") ? DefinitionKind.DEFAULTED : member.contains("delete")
                ? DefinitionKind.DELETED : DefinitionKind.ORDINARY, destructor.definitionKind());
        assertEquals(member.contains("noexcept"), destructor.exceptionSpecification().specified());
        var semantic = new SemanticAnalyzer(parser.result().program()); semantic.analyze();
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        assertAfter(parser);
    }

    @Test void virtualDestructorRemainsAnExplicitUnsupportedBoundary() {
        var parser = parse("struct Box { virtual ~Box() {} int retained; }; int after(){return 0;}");
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("CPP001")), () -> parser.errors().toString());
        assertAfter(parser);
    }

    @Test void deletedOutOfLineDestructorParsesButCannotRedefineAnEarlierDeclaration() {
        var parser = successful("typedef long long Size; namespace N {typedef char Size; struct Box {~Box();};} "
                + "N::Box::~Box()=delete; Size after; int main(){return 0;}");
        assertEquals(MiniType.LONG_LONG, parser.result().program().globals().getFirst().type());
        var out = parser.result().program().declarations().stream().filter(OutOfLineDestructorDecl.class::isInstance)
                .map(OutOfLineDestructorDecl.class::cast).findFirst().orElseThrow();
        assertEquals(DefinitionKind.DELETED, out.destructor().definitionKind());
        var semantic = new SemanticAnalyzer(parser.result().program()); semantic.analyze();
        assertTrue(semantic.errors().stream().anyMatch(d -> d.code().equals("CPP004")
                && d.message().contains("first declaration") && d.range().equals(out.range())), () -> semantic.errors().toString());
    }

    @Test void unionDestructorMetadataCannotDisappear() {
        var parser = parse("union Box { ~Box() {} int retained; }; int after(){return 0;}");
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("CPP001")));
        assertAfter(parser);
    }

    @Test void cModeRejectsDestructorSyntax() {
        var lexer = new Lexer(new SourceFile("legacy.c", "struct Box { ~Box() {} }; int main(){return 0;}"));
        var parser = new Parser(lexer.lex().tokens()); parser.parse();
        assertFalse(parser.succeeded());
    }

    private static void assertAfter(Parser parser) { assertTrue(parser.result().program().functions().stream().anyMatch(f -> f.name().equals("after")), () -> parser.errors().toString()); }
    private static Expression ungroup(Expression expression) { return expression instanceof GroupingExpr group ? ungroup(group.expression()) : expression; }
    private static List<AstNode> nodes(AstNode node) { var result = new ArrayList<AstNode>(); result.add(node); for (var child : AstChildren.of(node)) result.addAll(nodes(child)); return result; }
    private static Parser successful(String text) { var parser = parse(text); assertTrue(parser.succeeded(), () -> parser.errors().toString()); return parser; }
    private static Parser parse(String text) {
        var lexer = new Lexer(new SourceFile("destructor.cpp", text), LanguageMode.CPP17_ALGORITHM);
        var parser = new Parser(lexer.lex().tokens(), LanguageMode.CPP17_ALGORITHM, true); parser.parse(); return parser;
    }
}
