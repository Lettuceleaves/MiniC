package craken.visualization.layout;

@FunctionalInterface
public interface CancellationToken {
    CancellationToken NONE = () -> false;
    boolean isCancelled();
    default void check() {
        if (isCancelled() || Thread.currentThread().isInterrupted())
            throw new LayoutException(LayoutException.Code.CANCELLED, "Layout cancelled");
    }
}
