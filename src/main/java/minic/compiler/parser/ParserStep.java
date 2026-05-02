package minic.compiler.parser;

import java.util.Objects;
import java.util.Optional;

/**
 * Parser 正向推进一步的产物。
 *
 * @param node 本步完成的 AST 节点；没有完成节点时为 {@code null}
 */
public record ParserStep(Object node) {
    /**
     * 创建 parser 步骤产物。
     *
     * @param node 本步完成的 AST 节点；没有完成节点时为 {@code null}
     */
    public ParserStep {
    }

    /**
     * 创建完成节点步骤。
     *
     * @param node AST 节点
     * @return parser 步骤产物
     */
    public static ParserStep node(Object node) {
        return new ParserStep(Objects.requireNonNull(node, "node"));
    }

    /**
     * 创建没有完成节点但游标已推进的步骤。
     *
     * @return parser 步骤产物
     */
    public static ParserStep noNode() {
        return new ParserStep(null);
    }

    /**
     * 返回完成的 AST 节点。
     *
     * @return AST 节点 Optional
     */
    public Optional<Object> nodeOptional() {
        return Optional.ofNullable(node);
    }
}
