package craken.visualization.layout;

import craken.visualization.api.ViewLocation;
import java.util.*;
import static craken.visualization.layout.LayoutRequest.*;

public final class ArrayLayout implements LayoutEngine {
    @Override public LayoutResult layout(LayoutRequest request, CancellationToken cancellation) {
        cancellation.check();
        var units = LayoutSupport.ordered(request);
        int columns = Math.min(request.hints().columns(), Math.max(1, units.size()));
        int rows = (units.size() + columns - 1) / columns;
        var columnWidths = new double[columns]; var rowHeights = new double[rows];
        for (int i = 0; i < units.size(); i++) {
            columnWidths[i % columns] = Math.max(columnWidths[i % columns], units.get(i).size().width());
            rowHeights[i / columns] = Math.max(rowHeights[i / columns], units.get(i).size().height());
        }
        var xs = new double[columns]; var ys = new double[rows];
        if (columns > 0) xs[0] = request.hints().padding();
        if (rows > 0) ys[0] = request.hints().padding();
        for (int i = 1; i < columns; i++) xs[i] = xs[i - 1] + columnWidths[i - 1] + request.hints().horizontalGap();
        for (int i = 1; i < rows; i++) ys[i] = ys[i - 1] + rowHeights[i - 1] + request.hints().verticalGap();
        var positions = new LinkedHashMap<ViewLocation, Point>();
        for (int i = 0; i < units.size(); i++) {
            cancellation.check();
            positions.put(units.get(i).node(), new Point(xs[i % columns], ys[i / columns]));
        }
        return LayoutSupport.finish(request, positions, cancellation, "array");
    }
}
