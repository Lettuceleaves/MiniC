package minic.runtime.debug;

import minic.runtime.debug.visual.VisualEvent;

import java.util.List;
import java.util.Map;

final class IrDebugVisualEventRecorder {
    void recordVisualEvents(InterpreterState state, long snapshotId) {
        if (state.visualRuntimeGraphs.isEmpty() || !state.hasFrames()) {
            return;
        }
        CallFrame frame = state.currentFrame();
        for (VisualRuntimeGraph graph : state.visualRuntimeGraphs) {
            if (graph.visitVariable().isBlank()) {
                continue;
            }
            if (!graph.functionName().equals(frame.function.name())) {
                continue;
            }
            DebugValue visitValue = valueBySourceName(frame, graph.visitVariable());
            if (visitValue == null || numericValue(visitValue) == 0) {
                continue;
            }
            String nodeId = visitValue.summary();
            if (state.createdVisualNodeKeys.add(graph.name() + "\u0000" + nodeId)) {
                state.session.appendVisualEvent(VisualEvent.nodeCreated(
                        snapshotId,
                        graph.name(),
                        nodeId,
                        mappedValue(state, frame, graph.nodeLabelExpression(), nodeId)
                ));
            } else if (!graph.nodeLabelExpression().isBlank()) {
                state.session.appendVisualEvent(VisualEvent.nodeUpdated(
                        snapshotId,
                        graph.name(),
                        nodeId,
                        mappedValue(state, frame, graph.nodeLabelExpression(), nodeId)
                ));
            }
            for (VisualMetaMapping metaMapping : graph.metaMappings()) {
                String metaNodeId = mappedValue(state, frame, metaMapping.nodeExpression(), nodeId);
                String metaValue = mappedValue(state, frame, metaMapping.valueExpression(), "");
                if (!metaNodeId.isBlank() && !metaValue.isBlank()) {
                    state.session.appendVisualEvent(VisualEvent.metaSet(
                            snapshotId,
                            graph.name(),
                            metaNodeId,
                            metaMapping.key(),
                            metaValue
                    ));
                }
            }
            for (VisualEdgeMapping edgeMapping : graph.edgeMappings()) {
                String fromId = mappedValue(state, frame, edgeMapping.fromExpression(), "");
                String toId = mappedValue(state, frame, edgeMapping.toExpression(), "");
                if (!fromId.isBlank() && !toId.isBlank()) {
                    state.session.appendVisualEvent(VisualEvent.edgeSet(
                            snapshotId,
                            graph.name(),
                            edgeMapping.key(),
                            fromId,
                            toId
                    ));
                }
            }
        }
    }

    void recordVisualFieldWriteEvents(InterpreterState state, long snapshotId) {
        if (state.pendingVisualFieldWrites.isEmpty()) {
            return;
        }
        List<VisualFieldWrite> writes = List.copyOf(state.pendingVisualFieldWrites);
        state.pendingVisualFieldWrites.clear();
        for (VisualFieldWrite write : writes) {
            for (VisualRuntimeGraph graph : state.visualRuntimeGraphs) {
                if (!graph.functionName().equals(write.functionName())) {
                    continue;
                }
                VisualNodeMapping nodeMapping = graph.nodeMapping();
                if (nodeMapping == null || nodeMapping.idExpression().isBlank()) {
                    continue;
                }
                String nodeId = write.ownerAddress().display();
                if (nodeMapping.labelField().filter(write.fieldName()::equals).isPresent()) {
                    appendVisualNode(state, snapshotId, graph.name(), nodeId, write.value().summary());
                } else {
                    appendVisualNode(state, snapshotId, graph.name(), nodeId, visualNodeLabel(state, graph, write.ownerAddress(), nodeId));
                }
                for (VisualMetaMapping metaMapping : graph.metaMappings()) {
                    if (metaMapping.matchesField(nodeMapping.idExpression(), write.fieldName())) {
                        state.session.appendVisualEvent(VisualEvent.metaSet(
                                snapshotId,
                                graph.name(),
                                nodeId,
                                metaMapping.key(),
                                write.value().summary()
                        ));
                    }
                }
                for (VisualEdgeMapping edgeMapping : graph.edgeMappings()) {
                    if (!edgeMapping.matchesField(nodeMapping.idExpression(), write.fieldName())) {
                        continue;
                    }
                    String toId = write.value().kind() == DebugValueKind.NULL ? "null" : write.value().summary();
                    if (write.value().kind() == DebugValueKind.POINTER) {
                        DebugVirtualAddress targetAddress = pointerAddress(write.value());
                        appendVisualNode(state, snapshotId, graph.name(), toId, visualNodeLabel(state, graph, targetAddress, toId));
                    }
                    state.session.appendVisualEvent(VisualEvent.edgeSet(
                            snapshotId,
                            graph.name(),
                            edgeMapping.key(),
                            nodeId,
                            toId
                    ));
                }
            }
        }
    }

    private void appendVisualNode(InterpreterState state, long snapshotId, String graphName, String nodeId, String label) {
        if (state.createdVisualNodeKeys.add(graphName + "\u0000" + nodeId)) {
            state.session.appendVisualEvent(VisualEvent.nodeCreated(snapshotId, graphName, nodeId, label));
        } else {
            state.session.appendVisualEvent(VisualEvent.nodeUpdated(snapshotId, graphName, nodeId, label));
        }
    }

    private String visualNodeLabel(
            InterpreterState state,
            VisualRuntimeGraph graph,
            DebugVirtualAddress ownerAddress,
            String fallback
    ) {
        VisualNodeMapping nodeMapping = graph.nodeMapping();
        if (nodeMapping == null) {
            return fallback;
        }
        return nodeMapping.labelField()
                .map(fieldName -> fieldValueByName(state, pointerDebugValue(ownerAddress), fieldName))
                .map(DebugValue::summary)
                .orElse(fallback);
    }

    private DebugValue pointerDebugValue(DebugVirtualAddress address) {
        return DebugValue.pointerValue("pointer", address);
    }

    private String mappedValue(InterpreterState state, CallFrame frame, String expression, String fallback) {
        if (expression.isBlank()) {
            return fallback;
        }
        DebugValue value = valueByVisualExpression(state, frame, expression);
        return value == null ? expression : value.summary();
    }

    private DebugValue valueByVisualExpression(InterpreterState state, CallFrame frame, String expression) {
        String[] parts = expression.split("->");
        DebugValue value = valueBySourceName(frame, parts[0]);
        for (int i = 1; i < parts.length && value != null; i++) {
            value = fieldValueByName(state, value, parts[i]);
        }
        return value;
    }

    private DebugValue fieldValueByName(InterpreterState state, DebugValue owner, String fieldName) {
        if (owner.kind() == DebugValueKind.POINTER || owner.kind() == DebugValueKind.NULL) {
            if (owner.kind() == DebugValueKind.NULL) {
                return DebugValue.nullValue("pointer");
            }
            DebugVirtualAddress address = pointerAddress(owner);
            AddressLocal local = state.addressLocals.get(addressKey(address));
            AddressElement element = state.addressElements.get(addressKey(address));
            DebugValue localValue = local == null ? null : local.frame().locals.get(local.localName());
            DebugValue structValue = localValue != null && localValue.kind() == DebugValueKind.STRUCT
                    ? localValue
                    : element == null ? localValue : elementValue(state, element);
            if (structValue == null) {
                return null;
            }
            return fieldValueByName(state, structValue, fieldName);
        }
        if (owner.kind() == DebugValueKind.STRUCT) {
            return owner.fields().stream()
                    .filter(field -> field.name().equals(fieldName))
                    .map(DebugValueField::value)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private DebugValue valueBySourceName(CallFrame frame, String sourceName) {
        DebugValue parameter = frame.parameters.get(sourceName);
        if (parameter != null) {
            return parameter;
        }
        for (Map.Entry<String, DebugValue> entry : frame.locals.entrySet()) {
            if (frame.localNames.getOrDefault(entry.getKey(), entry.getKey()).equals(sourceName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private long numericValue(DebugValue value) {
        return switch (value.kind()) {
            case BOOL -> Boolean.parseBoolean(value.summary()) ? 1 : 0;
            case CHAR -> value.summary().length() >= 3 ? value.summary().charAt(1) : 0;
            case INT, LONG -> Long.parseLong(value.summary());
            case NULL -> 0;
            case POINTER -> value.pointerTargetOptional().map(DebugVirtualAddress::offset).orElse(0L);
            case FLOAT, DOUBLE -> (long) Double.parseDouble(value.summary());
            case ARRAY, STRUCT, UNINITIALIZED -> throw new IllegalStateException("value is not numeric: " + value.summary());
        };
    }

    private DebugVirtualAddress pointerAddress(DebugValue value) {
        return value.pointerTargetOptional().orElseThrow(() ->
                new IllegalStateException("value is not a pointer: " + value.summary()));
    }

    private DebugValue elementValue(InterpreterState state, AddressElement element) {
        AddressLocal local = state.addressLocals.get(addressKey(element.baseAddress()));
        if (local == null) {
            return state.memory.getOrDefault(addressKey(element.elementAddress()), DebugValue.uninitialized("element"));
        }
        DebugValue arrayValue = local.frame().locals.get(local.localName());
        if (arrayValue == null || arrayValue.kind() != DebugValueKind.ARRAY) {
            return DebugValue.uninitialized("element");
        }
        return arrayValue.elements().stream()
                .filter(valueElement -> valueElement.index() == element.index())
                .map(DebugValueElement::value)
                .findFirst()
                .orElse(DebugValue.uninitialized("element"));
    }

    private String addressKey(DebugVirtualAddress address) {
        return address.segment() + ":" + address.offset();
    }
}
