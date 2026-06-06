package minic.uilocal.text;

import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.List;

/**
 * Converts styled text segments into JavaFX text nodes.
 */
public final class MiniCTextFlowFactory {
    private MiniCTextFlowFactory() {}

    public static TextFlow textFlow(
            List<MiniCStyledTextSegment> segments,
            String flowStyleClass,
            boolean active
    ) {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add(flowStyleClass);
        if (active) {
            flow.getStyleClass().add("active");
        }
        for (MiniCStyledTextSegment segment : segments) {
            Text text = new Text(segment.text());
            text.getStyleClass().addAll(MiniCTextStyles.classes(segment.role()));
            flow.getChildren().add(text);
        }
        return flow;
    }
}
