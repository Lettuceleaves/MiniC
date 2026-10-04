package craken.ui.component.navigation;

import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import craken.ui.component.UiComponent;

import java.util.Objects;

/**
 * 无系统装饰窗口使用的应用标题栏。
 *
 * <p>按钮采用 Windows 标准标题栏字形，并提供最小化、最大化/还原和关闭行为。
 * 空白区域支持拖动、双击最大化；窗口边缘保留缩放能力。</p>
 */
public final class UiAppTitleBar extends HBox implements UiComponent {
    private static final double HEIGHT = 36;
    private static final double RESIZE_BORDER = 6;

    private final Region dragRegion = new Region();
    private final Button minimize = control("\uE921", "最小化", "window-minimize-button");
    private final Button maximize = control("\uE922", "最大化", "window-maximize-button");
    private final Button close = control("\uE8BB", "关闭", "window-close-button");
    private final EventHandler<MouseEvent> movedHandler = this::handleSceneMouseMoved;
    private final EventHandler<MouseEvent> pressedHandler = this::handleSceneMousePressed;
    private final EventHandler<MouseEvent> draggedHandler = this::handleSceneMouseDragged;
    private final EventHandler<MouseEvent> releasedHandler = this::handleSceneMouseReleased;

    private Scene observedScene;
    private ResizeEdge resizeEdge = ResizeEdge.NONE;
    private double startScreenX;
    private double startScreenY;
    private double startWindowX;
    private double startWindowY;
    private double startWindowWidth;
    private double startWindowHeight;
    private double dragWidthRatio;
    private boolean dragging;
    private boolean resizing;

    public UiAppTitleBar(Node leadingContent) {
        Objects.requireNonNull(leadingContent, "leadingContent");
        getStyleClass().add("ui-app-title-bar");
        setAlignment(Pos.CENTER_LEFT);
        setMinHeight(HEIGHT);
        setPrefHeight(HEIGHT);
        setMaxHeight(HEIGHT);

        leadingContent.getStyleClass().add("window-title-no-drag");
        dragRegion.getStyleClass().add("window-title-drag-region");
        dragRegion.setMinWidth(24);
        HBox.setHgrow(dragRegion, Priority.ALWAYS);

        HBox controls = new HBox(minimize, maximize, close);
        controls.setAlignment(Pos.CENTER_RIGHT);
        controls.getStyleClass().addAll("window-controls", "window-title-no-drag");
        getChildren().addAll(leadingContent, dragRegion, controls);

        minimize.setOnAction(event -> stage().setIconified(true));
        maximize.setOnAction(event -> toggleMaximized());
        close.setOnAction(event -> requestClose());

        dragRegion.setOnMousePressed(this::handleTitlePressed);
        dragRegion.setOnMouseDragged(this::handleTitleDragged);
        dragRegion.setOnMouseReleased(event -> dragging = false);
        dragRegion.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                toggleMaximized();
                event.consume();
            }
        });

        sceneProperty().addListener((observable, previous, current) -> observeScene(current));
        observeScene(getScene());
    }

    private void handleTitlePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || resizing) {
            return;
        }
        Stage stage = stage();
        dragging = true;
        startScreenX = event.getScreenX();
        startScreenY = event.getScreenY();
        startWindowX = stage.getX();
        startWindowY = stage.getY();
        dragWidthRatio = stage.getWidth() <= 0 ? 0.5 : event.getSceneX() / stage.getWidth();
        event.consume();
    }

    private void handleTitleDragged(MouseEvent event) {
        if (!dragging || !event.isPrimaryButtonDown()) {
            return;
        }
        Stage stage = stage();
        if (stage.isMaximized()) {
            stage.setMaximized(false);
            double ratio = Math.max(0, Math.min(1, dragWidthRatio));
            startWindowX = event.getScreenX() - stage.getWidth() * ratio;
            startWindowY = event.getScreenY() - HEIGHT / 2;
            startScreenX = event.getScreenX();
            startScreenY = event.getScreenY();
        }
        stage.setX(startWindowX + event.getScreenX() - startScreenX);
        stage.setY(startWindowY + event.getScreenY() - startScreenY);
        event.consume();
    }

    private void toggleMaximized() {
        Stage stage = stage();
        stage.setMaximized(!stage.isMaximized());
    }

    private void requestClose() {
        Stage stage = stage();
        WindowEvent request = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
        Event.fireEvent(stage, request);
        if (!request.isConsumed()) {
            stage.close();
        }
    }

    private void observeScene(Scene scene) {
        if (observedScene != null) {
            observedScene.removeEventFilter(MouseEvent.MOUSE_MOVED, movedHandler);
            observedScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, pressedHandler);
            observedScene.removeEventFilter(MouseEvent.MOUSE_DRAGGED, draggedHandler);
            observedScene.removeEventFilter(MouseEvent.MOUSE_RELEASED, releasedHandler);
        }
        observedScene = scene;
        if (scene == null) {
            return;
        }
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, movedHandler);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, pressedHandler);
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, draggedHandler);
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, releasedHandler);
        scene.windowProperty().addListener((observable, previous, current) -> {
            if (current instanceof Stage stage) {
                stage.maximizedProperty().addListener((ignored, wasMaximized, isMaximized) ->
                        updateMaximizeButton(isMaximized));
                updateMaximizeButton(stage.isMaximized());
            }
        });
        if (scene.getWindow() instanceof Stage stage) {
            stage.maximizedProperty().addListener((ignored, wasMaximized, isMaximized) ->
                    updateMaximizeButton(isMaximized));
            updateMaximizeButton(stage.isMaximized());
        }
    }

    private void handleSceneMouseMoved(MouseEvent event) {
        if (resizing) {
            return;
        }
        Stage stage = stageOrNull();
        if (stage == null || stage.isMaximized() || stage.isFullScreen()) {
            resizeEdge = ResizeEdge.NONE;
            observedScene.setCursor(Cursor.DEFAULT);
            return;
        }
        resizeEdge = ResizeEdge.at(event.getSceneX(), event.getSceneY(),
                observedScene.getWidth(), observedScene.getHeight());
        observedScene.setCursor(resizeEdge.cursor);
    }

    private void handleSceneMousePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || resizeEdge == ResizeEdge.NONE) {
            return;
        }
        Stage stage = stage();
        resizing = true;
        dragging = false;
        startScreenX = event.getScreenX();
        startScreenY = event.getScreenY();
        startWindowX = stage.getX();
        startWindowY = stage.getY();
        startWindowWidth = stage.getWidth();
        startWindowHeight = stage.getHeight();
        event.consume();
    }

    private void handleSceneMouseDragged(MouseEvent event) {
        if (!resizing || !event.isPrimaryButtonDown()) {
            return;
        }
        Stage stage = stage();
        double dx = event.getScreenX() - startScreenX;
        double dy = event.getScreenY() - startScreenY;
        resizeHorizontal(stage, dx);
        resizeVertical(stage, dy);
        event.consume();
    }

    private void resizeHorizontal(Stage stage, double dx) {
        if (resizeEdge.left) {
            double width = Math.max(stage.getMinWidth(), startWindowWidth - dx);
            stage.setX(startWindowX + startWindowWidth - width);
            stage.setWidth(width);
        } else if (resizeEdge.right) {
            stage.setWidth(Math.max(stage.getMinWidth(), startWindowWidth + dx));
        }
    }

    private void resizeVertical(Stage stage, double dy) {
        if (resizeEdge.top) {
            double height = Math.max(stage.getMinHeight(), startWindowHeight - dy);
            stage.setY(startWindowY + startWindowHeight - height);
            stage.setHeight(height);
        } else if (resizeEdge.bottom) {
            stage.setHeight(Math.max(stage.getMinHeight(), startWindowHeight + dy));
        }
    }

    private void handleSceneMouseReleased(MouseEvent event) {
        resizing = false;
        dragging = false;
    }

    private void updateMaximizeButton(boolean maximized) {
        maximize.setText(maximized ? "\uE923" : "\uE922");
        maximize.setAccessibleText(maximized ? "还原" : "最大化");
    }

    private Stage stage() {
        Stage stage = stageOrNull();
        if (stage == null) {
            throw new IllegalStateException("title bar is not attached to a Stage");
        }
        return stage;
    }

    private Stage stageOrNull() {
        return getScene() != null && getScene().getWindow() instanceof Stage stage ? stage : null;
    }

    private static Button control(String glyph, String accessibleText, String specificClass) {
        Button button = new Button(glyph);
        button.setAccessibleText(accessibleText);
        button.setFocusTraversable(false);
        button.getStyleClass().addAll("window-control-button", specificClass);
        button.setMinSize(46, HEIGHT);
        button.setPrefSize(46, HEIGHT);
        button.setMaxSize(46, HEIGHT);
        return button;
    }

    private enum ResizeEdge {
        NONE(false, false, false, false, Cursor.DEFAULT),
        N(false, false, true, false, Cursor.N_RESIZE),
        NE(false, true, true, false, Cursor.NE_RESIZE),
        E(false, true, false, false, Cursor.E_RESIZE),
        SE(false, true, false, true, Cursor.SE_RESIZE),
        S(false, false, false, true, Cursor.S_RESIZE),
        SW(true, false, false, true, Cursor.SW_RESIZE),
        W(true, false, false, false, Cursor.W_RESIZE),
        NW(true, false, true, false, Cursor.NW_RESIZE);

        private final boolean left;
        private final boolean right;
        private final boolean top;
        private final boolean bottom;
        private final Cursor cursor;

        ResizeEdge(boolean left, boolean right, boolean top, boolean bottom, Cursor cursor) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
            this.cursor = cursor;
        }

        private static ResizeEdge at(double x, double y, double width, double height) {
            boolean left = x >= 0 && x <= RESIZE_BORDER;
            boolean right = x <= width && x >= width - RESIZE_BORDER;
            boolean top = y >= 0 && y <= RESIZE_BORDER;
            boolean bottom = y <= height && y >= height - RESIZE_BORDER;
            if (top && left) return NW;
            if (top && right) return NE;
            if (bottom && left) return SW;
            if (bottom && right) return SE;
            if (top) return N;
            if (right) return E;
            if (bottom) return S;
            if (left) return W;
            return NONE;
        }
    }
}
