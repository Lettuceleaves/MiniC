package minic.ui;

import minic.uiapi.UiStageVisualDto;

import java.util.List;

/**
 * 根据 Assembly visual DTO 生成等宽文本行模型。
 */
public final class MiniCAssemblyTextModelFactory {
    /**
     * 创建 Assembly 文本行。
     *
     * @param visual 当前阶段 visual DTO
     * @return 文本行
     */
    public List<MiniCAssemblyTextLine> create(UiStageVisualDto visual) {
        return visual.assemblyLines().stream()
                .map(line -> new MiniCAssemblyTextLine(
                        line.lineNumber(),
                        line.text(),
                        line.section(),
                        line.label(),
                        line.kind(),
                        line.range(),
                        line.active()
                ))
                .toList();
    }
}
