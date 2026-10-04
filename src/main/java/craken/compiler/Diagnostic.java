package craken.compiler;

import craken.SourceRange;

import java.util.Objects;

/**
 * 表示编译或运行阶段产生的一条结构化诊断。
 *
 * @param code 稳定诊断编号，例如 {@code LEX001}
 * @param severity 诊断严重级别
 * @param message 具体错误原因
 * @param solution 预期解决方法
 * @param range 诊断关联的源码范围
 */
public record Diagnostic(
        String code,
        Severity severity,
        String message,
        String solution,
        SourceRange range
) {
    /**
     * 创建诊断对象。
     *
     * @param code 稳定诊断编号，例如 {@code LEX001}
     * @param severity 诊断严重级别
     * @param message 具体错误原因
     * @param solution 预期解决方法
     * @param range 诊断关联的源码范围
     */
    public Diagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(solution, "solution");
        Objects.requireNonNull(range, "range");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (solution.isBlank()) {
            throw new IllegalArgumentException("solution must not be blank");
        }
    }

    /**
     * 兼容内部尚未细分建议的诊断创建点。新诊断应优先显式传入 solution。
     */
    public Diagnostic(String code, Severity severity, String message, SourceRange range) {
        this(code, severity, message, defaultSolution(severity, message), range);
    }

    /** 生成可直接放入 Stage.Result.error 的完整错误文本。 */
    public String describe() {
        return "[" + code + "] 原因：" + message + "\n预期解决：" + solution;
    }

    private static String defaultSolution(Severity severity, String message) {
        return switch (severity) {
            case ERROR -> "请修改对应源码或构建配置，使其满足：" + message;
            case WARNING -> "请检查该位置，必要时按提示调整。";
            case INFO -> "无需修复，请根据提示继续。";
        };
    }

    /**
     * 诊断严重级别。
     */
    public enum Severity {
        /** 阻止当前阶段继续成功完成的错误。 */
        ERROR,

        /** 不阻止继续执行但需要展示给用户的警告。 */
        WARNING,

        /** 辅助说明信息。 */
        INFO
    }
}
