package minic.uiapi;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCDebugApiTest {
    @Test
    void startsDebugSetsBreakpointRunsAndStepsBack() {
        MiniCDebugApi api = new MiniCDebugApi();
        api.loadSource("ui-debug.mc", """
                int main() {
                    int value = 1;
                    value = value + 1;
                    return value;
                }
                """);

        UiDebugStateDto initial = api.startDebug();
        UiDebugStateDto withBreakpoint = api.setBreakpoint(3);
        UiDebugStateDto breakpointHit = api.runToBreakpoint();
        UiDebugStateDto steppedBack = api.stepBack();

        assertThat(initial.executionState()).isEqualTo("PAUSED");
        assertThat(withBreakpoint.breakpoints()).extracting(UiDebugBreakpointDto::line).containsExactly(3);
        assertThat(breakpointHit.currentSnapshot().stopReason()).isEqualTo("BREAKPOINT");
        assertThat(breakpointHit.currentSnapshot().sourceRange().startLine()).isEqualTo(3);
        assertThat(steppedBack.currentSnapshot().visibleStepIndex())
                .isLessThan(breakpointHit.currentSnapshot().visibleStepIndex());
    }

    @Test
    void debugDtosDoNotExposeRuntimeDebugTypes() {
        List<Class<?>> dtoTypes = List.of(
                UiDebugStateDto.class,
                UiDebugSnapshotDto.class,
                UiDebugFrameDto.class,
                UiDebugVariableDto.class,
                UiDebugProcessSpaceDto.class,
                UiDebugEventDto.class,
                UiDebugBreakpointDto.class
        );

        for (Class<?> dtoType : dtoTypes) {
            assertThat(Arrays.stream(dtoType.getRecordComponents())
                    .map(RecordComponent::getType)
                    .map(Class::getPackageName)
                    .filter(packageName -> packageName.startsWith("minic.runtime.debug")))
                    .as(dtoType.getSimpleName())
                    .isEmpty();
        }
    }
}
