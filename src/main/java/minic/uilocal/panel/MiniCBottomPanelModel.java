package minic.uilocal;

import java.util.List;
import java.util.Objects;

/**
 * 底部面板展示数据。
 *
 * @param problems 问题列表
 * @param output 输出列表
 * @param terminal 终端日志列表
 */
public record MiniCBottomPanelModel(
        List<String> problems,
        List<String> output,
        List<String> terminal
) {
    public MiniCBottomPanelModel {
        Objects.requireNonNull(problems, "problems");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(terminal, "terminal");
        problems = List.copyOf(problems);
        output = List.copyOf(output);
        terminal = List.copyOf(terminal);
    }
}
