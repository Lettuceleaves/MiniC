package craken.ui.component.editor;

/** 编辑器断点位置。line 为 1-based，offset 为文档字符偏移。 */
public record UiCodeEditorBreakpoint(int line, int offset) {
}
