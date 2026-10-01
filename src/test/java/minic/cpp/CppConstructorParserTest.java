package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.lexer.Lexer;
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

import static org.junit.jupiter.api.Assertions.*;

/** Construction grammar keeps source identity before normalization into executable initialization. */
@Timeout(10)
class CppConstructorParserTest {
    @Test void constructorPrototypesAndDefinitionsKeepAccessOrderAndNoReturnType() {
        var record = record(successful("class Box { Box(); public: Box(int value) : value(value) {} int value; };"));
        assertEquals(List.of("ConstructorMember", "AccessLabel", "ConstructorMember", "FieldMember"),
                record.cppInfo().members().stream().map(n -> n.getClass().getSimpleName()).toList());
        var constructors = constructors(record);
        assertNull(constructors.getFirst().body());
        assertTrue(constructors.getFirst().parameters().isEmpty());
        assertEquals("value", constructors.get(1).parameters().getFirst().name());
        assertEquals(MiniType.INT, constructors.get(1).parameters().getFirst().type());
        assertNotNull(constructors.get(1).body());
        assertEquals(1, record.fields().size());
        assertSame(record.fields().getFirst(), ((FieldMember) record.cppInfo().members().getLast()).field());
    }

    @Test void initializerSpellingWrittenOrderAndUtf8RangesAreExact() {
        String text = "// 中文\nstruct Box { int first; int second; Box(int value): second{value,}, first((value, 1)) { this->first = value; } };";
        var constructor = constructors(record(successful(text))).getFirst();
        var source = new SourceFile("constructor.cpp", text);
        assertEquals("Box", source.text(constructor.nameRange()));
        assertEquals("Box(int value): second{value,}, first((value, 1)) { this->first = value; }", source.text(constructor.range()));
        assertEquals(List.of("second", "first"), constructor.initializers().stream().map(i -> i.target().segments().getLast()).toList());
        var second = constructor.initializers().getFirst();
        assertEquals("second{value,}", source.text(second.range()));
        assertEquals("second", source.text(second.target().range()));
        assertEquals("{value,}", source.text(second.initializer().range()));
        assertEquals(CppInitializer.Kind.DIRECT_LIST, second.initializer().kind());
        var first = constructor.initializers().getLast().initializer();
        assertEquals(CppInitializer.Kind.DIRECT_PAREN, first.kind());
        assertEquals(1, first.arguments().size());
        assertInstanceOf(CommaExpr.class, assertInstanceOf(GroupingExpr.class, first.arguments().getFirst()).expression());
        assertTrue(nodes(constructor.body()).stream().anyMatch(ThisExpr.class::isInstance));
    }

    @Test void constructorArgumentsPreserveCommaSeparationNestedListsAndEmptyForms() {
        var constructor = constructors(record(successful("struct Box { int a; int b; int c; int d; "
                + "Box():a(),b{},c(1,2),d{{1},{2}}{} };"))).getFirst();
        assertEquals(List.of(0, 0, 2, 2), constructor.initializers().stream().map(i -> i.initializer().arguments().size()).toList());
        assertInstanceOf(CppInitializer.class, constructor.initializers().getLast().initializer().arguments().getFirst());
    }

    @Test void constructorInitializerAndBodySeeTheCompleteClassButParametersKeepTheirPoint() {
        var program = successful("typedef int Size; struct Box { Box(::Size input):value(sizeof(Size)) { value = sizeof(Size); } "
                + "int value; char Size; }; Size after;").result().program();
        var constructor = constructors(program.structs().getFirst()).getFirst();
        assertEquals(MiniType.INT, constructor.parameters().getFirst().type());
        var query = assertInstanceOf(SizeofExpr.class, constructor.initializers().getFirst().initializer().arguments().getFirst());
        assertNull(query.queriedType(), "Later member Size hides the outer type in a complete-class context");
        assertEquals("Size", assertInstanceOf(NameExpr.class, ungroup(query.expression())).name());
        assertEquals(MiniType.INT, program.globals().getFirst().type());
    }

    @Test void constructorParametersAndLocalTypedefsDoNotLeakToOtherDeferredContexts() {
        var program = successful("typedef int Type; struct Box { Box(int Type):value(sizeof(Type)) { typedef char Local; } "
                + "Box():value(sizeof(Type)) {} int value = sizeof(Type); int read(){ Type local; return sizeof(local); } }; Type after;")
                .result().program();
        var record = program.structs().getFirst();
        var constructors = constructors(record);
        assertNull(((SizeofExpr) constructors.getFirst().initializers().getFirst().initializer().arguments().getFirst()).queriedType());
        assertEquals(MiniType.INT, ((SizeofExpr) constructors.getLast().initializers().getFirst().initializer().arguments().getFirst()).queriedType());
        var field = record.cppInfo().members().stream().filter(FieldMember.class::isInstance).map(FieldMember.class::cast).findFirst().orElseThrow();
        assertEquals(MiniType.INT, ((SizeofExpr) field.defaultInitializer().arguments().getFirst()).queriedType());
        assertEquals(MiniType.INT, program.globals().getFirst().type());
    }

    @Test void defaultMemberInitializersRetainKindsAndCompleteClassLookup() {
        String text = "typedef int Later; struct Box { int first = sizeof(Later); int second{2}; int third = {3}; char Later; }; Later after;";
        var program = successful(text).result().program();
        var members = program.structs().getFirst().cppInfo().members().stream().map(FieldMember.class::cast).toList();
        assertEquals(CppInitializer.Kind.COPY, members.get(0).defaultInitializer().kind());
        assertEquals(CppInitializer.Kind.DIRECT_LIST, members.get(1).defaultInitializer().kind());
        assertEquals(CppInitializer.Kind.COPY_LIST, members.get(2).defaultInitializer().kind());
        assertNull(members.get(3).defaultInitializer());
        assertNull(((SizeofExpr) members.get(0).defaultInitializer().arguments().getFirst()).queriedType());
        assertEquals("= sizeof(Later)", new SourceFile("constructor.cpp", text).text(members.get(0).defaultInitializer().range()));
        assertEquals(MiniType.INT, program.globals().getFirst().type());
    }

    @ParameterizedTest @ValueSource(strings = {
            "N::Box::Box(Size n):value(n) { this->value = n; }",
            "namespace N { Box::Box(Size n):value(n) { this->value = n; } }",
            "::N::Box::Box(Size n):value(n) { this->value = n; }"
    })
    void qualifiedConstructorsKeepStructuredIdentityAndOwnerNamespaceScope(String definition) {
        var program = successful("typedef long long Size; namespace N { typedef char Size; struct Box { Box(Size); int value; }; } "
                + definition + " Size after; int main(){return 0;}").result().program();
        var out = nodes(program).stream().filter(OutOfLineConstructorDecl.class::isInstance).map(OutOfLineConstructorDecl.class::cast).findFirst().orElseThrow();
        assertEquals("Box", out.qualifiedName().segments().getLast());
        assertEquals(MiniType.CHAR, out.constructor().parameters().getFirst().type());
        assertNotNull(out.constructor().body());
        assertEquals("value", out.constructor().initializers().getFirst().target().segments().getLast());
        assertEquals(MiniType.LONG_LONG, program.globals().getFirst().type());
        assertEquals(List.of("main"), program.functions().stream().map(FunctionDecl::name).toList());
    }

    @Test void anAliasedOwnerStillUsesTheInjectedClassNameForItsConstructor() {
        var program = successful("struct Box { Box(); }; typedef Box Alias; Alias::Box() {} int main(){return 0;}").result().program();
        var definition = program.declarations().stream().filter(OutOfLineConstructorDecl.class::isInstance)
                .map(OutOfLineConstructorDecl.class::cast).findFirst().orElseThrow();
        assertEquals(List.of("Alias", "Box"), definition.qualifiedName().segments());
        assertNotNull(definition.constructor().body());
    }

    @Test void aNamespaceAndClassWithTheSameNameDoNotTurnAParenthesizedVariableIntoAConstructor() {
        var parser = successful("namespace N { struct N { int value; }; } N::N (object); int main(){return 0;}");
        assertEquals("object", parser.result().program().globals().getFirst().name());
        assertEquals(MiniType.struct("::N::N"), parser.result().program().globals().getFirst().type());
        assertTrue(nodes(parser.result().program()).stream().noneMatch(OutOfLineConstructorDecl.class::isInstance));
    }

    @Test void constructorNamesDoNotHideTheirInjectedTypeAndFunctionPointersRemainFields() {
        var record = record(successful("struct Box { Box(); Box *next; int (*callback)(int); Box(const Box &other); };"));
        assertEquals(List.of("next", "callback"), record.fields().stream().map(StructField::name).toList());
        assertEquals(MiniType.struct("::Box"), record.fields().getFirst().type().pointee());
        assertTrue(constructors(record).getLast().parameters().getFirst().type().isReference());
    }

    @Test void unnamedParametersAreAllowedInDeclarationsAndDefinitions() {
        successful("struct Box { Box(int); Box(int __unnamed0, int extra):value(__unnamed0) {} int value; };");
        var parser = parse("struct Box { Box(int):value(1) {} int value; }; int after(){return 0;}");
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        assertEquals(MiniType.INT, constructors(record(parser)).getFirst().parameters().getFirst().type());
        assertAfter(parser);
    }

    @Test void variadicConstructorParametersAreRetained() {
        assertTrue(constructors(record(successful("struct Box { Box(int first, ...); };"))).getFirst().variadic());
    }

    @ParameterizedTest @ValueSource(strings = {
            "Box():value(1 +) {}", "Box():value{1 +} {}", "Box(){ int broken = ; }", "int value = 1 +;"
    })
    void malformedDeferredContextCannotConsumeFollowingMembersOrDeclarations(String broken) {
        var parser = parse("struct Box { " + broken + " int retained; int good(){ return 3; } }; int after(){return 0;}");
        assertFalse(parser.succeeded());
        var record = record(parser);
        assertTrue(record.fields().stream().anyMatch(f -> f.name().equals("retained")));
        assertNotNull(record.cppInfo().members().stream().filter(MethodMember.class::isInstance).map(MethodMember.class::cast).findFirst().orElseThrow().method().body());
        assertAfter(parser);
    }

    @Test void failedOutOfLineConstructorRestoresOwnerAndParameterScopes() {
        var parser = parse("typedef long long Size; namespace N { typedef char Size; struct Box { Box(int); int value; }; } "
                + "N::Box::Box(int Size):value(1 +) { int bad = ; } Size after; int afterFunction(){ return 0; }");
        assertFalse(parser.succeeded());
        assertEquals(MiniType.LONG_LONG, parser.result().program().globals().getFirst().type());
        assertTrue(parser.result().program().functions().stream().anyMatch(f -> f.name().equals("afterFunction")));
    }

    @ParameterizedTest @ValueSource(strings = {"N::Box::Box() const {}", "N::Box::Box(Unknown parameter) {}",
            "N::Box::Box() = delete;"})
    void rejectedOutOfLineHeaderDoesNotRecoverPastTheNextDeclaration(String broken) {
        var parser = parse("typedef long long Size; namespace N { typedef char Size; struct Box { Box(); }; } "
                + broken + " Size after; int afterFunction(){return 0;}");
        assertFalse(parser.succeeded());
        assertEquals(MiniType.LONG_LONG, parser.result().program().globals().stream().filter(v -> v.name().equals("after"))
                .findFirst().orElseThrow(() -> new AssertionError(parser.errors())).type());
        assertTrue(parser.result().program().functions().stream().anyMatch(f -> f.name().equals("afterFunction")));
    }

    @ParameterizedTest @ValueSource(strings = {"void Box() {}", "int Box();"})
    void constructorSpellingWithAReturnTypeCannotMasqueradeAsAMethod(String member) {
        var parser = parse("struct Box { " + member + " int retained; }; int after(){return 0;}");
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("CPP001")));
        assertTrue(record(parser).fields().stream().anyMatch(f -> f.name().equals("retained")));
        assertAfter(parser);
    }

    @ParameterizedTest @ValueSource(strings = { "Box() = default;", "Box() = delete;",
            "constexpr Box() {}", "Box() noexcept {}", "Box():Box(1) {}" })
    void unsupportedConstructorExtensionsAreExplicitAndRecoveryIsBounded(String member) {
        var parser = parse("struct Box { " + member + " int retained; }; int after(){return 0;}");
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("CPP001")), () -> parser.errors().toString());
        assertTrue(record(parser).fields().stream().anyMatch(f -> f.name().equals("retained")));
        assertAfter(parser);
    }

    @ParameterizedTest @ValueSource(strings = {"Data() {}", "int value = 1;", "int value{1};"})
    void unionConstructionMetadataCannotSilentlyDisappear(String member) {
        var parser = parse("union Data { " + member + " int retained; }; int after(){return 0;}");
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("CPP001")));
        assertAfter(parser);
    }

    @ParameterizedTest @ValueSource(strings = {"struct Box { Box(); };", "struct Box { Box() {} };",
            "struct Box { int value = 1; };", "struct Box { Box(); }; Box::Box(){}"})
    void parsedConstructionBindsWhileRetainingItsSourceSyntax(String declaration) {
        var parser = successful(declaration + " int main(){return 0;}");
        var semantic = new SemanticAnalyzer(parser.result().program());
        semantic.analyze();
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        assertTrue(nodes(parser.result().program()).stream().anyMatch(n -> n instanceof ConstructorMember || n instanceof CppInitializer));
    }

    @Test void ordinaryScalarAndReferenceInitializersKeepTheirExecutableExistingShapes() {
        var parser = successful("int global = 1; int main(){ int value = 2; int &alias(value); const int &ref = value; return alias; }");
        assertInstanceOf(IntegerLiteralExpr.class, parser.result().program().globals().getFirst().initializer());
        var variables = parser.result().program().functions().getFirst().body().statements().stream().filter(VarDeclStmt.class::isInstance).map(VarDeclStmt.class::cast).toList();
        assertInstanceOf(GroupingExpr.class, variables.get(1).initializer());
        assertEquals(CppInitializer.Kind.COPY, parser.result().program().globals().getFirst().cppInitializer().kind());
        assertEquals(CppInitializer.Kind.DIRECT_PAREN, variables.get(1).cppInitializer().kind());
        var semantic = new SemanticAnalyzer(parser.result().program()); semantic.analyze();
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
    }

    @Test void cStructFieldsAndInitializationRemainUnchanged() {
        var lexer = new Lexer(new SourceFile("legacy.c", "struct Box { int Box; }; int main(){struct Box value={1}; return value.Box;}"));
        var parser = new Parser(lexer.lex().tokens()); parser.parse();
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        assertNull(record(parser).cppInfo());
        assertTrue(nodes(parser.result().program()).stream().noneMatch(CppInitializer.class::isInstance));
    }

    private static Expression ungroup(Expression expression) { return expression instanceof GroupingExpr group ? ungroup(group.expression()) : expression; }
    private static void assertAfter(Parser parser) { assertTrue(parser.result().program().functions().stream().anyMatch(f -> f.name().equals("after"))); }
    private static List<ConstructorMember> constructors(StructDecl record) { return record.cppInfo().members().stream().filter(ConstructorMember.class::isInstance).map(ConstructorMember.class::cast).toList(); }
    private static StructDecl record(Parser parser) { return parser.result().program().structs().getFirst(); }
    private static List<AstNode> nodes(AstNode node) { List<AstNode> result = new ArrayList<>(); result.add(node); for (var child : AstChildren.of(node)) result.addAll(nodes(child)); return result; }
    private static Parser successful(String text) { var parser = parse(text); assertTrue(parser.succeeded(), () -> parser.errors().toString()); return parser; }
    private static Parser parse(String text) {
        var lexer = new Lexer(new SourceFile("constructor.cpp", text), LanguageMode.CPP17_ALGORITHM);
        var parser = new Parser(lexer.lex().tokens(), LanguageMode.CPP17_ALGORITHM, true); parser.parse(); return parser;
    }
}
