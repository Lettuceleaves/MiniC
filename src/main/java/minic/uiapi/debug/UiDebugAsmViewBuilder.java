package minic.uiapi;

import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.source.SourceRange;
import minic.compiler.ir.IrResult;

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
     * @param irResult IR 结果
     * @param state Debug 状态
     * @return ASM Debug 视图
     */
    public UiDebugAsmViewDto build(SourceFile sourceFile, IrResult irResult, UiDebugStateDto state) {
        Assembler asm = new Assembler(irResult);
        asm.assemble();
        UiSourceSpanDto activeRange = state.currentSnapshot().sourceRange();
        ArrayList<UiAssemblyLineVisualDto> lines = new ArrayList<>();
        int lineNumber = 1;
        List<String> assemblyLines = asm.work().assemblyLines();
        for (int index = 0; index < assemblyLines.size(); index++) {
            SourceRange sourceRange = asm.work().sourceRangeAt(index).orElse(null);
            UiSourceSpanDto range = sourceRange == null ? null : UiSourceSpanDto.from(sourceFile, sourceRange);
            lines.add(new UiAssemblyLineVisualDto(
                    lineNumber++,
                    assemblyLines.get(index),
                    "ASM_LINE",
                    "debug-asm",
                    "",
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
