package craken.visualization.layout;

import craken.visualization.api.ViewLocation;
import java.util.*;
import static craken.visualization.layout.LayoutRequest.*;

public final class LinearLayout implements LayoutEngine {
    @Override public LayoutResult layout(LayoutRequest request, CancellationToken cancellation) {
        cancellation.check();
        var positions = new LinkedHashMap<ViewLocation, Point>();
        double x = request.hints().padding(), y = x;
        for (var unit : LayoutSupport.ordered(request)) {
            cancellation.check(); positions.put(unit.node(), new Point(x, y));
            if (request.hints().orientation() == Orientation.HORIZONTAL)
                x += unit.size().width() + request.hints().horizontalGap();
            else y += unit.size().height() + request.hints().verticalGap();
        }
        return LayoutSupport.finish(request, positions, cancellation, "linear");
    }
}
