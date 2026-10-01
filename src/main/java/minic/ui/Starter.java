package minic.ui;

import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;
import javafx.geometry.Orientation;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import minic.ui.component.UiStyles;
import minic.ui.display.DisplayArea;
import minic.ui.editor.EditorArea;
import minic.ui.editor.EditorSplitDemo;
import minic.ui.frame.AppFrame;
import minic.ui.interaction.InteractionArea;
import minic.ui.demo.HoverComponentsDemo;
import minic.ui.run.RunController;
import minic.ui.pipeline.PipelineController;

/**
 * MiniC UI 启动器。
 *
 * <p>负责启动 JavaFX Runtime、创建应用窗口，并将编辑区、展示区和交互区
 * 组装到整体框架。各区的内容、状态和业务由各自的 UI 类承担。</p>
 */
public final class Starter {
    private Starter() {
    }

    public static void main(String[] args) {
        Application.launch(UiApplication.class, args);
    }

    /** JavaFX 生命周期入口，由 JavaFX Runtime 实例化。 */
    public static final class UiApplication extends Application {
        private static final double WINDOW_CORNER_RADIUS = 8;
        private InteractionArea interactionArea;
        private RunController runController;
        private PipelineController pipelineController;

        @Override
        public void start(Stage primaryStage) {
            primaryStage.initStyle(StageStyle.TRANSPARENT);
            EditorArea editorArea = new EditorArea();
            boolean splitDemo = getParameters().getRaw().contains("--split-demo");
            if (splitDemo) {
                EditorSplitDemo.show(editorArea);
            }
            DisplayArea displayArea = new DisplayArea();
            interactionArea = new InteractionArea();
            AppFrame root = new AppFrame(editorArea, displayArea, interactionArea, editorArea.tabBar());
            runController = new RunController(editorArea, root, interactionArea);
            pipelineController = new PipelineController(editorArea, root, displayArea, interactionArea.projectRoot());
            installMenus(root, editorArea, primaryStage);
            Scene scene = new Scene(root, 1280, 800);
            scene.setFill(Color.TRANSPARENT);
            UiStyles.install(scene);

            // 圆角属于整个应用窗口；裁剪标题栏、内容和状态栏，露出透明的 Stage 四角。
            // 最大化/全屏铺满屏幕时使用直角，还原窗口时恢复圆角。
            Rectangle windowClip = new Rectangle();
            windowClip.widthProperty().bind(root.widthProperty());
            windowClip.heightProperty().bind(root.heightProperty());
            windowClip.arcWidthProperty().bind(Bindings.when(
                    primaryStage.maximizedProperty().or(primaryStage.fullScreenProperty()))
                    .then(0.0).otherwise(WINDOW_CORNER_RADIUS * 2));
            windowClip.arcHeightProperty().bind(windowClip.arcWidthProperty());
            root.setClip(windowClip);

            primaryStage.setTitle(splitDemo ? "MiniC — 分屏演示" : "MiniC");
            primaryStage.setMinWidth(960);
            primaryStage.setMinHeight(600);
            primaryStage.setScene(scene);
            primaryStage.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
                root.showEditorWorkspace();
                if (!editorArea.closeAll()) event.consume();
            });
            primaryStage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> {
                runController.close();
                pipelineController.close();
                interactionArea.close();
            });
            primaryStage.show();
            if (getParameters().getRaw().contains("--hover-demo")) {
                HoverComponentsDemo.show(primaryStage);
            }
        }

        @Override
        public void stop() {
            if (runController != null) runController.close();
            if (pipelineController != null) pipelineController.close();
            if (interactionArea != null) interactionArea.close();
        }

        private void installMenus(AppFrame frame, EditorArea area, Stage stage) {
            MenuItem save = editorCommand(frame, "保存", "Shortcut+S", () -> area.save(area.activeFile()));
            MenuItem close = editorCommand(frame, "关闭文件", "Shortcut+W", () -> area.closeFile(area.activeFile()));
            save.disableProperty().bind(area.activeFileProperty().isNull());
            close.disableProperty().bind(area.activeFileProperty().isNull());
            frame.setMenuItems("文件",
                    editorCommand(frame, "新建文件…", "Shortcut+N", area::chooseNewFile),
                    editorCommand(frame, "打开文件…", "Shortcut+O", area::chooseOpenFiles),
                    new SeparatorMenuItem(), save, close);

            Menu split = new Menu("编辑器布局");
            split.disableProperty().bind(area.activeFileProperty().isNull());
            split.getItems().addAll(
                    editorCommand(frame, "向右拆分", null, () -> area.splitFile(area.activeFile(), Orientation.HORIZONTAL)),
                    editorCommand(frame, "向下拆分", null, () -> area.splitFile(area.activeFile(), Orientation.VERTICAL)));
            Menu zoom = new Menu("编辑器缩放");
            zoom.disableProperty().bind(area.activeFileProperty().isNull());
            zoom.getItems().addAll(
                    editorCommand(frame, "放大", null, () -> area.editor().zoomIn()),
                    editorCommand(frame, "缩小", null, () -> area.editor().zoomOut()),
                    editorCommand(frame, "重置缩放", null, () -> area.editor().resetZoom()));
            frame.setMenuItems("查看", split, zoom, new SeparatorMenuItem(),
                    command("展开右侧信息栏", null, frame::expandDisplayArea),
                    command("收起右侧信息栏", null, frame::collapseDisplayArea),
                    command("恢复默认宽度", null, frame::restoreDefaultDisplayAreaWidth));
            frame.setMenuItems("帮助",
                    command("菜单与悬停提示演示…", null, () -> HoverComponentsDemo.show(stage)));
            frame.setMenuItems("终端",
                    editorCommand(frame, "新建 PowerShell 终端", "Shortcut+Shift+T", interactionArea::newTerminal),
                    editorCommand(frame, "关闭当前面板", null, () -> interactionArea.closeItem(interactionArea.activeItem())));
        }

        private MenuItem editorCommand(AppFrame frame, String title, String shortcut, Runnable action) {
            return command(title, shortcut, () -> {
                frame.showEditorWorkspace();
                action.run();
            });
        }

        private MenuItem command(String title, String shortcut, Runnable action) {
            MenuItem item = new MenuItem(title);
            if (shortcut != null) item.setAccelerator(KeyCombination.keyCombination(shortcut));
            item.setOnAction(event -> action.run());
            return item;
        }
    }
}
