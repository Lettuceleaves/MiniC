package minic.ui.component.editor;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/** 自绘行号与断点列。所有方法均在 Swing EDT 调用。 */
final class UiEditorBreakpoints {
    private static final int NO_LINE = -1;
    private static final int HORIZONTAL_PADDING = 8;
    private static final int DOT_MARGIN = 4;
    private static final int PREVIEW_ALPHA = 112;
    private static final Color MARKED_NUMBER_FILL = Color.WHITE;

    private final RSyntaxTextArea textArea;
    private final BreakpointLineNumbers lineNumbers = new BreakpointLineNumbers();
    private final List<Position> breakpoints = new ArrayList<>();
    private final Consumer<Boolean> clickableCursorChanged;

    private int hoveredLine = NO_LINE;
    private int pressedLine = NO_LINE;
    private Font lineNumberFont = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    private Color background = new Color(0x0d1117);
    private Color lineNumberColor = new Color(0x8b949e);
    private Color currentLineNumberColor = new Color(0xe6edf3);
    private Color borderColor = new Color(0x30363d);
    private Color breakpointColor = new Color(0xf85149);

    UiEditorBreakpoints(
            RSyntaxTextArea textArea,
            RTextScrollPane scrollPane,
            Consumer<Boolean> clickableCursorChanged
    ) {
        this.textArea = textArea;
        this.clickableCursorChanged = clickableCursorChanged;
        install(scrollPane);
        installInteraction();
        installRepaintListeners();
    }

    void applyStyle(
            Font font,
            Color background,
            Color lineNumberColor,
            Color currentLineNumberColor,
            Color borderColor,
            Color breakpointColor
    ) {
        this.background = background;
        this.lineNumberColor = lineNumberColor;
        this.currentLineNumberColor = currentLineNumberColor;
        this.borderColor = borderColor;
        this.breakpointColor = breakpointColor;
        setFont(font);
    }

    void setFont(Font font) {
        lineNumberFont = font;
        lineNumbers.setFont(font);
        lineNumbers.revalidate();
        lineNumbers.repaint();
    }

    Set<Integer> lines() {
        TreeSet<Integer> lines = new TreeSet<>();
        for (Position breakpoint : breakpoints) {
            lines.add(lineOf(breakpoint) + 1);
        }
        return Collections.unmodifiableSet(lines);
    }

    List<UiCodeEditorBreakpoint> breakpoints() {
        return breakpoints.stream()
                .map(position -> new UiCodeEditorBreakpoint(
                        lineOf(position) + 1,
                        position.getOffset()
                ))
                .sorted(Comparator.comparingInt(UiCodeEditorBreakpoint::line))
                .toList();
    }

    void set(int oneBasedLine, boolean enabled) {
        int line = oneBasedLine - 1;
        requireLine(line);
        Position breakpoint = find(line);
        if (enabled && breakpoint == null) {
            breakpoints.add(createPosition(line));
        } else if (!enabled && breakpoint != null) {
            breakpoints.remove(breakpoint);
        }
        lineNumbers.repaint();
    }

    void clear() {
        breakpoints.clear();
        hoveredLine = NO_LINE;
        pressedLine = NO_LINE;
        lineNumbers.repaint();
    }

    void toggleAtCaret() {
        try {
            toggle(textArea.getLineOfOffset(textArea.getCaretPosition()));
            lineNumbers.repaint();
        } catch (BadLocationException exception) {
            throw new IllegalStateException("caret is outside the document", exception);
        }
    }

    private void install(RTextScrollPane scrollPane) {
        scrollPane.setLineNumbersEnabled(false);
        scrollPane.setFoldIndicatorEnabled(false);
        lineNumbers.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setRowHeaderView(lineNumbers);
    }

    private void installInteraction() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                updateHoveredLine(event.getY());
            }

            @Override
            public void mouseEntered(MouseEvent event) {
                updateHoveredLine(event.getY());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hoveredLine = NO_LINE;
                pressedLine = NO_LINE;
                lineNumbers.setCursor(Cursor.getDefaultCursor());
                clickableCursorChanged.accept(false);
                lineNumbers.repaint();
            }

            @Override
            public void mousePressed(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    pressedLine = lineAt(event.getY());
                    lineNumbers.repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                int releasedLine = lineAt(event.getY());
                if (SwingUtilities.isLeftMouseButton(event)
                        && pressedLine != NO_LINE
                        && pressedLine == releasedLine) {
                    toggle(releasedLine);
                }
                pressedLine = NO_LINE;
                hoveredLine = releasedLine;
                lineNumbers.repaint();
            }
        };
        lineNumbers.addMouseListener(mouse);
        lineNumbers.addMouseMotionListener(mouse);
    }

    private void installRepaintListeners() {
        textArea.addCaretListener(event -> lineNumbers.repaint());
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                documentChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                removeDuplicateBreakpoints();
                documentChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                documentChanged();
            }

            private void documentChanged() {
                lineNumbers.revalidate();
                lineNumbers.repaint();
            }
        });
    }

    private void updateHoveredLine(int y) {
        hoveredLine = lineAt(y);
        boolean clickable = hoveredLine != NO_LINE;
        lineNumbers.setCursor(clickable
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getDefaultCursor());
        clickableCursorChanged.accept(clickable);
        lineNumbers.repaint();
    }

    private void toggle(int line) {
        Position breakpoint = find(line);
        if (breakpoint == null) {
            breakpoints.add(createPosition(line));
        } else {
            breakpoints.remove(breakpoint);
        }
    }

    private int lineAt(int y) {
        int offset = textArea.viewToModel2D(new Point(0, y));
        if (offset < 0) {
            return NO_LINE;
        }
        try {
            int line = textArea.getLineOfOffset(offset);
            Rectangle2D bounds = lineBounds(line);
            int lineHeight = textArea.getLineHeight();
            return bounds != null && y >= bounds.getY() && y < bounds.getY() + lineHeight
                    ? line
                    : NO_LINE;
        } catch (BadLocationException exception) {
            return NO_LINE;
        }
    }

    private Position find(int line) {
        for (Position breakpoint : breakpoints) {
            if (lineOf(breakpoint) == line) {
                return breakpoint;
            }
        }
        return null;
    }

    private int lineOf(Position breakpoint) {
        try {
            return textArea.getLineOfOffset(Math.min(
                    breakpoint.getOffset(),
                    textArea.getDocument().getLength()
            ));
        } catch (BadLocationException exception) {
            throw new IllegalStateException("breakpoint position is outside the document", exception);
        }
    }

    private Position createPosition(int line) {
        try {
            return textArea.getDocument().createPosition(textArea.getLineStartOffset(line));
        } catch (BadLocationException exception) {
            throw new IllegalArgumentException("invalid breakpoint line: " + (line + 1), exception);
        }
    }

    private Rectangle2D lineBounds(int line) throws BadLocationException {
        return textArea.modelToView2D(textArea.getLineStartOffset(line));
    }

    private void requireLine(int line) {
        if (line < 0 || line >= textArea.getLineCount()) {
            throw new IllegalArgumentException("invalid breakpoint line: " + (line + 1));
        }
    }

    private void removeDuplicateBreakpoints() {
        TreeSet<Integer> occupiedLines = new TreeSet<>();
        breakpoints.removeIf(breakpoint -> !occupiedLines.add(lineOf(breakpoint)));
    }

    private final class BreakpointLineNumbers extends JComponent {
        private BreakpointLineNumbers() {
            setOpaque(true);
            setFocusable(false);
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(lineNumberFont);
            int digitsWidth = metrics.stringWidth(Integer.toString(textArea.getLineCount()));
            int dotWidth = Math.max(6, textArea.getLineHeight() - DOT_MARGIN);
            int width = Math.max(
                    digitsWidth + HORIZONTAL_PADDING * 2,
                    dotWidth + HORIZONTAL_PADDING
            );
            int height = Math.max(textArea.getPreferredSize().height, textArea.getHeight());
            return new Dimension(width, height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                Rectangle clip = graphics2D.getClipBounds();
                graphics2D.setColor(background);
                graphics2D.fillRect(clip.x, clip.y, clip.width, clip.height);
                graphics2D.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );
                graphics2D.setRenderingHint(
                        RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                );
                graphics2D.setFont(lineNumberFont);

                int firstLine = visibleLineAt(clip.y);
                int lastLine = visibleLineAt(clip.y + clip.height);
                for (int line = firstLine; line <= lastLine; line++) {
                    paintLine(graphics2D, line);
                }

                graphics2D.setColor(borderColor);
                graphics2D.drawLine(
                        getWidth() - 1,
                        clip.y,
                        getWidth() - 1,
                        clip.y + clip.height
                );
            } finally {
                graphics2D.dispose();
            }
        }

        private void paintLine(Graphics2D graphics, int line) {
            try {
                Rectangle2D bounds = lineBounds(line);
                if (bounds == null) {
                    return;
                }
                boolean marked = find(line) != null;
                boolean previewed = !marked && hoveredLine == line;
                String number = Integer.toString(line + 1);
                GlyphVector glyph = lineNumberFont.createGlyphVector(
                        graphics.getFontRenderContext(),
                        number
                );
                Rectangle2D glyphBounds = glyph.getVisualBounds();
                double rowCenterY = bounds.getY() + textArea.getLineHeight() / 2.0;
                float baseline = (float) (rowCenterY - glyphBounds.getCenterY());

                if (marked) {
                    paintDot(graphics, rowCenterY, 255);
                } else if (previewed) {
                    paintDot(
                            graphics,
                            rowCenterY,
                            pressedLine == line ? 255 : PREVIEW_ALPHA
                    );
                }

                FontMetrics metrics = graphics.getFontMetrics();
                if (marked || previewed) {
                    float centeredX = (float) (getWidth() / 2.0 - glyphBounds.getCenterX());
                    paintMarkedNumber(graphics, number, centeredX, baseline);
                } else {
                    int x = getWidth() - HORIZONTAL_PADDING - metrics.stringWidth(number);
                    graphics.setColor(line == textArea.getCaretLineNumber()
                            ? currentLineNumberColor
                            : lineNumberColor);
                    graphics.drawString(number, x, baseline);
                }
            } catch (BadLocationException ignored) {
                // 文档变更与绘制重叠时，下一帧会使用新的行结构重绘。
            }
        }

        private void paintDot(Graphics2D graphics, double rowCenterY, int alpha) {
            int lineHeight = textArea.getLineHeight();
            int diameter = Math.max(6, lineHeight - DOT_MARGIN);
            int x = (int) Math.round(getWidth() / 2.0 - diameter / 2.0);
            int y = (int) Math.round(rowCenterY - diameter / 2.0);
            graphics.setColor(new Color(
                    breakpointColor.getRed(),
                    breakpointColor.getGreen(),
                    breakpointColor.getBlue(),
                    alpha
            ));
            graphics.fillOval(x, y, diameter, diameter);
        }

        private void paintMarkedNumber(
                Graphics2D graphics,
                String number,
                float x,
                float baseline
        ) {
            graphics.setColor(MARKED_NUMBER_FILL);
            graphics.drawString(number, x, baseline);
        }

        private int visibleLineAt(int y) {
            int offset = textArea.viewToModel2D(new Point(0, Math.max(0, y)));
            if (offset < 0) {
                return 0;
            }
            try {
                return textArea.getLineOfOffset(offset);
            } catch (BadLocationException exception) {
                return Math.max(0, textArea.getLineCount() - 1);
            }
        }
    }
}
