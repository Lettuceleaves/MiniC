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

final class LocalRegisterPlanTest {
    private static final SourceRange R = new SourceRange(1, 1, 1, 5);
    private static final IrConstant ONE = new IrConstant(1);

    @Test void closedIntervalsAlternateTheTwoVolatileRegistersForAChain() {
        var instructions = new ArrayList<IrInstruction>();
        var previous = temp("t0"); instructions.add(new IrMoveInstruction(previous, ONE, R));
        for (int i = 1; i < 12; i++) {
            var next = temp("t" + i);
            instructions.add(new IrBinaryInstruction(next, IrBinaryOperator.ADD, previous, ONE, R)); previous = next;
        }
        instructions.add(new IrReturnInstruction(previous, R));
        var plan = LocalRegisterPlan.allocate(function(instructions));
        assertEquals(12, plan.registers().size());
        assertEquals(Set.of("r10", "r11"), new HashSet<>(plan.registers().values()));
        for (int i = 0; i < 12; i++) assertEquals(i % 2 == 0 ? "r10" : "r11", plan.registers().get("t" + i));
        assertTrue(plan.stackTemporaries().isEmpty());
    }

    @Test void pressureFallsBackToStackInsteadOfOverwritingAnUnconsumedOperand() {
        var a = temp("a"); var b = temp("b"); var c = temp("c"); var first = temp("first"); var last = temp("last");
        var plan = LocalRegisterPlan.allocate(function(List.of(new IrMoveInstruction(a, ONE, R),
                new IrMoveInstruction(b, ONE, R), new IrMoveInstruction(c, ONE, R),
                new IrBinaryInstruction(first, IrBinaryOperator.ADD, a, b, R),
                new IrBinaryInstruction(last, IrBinaryOperator.ADD, first, c, R), new IrReturnInstruction(last, R))));
        assertEquals("r10", plan.registers().get("a")); assertEquals("r11", plan.registers().get("b"));
        assertTrue(plan.stackTemporaries().containsAll(Set.of("c", "first")));
        assertEquals("r10", plan.registers().get("last"));
    }

    @Test void directAndIndirectCallsKeepLiveAcrossValuesOnStackButCanConsumeRegisterArgumentsAndCallees() {
        var live = temp("live"); var argument = temp("argument"); var callee = new IrTemporary("callee", IrType.POINTER);
        var first = temp("first"); var result = temp("result");
        var plan = LocalRegisterPlan.allocate(function(List.of(new IrMoveInstruction(live, ONE, R),
                new IrMoveInstruction(argument, ONE, R), new IrCallInstruction(null, "effect", List.of(argument), false, R),
                new IrMoveInstruction(callee, new IrFunctionAddress("effect"), R),
                new IrIndirectCallInstruction(first, callee, List.of(ONE), false, R),
                new IrBinaryInstruction(result, IrBinaryOperator.ADD, live, first, R), new IrReturnInstruction(result, R))));
        assertTrue(plan.stackTemporaries().contains("live"));
        assertTrue(plan.registers().containsKey("argument")); assertTrue(plan.registers().containsKey("callee"));
        assertTrue(plan.registers().containsKey("first"));
    }

    @Test void blockEdgesAndLoopCarriedValuesUseStableStackHomes() {
        var carried = temp("carried"); var local = temp("local");
        var function = new IrFunction("main", MiniType.INT, List.of(), false, List.of(
                new IrBlock("entry", List.of(new IrMoveInstruction(carried, ONE, R), new IrJumpInstruction("loop", R))),
                new IrBlock("loop", List.of(new IrBinaryInstruction(local, IrBinaryOperator.ADD, carried, ONE, R),
                        new IrMoveInstruction(carried, local, R), new IrBranchInstruction(carried, "loop", "done", R))),
                new IrBlock("done", List.of(new IrReturnInstruction(carried, R)))), R);
        var plan = LocalRegisterPlan.allocate(function);
        assertTrue(plan.stackTemporaries().contains("carried"));
        assertEquals("r10", plan.registers().get("local"));
    }

    @Test void nonSsaRedefinitionsPreserveACopyOfTheOldValue() {
        var value = temp("value"); var snapshot = temp("snapshot"); var result = temp("result");
        var plan = LocalRegisterPlan.allocate(function(List.of(new IrMoveInstruction(value, ONE, R),
                new IrMoveInstruction(snapshot, value, R), new IrMoveInstruction(value, new IrConstant(9), R),
                new IrBinaryInstruction(result, IrBinaryOperator.ADD, snapshot, value, R), new IrReturnInstruction(result, R))));
        assertNotEquals(plan.registers().get("value"), plan.registers().get("snapshot"));
        assertTrue(plan.stackTemporaries().contains("result"));
    }

    @Test void unreachableAndDeadTailOccurrencesPinTheEntireNameToStack() {
        var value = temp("value"); var dead = temp("dead");
        var function = new IrFunction("main", MiniType.INT, List.of(), false, List.of(
                new IrBlock("entry", List.of(new IrMoveInstruction(value, ONE, R), new IrReturnInstruction(value, R),
                        new IrMoveInstruction(value, new IrConstant(8), R))),
                new IrBlock("unreachable", List.of(new IrMoveInstruction(dead, ONE, R), new IrReturnInstruction(dead, R)))), R);
        var plan = LocalRegisterPlan.allocate(function);
        assertTrue(plan.registers().isEmpty()); assertEquals(Set.of("value", "dead"), plan.stackTemporaries());
    }

    @Test void allIntegerWidthsAndPointersAreEligibleWhileFloatingValuesKeepStackHomes() {
        var instructions = new ArrayList<IrInstruction>();
        for (IrType type : IrType.values()) {
            var value = new IrTemporary(type.name(), type);
            instructions.add(new IrMoveInstruction(value, type.isFloatingScalar()
                    ? new IrFloatConstant(1, type) : new IrConstant(1, type), R));
        }
        instructions.add(new IrReturnInstruction(ONE, R));
        var plan = LocalRegisterPlan.allocate(function(instructions));
        assertEquals(Set.of("FLOAT", "DOUBLE"), plan.stackTemporaries());
        for (IrType type : IrType.values()) if (!type.isFloatingScalar()) assertEquals("r10", plan.registers().get(type.name()));
    }

    @Test void planIsDeterministicAndReadOnly() {
        var function = function(List.of(new IrMoveInstruction(temp("value"), ONE, R), new IrReturnInstruction(temp("value"), R)));
        var plan = LocalRegisterPlan.allocate(function);
        assertEquals(plan.registers(), LocalRegisterPlan.allocate(function).registers());
        assertThrows(UnsupportedOperationException.class, () -> plan.registers().put("other", "r10"));
        assertThrows(UnsupportedOperationException.class, () -> plan.stackTemporaries().add("other"));
        assertEquals("local-volatile-registers", plan.strategyName());
    }

    private static IrTemporary temp(String name) { return new IrTemporary(name, IrType.INT); }
    private static IrFunction function(List<IrInstruction> instructions) {
        return new IrFunction("main", MiniType.INT, List.of(), false, List.of(new IrBlock("entry", instructions)), R);
    }
}
