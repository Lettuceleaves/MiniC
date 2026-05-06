package minic.runtime.debug;

import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Debugger 当前执行游标。
 *
 * @param functionName 当前函数名
 * @param basicBlockId 当前 IR 基本块 ID
 * @param instructionId 当前 IR 指令 ID
 * @param sourceRange 当前源码范围；没有时为 {@code null}
 * @param astNodeId 当前 AST 节点 ID；没有时为 {@code null}
 * @param asmLineIds 当前关联 ASM 行 ID
 */
public record DebugCursor(
        String functionName,
        String basicBlockId,
        String instructionId,
        SourceRange sourceRange,
        String astNodeId,
        List<String> asmLineIds
) {
    /**
     * 创建执行游标。
     */
    public DebugCursor {
        Objects.requireNonNull(functionName, "functionName");
        Objects.requireNonNull(basicBlockId, "basicBlockId");
        Objects.requireNonNull(instructionId, "instructionId");
        Objects.requireNonNull(asmLineIds, "asmLineIds");
        if (functionName.isBlank()) {
            throw new IllegalArgumentException("functionName must not be blank");
        }
        if (basicBlockId.isBlank()) {
            throw new IllegalArgumentException("basicBlockId must not be blank");
        }
        if (instructionId.isBlank()) {
            throw new IllegalArgumentException("instructionId must not be blank");
        }
        asmLineIds = List.copyOf(asmLineIds);
    }

    /**
     * 创建初始占位游标。
     *
     * @return 初始游标
     */
    public static DebugCursor initial() {
        return new DebugCursor("main", "entry", "before-start", null, null, List.of());
    }

    /**
     * 返回源码范围。
     *
     * @return 源码范围 Optional
     */
    public Optional<SourceRange> sourceRangeOptional() {
        return Optional.ofNullable(sourceRange);
    }

    /**
     * 返回 AST 节点 ID。
     *
     * @return AST 节点 ID Optional
     */
    public Optional<String> astNodeIdOptional() {
        return Optional.ofNullable(astNodeId);
    }
}
