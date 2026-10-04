package craken.visualization.adapter.pipeline;

import craken.compiler.SourceFile;
import craken.compiler.parser.node.AstNode;
import craken.visualization.snapshot.VisualizationSnapshot;
import craken.visualization.adapter.pipeline.projector.SourceProjector;
import java.util.*;

/** Serial owner of two private core sessions; publishes only complete immutable frames. */
public final class PipelineVisualizationSession implements AutoCloseable {
    private final PipelineProjectionRegistry registry;
    private final PipelineCommitProbe probe;
    private final PipelineVisualizationHistory history = new PipelineVisualizationHistory();
    private final Set<AstNode> known = Collections.newSetFromMap(new IdentityHashMap<>());
    private ProjectionContainer input, output;
    private PipelineVisualizationFrame frame;
    private boolean closed;
    public PipelineVisualizationSession() { this(PipelineProjectionRegistry.standard(), PipelineCommitProbe.NONE); }
    public PipelineVisualizationSession(PipelineProjectionRegistry registry, PipelineCommitProbe probe) {
        this.registry = Objects.requireNonNull(registry); this.probe = Objects.requireNonNull(probe);
    }
    public synchronized void initialize(SourceFile source) {
        if (closed || frame != null) throw new IllegalStateException("Already initialized or closed");
        input = new ProjectionContainer(); output = new ProjectionContainer();
        input.apply(source == null ? PipelinePlans.empty("输入") : SourceProjector.source(source), "initial");
        output.apply(PipelinePlans.empty("输出"), "initial");
        frame = new PipelineVisualizationFrame(-1, -1, "", "", "", null, false, false, input.snapshot(), output.snapshot());
    }
    public synchronized PipelineVisualizationFrame project(PipelineStepObservation observation) {
        if (closed || frame == null) throw new IllegalStateException("Session not active");
        boolean rotate = frame.stageIndex() >= 0 && observation.stageIndex() != frame.stageIndex();
        if (rotate && (!frame.lastStep() || !frame.succeeded())) throw new IllegalStateException("Only a successful terminal may rotate");
        var beforeInput = input.session.snapshot();
        var beforeOutput = output.session.snapshot();
        var candidateInput = (rotate ? output : input).draft();
        var candidateOutput = rotate ? new ProjectionContainer() : output.draft();
        var prepared = new PreparedFrame(beforeInput, beforeOutput);
        PipelineVisualizationFrame candidate;
        try {
            StageProjection projected = registry.project(observation, candidateInput.plan);
            String sourceStep = observation.result().stageType().getName() + ":" + observation.result().stepIndex();
            candidateInput.apply(projected.input(), sourceStep);
            probe.checkpoint("INPUT");
            candidateOutput.apply(projected.output(), sourceStep);
            probe.checkpoint("OUTPUT");
            prepared.positions = new PipelinePositionTransition(known, candidateInput.locations, candidateOutput.locations);
            prepared.positions.apply();
            probe.checkpoint("POSITIONS");
            var result = observation.result();
            candidate = new PipelineVisualizationFrame(observation.stageIndex(), result.stepIndex(), result.stageType().getSimpleName(),
                    result.operation(), result.error(), result.sourceRange(), result.lastStep(), result.succeeded(),
                    candidateInput.snapshot(), candidateOutput.snapshot());
            probe.checkpoint("BEFORE_PUBLISH");
        } catch (RuntimeException | Error failure) {
            if (prepared.positions != null) prepared.positions.restore();
            input.session.restore(prepared.beforeInput);
            output.session.restore(prepared.beforeOutput);
            if (rotate) candidateOutput.session.close();
            throw failure;
        }
        ProjectionContainer retired = rotate ? input : null;
        input = candidateInput; output = candidateOutput;
        known.clear();
        known.addAll(prepared.positions.nodes());
        frame = candidate;
        history.save(candidate);
        if (retired != null) retired.session.close();
        return candidate;
    }
    public synchronized PipelineVisualizationFrame frame() { return frame; }
    public synchronized PipelineVisualizationFrame frameFor(int stageIndex) { return history.frame(stageIndex).orElse(frame); }
    public synchronized int activeContainerCount() { return closed || frame == null ? 0 : 2; }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        known.forEach(node -> node.visualSlots().clear()); known.clear(); history.clear();
        if (input != null) input.session.close(); if (output != null) output.session.close();
    }
    private static final class PreparedFrame {
        final VisualizationSnapshot beforeInput, beforeOutput;
        PipelinePositionTransition positions;
        PreparedFrame(VisualizationSnapshot beforeInput, VisualizationSnapshot beforeOutput) {
            this.beforeInput = beforeInput; this.beforeOutput = beforeOutput;
        }
    }
}
