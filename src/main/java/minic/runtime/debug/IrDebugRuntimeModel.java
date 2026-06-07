package minic.runtime.debug;

import minic.compiler.ir.instruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.model.IrModule;
import minic.compiler.ir.value.IrTemporary;
import minic.runtime.debug.dataflow.DataFlowEventType;
import minic.runtime.debug.visual.VisualAnnotation;
import minic.source.SourceFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
final class InterpreterState {
        final IrModule module;
        final IrFunction function;
        final DebugSession session;
        final List<VisualRuntimeGraph> visualRuntimeGraphs;
        final Map<String, AddressLocal> addressLocals = new LinkedHashMap<>();
        final Map<String, AddressField> addressFields = new LinkedHashMap<>();
        final Map<String, AddressElement> addressElements = new LinkedHashMap<>();
        final Map<String, DebugValue> memory = new LinkedHashMap<>();
        final java.util.ArrayList<PendingDataFlowEvent> pendingDataFlowEvents = new java.util.ArrayList<>();
        final java.util.ArrayList<VisualFieldWrite> pendingVisualFieldWrites = new java.util.ArrayList<>();
        final java.util.Set<String> createdVisualNodeKeys = new java.util.LinkedHashSet<>();
        final java.util.ArrayList<CallFrame> frames = new java.util.ArrayList<>();
        long nextFrameId = 1;
        long nextSnapshotId = 1;
        long nextVisibleStep = 1;
        long currentVisibleStep;
        long nextEventId;
        boolean completed;
        DebugValue returnValue;
        String lastFunctionName;
        VisibleStepKey lastVisibleStepKey;
        final StringBuilder stdout = new StringBuilder();

        InterpreterState(
                IrModule module,
                IrFunction function,
                SourceFile sourceFile,
                List<VisualAnnotation> visualAnnotations
        ) {
            this.module = module;
            this.function = function;
            this.session = DebugSession.fromSource(sourceFile);
            List<VisualAnnotation> visualMapAnnotations = visualAnnotations.stream()
                    .filter(annotation -> annotation.directive().equals("@visual-map"))
                    .toList();
            this.visualRuntimeGraphs = visualAnnotations.stream()
                    .filter(annotation -> annotation.directive().equals("@visual"))
                    .filter(annotation -> annotation.attributes().getOrDefault("mode", "").equals("runtime"))
                    .filter(annotation -> annotation.attributes().containsKey("function"))
                    .map(annotation -> new VisualRuntimeGraph(
                            annotation.name(),
                            annotation.attributes().get("function"),
                            annotation.attributes().getOrDefault("visit", ""),
                            nodeMapping(annotation.name(), visualMapAnnotations),
                            metaMappings(annotation.name(), visualMapAnnotations),
                            edgeMappings(annotation.name(), visualMapAnnotations)
                    ))
                    .toList();
            this.lastFunctionName = function.name();
        }

        VisualNodeMapping nodeMapping(String graphName, List<VisualAnnotation> visualMapAnnotations) {
            return visualMapAnnotations.stream()
                    .filter(annotation -> annotation.name().equals(graphName))
                    .filter(annotation -> annotation.structureType().equals("node"))
                    .map(annotation -> new VisualNodeMapping(
                            annotation.attributes().getOrDefault("id", ""),
                            annotation.attributes().getOrDefault("label", "")
                    ))
                    .findFirst()
                    .orElse(null);
        }

        List<VisualMetaMapping> metaMappings(String graphName, List<VisualAnnotation> visualMapAnnotations) {
            return visualMapAnnotations.stream()
                    .filter(annotation -> annotation.name().equals(graphName))
                    .filter(annotation -> annotation.structureType().equals("meta"))
                    .map(annotation -> new VisualMetaMapping(
                            annotation.attributes().get("key"),
                            annotation.attributes().get("node"),
                            annotation.attributes().get("value")
                    ))
                    .toList();
        }

        List<VisualEdgeMapping> edgeMappings(String graphName, List<VisualAnnotation> visualMapAnnotations) {
            return visualMapAnnotations.stream()
                    .filter(annotation -> annotation.name().equals(graphName))
                    .filter(annotation -> annotation.structureType().equals("edge"))
                    .map(annotation -> new VisualEdgeMapping(
                            annotation.attributes().getOrDefault("key", annotation.attributes().getOrDefault("label", "edge")),
                            annotation.attributes().get("from"),
                            annotation.attributes().get("to")
                    ))
                    .toList();
        }

        void pushFrame(IrFunction function, List<DebugValue> arguments, IrTemporary returnTarget) {
            frames.add(new CallFrame("frame-" + function.name() + "-" + nextFrameId++, function, arguments, returnTarget));
            lastFunctionName = function.name();
        }

        void popFrame() {
            if (frames.isEmpty()) {
                throw new IllegalStateException("call stack is empty");
            }
            lastFunctionName = currentFrame().function.name();
            frames.removeLast();
        }

        boolean hasFrames() {
            return !frames.isEmpty();
        }

        CallFrame currentFrame() {
            if (frames.isEmpty()) {
                throw new IllegalStateException("call stack is empty");
            }
            return frames.getLast();
        }

        void jumpTo(String label) {
            currentFrame().jumpTo(label);
        }

        List<String> callStackSummary() {
            return frames.stream().map(frame -> frame.function.name()).toList();
        }

        String currentFunctionName() {
            return frames.isEmpty() ? lastFunctionName : currentFrame().function.name();
        }

        long visibleStepFor(DebugCursor cursor, DebugStopReason stopReason) {
            VisibleStepKey key = VisibleStepKey.from(cursor, frames.size(), stopReason);
            if (!key.equals(lastVisibleStepKey)) {
                currentVisibleStep = nextVisibleStep++;
                lastVisibleStepKey = key;
            }
            return currentVisibleStep;
        }
    }

record VisibleStepKey(
            String functionName,
            int callDepth,
            String sourceFile,
            int line,
            DebugStopReason stopReason
    ) {
        static VisibleStepKey from(DebugCursor cursor, int callDepth, DebugStopReason stopReason) {
            if (cursor.sourceRange() == null) {
                return new VisibleStepKey(
                        cursor.functionName(),
                        callDepth,
                        "",
                        -1,
                        stopReason
                );
            }
            return new VisibleStepKey(
                    cursor.functionName(),
                    callDepth,
                    cursor.sourceRange().sourceFile().path(),
                    cursor.sourceRange().startPosition().line(),
                    stopReason
            );
        }
    }

final class CallFrame {
        final String frameId;
        final IrFunction function;
        final IrTemporary returnTarget;
        final Map<String, Integer> blockIndexes = new LinkedHashMap<>();
        final Map<String, DebugValue> parameters = new LinkedHashMap<>();
        final Map<String, DebugValue> locals = new LinkedHashMap<>();
        final Map<String, String> localNames = new LinkedHashMap<>();
        final Map<String, DebugVirtualAddress> localAddresses = new LinkedHashMap<>();
        final Map<String, IrLocal> localSlots = new LinkedHashMap<>();
        final Map<String, AddressField> tempAddressFields = new LinkedHashMap<>();
        final Map<String, AddressElement> tempAddressElements = new LinkedHashMap<>();
        final Map<String, DebugValue> temps = new LinkedHashMap<>();
        int blockIndex;
        int instructionIndex;

        CallFrame(String frameId, IrFunction function, List<DebugValue> arguments, IrTemporary returnTarget) {
            this.frameId = frameId;
            this.function = function;
            this.returnTarget = returnTarget;
            for (int i = 0; i < function.parameters().size(); i++) {
                parameters.put(function.parameters().get(i).name(), arguments.get(i));
            }
            for (int i = 0; i < function.blocks().size(); i++) {
                IrBlock block = function.blocks().get(i);
                blockIndexes.put(block.label(), i);
                for (IrInstruction instruction : block.instructions()) {
                    if (instruction instanceof IrDeclareLocalInstruction declare) {
                        localNames.put(declare.local().name(), declare.local().sourceName());
                        localSlots.put(declare.local().name(), declare.local());
                    }
                }
            }
        }

        IrBlock currentBlock() {
            return function.blocks().get(blockIndex);
        }

        void jumpTo(String label) {
            Integer targetIndex = blockIndexes.get(label);
            if (targetIndex == null) {
                throw new IllegalStateException("unknown block label: " + label);
            }
            blockIndex = targetIndex;
            instructionIndex = 0;
        }
    }

record VisualRuntimeGraph(
            String name,
            String functionName,
            String visitVariable,
            VisualNodeMapping nodeMapping,
            List<VisualMetaMapping> metaMappings,
            List<VisualEdgeMapping> edgeMappings
    ) {
        String nodeLabelExpression() {
            return nodeMapping == null ? "" : nodeMapping.labelExpression();
        }
    }

record VisualNodeMapping(
            String idExpression,
            String labelExpression
    ) {
        java.util.Optional<String> labelField() {
            return VisualMappingExpressions.fieldOf(idExpression, labelExpression);
        }
    }

record VisualMetaMapping(
            String key,
            String nodeExpression,
            String valueExpression
    ) {
        boolean matchesField(String ownerExpression, String fieldName) {
            return nodeExpression.equals(ownerExpression)
                    && VisualMappingExpressions.fieldOf(ownerExpression, valueExpression).filter(fieldName::equals).isPresent();
        }
    }

record VisualEdgeMapping(
            String key,
            String fromExpression,
            String toExpression
    ) {
        boolean matchesField(String ownerExpression, String fieldName) {
            return fromExpression.equals(ownerExpression)
                    && VisualMappingExpressions.fieldOf(ownerExpression, toExpression).filter(fieldName::equals).isPresent();
        }
    }

record VisualFieldWrite(
            String functionName,
            DebugVirtualAddress ownerAddress,
            String fieldName,
            DebugValue value
    ) {
    }

record PendingDataFlowEvent(
            minic.source.SourceRange sourceRange,
            DataFlowEventType type,
            String cExpression,
            String lvaluePath,
            String oldValue,
            String newValue,
            String address,
            String pointerTarget
    ) {
    }

final class VisualMappingExpressions {
    private VisualMappingExpressions() {
    }

    static java.util.Optional<String> fieldOf(String ownerExpression, String expression) {
        String prefix = ownerExpression + "->";
        if (ownerExpression.isBlank() || !expression.startsWith(prefix)) {
            return java.util.Optional.empty();
        }
        String field = expression.substring(prefix.length());
        if (field.isBlank() || field.contains("->")) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(field);
    }
}

record AddressLocal(
            CallFrame frame,
            String localName
    ) {
    }

record AddressField(
            DebugVirtualAddress baseAddress,
            String fieldName
    ) {
    }

record AddressElement(
            DebugVirtualAddress baseAddress,
            DebugVirtualAddress elementAddress,
            long index
    ) {
    }
