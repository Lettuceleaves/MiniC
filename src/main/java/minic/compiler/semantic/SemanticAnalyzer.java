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
        Objects.requireNonNull(program, "program");
        SemanticReporter reporter = new SemanticReporter();
        Scope globalScope = new Scope();
        Map<Expression, MiniType> expressionTypes = new IdentityHashMap<>();
        FunctionRegistry functionRegistry = new FunctionRegistry(globalScope, reporter);
        functionRegistry.defineFunctions(program);
        functionRegistry.validateMain(program);

        StatementSemanticAnalyzer statementAnalyzer = new StatementSemanticAnalyzer(
                globalScope,
                functionRegistry,
                reporter,
                expressionTypes
        );
        for (FunctionDecl functionDecl : program.functions()) {
            if (functionDecl.hasBody()) {
                statementAnalyzer.analyzeFunction(functionDecl);
            }
        }
        return new SemanticResult(globalScope, expressionTypes, reporter.diagnostics());
    }
}
