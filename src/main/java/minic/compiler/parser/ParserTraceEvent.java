package minic.compiler.parser;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 递归下降 parser 构建 AST 时产生的观察事件。
 *
 * @param kind 事件类型
 * @param label 展示标签
 * @param range 源码范围；没有时为 {@code null}
 * @param node 关联 AST 节点；非 build 事件为 {@code null}
 */
public record ParserTraceEvent(String kind, String label, SourceRange range, Object node) {
    /**
     * 创建 parser trace 事件。
     *
     * @param kind 事件类型
     * @param label 展示标签
     * @param range 源码范围
     * @param node 关联 AST 节点
     */
    public ParserTraceEvent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
    }
}
