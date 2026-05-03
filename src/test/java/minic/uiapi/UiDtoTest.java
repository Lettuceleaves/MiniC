package minic.uiapi;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UiDtoTest {
    @Test
    void currentStateDtoUsesUiFriendlyFields() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("dto.mc", "int main() { return 0; }");
        api.startSession();

        UiCurrentStateDto state = api.currentState();

        assertThat(state.sourceName()).isEqualTo("dto.mc");
        assertThat(state.currentStage()).isEqualTo("lexer");
        assertThat(state.playbackMode()).isEqualTo("PAUSED");
        assertThat(state.frameIntervalMillis()).isEqualTo(1000);
        assertThat(state.canPrevious()).isFalse();
        assertThat(state.canReversePlay()).isFalse();
    }

    @Test
    void stageAndGlobalDtosDefensivelyCopyCollections() {
        ArrayList<String> input = new ArrayList<>(List.of("input"));
        ArrayList<String> output = new ArrayList<>(List.of("output"));
        UiStageDataDto stageData = new UiStageDataDto("lexer", 1, -1, false, input, "item", output, List.of());
        input.add("changed");
        output.add("changed");

        assertThat(stageData.inputSummary()).containsExactly("input");
        assertThat(stageData.accumulatedOutput()).containsExactly("output");
        assertThatThrownBy(() -> stageData.inputSummary().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> stageData.accumulatedOutput().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);

        UiGlobalDataDto globalData = new UiGlobalDataDto(
                "source",
                input,
                List.of(),
                output,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        assertThat(globalData.stageSummaries()).containsExactly("input", "changed");
        assertThat(globalData.tokenSummary()).containsExactly("output", "changed");
        assertThatThrownBy(() -> globalData.stageSummaries().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void stageVisualDtosDefensivelyCopyCollections() {
        ArrayList<UiLexerTokenVisualDto> tokens = new ArrayList<>(List.of(
                new UiLexerTokenVisualDto("INT", "int", null, true)
        ));
        ArrayList<String> genericItems = new ArrayList<>(List.of("item"));
        UiStageVisualDto visual = new UiStageVisualDto(
                "lexer",
                "lexer",
                genericItems,
                tokens,
                null,
                null,
                false,
                List.of()
        );
        tokens.add(new UiLexerTokenVisualDto("IDENTIFIER", "main", null, false));
        genericItems.add("changed");

        assertThat(visual.lexerTokens()).containsExactly(new UiLexerTokenVisualDto("INT", "int", null, true));
        assertThat(visual.genericItems()).containsExactly("item");
        assertThatThrownBy(() -> visual.lexerTokens().add(new UiLexerTokenVisualDto("EOF", "", null, false)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> visual.genericItems().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void apiDoesNotExposeCompilerOrRuntimeDataModels() {
        assertThat(MiniCObservationApi.class.getMethods())
                .filteredOn(method -> method.getDeclaringClass() == MiniCObservationApi.class)
                .extracting(method -> method.getReturnType().getName())
                .allSatisfy(typeName -> assertThat(typeName)
                        .doesNotStartWith("minic.compiler.")
                        .doesNotStartWith("minic.runtime.step.")
                        .doesNotStartWith("minic.session."));
    }
}
