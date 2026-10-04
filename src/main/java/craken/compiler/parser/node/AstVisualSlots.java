package craken.compiler.parser.node;

import craken.visualization.api.ViewLocation;
import java.util.Objects;

/** The input/output locations of this AST object; empty slots are valid. */
public final class AstVisualSlots {
    private ViewLocation pre;
    private ViewLocation nxt;

    public ViewLocation pre() { return pre; }
    public ViewLocation nxt() { return nxt; }
    public void setPre(ViewLocation pre) { this.pre = pre; }
    public void setNxt(ViewLocation nxt) { this.nxt = nxt; }
    public void clear() { pre = null; nxt = null; }
    public PositionPair snapshot() { return new PositionPair(pre, nxt); }
    public void restore(PositionPair positions) {
        Objects.requireNonNull(positions, "positions");
        pre = positions.pre();
        nxt = positions.nxt();
    }

    public record PositionPair(ViewLocation pre, ViewLocation nxt) {}
}
