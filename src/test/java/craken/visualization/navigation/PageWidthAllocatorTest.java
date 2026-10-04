package craken.visualization.navigation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-layout")
final class PageWidthAllocatorTest {
    private final PageWidthAllocator allocator = new PageWidthAllocator();

    @Test void allocatesOccurrenceWeightsAndPreservesEveryPixel() {
        var result = allocator.allocate(3, 1200, 2, 8, 160);
        assertEquals(0, result.firstVisibleIndex());
        assertEquals(List.of(169.0, 338.0, 677.0), result.widths());
        assertEquals(1200, result.widths().stream().mapToDouble(Double::doubleValue).sum() + 2 * result.gap());
    }

    @Test void dropsDistantOccurrencesBeforeCompressingTheFocus() {
        var result = allocator.allocate(100, 1200, 2, 8, 160);
        assertEquals(97, result.firstVisibleIndex());
        assertEquals(List.of(169.0, 338.0, 677.0), result.widths());
    }

    @Test void acceptsExactlyTheMinimumThreshold() {
        assertEquals(List.of(160.0, 320.0, 640.0), allocator.allocate(3, 1136, 2, 8, 160).widths());
        assertEquals(2, allocator.allocate(3, 1135, 2, 8, 160).widths().size());
    }

    @Test void keepsOneFocusWhenTheViewportIsNarrow() {
        assertEquals(List.of(120.5), allocator.allocate(20, 120.5, 2, 8, 160).widths());
        assertTrue(allocator.allocate(20, 0, 2, 8, 160).widths().isEmpty());
        assertTrue(allocator.allocate(0, 1200, 2, 8, 160).widths().isEmpty());
    }

    @Test void neverRaisesQToTheFullPathDepth() {
        var result = allocator.allocate(Integer.MAX_VALUE, 1200, 2, 8, 160);
        assertEquals(Integer.MAX_VALUE - 3, result.firstVisibleIndex());
        assertEquals(3, result.widths().size());
    }

    @Test void matchesSmallIndependentExhaustiveOracle() {
        for (int count = 1; count < 9; count++) {
            for (double width : new double[]{160, 161.5, 400, 800, 1135, 1136, 2500}) {
                for (double q : new double[]{1.1, 1.5, 2, 5}) {
                    int expected = 1;
                    for (int visible = 2; visible <= count; visible++) {
                        double weights = 0;
                        for (int i = 0; i < visible; i++) weights += Math.pow(q, i);
                        if (Math.floor((width - (visible - 1) * 8) / weights) >= 160) expected = visible;
                    }
                    var result = allocator.allocate(count, width, q, 8, 160);
                    assertEquals(expected, result.widths().size(), count + ":" + width + ":" + q);
                    assertEquals(width, result.widths().stream().mapToDouble(Double::doubleValue).sum()
                            + (expected - 1) * 8, 1e-8);
                    assertTrue(result.widths().stream().allMatch(w -> Double.isFinite(w) && w >= 160));
                }
            }
        }
    }

    @Test void rejectsInvalidParametersAndKeepsOutputImmutable() {
        assertThrows(IllegalArgumentException.class, () -> allocator.allocate(-1, 1, 2, 8, 160));
        for (double width : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> allocator.allocate(1, width, 2, 8, 160));
        for (double q : new double[]{0, 1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> allocator.allocate(1, 1200, q, 8, 160));
        assertThrows(IllegalArgumentException.class, () -> allocator.allocate(1, 1200, 2, -1, 160));
        assertThrows(IllegalArgumentException.class, () -> allocator.allocate(1, 1200, 2, 8, 0));
        assertThrows(UnsupportedOperationException.class, () -> allocator.allocate(1, 1200, 2, 8, 160).widths().add(1.0));
    }
}
