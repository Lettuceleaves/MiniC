package craken.compiler.parser.node;

import craken.SourceRange;

import java.util.Objects;

/** Source operator-function-id metadata, distinct from an ordinary identifier or conversion. */
public record OperatorName(Kind kind, SourceRange range) {
    public OperatorName {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(range, "range");
    }

    public String spelling() { return "operator" + (kind.allocation() ? " " : "") + kind.symbol(); }

    /** Unary/binary and prefix/postfix operations share the same source name. */
    public enum Kind {
        ADD("+"), SUBTRACT("-"), MULTIPLY("*"), DIVIDE("/"), REMAINDER("%"),
        BIT_XOR("^"), BIT_AND("&"), BIT_OR("|"), BIT_NOT("~"), LOGICAL_NOT("!"),
        ASSIGN("="), LESS("<"), GREATER(">"), ADD_ASSIGN("+="), SUBTRACT_ASSIGN("-="),
        MULTIPLY_ASSIGN("*="), DIVIDE_ASSIGN("/="), REMAINDER_ASSIGN("%="),
        XOR_ASSIGN("^="), AND_ASSIGN("&="), OR_ASSIGN("|="), SHIFT_LEFT("<<"), SHIFT_RIGHT(">>"),
        SHIFT_LEFT_ASSIGN("<<="), SHIFT_RIGHT_ASSIGN(">>="), EQUAL("=="), NOT_EQUAL("!="),
        LESS_EQUAL("<="), GREATER_EQUAL(">="), LOGICAL_AND("&&"), LOGICAL_OR("||"),
        INCREMENT("++"), DECREMENT("--"), COMMA(","), POINTER_TO_MEMBER("->*"),
        MEMBER_ACCESS("->"), CALL("()"), SUBSCRIPT("[]"),
        NEW("new"), NEW_ARRAY("new[]"), DELETE("delete"), DELETE_ARRAY("delete[]");

        private final String symbol;
        Kind(String symbol) { this.symbol = symbol; }
        public String symbol() { return symbol; }
        public boolean allocation() { return this == NEW || this == NEW_ARRAY || this == DELETE || this == DELETE_ARRAY; }
    }
}
