package minic.runtime.debug;

import minic.compiler.ir.instruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.IrBinaryOperator;
import minic.compiler.ir.instruction.IrBranchInstruction;
import minic.compiler.ir.instruction.IrCastInstruction;
import minic.compiler.ir.instruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.IrCheckNonZeroInstruction;
import minic.compiler.ir.instruction.IrCallInstruction;
import minic.compiler.ir.instruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.IrJumpInstruction;
import minic.compiler.ir.instruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.IrMoveInstruction;
import minic.compiler.ir.instruction.IrReturnInstruction;
import minic.compiler.ir.instruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.IrUnaryInstruction;
import minic.compiler.ir.instruction.IrUnaryOperator;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.model.IrModule;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrConstant;
import minic.compiler.ir.value.IrFloatConstant;
import minic.compiler.ir.value.IrParameterRef;
import minic.compiler.ir.value.IrStringLiteral;
import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.runtime.debug.visual.VisualAnnotation;
import minic.runtime.debug.visual.VisualAnnotationParser;
import minic.runtime.debug.visual.VisualEvent;
import minic.source.SourceFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 最小 IR Debug 解释器。
 */
public final class IrDebugInterpreter {
    private final DebugExternalFunctionRegistry externalFunctions;

    /**
     * 使用默认外部函数 stub 创建解释器。
     */
    public IrDebugInterpreter() {
        this(DebugExternalFunctionRegistry.defaults());
    }

    /**
     * 使用指定外部函数 stub 注册表创建解释器。
     *
     * @param externalFunctions 外部函数 stub 注册表
     */
    public IrDebugInterpreter(DebugExternalFunctionRegistry externalFunctions) {
        this.externalFunctions = Objects.requireNonNull(externalFunctions, "externalFunctions");
    }

    /**
     * 执行模块中的 main 函数。
     *
     * @param module IR 模块
     * @param sourceFile 源码文件
     * @return Debug 会话
     */
    public DebugSession runMain(IrModule module, SourceFile sourceFile) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(sourceFile, "sourceFile");
        IrFunction main = module.findFunction("main").orElseThrow(() ->
                new IllegalArgumentException("IR module does not contain main"));
        List<VisualAnnotation> visualAnnotations = new VisualAnnotationParser().parse(sourceFile).annotations();
        InterpreterState state = new InterpreterState(module, main, sourceFile, visualAnnotations);
        state.pushFrame(main, List.of(), null);
        executeFunction(state);
        return state.session;
    }

    private void executeFunction(InterpreterState state) {
        if (state.function.blocks().isEmpty()) {
            throw new IllegalArgumentException("main function does not contain blocks");
        }
        while (!state.completed) {
            CallFrame frame = state.currentFrame();
            IrBlock block = frame.currentBlock();
            if (frame.instructionIndex >= block.instructions().size()) {
                state.completed = true;
                state.session.setState(DebugExecutionState.COMPLETED);
                return;
            }
            int currentInstructionIndex = frame.instructionIndex;
            IrInstruction instruction = block.instructions().get(currentInstructionIndex);
            frame.instructionIndex++;
            executeInstruction(state, block, currentInstructionIndex, instruction);
        }
    }

    private void executeInstruction(InterpreterState state, IrBlock block, int instructionIndex, IrInstruction instruction) {
        if (instruction instanceof IrDeclareLocalInstruction declare) {
            state.currentFrame().locals.put(declare.local().name(), DebugValue.uninitialized(typeName(declare.local().type())));
            recordStep(state, block, instructionIndex, instruction, "DECLARE_LOCAL", declare.local().sourceName());
            return;
        }
        if (instruction instanceof IrStoreLocalInstruction store) {
            state.currentFrame().locals.put(store.local().name(), resolveValue(state, store.value()));
            recordStep(state, block, instructionIndex, instruction, "STORE_LOCAL", store.local().sourceName());
            return;
        }
        if (instruction instanceof IrLoadLocalInstruction load) {
            state.currentFrame().temps.put(load.result().name(), localValue(state, load.local()));
            recordStep(state, block, instructionIndex, instruction, "LOAD_LOCAL", load.local().sourceName());
            return;
        }
        if (instruction instanceof IrMoveInstruction move) {
            state.currentFrame().temps.put(move.result().name(), resolveValue(state, move.value()));
            recordStep(state, block, instructionIndex, instruction, "MOVE", move.result().name());
            return;
        }
        if (instruction instanceof IrCheckInitializedInstruction check) {
            DebugValue value = localValue(state, check.local());
            if (value.kind() == DebugValueKind.UNINITIALIZED) {
                state.completed = true;
                recordStep(state, block, instructionIndex, instruction, "CHECK_INITIALIZED", check.local().sourceName(), DebugStopReason.ERROR, false);
                state.session.setState(DebugExecutionState.FAILED);
                return;
            }
            recordStep(state, block, instructionIndex, instruction, "CHECK_INITIALIZED", check.local().sourceName());
            return;
        }
        if (instruction instanceof IrCheckNonZeroInstruction check) {
            DebugValue value = resolveValue(state, check.value());
            if (numericValue(value) == 0) {
                state.completed = true;
                recordStep(state, block, instructionIndex, instruction, "CHECK_NON_ZERO", value.summary(), DebugStopReason.ERROR, false);
                state.session.setState(DebugExecutionState.FAILED);
                return;
            }
            recordStep(state, block, instructionIndex, instruction, "CHECK_NON_ZERO", value.summary());
            return;
        }
        if (instruction instanceof IrBinaryInstruction binary) {
            state.currentFrame().temps.put(binary.result().name(), binaryValue(state, binary));
            recordStep(state, block, instructionIndex, instruction, "BINARY", binary.operator().name());
            return;
        }
        if (instruction instanceof IrUnaryInstruction unary) {
            state.currentFrame().temps.put(unary.result().name(), unaryValue(state, unary));
            recordStep(state, block, instructionIndex, instruction, "UNARY", unary.operator().name());
            return;
        }
        if (instruction instanceof IrCastInstruction cast) {
            state.currentFrame().temps.put(cast.result().name(), castValue(resolveValue(state, cast.value()), cast.result().type()));
            recordStep(state, block, instructionIndex, instruction, "CAST", cast.result().type().name());
            return;
        }
        if (instruction instanceof IrBranchInstruction branch) {
            DebugValue condition = resolveValue(state, branch.condition());
            state.jumpTo(numericValue(condition) != 0 ? branch.thenLabel() : branch.elseLabel());
            recordStep(state, block, instructionIndex, instruction, "BRANCH", condition.summary());
            return;
        }
        if (instruction instanceof IrJumpInstruction jump) {
            state.jumpTo(jump.targetLabel());
            recordStep(state, block, instructionIndex, instruction, "JUMP", jump.targetLabel());
            return;
        }
        if (instruction instanceof IrCallInstruction call) {
            List<DebugValue> arguments = call.arguments().stream()
                    .map(argument -> resolveValue(state, argument))
                    .toList();
            java.util.Optional<IrFunction> callee = state.module.findFunction(call.calleeName());
            if (callee.isEmpty()) {
                executeExternalCall(state, block, instructionIndex, call, arguments);
                return;
            }
            recordStep(state, block, instructionIndex, instruction, "CALL", call.calleeName());
            state.pushFrame(callee.orElseThrow(), arguments, call.result());
            return;
        }
        if (instruction instanceof IrReturnInstruction ret) {
            state.returnValue = resolveValue(state, ret.value());
            IrTemporary returnTarget = state.currentFrame().returnTarget;
            state.popFrame();
            if (returnTarget == null || !state.hasFrames()) {
                state.completed = true;
                recordStep(state, block, instructionIndex, instruction, "RETURN", state.returnValue.summary(), DebugStopReason.COMPLETED, false);
                state.session.setState(DebugExecutionState.COMPLETED);
            } else {
                state.currentFrame().temps.put(returnTarget.name(), state.returnValue);
                recordStep(state, block, instructionIndex, instruction, "RETURN", state.returnValue.summary(), DebugStopReason.RETURN, false);
            }
            return;
        }
        throw new UnsupportedOperationException("unsupported E160 IR instruction: " + instruction.getClass().getSimpleName());
    }

    private DebugValue resolveValue(InterpreterState state, IrValue value) {
        if (value instanceof IrConstant constant) {
            return constantValue(constant);
        }
        if (value instanceof IrFloatConstant floating) {
            return new DebugValue(
                    floating.type() == IrType.FLOAT ? DebugValueKind.FLOAT : DebugValueKind.DOUBLE,
                    typeName(floating.type()),
                    Double.toString(floating.value()),
                    null,
                    List.of(),
                    List.of()
            );
        }
        if (value instanceof IrTemporary temporary) {
            DebugValue debugValue = state.currentFrame().temps.get(temporary.name());
            if (debugValue == null) {
                throw new IllegalStateException("temporary is not available: " + temporary.name());
            }
            return debugValue;
        }
        if (value instanceof IrParameterRef parameterRef) {
            DebugValue debugValue = state.currentFrame().parameters.get(parameterRef.name());
            if (debugValue == null) {
                throw new IllegalStateException("parameter is not available: " + parameterRef.name());
            }
            return debugValue;
        }
        if (value instanceof IrStringLiteral stringLiteral) {
            return DebugValue.pointerValue("char *", stringAddress(stringLiteral.label()));
        }
        throw new UnsupportedOperationException("unsupported E160 IR value: " + value.getClass().getSimpleName());
    }

    private void executeExternalCall(
            InterpreterState state,
            IrBlock block,
            int instructionIndex,
            IrCallInstruction call,
            List<DebugValue> arguments
    ) {
        if (!state.module.externalFunctionNames().contains(call.calleeName())) {
            throw new UnsupportedOperationException("function is not available: " + call.calleeName());
        }
        DebugExternalFunctionStub stub = externalFunctions.find(call.calleeName()).orElseThrow(() ->
                new UnsupportedOperationException("debug external stub is not available: " + call.calleeName()));
        DebugExternalCallResult result = stub.invoke(
                call.calleeName(),
                call.arguments(),
                arguments,
                new DebugExternalCallContext(state.module)
        );
        state.currentFrame().temps.put(call.result().name(), result.returnValue());
        state.stdout.append(result.stdoutAppend());
        recordStep(state, block, instructionIndex, call, "CALL_EXTERNAL", result.description());
    }

    private DebugValue binaryValue(InterpreterState state, IrBinaryInstruction binary) {
        DebugValue left = resolveValue(state, binary.left());
        DebugValue right = resolveValue(state, binary.right());
        long leftValue = numericValue(left);
        long rightValue = numericValue(right);
        IrType resultType = binary.result().type();
        return switch (binary.operator()) {
            case ADD -> integerResult(resultType, leftValue + rightValue);
            case SUBTRACT -> integerResult(resultType, leftValue - rightValue);
            case MULTIPLY -> integerResult(resultType, leftValue * rightValue);
            case DIVIDE -> integerResult(resultType, leftValue / rightValue);
            case MODULO -> integerResult(resultType, leftValue % rightValue);
            case BITWISE_AND -> integerResult(resultType, leftValue & rightValue);
            case BITWISE_OR -> integerResult(resultType, leftValue | rightValue);
            case BITWISE_XOR -> integerResult(resultType, leftValue ^ rightValue);
            case SHIFT_LEFT -> integerResult(resultType, leftValue << rightValue);
            case SHIFT_RIGHT -> integerResult(resultType, leftValue >> rightValue);
            case LOGICAL_AND -> DebugValue.intValue(leftValue != 0 && rightValue != 0 ? 1 : 0);
            case LOGICAL_OR -> DebugValue.intValue(leftValue != 0 || rightValue != 0 ? 1 : 0);
            case EQUAL -> DebugValue.intValue(leftValue == rightValue ? 1 : 0);
            case NOT_EQUAL -> DebugValue.intValue(leftValue != rightValue ? 1 : 0);
            case LESS_THAN -> DebugValue.intValue(leftValue < rightValue ? 1 : 0);
            case LESS_EQUAL -> DebugValue.intValue(leftValue <= rightValue ? 1 : 0);
            case GREATER_THAN -> DebugValue.intValue(leftValue > rightValue ? 1 : 0);
            case GREATER_EQUAL -> DebugValue.intValue(leftValue >= rightValue ? 1 : 0);
        };
    }

    private DebugValue unaryValue(InterpreterState state, IrUnaryInstruction unary) {
        DebugValue operand = resolveValue(state, unary.operand());
        long value = numericValue(operand);
        return switch (unary.operator()) {
            case LOGICAL_NOT -> DebugValue.intValue(value == 0 ? 1 : 0);
            case BITWISE_NOT -> integerResult(unary.result().type(), ~value);
        };
    }

    private DebugValue castValue(DebugValue value, IrType targetType) {
        if (targetType == IrType.FLOAT || targetType == IrType.DOUBLE) {
            return new DebugValue(
                    targetType == IrType.FLOAT ? DebugValueKind.FLOAT : DebugValueKind.DOUBLE,
                    typeName(targetType),
                    Double.toString(numericValue(value)),
                    null,
                    List.of(),
                    List.of()
            );
        }
        return integerResult(targetType, numericValue(value));
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

    private DebugValue integerResult(IrType type, long value) {
        return switch (type) {
            case BOOL -> DebugValue.boolValue(value != 0);
            case CHAR -> DebugValue.charValue((char) value);
            case INT -> DebugValue.intValue((int) value);
            case LONG -> DebugValue.longValue(value);
            case POINTER -> value == 0
                    ? DebugValue.nullValue("pointer")
                    : DebugValue.pointerValue("pointer", new DebugVirtualAddress("heap", value));
            case FLOAT, DOUBLE, INT_ARRAY, STRUCT -> throw new UnsupportedOperationException("unsupported integer result type: " + type);
        };
    }

    private DebugValue localValue(InterpreterState state, IrLocal local) {
        DebugValue debugValue = state.currentFrame().locals.get(local.name());
        if (debugValue == null) {
            throw new IllegalStateException("local is not declared: " + local.name());
        }
        return debugValue;
    }

    private DebugValue constantValue(IrConstant constant) {
        return switch (constant.type()) {
            case BOOL -> DebugValue.boolValue(constant.value() != 0);
            case CHAR -> DebugValue.charValue((char) constant.value());
            case INT -> DebugValue.intValue((int) constant.value());
            case LONG -> DebugValue.longValue(constant.value());
            case POINTER -> constant.value() == 0
                    ? DebugValue.nullValue("pointer")
                    : DebugValue.pointerValue("pointer", new DebugVirtualAddress("heap", constant.value()));
            case FLOAT, DOUBLE, INT_ARRAY, STRUCT -> throw new UnsupportedOperationException(
                    "unsupported integer constant type: " + constant.type());
        };
    }

    private void recordStep(
            InterpreterState state,
            IrBlock block,
            int instructionIndex,
            IrInstruction instruction,
            String eventType,
            String description
    ) {
        recordStep(state, block, instructionIndex, instruction, eventType, description, DebugStopReason.STEP, false);
    }

    private void recordStep(
            InterpreterState state,
            IrBlock block,
            int instructionIndex,
            IrInstruction instruction,
            String eventType,
            String description,
            DebugStopReason stopReason,
            boolean breakpointHit
    ) {
        DebugCursor cursor = new DebugCursor(
                state.function.name(),
                block.label(),
                block.label() + "#" + instructionIndex,
                instruction.range(),
                null,
                List.of()
        );
        long nextSnapshotId = state.nextSnapshotId++;
        long nextStep = state.visibleStepFor(cursor, stopReason);
        DebugSnapshot snapshot = new DebugSnapshot(
                nextSnapshotId,
                nextStep,
                cursor,
                state.callStackSummary(),
                processSpace(state, cursor),
                breakpointHit,
                stopReason
        );
        state.session.appendSnapshot(snapshot);
        state.session.appendEvent(new DebugEvent(
                state.nextEventId++,
                snapshot.snapshotId(),
                eventType,
                eventTitle(eventType),
                description,
                instruction.range(),
                List.of()
        ));
        recordVisualEvents(state, snapshot.snapshotId());
    }

    private void recordVisualEvents(InterpreterState state, long snapshotId) {
        if (state.visualRuntimeGraphs.isEmpty() || !state.hasFrames()) {
            return;
        }
        CallFrame frame = state.currentFrame();
        for (VisualRuntimeGraph graph : state.visualRuntimeGraphs) {
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
                        mappedValue(frame, graph.nodeLabelExpression(), nodeId)
                ));
            } else if (!graph.nodeLabelExpression().isBlank()) {
                state.session.appendVisualEvent(VisualEvent.nodeUpdated(
                        snapshotId,
                        graph.name(),
                        nodeId,
                        mappedValue(frame, graph.nodeLabelExpression(), nodeId)
                ));
            }
            for (VisualMetaMapping metaMapping : graph.metaMappings()) {
                String metaNodeId = mappedValue(frame, metaMapping.nodeExpression(), nodeId);
                String metaValue = mappedValue(frame, metaMapping.valueExpression(), "");
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
                String fromId = mappedValue(frame, edgeMapping.fromExpression(), "");
                String toId = mappedValue(frame, edgeMapping.toExpression(), "");
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

    private String mappedValue(CallFrame frame, String expression, String fallback) {
        if (expression.isBlank()) {
            return fallback;
        }
        DebugValue value = valueBySourceName(frame, expression);
        return value == null ? expression : value.summary();
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

    private DebugProcessSpace processSpace(InterpreterState state, DebugCursor cursor) {
        java.util.ArrayList<DebugStackFrame> frames = new java.util.ArrayList<>();
        for (CallFrame frameState : state.frames) {
            List<DebugMemoryEntry> frameLocals = frameState.locals.entrySet().stream()
                    .map(entry -> memoryEntry(frameState, entry))
                    .toList();
            List<DebugMemoryEntry> frameParameters = frameState.parameters.entrySet().stream()
                    .map(entry -> new DebugMemoryEntry(
                            entry.getKey(),
                            new DebugVirtualAddress("stack", Math.abs((frameState.function.name() + ":param:" + entry.getKey()).hashCode())),
                            entry.getValue().typeName(),
                            entry.getValue()
                    ))
                    .toList();
            frames.add(new DebugStackFrame(
                    "frame-" + frameState.function.name() + "-" + frames.size(),
                    frameState.function.name(),
                    frameParameters,
                    frameLocals,
                    frameState.returnTarget == null ? null : frameState.returnTarget.name(),
                    frameState == state.currentFrame() ? cursor.sourceRange() : null
            ));
        }
        if (frames.isEmpty()) {
            frames.add(new DebugStackFrame(
                    "frame-completed",
                    state.lastFunctionName,
                    List.of(),
                    List.of(),
                    null,
                    cursor.sourceRange()
            ));
        }
        return new DebugProcessSpace(
                new DebugCodeSegment(
                        state.module.functions().stream().map(IrFunction::name).toList(),
                        state.currentFunctionName(),
                        cursor.instructionId(),
                        List.of()
                ),
                staticSegment(state),
                new DebugStackSegment(frames),
                DebugHeapSegment.empty(),
                new DebugIoSegment("", stdout(state), "")
        );
    }

    private DebugStaticSegment staticSegment(InterpreterState state) {
        List<DebugMemoryEntry> stringLiterals = state.module.stringData().stream()
                .map(stringData -> new DebugMemoryEntry(
                        stringData.label(),
                        stringAddress(stringData.label()),
                        "char[]",
                        DebugValue.arrayValue("char[]", stringElements(stringData.value()))
                ))
                .toList();
        return new DebugStaticSegment(List.of(), stringLiterals);
    }

    private List<DebugValueElement> stringElements(String value) {
        java.util.ArrayList<DebugValueElement> elements = new java.util.ArrayList<>();
        for (int i = 0; i < value.length(); i++) {
            elements.add(new DebugValueElement(i, DebugValue.charValue(value.charAt(i))));
        }
        return elements;
    }

    private String stdout(InterpreterState state) {
        String stdout = state.stdout.toString();
        if (state.returnValue == null) {
            return stdout;
        }
        return stdout + "return " + state.returnValue.summary();
    }

    private DebugMemoryEntry memoryEntry(CallFrame frame, Map.Entry<String, DebugValue> entry) {
        return new DebugMemoryEntry(
                frame.localNames.getOrDefault(entry.getKey(), entry.getKey()),
                new DebugVirtualAddress("stack", Math.abs((frame.function.name() + ":" + entry.getKey()).hashCode())),
                entry.getValue().typeName(),
                entry.getValue()
        );
    }

    private String eventTitle(String eventType) {
        return switch (eventType) {
            case "DECLARE_LOCAL" -> "声明局部变量";
            case "STORE_LOCAL" -> "写入局部变量";
            case "LOAD_LOCAL" -> "读取局部变量";
            case "MOVE" -> "移动临时值";
            case "CHECK_INITIALIZED" -> "检查局部变量初始化";
            case "CHECK_NON_ZERO" -> "检查除数非零";
            case "BINARY" -> "计算二元表达式";
            case "UNARY" -> "计算一元表达式";
            case "CAST" -> "转换值类型";
            case "BRANCH" -> "条件跳转";
            case "JUMP" -> "无条件跳转";
            case "CALL_EXTERNAL" -> "调用外部函数";
            case "RETURN" -> "函数返回";
            default -> eventType;
        };
    }

    private DebugVirtualAddress stringAddress(String label) {
        return new DebugVirtualAddress("static", Math.abs(label.hashCode()));
    }

    private String typeName(IrType type) {
        return type.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static final class InterpreterState {
        private final IrModule module;
        private final IrFunction function;
        private final DebugSession session;
        private final List<VisualRuntimeGraph> visualRuntimeGraphs;
        private final java.util.Set<String> createdVisualNodeKeys = new java.util.LinkedHashSet<>();
        private final java.util.ArrayList<CallFrame> frames = new java.util.ArrayList<>();
        private long nextSnapshotId = 1;
        private long nextVisibleStep = 1;
        private long currentVisibleStep;
        private long nextEventId;
        private boolean completed;
        private DebugValue returnValue;
        private String lastFunctionName;
        private VisibleStepKey lastVisibleStepKey;
        private final StringBuilder stdout = new StringBuilder();

        private InterpreterState(
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
                    .filter(annotation -> annotation.attributes().containsKey("visit"))
                    .map(annotation -> new VisualRuntimeGraph(
                            annotation.name(),
                            annotation.attributes().get("function"),
                            annotation.attributes().get("visit"),
                            nodeLabelExpression(annotation.name(), visualMapAnnotations),
                            metaMappings(annotation.name(), visualMapAnnotations),
                            edgeMappings(annotation.name(), visualMapAnnotations)
                    ))
                    .toList();
            this.lastFunctionName = function.name();
        }

        private String nodeLabelExpression(String graphName, List<VisualAnnotation> visualMapAnnotations) {
            return visualMapAnnotations.stream()
                    .filter(annotation -> annotation.name().equals(graphName))
                    .filter(annotation -> annotation.structureType().equals("node"))
                    .map(annotation -> annotation.attributes().getOrDefault("label", ""))
                    .filter(label -> !label.isBlank())
                    .findFirst()
                    .orElse("");
        }

        private List<VisualMetaMapping> metaMappings(String graphName, List<VisualAnnotation> visualMapAnnotations) {
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

        private List<VisualEdgeMapping> edgeMappings(String graphName, List<VisualAnnotation> visualMapAnnotations) {
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

        private void pushFrame(IrFunction function, List<DebugValue> arguments, IrTemporary returnTarget) {
            frames.add(new CallFrame(function, arguments, returnTarget));
            lastFunctionName = function.name();
        }

        private void popFrame() {
            if (frames.isEmpty()) {
                throw new IllegalStateException("call stack is empty");
            }
            lastFunctionName = currentFrame().function.name();
            frames.removeLast();
        }

        private boolean hasFrames() {
            return !frames.isEmpty();
        }

        private CallFrame currentFrame() {
            if (frames.isEmpty()) {
                throw new IllegalStateException("call stack is empty");
            }
            return frames.getLast();
        }

        private void jumpTo(String label) {
            currentFrame().jumpTo(label);
        }

        private List<String> callStackSummary() {
            return frames.stream().map(frame -> frame.function.name()).toList();
        }

        private String currentFunctionName() {
            return frames.isEmpty() ? lastFunctionName : currentFrame().function.name();
        }

        private long visibleStepFor(DebugCursor cursor, DebugStopReason stopReason) {
            VisibleStepKey key = VisibleStepKey.from(cursor, frames.size(), stopReason);
            if (!key.equals(lastVisibleStepKey)) {
                currentVisibleStep = nextVisibleStep++;
                lastVisibleStepKey = key;
            }
            return currentVisibleStep;
        }
    }

    private record VisibleStepKey(
            String functionName,
            int callDepth,
            String sourceFile,
            int line,
            DebugStopReason stopReason
    ) {
        private static VisibleStepKey from(DebugCursor cursor, int callDepth, DebugStopReason stopReason) {
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

    private static final class CallFrame {
        private final IrFunction function;
        private final IrTemporary returnTarget;
        private final Map<String, Integer> blockIndexes = new LinkedHashMap<>();
        private final Map<String, DebugValue> parameters = new LinkedHashMap<>();
        private final Map<String, DebugValue> locals = new LinkedHashMap<>();
        private final Map<String, String> localNames = new LinkedHashMap<>();
        private final Map<String, DebugValue> temps = new LinkedHashMap<>();
        private int blockIndex;
        private int instructionIndex;

        private CallFrame(IrFunction function, List<DebugValue> arguments, IrTemporary returnTarget) {
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
                    }
                }
            }
        }

        private IrBlock currentBlock() {
            return function.blocks().get(blockIndex);
        }

        private void jumpTo(String label) {
            Integer targetIndex = blockIndexes.get(label);
            if (targetIndex == null) {
                throw new IllegalStateException("unknown block label: " + label);
            }
            blockIndex = targetIndex;
            instructionIndex = 0;
        }
    }

    private record VisualRuntimeGraph(
            String name,
            String functionName,
            String visitVariable,
            String nodeLabelExpression,
            List<VisualMetaMapping> metaMappings,
            List<VisualEdgeMapping> edgeMappings
    ) {
    }

    private record VisualMetaMapping(
            String key,
            String nodeExpression,
            String valueExpression
    ) {
    }

    private record VisualEdgeMapping(
            String key,
            String fromExpression,
            String toExpression
    ) {
    }
}
