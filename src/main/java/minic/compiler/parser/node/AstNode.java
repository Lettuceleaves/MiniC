package minic.compiler.parser.node;

import minic.SourceRange;

/**
 * 所有语法树节点的公共类型。
 */
public interface AstNode {
    SourceRange range();
}
