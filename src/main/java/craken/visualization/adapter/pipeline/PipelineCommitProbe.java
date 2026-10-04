package craken.visualization.adapter.pipeline;

/** Optional commit diagnostic hook; callbacks cannot publish a partial frame. */
@FunctionalInterface
public interface PipelineCommitProbe {
    PipelineCommitProbe NONE = name -> { };
    void checkpoint(String name);
}
