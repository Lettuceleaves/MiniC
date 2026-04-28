package minic.compiler.codegen;

import minic.compiler.ir.model.IrModule;

/**
 * 汇编文本 emitter。
 */
public interface AssemblyEmitter {
    /**
     * 将 IR 模块输出为目标平台汇编文本。
     *
     * @param module IR 模块
     * @return 汇编文本模型
     */
    AssemblySource emit(IrModule module);
}
