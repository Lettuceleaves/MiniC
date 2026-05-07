package minic.uiapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UiDebugIrViewBuilderTest {
    @Test
    void buildsIrDebugViewWithActiveInstructionAndOperands() {
        MiniCDebugApi api = new MiniCDebugApi();
        api.loadSource("ir-debug.mc", """
                int main() {
                    int value = 1;
                    value = value + 1;
                    return value;
                }
                """);
        api.startDebug();
        api.setBreakpoint(3);
        api.runToBreakpoint();

        UiDebugIrViewDto view = api.irDebugView();

        assertThat(view.currentInstructionId()).isNotBlank();
        assertThat(view.currentSourceRange().startLine()).isEqualTo(3);
        assertThat(view.explanation()).contains("Debug 快照");
        assertThat(view.lines()).anySatisfy(line -> {
            assertThat(line.active()).isTrue();
            assertThat(line.text()).contains("check_initialized");
        });
        assertThat(view.lines()).extracting(UiIrLineVisualDto::text)
                .contains("function main", "  block entry");
        assertThat(view.lines()).anySatisfy(line ->
                assertThat(line.text()).contains("store"));
        assertThat(view.operands()).anySatisfy(operand -> {
            assertThat(operand.name()).isEqualTo("value");
            assertThat(operand.valueRef()).isNotBlank();
        });
    }
}
