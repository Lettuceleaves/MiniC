package minic.compiler.ast.decl;

import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * MiniC 程序根节点。
 *
 * @param functions 程序中的函数声明列表
 * @param range 程序覆盖的源码范围
 */
public record Program(List<FunctionDecl> functions, SourceRange range) {
    /**
     * 创建程序根节点，并防御性复制函数列表。
     *
     * @param functions 程序中的函数声明列表
     * @param range 程序覆盖的源码范围
     */
    public Program {
        Objects.requireNonNull(functions, "functions");
        Objects.requireNonNull(range, "range");
        functions = List.copyOf(functions);
    }
}
