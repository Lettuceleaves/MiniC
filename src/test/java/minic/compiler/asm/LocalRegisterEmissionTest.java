package minic.compiler.asm;

import minic.SourceRange;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.IrReturnInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.*;
import minic.compiler.ir.optimize.IrOptimizationPipeline;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

final class LocalRegisterEmissionTest {
    private static final SourceRange R = new SourceRange(1, 1, 1, 5);

    @Test void optimizedEmissionActuallyReplacesStackTransfersWithRegistersWithoutClaimingASmallerFrame() {
        var function = chain(); var ir = new IrResult(List.of(function), List.of(), Set.of());
        var baseline = new Assembler(ir, new IrOptimizationPipeline(OptimizationLevel.BASELINE, List.of()));
        var optimized = new Assembler(ir, new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, List.of()));
        String before = baseline.assemble().text(), after = optimized.assemble().text();
        assertTrue(baseline.succeeded()); assertTrue(optimized.succeeded());
        assertFalse(before.contains("r10")); assertFalse(before.contains("r11"));
        assertTrue(after.contains("r10d") && after.contains("r11d"), after);
        assertEquals(0, stackReferences(after), "this chain has no remaining parameter/local/temporary memory reads or writes");
        assertTrue(stackReferences(before) >= 24);
        assertTrue(after.contains("sub rsp, " + FrameLayout.create(function, true).frameSize()),
                "fallback homes remain reserved even for register-assigned temporaries");
        assertSame(ir, optimized.input().irResult());
        assertSame(function, optimized.input().irResult().functions().getFirst());
        assertTrue(optimized.optimizationResult().passNames().isEmpty(), "backend placement is not an invented IR pass");
    }

    @Test void defaultModeRetainsBaselineAssembly() {
        var ir = new IrResult(List.of(chain()), List.of(), Set.of());
        assertEquals(new Assembler(ir).assemble().text(), new Assembler(ir, OptimizationLevel.BASELINE).assemble().text());
    }

    private static long stackReferences(String text) { return text.lines().filter(line -> line.contains("[rbp-")).count(); }
    private static IrFunction chain() {
        var instructions = new ArrayList<IrInstruction>();
        var previous = new IrTemporary("t0", IrType.INT); instructions.add(new IrMoveInstruction(previous, new IrConstant(1), R));
        for (int i = 1; i < 12; i++) {
            var next = new IrTemporary("t" + i, IrType.INT);
            instructions.add(new IrBinaryInstruction(next, IrBinaryOperator.ADD, previous, new IrConstant(1), R)); previous = next;
        }
        instructions.add(new IrReturnInstruction(previous, R));
        return new IrFunction("main", MiniType.INT, List.of(), false, List.of(new IrBlock("entry", instructions)), R);
    }
}
