package craken.ui.editor.completion;

/** 光标处的补全上下文。 */
public enum CompletionContext {
    /** 普通代码中的标识符前缀。 */
    IDENTIFIER,
    /** {@code #include <} 之后的尖括号头文件名。 */
    INCLUDE_ANGLE,
    /** {@code #include "} 之后的引号头文件名。 */
    INCLUDE_QUOTE
}
