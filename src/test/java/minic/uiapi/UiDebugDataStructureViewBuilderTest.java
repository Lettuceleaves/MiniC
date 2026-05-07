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

    @Test
    void revealsRecursiveGraphElementsFromExecutedBuildSteps() {
        MiniCDebugApi api = new MiniCDebugApi();
        api.loadSource("recursive-visual-tree.mc", """
                // @visual graph name=avl kind=tree root=root reveal=recursive function=build visit=index
                int left_of(int index) {
                    if (index == 1) {
                        return 2;
                    }
                    return 0;
                }
                int right_of(int index) {
                    if (index == 1) {
                        return 3;
                    }
                    return 0;
                }
                int build(int index) {
                    if (index == 0) {
                        return 0;
                    }
                    // @visual-node graph=avl id=1 label=10
                    // @visual-node graph=avl id=2 label=5
                    // @visual-node graph=avl id=3 label=15
                    // @visual-edge graph=avl from=1 to=2 label=left directed=true
                    // @visual-edge graph=avl from=1 to=3 label=right directed=true
                    return 1 + build(left_of(index)) + build(right_of(index));
                }
                int main() {
                    int root = 1;
                    return build(root);
                }
                """);
        api.startDebug();

        int initialElements = visualElementCount(api.dataStructureDebugView(), "avl");
        for (int i = 0; i < 18; i++) {
            api.stepInto();
        }
        int partialElements = visualElementCount(api.dataStructureDebugView(), "avl");
        api.fastForward();
        int completeElements = visualElementCount(api.dataStructureDebugView(), "avl");

        assertThat(initialElements).isLessThan(partialElements);
        assertThat(partialElements).isLessThanOrEqualTo(completeElements);
        assertThat(completeElements).isEqualTo(5);
    }

    @Test
    void rendersRuntimeMappedGraphFromVisualEventsAtCurrentSnapshot() {
        MiniCDebugApi api = new MiniCDebugApi();
        api.loadSource("runtime-visual-tree.mc", """
                // @visual graph name=avl kind=tree root=root mode=runtime function=dfs visit=index
                // @visual-map node graph=avl id=index label=index
                // @visual-map edge graph=avl key=left from=index to=left
                int dfs(int index) {
                    int left = index - 1;
                    if (index == 0) {
                        return 0;
                    }
                    return index + dfs(left);
                }
                int main() {
                    int root = 3;
                    return dfs(root);
                }
                """);
        api.startDebug();

        int initialElements = visualElementCount(api.dataStructureDebugView(), "avl");
        int partialElements = 0;
        for (int i = 0; i < 20 && partialElements == 0; i++) {
            api.stepInto();
            partialElements = visualElementCount(api.dataStructureDebugView(), "avl");
        }
        api.fastForward();
        UiDebugDataStructureViewDto completeView = api.dataStructureDebugView();
        int completeElements = visualElementCount(completeView, "avl");
        api.stepBack();
        int backElements = visualElementCount(api.dataStructureDebugView(), "avl");

        assertThat(initialElements).isZero();
        assertThat(partialElements).isGreaterThan(0);
        assertThat(completeElements).isGreaterThan(partialElements);
        assertThat(backElements).isLessThanOrEqualTo(completeElements);
        assertThat(completeView.visuals()).anySatisfy(visual -> {
            assertThat(visual.name()).isEqualTo("avl");
            assertThat(visual.elements()).extracting(UiDebugVisualElementDto::kind)
                    .contains("GRAPH_NODE", "GRAPH_EDGE");
        });
    }

    private int visualElementCount(UiDebugDataStructureViewDto view, String name) {
        return view.visuals().stream()
                .filter(visual -> visual.name().equals(name))
                .findFirst()
                .map(visual -> visual.elements().size())
                .orElse(0);
    }
}
