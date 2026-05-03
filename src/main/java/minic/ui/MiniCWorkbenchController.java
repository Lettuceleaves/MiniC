package minic.ui;

import minic.uiapi.UiControlResultDto;

import java.util.Objects;

/**
 * 工作台控制动作封装。
 */
public final class MiniCWorkbenchController {
    private final MiniCWorkbenchViewModel viewModel;

    /**
     * 创建工作台控制器。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCWorkbenchController(MiniCWorkbenchViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

    /**
     * 启动默认样例会话。
     */
    public void startDefaultSession() {
        MiniCSampleProgram sample = MiniCSamplePrograms.defaultSample();
        viewModel.loadSource(sample.name(), sample.source());
        viewModel.startSession();
    }

    /**
     * 执行下一步。
     *
     * @return 控制结果
     */
    public UiControlResultDto next() {
        return viewModel.next();
    }

    /**
     * 跳转到下一编译环节。
     *
     * @return 控制结果
     */
    public UiControlResultDto nextStage() {
        return viewModel.nextStage();
    }
}
