package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class DeadCodeEliminationTest {
    private static final SourceRange RANGE = new SourceRange(1, 1, 1, 9);
    private static final IrTemporary X = new IrTemporary("%x", IrType.INT);
    private static final IrTemporary Y = new IrTemporary("%y", IrType.INT);
    private static final IrConstant ZERO = new IrConstant(0);
    private static final IrConstant ONE = new IrConstant(1);

    @Test void trimsDeadTailsAndUnreachableBlocksIncludingUnreachableEffects() {
        var source = program(List.of(),
                block("entry", ret(ONE), new IrCallInstruction(null, "effect", List.of(), false, RANGE)),
                block("unreachable", new IrCallInstruction(null, "effect", List.of(), false, RANGE), ret(ZERO)));
        var result = run(source);
        assertEquals(1, result.functions().getFirst().blocks().size());
        assertEquals(List.of(ret(ONE)), instructions(result));
        assertEquals(2, source.functions().getFirst().blocks().size());
    }

    @Test void literalBranchesRemoveOnlyTheUnselectedPathAndPreserveRanges() {
        for (IrValue condition : List.of(ZERO, ONE)) {
            boolean truth = ((IrConstant) condition).value() != 0;
            var source = program(List.of(), block("entry", new IrBranchInstruction(condition, "yes", "no", RANGE)),
                    block("yes", ret(ONE)), block("no", ret(ZERO)));
            var result = run(source);
            assertEquals(List.of("entry", truth ? "yes" : "no"), result.functions().getFirst().blocks().stream().map(IrBlock::label).toList());
            var jump = assertInstanceOf(IrJumpInstruction.class, instructions(result).getFirst());
            assertSame(RANGE, jump.range());
        }
    }

    @Test void foldsIntegerConstantsAtTheirDeclaredWidthAndLeavesFloatBranchesIntact() {
        for (IrConstant zero : List.of(new IrConstant(256, IrType.UNSIGNED_CHAR),
                new IrConstant(65536, IrType.SHORT), new IrConstant(0x1_0000_0000L, IrType.INT))) {
            var result = run(program(List.of(), block("entry", new IrBranchInstruction(zero, "yes", "no", RANGE)),
                    block("yes", ret(ONE)), block("no", ret(ZERO))));
            assertEquals("no", ((IrJumpInstruction) instructions(result).getFirst()).targetLabel());
        }
        for (IrFloatConstant value : List.of(new IrFloatConstant(-0.0, IrType.DOUBLE),
                new IrFloatConstant(Double.NaN, IrType.DOUBLE), new IrFloatConstant(1e-50, IrType.FLOAT))) {
            var source = program(List.of(), block("entry", new IrBranchInstruction(value, "yes", "no", RANGE)),
                    block("yes", ret(ONE)), block("no", ret(ZERO)));
            assertEquals(source, run(source), "floating comparisons retain the native floating environment behavior");
        }
    }

    @Test void removesAnEntireUnusedComputationChain() {
        var source = program(List.of(), block("entry", new IrMoveInstruction(X, ONE, RANGE),
                new IrBinaryInstruction(Y, IrBinaryOperator.ADD, X, ONE, RANGE), ret(ZERO)));
        assertEquals(List.of(ret(ZERO)), instructions(run(source)));
    }

    @Test void keepsNonSsaDefinitionsThatAreLiveAtBranchMerge() {
        var parameter = new IrParameter("condition", MiniType.INT, IrType.INT, RANGE);
        var source = program(List.of(parameter), block("entry", new IrBranchInstruction(parameter.ref(), "yes", "no", RANGE)),
                block("yes", new IrMoveInstruction(X, ONE, RANGE), new IrJumpInstruction("merge", RANGE)),
                block("no", new IrMoveInstruction(X, ZERO, RANGE), new IrJumpInstruction("merge", RANGE)),
                block("merge", ret(X)));
        assertEquals(source, run(source));
    }

    @Test void removesOverwrittenDefinitionsWithoutLosingTheLastValue() {
        var source = program(List.of(), block("entry", new IrMoveInstruction(X, ZERO, RANGE),
                new IrMoveInstruction(X, ONE, RANGE), ret(X)));
        assertEquals(List.of(new IrMoveInstruction(X, ONE, RANGE), ret(X)), instructions(run(source)));
    }

    @Test void computesLivenessToAFixedPointAcrossBackEdges() {
        var parameter = new IrParameter("condition", MiniType.INT, IrType.INT, RANGE);
        var source = program(List.of(parameter), block("entry", new IrMoveInstruction(X, ZERO, RANGE), new IrJumpInstruction("loop", RANGE)),
                block("loop", new IrBinaryInstruction(X, IrBinaryOperator.ADD, X, ONE, RANGE),
                        new IrBranchInstruction(parameter.ref(), "loop", "exit", RANGE)), block("exit", ret(X)));
        assertEquals(source, run(source));
        var deadCycle = program(List.of(parameter), source.functions().getFirst().blocks().get(0),
                source.functions().getFirst().blocks().get(1), block("exit", ret(ZERO)));
        assertTrue(instructions(run(deadCycle)).stream().noneMatch(i -> i instanceof IrMoveInstruction || i instanceof IrBinaryInstruction));
    }

    @Test void neverRemovesMemoryEffectsCallsOrRuntimeChecks() {
        var local = new IrLocal("x#0", "x", MiniType.INT, IrType.INT, 4, 4, RANGE);
        var pointer = new IrConstant(256, IrType.POINTER);
        var source = program(List.of(), block("entry",
                new IrDeclareLocalInstruction(local, RANGE), new IrStoreLocalInstruction(local, ONE, true, RANGE),
                new IrCheckInitializedInstruction(local, RANGE), new IrLoadLocalInstruction(X, local, true, RANGE),
                new IrLoadPointerInstruction(Y, pointer, false, RANGE), new IrStorePointerInstruction(pointer, ONE, true, RANGE),
                new IrMemCopyInstruction(pointer, pointer, 4, true, RANGE), new IrCheckNonZeroInstruction(ONE, RANGE),
                new IrBinaryInstruction(new IrTemporary("%division", IrType.INT), IrBinaryOperator.DIVIDE, ONE, ONE, RANGE),
                new IrCallInstruction(new IrTemporary("%call", IrType.INT), "effect", List.of(), false, RANGE), ret(ZERO)));
        assertEquals(source, run(source));
    }

    @Test void keepsMutableParameterSnapshotsWhenUsedAfterParameterMutation() {
        var parameter = new IrParameter("p", MiniType.INT, IrType.INT, RANGE);
        var source = program(List.of(parameter), block("entry", new IrMoveInstruction(X, parameter.ref(), RANGE),
                new IrStorePointerInstruction(new IrParameterAddress("p"), ONE, RANGE), ret(X)));
        assertEquals(source, run(source));
    }

    @Test void preservesMetadataAndIsIdempotent() {
        var source = program(List.of(), block("entry", new IrMoveInstruction(X, ONE, RANGE), ret(ZERO)));
        var result = run(source);
        assertEquals(result, run(result));
        assertEquals(source.displayNames(), result.displayNames());
        assertEquals(source.currentSubject(), result.currentSubject());
        assertEquals(source.externalFunctionNames(), result.externalFunctionNames());
        assertEquals(2, instructions(source).size());
        assertThrows(UnsupportedOperationException.class, () -> result.functions().clear());
    }

    private static IrResult run(IrResult source) {
        return new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, List.of(new DeadCodeEliminationPass())).apply(source).ir();
    }

    private static IrResult program(List<IrParameter> parameters, IrBlock... blocks) {
        return new IrResult(List.of(new IrFunction("main", MiniType.INT, parameters, false, List.of(blocks), RANGE)),
                List.of(), List.of(), Set.of("effect"), Set.of(), Map.of(), null, "original", Map.of("main", "main"));
    }

    private static List<IrInstruction> instructions(IrResult result) {
        return result.functions().getFirst().blocks().stream().flatMap(b -> b.instructions().stream()).toList();
    }
    private static IrBlock block(String label, IrInstruction... instructions) { return new IrBlock(label, List.of(instructions)); }
    private static IrReturnInstruction ret(IrValue value) { return new IrReturnInstruction(value, RANGE); }
}
