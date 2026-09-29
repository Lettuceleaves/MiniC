package minic.session;

import minic.diagnostics.Diagnostic;
import minic.source.SourceRange;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 编译观测会话与 UI 之间的数据协议。 */
public final class Observation {
    private Observation() {
    }

    public enum StageId {
        SOURCE("source"),
        PREPROCESS("preprocess"),
        LEXER("lexer"),
        PARSER("parser"),
        SEMANTIC("semantic"),
        IR("ir"),
        ASM("asm"),
        OBJ("obj"),
        LINK("link"),
        EXECUTION("execution");

        private final String id;

        StageId(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public enum PlaybackMode {
        PAUSED,
        PLAYING,
        FAST_PLAYING
    }

    public enum Outcome {
        ADVANCED,
        STAGE_COMPLETED,
        FAILED,
        CANNOT_ADVANCE,
        UNSUPPORTED
    }

    public record ControlResult(
            Outcome outcome,
            StageId stage,
            String title,
            String description,
            List<Diagnostic> diagnostics
    ) {
        public ControlResult {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(description, "description");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    public record Capabilities(
            boolean canNext,
            boolean canPrevious,
            boolean canPlay,
            boolean canPlayFast,
            boolean canPause,
            boolean canReversePlay
    ) {
    }

    public record CurrentState(
            String sourceName,
            StageId currentStage,
            long globalStepIndex,
            long stageStepIndex,
            PlaybackMode playbackMode,
            Duration frameInterval,
            SourceRange sourceRange,
            String title,
            String description,
            List<Diagnostic> diagnostics,
            Capabilities capabilities
    ) {
        public CurrentState {
            Objects.requireNonNull(sourceName, "sourceName");
            Objects.requireNonNull(currentStage, "currentStage");
            Objects.requireNonNull(playbackMode, "playbackMode");
            Objects.requireNonNull(frameInterval, "frameInterval");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(description, "description");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
            Objects.requireNonNull(capabilities, "capabilities");
        }

        public Optional<SourceRange> sourceRangeOptional() {
            return Optional.ofNullable(sourceRange);
        }

        public boolean canNext() {
            return capabilities.canNext();
        }

        public boolean canPrevious() {
            return capabilities.canPrevious();
        }

        public boolean canPlay() {
            return capabilities.canPlay();
        }

        public boolean canPlayFast() {
            return capabilities.canPlayFast();
        }

        public boolean canPause() {
            return capabilities.canPause();
        }

        public boolean canReversePlay() {
            return capabilities.canReversePlay();
        }
    }

    public record Progress(long completedSteps, long totalSteps, boolean completed) {
    }

    public record StageData(
            StageId stage,
            Progress progress,
            List<String> inputSummary,
            String currentItem,
            List<String> accumulatedOutput,
            List<Diagnostic> diagnostics
    ) {
        public StageData {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(progress, "progress");
            inputSummary = List.copyOf(Objects.requireNonNull(inputSummary, "inputSummary"));
            Objects.requireNonNull(currentItem, "currentItem");
            accumulatedOutput = List.copyOf(Objects.requireNonNull(accumulatedOutput, "accumulatedOutput"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    public record GlobalData(
            String source,
            List<String> stageSummaries,
            List<Diagnostic> diagnostics,
            List<String> preprocessSummary,
            List<String> tokenSummary,
            List<String> astSummary,
            List<String> semanticSummary,
            List<String> irSummary,
            List<String> assemblySummary,
            List<String> artifactSummary,
            List<String> executionInputSummary,
            List<String> executionOutputSummary
    ) {
        public GlobalData {
            Objects.requireNonNull(source, "source");
            stageSummaries = List.copyOf(stageSummaries);
            diagnostics = List.copyOf(diagnostics);
            preprocessSummary = List.copyOf(preprocessSummary);
            tokenSummary = List.copyOf(tokenSummary);
            astSummary = List.copyOf(astSummary);
            semanticSummary = List.copyOf(semanticSummary);
            irSummary = List.copyOf(irSummary);
            assemblySummary = List.copyOf(assemblySummary);
            artifactSummary = List.copyOf(artifactSummary);
            executionInputSummary = List.copyOf(executionInputSummary);
            executionOutputSummary = List.copyOf(executionOutputSummary);
        }
    }
}
