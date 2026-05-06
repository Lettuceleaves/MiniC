package minic.uiapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UiDebugAstViewBuilderTest {
    @Test
    void buildsAstDebugViewWithActiveNodeAndMappings() {
        MiniCDebugApi api = new MiniCDebugApi();
        api.loadSource("ast-debug.mc", """
                int main() {
                    int value = 1;
                    value = value + 1;
                    return value;
                }
                """);
        api.startDebug();
        api.setBreakpoint(3);
        api.runToBreakpoint();

        UiDebugAstViewDto view = api.astDebugView();

        assertThat(view.root().kind()).isEqualTo("Program");
        assertThat(view.activeNode()).isNotNull();
        assertThat(view.activeNode().sourceRange().startLine()).isEqualTo(3);
        assertThat(view.activeNode().explanation()).contains("关联 IR/ASM");
        assertThat(view.relatedIrIds()).isNotEmpty();
        assertThat(view.relatedAsmIds()).isNotEmpty();
        assertThat(hasActiveNode(view.root())).isTrue();
    }

    private boolean hasActiveNode(UiAstNodeVisualDto node) {
        return node.active() || node.children().stream().anyMatch(this::hasActiveNode);
    }
}
