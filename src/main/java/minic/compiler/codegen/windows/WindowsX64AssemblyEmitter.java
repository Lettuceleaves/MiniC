package minic.compiler.codegen.windows;

import minic.compiler.codegen.AssemblyEmitter;
import minic.compiler.codegen.AssemblySource;
import minic.compiler.ir.model.IrModule;

import java.util.Objects;

/**
 * Windows x86_64 MASM 风格汇编 emitter。
 */
public final class WindowsX64AssemblyEmitter implements AssemblyEmitter {
    @Override
    public AssemblySource emit(IrModule module) {
        Objects.requireNonNull(module, "module");
        return new WindowsX64CodegenStepState(module).toAssemblySource();
    }
}
