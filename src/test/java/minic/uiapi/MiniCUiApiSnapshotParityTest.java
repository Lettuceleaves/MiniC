package minic.uiapi;

import minic.uiapi.web.MiniCUiApiJson;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCUiApiSnapshotParityTest {
    @Test
    void writesCanonicalSnapshotsFromRealUiApiFacades() throws Exception {
        Path outputDir = Path.of("build", "uiapi-snapshots");
        List<MiniCUiApiSnapshotFixtureWriter.SnapshotFile> snapshots =
                MiniCUiApiSnapshotFixtureWriter.writeSnapshots(outputDir);

        assertThat(snapshots).extracting(MiniCUiApiSnapshotFixtureWriter.SnapshotFile::dtoType)
                .contains(
                        "UiRealtimeAnalysisDto",
                        "List<UiStageViewDto>",
                        "UiInspectorModelDto",
                        "UiStageVisualDto",
                        "UiDebugStateDto",
                        "UiDebugMetadataViewDto",
                        "UiDebugDataStructureViewDto",
                        "UiDebugAstViewDto",
                        "UiDebugIrViewDto",
                        "UiDebugAsmViewDto"
                );
        assertThat(outputDir).isDirectory();
        assertThat(Files.list(outputDir)).hasSizeGreaterThan(12);
    }

    @Test
    void serializesAndDeserializesNestedDtoRecordsWithoutLoss() throws Exception {
        MiniCUiApiJson json = new MiniCUiApiJson();

        MiniCRealtimeAnalysisApi realtime = new MiniCRealtimeAnalysisApi();
        UiRealtimeAnalysisDto realtimeDto = realtime.analyze("roundtrip.mc", "int main() { return 0; }", 9);
        assertThat(json.read(json.write(realtimeDto), UiRealtimeAnalysisDto.class)).isEqualTo(realtimeDto);

        MiniCObservationApi observation = new MiniCObservationApi();
        observation.loadSource("visual.mc", """
                struct Node { int value; struct Node *next; };
                int main() {
                    struct Node node;
                    node.value = 1;
                    node.next = NULL;
                    return node.value;
                }
                """);
        observation.startSession();
        advanceTo(observation, "lexer");
        UiStageVisualDto visualDto = observation.currentStageVisualData();
        assertThat(json.read(json.write(visualDto), UiStageVisualDto.class)).isEqualTo(visualDto);

        MiniCDebugApi debug = new MiniCDebugApi();
        debug.loadSource("debug-roundtrip.mc", """
                int add(int value) {
                    return value + 1;
                }
                int main() {
                    int x = add(1);
                    return x;
                }
                """);
        UiDebugStateDto debugState = debug.startDebug();
        assertThat(json.read(json.write(debugState), UiDebugStateDto.class)).isEqualTo(debugState);
        assertThat(json.read(json.write(debug.metadataView()), UiDebugMetadataViewDto.class))
                .isEqualTo(debug.metadataView());
        assertThat(json.read(json.write(debug.dataStructureDebugView()), UiDebugDataStructureViewDto.class))
                .isEqualTo(debug.dataStructureDebugView());
        assertThat(json.read(json.write(debug.astDebugView()), UiDebugAstViewDto.class))
                .isEqualTo(debug.astDebugView());
        assertThat(json.read(json.write(debug.irDebugView()), UiDebugIrViewDto.class))
                .isEqualTo(debug.irDebugView());
        assertThat(json.read(json.write(debug.asmDebugView()), UiDebugAsmViewDto.class))
                .isEqualTo(debug.asmDebugView());
    }

    private static void advanceTo(MiniCObservationApi api, String stage) {
        for (int guard = 0; !api.currentState().currentStage().equals(stage) && guard < 1000; guard++) {
            api.next();
        }
        assertThat(api.currentState().currentStage()).isEqualTo(stage);
    }
}
