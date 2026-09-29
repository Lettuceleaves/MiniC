package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrJumpInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrTrapInstruction;
import minic.compiler.ir.instruction.ControlInstruction.TrapKind;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;

import java.util.ArrayList;
import java.util.List;

/** 调试代码区：持有独立的 IR 副本，并负责插装和源码行索引。 */
public final class DebugProgram {
    private final IrResult ir;
    private final List<List<Location>> lines;

    DebugProgram(SourceFile source, IrResult original) {
        int lineCount = 1 + (int) source.content().chars().filter(c -> c == '\n').count();
        ArrayList<List<Location>> lineIndex = new ArrayList<>();
        for (int line = 0; line <= lineCount; line++) lineIndex.add(new ArrayList<>());

        ArrayList<IrFunction> functions = new ArrayList<>();
        for (IrFunction function : original.functions()) {
            ArrayList<IrBlock> blocks = new ArrayList<>();
            for (IrBlock block : function.blocks()) {
                ArrayList<IrInstruction> instrumented = new ArrayList<>();
                int previousLine = -1;
                for (IrInstruction instruction : block.instructions()) {
                    if (instruction instanceof IrTrapInstruction) {
                        throw new IllegalArgumentException("IR has already been instrumented");
                    }
                    int line = instruction.range().startLine();
                    // 合成跳转沿用所属语句范围，本身不代表该源码行重新开始。
                    if (!(instruction instanceof IrJumpInstruction) && line != previousLine) {
                        instrumented.add(new IrTrapInstruction(TrapKind.LINE, instruction.range()));
                        previousLine = line;
                    }
                    if (instruction instanceof CallInstruction) {
                        instrumented.add(new IrTrapInstruction(TrapKind.CALL, instruction.range()));
                    }
                    instrumented.add(instruction);
                }
                // 空循环只有回跳指令，也必须能在每轮开始时暂停。
                if (instrumented.size() == 1 && instrumented.getFirst() instanceof IrJumpInstruction jump) {
                    instrumented.addFirst(new IrTrapInstruction(TrapKind.LINE, jump.range()));
                }

                blocks.add(new IrBlock(block.label(), instrumented));
                index(function.name(), block.label(), instrumented, lineIndex, lineCount);
            }
            functions.add(new IrFunction(
                    function.name(),
                    function.returnType(),
                    function.parameters(),
                    function.variadic(),
                    blocks,
                    function.range()
            ));
        }

        ir = new IrResult(
                functions,
                original.stringData(),
                original.externalFunctionNames(),
                original.structLayouts()
        );
        lines = lineIndex.stream().map(List::copyOf).toList();
    }

    private static void index(
            String function,
            String block,
            List<IrInstruction> instructions,
            List<List<Location>> lines,
            int lineCount
    ) {
        for (int index = 0; index < instructions.size(); index++) {
            IrInstruction instruction = instructions.get(index);
            int firstLine = instruction.range().startLine();
            int lastLine = instruction.range().endLine();
            if (instruction.range().endByte() == 0 && lastLine > firstLine) lastLine--;
            for (int line = firstLine; line <= Math.min(lastLine, lineCount); line++) {
                lines.get(line).add(new Location(function, block, index, instruction));
            }
        }
    }

    public IrResult ir() { return ir; }

    /** 第 0 项留空；lines().get(n) 对应 IDE 中第 n 行，包括插入的 trap。 */
    public List<List<Location>> lines() { return lines; }

    public List<Location> line(int n) {
        return n > 0 && n < lines.size() ? lines.get(n) : List.of();
    }

    public record Location(String function, String block, int index, IrInstruction instruction) {
        public String id() { return block + "#" + index; }
    }
}
