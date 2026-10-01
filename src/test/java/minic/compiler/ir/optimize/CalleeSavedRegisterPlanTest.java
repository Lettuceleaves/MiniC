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

final class CalleeSavedRegisterPlanTest {
    private static final SourceRange R = new SourceRange(1, 1, 1, 5);
    private static final IrConstant ONE = new IrConstant(1);

    @Test void profitableCallSurvivorUsesOneFunctionSaveInsteadOfCallSpills() {
        var value = temp("value");
        var code = new ArrayList<IrInstruction>();
        code.add(new IrMoveInstruction(value, ONE, R));
        uses(code, value, "before", 2);
        code.add(new IrCallInstruction(null, "effect", List.of(), false, R));
        uses(code, value, "after", 2);
        code.add(new IrReturnInstruction(value, R));
        var function = function(block("entry", code));
        var legacy = GlobalRegisterPlan.allocate(function);
        assertEquals(List.of(value), legacy.spillsAt("entry", 3));
        var plan = GlobalRegisterPlan.allocate(function, true);
        assertEquals("rbx", plan.registers().get("value"));
        assertEquals(List.of("rbx"), plan.calleeSavedRegisters());
        assertTrue(plan.spillsAt("entry", 3).isEmpty());
    }

    @Test void hotLoopSurvivorPaysNoPerIterationSaveOrRestore() {
        var value = temp("value");
        var done = new ArrayList<IrInstruction>();
        uses(done, value, "after", 4);
        done.add(new IrReturnInstruction(value, R));
        var function = function(
                block("entry", new IrMoveInstruction(value, ONE, R), new IrJumpInstruction("loop", R)),
                block("loop", new IrCallInstruction(null, "effect", List.of(), false, R),
                        new IrBranchInstruction(ONE, "loop", "done", R)), block("done", done));
        assertTrue(GlobalRegisterPlan.allocate(function).stackTemporaries().contains("value"));
        var plan = GlobalRegisterPlan.allocate(function, true);
        assertEquals("rbx", plan.registers().get("value"));
        assertTrue(plan.spillsAt("loop", 0).isEmpty());
    }

    @Test void aSingleDefinitionAndUseDoesNotOpenACalleeSavedRegister() {
        var values = List.of(temp("a"), temp("b"), temp("c"), temp("d"));
        var code = new ArrayList<IrInstruction>();
        for (var value : values) code.add(new IrMoveInstruction(value, ONE, R));
        code.add(new IrCallInstruction(null, "effect", new ArrayList<>(values), false, R));
        code.add(new IrReturnInstruction(ONE, R));
        var plan = GlobalRegisterPlan.allocate(function(block("entry", code)), true);
        assertTrue(plan.calleeSavedRegisters().isEmpty());
        assertEquals(2, plan.registers().size());
        assertEquals(2, plan.stackTemporaries().size());
    }

    @Test void cheapCallSurvivorStaysOnStackRatherThanPayingFunctionSave() {
        var value = temp("value");
        var plan = GlobalRegisterPlan.allocate(function(block("entry", new IrMoveInstruction(value, ONE, R),
                new IrCallInstruction(null, "effect", List.of(), false, R), new IrReturnInstruction(value, R))), true);
        assertTrue(plan.calleeSavedRegisters().isEmpty());
        assertTrue(plan.stackTemporaries().contains("value"));
    }

    @Test void additionalRegistersRelievePressureAndReportOnlyActuallyUsedHomes() {
        var code = new ArrayList<IrInstruction>();
        var values = List.of(temp("a"), temp("b"), temp("c"), temp("d"), temp("e"));
        for (var value : values) code.add(new IrMoveInstruction(value, ONE, R));
        for (int use = 0; use < 3; use++) for (var value : values) uses(code, value, value.name() + use, 1);
        code.add(new IrReturnInstruction(ONE, R));
        var plan = GlobalRegisterPlan.allocate(function(block("entry", code)), true);
        assertEquals(List.of("r10", "r11", "rbx", "r12", "r13"), values.stream().map(value -> plan.registers().get(value.name())).toList());
        assertEquals(List.of("rbx", "r12", "r13"), plan.calleeSavedRegisters());
        assertFalse(plan.calleeSavedRegisters().contains("rsi"));
        assertFalse(plan.calleeSavedRegisters().contains("rdi"));
        assertThrows(UnsupportedOperationException.class, () -> plan.calleeSavedRegisters().clear());
    }

    @Test void indirectCallResultDoesNotRestoreItsPriorSameNameVersion() {
        var value = temp("value"); var kept = temp("kept");
        var code = new ArrayList<IrInstruction>();
        code.add(new IrMoveInstruction(value, ONE, R));
        code.add(new IrMoveInstruction(kept, ONE, R));
        uses(code, kept, "before", 2);
        code.add(new IrIndirectCallInstruction(value, new IrFunctionAddress("effect"), List.of(value), false, R));
        uses(code, kept, "after", 2);
        code.add(new IrReturnInstruction(value, R));
        var plan = GlobalRegisterPlan.allocate(function(block("entry", code)), true);
        assertEquals("rbx", plan.registers().get("kept"));
        assertTrue(plan.spillsAt("entry", 4).isEmpty());
        assertNotEquals(plan.registers().get("kept"), plan.registers().get("value"));
    }

    @Test void floatingAndDeadTailValuesStillCannotGetAnyRegister() {
        var value = temp("value"); var floating = new IrTemporary("floating", IrType.DOUBLE);
        var code = new ArrayList<IrInstruction>();
        code.add(new IrMoveInstruction(value, ONE, R)); uses(code, value, "use", 4);
        code.add(new IrMoveInstruction(floating, new IrFloatConstant(1.0, IrType.DOUBLE), R));
        code.add(new IrReturnInstruction(value, R));
        code.add(new IrMoveInstruction(value, ONE, R));
        var plan = GlobalRegisterPlan.allocate(function(block("entry", code)), true);
        assertTrue(plan.stackTemporaries().containsAll(Set.of("value", "floating")));
        assertTrue(plan.calleeSavedRegisters().isEmpty());
    }

    private static void uses(List<IrInstruction> code, IrTemporary value, String prefix, int count) {
        for (int i = 0; i < count; i++) code.add(new IrBinaryInstruction(temp(prefix + i), IrBinaryOperator.ADD, value, ONE, R));
    }
    private static IrTemporary temp(String name) { return new IrTemporary(name, IrType.INT); }
    private static IrBlock block(String name, IrInstruction... code) { return new IrBlock(name, List.of(code)); }
    private static IrBlock block(String name, List<IrInstruction> code) { return new IrBlock(name, code); }
    private static IrFunction function(IrBlock... blocks) { return new IrFunction("main", MiniType.INT, List.of(), false, List.of(blocks), R); }
}
