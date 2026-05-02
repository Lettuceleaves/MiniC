package minic.compiler.ir.lowering;

import minic.compiler.ast.decl.Program;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ir.model.IrModule;
import minic.compiler.semantic.SemanticResult;
import minic.compiler.semantic.StructLayout;
import minic.compiler.type.MiniType;

import java.util.Map;
import java.util.Objects;

/**
 * 将 MiniC AST 降到基础 IR。
 */
public final class IrLowerer {
    /**
     * 将程序 AST 降为 IR 模块。
     *
     * @param program 程序 AST
     * @return IR 模块
     */
    public IrModule lower(Program program) {
        return lower(program, Map.of(), Map.of());
    }

    /**
     * 将程序 AST 和语义结果降为 IR 模块。
     *
     * @param program 程序 AST
     * @param semanticResult 语义分析结果
     * @return IR 模块
     */
    public IrModule lower(Program program, SemanticResult semanticResult) {
        Objects.requireNonNull(semanticResult, "semanticResult");
        IrStepState state = new IrStepState(program, semanticResult);
        while (state.canNext()) {
            state.next();
        }
        return state.toIrModule();
    }

    private IrModule lower(
            Program program,
            Map<String, StructLayout> structLayouts,
            Map<Expression, MiniType> expressionTypes
    ) {
        Objects.requireNonNull(program, "program");
        IrStepState state = new IrStepState(program, structLayouts, expressionTypes);
        while (state.canNext()) {
            state.next();
        }
        return state.toIrModule();
    }
}
