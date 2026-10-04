package craken.visualization.model;

/** A namespace's high water mark is never rewound, including after rollback. */
public final class MonotonicIds {
    private long highWater;

    public synchronized long next() {
        if (highWater == Long.MAX_VALUE) throw new IllegalStateException("ID namespace exhausted");
        return ++highWater;
    }
    public synchronized long highWater() { return highWater; }
    public synchronized void observe(long value) {
        if (value < 0) throw new IllegalArgumentException("Negative high water mark");
        highWater = Math.max(highWater, value);
    }
}
