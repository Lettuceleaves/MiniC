package craken.visualization.adapter.pipeline;

import java.util.*;

/** Terminal frames are stored by the actual executed stage, including failed terminals. */
public final class PipelineVisualizationHistory {
    private final Map<Integer, PipelineVisualizationFrame> frames = new LinkedHashMap<>();
    public void save(PipelineVisualizationFrame frame) {
        if (frame.lastStep()) frames.put(frame.stageIndex(), frame);
    }
    public Optional<PipelineVisualizationFrame> frame(int stageIndex) { return Optional.ofNullable(frames.get(stageIndex)); }
    public void clear() { frames.clear(); }
}
