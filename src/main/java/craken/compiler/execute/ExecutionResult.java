package craken.compiler.execute;

import craken.compiler.Stage;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * 可执行产物运行结果。
 *
 * @param stdout 标准输出文本
 * @param stderr 标准错误文本
 * @param exitCode 进程退出码；运行失败导致未启动或被中断时为 {@code null}
 */
public record ExecutionResult(String stdout, String stderr, Integer exitCode)
        implements Stage.Context {
    /**
     * 创建运行结果，并防御性复制诊断列表。
     *
     * @param stdout 标准输出文本
     * @param stderr 标准错误文本
     * @param exitCode 进程退出码；运行失败导致未启动或被中断时为 {@code null}
     */
    public ExecutionResult {
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
    }

    /**
     * 返回退出码。
     *
     * @return 退出码；不存在时为空
     */
    public OptionalInt exitCodeOptional() {
        if (exitCode == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(exitCode);
    }
}
