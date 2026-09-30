package minic.cpp;

import minic.SourceRange;
import minic.compiler.parser.CppTypeEnvironment;
import minic.compiler.parser.node.QualifiedName;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static minic.compiler.parser.CppTypeEnvironment.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class CppTypeEnvironmentTest {
    private static final SourceRange RANGE = new SourceRange(2, 3, 2, 8);
    private final CppTypeEnvironment names = new CppTypeEnvironment();

    @Test void reopenedNamespacesReuseStableQualifiedTypeIdentities() {
        names.enterNamespace(List.of("A", "B"), RANGE);
        MiniType original = names.declareStruct("S", false, RANGE);
        names.exitNamespace();
        assertEquals(MiniType.struct("::A::B::S"), original);
        assertEquals(original, lookup("A::B::S").type());
        names.enterNamespace(List.of("A", "B"), RANGE);
        assertEquals(original, names.declareStruct("S", false, true, RANGE));
        names.exitNamespace();
        assertTrue(names.diagnostics().isEmpty());
    }

    @Test void forwardSelfPointerAndAliasesShareTheSameType() {
        names.enterNamespace(List.of("List"), RANGE);
        MiniType self = names.declareStruct("Node", false, RANGE);
        names.declareTypedef("Link", self.pointerTo(), RANGE);
        assertEquals(self, lookup("Node").type());
        assertEquals(self.pointerTo(), lookup("Link").type());
        names.declareStruct("Node", false, true, RANGE);
        names.exitNamespace();
        assertEquals(self.pointerTo(), lookup("List::Link").type());
        assertTrue(names.diagnostics().isEmpty());
    }

    @Test void aliasesPreserveNestedArrayPointerAndFunctionLayers() {
        MiniType structure = names.declareStruct("S", false, true, RANGE);
        MiniType composite = MiniType.function(structure.pointerTo(), List.of(structure.arrayOf(3).pointerTo())).pointerTo();
        names.declareTypedef("Callback", composite, RANGE);
        assertSame(composite, lookup("Callback").type());
        assertEquals("::Callback", lookup("Callback").canonicalName());
    }

    @Test void localValuesHideTypesAndParameterScopesDoNotLeak() {
        names.declareTypedef("T", MiniType.INT, RANGE);
        names.enterLocalScope();
        names.declareValue("T", RANGE);
        assertEquals(VALUE, lookup("T").kind());
        assertEquals(TYPE, lookup("::T").kind());
        names.enterLocalScope();
        names.declareTypedef("T", MiniType.DOUBLE, RANGE);
        assertEquals(MiniType.DOUBLE, lookup("T").type());
        names.exitLocalScope();
        assertEquals(VALUE, lookup("T").kind());
        names.exitLocalScope();
        assertEquals(MiniType.INT, lookup("T").type());
        assertTrue(names.diagnostics().isEmpty());
    }

    @Test void ordinaryLookupAndElaboratedLookupHandleTagValueCoexistence() {
        MiniType tag = names.declareStruct("S", false, true, RANGE);
        names.declareValue("S", RANGE);
        assertEquals(VALUE, lookup("S").kind());
        assertEquals(tag, names.lookupElaborated(q("S")).type());
        assertEquals(tag, names.lookupElaborated(q("::S")).type());
        assertTrue(names.diagnostics().isEmpty());
    }

    @Test void namespaceQualifierIgnoresValuesButStopsAtInnerType() {
        names.enterNamespace(List.of("A"), RANGE);
        names.declareTypedef("T", MiniType.INT, RANGE);
        names.exitNamespace();
        names.enterLocalScope();
        names.declareValue("A", RANGE);
        assertEquals(MiniType.INT, lookup("A::T").type());
        names.enterLocalScope();
        names.declareTypedef("A", MiniType.LONG, RANGE);
        assertEquals(UNSUPPORTED_QUALIFIER, lookup("A::T").kind());
        assertEquals(MiniType.INT, lookup("::A::T").type());
    }

    @Test void importedTypesDeduplicateEntitiesAcrossDiamondsAndCycles() {
        names.enterNamespace(List.of("A"), RANGE);
        MiniType original = names.declareStruct("S", false, true, RANGE);
        names.exitNamespace();
        names.enterNamespace(List.of("B"), RANGE);
        use("A", true);
        names.exitNamespace();
        names.enterNamespace(List.of("C"), RANGE);
        use("A::S", false);
        use("B", true);
        names.exitNamespace();
        names.enterNamespace(List.of("A"), RANGE);
        use("C", true);
        names.exitNamespace();
        use("B", true);
        use("C", true);
        assertEquals(original, lookup("S").type());
        assertEquals(original, lookup("C::S").type());
        assertTrue(names.diagnostics().isEmpty());
    }

    @Test void directiveCandidatesAreInjectedAtCommonNamespaceAncestor() {
        names.enterNamespace(List.of("A"), RANGE);
        names.declareTypedef("T", MiniType.INT, RANGE);
        names.exitNamespace();
        names.enterNamespace(List.of("B"), RANGE);
        names.declareTypedef("T", MiniType.DOUBLE, RANGE);
        names.enterNamespace(List.of("Inner"), RANGE);
        use("A", true);
        assertEquals(MiniType.DOUBLE, lookup("T").type());
        names.exitNamespace();
        names.exitNamespace();
        use("A", true);
        names.declareTypedef("T", MiniType.LONG, RANGE);
        assertEquals(AMBIGUOUS, lookup("T").kind());
    }

    @Test void qualifiedLookupPrefersDirectDeclarationOverUsingDirectives() {
        names.enterNamespace(List.of("A"), RANGE);
        names.declareTypedef("T", MiniType.INT, RANGE);
        names.exitNamespace();
        names.enterNamespace(List.of("B"), RANGE);
        names.declareTypedef("T", MiniType.DOUBLE, RANGE);
        use("A", true);
        names.exitNamespace();
        assertEquals(MiniType.DOUBLE, lookup("B::T").type());
    }

    @Test void namespaceNamesBlockOuterTypesAndParticipateInAmbiguity() {
        names.declareTypedef("T", MiniType.INT, RANGE);
        names.enterNamespace(List.of("Outer", "T"), RANGE);
        names.exitNamespace();
        names.enterNamespace(List.of("Outer"), RANGE);
        assertEquals(NAMESPACE, lookup("T").kind());
        names.exitNamespace();
        use("Outer", true);
        assertEquals(AMBIGUOUS, lookup("T").kind());
        assertEquals(NAMESPACE, lookup("Outer::T").kind());
    }

    @Test void usingDeclarationsFreezeAndUsingDirectivesSeeReopenings() {
        names.enterNamespace(List.of("A"), RANGE);
        MiniType original = names.declareStruct("S", false, true, RANGE);
        names.exitNamespace();
        names.enterLocalScope();
        use("A::S", false);
        use("A", true);
        assertEquals(original, lookup("S").type());
        names.exitLocalScope();
        assertEquals(MISSING, lookup("S").kind());
        use("A", true);
        assertEquals(MISSING, lookup("Later").kind());
        names.enterNamespace(List.of("A"), RANGE);
        names.declareTypedef("Later", MiniType.LONG, RANGE);
        names.exitNamespace();
        assertEquals(MiniType.LONG, lookup("Later").type());
    }

    @Test void lookupIsPureAndMissingQualifiedTagsNeverCreateDeclarations() {
        names.enterNamespace(List.of("A"), RANGE);
        names.exitNamespace();
        assertEquals(MISSING, names.lookupElaborated(q("A::Missing")).kind());
        assertEquals(MISSING, lookup("Missing").kind());
        assertEquals(MISSING, lookup("Unknown::Missing").kind());
        assertTrue(names.diagnostics().isEmpty());
        use("A::Missing", false);
        assertEquals("CPP003", names.diagnostics().getFirst().code());
        assertEquals(RANGE, names.diagnostics().getFirst().range());
    }

    @Test void duplicateDefinitionsAndUnionKindMismatchesAreDiagnosed() {
        names.declareStruct("S", false, true, RANGE);
        names.declareStruct("S", false, RANGE);
        assertTrue(names.diagnostics().isEmpty());
        names.declareStruct("S", false, true, RANGE);
        names.declareStruct("S", true, RANGE);
        assertEquals(2, names.diagnostics().size());
        assertTrue(names.diagnostics().stream().allMatch(d -> d.code().equals("CPP004") && d.range().equals(RANGE)));
    }

    @Test void conflictingNamespacesAliasesAndValuesAreDiagnosedAtDeclaration() {
        names.declareTypedef("T", MiniType.INT, RANGE);
        names.declareTypedef("T", MiniType.INT, RANGE);
        assertTrue(names.diagnostics().isEmpty());
        names.declareTypedef("T", MiniType.DOUBLE, RANGE);
        names.declareValue("T", RANGE);
        names.enterNamespace(List.of("T"), RANGE);
        names.exitNamespace();
        assertEquals(3, names.diagnostics().size());
    }

    @Test void unionTypesKeepBackendMarkerAndScopeIdentitiesRemainDistinct() {
        names.enterNamespace(List.of("A"), RANGE);
        MiniType union = names.declareStruct("U", true, true, RANGE);
        names.exitNamespace();
        assertEquals(MiniType.struct("$union$::A::U"), union);
        assertEquals("::A::U", lookup("A::U").canonicalName());
        names.enterLocalScope();
        MiniType first = names.declareStruct("S", false, RANGE);
        names.exitLocalScope();
        names.enterLocalScope();
        MiniType second = names.declareStruct("S", false, RANGE);
        names.exitLocalScope();
        assertNotEquals(first, second);
        assertEquals(MISSING, lookup("S").kind());
    }

    @Test void rootScopesCannotBePoppedAndNamespacesCannotBeEnteredInsideLocals() {
        assertThrows(IllegalStateException.class, names::exitLocalScope);
        assertThrows(IllegalStateException.class, names::exitNamespace);
        names.enterLocalScope();
        assertThrows(IllegalStateException.class, () -> names.enterNamespace(List.of("A"), RANGE));
        names.exitLocalScope();
    }

    @Test void equivalentAliasesFromDifferentNamespacesDoNotBecomeAmbiguous() {
        names.enterNamespace(List.of("A"), RANGE);
        names.declareTypedef("T", MiniType.INT, RANGE);
        names.exitNamespace();
        names.enterNamespace(List.of("B"), RANGE);
        names.declareTypedef("T", MiniType.INT, RANGE);
        names.exitNamespace();
        use("A", true);
        use("B", true);
        assertEquals(MiniType.INT, lookup("T").type());
        assertEquals(TYPE, lookup("T").kind());
    }

    @Test void usingCanIntroduceTagsAlongsideValuesInEitherOrder() {
        names.enterNamespace(List.of("A"), RANGE);
        MiniType imported = names.declareStruct("S", false, true, RANGE);
        names.declareValue("V", RANGE);
        names.exitNamespace();
        names.declareValue("S", RANGE);
        use("A::S", false);
        MiniType own = names.declareStruct("V", false, true, RANGE);
        use("A::V", false);
        assertAll(
                () -> assertEquals(VALUE, lookup("S").kind()),
                () -> assertEquals(imported, names.lookupElaborated(q("S")).type()),
                () -> assertEquals(VALUE, lookup("V").kind()),
                () -> assertEquals(own, names.lookupElaborated(q("V")).type()),
                () -> assertTrue(names.diagnostics().isEmpty(), () -> names.diagnostics().toString()));
    }

    @Test void tagImportedByUsingCanBeForwardDeclaredAndCompleted() {
        names.enterNamespace(List.of("A"), RANGE);
        MiniType tag = names.declareStruct("S", false, RANGE);
        names.exitNamespace();
        use("A::S", false);
        assertEquals(tag, names.declareStruct("S", false, RANGE));
        assertEquals(tag, names.declareStruct("S", false, true, RANGE));
        assertTrue(names.diagnostics().isEmpty(), () -> names.diagnostics().toString());
        names.enterNamespace(List.of("A"), RANGE);
        names.declareStruct("S", false, true, RANGE);
        assertEquals(1, names.diagnostics().size());
    }

    @Test void elaboratedLookupDoesNotTreatAliasesAsTagsOrCreateMissingNames() {
        MiniType tag = names.declareStruct("S", false, RANGE);
        names.declareTypedef("Alias", tag, RANGE);
        assertEquals(UNSUPPORTED_QUALIFIER, names.lookupElaborated(q("Alias")).kind());
        assertEquals(MISSING, names.lookupElaborated(q("NoTag")).kind());
        assertTrue(names.diagnostics().isEmpty());
    }

    private CppTypeEnvironment.Lookup lookup(String name) { return names.lookup(q(name)); }
    private void use(String name, boolean directive) { names.registerUsing(q(name), directive, RANGE); }
    private static QualifiedName q(String name) {
        boolean global = name.startsWith("::");
        return new QualifiedName(global, List.of((global ? name.substring(2) : name).split("::")), RANGE);
    }
}
