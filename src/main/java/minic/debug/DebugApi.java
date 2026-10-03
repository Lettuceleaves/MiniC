package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;

import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * 调试上下文的上层双向循环。
 *
 * <p>DebugApi 只通过 Debugger.step/stepBack 驱动执行器，并把两个方向封装成一个
 * ListIterator。向后浏览不会重放程序；再次向前时优先复用已有历史，走到历史末端后
 * 才继续解释新的 IR。</p>
 */
public final class DebugApi {
    private final Debugger debugger;
    private final ListIterator<Debugger.Context> iterator;
    private Debugger.Context current;

    public DebugApi(SourceFile source) {
        this(new Debugger(source));
    }

    public DebugApi(SourceFile source, String standardInput) {
        this(new Debugger(source, standardInput));
    }

    /** Reuses validated, uninstrumented IR for this source; each debugger owns its runtime and trace copy. */
    public static DebugApi fromIr(SourceFile source, IrResult ir, String standardInput) {
        return new DebugApi(Debugger.fromIr(Objects.requireNonNull(source, "source"),
                Objects.requireNonNull(ir, "ir"), Objects.requireNonNull(standardInput, "standardInput")));
    }

    /** Retains at most this many contexts, including the current one; ordinary sessions keep full history. */
    public static DebugApi fromIr(SourceFile source, IrResult ir, String standardInput, int historyLimit) {
        return new DebugApi(Debugger.fromIr(Objects.requireNonNull(source, "source"),
                Objects.requireNonNull(ir, "ir"), Objects.requireNonNull(standardInput, "standardInput"), historyLimit));
    }

    /**
     * Runs the same checked interpreter without intermediate history snapshots.
     * The stop budget counts advances to source stops, including the terminal stop but
     * excluding the initial ready context. The output budget applies independently to
     * the raw bytes emitted to stdout and stderr; exactly the limit is permitted.
     * Budgets are checked between stops, so one advance can exceed the output budget.
     * These are not instruction, wall-clock, or hard memory limits.
     * Interactive stepping and history recording use the ordinary instance API.
     */
    public static Debugger.Execution execute(SourceFile source, IrResult ir, String standardInput,
                                             int maximumStops, int maximumOutputBytes) {
        return Debugger.fromIr(Objects.requireNonNull(source, "source"), Objects.requireNonNull(ir, "ir"),
                Objects.requireNonNull(standardInput, "standardInput"), 1).execute(maximumStops, maximumOutputBytes);
    }

    public DebugApi(Debugger debugger) {
        this.debugger = Objects.requireNonNull(debugger, "debugger");
        current = debugger.initialContext();
        iterator = new ContextIterator();
    }

    public Debugger.Context next() {
        if (!iterator.hasNext()) return current;
        return current = iterator.next();
    }

    public Debugger.Context previous() {
        if (!iterator.hasPrevious()) return current;
        return current = iterator.previous();
    }

    public Debugger.Context current() { return current; }
    public boolean canNext() { return iterator.hasNext(); }
    public boolean canPrevious() { return iterator.hasPrevious(); }

    private final class ContextIterator implements ListIterator<Debugger.Context> {
        @Override public boolean hasNext() { return debugger.canStep(); }
        @Override public Debugger.Context next() {
            if (!hasNext()) throw new NoSuchElementException("debug api is at the end");
            return debugger.step();
        }
        @Override public boolean hasPrevious() { return debugger.canStepBack(); }
        @Override public Debugger.Context previous() {
            if (!hasPrevious()) throw new NoSuchElementException("debug api is at the beginning");
            return debugger.stepBack();
        }
        @Override public int nextIndex() { return current.index() + 1; }
        @Override public int previousIndex() { return current.index() - 1; }
        @Override public void remove() { throw new UnsupportedOperationException(); }
        @Override public void set(Debugger.Context context) { throw new UnsupportedOperationException(); }
        @Override public void add(Debugger.Context context) { throw new UnsupportedOperationException(); }
    }
}
