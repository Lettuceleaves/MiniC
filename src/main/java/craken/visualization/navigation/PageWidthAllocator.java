package craken.visualization.navigation;

import java.util.List;
import java.util.ArrayList;

/** Widths are assigned to occurrences, including repeated pages, from root toward focus. */
public final class PageWidthAllocator {
    public record Allocation(int firstVisibleIndex, List<Double> widths, double gap) {
        public Allocation { widths = List.copyOf(widths); }
    }

    public Allocation allocate(int count, double width, double q, double gap, double minimum) {
        if (count < 0 || !Double.isFinite(width) || width < 0
                || !Double.isFinite(q) || q <= 1 || !Double.isFinite(gap) || gap < 0
                || !Double.isFinite(minimum) || minimum <= 0)
            throw new IllegalArgumentException("Invalid occurrence width parameters");
        if (count == 0 || width == 0) return new Allocation(count, List.of(), gap);
        if (width < minimum) return new Allocation(count - 1, List.of(width), gap);
        // Normalize by the largest weight. No power is raised to the total path depth.
        double inverse = 1 / q;
        double smallest = 1;
        double sum = 1;
        int visible = 1;
        while (visible < count) {
            double candidateSmallest = smallest * inverse;
            double candidateSum = 1 + sum * inverse;
            double net = width - visible * gap;
            if (net <= 0 || Math.floor(net / candidateSum * candidateSmallest) < minimum) break;
            smallest = candidateSmallest;
            sum = candidateSum;
            visible++;
        }
        double net = width - (visible - 1) * gap;
        double largest = net / sum;
        var widths = new ArrayList<Double>(visible);
        double used = 0;
        for (int i = 0; i < visible - 1; i++) {
            double item = Math.floor(largest * Math.pow(inverse, visible - 1 - i));
            widths.add(item);
            used += item;
        }
        widths.add(net - used);
        return new Allocation(count - visible, widths, gap);
    }
}
