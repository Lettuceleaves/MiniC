package minic.compiler.ast.stmt;

import minic.source.SourceRange;

/**
 * 语句 AST 节点基接口。
 */
public interface Statement {
    /**
     * 返回语句覆盖的源码范围。
     *
     * @return 语句源码范围
     */
    SourceRange range();
}
