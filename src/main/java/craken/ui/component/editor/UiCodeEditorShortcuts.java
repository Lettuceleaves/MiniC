package craken.ui.component.editor;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** JavaFX 快捷键配置与 Swing InputMap 之间的内部适配器。 */
final class UiCodeEditorShortcuts {
    private static final String DISABLED_ACTION = "craken-disabled-editor-action";
    private static final Map<UiCodeEditorAction, KeyCombination> DEFAULT_MAPPINGS =
            defaultMappings();
    private static final Map<UiCodeEditorAction, List<KeyCombination>> DEFAULT_ALIASES = Map.of(
            UiCodeEditorAction.ZOOM_IN, List.of(
                    new KeyCodeCombination(KeyCode.EQUALS,
                            KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                    ctrl(KeyCode.ADD)
            ),
            UiCodeEditorAction.ZOOM_OUT, List.of(
                    new KeyCodeCombination(KeyCode.MINUS,
                            KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                    ctrl(KeyCode.SUBTRACT)
            )
    );

    private final RSyntaxTextArea textArea;
    private final EnumMap<UiCodeEditorAction, KeyCombination> mappings =
            new EnumMap<>(UiCodeEditorAction.class);

    UiCodeEditorShortcuts(
            RSyntaxTextArea textArea,
            UiEditorBreakpoints breakpointGutter,
            Runnable zoomIn,
            Runnable zoomOut,
            Runnable resetZoom
    ) {
        this.textArea = textArea;
        installAction(UiCodeEditorAction.TOGGLE_BREAKPOINT, breakpointGutter::toggleAtCaret);
        installAction(UiCodeEditorAction.ZOOM_IN, zoomIn);
        installAction(UiCodeEditorAction.ZOOM_OUT, zoomOut);
        installAction(UiCodeEditorAction.RESET_ZOOM, resetZoom);
        replace(DEFAULT_MAPPINGS);
    }

    Map<UiCodeEditorAction, KeyCombination> mappings() {
        return Collections.unmodifiableMap(new EnumMap<>(mappings));
    }

    void replace(Map<UiCodeEditorAction, KeyCombination> replacements) {
        Objects.requireNonNull(replacements, "replacements");
        EnumMap<UiCodeEditorAction, KeyCombination> validated =
                new EnumMap<>(UiCodeEditorAction.class);
        Set<KeyStroke> occupied = new HashSet<>();
        replacements.forEach((action, shortcut) -> {
            Objects.requireNonNull(action, "shortcut action");
            Objects.requireNonNull(shortcut, "shortcut for " + action);
            if (!(shortcut instanceof KeyCodeCombination)) {
                throw new IllegalArgumentException(
                        "editor shortcuts must use KeyCodeCombination: " + shortcut);
            }
            if (!occupied.add(toSwingKeyStroke(shortcut))) {
                throw new IllegalArgumentException("duplicate editor shortcut: " + shortcut);
            }
            validated.put(action, shortcut);
        });

        InputMap inputMap = textArea.getInputMap(JComponent.WHEN_FOCUSED);
        maskExistingBindings(inputMap);
        validated.forEach((action, shortcut) -> inputMap.put(
                toSwingKeyStroke(shortcut),
                action.swingActionName()
        ));
        // Aliases belong to the default binding and disappear when it is changed or removed.
        // Explicit user mappings take precedence if they use one of these alias keys.
        DEFAULT_ALIASES.forEach((action, aliases) -> {
            if (DEFAULT_MAPPINGS.get(action).equals(validated.get(action))) {
                for (KeyCombination alias : aliases) {
                    KeyStroke key = toSwingKeyStroke(alias);
                    if (occupied.add(key)) {
                        inputMap.put(key, action.swingActionName());
                    }
                }
            }
        });
        mappings.clear();
        mappings.putAll(validated);
    }

    private void installAction(UiCodeEditorAction action, Runnable callback) {
        Objects.requireNonNull(callback, "callback for " + action);
        ActionMap actionMap = textArea.getActionMap();
        actionMap.put(action.swingActionName(),
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        callback.run();
                    }
                });
    }

    private static Map<UiCodeEditorAction, KeyCombination> defaultMappings() {
        EnumMap<UiCodeEditorAction, KeyCombination> defaults =
                new EnumMap<>(UiCodeEditorAction.class);
        defaults.put(UiCodeEditorAction.UNDO, ctrl(KeyCode.Z));
        defaults.put(UiCodeEditorAction.REDO, ctrl(KeyCode.Y));
        defaults.put(UiCodeEditorAction.CUT, ctrl(KeyCode.X));
        defaults.put(UiCodeEditorAction.COPY, ctrl(KeyCode.C));
        defaults.put(UiCodeEditorAction.PASTE, ctrl(KeyCode.V));
        defaults.put(UiCodeEditorAction.SELECT_ALL, ctrl(KeyCode.A));
        defaults.put(UiCodeEditorAction.TOGGLE_COMMENT, ctrl(KeyCode.SLASH));
        defaults.put(
                UiCodeEditorAction.GO_TO_MATCHING_BRACKET,
                ctrl(KeyCode.CLOSE_BRACKET)
        );
        defaults.put(
                UiCodeEditorAction.TOGGLE_BREAKPOINT,
                new KeyCodeCombination(KeyCode.F9)
        );
        defaults.put(UiCodeEditorAction.ZOOM_IN, ctrl(KeyCode.EQUALS));
        defaults.put(UiCodeEditorAction.ZOOM_OUT, ctrl(KeyCode.MINUS));
        defaults.put(UiCodeEditorAction.RESET_ZOOM, ctrl(KeyCode.NUMPAD0));
        return Collections.unmodifiableMap(defaults);
    }

    private static KeyCombination ctrl(KeyCode keyCode) {
        return new KeyCodeCombination(keyCode, KeyCombination.CONTROL_DOWN);
    }

    private static KeyStroke toSwingKeyStroke(KeyCombination shortcut) {
        KeyCodeCombination combination = (KeyCodeCombination) shortcut;
        int modifiers = 0;
        modifiers |= modifier(combination.getShift(), InputEvent.SHIFT_DOWN_MASK);
        modifiers |= modifier(combination.getControl(), InputEvent.CTRL_DOWN_MASK);
        modifiers |= modifier(combination.getAlt(), InputEvent.ALT_DOWN_MASK);
        modifiers |= modifier(combination.getMeta(), InputEvent.META_DOWN_MASK);
        if (combination.getShortcut() == KeyCombination.ModifierValue.DOWN) {
            modifiers |= InputEvent.CTRL_DOWN_MASK;
        }
        return KeyStroke.getKeyStroke(combination.getCode().getCode(), modifiers);
    }

    private static int modifier(
            KeyCombination.ModifierValue value,
            int downMask
    ) {
        if (value == KeyCombination.ModifierValue.ANY) {
            throw new IllegalArgumentException(
                    "ANY modifier is not supported by editor shortcuts");
        }
        return value == KeyCombination.ModifierValue.DOWN ? downMask : 0;
    }

    private static void maskExistingBindings(InputMap localInputMap) {
        for (InputMap current = localInputMap; current != null; current = current.getParent()) {
            KeyStroke[] keys = current.keys();
            if (keys == null) {
                continue;
            }
            for (KeyStroke key : keys) {
                Object actionName = current.get(key);
                for (UiCodeEditorAction action : UiCodeEditorAction.values()) {
                    if (action.swingActionName().equals(actionName)) {
                        localInputMap.put(key, DISABLED_ACTION);
                        break;
                    }
                }
            }
        }
    }
}
