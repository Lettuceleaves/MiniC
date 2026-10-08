package craken.ui.interaction.cases;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import craken.ui.component.feedback.UiTooltip;
import craken.ui.component.input.UiTextArea;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 一个用例标签的内容：上方左右两半分别是程序标准输入和预期结果，下方是本次运行的输出。
 *
 * <p>输入区（左右两半共用一种状态）有两种状态。输入中：可编辑并获得焦点，按 Esc 退出；
 * 就绪：只读、不参与焦点遍历，按键回到编辑器。点击任一输入区或“编辑输入”进入输入状态，
 * 文本始终保留。新标签第一次被选中时直接进入输入状态，其余时候切换列表不抢占键盘焦点。</p>
 *
 * <p>运行由 {@link craken.ui.run.RunController} 驱动：点击运行时清空上一轮输出并等待编译，
 * 编译成功后把那时捕获的输入快照写入程序标准输入，输出实时追加到下方；程序退出时把输出与
 * 运行开始时快照的预期结果比较（统一行尾、忽略每行尾随空白和末尾空行），通过或不匹配写入
 * {@link #verdictProperty()} 供列表着色。同一用例的新一轮运行先结束上一轮进程；运行代号让
 * 迟到的完成或取消回调只能更新属于自己的那一轮。</p>
 *
 * <p>预置输入按终端键入语义送入：缺少结尾换行时自动补一个回车，写完保持标准输入打开、
 * 不自动发送 EOF，程序继续读取时会保持运行；“发送 EOF”相当于终端里按 Ctrl+Z 回车。
 * 预期结果为空（或只有空白）时不做判定，列表保持中性配色。</p>
 */
public final class CasePanel extends BorderPane implements AutoCloseable {
    /** 一次运行的结果判定；{@link #NONE} 表示尚未比较或没有判定（预期为空、停止、取消、失败）。 */
    public enum Verdict { NONE, PASSED, FAILED }

    private static final String STDERR_PREFIX = "stderr: ";
    private static final String EDITING_STYLE = "case-input-active";
    private static final String PASSED_STYLE = "case-verdict-passed";
    private static final String FAILED_STYLE = "case-verdict-failed";
    /** 输出只保留最近的内容：程序失控刷屏时也不撑爆界面内存，与终端的有限回滚一致。 */
    private static final int OUTPUT_LIMIT = 1_000_000;

    private final Path sourcePath;
    private final Label status = new Label("就绪");
    private final Label verdictLabel = new Label();
    private final Label inputHint = new Label();
    private final UiTextArea input = new UiTextArea();
    private final UiTextArea expected = new UiTextArea();
    private final UiTextArea output = new UiTextArea();
    private final ReadOnlyObjectWrapper<Verdict> verdict = new ReadOnlyObjectWrapper<>(Verdict.NONE);
    private Button editInput;
    private Button clearOutput;
    private Button sendEof;
    private Button stop;
    private Button delete;
    private Runnable onDeleteRequest = () -> { };
    private RunSession session;
    private int generation;
    private boolean awaiting;
    private boolean editing;
    private boolean fresh = true;
    private boolean closed;
    private boolean stdoutLineStart = true;
    private boolean stderrLineStart = true;
    private String expectedSnapshot = "";

    public CasePanel(Path sourcePath) {
        this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath").toAbsolutePath().normalize();
        setMinSize(0, 0);
        setFocusTraversable(true);
        getStyleClass().addAll("terminal-panel", "case-panel");
        // 输入状态属于整个用例面板：焦点在输入区或工具栏按钮上时，Esc 都退出输入状态。
        addEventFilter(KeyEvent.KEY_PRESSED, this::filterInputKey);
        setTop(createToolbar());
        setCenter(createSections());
        updateInputChrome();
        updateVerdictChrome();
    }

    public Path sourcePath() { return sourcePath; }

    /** 当前预置输入文本；运行开始时按此文本快照。 */
    public String inputText() { return Objects.requireNonNullElse(input.getText(), ""); }

    /** 替换预置输入文本；不改变输入状态。 */
    public void setInputText(String text) {
        input.setText(Objects.requireNonNullElse(text, ""));
    }

    public String outputText() { return Objects.requireNonNullElse(output.getText(), ""); }

    /** 当前预期结果文本；运行开始时按此文本快照，用于和输出比较。 */
    public String expectedText() { return Objects.requireNonNullElse(expected.getText(), ""); }

    /** 替换预期结果文本；不改变输入状态。 */
    public void setExpectedText(String text) {
        expected.setText(Objects.requireNonNullElse(text, ""));
    }

    /** 最近一次运行的判定；{@link Verdict#NONE} 表示尚未比较或没有判定。 */
    public Verdict verdict() { return verdict.get(); }

    public ReadOnlyObjectProperty<Verdict> verdictProperty() { return verdict.getReadOnlyProperty(); }

    public String statusText() { return status.getText(); }

    public boolean isEditingInput() { return editing; }

    /** 当前是否有本用例的程序正在运行。 */
    public boolean isRunning() { return session != null; }

    public boolean isClosed() { return closed; }

    /** 删除请求由容器执行（关闭本用例标签）；回调始终位于 JavaFX 线程。 */
    public void setOnDeleteRequest(Runnable action) {
        onDeleteRequest = Objects.requireNonNull(action, "action");
    }

    /** 进入输入状态：程序输入与预期结果都可编辑并获得焦点；面板未挂载时只改变状态。 */
    public void enterInputState() {
        enterInputState(input);
    }

    private void enterInputState(Node target) {
        if (closed) return;
        editing = true;
        fresh = false;
        input.setEditable(true);
        expected.setEditable(true);
        input.setFocusTraversable(true);
        expected.setFocusTraversable(true);
        updateInputChrome();
        if (getScene() != null) target.requestFocus();
    }

    /** 退出输入状态：保留文本，两个输入区只读且不参与焦点遍历，键盘回到编辑器。 */
    public void exitInputState() {
        if (closed || !editing) return;
        editing = false;
        input.setEditable(false);
        expected.setEditable(false);
        input.setFocusTraversable(false);
        expected.setFocusTraversable(false);
        updateInputChrome();
        if (getScene() != null && ownsInputFocus()) requestFocus();
    }

    /**
     * 新一次运行开始：结束上一轮进程，清空输出并等待编译。
     *
     * @return 本次运行的代号，交给后续 {@link #run(int, Path, Path, String)} 等方法校验
     */
    public int beginRun() {
        if (closed) return generation;
        closeSession();
        output.clear();
        stdoutLineStart = true;
        stderrLineStart = true;
        awaiting = true;
        generation++;
        expectedSnapshot = expectedText();
        setVerdict(Verdict.NONE);
        status.setText("等待编译…");
        stop.setDisable(true);
        sendEof.setDisable(true);
        return generation;
    }

    /** 编译成功：用预置输入启动独立程序；运行代号不匹配（已开始新一轮）时忽略。 */
    public void run(int generation, Path workingDirectory, Path executable, String inputText) {
        if (closed || generation != this.generation || !awaiting) return;
        awaiting = false;
        RunSession next = new RunSession(workingDirectory, executable, Objects.requireNonNullElse(inputText, ""));
        session = next;
        status.setText("运行中…");
        stop.setDisable(false);
        sendEof.setDisable(false);
        next.start();
    }

    /** 本轮没有启动程序（编译失败、编译取消等）；已经结束的用例保留自己的结果。 */
    public void notRun(int generation, String description) {
        if (closed || generation != this.generation || !awaiting) return;
        awaiting = false;
        status.setText(Objects.requireNonNull(description, "description"));
        setVerdict(Verdict.NONE);
    }

    /** 本轮运行已取消：结束仍在运行的进程；已经退出或未运行的用例不改变状态。 */
    public void cancelRun(int generation) {
        if (closed || generation != this.generation) return;
        if (session != null) {
            endSession("已取消");
            return;
        }
        if (awaiting) {
            awaiting = false;
            status.setText("已取消，未运行");
            setVerdict(Verdict.NONE);
        }
    }

    /** 停止本用例正在运行的程序；没有运行中的程序时不做任何事。 */
    public void stopRun() {
        if (closed || session == null) return;
        endSession("已停止");
    }

    /** 模拟终端里按 Ctrl+Z 回车：关闭标准输入，让等待输入的程序读到 EOF 并继续执行。 */
    public void sendEof() {
        if (closed || session == null) return;
        session.sendEof();
        // 一次运行只发送一次 EOF；程序读到 EOF 后可能还会继续输出，状态保持“运行中…”。
        sendEof.setDisable(true);
    }

    /** 请求删除本用例标签：容器关闭标签并结束仍在运行的进程；预置输入与输出一并丢弃。 */
    public void requestDelete() {
        if (closed) return;
        onDeleteRequest.run();
    }

    /** 清空输出历史；预置输入保持不变。 */
    public void clearOutput() {
        if (closed) return;
        output.clear();
        stdoutLineStart = true;
        stderrLineStart = true;
    }

    /** 列表项挂载：内容已经就绪，没有需要提前准备的动作。 */
    public void start() { }

    /** 用户显式选择该标签：新标签直接进入输入状态，其余保持输入状态下的焦点。 */
    public void activate() {
        if (closed) return;
        if (fresh) {
            fresh = false;
            enterInputState();
            return;
        }
        if (editing) input.requestFocus();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        editing = false;
        input.setEditable(false);
        input.setFocusTraversable(false);
        closeSession();
        awaiting = false;
        status.setText("已关闭");
        setVerdict(Verdict.NONE);
        stop.setDisable(true);
        sendEof.setDisable(true);
        editInput.setDisable(true);
        clearOutput.setDisable(true);
        delete.setDisable(true);
    }

    private HBox createToolbar() {
        Label title = new Label("用例 · " + sourcePath.getFileName());
        title.setId("case-title");
        title.getStyleClass().add("terminal-title");
        // 工具栏按钮较多，窄面板下先压缩标题（省略号）而不是把按钮挤出可视区。
        title.setMinWidth(0);
        title.setTooltip(new UiTooltip("用例绑定的源文件", sourcePath.toString(), ""));
        status.setId("case-status");
        status.getStyleClass().add("terminal-status");
        verdictLabel.setId("case-verdict");
        verdictLabel.getStyleClass().add("case-verdict");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        editInput = action("编辑输入", "进入输入状态，按 Esc 退出", "case-edit-input", this::enterInputState);
        clearOutput = action("清空", "清空本用例的输出", "case-clear-output", this::clearOutput);
        sendEof = action("发送 EOF", "关闭标准输入（相当于终端里按 Ctrl+Z 回车），等待输入的程序会读到 EOF 并结束",
                "case-send-eof", this::sendEof);
        stop = action("停止", "停止这个用例正在运行的程序", "case-stop", this::stopRun);
        delete = action("删除", "删除本用例标签，预置输入与运行输出一并丢弃", "case-delete", this::requestDelete);
        sendEof.setDisable(true);
        stop.setDisable(true);
        HBox toolbar = new HBox(8, title, status, verdictLabel, spacer, editInput, clearOutput, sendEof, stop, delete);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 8, 0, 12));
        toolbar.setMinHeight(32);
        toolbar.setPrefHeight(32);
        toolbar.getStyleClass().add("interaction-toolbar");
        return toolbar;
    }

    private SplitPane createSections() {
        configureCaseArea(input, "case-input", "在此输入程序的标准输入；像终端一样送入，不会自动发送 EOF",
                "用例标准输入", "case-input-area");
        configureCaseArea(expected, "case-expected", "在此输入期望的程序输出，运行时用于判定",
                "用例预期结果", "case-expected-area");
        input.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) enterInputState(input);
        });
        expected.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) enterInputState(expected);
        });
        VBox.setVgrow(input, Priority.ALWAYS);
        VBox.setVgrow(expected, Priority.ALWAYS);

        output.setId("case-output");
        output.setEditable(false);
        output.setWrapText(false);
        output.setPromptText("运行后在此显示程序输出");
        output.setAccessibleText("用例程序输出");
        output.getStyleClass().add("case-output-area");
        VBox.setVgrow(output, Priority.ALWAYS);

        inputHint.setId("case-input-hint");
        inputHint.getStyleClass().add("case-section-hint");
        SplitPane inputs = new SplitPane(section("程序输入", inputHint, input), section("预期结果", null, expected));
        inputs.setId("case-input-split");
        inputs.setOrientation(Orientation.HORIZONTAL);
        inputs.getStyleClass().addAll("app-split-pane", "app-horizontal-split", "case-input-split");
        inputs.setMinSize(0, 0);
        inputs.setDividerPositions(0.5);
        SplitPane split = new SplitPane(inputs, section("输出", null, output));
        split.setId("case-split");
        split.setOrientation(Orientation.VERTICAL);
        split.getStyleClass().addAll("app-split-pane", "case-split");
        split.setMinSize(0, 0);
        split.setDividerPositions(0.5);
        return split;
    }

    private static void configureCaseArea(UiTextArea area, String id, String prompt,
                                          String accessible, String styleClass) {
        area.setId(id);
        area.setEditable(false);
        area.setFocusTraversable(false);
        area.setWrapText(true);
        area.setPromptText(prompt);
        area.setAccessibleText(accessible);
        area.getStyleClass().add(styleClass);
    }

    private static VBox section(String title, Label hint, UiTextArea area) {
        Label label = new Label(title);
        label.getStyleClass().add("case-section-title");
        HBox header = new HBox(8, label);
        if (hint != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            header.getChildren().addAll(spacer, hint);
        }
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 8, 0, 12));
        header.setMinHeight(22);
        header.setPrefHeight(22);
        header.getStyleClass().add("case-section-header");
        VBox box = new VBox(header, area);
        box.setMinSize(0, 0);
        box.setMinHeight(24);
        box.getStyleClass().add("case-section");
        return box;
    }

    private void filterInputKey(KeyEvent event) {
        if (event.getCode() != KeyCode.ESCAPE || !editing) return;
        event.consume();
        exitInputState();
    }

    private void updateInputChrome() {
        inputHint.setText(editing ? "输入中 · 按 Esc 结束输入" : "点击输入区进入输入状态");
        if (editing) {
            if (!inputHint.getStyleClass().contains(EDITING_STYLE)) inputHint.getStyleClass().add(EDITING_STYLE);
        } else {
            inputHint.getStyleClass().remove(EDITING_STYLE);
        }
        editInput.setDisable(editing);
    }

    private boolean ownsInputFocus() {
        if (getScene() == null) return false;
        for (var node = getScene().getFocusOwner(); node != null; node = node.getParent()) {
            if (node == input || node == expected) return true;
        }
        return false;
    }

    private void endSession(String description) {
        RunSession current = session;
        session = null;
        if (current != null) current.close();
        status.setText(description);
        setVerdict(Verdict.NONE);
        stop.setDisable(true);
        sendEof.setDisable(true);
    }

    private void closeSession() {
        RunSession current = session;
        session = null;
        if (current != null) current.close();
    }

    private void completeSession(RunSession owner, Integer exitCode, String failure) {
        if (closed || session != owner) return;
        session = null;
        owner.close();
        stop.setDisable(true);
        sendEof.setDisable(true);
        if (failure != null) {
            status.setText("运行失败：" + failure);
            setVerdict(Verdict.NONE);
            return;
        }
        status.setText("已退出（退出码 " + exitCode + "）");
        setVerdict(judge(expectedSnapshot, outputText()));
    }

    /** 预期为空时不做判定；否则统一行尾并忽略每行尾随空白、末尾空行后比较。 */
    private static Verdict judge(String expectedText, String actualText) {
        if (expectedText == null || expectedText.isBlank()) return Verdict.NONE;
        return normalizeForComparison(expectedText).equals(normalizeForComparison(actualText))
                ? Verdict.PASSED : Verdict.FAILED;
    }

    private static String normalizeForComparison(String text) {
        String[] lines = Objects.requireNonNullElse(text, "")
                .replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int end = lines.length;
        while (end > 0 && lines[end - 1].stripTrailing().isEmpty()) end--;
        StringBuilder normalized = new StringBuilder();
        for (int index = 0; index < end; index++) {
            if (index > 0) normalized.append('\n');
            normalized.append(lines[index].stripTrailing());
        }
        return normalized.toString();
    }

    private void setVerdict(Verdict next) {
        verdict.set(next == null ? Verdict.NONE : next);
        updateVerdictChrome();
    }

    private void updateVerdictChrome() {
        verdictLabel.getStyleClass().removeAll(PASSED_STYLE, FAILED_STYLE);
        switch (verdict.get()) {
            case PASSED -> {
                verdictLabel.setText("通过");
                verdictLabel.getStyleClass().add(PASSED_STYLE);
            }
            case FAILED -> {
                verdictLabel.setText("不匹配");
                verdictLabel.getStyleClass().add(FAILED_STYLE);
            }
            case NONE -> verdictLabel.setText("");
        }
    }

    private void appendOutput(String text, boolean stderr) {
        if (closed || text.isEmpty()) return;
        boolean lineStart = stderr ? stderrLineStart : stdoutLineStart;
        StringBuilder rendered = new StringBuilder(text.length() + 16);
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\r') {
                if (index + 1 < text.length() && text.charAt(index + 1) == '\n') continue;
                // 单独的回车在纯文本视图里按换行显示，保留进度型输出仍能读。
                character = '\n';
            }
            if (stderr && lineStart && character != '\n') rendered.append(STDERR_PREFIX);
            rendered.append(character);
            lineStart = character == '\n';
        }
        if (stderr) stderrLineStart = lineStart;
        else stdoutLineStart = lineStart;
        output.appendText(rendered.toString());
        if (output.getLength() > OUTPUT_LIMIT) output.deleteText(0, output.getLength() - OUTPUT_LIMIT / 2);
        output.positionCaret(output.getLength());
        output.setScrollTop(Double.MAX_VALUE);
    }

    /** 一次运行的界面状态与进程句柄；迟到回调只更新仍属于自己的那次运行。 */
    private final class RunSession implements CaseRun.Listener, AutoCloseable {
        private final Path workingDirectory;
        private final Path executable;
        private final String inputText;
        private CaseRun process;

        RunSession(Path workingDirectory, Path executable, String inputText) {
            this.workingDirectory = workingDirectory;
            this.executable = executable;
            this.inputText = inputText;
        }

        void start() {
            process = new CaseRun(workingDirectory, executable, inputText, this);
            process.start();
        }

        @Override public void stdout(String text) { deliver(() -> appendOutput(text, false)); }

        @Override public void stderr(String text) { deliver(() -> appendOutput(text, true)); }

        @Override public void exited(int exitCode) {
            deliver(() -> completeSession(this, exitCode, null));
        }

        @Override public void failed(String message) {
            deliver(() -> completeSession(this, null, message));
        }

        void sendEof() {
            CaseRun current = process;
            if (current != null) current.sendEof();
        }

        private void deliver(Runnable action) {
            Platform.runLater(() -> {
                if (!closed && session == this) action.run();
            });
        }

        @Override public void close() {
            CaseRun current = process;
            process = null;
            if (current != null) current.close();
        }
    }

    private static Button action(String text, String help, String id, Runnable handler) {
        Button button = new Button(text);
        button.setId(id);
        button.setPadding(new Insets(2, 6, 2, 6));
        button.setMinHeight(24);
        button.setPrefHeight(24);
        button.getStyleClass().add("interaction-action");
        button.setAccessibleText(help);
        button.setTooltip(new UiTooltip(help));
        button.setOnAction(event -> handler.run());
        return button;
    }
}
