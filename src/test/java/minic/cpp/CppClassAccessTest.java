package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.cpp.CppNameBinder;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential")
@Timeout(60)
final class CppClassAccessTest {
    @TempDir Path temporary;

    @ParameterizedTest @MethodSource("validPrograms")
    void supportedClassObjectsMatchCpp(String name, String content) throws Exception {
        var report = new CppDifferentialHarness(temporary, CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(), LanguageMode.CPP17_ALGORITHM).run(name, content, "");
        assertTrue(report.passed(), report::describe);
    }

    static Stream<Arguments> validPrograms() {
        return Stream.of(
                Arguments.of("public-layout", """
                        #include <stdio.h>
                        namespace Data {
                            class Packet;
                            struct Packet { int tag; double weight; };
                            struct Other;
                            class Other { public: Packet packet; int tail; };
                        }
                        Data::Packet copy(Data::Packet packet) { packet.tag += 4; return packet; }
                        int main() {
                            Data::Packet original = {3, 2.5};
                            Data::Packet changed = copy(original);
                            Data::Other items[2] = {{{1, 1.5}, 2}, {{5, 3.5}, 9}};
                            Data::Other *p = items;
                            (p + 1)->packet.tag += changed.tag;
                            printf("%d %d %d %d\\n", original.tag, changed.tag, items[1].packet.tag, (int)sizeof(Data::Other));
                            return original.tag == 3 && changed.tag == 7 && items[1].packet.tag == 12
                                && items[1].packet.weight == 3.5 && items[1].tail == 9 ? 0 : 1;
                        }
                        """),
                Arguments.of("private-empty-and-copy", """
                        class Hidden { int secret; public: int visible; };
                        Hidden global = {};
                        struct Outer { Hidden item; int tail; };
                        int main() {
                            Hidden first = {}; first.visible = 7;
                            Hidden plain = first;
                            Hidden braced = {first};
                            Hidden items[2] = {{}, {first}};
                            Outer outer = {{first}, 9};
                            Outer zero = {};
                            return global.visible + items[0].visible + zero.item.visible == 0
                                && plain.visible == 7 && braced.visible == 7 && items[1].visible == 7
                                && outer.item.visible == 7 && outer.tail == 9 ? 0 : 1;
                        }
                        """),
                Arguments.of("public-promoted-union", """
                        class Box { public: union { int value; int pair[2]; }; int tail; };
                        int main() { Box box = {}; box.pair[0] = 3; box.pair[1] = 4; box.tail = 5;
                            return box.pair[0] + box.pair[1] + box.tail == 12 ? 0 : 1; }
                        """));
    }

    @ParameterizedTest
    @ValueSource(strings = {"b.secret", "p->secret", "(b).secret", "((Box *)p)->secret", "p[0].secret",
            "make().secret", "pointer()->secret", "sizeof(b.secret)", "sizeof(p->secret)",
            "(true ? b : b).secret", "(0, b).secret", "(*p).secret", "(&b)->secret", "(&p[0])[0].secret",
            "(b = b).secret", "sizeof(&b.secret)"})
    void everyReceiverShapeChecksPrivateAccess(String expression) throws Exception {
        assertInvalid("class Box { public: int exposed; private: int secret; }; Box make(); Box *pointer(); "
                + "int read(Box b, Box *p) { return " + expression + "; } int main() { return 0; }", "访问");
    }

    @ParameterizedTest
    @ValueSource(strings = {"class Box { int secret; };", "struct Box { protected: int secret; };",
            "struct Box { public: int visible; private: int secret; };",
            "class Box { union { int secret; }; };",
            "struct Box { private: union { union { int secret; }; }; };"})
    void defaultsLabelsAndPromotedAnonymousUnionsCannotExposeRestrictedFields(String declaration) throws Exception {
        assertInvalid(declaration + " int read(Box b) { return b.secret; } int main() { return 0; }", "访问");
    }

    @ParameterizedTest @MethodSource("invalidInitializers")
    void initializationCannotBypassNonPublicDataMembers(String initializer) throws Exception {
        assertInvalid("class Hidden { int secret; }; struct Outer { Hidden item; int tail; }; "
                + "int main() { " + initializer + " return 0; }", "初始化");
    }

    static Stream<String> invalidInitializers() {
        return Stream.of("Hidden value = {1};", "Hidden values[2] = {{1}, {}};", "Hidden values[2] = {1, 2};",
                "Outer value = {{1}, 2};", "Outer value = {1, 2};", "Outer values[1] = {{{1}, 2}};",
                "Hidden value = {.secret = 1};", "Outer value = {.item = {1}};", "Outer value = {.item.secret = 1};");
    }

    @Test void globalNonPublicListInitializationIsRejected() throws Exception {
        assertInvalid("class Hidden { int secret; }; Hidden global = {1}; int main() { return 0; }", "初始化");
    }

    @Test void classAndStructForwardKeysDoNotOverrideDefinitionAccessDefaults() throws Exception {
        assertInvalid("struct Box; class Box { int secret; }; int read(Box b) { return b.secret; } int main(){return 0;}", "访问");
        var compiler = compiler("class Box; struct Box { int value; }; int main(){ Box box; box.value=7; return box.value-7; }");
        assertNotNull(compiler.runToIr());
    }

    @Test void dataOnlyMetadataLowersWithoutLosingSourceMappingsOrFieldOrder() {
        var compiler = compiler("class Box { int hidden; public: int visible; protected: int guarded; }; int main(){return 0;}");
        var parser = compiler.stages().stream().filter(Parser.class::isInstance).map(Parser.class::cast).findFirst().orElseThrow();
        compiler.runThrough(parser);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        var source = parser.result().program().structs().getFirst();
        var bound = CppNameBinder.bind(parser.result().program());
        assertTrue(bound.diagnostics().isEmpty(), () -> bound.diagnostics().toString());
        var core = bound.program().structs().getFirst();
        assertNull(core.cppInfo());
        assertSame(core, bound.sourceToCore().get(source));
        assertEquals(List.of("hidden", "visible", "guarded"), core.fields().stream().map(StructField::name).toList());
        for (int i = 0; i < source.fields().size(); i++) {
            assertSame(core.fields().get(i), bound.sourceToCore().get(source.fields().get(i)));
            assertEquals(source.fields().get(i).range(), core.fields().get(i).range());
        }
        assertNotNull(source.cppInfo());
    }

    @Test void memberMethodsBindWhileThisOutsideAMethodIsRejected() {
        var valid = compiler("class Box { public: int method() { return 0; } }; int main(){return 0;}");
        var bound = semantic(valid);
        valid.runThrough(bound);
        assertTrue(bound.succeeded(), () -> bound.errors().toString());
        assertTrue(bound.semanticResult().displayNames().containsValue("Box::method"));
        var invalid = compiler("int main() { return this; }");
        var rejected = semantic(invalid);
        invalid.runThrough(rejected);
        assertTrue(rejected.errors().stream().anyMatch(d -> d.code().equals("CPP004") && d.message().contains("this")),
                () -> rejected.errors().toString());
    }

    @Test void namespaceQualifiedTypesAndAliasesRetainIndependentAccessPolicies() throws Exception {
        String declarations = "namespace A { class Box { public: int value; }; } "
                + "namespace B { class Box { int value; }; } typedef A::Box Public; typedef B::Box Private; ";
        var valid = compiler(declarations + "using A::Box; int main(){ Public a; Box b; a.value=2; b.value=3; return a.value+b.value-5; }");
        assertNotNull(valid.runToIr());
        assertInvalid(declarations + "using B::Box; int read(Private a, Box b){return a.value+b.value;} int main(){return 0;}", "访问");
    }

    @Test void unknownFieldStillProducesAnErrorAfterClassDataSupport() {
        var compiler = compiler("class Box { public: int value; }; int main(){ Box box; return box.missing; }");
        var semantic = semantic(compiler);
        compiler.runThrough(semantic);
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d -> d.message().contains("missing")), () -> semantic.errors().toString());
        assertTrue(semantic.errors().stream().noneMatch(d -> d.message().contains("private")), () -> semantic.errors().toString());
    }

    @Test void listCopyRetainsDistinctSourceMappingsForTheListAndItsOperand() {
        var compiler = compiler("class Hidden { int secret; }; int main(){ Hidden first = {}; Hidden copy = {first}; return 0; }");
        var parser = compiler.stages().stream().filter(Parser.class::isInstance).map(Parser.class::cast).findFirst().orElseThrow();
        compiler.runThrough(parser);
        var declaration = (minic.compiler.parser.node.Statement.VarDeclStmt) parser.result().program().functions().getFirst().body().statements().get(1);
        var list = (minic.compiler.parser.node.Expression.AggregateInitExpr) declaration.initializer();
        var binding = CppNameBinder.bind(parser.result().program());
        assertTrue(binding.diagnostics().isEmpty(), () -> binding.diagnostics().toString());
        var coreList = binding.sourceToCore().get(list);
        var coreOperand = binding.sourceToCore().get(list.values().getFirst());
        assertNotSame(coreList, coreOperand);
        var group = assertInstanceOf(minic.compiler.parser.node.Expression.GroupingExpr.class, coreList);
        assertSame(coreOperand, group.expression());
        assertEquals(list.range(), group.range());
        assertEquals(list.values().getFirst().range(), coreOperand.range());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Hidden value;", "Hidden value = {};", "Hidden values[2] = {};",
            "Outer value = {};", "Outer value = {{}, 1};", "Outer values[2] = {};", "Outer value;",
            "Outer value = {.tail = 1};"})
    void implicitConstructionWithConstSubobjectsRequiresExplicitSupport(String declaration) throws Exception {
        assertInvalid("class Hidden { const int secret; public: int visible; }; struct Outer { Hidden item; int tail; }; "
                + "int main(){ " + declaration + " return 0; }", "初始化");
    }

    @Test void globalImplicitConstructionWithConstMembersCannotBecomeZeroFilledCStorage() throws Exception {
        assertInvalid("class Hidden { const int secret; public: int visible; }; Hidden global = {}; int main(){return 0;}", "初始化");
    }

    @Test void constTypesRemainUsableThroughDeclarationsPointersAndCopyInitialization() {
        var compiler = compiler("class Hidden { const int secret; public: int visible; }; Hidden *pointer; extern Hidden external; "
                + "Hidden copy(Hidden value){ Hidden plain=value; Hidden list={value}; return list; } "
                + "class Pointee { const int *pointer; public: int value; }; "
                + "int main(){ Pointee object={}; return object.value; }");
        assertNotNull(compiler.runToIr());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Frozen", "Outer"})
    void wholeObjectAssignmentCannotBypassConstSubobjects(String type) throws Exception {
        assertInvalid("class Frozen { const int key; }; struct Outer { Frozen item; }; int assign("
                + type + " a, " + type + " b){ a=b; return 0; } int main(){return 0;}", "赋值");
    }

    @Test void nestedDesignatorsCannotInitializeOnlyThePublicPartOfANonAggregate() throws Exception {
        assertInvalid("class Hidden { int secret; public: int visible; }; struct Outer { Hidden item; }; "
                + "int main(){ Outer value={.item.visible=1}; return 0; }", "初始化");
    }

    private void assertInvalid(String content, String diagnosticWord) throws Exception {
        Path file = temporary.resolve("access.cpp");
        Files.writeString(file, content);
        var reference = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()), "-std=c++17", "-fsyntax-only", file.toString()),
                temporary, "", Duration.ofSeconds(20), 65536);
        assertFalse(reference.timedOut(), reference::stderr);
        assertFalse(reference.outputExceeded(), reference::stderr);
        assertNotEquals(0, reference.exitCode(), () -> "Expected G++ rejection: " + content);
        var compiler = compiler(content);
        var semantic = semantic(compiler);
        compiler.runThrough(semantic);
        assertFalse(semantic.succeeded(), content);
        assertTrue(semantic.errors().stream().anyMatch(d -> (d.code().equals("CPP004")
                || !diagnosticWord.equals("访问") && d.code().equals("CPP005"))
                && d.message().contains(diagnosticWord)), () -> content + "\n" + semantic.errors());
    }
    private static CompilerApi compiler(String content) { return new CompilerApi(new SourceFile("access.cpp", content), LanguageMode.CPP17_ALGORITHM); }
    private static SemanticAnalyzer semantic(CompilerApi compiler) {
        return compiler.stages().stream().filter(SemanticAnalyzer.class::isInstance).map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
    }
}
