package minic.ui.component.editor;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class UiCodeEditorShortcutsTest {
    @Test
    void installsTheDefaultZoomShortcuts() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var fixture = new Fixture();

            assertEquals(ctrl(KeyCode.EQUALS),
                    fixture.shortcuts.mappings().get(UiCodeEditorAction.ZOOM_IN));
            assertEquals(ctrl(KeyCode.MINUS),
                    fixture.shortcuts.mappings().get(UiCodeEditorAction.ZOOM_OUT));
            assertEquals(ctrl(KeyCode.NUMPAD0),
                    fixture.shortcuts.mappings().get(UiCodeEditorAction.RESET_ZOOM));
            fixture.assertDefaultZoomBindings();
        });
    }

    @Test
    void defaultKeysAndAliasesInvokeTheCorrespondingCallbacks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var fixture = new Fixture();

            fixture.press(ctrlKey(KeyEvent.VK_EQUALS));
            fixture.press(ctrlShiftKey(KeyEvent.VK_EQUALS));
            fixture.press(ctrlKey(KeyEvent.VK_ADD));
            fixture.press(ctrlKey(KeyEvent.VK_MINUS));
            fixture.press(ctrlShiftKey(KeyEvent.VK_MINUS));
            fixture.press(ctrlKey(KeyEvent.VK_SUBTRACT));
            fixture.press(ctrlKey(KeyEvent.VK_NUMPAD0));

            assertArrayEquals(new int[]{3, 3, 1}, fixture.calls);
        });
    }

    @Test
    void remappingRemovesOldKeysAndAliasesAndActivatesNewKeys() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var fixture = new Fixture();
            var replacements = new EnumMap<>(fixture.shortcuts.mappings());
            replacements.put(UiCodeEditorAction.ZOOM_IN, ctrl(KeyCode.F2));
            replacements.put(UiCodeEditorAction.ZOOM_OUT, ctrl(KeyCode.F3));
            replacements.put(UiCodeEditorAction.RESET_ZOOM, ctrl(KeyCode.F4));

            fixture.shortcuts.replace(replacements);

            fixture.assertDefaultZoomBindingsRemoved();
            fixture.press(ctrlKey(KeyEvent.VK_F2));
            fixture.press(ctrlKey(KeyEvent.VK_F3));
            fixture.press(ctrlKey(KeyEvent.VK_F4));
            assertArrayEquals(new int[]{1, 1, 1}, fixture.calls);
        });
    }

    @Test
    void clearingMappingsRemovesDefaultZoomKeysAndAliases() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var fixture = new Fixture();

            fixture.shortcuts.replace(Map.of());

            assertEquals(Map.of(), fixture.shortcuts.mappings());
            fixture.assertDefaultZoomBindingsRemoved();
        });
    }

    @Test
    void restoringDefaultsRestoresZoomAliases() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var fixture = new Fixture();
            var defaults = fixture.shortcuts.mappings();
            fixture.shortcuts.replace(Map.of());

            fixture.shortcuts.replace(defaults);

            fixture.assertDefaultZoomBindings();
            fixture.press(ctrlKey(KeyEvent.VK_ADD));
            fixture.press(ctrlKey(KeyEvent.VK_SUBTRACT));
            assertArrayEquals(new int[]{1, 1, 0}, fixture.calls);
        });
    }

    @Test
    void explicitMappingTakesPrecedenceOverADefaultZoomAlias() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var fixture = new Fixture();
            var replacements = new EnumMap<>(fixture.shortcuts.mappings());
            replacements.put(UiCodeEditorAction.ZOOM_OUT, ctrl(KeyCode.ADD));

            fixture.shortcuts.replace(replacements);
            fixture.press(ctrlKey(KeyEvent.VK_ADD));
            fixture.press(ctrlKey(KeyEvent.VK_EQUALS));

            fixture.assertUnbound(ctrlKey(KeyEvent.VK_SUBTRACT), UiCodeEditorAction.ZOOM_OUT);
            assertArrayEquals(new int[]{1, 1, 0}, fixture.calls);
        });
    }

    private static KeyCombination ctrl(KeyCode code) {
        return new KeyCodeCombination(code, KeyCombination.CONTROL_DOWN);
    }

    private static KeyStroke ctrlKey(int code) {
        return KeyStroke.getKeyStroke(code, InputEvent.CTRL_DOWN_MASK);
    }

    private static KeyStroke ctrlShiftKey(int code) {
        return KeyStroke.getKeyStroke(code,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
    }

    private static final class Fixture {
        private final RSyntaxTextArea textArea = new RSyntaxTextArea();
        private final int[] calls = new int[3];
        private final UiCodeEditorShortcuts shortcuts;

        private Fixture() {
            var pane = new RTextScrollPane(textArea);
            var breakpoints = new UiEditorBreakpoints(textArea, pane, clickable -> {});
            shortcuts = new UiCodeEditorShortcuts(textArea, breakpoints,
                    () -> calls[0]++, () -> calls[1]++, () -> calls[2]++);
        }

        private void press(KeyStroke stroke) {
            Object actionName = textArea.getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
            var action = textArea.getActionMap().get(actionName);
            assertNotNull(action, () -> "No action bound to " + stroke);
            action.actionPerformed(new ActionEvent(textArea, ActionEvent.ACTION_PERFORMED,
                    actionName.toString()));
        }

        private void assertDefaultZoomBindings() {
            assertBound(ctrlKey(KeyEvent.VK_EQUALS), UiCodeEditorAction.ZOOM_IN);
            assertBound(ctrlShiftKey(KeyEvent.VK_EQUALS), UiCodeEditorAction.ZOOM_IN);
            assertBound(ctrlKey(KeyEvent.VK_ADD), UiCodeEditorAction.ZOOM_IN);
            assertBound(ctrlKey(KeyEvent.VK_MINUS), UiCodeEditorAction.ZOOM_OUT);
            assertBound(ctrlShiftKey(KeyEvent.VK_MINUS), UiCodeEditorAction.ZOOM_OUT);
            assertBound(ctrlKey(KeyEvent.VK_SUBTRACT), UiCodeEditorAction.ZOOM_OUT);
            assertBound(ctrlKey(KeyEvent.VK_NUMPAD0), UiCodeEditorAction.RESET_ZOOM);
        }

        private void assertDefaultZoomBindingsRemoved() {
            assertUnbound(ctrlKey(KeyEvent.VK_EQUALS), UiCodeEditorAction.ZOOM_IN);
            assertUnbound(ctrlShiftKey(KeyEvent.VK_EQUALS), UiCodeEditorAction.ZOOM_IN);
            assertUnbound(ctrlKey(KeyEvent.VK_ADD), UiCodeEditorAction.ZOOM_IN);
            assertUnbound(ctrlKey(KeyEvent.VK_MINUS), UiCodeEditorAction.ZOOM_OUT);
            assertUnbound(ctrlShiftKey(KeyEvent.VK_MINUS), UiCodeEditorAction.ZOOM_OUT);
            assertUnbound(ctrlKey(KeyEvent.VK_SUBTRACT), UiCodeEditorAction.ZOOM_OUT);
            assertUnbound(ctrlKey(KeyEvent.VK_NUMPAD0), UiCodeEditorAction.RESET_ZOOM);
        }

        private void assertBound(KeyStroke stroke, UiCodeEditorAction action) {
            assertEquals(action.swingActionName(),
                    textArea.getInputMap(JComponent.WHEN_FOCUSED).get(stroke));
        }

        private void assertUnbound(KeyStroke stroke, UiCodeEditorAction action) {
            assertNotEquals(action.swingActionName(),
                    textArea.getInputMap(JComponent.WHEN_FOCUSED).get(stroke));
        }
    }
}
