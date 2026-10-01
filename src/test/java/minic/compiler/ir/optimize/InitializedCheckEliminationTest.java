package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class InitializedCheckEliminationTest {
    private static final SourceRange R = new SourceRange(2, 1, 2, 8);
    private static final IrLocal X = local("x#0");
    private static final IrLocal Y = local("x#1");
    private static final IrParameter CONDITION = new IrParameter("condition", MiniType.INT, IrType.INT, R);

    @Test void storedLocalChecksDisappearWithoutRemovingStoresOrVolatileReads() {
        var value = new IrTemporary("%value", IrType.INT);
        var load = new IrLoadLocalInstruction(value, X, true, R);
        IrResult source = program(block("entry", declare(X), store(X), check(X), load, check(X), ret()));
        IrResult result = apply(source);
        assertEquals(0, checks(result));
        assertEquals(List.of(declare(X), store(X), load, ret()), result.functions().getFirst().blocks().getFirst().instructions());
        assertEquals(2, checks(source));
    }

    @Test void firstUnknownCheckRemainsAndEstablishesKnowledgeOnItsNormalContinuation() {
        IrResult result = apply(program(block("entry", declare(X), check(X), check(X), ret())));
        assertEquals(1, checks(result));
        assertSame(R, result.functions().getFirst().blocks().getFirst().instructions().get(1).range());
    }

    @Test void bothPredecessorsMustEstablishInitialization() {
        assertEquals(0, checks(apply(diamond(true))));
        assertEquals(1, checks(apply(diamond(false))));
    }

    @Test void checkedPredecessorsAlsoProveTheMergeCheckRedundant() {
        IrResult result = apply(program(block("entry", declare(X), branch("yes", "no")),
                block("yes", check(X), jump("merge")), block("no", check(X), jump("merge")),
                block("merge", check(X), ret())));
        assertEquals(2, checks(result));
        assertTrue(result.functions().getFirst().blocks().getLast().instructions().stream().noneMatch(IrCheckInitializedInstruction.class::isInstance));
    }

    @Test void loopInvariantStoreDominatesEveryCheck() {
        IrResult result = apply(program(block("entry", declare(X), store(X), jump("loop")),
                block("loop", check(X), branch("loop", "exit")), block("exit", check(X), ret())));
        assertEquals(0, checks(result));
    }

    @Test void loopCheckCannotUseTheFirstIterationsStoreOnLaterDeclarations() {
        IrResult result = apply(program(block("entry", jump("loop")),
                block("loop", declare(X), branch("store", "check")),
                block("store", store(X), jump("check")),
                block("check", check(X), branch("loop", "exit")), block("exit", ret())));
        assertEquals(1, checks(result));
    }

    @Test void aBackedgeStoreDoesNotInitializeTheFirstEntryIntoTheLoop() {
        IrResult result = apply(program(block("entry", declare(X), jump("loop")),
                block("loop", check(X), store(X), branch("loop", "exit")), block("exit", ret())));
        assertEquals(1, checks(result));
    }

    @Test void anEntryBackedgeStillHasTheUninitializedFunctionInvocationEdge() {
        IrResult result = apply(program(block("entry", check(X), store(X), branch("entry", "exit")), block("exit", ret())));
        assertEquals(1, checks(result));
    }

    @Test void redeclarationAndShadowedStorageDoNotShareKnowledge() {
        IrResult result = apply(program(block("entry", declare(X), store(X), declare(Y), check(Y),
                check(X), declare(X), check(X), ret())));
        assertEquals(2, checks(result));
    }

    @Test void indirectWritesAndMemoryCopiesDoNotPretendToSetTheNativeFlag() {
        var address = new IrTemporary("%address", IrType.POINTER);
        var pointer = new IrParameter("pointer", MiniType.INT.pointerTo(), IrType.POINTER, R);
        var instructions = List.<IrInstruction>of(declare(X), new IrAddressOfLocalInstruction(address, X, R),
                new IrStorePointerInstruction(address, new IrConstant(7), R),
                new IrMemCopyInstruction(address, pointer.ref(), 4, R), check(X), ret());
        var function = new IrFunction("f", MiniType.INT, List.of(pointer), false, List.of(new IrBlock("entry", instructions)), R);
        assertEquals(1, checks(apply(new IrResult(List.of(function)))));
    }

    @Test void callsCannotResetThePrivateFlagButCannotEstablishItEither() {
        var call = new IrCallInstruction(null, "effect", List.of(), false, R);
        IrResult source = program(block("entry", declare(X), store(X), call, check(X), declare(Y), call, check(Y), ret()));
        source = new IrResult(source.functions(), List.of(), Set.of("effect"));
        assertEquals(1, checks(apply(source)));
    }

    @Test void unreachablePredecessorsAndDeadTailsHaveNoDataflowEffect() {
        var dead = block("dead", declare(X), jump("exit"));
        var tail = declare(X);
        IrResult source = program(block("entry", declare(X), store(X), jump("exit"), tail),
                dead, block("exit", check(X), ret()));
        IrResult result = apply(source);
        assertEquals(0, checks(result));
        assertEquals(dead, result.functions().getFirst().blocks().get(1));
        assertSame(tail, result.functions().getFirst().blocks().getFirst().instructions().getLast());
    }

    @Test void immutableResultRetainsProgramMetadataAndNoopIdentity() {
        var source = program(block("entry", ret()));
        source = new IrResult(source.functions(), List.of(new IrStringData("s", "text")), List.of(), Set.of(), Set.of(),
                Map.of(), null, "subject", Map.of("f", "source::f"));
        assertSame(source, apply(source));
        var changed = new IrResult(program(block("entry", declare(X), store(X), check(X), ret())).functions(),
                source.stringData(), source.globalData(), source.externalFunctionNames(), source.externalObjectNames(),
                source.structLayouts(), source.currentAstNode(), source.currentSubject(), source.displayNames());
        var result = apply(changed);
        assertEquals(changed.stringData(), result.stringData());
        assertEquals(changed.currentSubject(), result.currentSubject());
        assertEquals(changed.displayNames(), result.displayNames());
    }

    private static IrResult diamond(boolean otherStored) {
        return program(block("entry", declare(X), branch("yes", "no")), block("yes", store(X), jump("merge")),
                otherStored ? block("no", store(X), jump("merge")) : block("no", jump("merge")),
                block("merge", check(X), ret()));
    }
    private static IrResult apply(IrResult source) {
        return new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, List.of(new InitializedCheckEliminationPass())).apply(source).ir();
    }
    private static IrResult program(IrBlock... blocks) {
        return new IrResult(List.of(new IrFunction("f", MiniType.INT, List.of(CONDITION), false, List.of(blocks), R)));
    }
    private static long checks(IrResult ir) {
        return ir.functions().stream().flatMap(f -> f.blocks().stream()).flatMap(b -> b.instructions().stream())
                .filter(IrCheckInitializedInstruction.class::isInstance).count();
    }
    private static IrLocal local(String name) { return new IrLocal(name, "x", MiniType.INT, IrType.INT, 4, 4, R); }
    private static IrBlock block(String name, IrInstruction... instructions) { return new IrBlock(name, List.of(instructions)); }
    private static IrInstruction declare(IrLocal local) { return new IrDeclareLocalInstruction(local, R); }
    private static IrInstruction store(IrLocal local) { return new IrStoreLocalInstruction(local, new IrConstant(1), R); }
    private static IrInstruction check(IrLocal local) { return new IrCheckInitializedInstruction(local, R); }
    private static IrInstruction jump(String target) { return new IrJumpInstruction(target, R); }
    private static IrInstruction branch(String yes, String no) { return new IrBranchInstruction(CONDITION.ref(), yes, no, R); }
    private static IrInstruction ret() { return new IrReturnInstruction(new IrConstant(0), R); }
}
