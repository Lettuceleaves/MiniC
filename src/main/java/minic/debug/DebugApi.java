package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.LanguageMode;

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

    public DebugApi(SourceFile source, String standardInput, LanguageMode languageMode) {
        this(new Debugger(source, standardInput, languageMode));
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
