package minic.ui.component.editor;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.BorderFactory;
import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;

/** RSyntaxTextArea 的可替换视觉配置，不包含编辑行为。 */
public record UiCodeEditorStyle(
        String syntaxThemeResource,
        Font font,
        Color background,
        Color foreground,
        Color mutedForeground,
        Color border,
        Color caret,
        Color selection,
        Color currentLine,
        Color occurrence,
        Color breakpoint,
        Color errorUnderline,
        Color rangeHighlight
) {
    void apply(RSyntaxTextArea textArea, RTextScrollPane scrollPane) {
        applyBaseTheme(textArea, syntaxThemeResource);
        textArea.setFont(font);
        textArea.setBackground(background);
        textArea.setForeground(foreground);
        textArea.setCaretColor(caret);
        textArea.setSelectionColor(selection);
        textArea.setSelectedTextColor(foreground);
        textArea.setCurrentLineHighlightColor(currentLine);
        textArea.setMarkOccurrencesColor(occurrence);
        textArea.setMatchedBracketBGColor(occurrence);
        textArea.setMatchedBracketBorderColor(selection);

        scrollPane.setBorder(BorderFactory.createLineBorder(border));
        scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
        UiCodeEditorScrollBarUi.install(
                scrollPane.getVerticalScrollBar(), background, mutedForeground, foreground);
        UiCodeEditorScrollBarUi.install(
                scrollPane.getHorizontalScrollBar(), background, mutedForeground, foreground);
        scrollPane.getViewport().setBackground(background);
        scrollPane.setBackground(background);

        Gutter gutter = scrollPane.getGutter();
        gutter.setBackground(background);
        gutter.setBorderColor(border);
        gutter.setLineNumberColor(mutedForeground);
        gutter.setCurrentLineNumberColor(foreground);
        gutter.setLineNumberFont(font.deriveFont(13f));
        gutter.setFoldBackground(background);
        gutter.setArmedFoldBackground(currentLine);
        gutter.setFoldIndicatorBackground(background);
        gutter.setFoldIndicatorForeground(mutedForeground);
        gutter.setFoldIndicatorArmedForeground(foreground);
    }

    private static void applyBaseTheme(RSyntaxTextArea textArea, String themeResource) {
        try (InputStream input = UiCodeEditorStyle.class.getResourceAsStream(themeResource)) {
            if (input == null) {
                throw new IllegalStateException(
                        "RSyntaxTextArea theme is missing: " + themeResource);
            }
            Theme.load(input).apply(textArea);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to load RSyntaxTextArea theme", exception);
        }
    }

}
