package minic.compiler.library;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * MiniC 源程序可引用的外部库符号。
 *
 * <p>源符号名与原生库中的导出名是两个不同概念；名称改写由
 * {@link LibraryBinding} 描述。</p>
 */
public record LibrarySymbol(String sourceName, SymbolKind symbolKind) {
    private static final Pattern SOURCE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public LibrarySymbol {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(symbolKind, "symbolKind");
        if (!SOURCE_NAME.matcher(sourceName).matches()) {
            throw new IllegalArgumentException("invalid library source symbol: " + sourceName);
        }
    }

    /** 原生符号的基本种类。 */
    public enum SymbolKind {
        FUNCTION,
        DATA
    }
}
