package minic.compiler.semantic;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 语义分析阶段使用的符号。
 *
 * @param name 符号名称
 * @param kind 符号种类
 * @param declarationRange 声明所在源码范围
 */
public record Symbol(String name, SymbolKind kind, SourceRange declarationRange) {
    /**
     * 创建符号。
     *
     * @param name 符号名称
     * @param kind 符号种类
     * @param declarationRange 声明所在源码范围
     */
    public Symbol {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(declarationRange, "declarationRange");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
