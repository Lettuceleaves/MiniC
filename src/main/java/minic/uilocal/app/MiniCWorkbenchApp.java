package minic.uilocal;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import minic.color.ThemeManager;
import minic.settings.MiniCSettings;

/**
 * MiniC Visual Workbench 的 JavaFX 应用入口。
 */
public final class MiniCWorkbenchApp extends Application {
    /**
     * UI 窗口标题。
     */
    public static final String TITLE = "MiniC 可视化工作台";

    /**
     * 默认窗口宽度。
     */
    public static final double DEFAULT_WIDTH = 1280;

    /**
     * 默认窗口高度。
     */
    public static final double DEFAULT_HEIGHT = 760;

    /**
     * 启动 JavaFX UI。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * 创建并展示首版工作台窗口。
     *
     * @param stage 主窗口
     */
    @Override
    public void start(Stage stage) {
        MiniCSettings.load();
        MiniCWorkbenchShell shell = new MiniCWorkbenchShell(new MiniCWorkbenchViewModel());
        Scene scene = new Scene(shell.createRoot(), DEFAULT_WIDTH, DEFAULT_HEIGHT);
        ThemeManager.bind(scene);

        stage.setTitle(TITLE);
        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(600);
        stage.show();
    }
}
