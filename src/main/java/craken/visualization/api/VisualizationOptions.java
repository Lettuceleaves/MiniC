package craken.visualization.api;

public record VisualizationOptions(boolean autoNavigate, boolean propagateHighlight) {
    public static final VisualizationOptions DEFAULT = new VisualizationOptions(true,true);
}
