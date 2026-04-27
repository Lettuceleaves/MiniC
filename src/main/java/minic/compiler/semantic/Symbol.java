package minic.compiler.semantic;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 语义分析阶段使用的符号。
 *
 * @param name 符号名称
 * @param kind 符号种类
 * @param declarationRange 声明所在源码范围
 * @param arity 函数形参数量；非函数符号为 {@code null}
 */
public record Symbol(String name, SymbolKind kind, SourceRange declarationRange, Integer arity) {
    /**
     * 创建符号。
     *
     * @param name 符号名称
     * @param kind 符号种类
     * @param declarationRange 声明所在源码范围
     * @param arity 函数形参数量；非函数符号为 {@code null}
     */
    public Symbol {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(declarationRange, "declarationRange");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    /**
     * 创建不携带函数参数数量的符号。
     *
     * @param name 符号名称
     * @param kind 符号种类
     * @param declarationRange 声明所在源码范围
     */
    public Symbol(String name, SymbolKind kind, SourceRange declarationRange) {
        this(name, kind, declarationRange, null);
    }
}
