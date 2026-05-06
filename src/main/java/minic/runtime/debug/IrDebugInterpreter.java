package minic.runtime.debug;

import minic.compiler.ir.instruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.IrMoveInstruction;
import minic.compiler.ir.instruction.IrReturnInstruction;
import minic.compiler.ir.instruction.IrStoreLocalInstruction;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.model.IrModule;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrConstant;
import minic.compiler.ir.value.IrFloatConstant;
import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.source.SourceFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 最小 IR Debug 解释器。
 */
public final class IrDebugInterpreter {
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
        InterpreterState state = new InterpreterState(module, main, sourceFile);
        executeFunction(state);
        return state.session;
    }

    private void executeFunction(InterpreterState state) {
        if (state.function.blocks().isEmpty()) {
            throw new IllegalArgumentException("main function does not contain blocks");
        }
        for (int blockIndex = 0; blockIndex < state.function.blocks().size() && !state.completed; blockIndex++) {
            IrBlock block = state.function.blocks().get(blockIndex);
            for (int instructionIndex = 0; instructionIndex < block.instructions().size() && !state.completed; instructionIndex++) {
                IrInstruction instruction = block.instructions().get(instructionIndex);
                executeInstruction(state, block, instructionIndex, instruction);
            }
        }
        if (!state.completed) {
            state.session.setState(DebugExecutionState.COMPLETED);
        }
    }

    private void executeInstruction(InterpreterState state, IrBlock block, int instructionIndex, IrInstruction instruction) {
        if (instruction instanceof IrDeclareLocalInstruction declare) {
            state.locals.put(declare.local().name(), DebugValue.uninitialized(typeName(declare.local().type())));
            recordStep(state, block, instructionIndex, instruction, "DECLARE_LOCAL", declare.local().sourceName());
            return;
        }
        if (instruction instanceof IrStoreLocalInstruction store) {
            state.locals.put(store.local().name(), resolveValue(state, store.value()));
            recordStep(state, block, instructionIndex, instruction, "STORE_LOCAL", store.local().sourceName());
            return;
        }
        if (instruction instanceof IrLoadLocalInstruction load) {
            state.temps.put(load.result().name(), localValue(state, load.local()));
            recordStep(state, block, instructionIndex, instruction, "LOAD_LOCAL", load.local().sourceName());
            return;
        }
        if (instruction instanceof IrMoveInstruction move) {
            state.temps.put(move.result().name(), resolveValue(state, move.value()));
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
        if (instruction instanceof IrReturnInstruction ret) {
            state.returnValue = resolveValue(state, ret.value());
            state.completed = true;
            recordStep(state, block, instructionIndex, instruction, "RETURN", state.returnValue.summary(), DebugStopReason.COMPLETED, false);
            state.session.setState(DebugExecutionState.COMPLETED);
            return;
        }
        throw new UnsupportedOperationException("unsupported E150 IR instruction: " + instruction.getClass().getSimpleName());
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
            DebugValue debugValue = state.temps.get(temporary.name());
            if (debugValue == null) {
                throw new IllegalStateException("temporary is not available: " + temporary.name());
            }
            return debugValue;
        }
        throw new UnsupportedOperationException("unsupported E150 IR value: " + value.getClass().getSimpleName());
    }

    private DebugValue localValue(InterpreterState state, IrLocal local) {
        DebugValue debugValue = state.locals.get(local.name());
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
        long nextSnapshotId = state.nextSnapshotId++;
        long nextStep = state.nextVisibleStep++;
        DebugCursor cursor = new DebugCursor(
                state.function.name(),
                block.label(),
                block.label() + "#" + instructionIndex,
                instruction.range(),
                null,
                List.of()
        );
        DebugSnapshot snapshot = new DebugSnapshot(
                nextSnapshotId,
                nextStep,
                cursor,
                List.of(state.function.name()),
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
    }

    private DebugProcessSpace processSpace(InterpreterState state, DebugCursor cursor) {
        List<DebugMemoryEntry> locals = state.locals.entrySet().stream()
                .map(entry -> new DebugMemoryEntry(
                        sourceLocalName(state, entry.getKey()),
                        new DebugVirtualAddress("stack", Math.abs(entry.getKey().hashCode())),
                        entry.getValue().typeName(),
                        entry.getValue()
                ))
                .toList();
        DebugStackFrame frame = new DebugStackFrame(
                "frame-main",
                state.function.name(),
                List.of(),
                locals,
                null,
                cursor.sourceRange()
        );
        return new DebugProcessSpace(
                new DebugCodeSegment(
                        state.module.functions().stream().map(IrFunction::name).toList(),
                        state.function.name(),
                        cursor.instructionId(),
                        List.of()
                ),
                DebugStaticSegment.empty(),
                DebugStackSegment.empty().push(frame),
                DebugHeapSegment.empty(),
                new DebugIoSegment("", state.returnValue == null ? "" : "return " + state.returnValue.summary(), "")
        );
    }

    private String sourceLocalName(InterpreterState state, String localName) {
        return state.localNames.getOrDefault(localName, localName);
    }

    private String eventTitle(String eventType) {
        return switch (eventType) {
            case "DECLARE_LOCAL" -> "声明局部变量";
            case "STORE_LOCAL" -> "写入局部变量";
            case "LOAD_LOCAL" -> "读取局部变量";
            case "MOVE" -> "移动临时值";
            case "CHECK_INITIALIZED" -> "检查局部变量初始化";
            case "RETURN" -> "函数返回";
            default -> eventType;
        };
    }

    private String typeName(IrType type) {
        return type.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static final class InterpreterState {
        private final IrModule module;
        private final IrFunction function;
        private final DebugSession session;
        private final Map<String, DebugValue> locals = new LinkedHashMap<>();
        private final Map<String, String> localNames = new LinkedHashMap<>();
        private final Map<String, DebugValue> temps = new LinkedHashMap<>();
        private long nextSnapshotId = 1;
        private long nextVisibleStep = 1;
        private long nextEventId;
        private boolean completed;
        private DebugValue returnValue;

        private InterpreterState(IrModule module, IrFunction function, SourceFile sourceFile) {
            this.module = module;
            this.function = function;
            this.session = DebugSession.fromSource(sourceFile);
            for (IrBlock block : function.blocks()) {
                for (IrInstruction instruction : block.instructions()) {
                    if (instruction instanceof IrDeclareLocalInstruction declare) {
                        localNames.put(declare.local().name(), declare.local().sourceName());
                    }
                }
            }
        }
    }
}
