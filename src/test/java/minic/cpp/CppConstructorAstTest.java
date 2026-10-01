package minic.cpp;

import minic.SourceRange;
import minic.compiler.LanguageMode;
import minic.compiler.ir.IrLowerer;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.CppInitializer.Kind;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.cpp.CppNameBinder;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Source contracts for construction and normalization into ordinary core functions. */
class CppConstructorAstTest {
    private static final SourceRange RANGE = new SourceRange(2, 0, 2, 50);
    private static final SourceRange NAME = new SourceRange(2, 4, 2, 10);
    private static final SourceRange INIT = new SourceRange(2, 12, 2, 20);

    @ParameterizedTest @EnumSource(Kind.class)
    void initializationSpellingAndNestedArgumentIdentitySurvive(Kind kind) {
        var first = new IntegerLiteralExpr(1, "1", INIT);
        var second = new IntegerLiteralExpr(2, "2", INIT);
        var comma = new GroupingExpr(new CommaExpr(List.of(first, second), INIT), INIT);
        var arguments = new ArrayList<Expression>(kind == Kind.DEFAULT ? List.of() : List.of(comma));
        var initializer = new CppInitializer(kind, arguments, INIT);
        arguments.clear();
        assertEquals(kind, initializer.kind());
        assertSame(INIT, initializer.range());
        assertEquals(kind == Kind.DEFAULT ? List.of() : List.of(comma), AstChildren.of(initializer));
        if (kind != Kind.DEFAULT) assertSame(comma, initializer.arguments().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> initializer.arguments().clear());
    }

    @Test void defaultAndCopyHaveGrammarArityWhileDirectFormsRetainDistinctArguments() {
        var one = new IntegerLiteralExpr(1, "1", INIT);
        var two = new IntegerLiteralExpr(2, "2", INIT);
        assertThrows(IllegalArgumentException.class, () -> new CppInitializer(Kind.DEFAULT, List.of(one), INIT));
        assertThrows(IllegalArgumentException.class, () -> new CppInitializer(Kind.COPY, List.of(), INIT));
        assertThrows(IllegalArgumentException.class, () -> new CppInitializer(Kind.COPY, List.of(one, two), INIT));
        var nested = new CppInitializer(Kind.DIRECT_LIST, List.of(one, two), INIT);
        var outer = new CppInitializer(Kind.COPY_LIST, List.of(nested), RANGE);
        assertSame(nested, outer.arguments().getFirst());
        assertEquals(List.of(one, two), new CppInitializer(Kind.DIRECT_PAREN, List.of(one, two), INIT).arguments());
    }

    @Test void constructorKeepsParameterAndWrittenMemberOrderWithoutInventingAReturnType() {
        var parameter = new Parameter("value", MiniType.INT, NAME);
        var second = member("second", 2);
        var first = member("first", 1);
        var body = new BlockStmt(List.of(), RANGE);
        var parameters = new ArrayList<>(List.of(parameter));
        var initializers = new ArrayList<>(List.of(second, first));
        var constructor = new ConstructorMember("Box", parameters, true, initializers, body, NAME, RANGE);
        parameters.clear(); initializers.clear();
        assertEquals("Box", constructor.name());
        assertTrue(constructor.variadic());
        assertSame(NAME, constructor.nameRange());
        assertSame(RANGE, constructor.range());
        assertEquals(List.of(parameter, second, first, body), AstChildren.of(constructor));
        assertEquals(List.of(second.initializer()), AstChildren.of(second));
        assertThrows(UnsupportedOperationException.class, () -> constructor.parameters().clear());
        assertThrows(UnsupportedOperationException.class, () -> constructor.initializers().clear());
        assertFalse((Object) constructor instanceof MethodMember);
        assertFalse((Object) constructor instanceof FunctionDecl);
        assertThrows(IllegalArgumentException.class, () -> new ConstructorMember("", List.of(), false, List.of(), null, NAME, RANGE));
    }

    @Test void defaultMemberInitializerRetainsFieldProjectionAndLegacyConstructor() {
        var field = new StructField("first", MiniType.INT, RANGE);
        var initializer = member("first", 1).initializer();
        var wrapped = new FieldMember(field, initializer);
        var constructor = constructor(List.of(), List.of(), null);
        var record = record(List.of(wrapped, constructor));
        assertSame(field, record.fields().getFirst());
        assertEquals(List.of(field, initializer), AstChildren.of(wrapped));
        assertSame(initializer, wrapped.defaultInitializer());
        assertNull(new FieldMember(field).defaultInitializer());
        assertEquals(List.of(field), AstChildren.of(new FieldMember(field)));
    }

    @Test void qualifiedDefinitionPreservesConstructorAndRequiresOwnerAndMatchingName() {
        var constructor = constructor(List.of(), List.of(), new BlockStmt(List.of(), RANGE));
        var definition = new OutOfLineConstructorDecl(name("N", "Box", "Box"), constructor, NAME);
        assertSame(constructor, definition.constructor());
        assertSame(RANGE, definition.range());
        assertEquals(List.of(constructor), AstChildren.of(definition));
        assertThrows(IllegalArgumentException.class, () -> new OutOfLineConstructorDecl(name("Box"), constructor, NAME));
        assertThrows(IllegalArgumentException.class, () -> new OutOfLineConstructorDecl(name("Box", "Other"), constructor, NAME));
    }

    @Test void unusedConstructorsAndDefaultMemberInitializersCannotSilentlyDisappear() {
        var constructor = constructor(List.of(), List.of(), new BlockStmt(List.of(), RANGE));
        var field = new FieldMember(new StructField("first", MiniType.INT, RANGE), member("first", 1).initializer());
        for (Declaration declaration : List.of(record(List.of(constructor)), record(List.of(field)))) {
            var source = program(LanguageMode.CPP17_ALGORITHM, declaration);
            var binding = CppNameBinder.bind(source);
            assertTrue(binding.diagnostics().isEmpty(), () -> binding.diagnostics().toString());
            assertTrue(binding.program().functions().size() > 1, "Construction must retain executable initialization functions");
            assertNull(AstChildren.firstCppSyntax(binding.program()));
            assertTrue(analyze(source).succeeded());
        }
        var missingOwner = CppNameBinder.bind(program(LanguageMode.CPP17_ALGORITHM,
                new OutOfLineConstructorDecl(name("Box", "Box"), constructor, NAME)));
        assertTrue(missingOwner.diagnostics().stream().anyMatch(d -> d.code().equals("CPP003")));
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void sourceInitializerIsNeverAcceptedAsAnOrdinaryCoreExpression(Kind kind) {
        var initializer = new CppInitializer(kind, kind == Kind.DEFAULT ? List.of()
                : List.of(new IntegerLiteralExpr(1, "1", INIT)), INIT);
        var main = main(new VarDeclStmt("value", MiniType.INT, initializer, RANGE));
        var cpp = program(LanguageMode.CPP17_ALGORITHM, main);
        assertTrue(CppNameBinder.bind(cpp).diagnostics().stream().anyMatch(d -> d.code().equals("CPP005")));
        var c = program(LanguageMode.C, main);
        assertSame(initializer, AstChildren.firstCppSyntax(c));
        assertTrue(analyze(c).errors().stream().anyMatch(d -> d.code().equals("CPP002") && d.range().equals(INIT)));
        assertThrows(IllegalArgumentException.class, () -> new IrLowerer(c, Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new IrLowerer().lower(c));
    }

    @Test void rawCoreGuardsScanFlatIndexesAndStandaloneConstructorNodes() {
        var constructor = constructor(List.of(), List.of(), null);
        var record = record(List.of(constructor));
        var main = main(null);
        var hidden = new Program(List.of(record), List.of(), List.of(), List.of(), List.of(main),
                List.of(main), LanguageMode.C, RANGE);
        assertSame(constructor, AstChildren.firstCppSyntax(constructor));
        assertSame(record, AstChildren.firstCppSyntax(hidden));
        assertThrows(IllegalArgumentException.class, () -> new IrLowerer().lower(hidden));
        assertFalse(analyze(hidden).succeeded());
        var definition = new OutOfLineConstructorDecl(name("Box", "Box"), constructor, NAME);
        assertSame(definition, AstChildren.firstCppSyntax(definition));
    }

    @Test void referenceEscapeGuardSeesConstructorSignatureAndInitializerTypeOperands() {
        var reference = new MiniType.ReferenceType(MiniType.INT);
        var parameter = new Parameter("value", reference, NAME);
        var constructor = constructor(List.of(parameter), List.of(), null);
        assertSame(parameter, AstChildren.firstReferenceSyntax(constructor));
        var query = new SizeofExpr(null, reference, INIT);
        var initializer = new CppInitializer(Kind.DIRECT_LIST, List.of(query), INIT);
        var member = new MemberInitializer(name("first"), initializer, INIT);
        assertSame(query, AstChildren.firstReferenceSyntax(constructor(List.of(), List.of(member), null)));
    }

    private static MemberInitializer member(String name, int value) {
        return new MemberInitializer(name(name), new CppInitializer(Kind.DIRECT_PAREN,
                List.of(new IntegerLiteralExpr(value, Integer.toString(value), INIT)), INIT), INIT);
    }
    private static QualifiedName name(String... parts) { return new QualifiedName(false, List.of(parts), NAME); }
    private static ConstructorMember constructor(List<Parameter> parameters, List<MemberInitializer> initializers, BlockStmt body) {
        return new ConstructorMember("Box", parameters, false, initializers, body, NAME, RANGE);
    }
    private static StructDecl record(List<CppMember> members) {
        return new StructDecl("Box", members.stream().filter(FieldMember.class::isInstance)
                .map(FieldMember.class::cast).map(FieldMember::field).toList(), true, false,
                new CppRecordInfo(RecordKey.STRUCT, members, NAME), RANGE);
    }
    private static FunctionDecl main(Statement statement) {
        var statements = new ArrayList<Statement>();
        if (statement != null) statements.add(statement);
        statements.add(new ReturnStmt(new IntegerLiteralExpr(0, "0", RANGE), RANGE));
        return new FunctionDecl("main", MiniType.INT, List.of(), false, new BlockStmt(statements, RANGE), false, RANGE);
    }
    private static Program program(LanguageMode mode, Declaration declaration) {
        var declarations = declaration instanceof FunctionDecl ? List.of(declaration) : List.of(declaration, main(null));
        return new Program(declarations.stream().filter(StructDecl.class::isInstance).map(StructDecl.class::cast).toList(),
                List.of(), List.of(), List.of(), declarations.stream().filter(FunctionDecl.class::isInstance)
                .map(FunctionDecl.class::cast).toList(), declarations, mode, RANGE);
    }
    private static SemanticAnalyzer analyze(Program program) {
        var result = new SemanticAnalyzer(program); result.analyze(); return result;
    }
}
