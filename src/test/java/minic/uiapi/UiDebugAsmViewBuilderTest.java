package minic.uiapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UiDebugAsmViewBuilderTest {
    @Test
    void buildsAsmDebugViewWithMappedActiveLines() {
        MiniCDebugApi api = new MiniCDebugApi();
        api.loadSource("asm-debug.mc", """
                int main() {
                    int value = 1;
                    value = value + 1;
                    return value;
                }
                """);
        api.startDebug();
        api.setBreakpoint(3);
        api.runToBreakpoint();

        UiDebugAsmViewDto view = api.asmDebugView();

        assertThat(view.explanation()).contains("映射", "不代表真实 CPU");
        assertThat(view.relatedIrIds()).contains(api.currentState().currentSnapshot().instructionId());
        assertThat(view.lines()).anySatisfy(line -> {
            assertThat(line.active()).isTrue();
            assertThat(line.range().startLine()).isEqualTo(3);
        });
    }
}
