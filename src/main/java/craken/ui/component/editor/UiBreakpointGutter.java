package craken.ui.component.editor;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.LineNumberList;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 在行号数字下层绘制并管理断点。所有方法均在 Swing EDT 调用。 */
final class UiBreakpointGutter {
    private static final int NO_LINE = -1;
    private static final int DOT_MARGIN = 4;
    private static final int PREVIEW_ALPHA = 112;

    private final RSyntaxTextArea textArea;
    private final LineNumberList lineNumbers;
    private final BreakpointLayer breakpointLayer = new BreakpointLayer();
    private final List<Position> breakpoints = new ArrayList<>();

    private int hoveredLine = NO_LINE;
    private int pressedLine = NO_LINE;
    private Color breakpointColor = new Color(0xf85149);

    UiBreakpointGutter(RSyntaxTextArea textArea, RTextScrollPane scrollPane) {
        this.textArea = textArea;
        this.lineNumbers = findLineNumbers(scrollPane.getGutter());
        install(scrollPane);
        installInteraction();
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                breakpointLayer.repaint();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                removeDuplicateBreakpoints();
                breakpointLayer.repaint();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                breakpointLayer.repaint();
            }
        });
    }

    void setColor(Color color) {
        breakpointColor = color;
        breakpointLayer.repaint();
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
        breakpointLayer.repaint();
    }

    void clear() {
        breakpoints.clear();
        hoveredLine = NO_LINE;
        pressedLine = NO_LINE;
        breakpointLayer.repaint();
    }

    void toggleAtCaret() {
        try {
            toggle(textArea.getLineOfOffset(textArea.getCaretPosition()));
            breakpointLayer.repaint();
        } catch (BadLocationException exception) {
            throw new IllegalStateException("caret is outside the document", exception);
        }
    }

    private void install(RTextScrollPane scrollPane) {
        Gutter gutter = scrollPane.getGutter();
        gutter.setOpaque(false);
        lineNumbers.setOpaque(false);
        scrollPane.setRowHeaderView(new LineNumberLayers(
                gutter,
                lineNumbers,
                breakpointLayer
        ));
    }

    private void installInteraction() {
        // 行号区域专用于断点，避免 RSyntaxTextArea 自带的整行选择抢占点击。
        lineNumbers.removeMouseListener(lineNumbers);
        lineNumbers.removeMouseMotionListener(lineNumbers);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                updateHoveredLine(event);
            }

            @Override
            public void mouseEntered(MouseEvent event) {
                updateHoveredLine(event);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hoveredLine = NO_LINE;
                pressedLine = NO_LINE;
                lineNumbers.setCursor(Cursor.getDefaultCursor());
                breakpointLayer.repaint();
            }

            @Override
            public void mousePressed(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    pressedLine = lineAt(event.getY());
                    breakpointLayer.repaint();
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
                breakpointLayer.repaint();
            }
        };
        lineNumbers.addMouseListener(mouse);
        lineNumbers.addMouseMotionListener(mouse);
    }

    private void updateHoveredLine(MouseEvent event) {
        hoveredLine = lineAt(event.getY());
        lineNumbers.setCursor(hoveredLine == NO_LINE
                ? Cursor.getDefaultCursor()
                : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        breakpointLayer.repaint();
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
        int offset = textArea.viewToModel2D(new java.awt.Point(0, y));
        if (offset < 0) {
            return NO_LINE;
        }
        try {
            int line = textArea.getLineOfOffset(offset);
            Rectangle2D bounds = textArea.modelToView2D(textArea.getLineStartOffset(line));
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

    private void requireLine(int line) {
        if (line < 0 || line >= textArea.getLineCount()) {
            throw new IllegalArgumentException("invalid breakpoint line: " + (line + 1));
        }
    }

    private void removeDuplicateBreakpoints() {
        TreeSet<Integer> occupiedLines = new TreeSet<>();
        breakpoints.removeIf(breakpoint -> !occupiedLines.add(lineOf(breakpoint)));
    }

    private static LineNumberList findLineNumbers(Gutter gutter) {
        for (Component component : gutter.getComponents()) {
            if (component instanceof LineNumberList list) {
                return list;
            }
        }
        throw new IllegalStateException("RSyntaxTextArea line-number component is missing");
    }

    private final class BreakpointLayer extends JComponent {
        private BreakpointLayer() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );
                for (Position breakpoint : breakpoints) {
                    paintDot(graphics2D, lineOf(breakpoint), 255);
                }
                if (hoveredLine != NO_LINE && find(hoveredLine) == null) {
                    paintDot(
                            graphics2D,
                            hoveredLine,
                            pressedLine == hoveredLine ? 255 : PREVIEW_ALPHA
                    );
                }
            } finally {
                graphics2D.dispose();
            }
        }

        private void paintDot(Graphics2D graphics, int line, int alpha) {
            try {
                Rectangle2D bounds = textArea.modelToView2D(textArea.getLineStartOffset(line));
                if (bounds == null) {
                    return;
                }
                int lineHeight = textArea.getLineHeight();
                int diameter = Math.max(6, lineHeight - DOT_MARGIN);
                int lineNumberX = SwingUtilities.convertPoint(
                        lineNumbers,
                        0,
                        0,
                        this
                ).x;
                int x = lineNumberX + (lineNumbers.getWidth() - diameter) / 2;
                int y = (int) Math.round(bounds.getY()) + (lineHeight - diameter) / 2;
                graphics.setColor(new Color(
                        breakpointColor.getRed(),
                        breakpointColor.getGreen(),
                        breakpointColor.getBlue(),
                        alpha
                ));
                graphics.fillOval(x, y, diameter, diameter);
            } catch (BadLocationException ignored) {
                // 文档更新与重绘重叠时，该断点会在下一次 repaint 中自然恢复。
            }
        }
    }

    private static final class LineNumberLayers extends JLayeredPane {
        private final Gutter gutter;
        private final JComponent lineNumbers;
        private final JComponent breakpoints;

        private LineNumberLayers(
                Gutter gutter,
                JComponent lineNumbers,
                JComponent breakpoints
        ) {
            this.gutter = gutter;
            this.lineNumbers = lineNumbers;
            this.breakpoints = breakpoints;
            setOpaque(true);
            add(breakpoints, DEFAULT_LAYER);
            add(gutter, PALETTE_LAYER);
        }

        @Override
        public void doLayout() {
            Dimension size = getSize();
            breakpoints.setBounds(0, 0, size.width, size.height);
            gutter.setBounds(0, 0, size.width, size.height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            graphics.setColor(gutter.getBackground());
            graphics.fillRect(0, 0, getWidth(), getHeight());
        }

        @Override
        public Dimension getPreferredSize() {
            return gutter.getPreferredSize();
        }

        @Override
        public Dimension getMinimumSize() {
            return gutter.getMinimumSize();
        }
    }
}
