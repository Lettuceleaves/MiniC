package minic.compiler.ir.optimize;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.Stage;
import minic.compiler.asm.Assembler;
import minic.compiler.execute.ExecutableRunner;
import minic.compiler.ir.IrLowerer;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryOperator;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.value.IrValue.IrConstant;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(60)
final class NativeOptimizationEntryTest {
    @TempDir Path temporary;
    private static final SourceFile SOURCE = new SourceFile("native-optimization.c", "int main(){ return 7; }");

    @Test void defaultAndExplicitModesKeepStageOrderAndOriginalIr() {
        var source = new SourceFile("native-optimization.cpp", """
                struct Box { int x; int read() const { return x; } };
                int main(){ const Box value = {7}; return value.read(); }
                """);
        var baseline = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM);
        var explicit = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM, OptimizationLevel.BASELINE);
        var optimized = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM, OptimizationLevel.OPTIMIZED);
        assertEquals(stageNames(baseline), stageNames(optimized));
        var original = optimized.runToIr();
        var irStage = stage(optimized, IrLowerer.class);
        var assembler = stage(optimized, Assembler.class);
        assertSame(original, irStage.result());
        assertEquals(5, optimized.currentStageIndex());
        assertEquals(0, assembler.work().completedLineCount(), "runToIr must stop before ASM");
        assertEquals(OptimizationLevel.OPTIMIZED, assembler.optimizationLevel());
        optimized.runThrough(assembler);
        assertSame(original, irStage.result());
        assertDoesNotThrow(() -> IrVerifier.verify(assembler.input().irResult()));
        assertEquals(List.of("known-function-call-resolution", "private-address-normalization", "initialized-check-elimination", "early-simplification",
                        "small-function-inlining", "post-inlining-scalar-preparation", "local-scalar-promotion", "read-only-parameter-promotion", "constant-propagation",
                        "nonzero-check-elimination", "loop-invariant-code-motion", "block-copy-propagation", "dead-code-elimination", "control-flow-simplification"),
                assembler.optimizationResult().passNames());
        for (CompilerApi api : List.of(baseline, explicit)) {
            var other = stage(api, Assembler.class);
            api.runThrough(other);
            assertEquals(OptimizationLevel.BASELINE, other.optimizationLevel());
        }
        assertEquals(stage(baseline, Assembler.class).result().text(), stage(explicit, Assembler.class).result().text());
    }

    @Test void defaultOptimizationMovesLoopWorkWithoutChangingSourceIrOrDebugHistory() {
        var source = new SourceFile("default-loop-motion.cpp", """
                int work(int a,int b,int n){int x=a;int y=b;int total=0;
                    for(int i=0;i<n;i++){total=total+x*y;}return total;}
                int main(){volatile int a=3;volatile int b=7;return work(a,b,5);}
                """);
        var api = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM, OptimizationLevel.OPTIMIZED);
        var original = api.runToIr(); var sourceFunctions = original.functions();
        var originalWork = original.functions().stream().filter(f -> original.displayName(f.name()).equals("work")).findFirst().orElseThrow();
        var sourceMultiply = originalWork.blocks().stream().filter(b -> !b.label().equals("entry"))
                .flatMap(b -> b.instructions().stream()).filter(i -> i instanceof IrBinaryInstruction binary
                        && binary.operator() == IrBinaryOperator.MULTIPLY).findFirst().orElseThrow();
        var assembler = stage(api, Assembler.class); api.runThrough(assembler);
        assertTrue(assembler.succeeded(), () -> assembler.errors().toString());
        var optimized = assembler.optimizationResult().ir();
        var work = optimized.functions().stream().filter(f -> optimized.displayName(f.name()).equals("work")).findFirst().orElseThrow();
        var moved = work.blocks().getFirst().instructions().stream().filter(i -> i instanceof IrBinaryInstruction binary
                && binary.operator() == IrBinaryOperator.MULTIPLY).findFirst().orElseThrow();
        assertEquals(sourceMultiply.range(), moved.range());
        assertTrue(work.blocks().stream().filter(b -> !b.label().equals("entry")).flatMap(b -> b.instructions().stream())
                .noneMatch(i -> i instanceof IrBinaryInstruction binary && binary.operator() == IrBinaryOperator.MULTIPLY));
        assertSame(sourceFunctions, original.functions());
        assertSame(original, stage(api, IrLowerer.class).result());
        var debug = DebugApi.fromIr(source, original, ""); var history = new ArrayList<Debugger.Context>(); history.add(debug.current());
        for (int steps = 0; debug.canNext() && steps < 10_000; steps++) history.add(debug.next());
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status());
        assertEquals(105, debug.current().runtime().returnValue().integer());
        for (int i = history.size() - 2; i >= 0; i--) assertSame(history.get(i), debug.previous());
        for (int i = 1; i < history.size(); i++) assertSame(history.get(i), debug.next());
    }

    @Test void aTransformationChangesOnlyNativeInputAndRunsExactlyOnce() {
        IrResult original = new CompilerApi(SOURCE).runToIr();
        AtomicInteger executions = new AtomicInteger();
        var assembler = new Assembler(original, replacementPipeline(executions));
        assertEquals(19, returnValue(assembler.input().irResult()));
        assertEquals(7, returnValue(original));
        var obj = new ObjBuilder(SOURCE, assembler, temporary, "transformed");
        var link = new Linker(SOURCE, obj, temporary, "transformed");
        var pipeline = new CompilerApi(List.of(assembler, obj, link));
        pipeline.runThrough(link);
        assertTrue(link.succeeded(), () -> "obj=" + obj.errors() + ", link=" + link.errors());
        var runner = new ExecutableRunner();
        var result = runner.run(SOURCE, link.result().executableArtifactOptional().orElseThrow());
        assertTrue(runner.errors().isEmpty(), () -> runner.errors().toString());
        assertEquals(19, result.exitCode());
        assertEquals(List.of("test-replace-return"), assembler.optimizationResult().passNames());
        assertEquals(1, executions.get());

        var debug = DebugApi.fromIr(SOURCE, original, "");
        var history = new ArrayList<Debugger.Context>();
        history.add(debug.current());
        for (int i = 0; debug.canNext() && i < 100; i++) history.add(debug.next());
        assertFalse(debug.canNext());
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status());
        assertEquals(7, debug.current().runtime().returnValue().integer());
        for (int i = history.size() - 2; i >= 0; i--) assertSame(history.get(i), debug.previous());
        for (int i = 1; i < history.size(); i++) assertSame(history.get(i), debug.next());
        assertEquals(7, returnValue(original));
        var returnRange = original.functions().getFirst().blocks().getFirst().instructions().getFirst().range();
        assertTrue(java.util.stream.IntStream.range(0, assembler.work().completedLineCount())
                .anyMatch(i -> assembler.work().sourceRangeAt(i).filter(returnRange::equals).isPresent()));
    }

    @Test void stageBackedAssemblerWaitsForIrAndDoesNotOverwriteTheLowererResult() {
        var api = new CompilerApi(SOURCE, OptimizationLevel.OPTIMIZED);
        var lowerer = stage(api, IrLowerer.class);
        var counter = new AtomicInteger();
        var assembler = new Assembler(lowerer, replacementPipeline(counter));
        assertThrows(IllegalStateException.class, assembler::input);
        assertEquals(0, counter.get());
        IrResult original = api.runToIr();
        assembler.assemble();
        assertSame(original, lowerer.result());
        assertEquals(7, returnValue(original));
        assertEquals(19, returnValue(assembler.input().irResult()));
        assembler.optimizationResult();
        assertEquals(1, counter.get());
    }

    @Test void badTransformationIsRejectedBeforeAnyAssemblyIsEmitted() {
        IrResult original = new CompilerApi(SOURCE).runToIr();
        IrPass damage = new IrPass() {
            public String name() { return "test-damaged-cfg"; }
            public IrResult apply(IrResult ir) {
                var function = ir.functions().getFirst();
                return new IrResult(List.of(new IrFunction(function.name(), function.returnType(), function.parameters(),
                        function.variadic(), List.of(new IrBlock("entry", List.of(
                        new IrJumpInstruction("missing", function.range())))), function.range())));
            }
        };
        var assembler = new Assembler(original, new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, List.of(damage)));
        var failure = assertThrows(IllegalArgumentException.class, assembler::step);
        assertTrue(failure.getMessage().contains("test-damaged-cfg"), failure.getMessage());
        assertEquals(0, assembler.work().completedLineCount());
        assertEquals(7, returnValue(original));
    }

    private static IrOptimizationPipeline replacementPipeline(AtomicInteger executions) {
        // Deliberately observable test transformation, never registered in product optimization.
        IrPass pass = new IrPass() {
            public String name() { return "test-replace-return"; }
            public IrResult apply(IrResult ir) {
                executions.incrementAndGet();
                var function = ir.functions().getFirst();
                var instruction = function.blocks().getFirst().instructions().getFirst();
                var changed = new IrFunction(function.name(), function.returnType(), function.parameters(), function.variadic(),
                        List.of(new IrBlock("entry", List.of(new IrReturnInstruction(new IrConstant(19), instruction.range())))), function.range());
                return new IrResult(List.of(changed), ir.stringData(), ir.globalData(), ir.externalFunctionNames(),
                        ir.externalObjectNames(), ir.structLayouts(), ir.currentAstNode(), ir.currentSubject(), ir.displayNames());
            }
        };
        return new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, List.of(pass));
    }

    private static int returnValue(IrResult ir) {
        return (int) ((IrConstant) ((IrReturnInstruction) ir.functions().getFirst().blocks().getFirst()
                .instructions().getFirst()).value()).value();
    }

    private static List<String> stageNames(CompilerApi api) {
        return api.stages().stream().map(stage -> stage.getClass().getName()).toList();
    }

    private static <T extends Stage> T stage(CompilerApi api, Class<T> type) {
        return api.stages().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }
}
