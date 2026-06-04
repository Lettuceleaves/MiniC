package minic.runtime.debug.memory;

import minic.runtime.debug.DebugHeapBlock;
import minic.runtime.debug.DebugMemoryEntry;
import minic.runtime.debug.DebugProcessSpace;
import minic.runtime.debug.DebugStackFrame;
import minic.runtime.debug.DebugValue;
import minic.runtime.debug.DebugValueElement;
import minic.runtime.debug.DebugValueField;
import minic.runtime.debug.DebugValueKind;
import minic.runtime.debug.DebugVirtualAddress;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds recursive typed memory graphs from debugger process snapshots.
 */
public final class TypedMemoryGraphBuilder {
    private TypedMemoryGraphBuilder() {
    }

    /**
     * Builds a typed memory graph from all visible process-space memory roots.
     *
     * @param processSpace process space snapshot
     * @return typed memory graph
     */
    public static TypedMemoryGraph build(DebugProcessSpace processSpace) {
        Objects.requireNonNull(processSpace, "processSpace");
        BuildState state = new BuildState();
        List<TypedMemoryNode> roots = new ArrayList<>();

        processSpace.staticData().globals().forEach(entry -> roots.add(state.fromEntry(entry)));
        processSpace.staticData().stringLiterals().forEach(entry -> roots.add(state.fromEntry(entry)));
        for (DebugStackFrame frame : processSpace.stack().frames()) {
            frame.parameters().forEach(entry -> roots.add(state.fromEntry(entry)));
            frame.locals().forEach(entry -> roots.add(state.fromEntry(entry)));
        }
        processSpace.heap().blocks().forEach(block -> roots.add(state.fromHeapBlock(block)));

        return new TypedMemoryGraph(roots, state.resolvePointerEdges(roots));
    }

    private static final class BuildState {
        private final List<TypedPointerEdge> pointerEdges = new ArrayList<>();

        private TypedMemoryNode fromEntry(DebugMemoryEntry entry) {
            String address = addressDisplay(entry.address());
            return fromValue(
                    entry.name(),
                    address,
                    rootId(entry.name(), address),
                    entry.typeName(),
                    entry.value()
            );
        }

        private TypedMemoryNode fromHeapBlock(DebugHeapBlock block) {
            List<TypedMemoryField> fields = new ArrayList<>();
            String address = block.address().display();
            String rootId = rootId(address, address);
            for (DebugMemoryEntry entry : block.entries()) {
                fields.add(new TypedMemoryField(entry.name(), fromEntry(entry)));
            }
            return new TypedMemoryNode(
                    rootId,
                    block.address().display(),
                    block.address().display(),
                    block.typeName(),
                    TypeShape.HEAP_BLOCK,
                    block.status() + " size=" + block.size(),
                    fields,
                    List.of(),
                    null
            );
        }

        private TypedMemoryNode fromValue(String name, String address, String nodeId, String typeName, DebugValue value) {
            String pointerTarget = addressDisplay(value.pointerTarget());
            List<TypedMemoryField> fields = value.fields().stream()
                    .map(field -> toField(name, address, nodeId, field))
                    .toList();
            List<TypedMemoryElement> elements = value.elements().stream()
                    .map(element -> toElement(name, address, nodeId, element))
                    .toList();
            TypedMemoryNode node = new TypedMemoryNode(
                    nodeId,
                    name,
                    address,
                    typeName,
                    shapeOf(value.kind()),
                    value.summary(),
                    fields,
                    elements,
                    pointerTarget
            );
            if (pointerTarget != null) {
                pointerEdges.add(new TypedPointerEdge(node.id(), pointerTarget));
            }
            return node;
        }

        private TypedMemoryField toField(String parentName, String parentAddress, String parentId, DebugValueField field) {
            String fieldPath = parentName + "." + field.name();
            String fieldAddress = parentAddress == null ? null : parentAddress + "." + field.name();
            return new TypedMemoryField(
                    field.name(),
                    fromValue(fieldPath, fieldAddress, parentId + ".field:" + field.name(), field.value().typeName(), field.value())
            );
        }

        private TypedMemoryElement toElement(String parentName, String parentAddress, String parentId, DebugValueElement element) {
            String elementPath = parentName + "[" + element.index() + "]";
            String elementAddress = parentAddress == null ? null : parentAddress + "[" + element.index() + "]";
            return new TypedMemoryElement(
                    element.index(),
                    fromValue(elementPath, elementAddress, parentId + ".element:" + element.index(), element.value().typeName(), element.value())
            );
        }

        private List<TypedPointerEdge> resolvePointerEdges(List<TypedMemoryNode> roots) {
            Map<String, String> nodesByAddress = new LinkedHashMap<>();
            roots.forEach(root -> collectAddressIds(root, nodesByAddress));
            return pointerEdges.stream()
                    .map(edge -> new TypedPointerEdge(edge.fromNodeId(), edge.toAddress(), nodesByAddress.get(edge.toAddress())))
                    .toList();
        }

        private void collectAddressIds(TypedMemoryNode node, Map<String, String> nodesByAddress) {
            if (node.address() != null) {
                nodesByAddress.putIfAbsent(node.address(), node.id());
            }
            node.fields().forEach(field -> collectAddressIds(field.value(), nodesByAddress));
            node.elements().forEach(element -> collectAddressIds(element.value(), nodesByAddress));
        }
    }

    private static String rootId(String name, String address) {
        return "typed-memory-node:" + (address == null ? name : address) + ":" + name;
    }

    private static TypeShape shapeOf(DebugValueKind kind) {
        return switch (kind) {
            case INT, LONG, CHAR, BOOL, FLOAT, DOUBLE -> TypeShape.SCALAR;
            case POINTER -> TypeShape.POINTER;
            case ARRAY -> TypeShape.ARRAY;
            case STRUCT -> TypeShape.STRUCT;
            case NULL -> TypeShape.NULL;
            case UNINITIALIZED -> TypeShape.UNINITIALIZED;
        };
    }

    private static String addressDisplay(DebugVirtualAddress address) {
        return address == null ? null : address.display();
    }
}
