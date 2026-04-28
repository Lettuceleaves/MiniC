package minic.compiler.ir;

import minic.source.SourceRange;

/**
 * IR 指令的基接口。
 */
public sealed interface IrInstruction permits IrBinaryInstruction, IrCallInstruction, IrCheckInitializedInstruction,
        IrCheckNonZeroInstruction, IrDeclareLocalInstruction, IrLoadLocalInstruction,
        IrReturnInstruction, IrStoreLocalInstruction {
    /**
     * 返回该指令对应的源码范围。
     *
     * @return 源码范围
     */
    SourceRange range();
}
