package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import minic.compiler.type.MiniType.TypeQualifier;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

final class LocalScalarPromotionTest {
    private static final SourceRange R = new SourceRange(1, 1, 1, 8);
    private static final SourceRange LOAD_RANGE = new SourceRange(2, 1, 2, 5);
    private static final IrLocal X = local("x", MiniType.INT, IrType.INT);
    private static final IrParameter CONDITION = new IrParameter("condition", MiniType.INT, IrType.INT, R);

    @Test void privateStoresAndLoadsBecomeTypedMovesAndSourceRangesSurvive() {
        var value = temp("value");
        var input = program(block("entry", declare(X), store(X, 7), new IrLoadLocalInstruction(value, X, LOAD_RANGE), ret(value)));
        var result = apply(input); var code = instructions(result);
        assertEquals(0, localAccesses(result)); assertEquals(3, code.size());
        var store = assertInstanceOf(IrMoveInstruction.class, code.get(0));
        var load = assertInstanceOf(IrMoveInstruction.class, code.get(1));
        assertEquals(IrType.INT, store.result().type()); assertEquals(new IrConstant(7), store.value());
        assertEquals(value, load.result()); assertEquals(store.result(), load.value());
        assertSame(LOAD_RANGE, load.range()); assertEquals(3, localAccesses(input));
    }

    @Test void everyPredecessorMustInitializeBeforeAMergeLoad() {
        assertEquals(0, localAccesses(apply(diamond(true))));
        var incomplete = diamond(false); assertSame(incomplete, apply(incomplete));
    }

    @Test void loopCarriedValueUsesOneNonSsaHomeAndKeepsLoadSnapshots() {
        var loaded = temp("loaded"); var next = temp("next");
        var input = program(block("entry", declare(X), store(X, 0), jump("loop")),
                block("loop", new IrLoadLocalInstruction(loaded, X, R),
                        new IrBinaryInstruction(next, IrBinaryOperator.ADD, loaded, new IrConstant(1), R),
                        new IrStoreLocalInstruction(X, next, R), branch("loop", "done")),
                block("done", new IrLoadLocalInstruction(loaded, X, R), ret(loaded)));
        var output = apply(input); assertEquals(0, localAccesses(output));
        var moves = instructions(output).stream().filter(IrMoveInstruction.class::isInstance).map(IrMoveInstruction.class::cast).toList();
        assertEquals(1, moves.stream().filter(move -> !move.result().equals(loaded)).map(move -> move.result().name()).distinct().count());
    }

    @Test void eachRedeclarationMayPromoteOnlyWhenThisLifetimeIsWrittenBeforeReading() {
        var loaded = temp("loaded");
        var input = program(block("entry", jump("loop")),
                block("loop", declare(X), store(X, 9), new IrLoadLocalInstruction(loaded, X, R), branch("loop", "done")),
                block("done", ret(loaded)));
        assertEquals(0, localAccesses(apply(input)));
    }

    @Test void continueThatSkipsAssignmentCannotReuseThePreviousLifetimesValue() {
        var value = temp("value");
        var input = program(block("entry", jump("loop")),
                block("loop", declare(X), branch("store", "load")), block("store", store(X, 9), jump("load")),
                block("load", new IrLoadLocalInstruction(value, X, R), branch("loop", "done")), block("done", ret(value)));
        assertSame(input, apply(input));
    }

    @Test void aBackedgeStoreCannotInitializeTheFirstFunctionEntry() {
        var value = temp("value");
        var input = program(block("entry", declare(X), jump("loop")),
                block("loop", new IrLoadLocalInstruction(value, X, R), store(X, 8), branch("loop", "done")), block("done", ret(value)));
        assertSame(input, apply(input));
    }

    @Test void anyRetainedInitializationCheckPinsTheEntireLocal() {
        var input = program(block("entry", declare(X), store(X, 7), new IrCheckInitializedInstruction(X, R), ret(new IrConstant(0))));
        assertSame(input, apply(input));
        var promoted = new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,
                List.of(new InitializedCheckEliminationPass(), new LocalScalarPromotionPass())).apply(input).ir();
        assertEquals(0, localAccesses(promoted));
    }

    @Test void addressExposurePinsStorageEvenWhenThePointerNeverEscapesThisBlock() {
        var address = new IrTemporary("address", IrType.POINTER);
        var input = program(block("entry", declare(X), store(X, 1), new IrAddressOfLocalInstruction(address, X, R), ret(new IrConstant(0))));
        assertSame(input, apply(input));
    }

    @Test void volatileTypeOrEitherVolatileAccessPinsStorage() {
        var qualified = local("volatile", MiniType.qualified(MiniType.INT, Set.of(TypeQualifier.VOLATILE)), IrType.INT);
        var value = temp("value");
        for (var input : List.of(
                program(block("entry", declare(qualified), store(qualified, 3), ret(new IrConstant(0)))),
                program(block("entry", declare(X), new IrStoreLocalInstruction(X, new IrConstant(3), true, R), ret(new IrConstant(0)))),
                program(block("entry", declare(X), store(X, 3), new IrLoadLocalInstruction(value, X, true, R), ret(value)))))
            assertSame(input, apply(input));
    }

    @Test void aggregatesOpaqueCursorsAndFloatingLocalsRemainInMemory() {
        var array = new IrLocal("array", "array", MiniType.INT.arrayOf(2), IrType.POINTER, 8, 4, R);
        var floating = local("floating", MiniType.DOUBLE, IrType.DOUBLE);
        var cursor = local("cursor", MiniType.VA_LIST, IrType.POINTER);
        var input = program(block("entry", declare(array), declare(floating), declare(cursor),
                declare(IrLocal.incomingArgumentArea(4, R)), ret(new IrConstant(0))));
        assertSame(input, apply(input));
    }

    @Test void referencesInUnreachableBlocksOrAfterATerminatorPinTheLocal() {
        var value = temp("value");
        var suffix = program(block("entry", declare(X), store(X, 1), ret(new IrConstant(0)), new IrLoadLocalInstruction(value, X, R)));
        assertSame(suffix, apply(suffix));
        var unreachable = program(block("entry", declare(X), store(X, 1), ret(new IrConstant(0))),
                block("dead", new IrLoadLocalInstruction(value, X, R), ret(value)));
        assertSame(unreachable, apply(unreachable));
    }

    @Test void loadsAreSnapshotsAcrossLaterStoresAndCallsIncludingMutableParameters() {
        var before = temp("before"); var after = temp("after"); var result = temp("result");
        var input = program(block("entry", declare(X), new IrStoreLocalInstruction(X, CONDITION.ref(), R),
                new IrLoadLocalInstruction(before, X, R), new IrCallInstruction(null, "effect", List.of(new IrParameterAddress("condition")), false, R),
                store(X, 9), new IrLoadLocalInstruction(after, X, R),
                new IrBinaryInstruction(result, IrBinaryOperator.ADD, before, after, R), ret(result)));
        input = new IrResult(input.functions(), List.of(), Set.of("effect"));
        var output = apply(input); var code = instructions(output);
        assertEquals(0, localAccesses(output));
        assertEquals(CONDITION.ref(), assertInstanceOf(IrMoveInstruction.class, code.getFirst()).value());
        assertEquals(before, assertInstanceOf(IrMoveInstruction.class, code.get(1)).result());
        assertEquals(before, assertInstanceOf(IrBinaryInstruction.class, code.get(5)).left());
        assertInstanceOf(IrParameterAddress.class, assertInstanceOf(IrCallInstruction.class, code.get(2)).arguments().getFirst());
    }

    @Test void integerWidthsAndPointerValuesRetainTheirExactIrTypes() {
        var types = List.of(IrType.BOOL, IrType.CHAR, IrType.UNSIGNED_CHAR, IrType.SHORT, IrType.UNSIGNED_SHORT,
                IrType.INT, IrType.UNSIGNED_INT, IrType.LONG, IrType.UNSIGNED_LONG, IrType.LONG_LONG, IrType.UNSIGNED_LONG_LONG, IrType.POINTER);
        var declared = List.of(MiniType.BOOL, MiniType.CHAR, MiniType.UNSIGNED_CHAR, MiniType.SHORT, MiniType.UNSIGNED_SHORT,
                MiniType.INT, MiniType.UNSIGNED_INT, MiniType.LONG, MiniType.UNSIGNED_LONG, MiniType.LONG_LONG, MiniType.UNSIGNED_LONG_LONG, MiniType.INT.pointerTo());
        for (int i = 0; i < types.size(); i++) {
            var local = local("value", declared.get(i), types.get(i)); var value = new IrTemporary("value-load", types.get(i));
            var input = program(block("entry", declare(local), new IrStoreLocalInstruction(local, new IrConstant(0, types.get(i)), R),
                    new IrLoadLocalInstruction(value, local, R), ret(new IrConstant(0))));
            var output = apply(input); assertEquals(0, localAccesses(output));
            assertEquals(types.get(i), assertInstanceOf(IrMoveInstruction.class, instructions(output).getFirst()).result().type());
        }
    }

    @Test void generatedHomesNeverCollideWithExistingTemporaryParameterLocalOrLabelNames() {
        var occupied = temp("__promoted_0"); var value = temp("value");
        var input = program(block("__promoted_1", new IrMoveInstruction(occupied, new IrConstant(2), R),
                declare(X), store(X, 7), new IrLoadLocalInstruction(value, X, R), ret(value)));
        var code = instructions(apply(input));
        var home = assertInstanceOf(IrMoveInstruction.class, code.get(1)).result();
        assertNotEquals(occupied.name(), home.name()); assertNotEquals("__promoted_1", home.name()); assertNotEquals(X.name(), home.name());
    }

    @Test void ordinarySourceLoopActuallyLosesItsLocalStackOperationsAfterProof() {
        var original = new CompilerApi(new SourceFile("loop.c", "int main(){int sum=0;for(int i=0;i<20;i++)sum+=i;return sum;}" )).runToIr();
        var pipeline = new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,
                List.of(new InitializedCheckEliminationPass(), new LocalScalarPromotionPass()));
        var output = pipeline.apply(original).ir();
        assertTrue(localAccesses(original) > 0); assertEquals(0, localAccesses(output));
    }

    @Test void programMetadataIsPreservedAndNoopRetainsIdentity() {
        var empty = program(block("entry", ret(new IrConstant(0)))); assertSame(empty, apply(empty));
        var input = program(block("entry", declare(X), store(X, 1), ret(new IrConstant(0))));
        input = new IrResult(input.functions(), List.of(new IrStringData("format", "x")), List.of(), Set.of(), Set.of(), Map.of(),
                null, "subject", Map.of("f", "N::f", "x", "sourceX"));
        var output = apply(input);
        assertEquals(input.stringData(), output.stringData()); assertEquals(input.currentSubject(), output.currentSubject());
        assertEquals(input.displayNames(), output.displayNames()); assertNotSame(input, output);
    }

    private static IrResult diamond(boolean both) {
        var value = temp("value");
        return program(block("entry", declare(X), branch("yes", "no")), block("yes", store(X, 1), jump("done")),
                both ? block("no", store(X, 2), jump("done")) : block("no", jump("done")),
                block("done", new IrLoadLocalInstruction(value, X, R), ret(value)));
    }
    private static IrResult apply(IrResult source) { return new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, List.of(new LocalScalarPromotionPass())).apply(source).ir(); }
    private static List<IrInstruction> instructions(IrResult ir) { return ir.functions().getFirst().blocks().stream().flatMap(block -> block.instructions().stream()).toList(); }
    private static long localAccesses(IrResult ir) { return instructions(ir).stream().filter(i -> i instanceof IrDeclareLocalInstruction || i instanceof IrStoreLocalInstruction || i instanceof IrLoadLocalInstruction || i instanceof IrCheckInitializedInstruction).count(); }
    private static IrResult program(IrBlock... blocks) { return new IrResult(List.of(new IrFunction("f", MiniType.INT, List.of(CONDITION), false, List.of(blocks), R))); }
    private static IrBlock block(String name, IrInstruction... code) { return new IrBlock(name, List.of(code)); }
    private static IrLocal local(String name, MiniType declared, IrType type) { return new IrLocal(name, name, declared, type, type.sizeBytes(), type.sizeBytes(), R); }
    private static IrTemporary temp(String name) { return new IrTemporary(name, IrType.INT); }
    private static IrInstruction declare(IrLocal local) { return new IrDeclareLocalInstruction(local, R); }
    private static IrInstruction store(IrLocal local, int value) { return new IrStoreLocalInstruction(local, new IrConstant(value, local.type()), R); }
    private static IrInstruction jump(String target) { return new IrJumpInstruction(target, R); }
    private static IrInstruction branch(String yes, String no) { return new IrBranchInstruction(CONDITION.ref(), yes, no, R); }
    private static IrInstruction ret(minic.compiler.ir.value.IrValue value) { return new IrReturnInstruction(value, R); }
}
