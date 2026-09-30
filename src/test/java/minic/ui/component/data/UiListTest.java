package minic.ui.component.data;

import javafx.geometry.Orientation;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiListTest {
    private static final double EPSILON = 0.0001;

    @Test
    void horizontalListHasFixedHeightAndEqualItemWidths() {
        UiList list = new UiList(Orientation.HORIZONTAL, 80, 36);
        Pane first = new Pane();
        Pane second = new Pane();
        Pane third = new Pane();
        list.getItems().addAll(first, second, third);

        list.resize(160, 36);
        list.layout();

        assertEquals(36, list.minHeight(-1), EPSILON);
        assertEquals(36, list.prefHeight(-1), EPSILON);
        assertEquals(36, list.maxHeight(-1), EPSILON);
        assertEquals(80, first.getWidth(), EPSILON);
        assertEquals(80, second.getWidth(), EPSILON);
        assertEquals(80, third.getWidth(), EPSILON);
        assertEquals(0, first.getLayoutX(), EPSILON);
        assertEquals(80, second.getLayoutX(), EPSILON);
        assertEquals(160, third.getLayoutX(), EPSILON);
        assertEquals(80, list.getMaxScrollOffset(), EPSILON);
    }

    @Test
    void verticalListHasFixedWidthAndEqualItemHeights() {
        UiList list = new UiList(Orientation.VERTICAL, 48, 48);
        Pane first = new Pane();
        Pane second = new Pane();
        Pane third = new Pane();
        list.getItems().addAll(first, second, third);

        list.resize(48, 96);
        list.layout();

        assertEquals(48, list.minWidth(-1), EPSILON);
        assertEquals(48, list.prefWidth(-1), EPSILON);
        assertEquals(48, list.maxWidth(-1), EPSILON);
        assertEquals(48, first.getHeight(), EPSILON);
        assertEquals(48, second.getHeight(), EPSILON);
        assertEquals(48, third.getHeight(), EPSILON);
        assertEquals(0, first.getLayoutY(), EPSILON);
        assertEquals(48, second.getLayoutY(), EPSILON);
        assertEquals(96, third.getLayoutY(), EPSILON);
        assertEquals(48, list.getMaxScrollOffset(), EPSILON);
    }

    @Test
    void wheelDownMovesForwardAndWheelUpMovesBackward() {
        UiList list = overflowingHorizontalList();

        assertTrue(list.scrollByWheel(-1));
        assertEquals(80, list.getScrollOffset(), EPSILON);
        assertFalse(list.scrollByWheel(-1), "offset must clamp at the far edge");

        assertTrue(list.scrollByWheel(1));
        assertEquals(0, list.getScrollOffset(), EPSILON);
        assertFalse(list.scrollByWheel(1), "offset must clamp at the near edge");
    }

    @Test
    void wheelDoesNothingWhenItemsFitInsideTheViewport() {
        UiList list = new UiList(Orientation.HORIZONTAL, 80, 36);
        list.getItems().addAll(new Pane(), new Pane());
        list.resize(160, 36);
        list.layout();

        assertFalse(list.scrollByWheel(-1));
        assertEquals(0, list.getScrollOffset(), EPSILON);
    }

    @Test
    void scrollEventOverTheListUsesTheWheelContractAndIsConsumed() {
        UiList list = overflowingHorizontalList();
        AtomicBoolean reachedLaterHandler = new AtomicBoolean();
        list.addEventHandler(ScrollEvent.SCROLL, ignored -> reachedLaterHandler.set(true));
        ScrollEvent wheelDown = new ScrollEvent(
                ScrollEvent.SCROLL,
                10, 10, 10, 10,
                false, false, false, false,
                false, false,
                0, -40, 0, -40,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                0, null
        );

        list.fireEvent(wheelDown);

        assertEquals(80, list.getScrollOffset(), EPSILON);
        assertFalse(reachedLaterHandler.get(), "the list must consume a wheel event that scrolls it");
    }

    private static UiList overflowingHorizontalList() {
        UiList list = new UiList(Orientation.HORIZONTAL, 80, 36);
        list.getItems().addAll(new Pane(), new Pane(), new Pane());
        list.resize(160, 36);
        list.layout();
        return list;
    }
}
