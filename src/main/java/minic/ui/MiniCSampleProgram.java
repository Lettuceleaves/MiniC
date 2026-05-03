package minic.ui;

import java.util.Objects;

/**
 * UI 内置样例程序。
 *
 * @param name 样例名称
 * @param source 源码文本
 */
public record MiniCSampleProgram(String name, String source) {
    public MiniCSampleProgram {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(source, "source");
    }
}
