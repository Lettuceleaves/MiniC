package minic.ui.component.editor;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit;
import org.fife.ui.rtextarea.RTextAreaEditorKit;

import javax.swing.text.DefaultEditorKit;

/** 可由上层重新映射快捷键的编辑器动作。 */
public enum UiCodeEditorAction {
    UNDO(RTextAreaEditorKit.rtaUndoAction),
    REDO(RTextAreaEditorKit.rtaRedoAction),
    CUT(DefaultEditorKit.cutAction),
    COPY(DefaultEditorKit.copyAction),
    PASTE(DefaultEditorKit.pasteAction),
    SELECT_ALL(DefaultEditorKit.selectAllAction),
    TOGGLE_COMMENT(RSyntaxTextAreaEditorKit.rstaToggleCommentAction),
    GO_TO_MATCHING_BRACKET(RSyntaxTextAreaEditorKit.rstaGoToMatchingBracketAction),
    TOGGLE_BREAKPOINT("minic-toggle-breakpoint"),
    ZOOM_IN("minic-zoom-in"),
    ZOOM_OUT("minic-zoom-out"),
    RESET_ZOOM("minic-reset-zoom");

    private final String swingActionName;

    UiCodeEditorAction(String swingActionName) {
        this.swingActionName = swingActionName;
    }

    String swingActionName() {
        return swingActionName;
    }
}
