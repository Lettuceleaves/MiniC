package minic.compiler.stage;

import minic.diagnostics.Diagnostic;
import minic.runtime.step.CompileStage;

import java.util.List;
import java.util.Objects;

/**
 * 编译阶段完成后的输出结果。
 *
 * @param stage 阶段标识
 * @param output 阶段输出数据
 * @param diagnostics 阶段 diagnostics
 * @param successful 是否成功完成
 * @param <O> 输出数据类型
 */
public record CompilerStageResult<O extends CompilerStageOutput>(
        CompileStage stage,
        O output,
        List<Diagnostic> diagnostics,
        boolean successful
) {
    /**
     * 创建阶段结果，并防御性复制 diagnostics。
     *
     * @param stage 阶段标识
     * @param output 阶段输出数据
     * @param diagnostics 阶段 diagnostics
     * @param successful 是否成功完成
     */
    public CompilerStageResult {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * 创建成功结果。
     *
     * @param stage 阶段标识
     * @param output 阶段输出数据
     * @param <O> 输出数据类型
     * @return 阶段结果
     */
    public static <O extends CompilerStageOutput> CompilerStageResult<O> success(CompileStage stage, O output) {
        return new CompilerStageResult<>(stage, output, List.of(), true);
    }

    /**
     * 创建失败结果。
     *
     * @param stage 阶段标识
     * @param diagnostics 阶段 diagnostics
     * @param <O> 输出数据类型
     * @return 阶段结果
     */
    public static <O extends CompilerStageOutput> CompilerStageResult<O> failure(
            CompileStage stage,
            List<Diagnostic> diagnostics
    ) {
        return new CompilerStageResult<>(stage, null, diagnostics, false);
    }
}
