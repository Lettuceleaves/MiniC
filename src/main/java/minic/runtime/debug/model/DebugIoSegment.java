package minic.runtime.debug;

import java.util.Objects;

/**
 * 虚拟进程 IO 段。
 *
 * @param stdin 标准输入
 * @param stdout 标准输出
 * @param stderr 标准错误
 */
public record DebugIoSegment(String stdin, String stdout, String stderr) {
    /**
     * 创建 IO 段。
     */
    public DebugIoSegment {
        Objects.requireNonNull(stdin, "stdin");
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
    }

    /**
     * 创建空 IO 段。
     *
     * @return 空 IO 段
     */
    public static DebugIoSegment empty() {
        return new DebugIoSegment("", "", "");
    }

    /**
     * 返回追加 stdout 后的新 IO 段。
     *
     * @param text 追加文本
     * @return 新 IO 段
     */
    public DebugIoSegment appendStdout(String text) {
        return new DebugIoSegment(stdin, stdout + Objects.requireNonNull(text, "text"), stderr);
    }
}
