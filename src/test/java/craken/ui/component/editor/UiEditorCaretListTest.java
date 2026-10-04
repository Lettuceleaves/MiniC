package craken.ui.component.editor;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UiEditorCaretListTest {
    @Test
    void prefersBelowWhenAllItemsFitEvenIfThereIsMoreSpaceAbove() {
        Bounds available = new BoundingBox(100, 100, 1000, 800);
        Bounds caret = new BoundingBox(250, 650, 1, 20);

        var placement = UiEditorCaretList.calculatePlacement(caret, available, 430, 20, 5, 12);

        assertNotNull(placement);
        assertFalse(placement.above());
        assertEquals(250, placement.x());
        assertEquals(672, placement.y());
        assertEquals(430, placement.width());
        assertEquals(102, placement.height());
        assertEquals(5, placement.visibleItems());
    }

    @Test
    void flipsAboveNearTheBottomEdge() {
        Bounds available = new BoundingBox(0, 0, 1000, 800);
        Bounds caret = new BoundingBox(100, 750, 1, 20);

        var placement = UiEditorCaretList.calculatePlacement(caret, available, 430, 20, 20, 12);

        assertNotNull(placement);
        assertTrue(placement.above());
        assertEquals(506, placement.y());
        assertEquals(242, placement.height());
        assertEquals(12, placement.visibleItems());
        assertEquals(caret.getMinY() - 2, placement.y() + placement.height());
    }

    @Test
    void shrinksToWholeRowsAboveWhenNeitherSideFitsTheRequestedHeight() {
        Bounds available = new BoundingBox(0, 0, 800, 200);
        Bounds caret = new BoundingBox(100, 150, 1, 20);

        var placement = UiEditorCaretList.calculatePlacement(caret, available, 430, 24, 20, 12);

        assertNotNull(placement);
        assertTrue(placement.above());
        assertEquals(6, placement.visibleItems());
        assertEquals(146, placement.height());
        assertEquals(2, placement.y());
    }

    @Test
    void shrinksBelowWhenThatSideHasMoreSpace() {
        Bounds available = new BoundingBox(0, 0, 800, 200);
        Bounds caret = new BoundingBox(100, 40, 1, 20);

        var placement = UiEditorCaretList.calculatePlacement(caret, available, 430, 24, 20, 12);

        assertNotNull(placement);
        assertFalse(placement.above());
        assertEquals(5, placement.visibleItems());
        assertEquals(122, placement.height());
        assertEquals(62, placement.y());
    }

    @Test
    void clampsTheRightEdgeToTheAvailableArea() {
        Bounds available = new BoundingBox(100, 100, 1000, 800);
        Bounds caret = new BoundingBox(1000, 200, 1, 20);

        var placement = UiEditorCaretList.calculatePlacement(caret, available, 430, 20, 3, 12);

        assertNotNull(placement);
        assertEquals(670, placement.x());
        assertEquals(available.getMaxX(), placement.x() + placement.width());
    }

    @Test
    void clampsTheLeftEdgeWhenTheCaretIsPartiallyVisible() {
        Bounds available = new BoundingBox(100, 100, 1000, 800);
        Bounds caret = new BoundingBox(99, 200, 2, 20);

        var placement = UiEditorCaretList.calculatePlacement(caret, available, 430, 20, 3, 12);

        assertNotNull(placement);
        assertEquals(available.getMinX(), placement.x());
        assertEquals(430, placement.width());
    }

    @Test
    void reducesWidthForANarrowWindow() {
        Bounds available = new BoundingBox(100, 50, 260, 450);
        Bounds caret = new BoundingBox(280, 100, 1, 20);

        var placement = UiEditorCaretList.calculatePlacement(caret, available, 430, 20, 3, 12);

        assertNotNull(placement);
        assertEquals(100, placement.x());
        assertEquals(260, placement.width());
        assertEquals(122, placement.y());
    }

    @Test
    void supportsScreensWithNegativeCoordinates() {
        Bounds available = new BoundingBox(-1920, -1080, 1920, 1080);
        Bounds caret = new BoundingBox(-300, -80, 1, 20);

        var placement = UiEditorCaretList.calculatePlacement(caret, available, 430, 20, 20, 12);

        assertNotNull(placement);
        assertTrue(placement.above());
        assertEquals(-430, placement.x());
        assertEquals(-324, placement.y());
        assertEquals(430, placement.width());
        assertEquals(242, placement.height());
    }

    @Test
    void acceptsAnExactWholeRowFitBelow() {
        Bounds available = new BoundingBox(0, 0, 800, 100);
        Bounds caret = new BoundingBox(100, 52, 1, 20);

        var placement = UiEditorCaretList.calculatePlacement(caret, available, 430, 24, 1, 12);

        assertNotNull(placement);
        assertFalse(placement.above());
        assertEquals(1, placement.visibleItems());
        assertEquals(74, placement.y());
        assertEquals(available.getMaxY(), placement.y() + placement.height());
    }

    @Test
    void returnsNoPlacementWhenNeitherSideFitsAWholeRow() {
        Bounds available = new BoundingBox(0, 0, 800, 40);
        Bounds caret = new BoundingBox(100, 10, 1, 20);

        assertNull(UiEditorCaretList.calculatePlacement(caret, available, 430, 24, 5, 12));
    }

    @Test
    void returnsNoPlacementForAnEmptyList() {
        Bounds available = new BoundingBox(0, 0, 800, 600);
        Bounds caret = new BoundingBox(100, 100, 1, 20);

        assertNull(UiEditorCaretList.calculatePlacement(caret, available, 430, 20, 0, 12));
    }

    @Test
    void returnsNoPlacementWhenTheCaretHasScrolledOutsideTheAvailableArea() {
        Bounds available = new BoundingBox(100, 100, 800, 600);

        assertNull(UiEditorCaretList.calculatePlacement(
                new BoundingBox(120, 70, 1, 20), available, 430, 20, 5, 12));
        assertNull(UiEditorCaretList.calculatePlacement(
                new BoundingBox(120, 710, 1, 20), available, 430, 20, 5, 12));
        assertNull(UiEditorCaretList.calculatePlacement(
                new BoundingBox(50, 200, 1, 20), available, 430, 20, 5, 12));
        assertNull(UiEditorCaretList.calculatePlacement(
                new BoundingBox(950, 200, 1, 20), available, 430, 20, 5, 12));
    }

    @Test
    void returnsNoPlacementWhenTheAvailableAreaHasNoWidth() {
        Bounds available = new BoundingBox(100, 0, 0, 600);
        Bounds caret = new BoundingBox(100, 100, 1, 20);

        assertNull(UiEditorCaretList.calculatePlacement(caret, available, 430, 20, 5, 12));
    }
}
