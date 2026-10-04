package craken.compiler.ir.optimize;

import craken.compiler.ir.IrResult;

/** A deterministic IR transformation; implementations must not mutate their input. */
public interface IrPass {
    String name();
    IrResult apply(IrResult input);
}
