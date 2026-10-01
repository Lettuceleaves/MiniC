package minic.compiler.ir.optimize;

import minic.SourceRange;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.IrConstant;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

final class IrOptimizationPipelineTest {
    private static final SourceRange RANGE = new SourceRange(2, 1, 2, 14);

    @Test void transformationsRunInOrderAndRecordOnlyCompletedPasses() {
        IrResult original = program(1);
        List<Integer> observed = new ArrayList<>();
        var result = pipeline(pass("add-two", ir -> {
            observed.add(value(ir));
            return replaceReturn(ir, value(ir) + 2);
        }), pass("multiply-four", ir -> {
            observed.add(value(ir));
            return replaceReturn(ir, value(ir) * 4);
        })).apply(original);
        assertEquals(List.of(1, 3), observed);
        assertEquals(12, value(result.ir()));
        assertEquals(List.of("add-two", "multiply-four"), result.passNames());
        assertEquals(1, value(original), "optimizer must leave the debugger's original IR intact");
        assertEquals(original.displayNames(), result.ir().displayNames());
        assertEquals(original.currentSubject(), result.ir().currentSubject());
        assertSame(RANGE, result.ir().functions().getFirst().range());
        assertSame(RANGE, result.ir().functions().getFirst().blocks().getFirst().instructions().getFirst().range());
        assertThrows(UnsupportedOperationException.class, () -> result.passNames().add("invented"));
    }

    @Test void baselineAndEmptyOptimizedModeReportNoImaginaryPasses() {
        IrResult original = program(7);
        for (var level : OptimizationLevel.values()) {
            var result = new IrOptimizationPipeline(level, List.of()).apply(original);
            assertSame(original, result.ir());
            assertTrue(result.passNames().isEmpty());
        }
    }

    @Test void malformedInputStopsBeforeTheFirstTransformation() {
        AtomicInteger executions = new AtomicInteger();
        var failure = assertThrows(IllegalArgumentException.class,
                () -> pipeline(pass("must-not-run", ir -> {
                    executions.incrementAndGet(); return ir;
                })).apply(damaged()));
        assertTrue(failure.getMessage().contains("input"), failure.getMessage());
        assertEquals(0, executions.get());
    }

    @Test void malformedIntermediateResultStopsBeforeTheNextTransformation() {
        AtomicInteger executions = new AtomicInteger();
        var failure = assertThrows(IllegalArgumentException.class,
                () -> pipeline(pass("break-cfg", ignored -> damaged()),
                        pass("must-not-run", ir -> { executions.incrementAndGet(); return ir; }))
                        .apply(program(0)));
        assertTrue(failure.getMessage().contains("break-cfg"), failure.getMessage());
        assertEquals(0, executions.get());
    }

    @Test void rejectsNullOutputsAndIdentifiesTheFailingPass() {
        var failure = assertThrows(IllegalArgumentException.class,
                () -> pipeline(pass("null-output", ir -> null)).apply(program(0)));
        assertTrue(failure.getMessage().contains("null-output"), failure.getMessage());
    }

    @Test void rejectsEmptyAndDuplicateNamesAndPassesInBaselineMode() {
        assertThrows(IllegalArgumentException.class, () -> pipeline(pass(" ", ir -> ir)));
        assertThrows(IllegalArgumentException.class,
                () -> pipeline(pass("same", ir -> ir), pass("same", ir -> ir)));
        assertThrows(IllegalArgumentException.class, () -> new IrOptimizationPipeline(
                OptimizationLevel.BASELINE, List.of(pass("hidden", ir -> ir))));
    }

    @Test void configurationIsCopiedAndBaselineRetainsItsExistingIrBoundary() {
        List<IrPass> passes = new ArrayList<>();
        passes.add(pass("first", ir -> ir));
        var pipeline = new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, passes);
        passes.clear();
        assertEquals(List.of("first"), pipeline.apply(program(2)).passNames());
        IrResult damaged = damaged();
        assertSame(damaged, IrOptimizationPipeline.forLevel(OptimizationLevel.BASELINE).apply(damaged).ir());
        assertThrows(IllegalArgumentException.class,
                () -> IrOptimizationPipeline.forLevel(OptimizationLevel.OPTIMIZED).apply(damaged));
    }

    private static IrOptimizationPipeline pipeline(IrPass... passes) {
        return new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, List.of(passes));
    }

    private static IrPass pass(String name, UnaryOperator<IrResult> transform) {
        return new IrPass() {
            public String name() { return name; }
            public IrResult apply(IrResult input) { return transform.apply(input); }
        };
    }

    private static IrResult program(int value) {
        return new IrResult(List.of(new IrFunction("main", MiniType.INT, List.of(), false,
                List.of(new IrBlock("entry", List.of(new IrReturnInstruction(new IrConstant(value), RANGE)))), RANGE)),
                List.of(), List.of(), Set.of(), Set.of(), Map.of(), null, "original", Map.of("main", "main"));
    }

    private static IrResult damaged() {
        return new IrResult(List.of(new IrFunction("main", MiniType.INT, List.of(), false,
                List.of(new IrBlock("entry", List.of(new IrJumpInstruction("absent", RANGE)))), RANGE)));
    }

    private static int value(IrResult ir) {
        return (int) ((IrConstant) ((IrReturnInstruction) ir.functions().getFirst()
                .blocks().getFirst().instructions().getFirst()).value()).value();
    }

    private static IrResult replaceReturn(IrResult ir, int value) {
        return new IrResult(program(value).functions(), ir.stringData(), ir.globalData(), ir.externalFunctionNames(),
                ir.externalObjectNames(), ir.structLayouts(), ir.currentAstNode(), ir.currentSubject(), ir.displayNames());
    }
}
