package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.IrConstant;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class IrControlFlowTest {
    private static final SourceRange RANGE = new SourceRange(1, 0, 1, 1);

    @Test void firstTerminatorDeterminesEdgesAndDeadTailDoesNotCreateReachability() {
        var function = new IrFunction("f", MiniType.INT, List.of(), false, List.of(
                new IrBlock("entry", List.of(new IrJumpInstruction("body", RANGE), new IrJumpInstruction("dead", RANGE))),
                new IrBlock("body", List.of(new IrBranchInstruction(new IrConstant(1), "body", "exit", RANGE))),
                new IrBlock("exit", List.of(new IrReturnInstruction(new IrConstant(0), RANGE))),
                new IrBlock("dead", List.of())), RANGE);
        var flow = IrControlFlow.analyze(function);
        assertEquals(List.of("body"), flow.successors("entry"));
        assertEquals(Set.of("entry", "body", "exit"), flow.reachable());
        assertEquals(List.of("entry", "body"), flow.predecessors("body"));
        assertEquals(1, flow.effectiveInstructions("entry").size());
        assertEquals(List.of(), flow.successors("dead"));
        assertThrows(UnsupportedOperationException.class, () -> flow.reachable().clear());
        assertThrows(UnsupportedOperationException.class, () -> flow.successors("entry").clear());
    }

    @Test void repeatedBranchDestinationProducesOneEdgeAndMissingTargetsStayVisible() {
        var function = new IrFunction("f", MiniType.INT, List.of(), false, List.of(
                new IrBlock("entry", List.of(new IrBranchInstruction(new IrConstant(1), "missing", "missing", RANGE)))), RANGE);
        var flow = IrControlFlow.analyze(function);
        assertEquals(List.of("missing"), flow.successors("entry"));
        assertEquals(Set.of("entry"), flow.reachable());
    }

    @Test void duplicateLabelsAreRejectedInsteadOfSilentlyReplacingABlock() {
        var function = new IrFunction("f", MiniType.INT, List.of(), false,
                List.of(new IrBlock("entry", List.of()), new IrBlock("entry", List.of())), RANGE);
        assertThrows(IllegalArgumentException.class, () -> IrControlFlow.analyze(function));
    }
}
