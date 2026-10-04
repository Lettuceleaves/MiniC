package craken.visualization.layout;

public interface LayoutEngine {
    LayoutResult layout(LayoutRequest request, CancellationToken cancellation);
    default LayoutResult layout(LayoutRequest request) { return layout(request, CancellationToken.NONE); }
}
