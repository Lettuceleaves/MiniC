package minic.uiapi;

import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UiDebugDataStructureEndToEndTest {
    @Test
    void visualSamplesStartDebugAndExposeExplainedStructures() throws IOException {
        for (VisualSample sample : samples()) {
            MiniCDebugApi api = new MiniCDebugApi();
            Path path = Path.of("samples", sample.fileName());
            String source = Files.readString(path);
            api.loadSource(new SourceFile(sample.fileName(), source));

            api.startDebug();
            int breakLine = findBreakLine(source);
            api.setBreakpoint(breakLine);
            UiDebugStateDto state = api.runToBreakpoint();

            assertThat(state.currentSnapshot().breakpointHit())
                    .as(sample.fileName() + " breakpoint hit")
                    .isTrue();
            assertThat(state.currentSnapshot().functionName())
                    .as(sample.fileName() + " function")
                    .isEqualTo("main");
            assertThat(state.currentSnapshot().sourceRange().startLine())
                    .as(sample.fileName() + " breakpoint line")
                    .isEqualTo(breakLine);

            UiDebugDataStructureViewDto view = api.dataStructureDebugView();
            assertThat(view.warnings())
                    .as(sample.fileName() + " warnings")
                    .isEmpty();

            UiDebugVisualStructureDto visual = expectedVisual(view, sample);
            assertThat(visual.kind()).isEqualTo(sample.expectedKind());
            assertThat(visual.explanation())
                    .as(sample.fileName() + " structure explanation")
                    .isNotBlank();
            assertThat(visual.elements())
                    .as(sample.fileName() + " elements")
                    .isNotEmpty();
            assertThat(visual.elements())
                    .as(sample.fileName() + " element explanations")
                    .allSatisfy(element -> assertThat(element.metadata())
                            .hasEntrySatisfying("explanation", explanation -> assertThat(explanation).isNotBlank()));
            assertVisualShape(sample, visual);
            api.fastForward();
        }
    }

    @Test
    void lruHashStressSampleUsesHashNextForBucketChains() throws IOException {
        Path path = Path.of("tmp", "ds_visual_stress", "01_lru_hash.mc");
        String source = Files.readString(path);
        MiniCDebugApi api = new MiniCDebugApi();
        api.loadSource(new SourceFile(path.getFileName().toString(), source));
        api.startDebug();
        api.setBreakpoint(findLine(source, "put(nodes, &used, 4, buckets, &head, &tail, 7, 700);"));
        api.runToBreakpoint();

        UiDebugVisualStructureDto buckets = api.dataStructureDebugView().visuals().stream()
                .filter(visual -> visual.name().equals("buckets"))
                .findFirst()
                .orElseThrow();

        List<UiDebugVisualElementDto> edges = elementsOfKind(buckets, "GRAPH_EDGE");
        assertThat(edges)
                .extracting(UiDebugVisualElementDto::label)
                .containsOnly("bucket", "hashNext");
        assertThat(edges)
                .noneSatisfy(edge -> assertThat(edge.metadata().getOrDefault("path", ""))
                        .as(edge.id() + " path")
                        .endsWith(".next"));
    }

    private static int findBreakLine(String source) {
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (int index = 0; index < lines.length; index++) {
            if (!lines[index].contains("// @break")) {
                continue;
            }
            String beforeMarker = lines[index].substring(0, lines[index].indexOf("// @break")).trim();
            if (!beforeMarker.isEmpty()) {
                return index + 1;
            }
            if (index + 1 < lines.length) {
                return index + 2;
            }
            throw new IllegalArgumentException("@break marker has no following line");
        }
        throw new IllegalArgumentException("sample has no @break marker");
    }

    private static int findLine(String source, String text) {
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].contains(text)) {
                return index + 1;
            }
        }
        throw new IllegalArgumentException("line not found: " + text);
    }

    private static UiDebugVisualStructureDto expectedVisual(
            UiDebugDataStructureViewDto view,
            VisualSample sample
    ) {
        return view.visuals().stream()
                .filter(visual -> visual.name().equals(sample.expectedName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(sample.fileName() + " missing visual " + sample.expectedName()));
    }

    private static void assertVisualShape(VisualSample sample, UiDebugVisualStructureDto visual) {
        switch (sample.expectedKind()) {
            case "scalar" -> assertScalar(sample, visual);
            case "pointer" -> assertPointer(sample, visual);
            case "pointer-chain" -> assertPointerChain(sample, visual);
            case "array" -> assertArray(sample, visual);
            case "pointer-array" -> assertPointerArray(sample, visual);
            case "matrix" -> assertMatrix(sample, visual);
            case "struct" -> assertStruct(sample, visual);
            case "struct-pointer" -> assertStructPointer(sample, visual);
            case "struct-pointer-chain" -> assertStructPointerChain(sample, visual);
            case "struct-array" -> assertStructArray(sample, visual);
            case "struct-matrix" -> assertStructMatrix(sample, visual);
            case "struct-list" -> assertStructList(sample, visual);
            case "lru-list" -> assertLruList(sample, visual);
            case "binary-tree" -> assertBinaryTree(sample, visual);
            case "hash-chain-table" -> assertHashChainTable(sample, visual);
            case "heap" -> assertHeap(sample, visual);
            case "fenwick-tree" -> assertFenwickTree(sample, visual);
            default -> throw new AssertionError("Unhandled visual kind: " + sample.expectedKind());
        }
    }

    private static void assertScalar(VisualSample sample, UiDebugVisualStructureDto visual) {
        assertThat(elementsOfKind(visual, "COMPOSITE_PART"))
                .as(sample.fileName() + " scalar parts")
                .hasSizeGreaterThanOrEqualTo(1);
        assertThat(visual.explanation()).contains("C 代码");
    }

    private static void assertPointer(VisualSample sample, UiDebugVisualStructureDto visual) {
        assertThat(elementsOfKind(visual, "GRAPH_NODE"))
                .as(sample.fileName() + " pointer nodes")
                .hasSizeGreaterThanOrEqualTo(2);
        List<UiDebugVisualElementDto> edges = elementsOfKind(visual, "GRAPH_EDGE");
        assertThat(edges)
                .as(sample.fileName() + " pointer edges")
                .hasSizeGreaterThanOrEqualTo(1);
        assertThat(edges)
                .allSatisfy(edge -> assertThat(metadata(edge, "pointerTarget")).isNotBlank());
        assertThat(edges.stream().map(edge -> metadata(edge, "explanation")))
                .anyMatch(explanation -> explanation.contains("解引用") || explanation.contains("pointerTarget"));
    }

    private static void assertPointerChain(VisualSample sample, UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> edges = elementsOfKind(visual, "GRAPH_EDGE");
        assertThat(edges)
                .as(sample.fileName() + " pointer-chain edges")
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(edges)
                .allSatisfy(edge -> assertThat(metadata(edge, "pointerTarget")).isNotBlank());
        assertThat(visual.explanation() + "\n" + explanations(edges))
                .as(sample.fileName() + " pointer-chain explanation")
                .contains("指针")
                .contains("解引用")
                .containsAnyOf("链式", "指针链");
    }

    private static void assertArray(VisualSample sample, UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> cells = elementsOfKind(visual, "ARRAY_CELL");
        assertThat(cells)
                .as(sample.fileName() + " array cells")
                .hasSize(3);
        assertThat(cells).extracting(cell -> metadata(cell, "indexPath"))
                .containsExactly("[0]", "[1]", "[2]");
        assertThat(cells)
                .allSatisfy(cell -> assertThat(metadata(cell, "explanation")).contains("下标"));
    }

    private static void assertPointerArray(VisualSample sample, UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> cells = elementsOfKind(visual, "ARRAY_CELL");
        assertThat(cells)
                .as(sample.fileName() + " pointer-array cells")
                .hasSize(2);
        assertThat(cells).extracting(cell -> metadata(cell, "indexPath"))
                .containsExactly("[0]", "[1]");
        assertThat(cells)
                .allSatisfy(cell -> assertThat(metadata(cell, "pointerTarget")).isNotBlank());
        assertThat(cells)
                .allSatisfy(cell -> assertThat(metadata(cell, "explanation"))
                        .contains("下标")
                        .contains("指针")
                        .containsAnyOf("目标", "地址"));
    }

    private static void assertMatrix(VisualSample sample, UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> cells = elementsOfKind(visual, "ARRAY_CELL");
        assertThat(cells)
                .as(sample.fileName() + " matrix cells")
                .hasSize(4);
        assertThat(cells).extracting(cell -> metadata(cell, "row"))
                .contains("0", "1");
        assertThat(cells).extracting(cell -> metadata(cell, "column"))
                .contains("0", "1");
        assertThat(cells)
                .allSatisfy(cell -> assertThat(metadata(cell, "explanation")).containsAnyOf("行", "列", "下标"));
    }

    private static void assertStruct(VisualSample sample, UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> parts = elementsOfKind(visual, "COMPOSITE_PART");
        assertThat(parts).extracting(part -> metadata(part, "fieldName"))
                .as(sample.fileName() + " struct fields")
                .contains("x", "y");
        assertThat(parts)
                .allSatisfy(part -> assertThat(metadata(part, "explanation")).contains("字段"));
    }

    private static void assertStructPointer(VisualSample sample, UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> edges = assertPointerEdges(sample, visual, "struct-pointer");
        assertThat(edges).hasSizeGreaterThanOrEqualTo(1);
        assertThat(visual.explanation() + "\n" + explanations(edges))
                .as(sample.fileName() + " struct-pointer explanation")
                .contains("结构体")
                .contains("字段")
                .contains("指针");
    }

    private static void assertStructPointerChain(VisualSample sample, UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> edges = assertPointerEdges(sample, visual, "struct-pointer-chain");
        assertThat(edges).hasSizeGreaterThanOrEqualTo(2);
        assertThat(visual.explanation() + "\n" + explanations(edges))
                .as(sample.fileName() + " struct-pointer-chain explanation")
                .contains("结构体")
                .contains("字段")
                .contains("指针");
    }

    private static void assertStructArray(VisualSample sample, UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> cells = elementsOfKind(visual, "ARRAY_CELL");
        assertThat(cells)
                .as(sample.fileName() + " struct-array cells")
                .hasSize(2);
        assertThat(visual.summary()).contains("shape=1x2");
        assertThat(cells).extracting(cell -> metadata(cell, "fieldNames"))
                .allMatch(fields -> fields.contains("x") && fields.contains("y"));
        assertThat(cells)
                .allSatisfy(cell -> assertThat(metadata(cell, "explanation"))
                        .contains("下标")
                        .contains("结构体")
                        .contains("字段"));
    }

    private static void assertStructMatrix(VisualSample sample, UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> cells = elementsOfKind(visual, "ARRAY_CELL");
        assertThat(cells)
                .as(sample.fileName() + " struct-matrix cells")
                .hasSize(4);
        assertThat(cells).extracting(cell -> metadata(cell, "row"))
                .contains("0", "1");
        assertThat(cells).extracting(cell -> metadata(cell, "column"))
                .contains("0", "1");
        assertThat(visual.summary()).contains("shape=2x2");
        assertThat(cells).extracting(cell -> metadata(cell, "fieldNames"))
                .allMatch(fields -> fields.contains("x") && fields.contains("y"));
        assertThat(cells)
                .allSatisfy(cell -> assertThat(metadata(cell, "explanation"))
                        .containsAnyOf("矩阵下标", "下标")
                        .contains("结构体")
                        .contains("字段"));
    }

    private static void assertStructList(VisualSample sample, UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> nodes = elementsOfKind(visual, "GRAPH_NODE");
        List<UiDebugVisualElementDto> edges = elementsOfKind(visual, "GRAPH_EDGE");
        assertThat(nodes).extracting(UiDebugVisualElementDto::label)
                .as(sample.fileName() + " list node labels")
                .contains("1", "2", "3");
        assertThat(edges).extracting(UiDebugVisualElementDto::label)
                .as(sample.fileName() + " list edge labels")
                .containsExactly("next", "next");
        assertThat(nodes)
                .allSatisfy(node -> assertThat(metadata(node, "explanation"))
                        .contains("链表")
                        .contains("next")
                        .contains("指针"));
        assertThat(edges)
                .allSatisfy(edge -> assertThat(metadata(edge, "explanation"))
                        .contains("链表")
                        .contains("next")
                        .contains("指针"));
    }

    private static void assertLruList(VisualSample sample, UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> nodes = elementsOfKind(visual, "GRAPH_NODE");
        List<UiDebugVisualElementDto> edges = elementsOfKind(visual, "GRAPH_EDGE");
        assertThat(nodes).extracting(UiDebugVisualElementDto::label)
                .as(sample.fileName() + " lru node labels")
                .contains("10", "20", "30");
        assertThat(nodes).anySatisfy(node -> assertThat(node.metadata()).containsEntry("marker", "head"));
        assertThat(nodes).anySatisfy(node -> assertThat(node.metadata()).containsEntry("marker", "tail"));
        assertThat(edges).filteredOn(edge -> edge.label().equals("next"))
                .allSatisfy(edge -> assertThat(edge.metadata()).containsEntry("edge-role", "primary"));
        assertThat(edges).filteredOn(edge -> edge.label().equals("prev"))
                .allSatisfy(edge -> assertThat(edge.metadata()).containsEntry("edge-role", "auxiliary"));
    }

    private static void assertBinaryTree(VisualSample sample, UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> nodes = elementsOfKind(visual, "GRAPH_NODE");
        List<UiDebugVisualElementDto> edges = elementsOfKind(visual, "GRAPH_EDGE");
        assertThat(visual.layoutHint()).isEqualTo("hierarchical");
        assertThat(nodes).extracting(UiDebugVisualElementDto::label)
                .as(sample.fileName() + " binary-tree node labels")
                .contains("10", "5", "15");
        assertThat(edges).extracting(UiDebugVisualElementDto::label)
                .containsExactly("left", "right");
        assertThat(edges).allSatisfy(edge -> assertThat(edge.metadata()).containsEntry("edge-role", "primary"));
    }

    private static void assertHashChainTable(VisualSample sample, UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> nodes = elementsOfKind(visual, "GRAPH_NODE");
        List<UiDebugVisualElementDto> edges = elementsOfKind(visual, "GRAPH_EDGE");
        assertThat(visual.layoutHint()).isEqualTo("bucketed");
        assertThat(nodes).filteredOn(node -> metadataOrEmpty(node, "visual-role").equals("bucket"))
                .extracting(node -> metadataOrEmpty(node, "bucketIndex"))
                .contains("0", "1", "2");
        assertThat(nodes).filteredOn(node -> metadataOrEmpty(node, "visual-role").equals("chain-node"))
                .extracting(UiDebugVisualElementDto::label)
                .contains("10", "20", "30");
        assertThat(edges).allSatisfy(edge -> assertThat(edge.metadata()).containsKey("bucketIndex"));
    }

    private static void assertHeap(VisualSample sample, UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> cells = elementsOfKind(visual, "ARRAY_CELL");
        assertThat(cells).hasSize(4);
        assertThat(cells.getFirst().metadata())
                .containsEntry("leftIndex", "1")
                .containsEntry("rightIndex", "2");
        assertThat(cells.get(1).metadata()).containsEntry("parentIndex", "0");
    }

    private static void assertFenwickTree(VisualSample sample, UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> cells = elementsOfKind(visual, "ARRAY_CELL");
        assertThat(cells).hasSize(4);
        assertThat(cells.get(3).metadata())
                .containsEntry("fenwickIndex", "4")
                .containsEntry("rangeStart", "1")
                .containsEntry("rangeEnd", "4");
    }

    private static List<UiDebugVisualElementDto> assertPointerEdges(
            VisualSample sample,
            UiDebugVisualStructureDto visual,
            String description
    ) {
        List<UiDebugVisualElementDto> edges = elementsOfKind(visual, "GRAPH_EDGE");
        assertThat(edges)
                .as(sample.fileName() + " " + description + " edges")
                .hasSizeGreaterThanOrEqualTo(1);
        assertThat(edges)
                .allSatisfy(edge -> assertThat(metadata(edge, "pointerTarget")).isNotBlank());
        assertThat(edges)
                .allSatisfy(edge -> assertThat(metadata(edge, "from")).isNotBlank());
        assertThat(edges)
                .allSatisfy(edge -> assertThat(metadata(edge, "to")).isNotBlank());
        return edges;
    }

    private static List<UiDebugVisualElementDto> elementsOfKind(
            UiDebugVisualStructureDto visual,
            String elementKind
    ) {
        return visual.elements().stream()
                .filter(element -> element.kind().equals(elementKind))
                .toList();
    }

    private static String metadata(UiDebugVisualElementDto element, String key) {
        Map<String, String> metadata = element.metadata();
        assertThat(metadata)
                .as(element.id() + " metadata")
                .containsKey(key);
        return metadata.get(key);
    }

    private static String metadataOrEmpty(UiDebugVisualElementDto element, String key) {
        return element.metadata().getOrDefault(key, "");
    }

    private static String explanations(List<UiDebugVisualElementDto> elements) {
        return elements.stream()
                .map(element -> metadata(element, "explanation"))
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private static List<VisualSample> samples() {
        return List.of(
                new VisualSample("visual_scalar.mc", "value", "scalar"),
                new VisualSample("visual_pointer.mc", "p", "pointer"),
                new VisualSample("visual_pointer_chain.mc", "pp", "pointer-chain"),
                new VisualSample("visual_array.mc", "values", "array"),
                new VisualSample("visual_pointer_array.mc", "items", "pointer-array"),
                new VisualSample("visual_matrix.mc", "matrix", "matrix"),
                new VisualSample("visual_struct.mc", "point", "struct"),
                new VisualSample("visual_struct_pointer.mc", "ptr", "struct-pointer"),
                new VisualSample("visual_struct_pointer_chain.mc", "handle", "struct-pointer-chain"),
                new VisualSample("visual_struct_array.mc", "points", "struct-array"),
                new VisualSample("visual_struct_matrix.mc", "grid", "struct-matrix"),
                new VisualSample("visual_struct_list.mc", "head", "struct-list"),
                new VisualSample("visual_lru_list.mc", "head", "lru-list"),
                new VisualSample("visual_binary_tree.mc", "root", "binary-tree"),
                new VisualSample("visual_hash_chain_table.mc", "buckets", "hash-chain-table"),
                new VisualSample("visual_heap.mc", "heap", "heap"),
                new VisualSample("visual_fenwick_tree.mc", "bit", "fenwick-tree")
        );
    }

    private record VisualSample(String fileName, String expectedName, String expectedKind) {
    }
}
