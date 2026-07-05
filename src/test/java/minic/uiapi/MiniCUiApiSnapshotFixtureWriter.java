package minic.uiapi;

import minic.uiapi.web.MiniCUiApiJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Writes deterministic UIAPI DTO snapshots from real facade executions.
 */
public final class MiniCUiApiSnapshotFixtureWriter {
    private static final String OBSERVATION_SOURCE = """
            // @visual root=node type=[Node] name=node
            struct Node { int key; struct Node *left; struct Node *right; };
            int inc(int value) {
                return value + 1;
            }
            int main() {
                struct Node node;
                node.key = inc(1);
                node.left = NULL;
                node.right = NULL;
                return node.key;
            }
            """;
    private static final String DEBUG_SOURCE = """
            int add(int value) {
                int next = value + 1;
                return next;
            }
            int main() {
                int x = add(1);
                return x;
            }
            """;

    private MiniCUiApiSnapshotFixtureWriter() {
    }

    public static List<SnapshotFile> writeSnapshots(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        cleanJsonFiles(outputDir);
        SnapshotWriter writer = new SnapshotWriter(outputDir);
        writeRealtimeSnapshots(writer);
        writeObservationSnapshots(writer);
        writeDebugSnapshots(writer);
        return writer.snapshots();
    }

    private static void writeRealtimeSnapshots(SnapshotWriter writer) throws IOException {
        MiniCRealtimeAnalysisApi realtime = new MiniCRealtimeAnalysisApi();
        writer.write("realtime-valid", "UiRealtimeAnalysisDto",
                realtime.analyze("realtime-valid.mc", OBSERVATION_SOURCE, 1));
        writer.write("realtime-diagnostic", "UiRealtimeAnalysisDto",
                realtime.analyze("realtime-diagnostic.mc", "int main() { return missing; }", 2));
        writer.write("realtime-tokenize", "List<UiLexerTokenVisualDto>",
                realtime.tokenize("guide-code.mc", "int main() { return 0; }"));
    }

    private static void writeObservationSnapshots(SnapshotWriter writer) throws IOException {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("observation.mc", OBSERVATION_SOURCE);
        api.startSession();
        writeObservationState(writer, "observation-source", api);
        for (String stage : List.of("preprocess", "lexer", "parser", "semantic", "ir", "codegen")) {
            advanceTo(api, stage);
            advanceUntilVisualReady(api, stage);
            writeObservationState(writer, "observation-" + stage, api);
        }
        writer.write("observation-lexer-explicit", "UiStageVisualDto", api.lexerVisualData());
        writer.write("observation-ast-explicit", "UiStageVisualDto", api.astVisualData());
        writer.write("observation-semantic-explicit", "UiStageVisualDto", api.semanticVisualData());
        writer.write("observation-codegen-explicit", "UiStageVisualDto", api.codegenVisualData());
    }

    private static void writeObservationState(SnapshotWriter writer, String scenario, MiniCObservationApi api)
            throws IOException {
        writer.write(scenario + "-state", "UiCurrentStateDto", api.currentState());
        writer.write(scenario + "-stage-data", "UiStageDataDto", api.currentStageData());
        writer.write(scenario + "-visual", "UiStageVisualDto", api.currentStageVisualData());
        writer.write(scenario + "-global", "UiGlobalDataDto", api.globalData());
        writer.write(scenario + "-stage-views", "List<UiStageViewDto>", api.stageViews());
        writer.write(scenario + "-inspector", "UiInspectorModelDto", api.inspectorModel());
    }

    private static void writeDebugSnapshots(SnapshotWriter writer) throws IOException {
        MiniCDebugApi started = startedDebug();
        writer.write("debug-initial-state", "UiDebugStateDto", started.currentState());
        writer.write("debug-metadata", "UiDebugMetadataViewDto", started.metadataView());
        writer.write("debug-data-structure", "UiDebugDataStructureViewDto", started.dataStructureDebugView());
        writer.write("debug-ast", "UiDebugAstViewDto", started.astDebugView());
        writer.write("debug-ir", "UiDebugIrViewDto", started.irDebugView());
        writer.write("debug-asm", "UiDebugAsmViewDto", started.asmDebugView());

        MiniCDebugApi breakpoint = startedDebug();
        breakpoint.setBreakpoint(6);
        writer.write("debug-run-to-breakpoint", "UiDebugStateDto", breakpoint.runToBreakpoint());
        writer.write("debug-step-over", "UiDebugStateDto", breakpoint.stepOver());
        writer.write("debug-step-back", "UiDebugStateDto", breakpoint.stepBack());
        writer.write("debug-back-to-breakpoint", "UiDebugStateDto", breakpoint.backToBreakpoint());

        MiniCDebugApi callSite = debugAtCalleeSecondStatement();
        writer.write("debug-back-to-call-site", "UiDebugStateDto", callSite.backToCallSite());

        MiniCDebugApi stepInto = startedDebug();
        stepInto.setBreakpoint(6);
        stepInto.runToBreakpoint();
        writer.write("debug-step-into", "UiDebugStateDto", stepInto.stepInto());
        writer.write("debug-step-out", "UiDebugStateDto", stepInto.stepOut());

        MiniCDebugApi runToEnd = startedDebug();
        writer.write("debug-run-to-end", "UiDebugStateDto", runToEnd.runToEnd());
    }

    private static MiniCDebugApi startedDebug() {
        MiniCDebugApi api = new MiniCDebugApi();
        api.loadSource("debug-snapshot.mc", DEBUG_SOURCE);
        api.startDebug();
        return api;
    }

    private static MiniCDebugApi debugAtCalleeSecondStatement() {
        MiniCDebugApi api = startedDebug();
        api.setBreakpoint(6);
        api.runToBreakpoint();
        api.stepInto();
        api.stepOver();
        return api;
    }

    private static void advanceTo(MiniCObservationApi api, String stage) {
        for (int guard = 0; !api.currentState().currentStage().equals(stage) && guard < 10000; guard++) {
            api.next();
        }
        if (!api.currentState().currentStage().equals(stage)) {
            throw new IllegalStateException("could not advance observation snapshot to stage " + stage);
        }
    }

    private static void advanceUntilVisualReady(MiniCObservationApi api, String stage) {
        for (int guard = 0; !visualReady(api, stage) && guard < 10000; guard++) {
            if (!api.currentState().currentStage().equals(stage) || !api.currentState().canNext()) {
                break;
            }
            api.next();
        }
    }

    private static boolean visualReady(MiniCObservationApi api, String stage) {
        UiStageVisualDto visual = api.currentStageVisualData();
        return switch (stage) {
            case "lexer" -> !visual.lexerTokens().isEmpty();
            case "parser" -> visual.astRoot() != null;
            case "semantic", "ir" -> visual.semanticRoot() != null || visual.astRoot() != null;
            case "codegen" -> !visual.assemblyLines().isEmpty();
            default -> true;
        };
    }

    private static void cleanJsonFiles(Path outputDir) throws IOException {
        try (Stream<Path> files = Files.list(outputDir)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                Files.delete(file);
            }
        }
    }

    public record SnapshotFile(String fileName, String scenario, String dtoType, Object value) {
    }

    private record SnapshotEnvelope(String scenario, String dtoType, Object value) {
    }

    private static final class SnapshotWriter {
        private final Path outputDir;
        private final MiniCUiApiJson json = new MiniCUiApiJson();
        private final ArrayList<SnapshotFile> snapshots = new ArrayList<>();

        private SnapshotWriter(Path outputDir) {
            this.outputDir = outputDir;
        }

        private void write(String scenario, String dtoType, Object value) throws IOException {
            String fileName = "%03d-%s.json".formatted(snapshots.size() + 1, scenario);
            Path file = outputDir.resolve(fileName);
            Files.writeString(file, json.write(new SnapshotEnvelope(scenario, dtoType, value)), StandardCharsets.UTF_8);
            snapshots.add(new SnapshotFile(fileName, scenario, dtoType, value));
        }

        private List<SnapshotFile> snapshots() {
            return snapshots.stream()
                    .sorted(Comparator.comparing(SnapshotFile::fileName))
                    .toList();
        }
    }
}
