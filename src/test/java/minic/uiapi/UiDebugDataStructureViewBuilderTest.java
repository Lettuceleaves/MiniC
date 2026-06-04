package minic.uiapi;

import minic.runtime.debug.DebugCodeSegment;
import minic.runtime.debug.DebugHeapSegment;
import minic.runtime.debug.DebugIoSegment;
import minic.runtime.debug.DebugMemoryEntry;
import minic.runtime.debug.DebugProcessSpace;
import minic.runtime.debug.DebugStackFrame;
import minic.runtime.debug.DebugStackSegment;
import minic.runtime.debug.DebugStaticSegment;
import minic.runtime.debug.DebugValue;
import minic.runtime.debug.DebugValueElement;
import minic.runtime.debug.DebugValueField;
import minic.runtime.debug.DebugVirtualAddress;
import minic.runtime.debug.dataflow.DataFlowEvent;
import minic.runtime.debug.dataflow.DataFlowEventType;
import minic.runtime.debug.visual.VisualEvent;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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

    @Test
    void preservesNestedDebugValuesAndTypedVisualSpecMetadataInUiDtos() {
        DebugVirtualAddress nextAddress = new DebugVirtualAddress("stack", 0x24);
        DebugMemoryEntry node = new DebugMemoryEntry(
                "a",
                new DebugVirtualAddress("stack", 0x10),
                "struct Node",
                DebugValue.structValue("struct Node", List.of(
                        new DebugValueField("value", DebugValue.intValue(7)),
                        new DebugValueField("next", DebugValue.pointerValue("struct Node *", nextAddress))
                ))
        );
        DebugMemoryEntry arr = new DebugMemoryEntry(
                "arr",
                new DebugVirtualAddress("stack", 0x40),
                "struct Point[2]",
                DebugValue.arrayValue("struct Point[2]", List.of(
                        new DebugValueElement(0, pointValue(1, 2)),
                        new DebugValueElement(1, pointValue(3, 4))
                ))
        );
        DebugProcessSpace processSpace = processSpace(node, arr);
        UiDebugStateDto state = state(processSpace);

        UiDebugDataStructureViewDto view = new UiDebugDataStructureViewBuilder().build(
                new SourceFile("typed-array-ui.mc", """
                        // @visual root=arr kind=array fields=x,y
                        int main() { return 0; }
                        """),
                state,
                processSpace
        );

        UiDebugVariableDto a = view.processSpace().stackFrames().getFirst().locals().stream()
                .filter(variable -> variable.name().equals("a"))
                .findFirst()
                .orElseThrow();
        assertThat(a.typeShape()).isEqualTo("STRUCT");
        assertThat(a.address()).isEqualTo("stack:0x10");
        assertThat(a.highlightedChange()).isFalse();
        assertThat(a.explanation()).isEmpty();
        assertThat(a.fields()).extracting(UiDebugVariableDto::name)
                .containsExactly("value", "next");
        assertThat(a.fields()).anySatisfy(field -> {
            assertThat(field.name()).isEqualTo("next");
            assertThat(field.pointerTarget()).isEqualTo("stack:0x24");
        });

        UiDebugVariableDto arrDto = view.processSpace().stackFrames().getFirst().locals().stream()
                .filter(variable -> variable.name().equals("arr"))
                .findFirst()
                .orElseThrow();
        assertThat(arrDto.elements().get(1).name()).isEqualTo("[1]");
        assertThat(arrDto.elements().get(1).fields()).anySatisfy(field -> {
            assertThat(field.name()).isEqualTo("x");
            assertThat(field.valueSummary()).isEqualTo("3");
        });
        assertThat(view.visuals()).anySatisfy(visual -> {
            assertThat(visual.name()).isEqualTo("arr");
            assertThat(visual.explanation())
                    .contains("arr")
                    .contains("struct Point[2]")
                    .contains("C 代码")
                    .contains("数组");
            assertThat(visual.elements()).anySatisfy(element ->
                    assertThat(element.metadata())
                            .containsEntry("root", "arr")
                            .containsEntry("indexPath", "[1]")
                            .containsEntry("fieldNames", "x,y")
                            .hasEntrySatisfying("explanation", explanation -> assertThat(explanation)
                                    .contains("arr[1]")
                                    .contains("下标")
                                    .contains("C 代码")));
        });
    }

    @Test
    void preservesHashChainTemplateLayoutAndBucketMetadataInUiDtos() {
        DebugMemoryEntry buckets = pointerArrayEntry(
                "buckets",
                0x200,
                new DebugVirtualAddress("stack", 0x220),
                null,
                new DebugVirtualAddress("stack", 0x240)
        );
        DebugMemoryEntry e0 = entryNode("e0", 0x220, 10, new DebugVirtualAddress("stack", 0x230));
        DebugMemoryEntry e1 = entryNode("e1", 0x230, 20, null);
        DebugMemoryEntry e2 = entryNode("e2", 0x240, 30, null);
        DebugProcessSpace processSpace = processSpace(buckets, e0, e1, e2);

        UiDebugDataStructureViewDto view = new UiDebugDataStructureViewBuilder().build(
                new SourceFile("hash-chain-ui.mc", """
                        // @visual root=buckets kind=hash-chain-table label=key
                        int main() { return 0; }
                        """),
                state(processSpace),
                processSpace
        );

        assertThat(view.visuals()).singleElement().satisfies(visual -> {
            assertThat(visual.kind()).isEqualTo("hash-chain-table");
            assertThat(visual.layoutHint()).isEqualTo("bucketed");
            assertThat(visual.elements()).anySatisfy(element ->
                    assertThat(element.metadata())
                            .containsEntry("visual-role", "bucket")
                            .containsEntry("bucketIndex", "0"));
            assertThat(visual.elements()).anySatisfy(element ->
                    assertThat(element.metadata())
                            .containsEntry("visual-role", "chain-node")
                            .containsEntry("bucketIndex", "0")
                            .containsEntry("chainDepth", "1"));
        });
    }

    @Test
    void flatVariableDtoConstructorKeepsTypeShapeEmptyForLegacyCallers() {
        UiDebugVariableDto variable = new UiDebugVariableDto(
                "value",
                "stack:0x10",
                "int",
                "INT",
                "7"
        );

        assertThat(variable.typeShape()).isEmpty();
        assertThat(variable.pointerTarget()).isEmpty();
        assertThat(variable.fields()).isEmpty();
        assertThat(variable.elements()).isEmpty();
    }

    @Test
    void arrayElementExplanationUsesMatchingDataFlowOldAndNewValues() {
        DebugMemoryEntry arr = intArrayEntry("arr", 0x40, 1, 20, 3);
        UiDebugDataStructureViewDto view = buildWithDataFlow(
                new SourceFile("array-flow-ui.mc", """
                        // @visual root=arr kind=array
                        int main() { return 0; }
                        """),
                processSpace(arr),
                List.of(dataFlow(DataFlowEventType.ARRAY_ELEMENT_WRITE,
                        "arr[1] = arr[0] + 19",
                        "arr[1]",
                        "2",
                        "20",
                        "stack:0x44",
                        ""))
        );

        String explanation = elementExplanation(view, "arr", metadata -> metadata.get("indexPath").equals("[1]"));

        assertThat(explanation)
                .contains("arr[1] = arr[0] + 19")
                .contains("arr[1]")
                .contains("旧值 `2`")
                .contains("新值 `20`")
                .contains("下标");
    }

    @Test
    void pointerDereferenceExplanationUsesMatchingDataFlowPointerTarget() {
        DebugVirtualAddress xAddress = new DebugVirtualAddress("stack", 0x80);
        DebugMemoryEntry x = new DebugMemoryEntry("x", xAddress, "int", DebugValue.intValue(7));
        DebugMemoryEntry p = new DebugMemoryEntry(
                "p",
                new DebugVirtualAddress("stack", 0x90),
                "int *",
                DebugValue.pointerValue("int *", xAddress)
        );
        UiDebugDataStructureViewDto view = buildWithDataFlow(
                new SourceFile("pointer-flow-ui.mc", """
                        // @visual root=p kind=pointer
                        int main() { return 0; }
                        """),
                processSpace(x, p),
                List.of(dataFlow(DataFlowEventType.LOAD_POINTER,
                        "*p",
                        "*p",
                        "1",
                        "7",
                        xAddress.display(),
                        xAddress.display()))
        );

        String explanation = elementExplanation(view, "p", metadata ->
                metadata.containsKey("from")
                        && metadata.containsKey("to")
                        && metadata.containsKey("pointerTarget"));

        assertThat(explanation)
                .contains("*p")
                .contains("旧值 `1`")
                .contains("新值 `7`")
                .contains(xAddress.display())
                .contains("解引用");
    }

    @Test
    void matchingDataFlowUsesLatestSameSnapshotSameScoreEvent() {
        DebugMemoryEntry arr = intArrayEntry("arr", 0x40, 1, 30, 3);
        UiDebugDataStructureViewDto view = buildWithDataFlow(
                new SourceFile("array-latest-flow-ui.mc", """
                        // @visual root=arr kind=array
                        int main() { return 0; }
                        """),
                processSpace(arr),
                List.of(
                        dataFlow(DataFlowEventType.ARRAY_ELEMENT_WRITE,
                                "arr[1] = 20",
                                "arr[1]",
                                "2",
                                "20",
                                "stack:0x44",
                                ""),
                        dataFlow(DataFlowEventType.ARRAY_ELEMENT_WRITE,
                                "arr[1] = 30",
                                "arr[1]",
                                "20",
                                "30",
                                "stack:0x44",
                                "")
                )
        );

        String explanation = elementExplanation(view, "arr", metadata -> metadata.get("indexPath").equals("[1]"));

        assertThat(explanation)
                .contains("arr[1] = 30")
                .contains("旧值 `20`")
                .contains("新值 `30`")
                .doesNotContain("arr[1] = 20", "旧值 `2`");
    }

    @Test
    void pointerTargetAloneDoesNotMatchAnotherPointerEvent() {
        DebugVirtualAddress xAddress = new DebugVirtualAddress("stack", 0x80);
        DebugMemoryEntry x = new DebugMemoryEntry("x", xAddress, "int", DebugValue.intValue(7));
        DebugMemoryEntry p = new DebugMemoryEntry(
                "p",
                new DebugVirtualAddress("stack", 0x90),
                "int *",
                DebugValue.pointerValue("int *", xAddress)
        );
        DebugMemoryEntry q = new DebugMemoryEntry(
                "q",
                new DebugVirtualAddress("stack", 0x98),
                "int *",
                DebugValue.pointerValue("int *", xAddress)
        );
        UiDebugDataStructureViewDto view = buildWithDataFlow(
                new SourceFile("pointer-ambiguous-flow-ui.mc", """
                        // @visual root=p kind=pointer
                        int main() { return 0; }
                        """),
                processSpace(x, p, q),
                List.of(dataFlow(DataFlowEventType.POINTER_RETARGET,
                        "q = &x",
                        "q",
                        "stack:0x70",
                        xAddress.display(),
                        "stack:0x98",
                        xAddress.display()))
        );

        String explanation = elementExplanation(view, "p", metadata ->
                metadata.containsKey("from")
                        && metadata.containsKey("to")
                        && metadata.containsKey("pointerTarget"));

        assertThat(explanation)
                .doesNotContain("q = &x", "旧值 `stack:0x70`")
                .contains("*p");
    }

    @Test
    void structFieldExplanationUsesMatchingDataFlowFieldPath() {
        DebugMemoryEntry point = pointEntry("point", 0x30, 7, 2);
        UiDebugDataStructureViewDto view = buildWithDataFlow(
                new SourceFile("struct-flow-ui.mc", """
                        // @visual root=point kind=struct
                        int main() { return 0; }
                        """),
                processSpace(point),
                List.of(dataFlow(DataFlowEventType.FIELD_WRITE,
                        "point.x = 7",
                        "point.x",
                        "3",
                        "7",
                        "stack:0x30",
                        ""))
        );

        String explanation = elementExplanation(view, "point", metadata -> "x".equals(metadata.get("fieldName")));

        assertThat(explanation)
                .contains("point.x = 7")
                .contains("point.x")
                .contains("旧值 `3`")
                .contains("新值 `7`")
                .contains("字段");
    }

    @Test
    void structMatrixExplanationUsesMatchingDataFlowRowColumnAndFieldPath() {
        DebugMemoryEntry grid = new DebugMemoryEntry(
                "grid",
                new DebugVirtualAddress("stack", 0x120),
                "struct Point[4]",
                DebugValue.arrayValue("struct Point[4]", List.of(
                        new DebugValueElement(0, pointValue(1, 2)),
                        new DebugValueElement(1, pointValue(3, 4)),
                        new DebugValueElement(2, pointValue(9, 6)),
                        new DebugValueElement(3, pointValue(7, 8))
                ))
        );
        UiDebugDataStructureViewDto view = buildWithDataFlow(
                new SourceFile("struct-matrix-flow-ui.mc", """
                        // @visual root=grid kind=struct-matrix rows=2 columns=2 fields=x,y
                        int main() { return 0; }
                        """),
                processSpace(grid),
                List.of(dataFlow(DataFlowEventType.FIELD_WRITE,
                        "grid[1][0].x = 9",
                        "grid[1][0].x",
                        "5",
                        "9",
                        "stack:0x128",
                        ""))
        );

        String explanation = elementExplanation(view, "grid", metadata -> "[1][0]".equals(metadata.get("indexPath")));

        assertThat(explanation)
                .contains("grid[1][0].x = 9")
                .contains("grid[1][0].x")
                .contains("旧值 `5`")
                .contains("新值 `9`")
                .contains("第 `1` 行")
                .contains("第 `0` 列")
                .contains("字段");
    }

    private int visualElementCount(UiDebugDataStructureViewDto view, String name) {
        return view.visuals().stream()
                .filter(visual -> visual.name().equals(name))
                .findFirst()
                .map(visual -> visual.elements().size())
                .orElse(0);
    }

    private UiDebugDataStructureViewDto buildWithDataFlow(
            SourceFile sourceFile,
            DebugProcessSpace processSpace,
            List<DataFlowEvent> dataFlowEvents
    ) {
        return new UiDebugDataStructureViewBuilder().build(
                sourceFile,
                state(processSpace),
                processSpace,
                List.<VisualEvent>of(),
                dataFlowEvents
        );
    }

    private String elementExplanation(
            UiDebugDataStructureViewDto view,
            String visualName,
            java.util.function.Predicate<java.util.Map<String, String>> metadataPredicate
    ) {
        return view.visuals().stream()
                .filter(visual -> visual.name().equals(visualName))
                .flatMap(visual -> visual.elements().stream())
                .filter(element -> metadataPredicate.test(element.metadata()))
                .map(element -> element.metadata().getOrDefault("explanation", ""))
                .findFirst()
                .orElseThrow();
    }

    private static DataFlowEvent dataFlow(
            DataFlowEventType type,
            String cExpression,
            String lvaluePath,
            String oldValue,
            String newValue,
            String address,
            String pointerTarget
    ) {
        return new DataFlowEvent(
                1,
                "ir-1",
                null,
                type,
                cExpression,
                lvaluePath,
                oldValue,
                newValue,
                address,
                pointerTarget
        );
    }

    private static DebugValue pointValue(int x, int y) {
        return DebugValue.structValue("struct Point", List.of(
                new DebugValueField("x", DebugValue.intValue(x)),
                new DebugValueField("y", DebugValue.intValue(y))
        ));
    }

    private static DebugMemoryEntry pointEntry(String name, long address, int x, int y) {
        return new DebugMemoryEntry(
                name,
                new DebugVirtualAddress("stack", address),
                "struct Point",
                pointValue(x, y)
        );
    }

    private static DebugMemoryEntry pointerArrayEntry(String name, long address, DebugVirtualAddress... targets) {
        ArrayList<DebugValueElement> elements = new ArrayList<>();
        for (int index = 0; index < targets.length; index++) {
            DebugValue value = targets[index] == null
                    ? DebugValue.nullValue("struct Entry *")
                    : DebugValue.pointerValue("struct Entry *", targets[index]);
            elements.add(new DebugValueElement(index, value));
        }
        return new DebugMemoryEntry(
                name,
                new DebugVirtualAddress("stack", address),
                "struct Entry *[" + targets.length + "]",
                DebugValue.arrayValue("struct Entry *[" + targets.length + "]", elements)
        );
    }

    private static DebugMemoryEntry entryNode(String name, long address, int key, DebugVirtualAddress next) {
        DebugValue nextValue = next == null ? DebugValue.nullValue("struct Entry *") : DebugValue.pointerValue("struct Entry *", next);
        return new DebugMemoryEntry(name, new DebugVirtualAddress("stack", address), "struct Entry", DebugValue.structValue("struct Entry", List.of(
                new DebugValueField("key", DebugValue.intValue(key)),
                new DebugValueField("next", nextValue)
        )));
    }

    private static DebugMemoryEntry intArrayEntry(String name, long address, int... values) {
        ArrayList<DebugValueElement> elements = new ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            elements.add(new DebugValueElement(index, DebugValue.intValue(values[index])));
        }
        return new DebugMemoryEntry(
                name,
                new DebugVirtualAddress("stack", address),
                "int[" + values.length + "]",
                DebugValue.arrayValue("int[" + values.length + "]", elements)
        );
    }

    private static DebugProcessSpace processSpace(DebugMemoryEntry... locals) {
        return new DebugProcessSpace(
                DebugCodeSegment.empty(),
                DebugStaticSegment.empty(),
                new DebugStackSegment(List.of(new DebugStackFrame(
                        "frame-main",
                        "main",
                        List.of(),
                        List.of(locals),
                        null,
                        null
                ))),
                DebugHeapSegment.empty(),
                DebugIoSegment.empty()
        );
    }

    private static UiDebugStateDto state(DebugProcessSpace processSpace) {
        UiDebugProcessSpaceDto processSpaceDto = new UiDebugProcessSpaceDto(
                "",
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "",
                "",
                ""
        );
        UiDebugSnapshotDto snapshot = new UiDebugSnapshotDto(
                1,
                1,
                "main",
                "entry",
                "ir-1",
                null,
                List.of(),
                processSpaceDto,
                false,
                "STEP"
        );
        return new UiDebugStateDto("typed-array-ui.mc", "RUNNING", snapshot, List.of(snapshot), List.of(), List.of());
    }
}
