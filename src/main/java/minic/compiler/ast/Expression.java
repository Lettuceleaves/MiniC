package minic.compiler.ast;

import minic.source.SourceRange;

/**
 * 表达式 AST 节点基接口。
 */
public sealed interface Expression permits IntegerLiteralExpr, NameExpr, AssignmentExpr, BinaryExpr, GroupingExpr, CallExpr {
    /**
     * 返回表达式覆盖的源码范围。
     *
     * @return 表达式源码范围
     */
    SourceRange range();
}
