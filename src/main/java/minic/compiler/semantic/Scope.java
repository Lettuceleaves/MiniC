package minic.compiler.semantic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 符号作用域。
 *
 * <p>作用域保存当前层级直接声明的符号，并可通过 parent 向外层作用域查找。</p>
 */
public final class Scope {
    private final Scope parent;
    private final Map<String, Symbol> symbols = new LinkedHashMap<>();
    private final java.util.List<Scope> children = new java.util.ArrayList<>();

    /**
     * 创建根作用域。
     */
    public Scope() {
        this(null);
    }

    /**
     * 创建带父作用域的作用域。
     *
     * @param parent 父作用域；根作用域为 {@code null}
     */
    public Scope(Scope parent) {
        this.parent = parent;
        if (parent != null) {
            parent.children.add(this);
        }
    }

    /**
     * 返回父作用域。
     *
     * @return 父作用域；根作用域为空
     */
    public Optional<Scope> parent() {
        return Optional.ofNullable(parent);
    }

    /**
     * 在当前作用域定义符号。
     *
     * <p>只检查当前作用域是否重复定义；允许内层作用域遮蔽外层符号。</p>
     *
     * @param symbol 要定义的符号
     * @return 定义成功返回 {@code true}；当前作用域已有同名符号时返回 {@code false}
     */
    public boolean define(Symbol symbol) {
        Objects.requireNonNull(symbol, "symbol");
        if (symbols.containsKey(symbol.name())) {
            return false;
        }
        symbols.put(symbol.name(), symbol);
        return true;
    }

    /**
     * 从当前作用域向外查找符号。
     *
     * @param name 符号名称
     * @return 找到的最近符号；不存在时为空
     */
    public Optional<Symbol> resolve(String name) {
        Objects.requireNonNull(name, "name");
        Symbol symbol = symbols.get(name);
        if (symbol != null) {
            return Optional.of(symbol);
        }
        if (parent == null) {
            return Optional.empty();
        }
        return parent.resolve(name);
    }

    /**
     * 只在当前作用域查找符号。
     *
     * @param name 符号名称
     * @return 当前作用域中的符号；不存在时为空
     */
    public Optional<Symbol> resolveLocal(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(symbols.get(name));
    }

    /**
     * 返回当前作用域直接声明的符号快照。
     *
     * @return 当前作用域符号
     */
    public java.util.List<Symbol> symbols() {
        return java.util.List.copyOf(symbols.values());
    }

    /**
     * 返回直接子作用域快照。
     *
     * @return 子作用域
     */
    public java.util.List<Scope> children() {
        return java.util.List.copyOf(children);
    }
}
