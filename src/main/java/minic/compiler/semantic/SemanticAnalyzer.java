package minic.compiler.semantic;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Program;
import minic.compiler.ast.expr.Expression;
import minic.compiler.type.MiniType;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * MiniC 语义分析器入口。
 */
public final class SemanticAnalyzer {
    /**
     * 分析程序中的函数和变量引用。
     *
     * @param program 待分析程序 AST
     * @return 语义分析结果
     */
    public SemanticResult analyze(Program program) {
        SemanticStepState state = new SemanticStepState(Objects.requireNonNull(program, "program"));
        while (state.canNext()) {
            state.next();
        }
        return state.toSemanticResult();
    }
}
