package craken.debug;

import craken.compiler.SourceFile;
import craken.compiler.ir.IrResult;
import craken.compiler.CompilerApi;
import craken.compiler.ir.instruction.IrInstruction;
import craken.compiler.ir.instruction.CallInstruction.*;
import craken.compiler.ir.instruction.ComputeInstruction.*;
import craken.compiler.ir.instruction.ControlInstruction.*;
import craken.compiler.ir.instruction.MemoryInstruction.*;
import craken.compiler.ir.model.IrType;
import craken.compiler.ir.value.IrValue;
import craken.compiler.ir.value.IrValue.*;
import craken.debug.DebugRuntime.Frame;
import craken.debug.DebugRuntime.RuntimeState;
import craken.debug.DebugRuntime.Value;
import craken.SourceRange;
import craken.debug.visualization.RuntimeEventCollector;
import craken.debug.visualization.RuntimeEventBatch;

import java.util.ArrayList;
import java.util.List;

/** 启动时只编译到 IR；之后 step() 在自己的 runtime 中解释执行到下一个 trap。 */
public final class Debugger {
    private final DebugRuntime runtime;
    private final DebugSystemLibrary systemLibrary = new DebugSystemLibrary();
    private final List<Context> contexts = new ArrayList<>();
    private final int historyLimit;
    private int nextContextIndex;
    private Stop latestStop = new Stop(Status.READY, null, null, "", "", -1, "");
    private int contextIndex;

    public Debugger(SourceFile source) {
        this(source, "");
    }

    public Debugger(SourceFile source, String standardInput) {
        this(source, standardInput, DebugTimeSource.system());
    }

    /** Opt-in VM memory observation; the internal collector cannot call visualization code. */
    public Debugger(SourceFile source, String standardInput, RuntimeEventCollector events) {
        this(source, new CompilerApi(source).runToIr(), standardInput, DebugTimeSource.system(),
                DebugRuntime.DEFAULT_HEAP_CAPACITY, Integer.MAX_VALUE, events);
    }

    Debugger(SourceFile source, String standardInput, DebugTimeSource timeSource) {
        this(source, standardInput, timeSource, DebugRuntime.DEFAULT_HEAP_CAPACITY);
    }

    Debugger(SourceFile source, String standardInput, DebugTimeSource timeSource, int heapCapacity) {
        this(source, new CompilerApi(source).runToIr(), standardInput, timeSource, heapCapacity);
    }

    static Debugger fromIr(SourceFile source, IrResult ir, String standardInput) {
        return new Debugger(source, ir, standardInput, DebugTimeSource.system(), DebugRuntime.DEFAULT_HEAP_CAPACITY);
    }

    static Debugger fromIr(SourceFile source, IrResult ir, String standardInput, int historyLimit) {
        return new Debugger(source, ir, standardInput, DebugTimeSource.system(), DebugRuntime.DEFAULT_HEAP_CAPACITY, historyLimit);
    }

    static Debugger fromIr(SourceFile source, IrResult ir, String standardInput, int historyLimit,
                          RuntimeEventCollector events) {
        return new Debugger(source, ir, standardInput, DebugTimeSource.system(),
                DebugRuntime.DEFAULT_HEAP_CAPACITY, historyLimit, events);
    }

    private Debugger(SourceFile source, IrResult ir, String standardInput, DebugTimeSource timeSource,
                     int heapCapacity) {
        this(source, ir, standardInput, timeSource, heapCapacity, Integer.MAX_VALUE);
    }

    private Debugger(SourceFile source, IrResult ir, String standardInput, DebugTimeSource timeSource,
                     int heapCapacity, int historyLimit) {
        this(source, ir, standardInput, timeSource, heapCapacity, historyLimit, RuntimeEventCollector.disabled());
    }

    private Debugger(SourceFile source, IrResult ir, String standardInput, DebugTimeSource timeSource,
                     int heapCapacity, int historyLimit, RuntimeEventCollector events) {
        if (historyLimit < 1) throw new IllegalArgumentException("History limit must retain at least the current context");
        this.historyLimit = historyLimit;
        runtime = new DebugRuntime(new DebugProgram(source, ir), standardInput, timeSource, heapCapacity, events);
        runtime.push(runtime.code().ir().findFunction(runtime.code().ir().entryFunction())
                .orElseThrow(() -> new IllegalStateException("Missing entry function: " + runtime.code().ir().entryFunction())), List.of(), null);
        remember(latestStop);
    }

    /** 当前 trap 已消费；执行其后的 IR，停在下一条有效 trap，停下时尚未执行对应源码。 */
    public Context step() {
        if (contextIndex < contexts.size() - 1) {
            return contexts.get(++contextIndex);
        }
        if (finished()) return contexts.get(contextIndex);
        return remember(advance());
    }

    private Stop advance() {
        SourceRange range = null;
        String function = "", block = "";
        int index = -1;
        try {
            while (!runtime.stack.isEmpty()) {
                Frame frame = runtime.stack.getLast();
                var instructions = frame.function.blocks().get(frame.block).instructions();
                function = runtime.code().ir().displayName(frame.function.name());
                block = frame.function.blocks().get(frame.block).label();
                index = frame.pc;
                if (index >= instructions.size()) throw new IllegalStateException("Block has no terminator: " + block);
                IrInstruction instruction = instructions.get(frame.pc++);
                range = instruction.range();
                if (instruction instanceof IrTrapInstruction trap) {
                    if (trap.kind() == TrapKind.LINE) {
                        if (frame.lastLine == range.startLine()) continue;
                        frame.lastLine = range.startLine();
                    }
                    return new Stop(Status.PAUSED, range, trap.kind(), function, block, index, "");
                }
                execute(frame, instruction);
            }
            return new Stop(Status.COMPLETED, range, null, function, block, index, "");
        } catch (RuntimeException error) {
            String message = error.getMessage() == null
                    ? error.getClass().getSimpleName()
                    : error.getMessage();
            runtime.fail(message);
            return new Stop(Status.FAILED, range, null, function, block, index, message);
        }
    }

    /** A bounded interpreter run with a final snapshot, without recording intermediate history. */
    public record Execution(Context context, boolean stepLimitReached, boolean outputLimitReached) { }

    Execution execute(int maximumStops, int maximumOutputBytes) {
        if (maximumStops < 1 || maximumOutputBytes < 0)
            throw new IllegalArgumentException("Positive stop budget and nonnegative output budget required");
        int stops = 0;
        while (!finished() && stops < maximumStops && !runtime.outputExceeds(maximumOutputBytes)) {
            latestStop = advance();
            stops++;
        }
        nextContextIndex += Math.max(0, stops - 1);
        return new Execution(remember(latestStop), !finished() && stops == maximumStops,
                runtime.outputExceeds(maximumOutputBytes));
    }

    /** 在已生成的上下文历史中向后移动一步；到达初始上下文后保持不动。 */
    public Context stepBack() {
        if (contextIndex > 0) contextIndex--;
        return contexts.get(contextIndex);
    }

    Context initialContext() { return contexts.getFirst(); }

    boolean canStep() {
        return contextIndex < contexts.size() - 1 || !finished();
    }

    boolean canStepBack() { return contextIndex > 0; }

    private boolean finished() {
        return latestStop.status == Status.COMPLETED || latestStop.status == Status.FAILED;
    }

    private Context remember(Stop next) {
        latestStop = next;
        int index = nextContextIndex++;
        RuntimeState state = runtime.snapshot();
        Context context = new Context(index, next, runtime.code(), state, runtime.takeEvents(index));
        if (contexts.size() == historyLimit) contexts.removeFirst();
        contexts.add(context);
        contextIndex = contexts.size() - 1;
        return context;
    }

    private void execute(Frame frame, IrInstruction instruction) {
        switch (instruction) {
            case IrDeclareLocalInstruction i -> runtime.declareLocal(frame, i.local());
            case IrAddressOfLocalInstruction i -> put(frame, i.result(), Value.of(IrType.POINTER, runtime.local(frame, i.local())));
            case IrLoadLocalInstruction i -> put(frame, i.result(), runtime.read(runtime.local(frame, i.local()), i.result().type()));
            case IrStoreLocalInstruction i -> runtime.write(runtime.local(frame, i.local()), value(frame, i.value()).cast(i.local().type()));
            case IrCheckInitializedInstruction i -> runtime.read(runtime.local(frame, i.local()), i.local().type());
            case IrLoadPointerInstruction i -> put(frame, i.result(), runtime.read(value(frame, i.address()).integer(), i.result().type()));
            case IrStorePointerInstruction i -> runtime.write(value(frame, i.address()).integer(), value(frame, i.value()));
            case IrFieldAddressInstruction i -> put(frame, i.result(), Value.of(IrType.POINTER,
                    Math.addExact(value(frame, i.baseAddress()).integer(), i.offset())));
            case IrElementAddressInstruction i -> put(frame, i.result(), Value.of(IrType.POINTER,
                    Math.addExact(value(frame, i.baseAddress()).integer(), Math.multiplyExact(value(frame, i.index()).integer(), i.elementSizeBytes()))));
            case IrMemCopyInstruction i -> runtime.copy(value(frame, i.destination()).integer(), value(frame, i.source()).integer(), i.sizeBytes());
            case IrMoveInstruction i -> put(frame, i.result(), value(frame, i.value()));
            case IrCastInstruction i -> put(frame, i.result(), value(frame, i.value()));
            case IrSelectInstruction i -> put(frame, i.result(), value(frame, value(frame, i.condition()).truth() ? i.thenValue() : i.elseValue()));
            case IrBinaryInstruction i -> put(frame, i.result(), binary(i.operator(), value(frame, i.left()), value(frame, i.right()), i.result().type()));
            case IrUnaryInstruction i -> {
                Value operand = value(frame, i.operand());
                Value result = switch (i.operator()) {
                    case LOGICAL_NOT -> Value.of(i.result().type(), operand.truth() ? 0 : 1);
                    case BITWISE_NOT -> Value.of(i.result().type(), ~operand.integer());
                    case NEGATE -> operand.type().isFloatingScalar()
                            ? Value.of(i.result().type(), -operand.real()) : Value.of(i.result().type(), -operand.integer());
                };
                put(frame, i.result(), result);
            }
            case IrBranchInstruction i -> frame.jump(value(frame, i.condition()).truth() ? i.thenLabel() : i.elseLabel());
            case IrJumpInstruction i -> frame.jump(i.targetLabel());
            case IrReturnInstruction i -> runtime.pop(i.value() == null ? null : value(frame, i.value()));
            case IrCheckNonZeroInstruction i -> {
                if (!value(frame, i.value()).truth()) throw new IllegalStateException("Division by zero");
            }
            case IrCallInstruction i -> call(frame, i.calleeName(), i.arguments(), i.result());
            case IrIndirectCallInstruction i -> call(frame, runtime.function(value(frame, i.calleeAddress()).integer()), i.arguments(), i.result());
            case IrTrapInstruction ignored -> throw new IllegalStateException("Trap must be consumed by step");
        }
    }

    private Value value(Frame frame, IrValue value) {
        Value resolved = switch (value) {
            case IrConstant c -> Value.of(c.type(), c.value());
            case IrFloatConstant c -> Value.of(c.type(), c.value());
            case IrTemporary t -> frame.temps.get(t.name());
            case IrParameterRef p -> runtime.parameter(frame, p.name());
            case IrParameterAddress p -> Value.of(IrType.POINTER, runtime.parameterAddress(frame, p.name()));
            case IrStringLiteral s -> Value.of(IrType.POINTER, runtime.symbol(s.label()));
            case IrFunctionAddress f -> Value.of(IrType.POINTER, runtime.symbol(f.functionName()));
            case craken.compiler.ir.value.IrValue.IrGlobalAddress g ->
                    Value.of(IrType.POINTER, runtime.symbol(g.globalName()));
        };
        if (resolved == null) throw new IllegalStateException("Undefined IR value: " + value);
        return resolved;
    }

    private void put(Frame frame, IrTemporary target, Value value) {
        frame.temps.put(target.name(), value.cast(target.type()));
    }

    private void call(Frame frame, String name, List<IrValue> operands, IrTemporary target) {
        List<Value> arguments = operands.stream().map(v -> value(frame, v)).toList();
        var function = runtime.code().ir().findFunction(name);
        if (function.isPresent()) {
            runtime.push(function.get(), arguments, target);
            return;
        }
        DebugLibraryCallResult result = systemLibrary.invoke(name, runtime, arguments)
                .orElseThrow(() -> new IllegalStateException("Unsupported external function: " + runtime.code().ir().displayName(name)));
        switch (result) {
            case DebugLibraryCallResult.Returned returned -> {
                if (target == null) {
                    return;
                }
                if (returned.value() == null) {
                    throw new IllegalStateException(
                            "Value-returning external function completed without a value: " + name
                    );
                }
                put(frame, target, returned.value());
            }
            case DebugLibraryCallResult.Terminated terminated ->
                    runtime.terminate(terminated.status(), terminated.reason());
            case DebugLibraryCallResult.Failed failed ->
                    throw new IllegalStateException(failed.code() + ": " + failed.message());
            case DebugLibraryCallResult.Suspended suspended ->
                    throw new IllegalStateException(
                            "External continuation is not implemented: " + suspended.continuationId()
                    );
            case DebugLibraryCallResult.Jumped jumped ->
                    throw new IllegalStateException("External jump is not implemented: " + jumped.target());
        }
    }

    private Value binary(IrBinaryOperator operator, Value left, Value right, IrType type) {
        long a = left.integer(), b = right.integer();
        boolean floating = left.type().isFloatingScalar() || right.type().isFloatingScalar();
        if (floating) {
            double x = left.real(), y = right.real();
            return Value.of(type, switch (operator) {
                case ADD -> x + y; case SUBTRACT -> x - y; case MULTIPLY -> x * y;
                case DIVIDE -> x / y; case MODULO -> x % y;
                case EQUAL -> x == y ? 1 : 0; case NOT_EQUAL -> x != y ? 1 : 0;
                case LESS_THAN -> x < y ? 1 : 0; case LESS_EQUAL -> x <= y ? 1 : 0;
                case GREATER_THAN -> x > y ? 1 : 0; case GREATER_EQUAL -> x >= y ? 1 : 0;
                case LOGICAL_AND -> left.truth() && right.truth() ? 1 : 0;
                case LOGICAL_OR -> left.truth() || right.truth() ? 1 : 0;
                default -> throw new IllegalStateException("Invalid floating operator: " + operator);
            });
        }
        IrType operandType = integerOperationType(left.type(), right.type());
        a = Value.of(operandType, a).integer();
        b = Value.of(operandType, b).integer();
        boolean unsigned = operandType.isUnsignedInteger();
        int comparison = unsigned ? Long.compareUnsigned(a, b) : Long.compare(a, b);
        return Value.of(type, switch (operator) {
            case ADD -> a + b; case SUBTRACT -> a - b; case MULTIPLY -> a * b;
            case DIVIDE -> unsigned ? Long.divideUnsigned(a, b) : a / b;
            case MODULO -> unsigned ? Long.remainderUnsigned(a, b) : a % b;
            case BITWISE_AND -> a & b; case BITWISE_OR -> a | b; case BITWISE_XOR -> a ^ b;
            case SHIFT_LEFT -> a << b; case SHIFT_RIGHT -> unsigned ? a >>> b : a >> b;
            case LOGICAL_AND -> a != 0 && b != 0 ? 1 : 0; case LOGICAL_OR -> a != 0 || b != 0 ? 1 : 0;
            case EQUAL -> a == b ? 1 : 0; case NOT_EQUAL -> a != b ? 1 : 0;
            case LESS_THAN -> comparison < 0 ? 1 : 0; case LESS_EQUAL -> comparison <= 0 ? 1 : 0;
            case GREATER_THAN -> comparison > 0 ? 1 : 0; case GREATER_EQUAL -> comparison >= 0 ? 1 : 0;
        });
    }

    /** Matches native IR operation selection; comparison result type is commonly INT. */
    private static IrType integerOperationType(IrType left, IrType right) {
        if (left == right) return left;
        if (left == IrType.POINTER || right == IrType.POINTER) return IrType.POINTER;
        if (left.sizeBytes() > right.sizeBytes()) return left;
        if (right.sizeBytes() > left.sizeBytes()) return right;
        return left.isUnsignedInteger() ? left : right;
    }

    public enum Status { READY, PAUSED, COMPLETED, FAILED }
    public record Stop(Status status, SourceRange range, TrapKind kind, String function,
                       String block, int instruction, String error) {}
    public record Context(int index, Stop stop, DebugProgram program, RuntimeState runtime, RuntimeEventBatch events) {
        public Context(int index, Stop stop, DebugProgram program, RuntimeState runtime) {
            this(index, stop, program, runtime, RuntimeEventBatch.unmonitored(index));
        }
    }
}
