package minic.compiler.ir.lowering;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Program;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrModule;

import java.util.ArrayList;
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
        Objects.requireNonNull(program, "program");
        ArrayList<IrFunction> functions = new ArrayList<>();
        for (FunctionDecl function : program.functions()) {
            if (function.hasBody()) {
                functions.add(new IrFunctionLowerer(function).lower());
            }
        }
        return new IrModule(functions);
    }
}
