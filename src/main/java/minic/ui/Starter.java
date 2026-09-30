package minic.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import minic.ui.component.UiStyles;
import minic.ui.page.WorkbenchPage;

/**
 * MiniC UI 启动器。
 *
 * <p>该类只负责启动 JavaFX Runtime。UI 结构、状态和编译/调试业务
 * 应由后续的 UI 类承担，不应写入 Starter。</p>
 */
public final class Starter {
    private Starter() {
    }

    public static void main(String[] args) {
        Application.launch(UiApplication.class, args);
    }

    /** JavaFX 生命周期入口，由 JavaFX Runtime 实例化。 */
    public static final class UiApplication extends Application {
        @Override
        public void start(Stage primaryStage) {
            WorkbenchPage root = new WorkbenchPage();
            Scene scene = new Scene(root, 1280, 800);
            UiStyles.install(scene);

            primaryStage.setTitle("MiniC");
            primaryStage.setMinWidth(960);
            primaryStage.setMinHeight(600);
            primaryStage.setScene(scene);
            primaryStage.show();
        }
    }
}
