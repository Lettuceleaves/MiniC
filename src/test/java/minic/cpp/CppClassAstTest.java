package minic.cpp;

import minic.SourceRange;
import minic.compiler.LanguageMode;
import minic.compiler.ir.IrLowerer;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.AstNode;
import minic.compiler.parser.node.Declaration;
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

/** Manual AST contracts keep this commit independent from parser grammar changes. */
class CppClassAstTest {
    private static final SourceRange RECORD = range(2, 0, 50);
    private static final SourceRange KEY = range(2, 0, 6);
    private static final SourceRange FIELD = range(2, 8, 14);
    private static final SourceRange LABEL = range(3, 0, 8);
    private static final SourceRange METHOD = range(4, 0, 30);
    private static final SourceRange METHOD_NAME = range(4, 4, 8);
    private static final SourceRange THIS = range(4, 15, 19);
    private static final SourceRange MAIN = range(10, 0, 24);

    @Test void legacyStructConstructorsKeepCShapeAndExactFieldIdentity() {
        var field = field();
        for (StructDecl node : List.of(new StructDecl("Record", List.of(field), RECORD),
                new StructDecl("Record", List.of(field), true, RECORD),
                new StructDecl("Record", List.of(field), true, false, RECORD))) {
            assertNull(node.cppInfo());
            assertSame(field, node.fields().getFirst());
            assertSame(field, AstChildren.of(node).getFirst());
        }
        var union = new StructDecl("$union$Data", List.of(field), true, true, RECORD);
        assertTrue(union.union());
        assertNull(union.cppInfo());
    }

    @Test void metadataIsImmutableAndChildrenPreserveMemberOrderAndOriginalNodes() {
        var field = field();
        var fieldMember = new FieldMember(field);
        var thisNode = new ThisExpr(THIS);
        var method = new MethodMember(method(thisNode), METHOD_NAME);
        var label = new AccessLabel(Access.PUBLIC, LABEL);
        var members = new ArrayList<CppMember>(List.of(fieldMember, label, method));
        var fields = new ArrayList<>(List.of(field));
        var info = new CppRecordInfo(RecordKey.STRUCT, members, KEY);
        var record = new StructDecl("Record", fields, true, false, info, RECORD);
        members.clear();
        fields.clear();
        assertEquals(List.of(fieldMember, label, method), info.members());
        assertThrows(UnsupportedOperationException.class, () -> info.members().clear());
        assertThrows(UnsupportedOperationException.class, () -> record.fields().clear());
        assertSame(info, record.cppInfo());
        assertEquals(KEY, info.keyRange());
        assertEquals(List.of(fieldMember, label, method), AstChildren.of(record));
        assertSame(field, AstChildren.of(fieldMember).getFirst());
        assertSame(method.method(), AstChildren.of(method).getFirst());
        assertSame(FIELD, fieldMember.range());
        assertSame(METHOD, method.range());
        assertSame(METHOD_NAME, method.nameRange());
        assertSame(THIS, thisNode.range());
        assertTrue(AstChildren.of(label).isEmpty());
        assertTrue(AstChildren.of(thisNode).isEmpty());
        List<AstNode> walked = new ArrayList<>();
        walk(record, walked);
        assertEquals(1, walked.stream().filter(n -> n == field).count());
        assertEquals(1, walked.stream().filter(n -> n == method.method()).count());
        assertEquals(1, walked.stream().filter(n -> n == thisNode).count());
    }

    @Test void metadataFieldProjectionRequiresOrderAndObjectIdentity() {
        var first = field();
        var equalCopy = field();
        var second = new StructField("other", MiniType.LONG_LONG, FIELD);
        var info = new CppRecordInfo(RecordKey.STRUCT, List.of(new FieldMember(first), new FieldMember(second)), KEY);
        assertEquals(first, equalCopy);
        assertThrows(IllegalArgumentException.class, () -> new StructDecl("Record", List.of(first), true, false, info, RECORD));
        assertThrows(IllegalArgumentException.class, () -> new StructDecl("Record", List.of(second, first), true, false, info, RECORD));
        assertThrows(IllegalArgumentException.class, () -> new StructDecl("Record", List.of(equalCopy, second), true, false, info, RECORD));
        assertThrows(NullPointerException.class, () -> new FieldMember(null));
        assertThrows(NullPointerException.class, () -> new MethodMember(method(null), null));
        assertThrows(NullPointerException.class, () -> new AccessLabel(null, LABEL));
        assertThrows(NullPointerException.class, () -> new ThisExpr(null));
    }

    @Test void dataOnlyStructMetadataBindsToCoreWithoutLosingTheOriginalFieldMapping() {
        var field = field();
        var record = record(RecordKey.STRUCT, List.of(new FieldMember(field)));
        var source = program(LanguageMode.CPP17_ALGORITHM, record, main(null));
        var binding = CppNameBinder.bind(source);
        assertTrue(binding.diagnostics().isEmpty(), () -> binding.diagnostics().toString());
        var core = binding.program().structs().getFirst();
        assertNull(core.cppInfo());
        assertSame(core, binding.sourceToCore().get(record));
        assertSame(core.fields().getFirst(), binding.sourceToCore().get(field));
        assertEquals(FIELD, core.fields().getFirst().range());
        assertSame(field, record.fields().getFirst());
        assertNotNull(record.cppInfo());
        var semantic = analyze(source);
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        assertNotNull(new IrLowerer(source, semantic.semanticResult()).lower());
    }

    @Test void classKeyIncludingForwardDeclarationsLowersAfterDataAccessMetadataIsRecorded() {
        var classDefinition = record(RecordKey.CLASS, List.of(new FieldMember(field())));
        var forward = new StructDecl("Record", List.of(), false, false,
                new CppRecordInfo(RecordKey.CLASS, List.of(), KEY), RECORD);
        var binding = CppNameBinder.bind(program(LanguageMode.CPP17_ALGORITHM, forward, classDefinition, main(null)));
        assertTrue(binding.diagnostics().isEmpty(), () -> binding.diagnostics().toString());
        assertEquals(2, binding.program().structs().size());
        assertEquals(binding.program().structs().getFirst().name(), binding.program().structs().getLast().name());
        assertFalse(binding.program().structs().getFirst().definition());
        assertTrue(binding.program().structs().getLast().definition());
        assertSame(binding.program().structs().getLast(), binding.sourceToCore().get(classDefinition));
    }

    @Test void unusedMethodCannotDisappearDuringCoreBinding() {
        var method = new MethodMember(method(null), METHOD_NAME);
        var record = record(RecordKey.STRUCT, List.of(method));
        rejectedByBinder(record, METHOD_NAME);
        var source = program(LanguageMode.CPP17_ALGORITHM, record, main(null));
        var semantic = analyze(source);
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d -> d.code().equals("CPP005") && d.range().equals(METHOD_NAME)));
        assertFalse(semantic.program().structs().stream().anyMatch(s -> s.name().equals("Record")),
                "An unsupported record must not be emitted as a misleading empty core record");
    }

    @ParameterizedTest @EnumSource(Access.class)
    void accessLabelsAreConsumedBeforeLoweringDataOnlyRecords(Access access) {
        var record = record(RecordKey.STRUCT, List.of(new AccessLabel(access, LABEL)));
        var binding = CppNameBinder.bind(program(LanguageMode.CPP17_ALGORITHM, record, main(null)));
        assertTrue(binding.diagnostics().isEmpty(), () -> binding.diagnostics().toString());
        assertNull(binding.program().structs().getFirst().cppInfo());
        assertSame(LABEL, record.cppInfo().members().getFirst().range());
    }

    @Test void thisGetsAnExplicitCppDiagnosticBeforeCoreExpressionAnalysis() {
        var source = program(LanguageMode.CPP17_ALGORITHM, main(new ThisExpr(THIS)));
        var result = CppNameBinder.bind(source);
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("CPP005")
                && d.message().contains("this") && d.range().equals(THIS)), () -> result.diagnostics().toString());
        assertFalse(analyze(source).succeeded());
    }

    @Test void cModeRejectsEveryCppMetadataShapeAndThisAtSourceRanges() {
        for (StructDecl record : List.of(record(RecordKey.STRUCT, List.of(new FieldMember(field()))),
                record(RecordKey.CLASS, List.of()), record(RecordKey.STRUCT, List.of(new MethodMember(method(null), METHOD_NAME))))) {
            var semantic = analyze(program(LanguageMode.C, record, main(null)));
            assertFalse(semantic.succeeded());
            assertTrue(semantic.errors().stream().anyMatch(d -> d.code().equals("CPP002") && d.range().equals(KEY)),
                    () -> semantic.errors().toString());
        }
        var semantic = analyze(program(LanguageMode.C, main(new ThisExpr(THIS))));
        assertTrue(semantic.errors().stream().anyMatch(d -> d.code().equals("CPP002") && d.range().equals(THIS)));
    }

    @Test void rawIrEntryPointsRejectCppNodesEvenWhenModeOrOrderedIndexIsMalformed() {
        var record = record(RecordKey.STRUCT, List.of(new FieldMember(field())));
        var main = main(null);
        var hiddenRecord = new Program(List.of(record), List.of(), List.of(), List.of(), List.of(main),
                List.of(main), LanguageMode.C, MAIN);
        var hiddenThis = new Program(List.of(), List.of(), List.of(), List.of(), List.of(main(new ThisExpr(THIS))),
                List.of(main), LanguageMode.C, MAIN);
        for (Program source : List.of(program(LanguageMode.CPP17_ALGORITHM, record, main),
                program(LanguageMode.C, record, main), program(LanguageMode.C, main(new ThisExpr(THIS))), hiddenRecord, hiddenThis)) {
            assertThrows(IllegalArgumentException.class, () -> new IrLowerer(source, Map.of(), Map.of()));
            assertThrows(IllegalArgumentException.class, () -> new IrLowerer().lower(source));
        }
        assertTrue(analyze(hiddenRecord).errors().stream().anyMatch(d -> d.code().equals("CPP002")));
        assertTrue(analyze(hiddenThis).errors().stream().anyMatch(d -> d.code().equals("CPP002")));
    }

    private static StructField field() { return new StructField("value", MiniType.INT, FIELD); }
    private static StructDecl record(RecordKey key, List<CppMember> members) {
        var fields = members.stream().filter(FieldMember.class::isInstance).map(FieldMember.class::cast).map(FieldMember::field).toList();
        return new StructDecl("Record", fields, true, false, new CppRecordInfo(key, members, KEY), RECORD);
    }
    private static FunctionDecl method(ThisExpr expression) {
        return new FunctionDecl("read", MiniType.INT, List.of(), false,
                new BlockStmt(List.of(new ReturnStmt(expression == null ? new IntegerLiteralExpr(0, "0", METHOD) : expression, METHOD)), METHOD), false, METHOD);
    }
    private static FunctionDecl main(ThisExpr expression) {
        return new FunctionDecl("main", MiniType.INT, List.of(), false,
                new BlockStmt(List.of(new ReturnStmt(expression == null ? new IntegerLiteralExpr(0, "0", MAIN) : expression, MAIN)), MAIN), false, MAIN);
    }
    private static Program program(LanguageMode mode, Declaration... declarations) {
        List<Declaration> ordered = List.of(declarations);
        return new Program(ordered.stream().filter(StructDecl.class::isInstance).map(StructDecl.class::cast).toList(),
                List.of(), List.of(), List.of(), ordered.stream().filter(FunctionDecl.class::isInstance).map(FunctionDecl.class::cast).toList(),
                ordered, mode, MAIN);
    }
    private static SemanticAnalyzer analyze(Program source) {
        var semantic = new SemanticAnalyzer(source);
        semantic.analyze();
        return semantic;
    }
    private static void rejectedByBinder(StructDecl record, SourceRange range) {
        var result = CppNameBinder.bind(program(LanguageMode.CPP17_ALGORITHM, record, main(null)));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("CPP005") && d.range().equals(range)),
                () -> result.diagnostics().toString());
        assertTrue(result.program().structs().isEmpty());
    }
    private static void walk(AstNode node, List<AstNode> target) {
        target.add(node);
        AstChildren.of(node).forEach(child -> walk(child, target));
    }
    private static SourceRange range(int line, int from, int to) { return new SourceRange(line, from, line, to); }
}
