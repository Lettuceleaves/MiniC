package minic.uilocal;

import javafx.application.Application;

/**
 * MiniC Visual Workbench 的普通 Java main 启动器。
 *
 * <p>不要直接把 Gradle JavaExec 指向继承 {@link Application} 的类型，否则 Java 启动器会按
 * JavaFX 模块启动路径处理，并在 classpath 运行方式下报告缺少 JavaFX runtime。</p>
 */
public final class MiniCWorkbenchLauncher {
    private MiniCWorkbenchLauncher() {
    }

    /**
     * 启动 JavaFX 应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        Application.launch(MiniCWorkbenchApp.class, args);
    }
}
