package minic.cpp;

import minic.SourceRange;
import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.ir.IrLowerer;
import minic.compiler.ir.manager.IrTypeLowerer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.Declaration;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Statement;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import minic.compiler.type.TypeLayout;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** F04a1 permits source reference types but deliberately stops them before core lowering. */
@Timeout(60)
final class CppReferenceSyntaxTest {
    @TempDir Path temporary;
    private static final SourceRange RANGE = new SourceRange(2, 3, 2, 15);

    @Test void referenceTypeIsAnIndependentSourceType() {
        MiniType reference = MiniType.INT.referenceTo();
        assertInstanceOf(MiniType.ReferenceType.class, reference);
        assertTrue(reference.isReference());
        assertSame(MiniType.INT, reference.referent());
        assertFalse(reference.isPointer());
        assertFalse(reference.isScalar());
        assertFalse(reference.isArray());
        assertNotEquals(MiniType.INT.pointerTo(), reference);
        assertEquals("int&", reference.toString());
        assertThrows(IllegalStateException.class, reference::pointee);
        assertThrows(IllegalStateException.class, MiniType.INT::referent);
        assertThrows(NullPointerException.class, () -> new MiniType.ReferenceType(null));
    }

    @Test void referenceAliasesCollapseWithoutRemovingReferentQualifiers() {
        MiniType object = MiniType.qualified(MiniType.INT, Set.of(MiniType.TypeQualifier.CONST));
        MiniType reference = object.referenceTo();
        assertEquals(reference, reference.referenceTo());
        assertEquals(reference, new MiniType.ReferenceType(reference));
        assertTrue(reference.referent().isConstQualified());
        assertFalse(reference.isConstQualified());
        assertSame(reference, MiniType.qualified(reference,
                Set.of(MiniType.TypeQualifier.CONST, MiniType.TypeQualifier.VOLATILE)));
        assertTrue(reference.referent().isConstQualified());
        assertFalse(reference.referent().isVolatileQualified());
    }

    @Test void referenceToPointerKeepsEveryQualifierAtItsOwnLayer() {
        MiniType pointee = MiniType.qualified(MiniType.INT, Set.of(MiniType.TypeQualifier.VOLATILE));
        MiniType pointer = MiniType.qualified(pointee.pointerTo(), Set.of(MiniType.TypeQualifier.CONST));
        MiniType reference = pointer.referenceTo();
        assertTrue(reference.referent().isPointer());
        assertTrue(reference.referent().isConstQualified());
        assertTrue(reference.referent().pointee().isVolatileQualified());
        assertFalse(reference.isPointer());
        assertFalse(reference.isConstQualified());
    }

    @Test void arrayAndFunctionReferencesRemainUndecayedInSignatures() {
        MiniType array = MiniType.INT.arrayOf(3);
        MiniType arrayReference = array.referenceTo();
        MiniType signature = MiniType.function(arrayReference, List.of(arrayReference, MiniType.INT.referenceTo()));
        MiniType functionReference = signature.referenceTo();
        assertSame(array, arrayReference.referent());
        assertEquals(3, arrayReference.referent().arrayLength());
        assertSame(signature, functionReference.referent());
        assertTrue(functionReference.referent().isFunction());
        assertTrue(signature.returnType().isReference());
        assertTrue(signature.parameterTypes().stream().allMatch(MiniType::isReference));
    }

    @Test void recursiveReferenceDetectionIncludesNestedFunctionTypes() {
        MiniType reference = MiniType.INT.referenceTo();
        assertTrue(reference.containsReference());
        assertTrue(reference.pointerTo().containsReference());
        assertTrue(reference.arrayOf(2).containsReference());
        assertTrue(MiniType.function(MiniType.INT, List.of(reference)).pointerTo().containsReference());
        assertTrue(MiniType.function(reference, List.of()).containsReference());
        assertTrue(MiniType.qualified(reference.pointerTo(), Set.of(MiniType.TypeQualifier.CONST)).containsReference());
        assertFalse(MiniType.INT.containsReference());
        assertFalse(MiniType.function(MiniType.INT.pointerTo(), List.of(MiniType.INT.arrayOf(2))).containsReference());
    }

    @Test void existingCTypesRetainTheirQualifierBehavior() {
        MiniType object = MiniType.qualified(MiniType.INT, Set.of(MiniType.TypeQualifier.CONST));
        MiniType pointer = MiniType.qualified(object.pointerTo(), Set.of(MiniType.TypeQualifier.VOLATILE));
        assertTrue(object.isConstQualified());
        assertTrue(pointer.isVolatileQualified());
        assertTrue(pointer.pointee().isConstQualified());
        assertFalse(pointer.containsReference());
        assertFalse(pointer.isReference());
        assertEquals(Set.of(MiniType.TypeQualifier.CONST, MiniType.TypeQualifier.VOLATILE),
                MiniType.qualified(object, Set.of(MiniType.TypeQualifier.VOLATILE)).qualifiers());
    }

    static Stream<MiniType> unnormalizedReferenceTypes() {
        MiniType reference = MiniType.INT.referenceTo();
        return Stream.of(reference, reference.arrayOf(2), reference.pointerTo(),
                MiniType.function(reference, List.of(MiniType.INT)).pointerTo());
    }

    @ParameterizedTest @MethodSource("unnormalizedReferenceTypes")
    void sourceOnlyReferencesNeverAcquireACoreLayoutOrIrScalarType(MiniType type) {
        assertAll(
                () -> assertFalse(TypeLayout.hasFixedLayout(type)),
                () -> assertThrows(IllegalArgumentException.class, () -> TypeLayout.sizeOf(type)),
                () -> assertThrows(IllegalArgumentException.class, () -> TypeLayout.alignmentOf(type)),
                () -> assertThrows(IllegalArgumentException.class, () -> IrTypeLowerer.lower(type)));
    }

    static Stream<Arguments> validSyntax() {
        return Stream.of(
                Arguments.of("local", "int main(){int value=1; int &alias=value; return alias;}"),
                Arguments.of("cv-local", "int main(){volatile int value=1; const volatile int &alias=value; return alias;}"),
                Arguments.of("return-and-parameter", "int &identity(int &value){return value;} int main(){return 0;}"),
                Arguments.of("abstract-parameter", "int declared(const int &); int main(){return 0;}"),
                Arguments.of("pointer-reference", "void redirect(int *&value, int *target){value=target;} int main(){return 0;}"),
                Arguments.of("array-reference", "int (&identity(int (&value)[3]))[3]{return value;} int main(){return 0;}"),
                Arguments.of("function-reference", "int operation(int x){return x;} int (&choose())(int){return operation;} int main(){return 0;}"),
                Arguments.of("reference-to-function-returning-reference", "void accept(int &(&operation)(int)); int main(){return 0;}"),
                Arguments.of("qualified-alias", "namespace N{typedef int &Ref;} int main(){int value=1; const N::Ref &alias=value;return alias;}"),
                Arguments.of("sizeof-type", "int main(){return sizeof(int &);}"),
                Arguments.of("cast-type", "int main(){int value=1; return (int &)value;}"),
                Arguments.of("global", "int value=1; int &alias=value; int main(){return alias;}"),
                Arguments.of("reference-member", "struct View { int &value; }; int main(){return 0;}"),
                Arguments.of("direct-initialization", "int main(){int value=1; int &alias(value); return alias;}"),
                Arguments.of("list-initialization", "int main(){int value=1; int &alias{value}; const int &view={alias}; return view;}"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("validSyntax")
    void parsesReferenceSyntaxButReportsAnExplicitImplementationBoundary(String name, String source) throws Exception {
        referenceCompile(name, source, true);
        var api = compiler(source, LanguageMode.CPP17_ALGORITHM);
        var parser = stage(api, Parser.class);
        var semantic = stage(api, SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(error -> error.code().equals("CPP005")),
                () -> semantic.errors().toString());
    }

    @ParameterizedTest @ValueSource(strings={
            "int main(){int value=1; int &alias=value; return 0;}",
            "int &function(int &value){return value;} int main(){return 0;}"})
    void cModeStillRejectsReferenceDeclarators(String source) {
        var api = compiler(source, LanguageMode.C);
        var parser = stage(api, Parser.class);
        api.runThrough(parser);
        assertFalse(parser.succeeded());
    }

    @ParameterizedTest @ValueSource(strings={
            "int main(){int &&value=1; return value;}",
            "int &&declared(); int main(){return 0;}"})
    void rvalueReferencesHaveAnExplicitUnsupportedDiagnostic(String source) throws Exception {
        referenceCompile("rvalue", source, true);
        var api = compiler(source, LanguageMode.CPP17_ALGORITHM);
        var parser = stage(api, Parser.class);
        api.runThrough(parser);
        assertFalse(parser.succeeded());
        assertTrue(parser.errors().stream().anyMatch(error -> error.code().equals("CPP001") && error.message().contains("右值引用")),
                () -> parser.errors().toString());
    }

    @ParameterizedTest @ValueSource(strings={
            "void &bad; int main(){return 0;}",
            "int &*bad; int main(){return 0;}",
            "int &bad[2]; int main(){return 0;}",
            "int main(){int value=1; int &const alias=value;return 0;}",
            "int main(){int value=1; int &(&alias)=value;return 0;}"})
    void malformedReferenceTypeShapesProduceSourceDiagnostics(String source) throws Exception {
        referenceCompile("malformed", source, false);
        var api = compiler(source, LanguageMode.CPP17_ALGORITHM);
        var parser = stage(api, Parser.class);
        api.runThrough(parser);
        assertFalse(parser.succeeded());
        assertFalse(parser.errors().isEmpty());
    }

    @Test void referenceTypesCannotEscapeThroughAManuallyConstructedCProgram() {
        MiniType signature = MiniType.function(MiniType.INT.referenceTo(), List.of(MiniType.INT));
        var local = new VarDeclStmt("callback", signature.pointerTo(), null, RANGE);
        var main = new FunctionDecl("main", MiniType.INT, List.of(), false, new BlockStmt(List.of(local,
                new ReturnStmt(new IntegerLiteralExpr(0, "0", RANGE), RANGE)), RANGE), false, RANGE);
        var program = new Program(List.of(), List.of(main), RANGE);
        assertSame(local, AstChildren.firstCppSyntax(program));
        var semantic = new SemanticAnalyzer(program);
        while (semantic.canNext()) semantic.step();
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(error -> error.code().equals("CPP002") && error.range().equals(RANGE)),
                () -> semantic.errors().toString());
        assertThrows(IllegalArgumentException.class, () -> new IrLowerer(program, Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new IrLowerer().lower(program));
    }

    static Stream<Arguments> referenceTypeCarriers() {
        MiniType reference = MiniType.INT.referenceTo();
        MiniType callback = MiniType.function(MiniType.INT, List.of(reference)).pointerTo();
        var literal = new IntegerLiteralExpr(0, "0", RANGE);
        return Stream.of(
                Arguments.of("return", program(new FunctionDecl("declared", reference, List.of(), false, null, false, RANGE))),
                Arguments.of("parameter", program(new FunctionDecl("declared", MiniType.INT,
                        List.of(new Parameter("argument", callback, RANGE)), false, null, false, RANGE))),
                Arguments.of("global", program(new GlobalVarDecl("object", reference, null, false, List.of(), RANGE))),
                Arguments.of("typedef", program(new TypedefDecl("Ref", reference, RANGE))),
                Arguments.of("field", program(new StructDecl("Carrier", List.of(new StructField("field", reference, RANGE)), RANGE))),
                Arguments.of("local-typedef", localProgram(new TypedefStmt("Ref", reference, RANGE))),
                Arguments.of("cast", expressionProgram(new CastExpr(reference, literal, RANGE))),
                Arguments.of("sizeof", expressionProgram(new SizeofExpr(null, reference, RANGE))),
                Arguments.of("alignof", expressionProgram(new AlignofExpr(null, reference, RANGE))),
                Arguments.of("va-arg", expressionProgram(new VaArgExpr(new NameExpr("args", RANGE), reference, RANGE))),
                Arguments.of("alignment", localProgram(new VarDeclStmt("object", MiniType.INT, null,
                        List.of(AlignmentSpec.type(reference, RANGE)), RANGE))),
                Arguments.of("flat-index", new Program(List.of(), List.of(), List.of(new TypedefDecl("Ref", reference, RANGE)),
                        List.of(), List.of(), List.of(), LanguageMode.C, RANGE)));
    }

    @ParameterizedTest(name="{0}") @MethodSource("referenceTypeCarriers")
    void everyTypeBearingAstSlotIsProtectedBeforeCoreStages(String name, Program program) {
        assertNotNull(AstChildren.firstCppSyntax(program));
        var semantic = new SemanticAnalyzer(program);
        semantic.analyze();
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(error -> error.code().equals("CPP002") && error.range().equals(RANGE)),
                () -> semantic.errors().toString());
        assertThrows(IllegalArgumentException.class, () -> new IrLowerer(program, Map.of(), Map.of()));
    }

    @Test void parsedReferenceDeclaratorsPreserveArrayFunctionAndAliasStructure() {
        var api = compiler("""
                typedef int &Ref;
                int (&arrayIdentity(int (&value)[3]))[3];
                int (&functionChoice())(int);
                int main(){int value=1; const Ref &alias(value); return alias;}
                """, LanguageMode.CPP17_ALGORITHM);
        var parser = stage(api, Parser.class);
        api.runThrough(parser);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        var source = parser.result().program();
        assertEquals(MiniType.INT.referenceTo(), source.typedefs().getFirst().type());
        var array = source.functions().getFirst();
        assertEquals(MiniType.INT.arrayOf(3).referenceTo(), array.returnType());
        assertEquals(array.returnType(), array.parameters().getFirst().type());
        var function = source.functions().get(1);
        assertEquals(MiniType.function(MiniType.INT, List.of(MiniType.INT)).referenceTo(), function.returnType());
        var alias = assertInstanceOf(VarDeclStmt.class, source.functions().get(2).body().statements().get(1));
        assertEquals(MiniType.INT.referenceTo(), alias.type());
        assertFalse(alias.type().referent().isConstQualified());
        assertInstanceOf(GroupingExpr.class, alias.initializer());
    }

    private static Program expressionProgram(Expression expression) { return localProgram(new ExprStmt(expression, RANGE)); }

    private static Program localProgram(Statement statement) {
        return program(new FunctionDecl("main", MiniType.INT, List.of(), false,
                new BlockStmt(List.of(statement, new ReturnStmt(new IntegerLiteralExpr(0, "0", RANGE), RANGE)), RANGE), false, RANGE));
    }

    private static Program program(Declaration declaration) {
        return new Program(declaration instanceof StructDecl n ? List.of(n) : List.of(), List.of(),
                declaration instanceof TypedefDecl n ? List.of(n) : List.of(),
                declaration instanceof GlobalVarDecl n ? List.of(n) : List.of(),
                declaration instanceof FunctionDecl n ? List.of(n) : List.of(), List.of(declaration), LanguageMode.C, RANGE);
    }

    private void referenceCompile(String name, String source, boolean valid) throws Exception {
        Path file = temporary.resolve(name+".cpp");
        Files.writeString(file, source);
        var result = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                "-std=c++17", "-pedantic-errors", "-fsyntax-only", file.toString()), temporary,"",Duration.ofSeconds(20),65536);
        assertFalse(result.timedOut(), result::stderr);
        if(valid) assertEquals(0,result.exitCode(),result::stderr);
        else assertNotEquals(0,result.exitCode(),"G++ must reject the malformed reference type");
    }

    private static CompilerApi compiler(String source, LanguageMode mode) {
        return new CompilerApi(new SourceFile("reference-syntax.cpp", source),mode);
    }

    private static <T> T stage(CompilerApi api, Class<T> type) {
        return api.stages().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }
}
