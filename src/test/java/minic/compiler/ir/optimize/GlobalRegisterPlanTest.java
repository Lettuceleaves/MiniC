package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

final class GlobalRegisterPlanTest {
    private static final SourceRange R = new SourceRange(1, 1, 1, 5);
    private static final IrConstant ONE = new IrConstant(1);

    @Test void fixedHomeFollowsABranchValueIntoAnotherBlock() {
        var value = temp("value"); var joined = temp("joined");
        var function = function(List.of(
                block("entry", new IrMoveInstruction(value, ONE, R), new IrBranchInstruction(ONE, "left", "right", R)),
                block("left", new IrMoveInstruction(joined, value, R), new IrJumpInstruction("done", R)),
                block("right", new IrBinaryInstruction(joined, IrBinaryOperator.ADD, value, ONE, R), new IrJumpInstruction("done", R)),
                block("done", new IrReturnInstruction(joined, R))));
        assertTrue(LocalRegisterPlan.allocate(function).registers().isEmpty());
        var plan = GlobalRegisterPlan.allocate(function);
        assertEquals("r10", plan.registers().get("value"));
        assertEquals("r11", plan.registers().get("joined"));
    }

    @Test void backedgeExtendsAValuePastItsLastTextualDefinition() {
        var carried = temp("carried"); var scratch = temp("scratch");
        var function = function(List.of(
                block("entry", new IrJumpInstruction("body", R)),
                block("header", new IrBranchInstruction(carried, "done", "body", R)),
                block("body", new IrMoveInstruction(carried, ONE, R), new IrMoveInstruction(scratch, new IrConstant(9), R), new IrJumpInstruction("header", R)),
                block("done", new IrReturnInstruction(ONE, R))));
        var plan = GlobalRegisterPlan.allocate(function);
        assertEquals("r10", plan.registers().get("carried"));
        assertEquals("r11", plan.registers().get("scratch"), "a dead write after carried's definition must not overwrite its live backedge value");
    }

    @Test void profitableAcyclicCallSpillsAndRestoresOnlySurvivingValues() {
        var value = temp("value");
        var instructions = usesAroundCall(value, new IrCallInstruction(null, "effect", List.of(), false, R));
        var plan = GlobalRegisterPlan.allocate(function(List.of(new IrBlock("entry", instructions))));
        assertEquals("r10", plan.registers().get("value"));
        assertEquals(List.of(value), plan.spillsAt("entry", 3));
        assertEquals(List.of(), plan.spillsAt("entry", 2));
    }

    @Test void sameNameCallResultDoesNotRestoreTheOldVersionOverTheNewResult() {
        var old = temp("value"); var survives = temp("survives");
        var instructions = new ArrayList<IrInstruction>();
        instructions.add(new IrMoveInstruction(old, ONE, R));
        instructions.add(new IrMoveInstruction(survives, new IrConstant(9), R));
        for (int i = 0; i < 3; i++) instructions.add(new IrBinaryInstruction(temp("before" + i), IrBinaryOperator.ADD, survives, ONE, R));
        instructions.add(new IrCallInstruction(old, "effect", List.of(old), false, R));
        for (int i = 0; i < 3; i++) instructions.add(new IrBinaryInstruction(temp("after" + i), IrBinaryOperator.ADD, survives, ONE, R));
        instructions.add(new IrReturnInstruction(old, R));
        var plan = GlobalRegisterPlan.allocate(function(List.of(new IrBlock("entry", instructions))));
        assertTrue(plan.registers().containsKey(old.name())); assertTrue(plan.registers().containsKey(survives.name()));
        assertEquals(List.of(survives), plan.spillsAt("entry", 5));
        assertNotEquals(plan.registers().get(old.name()), plan.registers().get(survives.name()));
    }

    @Test void cheapValueAcrossACallKeepsItsExistingStackHome() {
        var value = temp("value");
        var plan = GlobalRegisterPlan.allocate(function(List.of(block("entry", new IrMoveInstruction(value, ONE, R),
                new IrCallInstruction(null, "effect", List.of(), false, R), new IrReturnInstruction(value, R)))));
        assertTrue(plan.stackTemporaries().contains("value"));
        assertTrue(plan.spillsAt("entry", 1).isEmpty());
    }

    @Test void valueUsedOnlyOutsideAHotCallLoopNeverAddsPerIterationSpills() {
        var value = temp("value");
        var use = new ArrayList<IrInstruction>();
        for (int i = 0; i < 8; i++) use.add(new IrBinaryInstruction(temp("after" + i), IrBinaryOperator.ADD, value, ONE, R));
        use.add(new IrReturnInstruction(value, R));
        var plan = GlobalRegisterPlan.allocate(function(List.of(
                block("entry", new IrMoveInstruction(value, ONE, R), new IrJumpInstruction("loop", R)),
                block("loop", new IrCallInstruction(null, "effect", List.of(), false, R), new IrBranchInstruction(ONE, "loop", "done", R)),
                new IrBlock("done", use))));
        assertTrue(plan.stackTemporaries().contains("value"));
        assertTrue(plan.spillsAt("loop", 0).isEmpty());
    }

    @Test void indirectCallsUseTheSameSurvivorRulesAndReturnImmutableSpillLists() {
        var value = temp("value");
        var call = new IrIndirectCallInstruction(null, new IrFunctionAddress("effect"), List.of(), false, R);
        var plan = GlobalRegisterPlan.allocate(function(List.of(new IrBlock("entry", usesAroundCall(value, call)))));
        assertEquals(List.of(value), plan.spillsAt("entry", 3));
        assertThrows(UnsupportedOperationException.class, () -> plan.spillsAt("entry", 3).clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.registers().clear());
    }

    @Test void nonExecutableAndFloatingValuesRetainStackHomes() {
        var value = temp("value"); var floating = new IrTemporary("floating", IrType.DOUBLE);
        var plan = GlobalRegisterPlan.allocate(function(List.of(block("entry", new IrMoveInstruction(value, ONE, R),
                new IrMoveInstruction(floating, new IrFloatConstant(0.5, IrType.DOUBLE), R), new IrReturnInstruction(value, R),
                new IrMoveInstruction(value, new IrConstant(8), R)))));
        assertEquals(Set.of("value", "floating"), plan.stackTemporaries());
        assertTrue(plan.registers().isEmpty());
    }

    private static List<IrInstruction> usesAroundCall(IrTemporary value, IrInstruction call) {
        return List.of(new IrMoveInstruction(value, ONE, R),
                new IrBinaryInstruction(temp("before0"), IrBinaryOperator.ADD, value, ONE, R),
                new IrBinaryInstruction(temp("before1"), IrBinaryOperator.ADD, value, ONE, R), call,
                new IrBinaryInstruction(temp("after0"), IrBinaryOperator.ADD, value, ONE, R),
                new IrBinaryInstruction(temp("after1"), IrBinaryOperator.ADD, value, ONE, R), new IrReturnInstruction(value, R));
    }
    private static IrTemporary temp(String name) { return new IrTemporary(name, IrType.INT); }
    private static IrBlock block(String name, IrInstruction... instructions) { return new IrBlock(name, List.of(instructions)); }
    private static IrFunction function(List<IrBlock> blocks) { return new IrFunction("main", MiniType.INT, List.of(), false, blocks, R); }
}
