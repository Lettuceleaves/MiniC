package minic.uiapi;

import java.util.List;
import java.util.Objects;

/**
 * UI Debug 调用帧 DTO。
 */
public record UiDebugFrameDto(
        String frameId,
        String functionName,
        List<UiDebugVariableDto> parameters,
        List<UiDebugVariableDto> locals,
        String returnTarget,
        UiSourceSpanDto activeRange
) {
    public UiDebugFrameDto {
        Objects.requireNonNull(frameId, "frameId");
        Objects.requireNonNull(functionName, "functionName");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(locals, "locals");
        parameters = List.copyOf(parameters);
        locals = List.copyOf(locals);
    }
}
