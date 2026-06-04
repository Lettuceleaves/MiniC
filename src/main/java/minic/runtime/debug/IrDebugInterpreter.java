package minic.runtime.debug;

import minic.compiler.ir.instruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.IrBinaryOperator;
import minic.compiler.ir.instruction.IrAddressOfLocalInstruction;
import minic.compiler.ir.instruction.IrBranchInstruction;
import minic.compiler.ir.instruction.IrCastInstruction;
import minic.compiler.ir.instruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.IrCheckNonZeroInstruction;
import minic.compiler.ir.instruction.IrCallInstruction;
import minic.compiler.ir.instruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.IrElementAddressInstruction;
import minic.compiler.ir.instruction.IrFieldAddressInstruction;
import minic.compiler.ir.instruction.IrIndirectCallInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.IrJumpInstruction;
import minic.compiler.ir.instruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.IrLoadPointerInstruction;
import minic.compiler.ir.instruction.IrMemCopyInstruction;
import minic.compiler.ir.instruction.IrMoveInstruction;
import minic.compiler.ir.instruction.IrReturnInstruction;
import minic.compiler.ir.instruction.IrSelectInstruction;
import minic.compiler.ir.instruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.IrStorePointerInstruction;
import minic.compiler.ir.instruction.IrUnaryInstruction;
import minic.compiler.ir.instruction.IrUnaryOperator;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.model.IrModule;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrConstant;
import minic.compiler.ir.value.IrFloatConstant;
import minic.compiler.ir.value.IrFunctionAddress;
import minic.compiler.ir.value.IrParameterRef;
import minic.compiler.ir.value.IrStringLiteral;
import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.runtime.debug.dataflow.DataFlowEvent;
import minic.runtime.debug.dataflow.DataFlowEventType;
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
            DebugValue value = DebugValue.uninitialized(typeName(declare.local().type()));
            state.currentFrame().locals.put(declare.local().name(), value);
            queueDataFlowEvent(
                    state,
                    instruction,
                    DataFlowEventType.DECLARE_LOCAL,
                    declare.local().sourceName(),
                    null,
                    value,
                    "",
                    ""
            );
            recordStep(state, block, instructionIndex, instruction, "DECLARE_LOCAL", declare.local().sourceName());
            return;
        }
        if (instruction instanceof IrAddressOfLocalInstruction addressOfLocal) {
            DebugVirtualAddress address = localAddress(state, state.currentFrame(), addressOfLocal.local());
            state.currentFrame().temps.put(
                    addressOfLocal.result().name(),
                    DebugValue.pointerValue(typeName(addressOfLocal.result().type()), address)
            );
            queueDataFlowEvent(
                    state,
                    instruction,
                    DataFlowEventType.ADDRESS_OF_LOCAL,
                    "&" + addressOfLocal.local().sourceName(),
                    null,
                    DebugValue.pointerValue("pointer", address),
                    address.display(),
                    address.display()
            );
            recordStep(state, block, instructionIndex, instruction, "ADDRESS_OF_LOCAL", addressOfLocal.local().sourceName());
            return;
        }
        if (instruction instanceof IrFieldAddressInstruction fieldAddress) {
            DebugVirtualAddress baseAddress = pointerAddress(resolveValue(state, fieldAddress.baseAddress()));
            DebugVirtualAddress address = new DebugVirtualAddress(baseAddress.segment(), baseAddress.offset() + fieldAddress.offset());
            AddressField fieldReference = new AddressField(baseAddress, fieldAddress.fieldName());
            state.currentFrame().temps.put(
                    fieldAddress.result().name(),
                    DebugValue.pointerValue(typeName(fieldAddress.result().type()), address)
            );
            state.currentFrame().tempAddressFields.put(fieldAddress.result().name(), fieldReference);
            state.addressFields.put(addressKey(address), fieldReference);
            queueDataFlowEvent(
                    state,
                    instruction,
                    DataFlowEventType.FIELD_ADDRESS,
                    fieldPath(state, fieldReference),
                    null,
                    DebugValue.pointerValue("pointer", address),
                    address.display(),
                    address.display()
            );
            recordStep(state, block, instructionIndex, instruction, "FIELD_ADDRESS", fieldAddress.fieldName());
            return;
        }
        if (instruction instanceof IrElementAddressInstruction elementAddress) {
            DebugVirtualAddress baseAddress = pointerAddress(resolveValue(state, elementAddress.baseAddress()));
            long indexValue = numericValue(resolveValue(state, elementAddress.index()));
            DebugVirtualAddress address = new DebugVirtualAddress(
                    baseAddress.segment(),
                    baseAddress.offset() + indexValue * elementAddress.elementSizeBytes()
            );
            state.currentFrame().temps.put(
                    elementAddress.result().name(),
                    DebugValue.pointerValue(typeName(elementAddress.result().type()), address)
            );
            if (state.addressLocals.containsKey(addressKey(baseAddress)) || state.addressElements.containsKey(addressKey(baseAddress))) {
                AddressElement elementReference = new AddressElement(baseAddress, address, indexValue);
                state.currentFrame().tempAddressElements.put(elementAddress.result().name(), elementReference);
                state.addressElements.put(addressKey(address), elementReference);
            }
            queueDataFlowEvent(
                    state,
                    instruction,
                    DataFlowEventType.ELEMENT_ADDRESS,
                    addressPath(state, elementAddress.result(), address),
                    null,
                    DebugValue.pointerValue("pointer", address),
                    address.display(),
                    address.display()
            );
            recordStep(state, block, instructionIndex, instruction, "ELEMENT_ADDRESS", Long.toString(indexValue));
            return;
        }
        if (instruction instanceof IrStoreLocalInstruction store) {
            DebugValue oldValue = localValue(state, store.local());
            DebugValue newValue = resolveValue(state, store.value());
            DebugVirtualAddress address = localAddress(state, state.currentFrame(), store.local());
            String lvaluePath = localSourceName(state.currentFrame(), store.local().name());
            DataFlowEventType dataFlowType = isPointerRetarget(store.local(), newValue)
                    ? DataFlowEventType.POINTER_RETARGET
                    : DataFlowEventType.WRITE_LOCAL;
            queueDataFlowEvent(
                    state,
                    instruction,
                    dataFlowType,
                    lvaluePath,
                    oldValue,
                    newValue,
                    address.display(),
                    pointerTarget(newValue)
            );
            state.currentFrame().locals.put(store.local().name(), newValue);
            recordStep(state, block, instructionIndex, instruction, "STORE_LOCAL", store.local().sourceName());
            return;
        }
        if (instruction instanceof IrLoadPointerInstruction loadPointer) {
            DebugVirtualAddress address = pointerAddress(resolveValue(state, loadPointer.address()));
            DebugValue value = pointerValueAt(state, loadPointer.address(), address);
            state.currentFrame().temps.put(loadPointer.result().name(), value);
            queueDataFlowEvent(
                    state,
                    instruction,
                    DataFlowEventType.LOAD_POINTER,
                    addressPath(state, loadPointer.address(), address),
                    null,
                    value,
                    address.display(),
                    pointerTarget(value)
            );
            recordStep(state, block, instructionIndex, instruction, "LOAD_POINTER", address.display());
            return;
        }
        if (instruction instanceof IrStorePointerInstruction storePointer) {
            DebugVirtualAddress address = pointerAddress(resolveValue(state, storePointer.address()));
            DebugValue value = resolveValue(state, storePointer.value());
            DebugValue oldValue = pointerValueAt(state, storePointer.address(), address);
            DataFlowEventType dataFlowType = pointerStoreEventType(state, storePointer.address(), address);
            String lvaluePath = addressPath(state, storePointer.address(), address);
            queueDataFlowEvent(
                    state,
                    instruction,
                    dataFlowType,
                    lvaluePath,
                    oldValue,
                    value,
                    address.display(),
                    pointerTarget(value)
            );
            VisualFieldWrite visualFieldWrite = visualFieldWrite(state, storePointer.address(), address, value);
            writePointerValue(state, storePointer.address(), address, value);
            if (visualFieldWrite != null) {
                state.pendingVisualFieldWrites.add(visualFieldWrite);
            }
            recordStep(state, block, instructionIndex, instruction, "STORE_POINTER", address.display());
            return;
        }
        if (instruction instanceof IrMemCopyInstruction memCopy) {
            DebugVirtualAddress destAddr = pointerAddress(resolveValue(state, memCopy.destination()));
            DebugVirtualAddress srcAddr = pointerAddress(resolveValue(state, memCopy.source()));
            for (int offset = 0; offset < memCopy.sizeBytes(); offset += 4) {
                DebugVirtualAddress srcField = new DebugVirtualAddress(srcAddr.segment(), srcAddr.offset() + offset);
                DebugVirtualAddress destField = new DebugVirtualAddress(destAddr.segment(), destAddr.offset() + offset);
                DebugValue value = pointerValueAt(state, srcField);
                DebugValue oldValue = pointerValueAt(state, destField);
                queueDataFlowEvent(
                        state,
                        instruction,
                        DataFlowEventType.STORE_POINTER,
                        addressPath(state, destField),
                        oldValue,
                        value,
                        destField.display(),
                        pointerTarget(value)
                );
                writePointerValue(state, destField, value);
            }
            recordStep(state, block, instructionIndex, instruction, "MEM_COPY", destAddr.display());
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
        if (instruction instanceof IrIndirectCallInstruction indirectCall) {
            DebugValue calleeValue = resolveValue(state, indirectCall.calleeAddress());
            DebugVirtualAddress calleeAddr = pointerAddress(calleeValue);
            String calleeName = resolveFunctionName(state, calleeAddr);
            List<DebugValue> arguments = indirectCall.arguments().stream()
                    .map(argument -> resolveValue(state, argument))
                    .toList();
            java.util.Optional<IrFunction> callee = state.module.findFunction(calleeName);
            if (callee.isEmpty()) {
                if (state.module.externalFunctionNames().contains(calleeName)) {
                    DebugExternalFunctionStub stub = externalFunctions.find(calleeName).orElseThrow(() ->
                            new UnsupportedOperationException("debug external stub is not available: " + calleeName));
                    DebugExternalCallResult result = stub.invoke(
                            calleeName,
                            indirectCall.arguments(),
                            arguments,
                            new DebugExternalCallContext(state.module)
                    );
                    state.currentFrame().temps.put(indirectCall.result().name(), result.returnValue());
                    state.stdout.append(result.stdoutAppend());
                    recordStep(state, block, instructionIndex, instruction, "INDIRECT_CALL_EXTERNAL", calleeName);
                    return;
                }
                throw new UnsupportedOperationException("indirect call target function not found: " + calleeName);
            }
            recordStep(state, block, instructionIndex, instruction, "INDIRECT_CALL", calleeName);
            state.pushFrame(callee.orElseThrow(), arguments, indirectCall.result());
            return;
        }
        if (instruction instanceof IrSelectInstruction select) {
            DebugValue condition = resolveValue(state, select.condition());
            DebugValue result = numericValue(condition) != 0
                    ? resolveValue(state, select.thenValue())
                    : resolveValue(state, select.elseValue());
            state.currentFrame().temps.put(select.result().name(), result);
            recordStep(state, block, instructionIndex, instruction, "SELECT", condition.summary());
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
        if (value instanceof IrFunctionAddress functionAddress) {
            return DebugValue.pointerValue("fn *", functionAddress(functionAddress.functionName()));
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
            case NEGATE -> integerResult(unary.result().type(), -value);
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

    private DebugVirtualAddress localAddress(InterpreterState state, CallFrame frame, IrLocal local) {
        DebugVirtualAddress address = frame.localAddresses.get(local.name());
        if (address == null) {
            address = new DebugVirtualAddress("stack", Math.abs((frame.function.name() + ":" + local.name()).hashCode()));
            frame.localAddresses.put(local.name(), address);
        }
        state.addressLocals.put(addressKey(address), new AddressLocal(frame, local.name()));
        return address;
    }

    private DebugVirtualAddress pointerAddress(DebugValue value) {
        return value.pointerTargetOptional().orElseThrow(() ->
                new IllegalStateException("value is not a pointer: " + value.summary()));
    }

    private DebugValue pointerValueAt(InterpreterState state, DebugVirtualAddress address) {
        return pointerValueAt(state, null, address);
    }

    private DebugValue pointerValueAt(InterpreterState state, IrValue addressValue, DebugVirtualAddress address) {
        AddressField tempField = tempAddressField(state, addressValue);
        if (tempField != null) {
            return fieldValue(state, tempField);
        }
        AddressElement tempElement = tempAddressElement(state, addressValue);
        if (tempElement != null) {
            return elementValue(state, tempElement);
        }
        AddressField field = state.addressFields.get(addressKey(address));
        if (field != null) {
            return fieldValue(state, field);
        }
        AddressElement element = state.addressElements.get(addressKey(address));
        if (element != null) {
            return elementValue(state, element);
        }
        AddressLocal local = state.addressLocals.get(addressKey(address));
        if (local != null) {
            return local.frame().locals.getOrDefault(local.localName(), DebugValue.uninitialized("unknown"));
        }
        return state.memory.getOrDefault(addressKey(address), DebugValue.uninitialized("memory"));
    }

    private void writePointerValue(InterpreterState state, DebugVirtualAddress address, DebugValue value) {
        writePointerValue(state, null, address, value);
    }

    private void writePointerValue(InterpreterState state, IrValue addressValue, DebugVirtualAddress address, DebugValue value) {
        AddressField tempField = tempAddressField(state, addressValue);
        if (tempField != null) {
            writeFieldValue(state, tempField, value);
            return;
        }
        AddressElement tempElement = tempAddressElement(state, addressValue);
        if (tempElement != null) {
            writeElementValue(state, tempElement, value);
            return;
        }
        AddressField field = state.addressFields.get(addressKey(address));
        if (field != null) {
            writeFieldValue(state, field, value);
            return;
        }
        AddressElement element = state.addressElements.get(addressKey(address));
        if (element != null) {
            writeElementValue(state, element, value);
            return;
        }
        AddressLocal local = state.addressLocals.get(addressKey(address));
        if (local != null) {
            local.frame().locals.put(local.localName(), value);
            return;
        }
        state.memory.put(addressKey(address), value);
    }

    private AddressField tempAddressField(InterpreterState state, IrValue addressValue) {
        if (addressValue instanceof IrTemporary temporary && state.hasFrames()) {
            return state.currentFrame().tempAddressFields.get(temporary.name());
        }
        return null;
    }

    private AddressElement tempAddressElement(InterpreterState state, IrValue addressValue) {
        if (addressValue instanceof IrTemporary temporary && state.hasFrames()) {
            return state.currentFrame().tempAddressElements.get(temporary.name());
        }
        return null;
    }

    private DebugValue fieldValue(InterpreterState state, AddressField field) {
        AddressElement element = state.addressElements.get(addressKey(field.baseAddress()));
        if (element != null) {
            return fieldValue(elementValue(state, element), field.fieldName());
        }
        AddressLocal local = state.addressLocals.get(addressKey(field.baseAddress()));
        DebugValue structValue = local == null ? null : local.frame().locals.get(local.localName());
        if (structValue == null || structValue.kind() == DebugValueKind.UNINITIALIZED) {
            return DebugValue.uninitialized(field.fieldName());
        }
        return fieldValue(structValue, field.fieldName());
    }

    private void writeFieldValue(InterpreterState state, AddressField field, DebugValue value) {
        AddressElement element = state.addressElements.get(addressKey(field.baseAddress()));
        if (element != null) {
            DebugValue structValue = elementValue(state, element);
            writeElementValue(state, element, structWithField(structValue, field.fieldName(), value));
            return;
        }
        AddressLocal local = state.addressLocals.get(addressKey(field.baseAddress()));
        if (local == null) {
            state.memory.put(addressKey(new DebugVirtualAddress(field.baseAddress().segment(), field.baseAddress().offset())), value);
            return;
        }
        DebugValue structValue = local.frame().locals.get(local.localName());
        local.frame().locals.put(local.localName(), structWithField(structValue, field.fieldName(), value));
    }

    private DebugValue fieldValue(DebugValue structValue, String fieldName) {
        if (structValue.kind() == DebugValueKind.UNINITIALIZED) {
            return DebugValue.uninitialized(fieldName);
        }
        return structValue.fields().stream()
                .filter(valueField -> valueField.name().equals(fieldName))
                .map(DebugValueField::value)
                .findFirst()
                .orElse(DebugValue.uninitialized(fieldName));
    }

    private DebugValue structWithField(DebugValue structValue, String fieldName, DebugValue value) {
        java.util.LinkedHashMap<String, DebugValue> fields = new java.util.LinkedHashMap<>();
        if (structValue != null && structValue.kind() == DebugValueKind.STRUCT) {
            structValue.fields().forEach(valueField -> fields.put(valueField.name(), valueField.value()));
        }
        fields.put(fieldName, value);
        return DebugValue.structValue("struct", fields.entrySet().stream()
                .map(entry -> new DebugValueField(entry.getKey(), entry.getValue()))
                .toList());
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

    private void writeElementValue(InterpreterState state, AddressElement element, DebugValue value) {
        AddressLocal local = state.addressLocals.get(addressKey(element.baseAddress()));
        if (local == null) {
            state.memory.put(addressKey(element.elementAddress()), value);
            return;
        }
        DebugValue arrayValue = local.frame().locals.get(local.localName());
        java.util.LinkedHashMap<Long, DebugValue> elements = new java.util.LinkedHashMap<>();
        if (arrayValue != null && arrayValue.kind() == DebugValueKind.ARRAY) {
            arrayValue.elements().forEach(valueElement -> elements.put(valueElement.index(), valueElement.value()));
        }
        elements.put(element.index(), value);
        IrLocal localSlot = local.frame().localSlots.get(local.localName());
        int elementCount = localSlot == null ? elements.size() : localSlot.elementCount();
        java.util.ArrayList<DebugValueElement> debugElements = new java.util.ArrayList<>();
        for (long index = 0; index < elementCount; index++) {
            debugElements.add(new DebugValueElement(index, elements.getOrDefault(index, DebugValue.uninitialized("element"))));
        }
        String arrayTypeName = arrayValue != null && arrayValue.kind() == DebugValueKind.ARRAY
                ? arrayValue.typeName()
                : localSlot == null ? "array" : typeName(localSlot.type());
        local.frame().locals.put(local.localName(), DebugValue.arrayValue(arrayTypeName, debugElements));
    }

    private String addressKey(DebugVirtualAddress address) {
        return address.segment() + ":" + address.offset();
    }

    private VisualFieldWrite visualFieldWrite(
            InterpreterState state,
            IrValue addressValue,
            DebugVirtualAddress fieldAddress,
            DebugValue value
    ) {
        AddressField field = tempAddressField(state, addressValue);
        if (field == null) {
            field = state.addressFields.get(addressKey(fieldAddress));
        }
        if (field == null || !state.hasFrames()) {
            return null;
        }
        return new VisualFieldWrite(state.currentFrame().function.name(), field.baseAddress(), field.fieldName(), value);
    }

    private void queueDataFlowEvent(
            InterpreterState state,
            IrInstruction instruction,
            DataFlowEventType type,
            String lvaluePath,
            DebugValue oldValue,
            DebugValue newValue,
            String address,
            String pointerTarget
    ) {
        state.pendingDataFlowEvents.add(new PendingDataFlowEvent(
                instruction.range(),
                type,
                dataFlowExpression(instruction, lvaluePath, type),
                lvaluePath.isBlank() ? address : lvaluePath,
                valueSummary(oldValue),
                valueSummary(newValue),
                address,
                pointerTarget
        ));
    }

    private DataFlowEventType pointerStoreEventType(
            InterpreterState state,
            IrValue addressValue,
            DebugVirtualAddress address
    ) {
        if (tempAddressField(state, addressValue) != null || state.addressFields.containsKey(addressKey(address))) {
            return DataFlowEventType.FIELD_WRITE;
        }
        if (tempAddressElement(state, addressValue) != null || state.addressElements.containsKey(addressKey(address))) {
            return DataFlowEventType.ARRAY_ELEMENT_WRITE;
        }
        return DataFlowEventType.STORE_POINTER;
    }

    private String addressPath(InterpreterState state, IrValue addressValue, DebugVirtualAddress address) {
        AddressField tempField = tempAddressField(state, addressValue);
        if (tempField != null) {
            return fieldPath(state, tempField);
        }
        AddressElement tempElement = tempAddressElement(state, addressValue);
        if (tempElement != null) {
            return elementPath(state, tempElement);
        }
        return addressPath(state, address);
    }

    private String addressPath(InterpreterState state, DebugVirtualAddress address) {
        AddressField field = state.addressFields.get(addressKey(address));
        if (field != null) {
            return fieldPath(state, field);
        }
        AddressElement element = state.addressElements.get(addressKey(address));
        if (element != null) {
            return elementPath(state, element);
        }
        AddressLocal local = state.addressLocals.get(addressKey(address));
        if (local != null) {
            return localSourceName(local.frame(), local.localName());
        }
        return address.display();
    }

    private String fieldPath(InterpreterState state, AddressField field) {
        String basePath = fieldBasePath(state, field.baseAddress());
        if (basePath.isBlank()) {
            basePath = field.baseAddress().display();
        }
        return basePath + "." + field.fieldName();
    }

    private String elementPath(InterpreterState state, AddressElement element) {
        String basePath = elementBasePath(state, element.baseAddress());
        if (basePath.isBlank()) {
            basePath = element.baseAddress().display();
        }
        return basePath + "[" + element.index() + "]";
    }

    private String fieldBasePath(InterpreterState state, DebugVirtualAddress baseAddress) {
        AddressElement element = state.addressElements.get(addressKey(baseAddress));
        if (element != null) {
            return elementPath(state, element);
        }
        AddressLocal local = state.addressLocals.get(addressKey(baseAddress));
        if (local != null) {
            return localSourceName(local.frame(), local.localName());
        }
        AddressField parentField = state.addressFields.get(addressKey(baseAddress));
        if (parentField != null && !parentField.baseAddress().equals(baseAddress)) {
            return fieldPath(state, parentField);
        }
        return baseAddress.display();
    }

    private String elementBasePath(InterpreterState state, DebugVirtualAddress baseAddress) {
        AddressLocal local = state.addressLocals.get(addressKey(baseAddress));
        if (local != null) {
            return localSourceName(local.frame(), local.localName());
        }
        AddressElement parentElement = state.addressElements.get(addressKey(baseAddress));
        if (parentElement != null && !parentElement.elementAddress().equals(baseAddress)) {
            return elementPath(state, parentElement);
        }
        AddressField field = state.addressFields.get(addressKey(baseAddress));
        if (field != null && !field.baseAddress().equals(baseAddress)) {
            return fieldPath(state, field);
        }
        return baseAddress.display();
    }

    private boolean isPointerRetarget(IrLocal local, DebugValue value) {
        String typeName = typeName(local.type());
        return typeName.contains("*") || value.kind() == DebugValueKind.POINTER || value.kind() == DebugValueKind.NULL;
    }

    private String localSourceName(CallFrame frame, String localName) {
        return frame.localNames.getOrDefault(localName, localName);
    }

    private String pointerTarget(DebugValue value) {
        if (value.kind() == DebugValueKind.NULL) {
            return "null";
        }
        return value.pointerTargetOptional()
                .map(DebugVirtualAddress::display)
                .orElse("");
    }

    private String valueSummary(DebugValue value) {
        return value == null ? "<uninitialized>" : value.summary();
    }

    private String dataFlowExpression(IrInstruction instruction, String lvaluePath, DataFlowEventType type) {
        String sourceText = instruction.range() == null ? "" : instruction.range().text().strip();
        if (sourceText.isBlank()) {
            return lvaluePath + " " + type.name().toLowerCase(java.util.Locale.ROOT);
        }
        String compactSource = sourceText.replaceAll("\\s+", " ");
        if (!mentionsPath(compactSource, lvaluePath)) {
            return compactSource + " -> " + lvaluePath;
        }
        return compactSource;
    }

    private boolean mentionsPath(String sourceText, String lvaluePath) {
        if (lvaluePath.isBlank()) {
            return true;
        }
        if (sourceText.contains(lvaluePath)) {
            return true;
        }
        String leaf = lvaluePath;
        int dotIndex = Math.max(leaf.lastIndexOf('.'), leaf.lastIndexOf('>'));
        if (dotIndex >= 0 && dotIndex + 1 < leaf.length()) {
            leaf = leaf.substring(dotIndex + 1);
        }
        int bracketIndex = leaf.indexOf('[');
        if (bracketIndex > 0) {
            leaf = leaf.substring(0, bracketIndex);
        }
        return !leaf.isBlank() && sourceText.contains(leaf);
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
        recordDataFlowEvents(state, snapshot.snapshotId(), cursor.instructionId());
        recordVisualEvents(state, snapshot.snapshotId());
        recordVisualFieldWriteEvents(state, snapshot.snapshotId());
    }

    private void recordDataFlowEvents(InterpreterState state, long snapshotId, String instructionId) {
        if (state.pendingDataFlowEvents.isEmpty()) {
            return;
        }
        List<PendingDataFlowEvent> events = List.copyOf(state.pendingDataFlowEvents);
        state.pendingDataFlowEvents.clear();
        for (PendingDataFlowEvent event : events) {
            state.session.appendDataFlowEvent(new DataFlowEvent(
                    snapshotId,
                    instructionId,
                    event.sourceRange(),
                    event.type(),
                    event.cExpression(),
                    event.lvaluePath(),
                    event.oldValue(),
                    event.newValue(),
                    event.address(),
                    event.pointerTarget()
            ));
        }
    }

    private void recordVisualEvents(InterpreterState state, long snapshotId) {
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

    private void recordVisualFieldWriteEvents(InterpreterState state, long snapshotId) {
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
                frame.localAddresses.getOrDefault(
                        entry.getKey(),
                        new DebugVirtualAddress("stack", Math.abs((frame.function.name() + ":" + entry.getKey()).hashCode()))
                ),
                entry.getValue().typeName(),
                entry.getValue()
        );
    }

    private String eventTitle(String eventType) {
        return switch (eventType) {
            case "DECLARE_LOCAL" -> "声明局部变量";
            case "ADDRESS_OF_LOCAL" -> "取得局部变量地址";
            case "FIELD_ADDRESS" -> "计算结构体字段地址";
            case "ELEMENT_ADDRESS" -> "计算元素地址";
            case "STORE_LOCAL" -> "写入局部变量";
            case "LOAD_LOCAL" -> "读取局部变量";
            case "LOAD_POINTER" -> "通过指针读取";
            case "STORE_POINTER" -> "通过指针写入";
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

    private DebugVirtualAddress functionAddress(String functionName) {
        return new DebugVirtualAddress("code", Math.abs(functionName.hashCode()));
    }

    private String resolveFunctionName(InterpreterState state, DebugVirtualAddress address) {
        for (IrFunction function : state.module.functions()) {
            if (functionAddress(function.name()).equals(address)) {
                return function.name();
            }
        }
        for (String externalName : state.module.externalFunctionNames()) {
            if (functionAddress(externalName).equals(address)) {
                return externalName;
            }
        }
        throw new UnsupportedOperationException("cannot resolve function at address: " + address.display());
    }

    private String typeName(IrType type) {
        return type.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static final class InterpreterState {
        private final IrModule module;
        private final IrFunction function;
        private final DebugSession session;
        private final List<VisualRuntimeGraph> visualRuntimeGraphs;
        private final Map<String, AddressLocal> addressLocals = new LinkedHashMap<>();
        private final Map<String, AddressField> addressFields = new LinkedHashMap<>();
        private final Map<String, AddressElement> addressElements = new LinkedHashMap<>();
        private final Map<String, DebugValue> memory = new LinkedHashMap<>();
        private final java.util.ArrayList<PendingDataFlowEvent> pendingDataFlowEvents = new java.util.ArrayList<>();
        private final java.util.ArrayList<VisualFieldWrite> pendingVisualFieldWrites = new java.util.ArrayList<>();
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

        private VisualNodeMapping nodeMapping(String graphName, List<VisualAnnotation> visualMapAnnotations) {
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
        private final Map<String, DebugVirtualAddress> localAddresses = new LinkedHashMap<>();
        private final Map<String, IrLocal> localSlots = new LinkedHashMap<>();
        private final Map<String, AddressField> tempAddressFields = new LinkedHashMap<>();
        private final Map<String, AddressElement> tempAddressElements = new LinkedHashMap<>();
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
                        localSlots.put(declare.local().name(), declare.local());
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
            VisualNodeMapping nodeMapping,
            List<VisualMetaMapping> metaMappings,
            List<VisualEdgeMapping> edgeMappings
    ) {
        private String nodeLabelExpression() {
            return nodeMapping == null ? "" : nodeMapping.labelExpression();
        }
    }

    private record VisualNodeMapping(
            String idExpression,
            String labelExpression
    ) {
        private java.util.Optional<String> labelField() {
            return fieldOf(idExpression, labelExpression);
        }
    }

    private record VisualMetaMapping(
            String key,
            String nodeExpression,
            String valueExpression
    ) {
        private boolean matchesField(String ownerExpression, String fieldName) {
            return nodeExpression.equals(ownerExpression)
                    && fieldOf(ownerExpression, valueExpression).filter(fieldName::equals).isPresent();
        }
    }

    private record VisualEdgeMapping(
            String key,
            String fromExpression,
            String toExpression
    ) {
        private boolean matchesField(String ownerExpression, String fieldName) {
            return fromExpression.equals(ownerExpression)
                    && fieldOf(ownerExpression, toExpression).filter(fieldName::equals).isPresent();
        }
    }

    private record VisualFieldWrite(
            String functionName,
            DebugVirtualAddress ownerAddress,
            String fieldName,
            DebugValue value
    ) {
    }

    private record PendingDataFlowEvent(
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

    private static java.util.Optional<String> fieldOf(String ownerExpression, String expression) {
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

    private record AddressLocal(
            CallFrame frame,
            String localName
    ) {
    }

    private record AddressField(
            DebugVirtualAddress baseAddress,
            String fieldName
    ) {
    }

    private record AddressElement(
            DebugVirtualAddress baseAddress,
            DebugVirtualAddress elementAddress,
            long index
    ) {
    }
}
