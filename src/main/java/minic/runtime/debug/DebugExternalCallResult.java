package minic.runtime.debug;

import java.util.Objects;

/**
 * 外部函数 debug stub 返回结果。
 *
 * @param returnValue 返回值
 * @param stdoutAppend 追加到虚拟 stdout 的文本
 * @param description 事件说明
 */
public record DebugExternalCallResult(
        DebugValue returnValue,
        String stdoutAppend,
        String description
) {
    /**
     * 创建外部调用结果。
     */
    public DebugExternalCallResult {
        Objects.requireNonNull(returnValue, "returnValue");
        Objects.requireNonNull(stdoutAppend, "stdoutAppend");
        Objects.requireNonNull(description, "description");
    }
}
