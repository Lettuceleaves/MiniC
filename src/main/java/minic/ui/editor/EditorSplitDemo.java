package minic.ui.editor;

import javafx.geometry.Orientation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 使用 --split-demo 启动时展示递归分屏；每个窗格都可以继续独立拆分。 */
public final class EditorSplitDemo {
    private EditorSplitDemo() {
    }

    public static void show(EditorArea area) {
        new EditorSplitDemo().populate(area);
    }

    private void populate(EditorArea area) {
        try {
            Path directory = Files.createTempDirectory("minic-split-demo-");
            Path mainPath = directory.resolve("main.c");
            Path mathPath = directory.resolve("math.c");
            Path loopPath = directory.resolve("loop.c");
            Files.writeString(mainPath, "int main() { return 0; }\n");
            Files.writeString(mathPath, """
                // 右侧编辑器
                int add(int a, int b) {
                    return a + b;
                }

                int square(int x) {
                    return x * x;
                }
                """);
            Files.writeString(loopPath, """
                // 再次向下拆分
                int sum(int n) {
                    int value = 0;
                    while (n > 0) {
                        value += n;
                        n--;
                    }
                    return value;
                }
                """);
            EditorFile main = area.openFile(mainPath);
            EditorFile right = area.openFileBeside(mathPath, main, Orientation.HORIZONTAL);
            area.openFileBeside(loopPath, right, Orientation.VERTICAL);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot create split demo files", failure);
        }
    }
}
