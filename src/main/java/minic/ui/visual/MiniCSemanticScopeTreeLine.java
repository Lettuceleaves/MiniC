package minic.ui;

import java.util.List;
import java.util.Objects;

/**
 * Semantic 作用域树视图中的一行。
 *
 * @param label 作用域标签
 * @param depth 树深度
 * @param symbols 符号摘要
 * @param active 是否高亮
 * @param onActivePath 是否位于当前路径
 * @param arrowDirection 边方向
 */
public record MiniCSemanticScopeTreeLine(
        String label,
        int depth,
        List<String> symbols,
        boolean active,
        boolean onActivePath,
        String arrowDirection
) {
    public MiniCSemanticScopeTreeLine {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(symbols, "symbols");
        Objects.requireNonNull(arrowDirection, "arrowDirection");
        if (depth < 0) {
            throw new IllegalArgumentException("depth must not be negative");
        }
        symbols = List.copyOf(symbols);
    }
}
