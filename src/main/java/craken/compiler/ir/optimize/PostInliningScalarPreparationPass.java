package craken.compiler.ir.optimize;

import craken.compiler.ir.IrResult;

/**
 * Prepares newly private scalar addresses exposed by call expansion. The original
 * nonescape and must-initialization rules are reused unchanged; scalar promotion
 * remains a separate following stage. The source/debug IR is never mutated.
 */
public final class PostInliningScalarPreparationPass implements IrPass {
    @Override public String name() { return "post-inlining-scalar-preparation"; }
    @Override public IrResult apply(IrResult source) {
        return new InitializedCheckEliminationPass().apply(new PrivateAddressNormalizationPass().apply(source));
    }
}
