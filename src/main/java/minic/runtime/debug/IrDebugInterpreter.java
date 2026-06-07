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
    private final IrDebugProcessSpaceBuilder processSpaceBuilder = new IrDebugProcessSpaceBuilder();
    private final IrDebugVisualEventRecorder visualEventRecorder = new IrDebugVisualEventRecorder();

    public IrDebugInterpreter() {
        this(DebugExternalFunctionRegistry.defaults());
    }

    public IrDebugInterpreter(DebugExternalFunctionRegistry externalFunctions) {
        this.externalFunctions = Objects.requireNonNull(externalFunctions, "externalFunctions");
    }

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
            boolean completedProgram = returnTarget == null || state.frames.size() <= 1;
            if (completedProgram) {
                recordStep(state, block, instructionIndex, instruction, "RETURN", state.returnValue.summary(), DebugStopReason.COMPLETED, false);
                state.popFrame();
                state.completed = true;
                state.session.setState(DebugExecutionState.COMPLETED);
            } else {
                recordStep(state, block, instructionIndex, instruction, "RETURN", state.returnValue.summary(), DebugStopReason.RETURN, false);
                state.popFrame();
                state.currentFrame().temps.put(returnTarget.name(), state.returnValue);
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
                IrDebugEventFormatter.dataFlowExpression(instruction, lvaluePath, type),
                lvaluePath.isBlank() ? address : lvaluePath,
                IrDebugEventFormatter.valueSummary(oldValue),
                IrDebugEventFormatter.valueSummary(newValue),
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
                    processSpaceBuilder.processSpace(state, cursor),
                breakpointHit,
                stopReason
        );
        state.session.appendSnapshot(snapshot);
        state.session.appendEvent(new DebugEvent(
                state.nextEventId++,
                snapshot.snapshotId(),
                eventType,
                IrDebugEventFormatter.title(eventType),
                description,
                instruction.range(),
                List.of()
        ));
        recordDataFlowEvents(state, snapshot.snapshotId(), cursor.instructionId());
        visualEventRecorder.recordVisualEvents(state, snapshot.snapshotId());
        visualEventRecorder.recordVisualFieldWriteEvents(state, snapshot.snapshotId());
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

}
