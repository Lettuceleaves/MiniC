package minic.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * MiniC Visual Workbench 的 JavaFX 应用入口。
 */
public final class MiniCWorkbenchApp extends Application {
    /**
     * UI 窗口标题。
     */
    public static final String TITLE = "MiniC Visual Workbench";

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
        MiniCWorkbenchShell shell = new MiniCWorkbenchShell(new MiniCWorkbenchViewModel());
        Scene scene = new Scene(shell.createRoot(), DEFAULT_WIDTH, DEFAULT_HEIGHT);
        scene.getStylesheets().add(Objects.requireNonNull(
                MiniCWorkbenchApp.class.getResource("/minic/ui/workbench.css"),
                "workbench.css"
        ).toExternalForm());

        stage.setTitle(TITLE);
        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(600);
        stage.show();
    }
}
