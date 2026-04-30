package minic.compiler.ast.decl;

import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * MiniC 程序根节点。
 *
 * @param structs 程序中的结构体声明列表
 * @param functions 程序中的函数声明列表
 * @param range 程序覆盖的源码范围
 */
public record Program(List<StructDecl> structs, List<FunctionDecl> functions, SourceRange range) {
    /**
     * 创建程序根节点，并防御性复制声明列表。
     *
     * @param structs 程序中的结构体声明列表
     * @param functions 程序中的函数声明列表
     * @param range 程序覆盖的源码范围
     */
    public Program {
        Objects.requireNonNull(structs, "structs");
        Objects.requireNonNull(functions, "functions");
        Objects.requireNonNull(range, "range");
        structs = List.copyOf(structs);
        functions = List.copyOf(functions);
    }

    /**
     * 创建只包含函数声明的程序根节点。
     *
     * @param functions 程序中的函数声明列表
     * @param range 程序覆盖的源码范围
     */
    public Program(List<FunctionDecl> functions, SourceRange range) {
        this(List.of(), functions, range);
    }
}
