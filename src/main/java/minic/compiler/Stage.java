package minic.compiler;

import minic.source.SourceRange;

/**
 * 编译流水线中的一个可逐步执行阶段。
 *
 * <p>具体阶段必须实现 {@link #step()} 与 {@link #canNext()}。Stage 持有该阶段的
 * 统一 Result；Result 当前只建立归属关系，具体内容后续补充。</p>
 */
public abstract class Stage {
    private final Result result = new Result();

    /**
     * 执行当前阶段的一个最小步骤。
     *
     * @return 本步对应的源码范围；最终空步骤或没有源码对应关系时为 {@code null}
     */
    public abstract SourceRange step();

    /**
     * 返回当前阶段是否还能继续执行。
     */
    public abstract boolean canNext();

    /**
     * 返回阶段结束后是否允许流水线进入下一阶段。
     */
    public abstract boolean succeeded();

    /**
     * 返回当前阶段持有的统一结果对象。
     */
    public final Result stageResult() {
        return result;
    }

    /**
     * 阶段运行结果。当前为空定义。
     */
    public static class Result {
    }
}
