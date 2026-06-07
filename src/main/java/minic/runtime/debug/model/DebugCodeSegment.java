package minic.runtime.debug;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 虚拟进程 code 段。
 *
 * @param functions 函数摘要
 * @param currentFunction 当前函数；没有时为 {@code null}
 * @param currentInstruction 当前 IR 指令；没有时为 {@code null}
 * @param mappedAsmLines 当前映射 ASM 行
 */
public record DebugCodeSegment(
        List<String> functions,
        String currentFunction,
        String currentInstruction,
        List<String> mappedAsmLines
) {
    /**
     * 创建 code 段。
     */
    public DebugCodeSegment {
        Objects.requireNonNull(functions, "functions");
        Objects.requireNonNull(mappedAsmLines, "mappedAsmLines");
        functions = List.copyOf(functions);
        mappedAsmLines = List.copyOf(mappedAsmLines);
    }

    /**
     * 创建空 code 段。
     *
     * @return 空 code 段
     */
    public static DebugCodeSegment empty() {
        return new DebugCodeSegment(List.of(), null, null, List.of());
    }

    /**
     * 返回当前函数。
     *
     * @return 当前函数 Optional
     */
    public Optional<String> currentFunctionOptional() {
        return Optional.ofNullable(currentFunction);
    }

    /**
     * 返回当前 IR 指令。
     *
     * @return 当前 IR 指令 Optional
     */
    public Optional<String> currentInstructionOptional() {
        return Optional.ofNullable(currentInstruction);
    }
}
