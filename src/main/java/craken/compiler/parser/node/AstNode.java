package craken.compiler.parser.node;

import craken.SourceRange;

/**
 * 所有语法树节点的公共类型。
 */
public interface AstNode {
    SourceRange range();
}
