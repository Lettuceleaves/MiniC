package minic.uiapi;

import minic.compiler.codegen.windows.WindowsX64AssemblyLine;
import minic.compiler.codegen.windows.WindowsX64CodegenStepState;
import minic.compiler.ir.model.IrModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ASM Debug 视图构建器。
 */
public final class UiDebugAsmViewBuilder {
    /**
     * 构建 ASM Debug 视图。
     *
     * @param module IR 模块
     * @param state Debug 状态
     * @return ASM Debug 视图
     */
    public UiDebugAsmViewDto build(IrModule module, UiDebugStateDto state) {
        WindowsX64CodegenStepState codegen = new WindowsX64CodegenStepState(module);
        codegen.toAssemblySource();
        UiSourceSpanDto activeRange = state.currentSnapshot().sourceRange();
        ArrayList<UiAssemblyLineVisualDto> lines = new ArrayList<>();
        int lineNumber = 1;
        for (WindowsX64AssemblyLine line : codegen.work().assemblyLineData()) {
            UiSourceSpanDto range = line.sourceRange() == null ? null : UiSourceSpanDto.from(line.sourceRange());
            lines.add(new UiAssemblyLineVisualDto(
                    lineNumber++,
                    line.text(),
                    line.kind().name(),
                    "debug-asm",
                    line.subject(),
                    range,
                    overlaps(range, activeRange)
            ));
        }
        return new UiDebugAsmViewDto(
                lines,
                "ASM 视图展示生成汇编与当前 IR/源码的映射，不代表真实 CPU 正在执行的机器指令。",
                List.of(state.currentSnapshot().instructionId())
        );
    }

    private boolean overlaps(UiSourceSpanDto left, UiSourceSpanDto right) {
        return left != null
                && right != null
                && Objects.equals(left.sourceName(), right.sourceName())
                && left.startOffset() < right.endOffset()
                && right.startOffset() < left.endOffset();
    }
}
