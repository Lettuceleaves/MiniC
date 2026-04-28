package minic.compiler.ir.instruction;

import minic.source.SourceRange;

/**
 * IR 指令的基接口。
 */
public interface IrInstruction {
    /**
     * 返回该指令对应的源码范围。
     *
     * @return 源码范围
     */
    SourceRange range();
}
