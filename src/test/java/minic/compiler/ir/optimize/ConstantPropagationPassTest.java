package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.IrCallInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class ConstantPropagationPassTest {
    private static final SourceRange R = new SourceRange(2, 1, 2, 9);
    private static final IrTemporary X = new IrTemporary("%x", IrType.INT);
    private static final IrTemporary Y = new IrTemporary("%y", IrType.INT);
    private static final IrParameter CONDITION = new IrParameter("condition", MiniType.INT, IrType.INT, R);

    @Test void foldsAcrossBlocksAndPreservesOriginalMetadataAndInstructionRanges() {
        IrResult original = program(block("entry", new IrMoveInstruction(X, c(6), R), jump("next")),
                block("next", new IrBinaryInstruction(Y, IrBinaryOperator.MULTIPLY, X, c(7), R), ret(Y)));
        original = new IrResult(original.functions(), List.of(new IrStringData("text", "kept")), List.of(), Set.of(), Set.of(),
                Map.of(), null, "kept subject", Map.of("f", "Friendly::f"));
        IrResult result = apply(original);
        assertEquals(c(42), returned(result, "next"));
        assertInstanceOf(IrTemporary.class, returned(original, "next"));
        assertEquals(original.displayNames(), result.displayNames());
        assertEquals(original.stringData(), result.stringData());
        assertEquals(original.currentSubject(), result.currentSubject());
        assertEquals(R, instructions(result, "next").getFirst().range());
    }

    @Test void nonSsaMergeRetainsOnlyFactsSharedByEveryPredecessor() {
        assertEquals(c(9), returned(apply(diamond(9)), "merge"));
        assertEquals(X, returned(apply(diamond(10)), "merge"));
    }

    @Test void loopFixedPointKeepsInvariantAndDropsChangingInductionValue() {
        IrResult invariant = program(block("entry", new IrMoveInstruction(X, c(7), R), jump("loop")),
                block("loop", new IrBinaryInstruction(Y, IrBinaryOperator.ADD, X, c(1), R), branch("loop", "exit")),
                block("exit", ret(Y)));
        assertEquals(c(8), returned(apply(invariant), "exit"));
        IrResult changing = program(block("entry", new IrMoveInstruction(X, c(0), R), jump("loop")),
                block("loop", new IrBinaryInstruction(X, IrBinaryOperator.ADD, X, c(1), R), branch("loop", "exit")),
                block("exit", ret(X)));
        assertEquals(X, returned(apply(changing), "exit"));
        assertInstanceOf(IrBinaryInstruction.class, instructions(apply(changing), "loop").getFirst());
    }

    @Test void loopReexecutionCannotReplaceAnEarlierIterationSnapshot() {
        IrTemporary current = new IrTemporary("%current", IrType.INT);
        IrTemporary snapshot = new IrTemporary("%snapshot", IrType.INT);
        IrResult ir = program(block("entry", new IrMoveInstruction(snapshot, c(0), R), jump("loop")),
                block("loop", new IrCallInstruction(current, "next", List.of(), false, R),
                        new IrCallInstruction(null, "consume", List.of(snapshot), false, R),
                        new IrMoveInstruction(snapshot, current, R), branch("loop", "exit")),
                block("exit", ret(snapshot)));
        ir = new IrResult(ir.functions(), List.of(), Set.of("next", "consume"));
        IrResult optimized = apply(ir);
        var consume = (IrCallInstruction) instructions(optimized, "loop").get(1);
        assertEquals(List.of(snapshot), consume.arguments(), "A new call result cannot overwrite the old snapshot logically");
    }

    @Test void parameterSnapshotRemainsATemporaryAcrossAParameterStoreAndCall() {
        IrResult ir = program(block("entry", new IrMoveInstruction(X, CONDITION.ref(), R),
                new IrStorePointerInstruction(new IrParameterAddress("condition"), c(19), R),
                new IrCallInstruction(null, "consume", List.of(X), false, R), ret(X)));
        ir = new IrResult(ir.functions(), List.of(), Set.of("consume"));
        IrResult optimized = apply(ir);
        assertEquals(X, returned(optimized, "entry"));
        assertEquals(List.of(X), ((IrCallInstruction) instructions(optimized, "entry").get(2)).arguments());
    }

    @Test void temporaryCopyCanBypassACallWithoutGuessingAtMemoryContents() {
        IrLocal local = new IrLocal("value#0", "value", MiniType.INT, IrType.INT, 4, 4, R);
        IrResult ir = program(block("entry", new IrDeclareLocalInstruction(local, R),
                new IrLoadLocalInstruction(X, local, true, R), new IrMoveInstruction(Y, X, R),
                new IrCallInstruction(null, "mutate", List.of(), false, R), ret(Y)));
        ir = new IrResult(ir.functions(), List.of(), Set.of("mutate"));
        IrResult optimized = apply(ir);
        assertEquals(X, returned(optimized, "entry"));
        assertTrue(((IrLoadLocalInstruction) instructions(optimized, "entry").get(1)).volatileAccess());
        assertEquals(5, instructions(optimized, "entry").size(), "The pass does not remove observable instructions");
    }

    static Stream<Arguments> integerCases() {
        return Stream.of(
                Arguments.of(IrType.UNSIGNED_INT, IrBinaryOperator.ADD, 0xffff_ffffL, 1L, IrType.UNSIGNED_INT, 0L),
                Arguments.of(IrType.INT, IrBinaryOperator.ADD, 0x7fff_ffffL, 1L, IrType.INT, -2147483648L),
                Arguments.of(IrType.UNSIGNED_LONG_LONG, IrBinaryOperator.DIVIDE, -1L, 2L, IrType.UNSIGNED_LONG_LONG, Long.MAX_VALUE),
                Arguments.of(IrType.UNSIGNED_LONG_LONG, IrBinaryOperator.MODULO, -1L, 2L, IrType.UNSIGNED_LONG_LONG, 1L),
                Arguments.of(IrType.UNSIGNED_LONG_LONG, IrBinaryOperator.GREATER_THAN, -1L, 0L, IrType.INT, 1L),
                Arguments.of(IrType.LONG_LONG, IrBinaryOperator.GREATER_THAN, -1L, 0L, IrType.INT, 0L),
                Arguments.of(IrType.UNSIGNED_INT, IrBinaryOperator.SHIFT_RIGHT, 0x8000_0000L, 31L, IrType.UNSIGNED_INT, 1L),
                Arguments.of(IrType.INT, IrBinaryOperator.SHIFT_RIGHT, -8L, 2L, IrType.INT, -2L),
                Arguments.of(IrType.INT, IrBinaryOperator.SHIFT_LEFT, 3L, 2L, IrType.INT, 12L),
                Arguments.of(IrType.UNSIGNED_LONG_LONG, IrBinaryOperator.MULTIPLY, Long.MIN_VALUE, 2L, IrType.UNSIGNED_LONG_LONG, 0L)
        );
    }

    @ParameterizedTest @MethodSource("integerCases")
    void integerFoldingUsesTargetWidthAndSignedness(IrType type, IrBinaryOperator op, long left, long right, IrType resultType, long expected) {
        var result = new IrTemporary("%result", resultType);
        IrResult output = apply(withReturnType(sourceType(resultType), block("entry",
                new IrBinaryInstruction(result, op, new IrConstant(left, type), new IrConstant(right, type), R), ret(result))));
        assertEquals(new IrConstant(expected, resultType), returned(output, "entry"));
    }

    @Test void integerCastsNormalizeSourceAndTargetWidths() {
        var narrowed = new IrTemporary("%narrow", IrType.UNSIGNED_CHAR);
        var wide = new IrTemporary("%wide", IrType.UNSIGNED_LONG_LONG);
        IrResult output = apply(withReturnType(MiniType.UNSIGNED_LONG_LONG, block("entry",
                new IrCastInstruction(narrowed, c(-1), R), new IrCastInstruction(wide, narrowed, R), ret(wide))));
        assertEquals(new IrConstant(255, IrType.UNSIGNED_LONG_LONG), returned(output, "entry"));
    }

    @Test void integerUnarySelectAndBranchOperandsUseKnownValuesWithoutRemovingControlFlow() {
        var selected = new IrTemporary("%selected", IrType.INT);
        var condition = new IrTemporary("%test", IrType.INT);
        IrResult output = apply(program(block("entry",
                new IrUnaryInstruction(X, IrUnaryOperator.BITWISE_NOT, c(7), R),
                new IrUnaryInstruction(Y, IrUnaryOperator.NEGATE, X, R),
                new IrUnaryInstruction(condition, IrUnaryOperator.LOGICAL_NOT, c(0), R),
                new IrSelectInstruction(selected, condition, Y, c(99), R),
                new IrBranchInstruction(condition, "yes", "no", R)),
                block("yes", ret(selected)), block("no", ret(c(0)))));
        assertEquals(c(8), returned(output, "yes"));
        var branch = assertInstanceOf(IrBranchInstruction.class, instructions(output, "entry").getLast());
        assertEquals(c(1), branch.condition());
        assertEquals(3, output.functions().getFirst().blocks().size());
    }

    @Test void singleDefinitionTemporaryCopiesResolveTransitivelyButKeepLoadsAndCalls() {
        IrTemporary third = new IrTemporary("%third", IrType.INT);
        IrLocal local = new IrLocal("value#0", "value", MiniType.INT, IrType.INT, 4, 4, R);
        IrResult ir = program(block("entry", new IrDeclareLocalInstruction(local, R),
                new IrLoadLocalInstruction(X, local, R), new IrMoveInstruction(Y, X, R),
                new IrMoveInstruction(third, Y, R), ret(third)));
        assertEquals(X, returned(apply(ir), "entry"));
    }

    @Test void trappingDivisionInvalidShiftsAndFloatingComputationsRemainInstructions() {
        for (var operation : List.of(
                new IrBinaryInstruction(X, IrBinaryOperator.DIVIDE, c(1), c(0), R),
                new IrBinaryInstruction(X, IrBinaryOperator.MODULO, c(Integer.MIN_VALUE), c(-1), R),
                new IrBinaryInstruction(X, IrBinaryOperator.DIVIDE, c(Integer.MIN_VALUE), c(-1), R),
                new IrBinaryInstruction(X, IrBinaryOperator.SHIFT_LEFT, c(1), c(-1), R),
                new IrBinaryInstruction(X, IrBinaryOperator.SHIFT_LEFT, c(1), c(32), R))) {
            IrResult result = apply(program(block("entry", new IrCheckNonZeroInstruction(c(0), R), operation, ret(X))));
            assertEquals(operation, instructions(result, "entry").get(1));
            assertInstanceOf(IrCheckNonZeroInstruction.class, instructions(result, "entry").getFirst());
        }
        var value = new IrTemporary("%float", IrType.DOUBLE);
        var floating = new IrBinaryInstruction(value, IrBinaryOperator.ADD, new IrFloatConstant(1, IrType.DOUBLE), new IrFloatConstant(2, IrType.DOUBLE), R);
        IrResult result = apply(withReturnType(MiniType.DOUBLE, block("entry", floating, ret(value))));
        assertEquals(floating, instructions(result, "entry").getFirst());
        assertEquals(value, returned(result, "entry"));
    }

    @Test void unreachableBlocksAndDeadTailsArePreservedForTheDcePass() {
        IrInstruction tail = new IrMoveInstruction(X, c(4), R);
        IrResult original = program(block("entry", ret(c(0)), tail), block("dead"));
        IrResult optimized = apply(original);
        assertEquals(original.functions(), optimized.functions());
    }

    private static IrResult diamond(int right) {
        return program(block("entry", branch("left", "right")),
                block("left", new IrMoveInstruction(X, c(9), R), jump("merge")),
                block("right", new IrMoveInstruction(X, c(right), R), jump("merge")), block("merge", ret(X)));
    }
    private static MiniType sourceType(IrType type) {
        return switch (type) {
            case INT -> MiniType.INT; case UNSIGNED_INT -> MiniType.UNSIGNED_INT;
            case LONG_LONG -> MiniType.LONG_LONG; case UNSIGNED_LONG_LONG -> MiniType.UNSIGNED_LONG_LONG;
            default -> throw new IllegalArgumentException();
        };
    }
    private static IrResult apply(IrResult ir) {
        return new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, List.of(new ConstantPropagationPass())).apply(ir).ir();
    }
    private static IrResult program(IrBlock... blocks) { return withReturnType(MiniType.INT, blocks); }
    private static IrResult withReturnType(MiniType type, IrBlock... blocks) {
        return new IrResult(List.of(new IrFunction("f", type, List.of(CONDITION), false, List.of(blocks), R)));
    }
    private static IrBlock block(String label, IrInstruction... instructions) { return new IrBlock(label, List.of(instructions)); }
    private static IrConstant c(int number) { return new IrConstant(number); }
    private static IrJumpInstruction jump(String target) { return new IrJumpInstruction(target, R); }
    private static IrBranchInstruction branch(String yes, String no) { return new IrBranchInstruction(CONDITION.ref(), yes, no, R); }
    private static IrReturnInstruction ret(IrValue value) { return new IrReturnInstruction(value, R); }
    private static List<IrInstruction> instructions(IrResult ir, String label) {
        return ir.functions().getFirst().blocks().stream().filter(block -> block.label().equals(label)).findFirst().orElseThrow().instructions();
    }
    private static IrValue returned(IrResult ir, String label) { return ((IrReturnInstruction) instructions(ir, label).getLast()).value(); }
}
