package minic.uiapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UiDebugMetadataViewBuilderTest {
    @Test
    void buildsMetadataViewFieldsAndTimeline() {
        MiniCDebugApi api = new MiniCDebugApi();
        api.loadSource("metadata.mc", """
                int main() {
                    int value = 1;
                    value = value + 1;
                    return value;
                }
                """);
        api.startDebug();
        api.setBreakpoint(3);
        api.runToBreakpoint();

        UiDebugMetadataViewDto view = api.metadataView();

        assertThat(view.executionState()).isEqualTo("PAUSED");
        assertThat(view.stopReason()).isEqualTo("BREAKPOINT");
        assertThat(view.currentFunction()).isEqualTo("main");
        assertThat(view.currentSourceRange().startLine()).isEqualTo(3);
        assertThat(view.callStack()).isNotEmpty();
        assertThat(view.variables()).anySatisfy(variable -> {
            assertThat(variable.name()).isEqualTo("value");
            assertThat(variable.address()).isNotBlank();
            assertThat(variable.valueSummary()).isIn("<uninitialized>", "1");
        });
        assertThat(view.stdout()).isEmpty();
        assertThat(view.stderr()).isEmpty();
        assertThat(view.breakpoints()).extracting(UiDebugBreakpointDto::line).containsExactly(3);
        assertThat(view.events()).isNotEmpty();
        assertThat(view.timeline()).extracting(UiDebugTimelineItemDto::snapshotId).contains(0L);
    }
}
