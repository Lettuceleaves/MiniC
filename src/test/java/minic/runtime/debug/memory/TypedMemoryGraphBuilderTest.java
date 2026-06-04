package minic.runtime.debug.memory;

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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TypedMemoryGraphBuilderTest {
    @Test
    void buildsScalarLocalAsScalarNode() {
        TypedMemoryGraph graph = TypedMemoryGraphBuilder.build(processSpaceWithLocals(
                local("x", 0x10, "int", DebugValue.intValue(42))
        ));

        assertThat(graph.findRoot("x")).hasValueSatisfying(node -> {
            assertThat(node.name()).isEqualTo("x");
            assertThat(node.address()).isEqualTo("stack:0x10");
            assertThat(node.typeName()).isEqualTo("int");
            assertThat(node.shape()).isEqualTo(TypeShape.SCALAR);
            assertThat(node.valueSummary()).isEqualTo("42");
            assertThat(node.fields()).isEmpty();
            assertThat(node.elements()).isEmpty();
            assertThat(node.pointerTarget()).isNull();
        });
        assertThat(graph.pointerEdges()).isEmpty();
    }

    @Test
    void buildsPointerLocalWithTargetAndPointerEdge() {
        DebugVirtualAddress target = new DebugVirtualAddress("heap", 0x40);
        TypedMemoryGraph graph = TypedMemoryGraphBuilder.build(processSpaceWithLocals(
                local("p", 0x18, "int *", DebugValue.pointerValue("int *", target))
        ));

        assertThat(graph.findRoot("p")).hasValueSatisfying(node -> {
            assertThat(node.shape()).isEqualTo(TypeShape.POINTER);
            assertThat(node.pointerTarget()).isEqualTo("heap:0x40");
            assertThat(graph.pointerEdges())
                    .containsExactly(new TypedPointerEdge(node.id(), "heap:0x40"));
        });
    }

    @Test
    void resolvesPointerEdgesToVisibleTargetNodes() {
        TypedMemoryGraph graph = TypedMemoryGraphBuilder.build(processSpaceWithLocals(
                local("x", 0x10, "int", DebugValue.intValue(7)),
                local("p", 0x18, "int *", DebugValue.pointerValue("int *", new DebugVirtualAddress("stack", 0x10)))
        ));

        TypedMemoryNode x = graph.findRoot("x").orElseThrow();
        TypedMemoryNode p = graph.findRoot("p").orElseThrow();

        assertThat(graph.pointerEdges())
                .containsExactly(new TypedPointerEdge(p.id(), "stack:0x10", x.id()));
    }

    @Test
    void buildsArrayLocalWithIndexedElements() {
        DebugValue array = DebugValue.arrayValue("int[3]", List.of(
                new DebugValueElement(0, DebugValue.intValue(10)),
                new DebugValueElement(1, DebugValue.intValue(20)),
                new DebugValueElement(2, DebugValue.intValue(30))
        ));

        TypedMemoryGraph graph = TypedMemoryGraphBuilder.build(processSpaceWithLocals(
                local("arr", 0x20, "int[3]", array)
        ));

        assertThat(graph.findRoot("arr")).hasValueSatisfying(node -> {
            assertThat(node.shape()).isEqualTo(TypeShape.ARRAY);
            assertThat(node.elements()).hasSize(3);
            assertThat(node.elements()).extracting(TypedMemoryElement::index)
                    .containsExactly(0L, 1L, 2L);
            assertThat(node.elements()).extracting(element -> element.value().valueSummary())
                    .containsExactly("10", "20", "30");
            assertThat(node.elements()).allSatisfy(element ->
                    assertThat(element.value().shape()).isEqualTo(TypeShape.SCALAR));
            assertThat(node.elements()).extracting(element -> element.value().address())
                    .containsExactly("stack:0x20[0]", "stack:0x20[1]", "stack:0x20[2]");
        });
    }

    @Test
    void buildsStructLocalWithNamedFields() {
        DebugValue point = pointValue(1, 2);

        TypedMemoryGraph graph = TypedMemoryGraphBuilder.build(processSpaceWithLocals(
                local("point", 0x30, "struct Point", point)
        ));

        assertThat(graph.findRoot("point")).hasValueSatisfying(node -> {
            assertThat(node.shape()).isEqualTo(TypeShape.STRUCT);
            assertThat(node.fields()).hasSize(2);
            assertThat(node.fields()).extracting(TypedMemoryField::name)
                    .containsExactly("x", "y");
            assertThat(node.fields()).extracting(field -> field.value().valueSummary())
                    .containsExactly("1", "2");
            assertThat(node.fields()).extracting(field -> field.value().address())
                    .containsExactly("stack:0x30.x", "stack:0x30.y");
            assertThat(node.fields()).extracting(field -> field.value().id())
                    .containsExactly(
                            "typed-memory-node:stack:0x30:point.field:x",
                            "typed-memory-node:stack:0x30:point.field:y"
                    );
        });
    }

    @Test
    void buildsStructArrayWithStructElementsAndFields() {
        DebugValue points = DebugValue.arrayValue("struct Point[3]", List.of(
                new DebugValueElement(0, pointValue(1, 2)),
                new DebugValueElement(1, pointValue(3, 4)),
                new DebugValueElement(2, pointValue(5, 6))
        ));

        TypedMemoryGraph graph = TypedMemoryGraphBuilder.build(processSpaceWithLocals(
                local("points", 0x40, "struct Point[3]", points)
        ));

        assertThat(graph.findRoot("points")).hasValueSatisfying(node -> {
            assertThat(node.shape()).isEqualTo(TypeShape.ARRAY);
            assertThat(node.elements()).hasSize(3);
            assertThat(node.elements()).allSatisfy(element -> {
                assertThat(element.value().shape()).isEqualTo(TypeShape.STRUCT);
                assertThat(element.value().fields()).extracting(TypedMemoryField::name)
                        .containsExactly("x", "y");
            });
            assertThat(node.elements().get(1).value().fields())
                    .extracting(field -> field.value().valueSummary())
                    .containsExactly("3", "4");
            assertThat(node.elements().get(1).value().address()).isEqualTo("stack:0x40[1]");
            assertThat(node.elements().get(1).value().fields())
                    .extracting(field -> field.value().address())
                    .containsExactly("stack:0x40[1].x", "stack:0x40[1].y");
        });
    }

    @Test
    void usesStableIdsDerivedFromAddressAndPath() {
        TypedMemoryGraph graph = TypedMemoryGraphBuilder.build(processSpaceWithLocals(
                local("before", 0x28, "int", DebugValue.intValue(0)),
                local("point", 0x30, "struct Point", pointValue(1, 2))
        ));

        assertThat(graph.findRoot("point")).hasValueSatisfying(node -> {
            assertThat(node.id()).isEqualTo("typed-memory-node:stack:0x30:point");
            assertThat(node.fields()).extracting(field -> field.value().id())
                    .containsExactly(
                            "typed-memory-node:stack:0x30:point.field:x",
                            "typed-memory-node:stack:0x30:point.field:y"
                    );
        });
    }

    private static DebugValue pointValue(int x, int y) {
        return DebugValue.structValue("struct Point", List.of(
                new DebugValueField("x", DebugValue.intValue(x)),
                new DebugValueField("y", DebugValue.intValue(y))
        ));
    }

    private static DebugMemoryEntry local(String name, long offset, String typeName, DebugValue value) {
        return new DebugMemoryEntry(name, new DebugVirtualAddress("stack", offset), typeName, value);
    }

    private static DebugProcessSpace processSpaceWithLocals(DebugMemoryEntry... locals) {
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
}
