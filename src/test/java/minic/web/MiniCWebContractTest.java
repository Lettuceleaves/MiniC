package minic.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCWebContractTest {
    @Test
    void containsAllRequiredRoutesAndNamedPublicSchemas() throws Exception {
        String openApi = Files.readString(Path.of("docs", "api", "minic-web-openapi.yaml"));

        assertThat(openApi)
                .contains("/api/health")
                .contains("/api/compile/sessions")
                .contains("/api/debug/sessions")
                .contains("/api/analysis/realtime")
                .contains("/api/settings")
                .contains("/api/settings/themes");

        for (String schema : requiredSchemas()) {
            assertThat(openApi).contains("    " + schema + ":");
        }

        assertThat(openApi).doesNotContain("application/json:\n              schema:\n                type: object");
    }

    private static List<String> requiredSchemas() {
        return List.of(
                "WebError",
                "CreateSessionRequest",
                "SessionCreatedResponse",
                "CompileSnapshotResponse",
                "DebugSnapshotResponse",
                "UiControlResultDto",
                "UiCurrentStateDto",
                "UiStageDataDto",
                "UiStageVisualDto",
                "UiGlobalDataDto",
                "UiRealtimeAnalysisDto",
                "UiDebugStateDto",
                "UiDebugMetadataViewDto",
                "UiDebugAstViewDto",
                "UiDebugIrViewDto",
                "UiDebugAsmViewDto",
                "UiDebugDataStructureViewDto",
                "SettingsSnapshot",
                "SettingsUpdateRequest",
                "ThemeListResponse"
        );
    }
}
