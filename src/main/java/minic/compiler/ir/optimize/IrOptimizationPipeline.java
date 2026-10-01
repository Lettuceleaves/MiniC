package minic.compiler.ir.optimize;

import minic.compiler.ir.IrResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Native-only optimization boundary. */
public final class IrOptimizationPipeline {
    private final OptimizationLevel level;
    private final List<PassStep> passes;

    public IrOptimizationPipeline(OptimizationLevel level, List<? extends IrPass> passes) {
        this.level = Objects.requireNonNull(level, "level");
        Objects.requireNonNull(passes, "passes");
        if (level == OptimizationLevel.BASELINE && !passes.isEmpty()) {
            throw new IllegalArgumentException("baseline mode cannot run optimization passes");
        }
        var names = new HashSet<String>();
        var configured = new ArrayList<PassStep>();
        for (IrPass pass : passes) {
            Objects.requireNonNull(pass, "pass");
            String name = Objects.requireNonNull(pass.name(), "pass name");
            if (name.isBlank() || !names.add(name)) {
                throw new IllegalArgumentException("pass names must be nonblank and unique: " + name);
            }
            configured.add(new PassStep(name, pass));
        }
        this.passes = List.copyOf(configured);
    }

    public static IrOptimizationPipeline forLevel(OptimizationLevel level) {
        Objects.requireNonNull(level, "level");
        return new IrOptimizationPipeline(level, level == OptimizationLevel.OPTIMIZED
                ? List.of(new DirectCallResolutionPass(), new InitializedCheckEliminationPass(),
                          new EarlySimplificationPass(), new SmallFunctionInliningPass(),
                          new LocalScalarPromotionPass(), new ConstantPropagationPass(),
                          new NonZeroCheckEliminationPass(), new LoopInvariantCodeMotionPass(),
                          new DeadCodeEliminationPass(), new ControlFlowSimplificationPass()) : List.of());
    }

    public OptimizationLevel level() { return level; }

    public Result apply(IrResult input) {
        Objects.requireNonNull(input, "input");
        // Keep the pre-existing native path available as a differential baseline.
        if (level == OptimizationLevel.BASELINE) return new Result(input, List.of());
        verify(input, "input");
        IrResult current = input;
        var applied = new ArrayList<String>();
        for (PassStep step : passes) {
            IrResult next;
            try {
                next = step.pass().apply(current);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("optimization pass '" + step.name() + "' failed", exception);
            }
            if (next == null) {
                throw new IllegalArgumentException("optimization pass '" + step.name() + "' returned null");
            }
            verify(next, "output of pass '" + step.name() + "'");
            current = next;
            applied.add(step.name());
        }
        return new Result(current, applied);
    }

    private static void verify(IrResult ir, String boundary) {
        try {
            IrVerifier.verify(ir);
        } catch (IrVerifier.VerificationException exception) {
            throw new IllegalArgumentException("invalid optimization " + boundary + ": " + exception.getMessage(), exception);
        }
    }

    private record PassStep(String name, IrPass pass) { }

    public record Result(IrResult ir, List<String> passNames) {
        public Result {
            Objects.requireNonNull(ir, "ir");
            passNames = List.copyOf(passNames);
        }
    }
}
