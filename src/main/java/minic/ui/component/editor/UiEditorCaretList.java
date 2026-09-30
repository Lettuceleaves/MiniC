package minic.ui.component.editor;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** JavaFX 光标列表。全部方法由 FX 线程调用，选择回调也在 FX 线程执行。 */
final class UiEditorCaretList {
    static final double DEFAULT_POPUP_WIDTH = 430;
    static final int DEFAULT_MAX_VISIBLE_ITEMS = 12;
    private static final double BORDER_WIDTH = 1;
    private static final double CARET_GAP = 2;
    private static final double CELL_HORIZONTAL_PADDING = 8;

    private final Node owner;
    private final Popup popup = new Popup();
    private final DoubleProperty cellWidth = new SimpleDoubleProperty(DEFAULT_POPUP_WIDTH - 2);
    private final List<CaretListCell> cells = new ArrayList<>();
    private final ListView<UiCodeEditorListItem> listView = new CaretListView();
    private final ChangeListener<Window> sceneWindowListener =
            (observable, oldWindow, newWindow) -> observeWindow(newWindow);
    private final ChangeListener<Boolean> windowShowingListener =
            (observable, oldShowing, showing) -> {
                if (!showing) {
                    hide();
                }
            };

    private Scene observedScene;
    private Window observedWindow;
    private Consumer<UiCodeEditorListItem> onChosen;
    private Bounds lastCaretBounds;
    private Bounds lastAvailableBounds;
    private double popupWidth = DEFAULT_POPUP_WIDTH;
    private int maxVisibleItems = DEFAULT_MAX_VISIBLE_ITEMS;
    private int visibleItems;
    private double requestedRowHeight = 24;
    private double rowHeight = 24;
    private Font font = Font.font("Monospaced", 13);
    private Color background = Color.rgb(31, 31, 31);
    private Color foreground = Color.rgb(220, 220, 220);
    private Color mutedForeground = Color.rgb(140, 140, 140);
    private Color border = Color.rgb(70, 70, 70);
    private Color selection = Color.rgb(102, 85, 27);
    private Color currentLine = Color.rgb(45, 45, 45);

    UiEditorCaretList(Node owner, EventHandler<KeyEvent> keyHandler) {
        this.owner = Objects.requireNonNull(owner, "owner");
        popup.setAutoFix(false);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setConsumeAutoHidingEvents(false);
        popup.getContent().add(listView);
        popup.setOnHidden(event -> clearAfterHide());
        // JavaFX 会先把编辑器按键转发给 Popup；两处必须共用完整的按下/输入/释放处理。
        popup.addEventFilter(KeyEvent.ANY, Objects.requireNonNull(keyHandler, "keyHandler"));

        listView.setFocusTraversable(false);
        listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        listView.setCellFactory(view -> new CaretListCell());
        listView.setFixedCellSize(rowHeight);
        applyListStyle();

        owner.sceneProperty().addListener((observable, oldScene, newScene) -> observeScene(newScene));
        owner.visibleProperty().addListener((observable, oldVisible, visible) -> {
            if (!visible) {
                hide();
            }
        });
        observeScene(owner.getScene());
    }

    void show(List<UiCodeEditorListItem> items, Consumer<UiCodeEditorListItem> onChosen,
              Bounds caretOnScreen, Bounds availableOnScreen, double rowHeight) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(onChosen, "onChosen");
        List<UiCodeEditorListItem> snapshot = List.copyOf(items);
        if (!Double.isFinite(rowHeight) || rowHeight <= 0) {
            throw new IllegalArgumentException("rowHeight must be finite and positive");
        }
        hideTooltips();
        if (snapshot.isEmpty() || !ownerIsShowing()) {
            hide();
            return;
        }
        this.onChosen = onChosen;
        requestedRowHeight = rowHeight;
        updateRowHeight();
        listView.getItems().setAll(snapshot);
        listView.getSelectionModel().selectFirst();
        listView.scrollTo(0);
        reposition(caretOnScreen, availableOnScreen);
    }

    void reposition(Bounds caretOnScreen, Bounds availableOnScreen) {
        if (listView.getItems().isEmpty() || !ownerIsShowing()) {
            hide();
            return;
        }
        lastCaretBounds = caretOnScreen;
        lastAvailableBounds = availableOnScreen;
        if (validBounds(caretOnScreen) && caretOnScreen.getHeight() > 0) {
            requestedRowHeight = caretOnScreen.getHeight();
            updateRowHeight();
        }
        Bounds usableBounds = intersectScreen(caretOnScreen, availableOnScreen);
        Placement placement = calculatePlacement(caretOnScreen, usableBounds, popupWidth,
                rowHeight, listView.getItems().size(), maxVisibleItems);
        if (placement == null) {
            hide();
            return;
        }
        hideTooltips();
        visibleItems = placement.visibleItems();
        listView.setMinSize(placement.width(), placement.height());
        listView.setPrefSize(placement.width(), placement.height());
        listView.setMaxSize(placement.width(), placement.height());
        // Popup is deliberately non-focusable; keyboard events normally arrive from the editor.
        if (!popup.isShowing()) {
            popup.show(owner, placement.x(), placement.y());
        } else {
            popup.setX(placement.x());
            popup.setY(placement.y());
        }
        listView.applyCss();
        listView.layout();
    }

    void hide() {
        popup.hide();
        clearAfterHide();
    }

    boolean isShowing() {
        return popup.isShowing();
    }

    void applyStyle(UiCodeEditorStyle style, double zoomFactor) {
        Objects.requireNonNull(style, "style");
        background = fxColor(style.background());
        foreground = fxColor(style.foreground());
        mutedForeground = fxColor(style.mutedForeground());
        border = fxColor(style.border());
        selection = fxColor(style.selection());
        currentLine = fxColor(style.currentLine());
        java.awt.Font editorFont = style.font();
        font = Font.font(editorFont.getFamily(),
                editorFont.isBold() ? FontWeight.BOLD : FontWeight.NORMAL,
                editorFont.isItalic() ? FontPosture.ITALIC : FontPosture.REGULAR,
                editorFont.getSize2D() * zoomFactor);
        updateRowHeight();
        applyListStyle();
        cells.forEach(CaretListCell::applyCellStyle);
        if (isShowing()) {
            reposition(lastCaretBounds, lastAvailableBounds);
        }
    }

    void setPopupWidth(double width) {
        if (!Double.isFinite(width) || width <= 0) {
            throw new IllegalArgumentException("popup width must be finite and positive");
        }
        popupWidth = width;
        if (isShowing()) {
            reposition(lastCaretBounds, lastAvailableBounds);
        }
    }

    void setMaxVisibleItems(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("maximum visible items must be positive");
        }
        maxVisibleItems = count;
        if (isShowing()) {
            reposition(lastCaretBounds, lastAvailableBounds);
        }
    }

    boolean handleKey(KeyCode code) {
        if (!isShowing()) {
            return false;
        }
        int selected = listView.getSelectionModel().getSelectedIndex();
        switch (code) {
            case UP -> selectIndex(selected - 1);
            case DOWN -> selectIndex(selected + 1);
            case PAGE_UP -> selectIndex(selected - Math.max(1, visibleItems));
            case PAGE_DOWN -> selectIndex(selected + Math.max(1, visibleItems));
            case ENTER, TAB -> choose(listView.getSelectionModel().getSelectedItem());
            case ESCAPE -> hide();
            default -> {
                return false;
            }
        }
        return true;
    }

    private void selectIndex(int index) {
        hideTooltips();
        int bounded = Math.max(0, Math.min(index, listView.getItems().size() - 1));
        listView.getSelectionModel().select(bounded);
        listView.scrollTo(bounded);
    }

    private void choose(UiCodeEditorListItem item) {
        Consumer<UiCodeEditorListItem> callback = onChosen;
        hide();
        if (item != null && callback != null) {
            callback.accept(item);
        }
    }

    private void clearAfterHide() {
        hideTooltips();
        onChosen = null;
        lastCaretBounds = null;
        lastAvailableBounds = null;
        listView.getItems().clear();
    }

    private void hideTooltips() {
        cells.forEach(cell -> {
            cell.hideTooltip();
            cell.requestLayout();
        });
    }

    private void updateRowHeight() {
        Text sample = new Text("Ag");
        sample.setFont(font);
        rowHeight = Math.max(requestedRowHeight, Math.ceil(sample.getLayoutBounds().getHeight()) + 8);
        listView.setFixedCellSize(rowHeight);
    }

    private void applyListStyle() {
        // Background and Border are composite CSS properties. Set all their parts at
        // the same (inline) priority, so Modena cannot reintroduce its default fills
        // when applying background-insets or a selected/odd-row pseudo-class.
        listView.setStyle("-fx-background-color: " + cssColor(background)
                + "; -fx-control-inner-background: " + cssColor(background)
                + "; -fx-control-inner-background-alt: " + cssColor(background)
                + "; -fx-base: " + cssColor(background)
                + "; -fx-selection-bar: " + cssColor(selection)
                + "; -fx-selection-bar-non-focused: " + cssColor(selection)
                + "; -fx-text-background-color: " + cssColor(foreground)
                + "; -fx-text-inner-color: " + cssColor(foreground)
                + "; -fx-background-insets: 0; -fx-background-radius: 0;"
                + " -fx-border-color: " + cssColor(border)
                + "; -fx-border-width: " + BORDER_WIDTH
                + "; -fx-border-insets: 0; -fx-border-radius: 0; -fx-padding: 0;"
                + " -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        listView.requestLayout();
    }

    private boolean ownerIsShowing() {
        return owner.isVisible() && owner.getScene() != null
                && owner.getScene().getWindow() != null && owner.getScene().getWindow().isShowing();
    }

    private void observeScene(Scene scene) {
        hide();
        if (observedScene != null) {
            observedScene.windowProperty().removeListener(sceneWindowListener);
        }
        observedScene = scene;
        if (scene != null) {
            scene.windowProperty().addListener(sceneWindowListener);
        }
        observeWindow(scene == null ? null : scene.getWindow());
    }

    private void observeWindow(Window window) {
        hide();
        if (observedWindow != null) {
            observedWindow.showingProperty().removeListener(windowShowingListener);
        }
        observedWindow = window;
        if (window != null) {
            window.showingProperty().addListener(windowShowingListener);
        }
    }

    private static Bounds intersectScreen(Bounds caret, Bounds available) {
        if (!validBounds(caret) || !validBounds(available)) {
            return null;
        }
        List<Screen> screens = Screen.getScreensForRectangle(caret.getMinX(), caret.getMinY(),
                Math.max(1, caret.getWidth()), Math.max(1, caret.getHeight()));
        if (screens.isEmpty()) {
            return null;
        }
        Rectangle2D screen = screens.getFirst().getVisualBounds();
        double minX = Math.max(screen.getMinX(), available.getMinX());
        double minY = Math.max(screen.getMinY(), available.getMinY());
        double maxX = Math.min(screen.getMaxX(), available.getMaxX());
        double maxY = Math.min(screen.getMaxY(), available.getMaxY());
        if (maxX <= minX || maxY <= minY) {
            return null;
        }
        return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
    }

    /** 不依赖 JavaFX toolkit 的纯布局计算；没有一整行的空间时返回 null。 */
    static Placement calculatePlacement(Bounds caret, Bounds available, double width,
                                        double rowHeight, int itemCount, int maxVisibleItems) {
        if (!validBounds(caret) || !validBounds(available)
                || !Double.isFinite(width) || width <= 0
                || !Double.isFinite(rowHeight) || rowHeight <= 0
                || itemCount <= 0 || maxVisibleItems <= 0
                || caret.getMaxX() < available.getMinX() || caret.getMinX() > available.getMaxX()
                || caret.getMaxY() <= available.getMinY() || caret.getMinY() >= available.getMaxY()) {
            return null;
        }
        double actualWidth = Math.min(width, available.getWidth());
        if (actualWidth < 2 * CELL_HORIZONTAL_PADDING + 2 * BORDER_WIDTH + 1) {
            return null;
        }
        int desiredRows = Math.min(itemCount, maxVisibleItems);
        double desiredHeight = desiredRows * rowHeight + 2 * BORDER_WIDTH;
        double belowY = caret.getMaxY() + CARET_GAP;
        double aboveY = caret.getMinY() - CARET_GAP;
        double belowSpace = Math.max(0, available.getMaxY() - belowY);
        double aboveSpace = Math.max(0, aboveY - available.getMinY());
        boolean above = desiredHeight > belowSpace && aboveSpace > belowSpace;
        double room = above ? aboveSpace : belowSpace;
        int rows = Math.min(desiredRows, (int) Math.floor((room - 2 * BORDER_WIDTH) / rowHeight));
        if (rows < 1) {
            return null;
        }
        double height = rows * rowHeight + 2 * BORDER_WIDTH;
        double x = Math.max(available.getMinX(), Math.min(caret.getMinX(), available.getMaxX() - actualWidth));
        return new Placement(x, above ? aboveY - height : belowY, actualWidth, height, rows, above);
    }

    private static boolean validBounds(Bounds bounds) {
        return bounds != null && Double.isFinite(bounds.getMinX()) && Double.isFinite(bounds.getMinY())
                && Double.isFinite(bounds.getMaxX()) && Double.isFinite(bounds.getMaxY())
                && bounds.getWidth() >= 0 && bounds.getHeight() >= 0;
    }

    record Placement(double x, double y, double width, double height, int visibleItems, boolean above) {
    }

    private static Color fxColor(java.awt.Color color) {
        return Color.rgb(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() / 255.0);
    }

    private static String cssColor(Color color) {
        return String.format(java.util.Locale.ROOT, "rgba(%d,%d,%d,%.3f)",
                Math.round(color.getRed() * 255), Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255), color.getOpacity());
    }

    private final class CaretListView extends ListView<UiCodeEditorListItem> {
        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            double scrollbarWidth = 0;
            for (Node node : lookupAll(".scroll-bar")) {
                if (!(node instanceof ScrollBar scrollbar)) {
                    continue;
                }
                if (scrollbar.getOrientation() == Orientation.HORIZONTAL) {
                    scrollbar.setMinHeight(0);
                    scrollbar.setPrefHeight(0);
                    scrollbar.setMaxHeight(0);
                    scrollbar.setOpacity(0);
                    scrollbar.setMouseTransparent(true);
                } else {
                    if (getItems().size() > visibleItems) {
                        scrollbarWidth = Math.max(scrollbar.getWidth(), scrollbar.prefWidth(-1));
                    }
                    scrollbar.setStyle("-fx-background-color: " + cssColor(background) + ";");
                    Node thumb = scrollbar.lookup(".thumb");
                    if (thumb != null) {
                        thumb.setStyle("-fx-background-color: " + cssColor(mutedForeground)
                                + "; -fx-opacity: 0.65; -fx-background-radius: 2;");
                    }
                }
            }
            cellWidth.set(Math.max(0, Math.floor(getWidth() - getInsets().getLeft()
                    - getInsets().getRight() - scrollbarWidth)));
        }
    }

    private final class CaretListCell extends ListCell<UiCodeEditorListItem> {
        private final Tooltip fullText = new Tooltip();
        private final Text measurement = new Text();

        CaretListCell() {
            cells.add(this);
            setFocusTraversable(false);
            setWrapText(false);
            setTextOverrun(OverrunStyle.ELLIPSIS);
            setEllipsisString("...");
            setAlignment(Pos.CENTER_LEFT);
            setPadding(new Insets(0, CELL_HORIZONTAL_PADDING, 0, CELL_HORIZONTAL_PADDING));
            setMinWidth(0);
            prefWidthProperty().bind(cellWidth);
            maxWidthProperty().bind(cellWidth);
            fullText.setWrapText(true);
            fullText.setMaxWidth(640);
            fullText.setShowDelay(Duration.millis(450));
            fullText.setHideDelay(Duration.millis(100));
            fullText.setShowDuration(Duration.seconds(30));
            selectedProperty().addListener((observable, oldSelected, selected) -> updateBackground());
            hoverProperty().addListener((observable, oldHover, hover) -> updateBackground());
            setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && !isEmpty()) {
                    choose(getItem());
                    event.consume();
                }
            });
            applyCellStyle();
        }

        @Override
        protected void updateItem(UiCodeEditorListItem item, boolean empty) {
            hideTooltip();
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.text().replace('\r', ' ').replace('\n', ' ').replace('\t', ' '));
            fullText.setText(empty || item == null ? "" : item.text());
            updateBackground();
            requestLayout();
        }

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            if (isEmpty() || getItem() == null || !popup.isShowing()) {
                hideTooltip();
                return;
            }
            measurement.setText(getText());
            measurement.setFont(getFont());
            double availableWidth = Math.max(0, getWidth() - getInsets().getLeft() - getInsets().getRight());
            boolean clipped = measurement.getLayoutBounds().getWidth() > availableWidth
                    || !getText().equals(getItem().text());
            if (clipped && getTooltip() != fullText) {
                setTooltip(fullText);
            } else if (!clipped) {
                hideTooltip();
            }
        }

        void applyCellStyle() {
            setFont(font);
            fullText.setStyle("-fx-background-color: " + cssColor(background)
                    + "; -fx-text-fill: " + cssColor(foreground)
                    + "; -fx-border-color: " + cssColor(border)
                    + "; -fx-border-width: 1; -fx-background-radius: 2; -fx-padding: 8;");
            fullText.setFont(font);
            updateBackground();
            requestLayout();
        }

        void hideTooltip() {
            fullText.hide();
            setTooltip(null);
        }

        private void updateBackground() {
            Color rowBackground = isEmpty() ? background
                    : isSelected() ? selection : isHover() ? currentLine : background;
            setStyle("-fx-background-color: " + cssColor(rowBackground)
                    + "; -fx-text-fill: " + cssColor(foreground)
                    + "; -fx-background-insets: 0; -fx-background-radius: 0;"
                    + " -fx-border-color: transparent; -fx-border-width: 0;"
                    + " -fx-padding: 0 " + CELL_HORIZONTAL_PADDING + " 0 " + CELL_HORIZONTAL_PADDING + ";");
        }
    }
}
