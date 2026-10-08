package craken.ui.editor.completion;

/** 补全候选项的来源类别，用于测试断言和光标浮窗条目标识。 */
public enum CompletionKind {
    /** 语言关键词。 */
    KEYWORD,
    /** 当前源码中出现过的标识符。 */
    VARIABLE,
    /** include 指令的头文件名。 */
    HEADER
}
