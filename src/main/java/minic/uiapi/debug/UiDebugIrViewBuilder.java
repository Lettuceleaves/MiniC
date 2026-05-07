package minic.uiapi;

import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.IrAddressOfLocalInstruction;
import minic.compiler.ir.instruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.IrBranchInstruction;
import minic.compiler.ir.instruction.IrCallInstruction;
import minic.compiler.ir.instruction.IrCastInstruction;
import minic.compiler.ir.instruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.IrCheckNonZeroInstruction;
import minic.compiler.ir.instruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.IrElementAddressInstruction;
import minic.compiler.ir.instruction.IrFieldAddressInstruction;
import minic.compiler.ir.instruction.IrIndirectCallInstruction;
import minic.compiler.ir.instruction.IrJumpInstruction;
import minic.compiler.ir.instruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.IrLoadPointerInstruction;
import minic.compiler.ir.instruction.IrMoveInstruction;
import minic.compiler.ir.instruction.IrReturnInstruction;
import minic.compiler.ir.instruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.IrStorePointerInstruction;
import minic.compiler.ir.instruction.IrSelectInstruction;
import minic.compiler.ir.instruction.IrUnaryInstruction;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.model.IrModule;
import minic.compiler.ir.value.IrConstant;
import minic.compiler.ir.value.IrFloatConstant;
import minic.compiler.ir.value.IrFunctionAddress;
import minic.compiler.ir.value.IrParameterRef;
import minic.compiler.ir.value.IrStringLiteral;
import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * IR Debug 视图构建器。
 */
public final class UiDebugIrViewBuilder {
    /**
     * 构建 IR Debug 视图。
     *
     * @param module IR 模块
     * @param state Debug 状态
     * @return IR Debug 视图
     */
    public UiDebugIrViewDto build(IrModule module, UiDebugStateDto state) {
        UiDebugSnapshotDto snapshot = state.currentSnapshot();
        UiSourceSpanDto activeRange = snapshot.sourceRange();
        ArrayList<UiIrLineVisualDto> lines = new ArrayList<>();
        for (IrFunction function : module.functions()) {
            lines.add(new UiIrLineVisualDto(lines.size() + 1, "function " + function.name(), UiSourceSpanDto.from(function.range()), false));
            for (IrBlock block : function.blocks()) {
                lines.add(new UiIrLineVisualDto(lines.size() + 1, "  block " + block.label(), null, false));
                for (int i = 0; i < block.instructions().size(); i++) {
                    IrInstruction instruction = block.instructions().get(i);
                    String instructionId = block.label() + "#" + i;
                    UiSourceSpanDto range = UiSourceSpanDto.from(instruction.range());
                    lines.add(new UiIrLineVisualDto(
                            lines.size() + 1,
                            "    " + formatInstruction(instruction),
                            range,
                            instructionId.equals(snapshot.instructionId()) || overlaps(range, activeRange)
                    ));
                }
            }
        }
        return new UiDebugIrViewDto(
                lines,
                snapshot.instructionId(),
                snapshot.sourceRange(),
                "当前 IR 指令来自 Debug 快照，可通过源码范围关联 AST 和 ASM 映射。",
                operands(snapshot)
        );
    }

    private List<UiDebugIrOperandDto> operands(UiDebugSnapshotDto snapshot) {
        ArrayList<UiDebugIrOperandDto> operands = new ArrayList<>();
        for (UiDebugFrameDto frame : snapshot.processSpace().stackFrames()) {
            frame.parameters().forEach(variable -> operands.add(operand(variable)));
            frame.locals().forEach(variable -> operands.add(operand(variable)));
        }
        return operands;
    }

    private UiDebugIrOperandDto operand(UiDebugVariableDto variable) {
        return new UiDebugIrOperandDto(
                variable.name(),
                variable.typeName(),
                variable.valueSummary(),
                variable.address()
        );
    }

    private boolean overlaps(UiSourceSpanDto left, UiSourceSpanDto right) {
        return left != null
                && right != null
                && Objects.equals(left.sourceName(), right.sourceName())
                && left.startOffset() < right.endOffset()
                && right.startOffset() < left.endOffset();
    }

    private String formatInstruction(IrInstruction instruction) {
        if (instruction instanceof IrDeclareLocalInstruction declareLocal) {
            return "declare " + formatLocal(declareLocal.local());
        }
        if (instruction instanceof IrCheckInitializedInstruction checkInitialized) {
            return "check_initialized " + formatLocal(checkInitialized.local());
        }
        if (instruction instanceof IrAddressOfLocalInstruction addressOfLocal) {
            return formatValue(addressOfLocal.result()) + " = address_of " + formatLocal(addressOfLocal.local());
        }
        if (instruction instanceof IrLoadLocalInstruction loadLocal) {
            return formatValue(loadLocal.result()) + " = load " + formatLocal(loadLocal.local());
        }
        if (instruction instanceof IrStoreLocalInstruction storeLocal) {
            return "store " + formatValue(storeLocal.value()) + ", " + formatLocal(storeLocal.local());
        }
        if (instruction instanceof IrLoadPointerInstruction loadPointer) {
            return formatValue(loadPointer.result()) + " = load_ptr " + formatValue(loadPointer.address());
        }
        if (instruction instanceof IrStorePointerInstruction storePointer) {
            return "store_ptr " + formatValue(storePointer.value()) + ", " + formatValue(storePointer.address());
        }
        if (instruction instanceof IrElementAddressInstruction elementAddress) {
            return formatValue(elementAddress.result())
                    + " = element_address "
                    + formatValue(elementAddress.baseAddress())
                    + ", "
                    + formatValue(elementAddress.index())
                    + ", size "
                    + elementAddress.elementSizeBytes();
        }
        if (instruction instanceof IrFieldAddressInstruction fieldAddress) {
            return formatValue(fieldAddress.result())
                    + " = field_address "
                    + formatValue(fieldAddress.baseAddress())
                    + "."
                    + fieldAddress.fieldName()
                    + ", offset "
                    + fieldAddress.offset();
        }
        if (instruction instanceof IrBinaryInstruction binary) {
            return formatValue(binary.result())
                    + " = "
                    + binary.operator().name().toLowerCase()
                    + " "
                    + formatValue(binary.left())
                    + ", "
                    + formatValue(binary.right());
        }
        if (instruction instanceof IrUnaryInstruction unary) {
            return formatValue(unary.result())
                    + " = "
                    + unary.operator().name().toLowerCase()
                    + " "
                    + formatValue(unary.operand());
        }
        if (instruction instanceof IrSelectInstruction select) {
            return formatValue(select.result())
                    + " = select "
                    + formatValue(select.condition())
                    + ", "
                    + formatValue(select.thenValue())
                    + ", "
                    + formatValue(select.elseValue());
        }
        if (instruction instanceof IrCheckNonZeroInstruction checkNonZero) {
            return "check_nonzero " + formatValue(checkNonZero.value());
        }
        if (instruction instanceof IrCastInstruction cast) {
            return formatValue(cast.result()) + " = cast " + formatValue(cast.value()) + " to " + cast.result().type();
        }
        if (instruction instanceof IrCallInstruction call) {
            return formatValue(call.result()) + " = call " + call.calleeName() + "(" + formatValues(call.arguments()) + ")";
        }
        if (instruction instanceof IrIndirectCallInstruction call) {
            return formatValue(call.result()) + " = call* " + formatValue(call.calleeAddress()) + "(" + formatValues(call.arguments()) + ")";
        }
        if (instruction instanceof IrBranchInstruction branch) {
            return "branch "
                    + formatValue(branch.condition())
                    + ", "
                    + branch.thenLabel()
                    + ", "
                    + branch.elseLabel();
        }
        if (instruction instanceof IrJumpInstruction jump) {
            return "jump " + jump.targetLabel();
        }
        if (instruction instanceof IrReturnInstruction ret) {
            return "return " + formatValue(ret.value());
        }
        if (instruction instanceof IrMoveInstruction move) {
            return formatValue(move.result()) + " = move " + formatValue(move.value());
        }
        return instruction.getClass().getSimpleName();
    }

    private String formatValues(List<IrValue> values) {
        return values.stream()
                .map(this::formatValue)
                .collect(Collectors.joining(", "));
    }

    private String formatValue(IrValue value) {
        if (value instanceof IrTemporary temporary) {
            return temporary.name();
        }
        if (value instanceof IrParameterRef parameter) {
            return parameter.name();
        }
        if (value instanceof IrConstant constant) {
            return Long.toString(constant.value());
        }
        if (value instanceof IrFloatConstant constant) {
            return Double.toString(constant.value());
        }
        if (value instanceof IrStringLiteral stringLiteral) {
            return stringLiteral.label();
        }
        if (value instanceof IrFunctionAddress functionAddress) {
            return "&" + functionAddress.functionName();
        }
        return value.getClass().getSimpleName();
    }

    private String formatLocal(IrLocal local) {
        if (local.name().equals(local.sourceName())) {
            return local.name();
        }
        return local.name() + "(" + local.sourceName() + ")";
    }
}
