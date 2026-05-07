package minic.uiapi;

import java.util.List;
import java.util.Objects;

/**
 * UI Debug 虚拟进程空间 DTO。
 */
public record UiDebugProcessSpaceDto(
        String currentFunctionName,
        String currentInstructionId,
        List<String> functions,
        List<UiDebugVariableDto> staticValues,
        List<UiDebugFrameDto> stackFrames,
        List<UiDebugVariableDto> heapValues,
        String stdin,
        String stdout,
        String stderr
) {
    public UiDebugProcessSpaceDto {
        Objects.requireNonNull(currentFunctionName, "currentFunctionName");
        Objects.requireNonNull(currentInstructionId, "currentInstructionId");
        Objects.requireNonNull(functions, "functions");
        Objects.requireNonNull(staticValues, "staticValues");
        Objects.requireNonNull(stackFrames, "stackFrames");
        Objects.requireNonNull(heapValues, "heapValues");
        Objects.requireNonNull(stdin, "stdin");
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
        functions = List.copyOf(functions);
        staticValues = List.copyOf(staticValues);
        stackFrames = List.copyOf(stackFrames);
        heapValues = List.copyOf(heapValues);
    }
}
