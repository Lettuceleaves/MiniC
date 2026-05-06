package minic.runtime.debug;

import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 虚拟调用栈帧。
 *
 * @param frameId 栈帧 ID
 * @param functionName 函数名
 * @param parameters 参数
 * @param locals 局部变量
 * @param returnTarget 返回目标摘要；没有时为 {@code null}
 * @param currentSourceRange 当前源码范围；没有时为 {@code null}
 */
public record DebugStackFrame(
        String frameId,
        String functionName,
        List<DebugMemoryEntry> parameters,
        List<DebugMemoryEntry> locals,
        String returnTarget,
        SourceRange currentSourceRange
) {
    /**
     * 创建虚拟调用栈帧。
     */
    public DebugStackFrame {
        Objects.requireNonNull(frameId, "frameId");
        Objects.requireNonNull(functionName, "functionName");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(locals, "locals");
        if (frameId.isBlank()) {
            throw new IllegalArgumentException("frameId must not be blank");
        }
        if (functionName.isBlank()) {
            throw new IllegalArgumentException("functionName must not be blank");
        }
        parameters = List.copyOf(parameters);
        locals = List.copyOf(locals);
    }

    /**
     * 返回返回目标。
     *
     * @return 返回目标 Optional
     */
    public Optional<String> returnTargetOptional() {
        return Optional.ofNullable(returnTarget);
    }

    /**
     * 返回当前源码范围。
     *
     * @return 当前源码范围 Optional
     */
    public Optional<SourceRange> currentSourceRangeOptional() {
        return Optional.ofNullable(currentSourceRange);
    }
}
