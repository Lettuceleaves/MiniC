package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrLowerer;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.ir.optimize.DeadCodeEliminationPass;
import minic.compiler.ir.optimize.IrOptimizationPipeline;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(120)
final class CppDeadCodeOptimizationTest {
    @TempDir Path temporary;

    private static final String DEAD_SOURCE = """
            int main() {
                int value = 4;
                (value + 3) * (value - 2);
                if (0) value = 99;
                return value - 4;
            }
            """;

    private static final String MERGE_SOURCE = """
            #include <stdio.h>
            int pick(int value) { return value < 0 ? 11 : value == 0 ? 22 : 33; }
            int main() {
                int input = getchar() - '0';
                printf("%d %d %d\\n", pick(-input), pick(0), pick(input));
                return 0;
            }
            """;

    private static final String EFFECT_SOURCE = """
            #include <stdio.h>
            int calls = 0;
            int side() { calls++; return calls; }
            int exercise(int *pointer, volatile int *observed, int divisor) {
                volatile int local = 3;
                local;
                *pointer;
                *observed;
                side();
                12 / divisor;
                12 % divisor;
                return calls;
            }
            int main() {
                int value = 5;
                volatile int observed = 7;
                printf("%d\\n", exercise(&value, &observed, 2));
                return 0;
            }
            """;

    static Stream<Arguments> validPrograms() {
        return Stream.of(
                Arguments.of("literal-dead-paths-and-unused-arithmetic", """
                        #include <stdio.h>
                        int main() {
                            int value = 4;
                            (value + 3) * (value - 2);
                            if (0) puts("unreachable");
                            if (1) value += 2; else value += 100;
                            printf("%d\\n", value);
                            return 0;
                        }
                        """, "", "6\n"),
                Arguments.of("non-ssa-conditional-merge-and-fixed-input", MERGE_SOURCE, "3\n", "11 22 33\n"),
                Arguments.of("loop-backedge-break-continue-and-switch", """
                        #include <stdio.h>
                        int main() {
                            int total = 0;
                            for (int i = 1; i <= 8; i++) {
                                if (i == 3) continue;
                                switch (i) { case 5: total += 50; break; default: total += i; }
                                if (i == 6) break;
                            }
                            int j = 0;
                            while (j < 5) { ++j; if (j == 2) continue; total += j; }
                            do { total += 2; } while (0);
                            printf("%d\\n", total);
                            return 0;
                        }
                        """, "", "78\n"),
                Arguments.of("mutable-parameter-receiver-and-indirect-callee-snapshots", """
                        #include <stdio.h>
                        int twice(int value) { return value * 2; }
                        int triple(int value) { return value * 3; }
                        int replace(int (**slot)(int)) { *slot = triple; return 3; }
                        int indirect(int (*function)(int)) { return function(replace(&function)); }
                        int snapshot(int value) { int *slot = &value; int old = value++; *slot += 3; return old * 100 + value; }
                        struct Box { int value; int add(int amount) { value += amount; return value; } };
                        int change(Box **slot, Box *other) { *slot = other; return 4; }
                        int invoke(Box *pointer, Box *other) { return pointer->add(change(&pointer, other)); }
                        int main() {
                            Box first = {2};
                            Box second = {20};
                            int value = invoke(&first, &second);
                            printf("%d %d %d %d %d\\n", snapshot(3), value, first.value, second.value, indirect(twice));
                            return 0;
                        }
                        """, "", "307 6 6 20 6\n"),
                Arguments.of("discarded-volatile-pointer-loads-calls-and-division", EFFECT_SOURCE, "", "1\n"),
                Arguments.of("aggregate-copy-addresses-stay-live", """
                        #include <stdio.h>
                        struct Pair { int first; int second; };
                        int main() {
                            Pair first = {4, 9};
                            Pair second = {};
                            second = first;
                            second.first += 3;
                            printf("%d %d %d\\n", first.first, second.first, second.second);
                            return 0;
                        }
                        """, "", "4 7 9\n")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPrograms")
    void baselineOptimizedNativeReferenceAndSourceDebuggerAgree(String name, String source, String stdin, String expected) throws Exception {
        for (OptimizationLevel level : OptimizationLevel.values()) {
            var report = harness(level).run(name + "-" + level, source, stdin);
            assertTrue(report.passed(), () -> level + ": " + report.describe());
            for (var outcome : report.outcomes().values()) assertEquals(expected, normalize(outcome.stdout()), report::describe);
        }
    }

    @Test void optimizedNativeActuallyRunsDeadCodeEliminationWhileBaselineRemainsOriginal() {
        SourceFile source = source(DEAD_SOURCE);
        var api = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM, OptimizationLevel.OPTIMIZED);
        IrResult original = api.runToIr();
        var sourceInstructions = instructions(original);
        var sourceFunctions = original.functions();
        var assembler = stage(api, Assembler.class);
        api.runThrough(assembler);
        assertTrue(assembler.succeeded(), () -> assembler.errors().toString());
        assertTrue(assembler.optimizationResult().passNames().contains("dead-code-elimination"),
                "OPTIMIZED must register and execute the verified DCE pass");
        IrResult optimized = assembler.input().irResult();
        assertTrue(instructions(optimized).size() < sourceInstructions.size(), "The source fixture has provably unused integer work");
        assertTrue(optimized.functions().getFirst().blocks().size() < original.functions().getFirst().blocks().size());
        assertSame(original, stage(api, IrLowerer.class).result());
        assertSame(sourceFunctions, original.functions());
        assertEquals(sourceInstructions, instructions(original), "Native optimization must not rewrite the source/debug IR");
        assertEquals(original.displayNames(), optimized.displayNames());
        assertEquals(original.structLayouts(), optimized.structLayouts());
        var baseline = new Assembler(original);
        baseline.assemble();
        assertSame(original, baseline.input().irResult());
        assertEquals(OptimizationLevel.BASELINE, baseline.optimizationLevel());
        assertTrue(baseline.optimizationResult().passNames().isEmpty());

        Set<IrInstruction> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        identities.addAll(sourceInstructions);
        // Identity preservation here is the DCE pass's contract; other registered
        // passes may legitimately replace operands or inline instructions.
        for (IrInstruction instruction : instructions(optimized(original))) {
            assertTrue(sourceInstructions.stream().anyMatch(old -> old.range().equals(instruction.range())));
            if (!identities.contains(instruction)) {
                IrJumpInstruction jump = assertInstanceOf(IrJumpInstruction.class, instruction);
                assertTrue(sourceInstructions.stream().filter(IrBranchInstruction.class::isInstance)
                        .map(IrBranchInstruction.class::cast).anyMatch(old -> old.range().equals(jump.range())
                                && List.of(old.thenLabel(), old.elseLabel()).contains(jump.targetLabel())));
            }
        }
    }

    @Test void realConditionalMergeKeepsEveryLiveDefinitionWithoutAssumingSsa() {
        IrResult original = new CompilerApi(source(MERGE_SOURCE), LanguageMode.CPP17_ALGORITHM).runToIr();
        IrResult optimized = optimized(original);
        var definitions = instructions(original).stream().filter(IrMoveInstruction.class::isInstance)
                .map(IrMoveInstruction.class::cast).collect(Collectors.groupingBy(move -> move.result().name()));
        var merges = definitions.values().stream().filter(values -> values.size() > 1).toList();
        assertFalse(merges.isEmpty(), "This fixture must exercise the compiler's repeated merge temporary definitions");
        for (var merge : merges) for (var move : merge) assertTrue(instructions(optimized).contains(move));
    }

    @Test void discardedObservableOperationsAndTheirAddressDependenciesRemain() {
        IrResult original = new CompilerApi(source(EFFECT_SOURCE), LanguageMode.CPP17_ALGORITHM).runToIr();
        var retained = instructions(optimized(original));
        var effects = instructions(original).stream().filter(instruction ->
                instruction instanceof IrLoadPointerInstruction
                        || instruction instanceof IrLoadLocalInstruction load && load.volatileAccess()
                        || instruction instanceof IrCallInstruction
                        || instruction instanceof IrCheckInitializedInstruction
                        || instruction instanceof IrCheckNonZeroInstruction
                        || instruction instanceof IrBinaryInstruction binary
                        && (binary.operator() == IrBinaryOperator.DIVIDE || binary.operator() == IrBinaryOperator.MODULO)).toList();
        assertTrue(effects.stream().filter(IrLoadPointerInstruction.class::isInstance).count() >= 2);
        assertTrue(effects.stream().anyMatch(IrCheckNonZeroInstruction.class::isInstance));
        assertTrue(effects.stream().anyMatch(instruction -> instruction instanceof IrLoadLocalInstruction load && load.volatileAccess()));
        assertTrue(retained.containsAll(effects), "Unused results cannot erase observable or trapping operations");
    }

    @Test void unusedFloatingComputationsRetainFloatingEnvironmentEffects() {
        IrResult original = new CompilerApi(source("double compute(double value){value + 1.0; return value;} int main(){compute(2.0); return 0;}"),
                LanguageMode.CPP17_ALGORITHM).runToIr();
        var floating = instructions(original).stream().filter(instruction -> instruction instanceof IrBinaryInstruction binary
                && (binary.left().type().isFloatingScalar() || binary.right().type().isFloatingScalar())).toList();
        assertFalse(floating.isEmpty());
        assertTrue(instructions(optimized(original)).containsAll(floating));
    }

    static Stream<Arguments> faultPrograms() {
        return Stream.of(Arguments.of("uninitialized", "int main(){int value; value; return 0;}", 101, "uninitialized"),
                Arguments.of("division", "int main(){int zero=0; 42/zero; return 0;}", 102, "zero"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("faultPrograms")
    void discardedFaultingReadsStillFailInBothNativeModesAndDebug(String name, String text, int expectedExit, String marker) throws Exception {
        // These programs have undefined C++ behavior; G++ is deliberately not a semantic oracle for them.
        SourceFile source = source(text);
        IrResult original = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM).runToIr();
        for (OptimizationLevel level : OptimizationLevel.values()) {
            assertEquals(expectedExit, runNative(source, original, level, name + level));
        }
        for (IrResult input : List.of(original, optimized(original))) {
            var debug = DebugApi.fromIr(source, input, "");
            for (int steps = 0; debug.canNext() && steps < 500; steps++) debug.next();
            assertFalse(debug.canNext());
            assertEquals(Debugger.Status.FAILED, debug.current().stop().status());
            assertTrue(debug.current().stop().error().contains(marker), debug.current().stop()::error);
        }
    }

    @Test void sourceDebuggerRetainsDiscardedExpressionLocationsAndExactHistory() {
        SourceFile source = source(DEAD_SOURCE);
        var api = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM, OptimizationLevel.OPTIMIZED);
        IrResult original = api.runToIr();
        api.runThrough(stage(api, Assembler.class));
        var debug = DebugApi.fromIr(source, original, "");
        var history = new ArrayList<Debugger.Context>();
        history.add(debug.current());
        for (int steps = 0; debug.canNext() && steps < 500; steps++) history.add(debug.next());
        assertFalse(debug.canNext());
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status());
        assertEquals(0, debug.current().runtime().termination().status());
        assertTrue(history.stream().anyMatch(context -> context.stop().range() != null && context.stop().range().startLine() == 3),
                "Source debugging still visits the arithmetic discarded by native DCE");
        for (int index = history.size() - 2; index >= 0; index--) assertSame(history.get(index), debug.previous());
        for (int index = 1; index < history.size(); index++) assertSame(history.get(index), debug.next());
    }

    private CppDifferentialHarness harness(OptimizationLevel level) {
        return new CppDifferentialHarness(temporary, CppDifferentialHarness.referenceCompiler(System.getenv()),
                new CppDifferentialHarness.Limits(Duration.ofSeconds(30), Duration.ofSeconds(10), 50_000, 1_048_576),
                LanguageMode.CPP17_ALGORITHM, level);
    }

    private int runNative(SourceFile source, IrResult ir, OptimizationLevel level, String name) throws Exception {
        Path directory = temporary.resolve(name);
        var assembler = new Assembler(ir, level);
        var obj = new ObjBuilder(source, assembler, directory, "program");
        var linker = new Linker(source, obj, directory, "program");
        new CompilerApi(List.of(assembler, obj, linker)).runThrough(linker);
        assertTrue(linker.succeeded(), () -> "asm=" + assembler.errors() + ", obj=" + obj.errors() + ", link=" + linker.errors());
        var run = BoundedProcess.run(List.of(directory.resolve("program.exe").toString()), temporary, "", Duration.ofSeconds(10), 65_536);
        assertFalse(run.timedOut(), run::stderr);
        assertFalse(run.outputExceeded(), run::stderr);
        return run.exitCode();
    }

    private static IrResult optimized(IrResult original) {
        var assembler = new Assembler(original, new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,
                List.of(new DeadCodeEliminationPass())));
        assembler.assemble();
        assertTrue(assembler.succeeded(), () -> assembler.errors().toString());
        return assembler.input().irResult();
    }
    private static SourceFile source(String text) { return new SourceFile("dead-code.cpp", text); }
    private static List<IrInstruction> instructions(IrResult ir) {
        return ir.functions().stream().flatMap(function -> function.blocks().stream()).flatMap(block -> block.instructions().stream()).toList();
    }
    private static <T> T stage(CompilerApi api, Class<T> type) {
        return api.stages().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }
    private static String normalize(String text) { return text.replace("\r\n", "\n"); }
}
