package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.ir.instruction.MemoryInstruction;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Statement;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.SemanticResult;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppDifferentialHarness.Backend;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Every update is sequenced before its observation; reference programs contain no unsequenced updates. */
@Tag("cpp-differential")
@Timeout(60)
final class UpdateExpressionTest {
    @TempDir Path temporary;

    record Program(String name, String source, String output) {}

    static Stream<Arguments> validPrograms() {
        return Stream.of(
                new Program("prefix-values", """
                        #include <stdio.h>
                        int main() {
                            int value = 5;
                            int increased = ++value;
                            int decreased = --value;
                            printf("%d %d %d\\n", increased, decreased, value);
                            return 0;
                        }
                        """, "6 5 5\n"),
                new Program("postfix-values", """
                        #include <stdio.h>
                        int main() {
                            int value = 5;
                            int increased = value++;
                            int decreased = value--;
                            printf("%d %d %d\\n", increased, decreased, value);
                            return 0;
                        }
                        """, "5 6 5\n"),
                new Program("pointer-step-and-result", """
                        #include <stdio.h>
                        struct Item { int first; int second; };
                        int main() {
                            struct Item items[4] = {{10, 11}, {20, 21}, {30, 31}, {40, 41}};
                            struct Item *pointer = items + 1;
                            struct Item *old_increase = pointer++;
                            struct Item *new_increase = ++pointer;
                            struct Item *old_decrease = pointer--;
                            struct Item *new_decrease = --pointer;
                            printf("%d %d %d %d %d\\n", old_increase->first, new_increase->first,
                                old_decrease->first, new_decrease->first, pointer->second);
                            return 0;
                        }
                        """, "20 40 40 20 21\n"),
                new Program("floating-update-values", """
                        #include <stdio.h>
                        int main() {
                            float value = 2.5f;
                            float old_increase = value++;
                            float new_increase = ++value;
                            float old_decrease = value--;
                            float new_decrease = --value;
                            printf("%d %d %d %d %d\\n", (int)(old_increase * 10), (int)(new_increase * 10),
                                (int)(old_decrease * 10), (int)(new_decrease * 10), (int)(value * 10));
                            double wide = 2.5;
                            double old_wide = wide++;
                            double new_wide = --wide;
                            printf("%d %d %d\\n", (int)(old_wide * 10), (int)(new_wide * 10), (int)(wide * 10));
                            return 0;
                        }
                        """, "25 45 45 25 25\n25 25 25\n"),
                new Program("narrow-unsigned-update-values", """
                        #include <stdio.h>
                        int main() {
                            unsigned char value = 255;
                            int before = value++;
                            int after = value;
                            int increased = ++value;
                            int decreased = --value;
                            printf("%d %d %d %d %d\\n", before, after, increased, decreased, value);
                            return 0;
                        }
                        """, "255 0 1 0 0\n"),
                receiverProgram("index-receiver-once", """
                        int values[1] = {10};
                        int next() { calls = calls + 1; return 0; }
                        """, "values[next()]", "values[0]"),
                new Program("prefix-dereference-receiver-once", """
                        #include <stdio.h>
                        int calls = 0;
                        int value = 10;
                        int *receiver() { calls = calls + 1; return &value; }
                        int main() {
                            int result = ++*receiver();
                            printf("%d %d %d\\n", calls, result, value);
                            calls = 0; result = --*receiver();
                            printf("%d %d %d\\n", calls, result, value);
                            return 0;
                        }
                        """, "1 11 11\n1 10 10\n"),
                receiverProgram("grouped-dereference-receiver-once", """
                        int value = 10;
                        int *receiver() { calls = calls + 1; return &value; }
                        """, "(*receiver())", "value"),
                receiverProgram("member-receiver-once", """
                        struct Box { int value; };
                        struct Box box = {10};
                        struct Box *receiver() { calls = calls + 1; return &box; }
                        """, "receiver()->value", "box.value"),
                new Program("while-pointer-postincrement", """
                        #include <stdio.h>
                        int main() {
                            char text[5] = {'a', 'b', 'c', '\\0', '\\0'};
                            char *pointer = text;
                            int length = 0;
                            while (*pointer++) length = length + 1;
                            printf("%d %d\\n", length, (int)(pointer - text));
                            return 0;
                        }
                        """, "3 4\n"))
                .flatMap(program -> Stream.of(LanguageMode.values())
                        .map(mode -> Arguments.of(program.name(), mode, program.source(), program.output())));
    }

    private static Program receiverProgram(String name, String declarations, String target, String stored) {
        // Each helper always selects the same valid object, so a buggy duplicate evaluation is
        // observable through calls without turning the test into an out-of-bounds access.
        String source = "#include <stdio.h>\nint calls = 0;\n" + declarations + "int main() {\n"
                + "int result = ++" + target + ";\n"
                + observation(stored)
                + "calls = 0; result = --" + target + ";\n"
                + observation(stored)
                + "calls = 0; result = " + target + "++;\n"
                + observation(stored)
                + "calls = 0; result = " + target + "--;\n"
                + observation(stored)
                + "return 0;\n}\n";
        return new Program(name, source, "1 11 11\n1 10 10\n1 10 11\n1 11 10\n");
    }

    private static String observation(String stored) {
        return "printf(\"%d %d %d\\n\", calls, result, " + stored + ");\n";
    }

    @ParameterizedTest(name = "{0} [{1}]")
    @MethodSource("validPrograms")
    void updateValuesAndReceiverEffectsMatchTheReference(String name, LanguageMode mode,
                                                        String source, String output) throws Exception {
        var harness = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()), CppDifferentialHarness.Limits.defaults(), mode);
        var report = harness.run(name + "-" + mode, source, "");
        var reference = report.outcomes().get(Backend.GXX);
        assertEquals(CppDifferentialHarness.Status.OK, reference.status(), report::describe);
        assertEquals(output, reference.stdout().replace("\r\n", "\n"), report::describe);
        assertTrue(report.passed(), report::describe);
    }

    static Stream<Arguments> invalidPrograms() {
        Stream<Arguments> common = Stream.of(
                new Program("const-prefix", "int main(){const int value=3; ++value; return 0;}", ""),
                new Program("const-postfix", "int main(){const int value=3; value--; return 0;}", ""),
                new Program("const-pointer-prefix", "int main(){int value=3; int *const pointer=&value; ++pointer; return 0;}", ""),
                new Program("void-pointer-prefix-increase", "int main(){int value=3; void *pointer=&value; ++pointer; return 0;}", ""),
                new Program("void-pointer-prefix-decrease", "int main(){int value=3; void *pointer=&value; --pointer; return 0;}", ""),
                new Program("void-pointer-postfix-increase", "int main(){int value=3; void *pointer=&value; pointer++; return 0;}", ""),
                new Program("void-pointer-postfix-decrease", "int main(){int value=3; void *pointer=&value; pointer--; return 0;}", ""),
                new Program("incomplete-pointer-prefix-increase", "struct Item; int main(){struct Item *pointer=0; ++pointer; return 0;}", ""),
                new Program("incomplete-pointer-prefix-decrease", "struct Item; int main(){struct Item *pointer=0; --pointer; return 0;}", ""),
                new Program("incomplete-pointer-postfix-increase", "struct Item; int main(){struct Item *pointer=0; pointer++; return 0;}", ""),
                new Program("incomplete-pointer-postfix-decrease", "struct Item; int main(){struct Item *pointer=0; pointer--; return 0;}", ""),
                new Program("literal-postfix", "int main(){return 3++;}", ""),
                new Program("sum-prefix", "int main(){int value=3; return ++(value+1);}", ""),
                new Program("call-postfix", "int value(){return 3;} int main(){return value()--;}", ""))
                .flatMap(program -> Stream.of(LanguageMode.values())
                        .map(mode -> Arguments.of(program.name(), mode, program.source())));
        Stream<Arguments> booleanUpdates = Stream.of("++value", "--value", "value++", "value--")
                .map(update -> Arguments.of("cpp-bool-" + update, LanguageMode.CPP17_ALGORITHM,
                        "int main(){bool value=false; " + update + "; return 0;}"));
        return Stream.concat(common, booleanUpdates);
    }

    @Test
    void cModeRetainsBooleanUpdateAcceptance() {
        var api = new CompilerApi(new SourceFile("c-bool-update.mc", """
                int main(){bool value=false; ++value; --value; value++; value--; return 0;}
                """), LanguageMode.C);
        var semantic = api.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        api.runThrough(semantic);
        assertTrue(semantic.succeeded(), () -> api.stages().stream()
                .flatMap(stage -> stage.errors().stream()).toList().toString());
    }

    static Stream<Arguments> uninitializedPrograms() {
        return Stream.of("++value", "--value", "value++", "value--").flatMap(update ->
                Stream.of(LanguageMode.values()).map(mode -> Arguments.of(update, mode)));
    }

    @ParameterizedTest(name = "{0} [{1}]")
    @MethodSource("uninitializedPrograms")
    void updatesRetainDebuggerUninitializedReadChecks(String update, LanguageMode mode) {
        // Reading an uninitialized object is not a C/C++ reference program. This checks
        // the debugger's existing diagnostic contract without using G++ as an oracle.
        var debug = new DebugApi(new SourceFile("uninitialized-update.mc",
                "int main(){int value; " + update + "; return 0;}"), "", mode);
        int steps = 1000;
        while (debug.canNext() && steps-- > 0) debug.next();
        assertFalse(debug.canNext(), "debugger exhausted the step budget");
        assertEquals(Debugger.Status.FAILED, debug.current().stop().status());
        assertTrue(debug.current().stop().error().contains("uninitialized"), debug.current().stop()::error);
    }

    @Test
    void updatesPreserveVolatileAccessesWithoutTakingAnOrdinaryLocalsAddress() {
        var api = new CompilerApi(new SourceFile("volatile-update.mc", """
                volatile int global = 2;
                int main(){volatile int local=1; local++; --local; global++; --global; return 0;}
                """), LanguageMode.C);
        var instructions = api.runToIr().findFunction("main").orElseThrow().blocks().stream()
                .flatMap(block -> block.instructions().stream()).toList();
        assertEquals(4, instructions.stream().filter(instruction ->
                instruction instanceof MemoryInstruction.IrLoadLocalInstruction localLoad && localLoad.volatileAccess()
                || instruction instanceof MemoryInstruction.IrLoadPointerInstruction pointerLoad && pointerLoad.volatileAccess()).count());
        assertTrue(instructions.stream().filter(instruction ->
                instruction instanceof MemoryInstruction.IrStoreLocalInstruction localStore && localStore.volatileAccess()
                || instruction instanceof MemoryInstruction.IrStorePointerInstruction pointerStore && pointerStore.volatileAccess()).count() >= 4);
        assertTrue(instructions.stream().noneMatch(MemoryInstruction.IrAddressOfLocalInstruction.class::isInstance));
    }

    @ParameterizedTest
    @EnumSource(LanguageMode.class)
    void semanticStepsKeepThePostfixReceiverAndItsOriginalSourceNodes(LanguageMode mode) {
        var api = new CompilerApi(new SourceFile("postfix-visits.mc", """
                struct Box { int value; };
                struct Box box = {1};
                struct Box *receiver(){return &box;}
                int main(){receiver()->value++; return 0;}
                """), mode);
        var parser = api.stages().stream().filter(Parser.class::isInstance).map(Parser.class::cast).findFirst().orElseThrow();
        var semantic = api.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        api.setResultRecording(semantic, true);
        api.runThrough(semantic);
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        var main = parser.result().program().functions().stream().filter(function -> function.name().equals("main"))
                .findFirst().orElseThrow();
        var update = assertInstanceOf(Expression.PostfixUpdateExpr.class,
                ((Statement.ExprStmt) main.body().statements().getFirst()).expression());
        var field = assertInstanceOf(Expression.FieldAccessExpr.class, update.target());
        var receiver = assertInstanceOf(Expression.CallExpr.class, field.target());
        var visited = semantic.stepResults().stream().filter(step -> step.operation().equals("VISIT_AST_NODE"))
                .map(step -> step.contextAs(SemanticResult.class).orElseThrow().action().astNode()).toList();
        for (var expected : List.of(update, field, receiver, receiver.callee())) {
            assertTrue(visited.stream().anyMatch(actual -> actual == expected),
                    "Missing original source node: " + expected.getClass().getSimpleName());
        }
    }

    @ParameterizedTest(name = "{0} [{1}]")
    @MethodSource("invalidPrograms")
    void updatesRequireAModifiableLvalue(String name, LanguageMode mode, String source) throws Exception {
        Path path = temporary.resolve(name + ".cpp");
        Files.writeString(path, source);
        var reference = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                "-std=c++17", "-pedantic-errors", "-fsyntax-only", path.toString()), temporary, "", Duration.ofSeconds(20), 65536);
        assertFalse(reference.timedOut());
        assertFalse(reference.outputExceeded());
        assertNotEquals(0, reference.exitCode(), reference::stderr);
        assertFalse(reference.stderr().isBlank());

        var api = new CompilerApi(new SourceFile(path.toString(), source), mode);
        var semantic = api.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        api.runThrough(semantic);
        var errors = api.stages().stream().flatMap(stage -> stage.errors().stream()).toList();
        assertFalse(errors.isEmpty(), "Invalid update must be rejected before lowering");
        assertTrue(errors.stream().anyMatch(error -> error.range() != null), errors::toString);
    }
}
