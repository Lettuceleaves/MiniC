package minic.compiler.ir.optimize;

import minic.compiler.ir.IrResult;

/** A deterministic IR transformation; implementations must not mutate their input. */
public interface IrPass {
    String name();
    IrResult apply(IrResult input);
}
