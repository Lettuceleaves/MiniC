package minic.uiapi;

import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrModule;

import java.util.ArrayList;
import java.util.List;

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
                            "    " + instruction.getClass().getSimpleName(),
                            range,
                            instructionId.equals(snapshot.instructionId())
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
}
