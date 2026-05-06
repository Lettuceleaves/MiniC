package minic.uiapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UiDebugDataStructureViewBuilderTest {
    @Test
    void buildsDataStructureViewWithProcessSpaceAndVisualCards() {
        MiniCDebugApi api = new MiniCDebugApi();
        api.loadSource("data-view.mc", """
                // @visual array name=arr kind=array root=value
                // @visual-node graph=network id=1 label=a
                // @visual-node graph=network id=2 label=b
                // @visual-edge graph=network from=1 to=2 label=next directed=true
                // @visual composite name=cache kind=hash_table
                int main() {
                    int value = 1;
                    value = value + 1;
                    return value;
                }
                """);
        api.startDebug();
        api.setBreakpoint(8);
        api.runToBreakpoint();

        UiDebugDataStructureViewDto view = api.dataStructureDebugView();

        assertThat(view.processSpace().stackFrames()).isNotEmpty();
        assertThat(view.visuals()).extracting(UiDebugVisualStructureDto::type)
                .contains("ARRAY", "GRAPH", "COMPOSITE");
        assertThat(view.visuals()).anySatisfy(visual -> {
            assertThat(visual.name()).isEqualTo("network");
            assertThat(visual.elements()).extracting(UiDebugVisualElementDto::kind)
                    .contains("GRAPH_NODE", "GRAPH_EDGE");
        });
        assertThat(view.visuals()).anySatisfy(visual -> {
            assertThat(visual.name()).isEqualTo("arr");
            assertThat(visual.elements()).anySatisfy(element ->
                    assertThat(element.metadata()).containsKey("root"));
        });
    }
}
