package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.AstNode;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement.ReturnStmt;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-frontend")
@Timeout(10)
final class CppRecordParserTest {
    @Test void classMembersRetainSourceOrderAccessAndExactRanges() {
        String text = """
                // 中文
                class Box {
                    int value;
                public:
                    int read() { return this->value; }
                protected:
                    int declared(int argument);
                private:
                    int tail;
                };
                int after() { return 0; }
                """;
        var parser = successful(text);
        var record = parser.result().program().structs().getFirst();
        var source = new SourceFile("record.cpp", text);
        assertEquals(RecordKey.CLASS, record.cppInfo().key());
        assertEquals("class", source.text(record.cppInfo().keyRange()));
        var members = record.cppInfo().members();
        assertEquals(List.of("FieldMember", "AccessLabel", "MethodMember", "AccessLabel", "MethodMember", "AccessLabel", "FieldMember"),
                members.stream().map(n -> n.getClass().getSimpleName()).toList());
        assertEquals(List.of("value", "tail"), record.fields().stream().map(StructField::name).toList());
        assertSame(record.fields().getFirst(), ((FieldMember) members.getFirst()).field());
        assertEquals(Access.PUBLIC, ((AccessLabel) members.get(1)).access());
        assertEquals("public:", source.text(members.get(1).range()));
        assertEquals(Access.PROTECTED, ((AccessLabel) members.get(3)).access());
        assertEquals(Access.PRIVATE, ((AccessLabel) members.get(5)).access());
        var method = (MethodMember) members.get(2);
        assertEquals("read", source.text(method.nameRange()));
        assertEquals("int read() { return this->value; }", source.text(method.range()));
        var access = assertInstanceOf(FieldAccessExpr.class, ((ReturnStmt) method.method().body().statements().getFirst()).expression());
        assertTrue(access.viaPointer());
        assertEquals("value", access.fieldName());
        var self = assertInstanceOf(ThisExpr.class, access.target());
        assertEquals("this", source.text(self.range()));
        var declaration = ((MethodMember) members.get(4)).method();
        assertNull(declaration.body());
        assertEquals("argument", declaration.parameters().getFirst().name());
        assertEquals(List.of("after"), parser.result().program().functions().stream().map(FunctionDecl::name).toList());
    }

    @Test void structMetadataAndFunctionPointerFieldsRemainDistinctFromMethods() {
        var record = successful("struct Box { int (*callback)(int); int method(int); int field; };")
                .result().program().structs().getFirst();
        assertEquals(RecordKey.STRUCT, record.cppInfo().key());
        assertEquals(List.of("callback", "field"), record.fields().stream().map(StructField::name).toList());
        assertTrue(record.fields().getFirst().type().isPointer());
        assertTrue(record.fields().getFirst().type().pointee().isFunction());
        assertInstanceOf(MethodMember.class, record.cppInfo().members().get(1));
    }

    @Test void methodBodiesSeeAllMembersWhileSignaturesKeepTheirDeclarationPoint() {
        var parser = successful("typedef int Size; struct Box { ::Size read() { return sizeof(Size); } char Size; }; Size outside;");
        var method = method(parser.result().program().structs().getFirst(), "read");
        assertEquals(MiniType.INT, method.returnType());
        var query = assertInstanceOf(SizeofExpr.class, ((ReturnStmt) method.body().statements().getFirst()).expression());
        assertNull(query.queriedType(), "Later field Size must hide the outer typedef in the complete class body");
        var operand = assertInstanceOf(GroupingExpr.class, query.expression()).expression();
        assertEquals("Size", assertInstanceOf(NameExpr.class, operand).name());
        assertEquals(MiniType.INT, parser.result().program().globals().getFirst().type());
    }

    @Test void laterMemberNamesCanInvalidateTypeSyntaxInEarlierMethodBodies() {
        var parser = parse("typedef int Type; struct Box { int read() { Type local; return 0; } int Type; }; int after() { return 0; }");
        assertFalse(parser.succeeded());
        assertTrue(parser.result().program().functions().stream().anyMatch(f -> f.name().equals("after")));
    }

    @Test void eachDeferredBodyHasAnIndependentParameterAndTypedefScope() {
        var parser = successful("typedef int Type; struct Box { int first(int Type) { return sizeof(Type); } "
                + "int second() { Type item; return sizeof(item); } }; Type outside;");
        var record = parser.result().program().structs().getFirst();
        var query = (SizeofExpr) ((ReturnStmt) method(record, "first").body().statements().getFirst()).expression();
        assertNull(query.queriedType());
        assertEquals(2, method(record, "second").body().statements().size());
        assertEquals(MiniType.INT, parser.result().program().globals().getFirst().type());
    }

    @Test void forwardClassAndStructKeysShareOneTypeIdentity() {
        var records = successful("class Box; struct Box { int value; }; class Box;").result().program().structs();
        assertEquals(List.of("::Box", "::Box", "::Box"), records.stream().map(StructDecl::name).toList());
        assertEquals(List.of(RecordKey.CLASS, RecordKey.STRUCT, RecordKey.CLASS), records.stream().map(r -> r.cppInfo().key()).toList());
        assertFalse(records.getFirst().definition());
        assertTrue(records.get(1).definition());
    }

    @Test void inlineRecordsWithObjectDeclaratorsKeepTheirMethodMetadata() {
        var program = successful("struct Box { int value; int read() { return value; } } item; class Box *pointer;")
                .result().program();
        assertEquals("read", method(program.structs().getFirst(), "read").name());
        assertEquals(List.of("item", "pointer"), program.globals().stream().map(GlobalVarDecl::name).toList());
        assertEquals(MiniType.struct("::Box"), program.globals().getFirst().type());
    }

    @Test void methodCallsAndFieldReadsKeepTheirExistingExpressionShapes() {
        var record = successful("struct Box { int value; int read() { return this->value; } "
                + "int twice() { return this->read() + read(); } };").result().program().structs().getFirst();
        List<AstNode> nodes = new ArrayList<>();
        walk(method(record, "twice"), nodes);
        var calls = nodes.stream().filter(CallExpr.class::isInstance).map(CallExpr.class::cast).toList();
        assertEquals(2, calls.size());
        assertInstanceOf(FieldAccessExpr.class, calls.getFirst().callee());
        assertInstanceOf(NameExpr.class, calls.get(1).callee());
    }

    @Test void malformedDeferredBodyCannotConsumeLaterMethodsOrTopLevelDeclarations() {
        var parser = parse("struct Box { int bad() { int = ; } int good() { return 7; } int field; }; int after() { return 0; }");
        assertFalse(parser.succeeded());
        assertNotNull(method(parser.result().program().structs().getFirst(), "good").body());
        assertEquals(List.of("after"), parser.result().program().functions().stream().map(FunctionDecl::name).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Box() {}", "~Box() {}", "template<class T> int method(T item) { return 0; }",
            "static int method();", "int method() const;", "int method() volatile;",
            "int method() &;", "int method() = 0;", "int field = 3;"
    })
    void unsupportedMemberSyntaxIsExplicitAndRecoveryKeepsFollowingDeclarations(String unsupported) {
        var parser = parse("struct Box { " + unsupported + " int retained; }; int after() { return 0; }");
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("CPP001")), () -> parser.errors().toString());
        assertTrue(parser.result().program().structs().getFirst().fields().stream().anyMatch(f -> f.name().equals("retained")));
        assertTrue(parser.result().program().functions().stream().anyMatch(f -> f.name().equals("after")));
    }

    @Test void inheritanceIsExplicitlyRejectedWithoutConsumingTheFollowingDeclaration() {
        var parser = parse("struct Base { int value; }; class Derived : public Base { int extra; }; int after() { return 0; }");
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("CPP001")));
        assertTrue(parser.result().program().functions().stream().anyMatch(f -> f.name().equals("after")));
    }

    @ParameterizedTest @ValueSource(strings = {"int method();", "public: int value;"})
    void unionMethodsAndAccessAreRejectedButLegacyDataUnionsRemainSupported(String member) {
        var parser = parse("union Data { " + member + " }; int after() { return 0; }");
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(d -> d.code().equals("CPP001")));
        assertTrue(parser.result().program().functions().stream().anyMatch(f -> f.name().equals("after")));
        assertTrue(successful("union Data { int value; char bytes[4]; };").result().program().structs().getFirst().union());
    }

    @ParameterizedTest @ValueSource(strings = {"class Box { int value; };", "class Box *pointer;", "struct Box { int read() { return 0; } };", "struct Box { public: int value; };"})
    void parsedNewRecordSyntaxRemainsGuardedUntilMemberSemanticsExist(String declaration) {
        var parser = successful(declaration + " int main() { return 0; }");
        var semantic = new SemanticAnalyzer(parser.result().program());
        semantic.analyze();
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d -> d.code().equals("CPP005")), () -> semantic.errors().toString());
    }

    @Test void generatedPlaceholderSpellingDoesNotMakeAnExplicitParameterUnnamed() {
        successful("struct Box { int method(int __unnamed0) { return __unnamed0; } };");
        var missing = parse("struct Box { int method(int) { return 0; } };");
        assertFalse(missing.succeeded());
        assertTrue(missing.errors().stream().anyMatch(d -> d.message().contains("参数必须命名")));
    }

    @Test void emptyMemberDeclarationsAndTrailingMethodSemicolonsAreAccepted() {
        var record = successful("struct Box { ; int method() { return 0; }; ; int value; };")
                .result().program().structs().getFirst();
        assertEquals(2, record.cppInfo().members().size());
        assertNotNull(method(record, "method").body());
    }

    @ParameterizedTest
    @ValueSource(strings = {"struct { int first; } member", "union { int first; char second; } member",
            "struct { int first; } member[2]", "union { int first; char second; } member[2]",
            "struct { int first; } *member", "union { int first; char second; } *member"})
    void anonymousAggregateTypesKeepTheirNamedFieldDeclarators(String field) {
        var parser = successful("typedef struct { " + field + "; int tail; } Box; int main() { return 0; }");
        var program = parser.result().program();
        var aliasType = (MiniType.StructType) program.typedefs().getFirst().type().unqualified();
        var record = program.structs().stream().filter(s -> s.name().equals(aliasType.name())).findFirst().orElseThrow();
        assertEquals(List.of("member", "tail"), record.fields().stream().map(StructField::name).toList());
        assertFalse(record.fields().getFirst().anonymous());
        assertEquals(field.contains("[2]"), record.fields().getFirst().type().isArray());
        assertEquals(field.contains("*member"), record.fields().getFirst().type().isPointer());
        var semantic = new SemanticAnalyzer(program);
        semantic.analyze();
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
    }

    @Test void actualAnonymousUnionMembersStillPromoteTheirFields() {
        var parser = successful("typedef struct { union { int first; char second; }; int tail; } Box; "
                + "int main() { Box value; value.first = 3; return value.first - 3; }");
        var program = parser.result().program();
        var aliasType = (MiniType.StructType) program.typedefs().getFirst().type().unqualified();
        var record = program.structs().stream().filter(s -> s.name().equals(aliasType.name())).findFirst().orElseThrow();
        assertTrue(record.fields().getFirst().anonymous());
        var semantic = new SemanticAnalyzer(program);
        semantic.analyze();
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
    }

    @Test void cModeKeepsClassAccessAndThisAsOrdinaryIdentifiers() {
        var lexer = new Lexer(new SourceFile("legacy.c", "struct class { int this; int public; }; int private() { return 0; }"));
        var parser = new Parser(lexer.lex().tokens());
        parser.parse();
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        assertNull(parser.result().program().structs().getFirst().cppInfo());
    }

    private static FunctionDecl method(StructDecl record, String name) {
        return record.cppInfo().members().stream().filter(MethodMember.class::isInstance).map(MethodMember.class::cast)
                .map(MethodMember::method).filter(m -> m.name().equals(name)).findFirst().orElseThrow();
    }
    private static void walk(AstNode node, List<AstNode> output) { output.add(node); AstChildren.of(node).forEach(child -> walk(child, output)); }
    private static Parser successful(String text) { var parser = parse(text); assertTrue(parser.succeeded(), () -> parser.errors().toString()); return parser; }
    private static Parser parse(String text) {
        var lexer = new Lexer(new SourceFile("record.cpp", text), LanguageMode.CPP17_ALGORITHM);
        var parser = new Parser(lexer.lex().tokens(), LanguageMode.CPP17_ALGORITHM, true);
        parser.parse();
        return parser;
    }
}
