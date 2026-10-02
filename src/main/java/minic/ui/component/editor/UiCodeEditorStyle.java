package minic.ui.component.editor;

import minic.ui.component.swing.UiSwingScrollBarUi;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Style;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
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
        useNormalStylesForLexicalErrors(textArea);
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
        UiSwingScrollBarUi.install(
                scrollPane.getVerticalScrollBar(), background, mutedForeground, foreground);
        UiSwingScrollBarUi.install(
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

    /** 编辑阶段不由词法器绘制错误；未完成的输入仍使用对应的普通语法样式。 */
    private static void useNormalStylesForLexicalErrors(RSyntaxTextArea textArea) {
        SyntaxScheme scheme = textArea.getSyntaxScheme();
        scheme.setStyle(TokenTypes.ERROR_IDENTIFIER,
                (Style) scheme.getStyle(TokenTypes.IDENTIFIER).clone());
        scheme.setStyle(TokenTypes.ERROR_NUMBER_FORMAT,
                (Style) scheme.getStyle(TokenTypes.LITERAL_NUMBER_DECIMAL_INT).clone());
        scheme.setStyle(TokenTypes.ERROR_STRING_DOUBLE,
                (Style) scheme.getStyle(TokenTypes.LITERAL_STRING_DOUBLE_QUOTE).clone());
        scheme.setStyle(TokenTypes.ERROR_CHAR,
                (Style) scheme.getStyle(TokenTypes.LITERAL_CHAR).clone());
        textArea.setSyntaxScheme(scheme);
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
