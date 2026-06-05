package minic.web.dto;

import minic.uiapi.UiCurrentStateDto;
import minic.uiapi.UiDebugAsmViewDto;
import minic.uiapi.UiDebugAstViewDto;
import minic.uiapi.UiDebugDataStructureViewDto;
import minic.uiapi.UiDebugIrViewDto;
import minic.uiapi.UiDebugMetadataViewDto;
import minic.uiapi.UiDebugStateDto;
import minic.uiapi.UiGlobalDataDto;
import minic.uiapi.UiStageDataDto;
import minic.uiapi.UiStageVisualDto;

/**
 * Web session request and response records.
 */
public final class WebSessionDtos {
    private WebSessionDtos() {
    }

    public record CreateSessionRequest(String sourceName, String sourceText) {
        public CreateSessionRequest {
            if (sourceName == null || sourceName.isBlank()) {
                throw new IllegalArgumentException("sourceName is required");
            }
            if (sourceText == null) {
                throw new IllegalArgumentException("sourceText is required");
            }
        }
    }

    public record SessionCreatedResponse(String sessionId, long version) {
    }

    public record SessionClosedResponse(String sessionId, long version, boolean closed) {
    }

    public record CommandInputRequest(String standardInput) {
    }

    public record CompileSnapshotResponse(
            UiCurrentStateDto state,
            UiStageDataDto stage,
            UiGlobalDataDto global,
            UiStageVisualDto visual
    ) {
    }

    public record BreakpointRequest(int line) {
    }

    public record DebugSnapshotResponse(
            UiDebugStateDto state,
            UiDebugMetadataViewDto metadata,
            UiDebugAstViewDto ast,
            UiDebugIrViewDto ir,
            UiDebugAsmViewDto asm,
            UiDebugDataStructureViewDto dataStructure
    ) {
    }
}
