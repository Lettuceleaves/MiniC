package minic.compiler.ir.optimize;

import minic.compiler.ir.IrResult;

/** Exposes constant wrapper bodies before bounded inlining chooses its candidates. */
public final class EarlySimplificationPass implements IrPass {
    @Override public String name() { return "early-simplification"; }
    @Override public IrResult apply(IrResult input) {
        IrResult result = new ConstantPropagationPass().apply(input);
        result = new NonZeroCheckEliminationPass().apply(result);
        return new DeadCodeEliminationPass().apply(result);
    }
}
