package craken.ui.component.editor;

import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import craken.SourceRange;
import craken.ui.component.UiComponent;
import craken.ui.component.UiStyles;
import craken.ui.component.swing.UiSwingNodeSurface;
import craken.ui.component.swing.UiSwingFocus;
import craken.ui.component.swing.UiSwingNode;
import org.fife.ui.rsyntaxtextarea.Style;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

import javax.swing.SwingUtilities;
import javax.swing.JScrollPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Craken 源码和只读编译产物共用的代码编辑器。 */
public final class UiCodeEditor extends StackPane implements UiComponent {
    private final UiSwingNode swingNode = new UiSwingNode();
    private final UiCodeEditorTextArea textArea = new UiCodeEditorTextArea();
    private final UiSwingFocus focus = new UiSwingFocus(swingNode, () -> textArea);
    private final UiCodeEditorScrollPane scrollPane = new UiCodeEditorScrollPane(textArea, true);
    private final UiEditorResizeSurface resizeSurface = new UiEditorResizeSurface(swingNode, scrollPane);
    private UiEditorBreakpoints breakpointGutter;
    private UiEditorDecorations decorations;
    private UiCodeEditorShortcuts shortcuts;
    private UiCodeEditorKeywordStyle keywordStyle;
    private final UiEditorCaretListBridge caretList;
    private final UiEditorZoom zoom = new UiEditorZoom();
    private UiCodeEditorStyle baseStyle;
    private Font baseFont;
    private volatile boolean mouseWheelZoomEnabled = true;
    private volatile Cursor embeddedCursor = Cursor.DEFAULT;
    private volatile Runnable onTextChanged;
    private final AtomicBoolean textChangeQueued = new AtomicBoolean();
    private final DocumentListener textListener = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent event) { queueTextChanged(); }
        @Override public void removeUpdate(DocumentEvent event) { queueTextChanged(); }
        @Override public void changedUpdate(DocumentEvent event) { queueTextChanged(); }
    };

    /** 创建空编辑器。 */
    public UiCodeEditor() {
        this("");
    }

    /** 创建带初始文本的编辑器；null 按空文本处理，光标位于开头，撤销历史为空。 */
    public UiCodeEditor(String initialSource) {
        this(initialSource, null);
    }

    /** 同一磁盘文件的分屏共享文本模型，各自保留光标、滚动、缩放与断点视图。 */
    public static UiCodeEditor linkedTo(UiCodeEditor editor) {
        return new UiCodeEditor("", Objects.requireNonNull(editor));
    }

    private UiCodeEditor(String initialSource, UiCodeEditor shared) {
        setMinSize(0, 0);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setPrefSize(640, 480);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
        getStyleClass().add("ui-code-editor");
        getChildren().addAll(swingNode, resizeSurface.view());
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        swingNode.addEventFilter(ScrollEvent.SCROLL, this::handleZoomScroll);

        runOnSwingThread(() -> {
            configureEditor();
            if (shared != null) textArea.setDocument(shared.textArea.getDocument());
            baseFont = textArea.getFont();
            decorations = new UiEditorDecorations(textArea);
            breakpointGutter = new UiEditorBreakpoints(
                    textArea,
                    scrollPane,
                    clickable -> showEmbeddedCursor(clickable ? Cursor.HAND : Cursor.DEFAULT)
            );
            shortcuts = new UiCodeEditorShortcuts(
                    textArea, breakpointGutter, this::zoomIn, this::zoomOut, this::resetZoom);
            if (shared == null) setSource(initialSource);
            textArea.getDocument().addDocumentListener(textListener);
        });
        caretList = new UiEditorCaretListBridge(this, swingNode, textArea, scrollPane);
        UiStyles.manage(this);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        resizeSurface.layout();
    }

    private void configureEditor() {
        scrollPane.setLayout(new UiCodeEditorScrollPaneLayout());
        scrollPane.setCorner(JScrollPane.LOWER_RIGHT_CORNER, null);
        textArea.enableInputMethods(true);
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_C);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);
        textArea.setAutoIndentEnabled(true);
        textArea.setBracketMatchingEnabled(true);
        textArea.setAnimateBracketMatching(false);
        textArea.setPaintMatchedBracketPair(true);
        textArea.setMarkOccurrences(true);
        textArea.setPaintMarkOccurrencesBorder(false);
        textArea.setHighlightCurrentLine(true);
        textArea.setFadeCurrentLineHighlight(false);
        textArea.setTabsEmulated(true);
        textArea.setTabSize(4);
        textArea.setLineWrap(false);
        textArea.setPaintTabLines(false);
        installCursorBridge();
    }

    private void installCursorBridge() {
        java.awt.Cursor textCursor = java.awt.Cursor.getPredefinedCursor(
                java.awt.Cursor.TEXT_CURSOR
        );
        textArea.setCursor(textCursor);

        MouseAdapter editorCursor = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                showEmbeddedCursor(Cursor.TEXT);
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                showEmbeddedCursor(Cursor.TEXT);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                showEmbeddedCursor(Cursor.DEFAULT);
            }
        };
        textArea.addMouseListener(editorCursor);
        textArea.addMouseMotionListener(editorCursor);

        MouseAdapter defaultCursor = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                showEmbeddedCursor(Cursor.DEFAULT);
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                showEmbeddedCursor(Cursor.DEFAULT);
            }
        };
        scrollPane.getVerticalScrollBar().addMouseListener(defaultCursor);
        scrollPane.getVerticalScrollBar().addMouseMotionListener(defaultCursor);
        scrollPane.getHorizontalScrollBar().addMouseListener(defaultCursor);
        scrollPane.getHorizontalScrollBar().addMouseMotionListener(defaultCursor);
    }

    private void showEmbeddedCursor(Cursor cursor) {
        if (embeddedCursor == cursor) {
            return;
        }
        embeddedCursor = cursor;
        Runnable update = () -> swingNode.setCursor(cursor);
        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    public void setSource(String source) {
        if (caretList != null) {
            caretList.hide();
        }
        runOnSwingThread(() -> {
            decorations.clear();
            textArea.setText(source == null ? "" : source);
            breakpointGutter.clear();
            textArea.setCaretPosition(0);
            textArea.discardAllEdits();
        });
    }

    public String source() {
        return text();
    }

    /** 文本变更通知在 JavaFX 线程合并发送，调用者不需要直接访问 Swing Document。 */
    public void setOnTextChanged(Runnable handler) {
        onTextChanged = handler;
    }

    private void queueTextChanged() {
        if (onTextChanged != null && textChangeQueued.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                textChangeQueued.set(false);
                Runnable handler = onTextChanged;
                if (handler != null) handler.run();
            });
        }
    }

    /** 仅在真正关闭文件时释放嵌入视图；切换 Tab 不调用此方法。 */
    public void dispose() {
        focus.close();
        onTextChanged = null;
        caretList.dispose();
        runOnSwingThread(() -> {
            textArea.getDocument().removeDocumentListener(textListener);
            breakpointGutter.dispose();
            // 从共享 Document 脱离，Swing 自身的 caret/undo/UI 监听器也随之解绑。
            textArea.setDocument(new org.fife.ui.rsyntaxtextarea.RSyntaxDocument(SyntaxConstants.SYNTAX_STYLE_C));
            UiSwingNodeSurface.release(scrollPane);
            swingNode.setContent(null);
        });
    }

    /** 获取编辑器中的完整文本内容。 */
    public String text() {
        AtomicReference<String> source = new AtomicReference<>();
        runOnSwingThread(() -> source.set(textArea.getText()));
        return source.get();
    }

    public void moveTo(int offset) {
        runOnSwingThread(() -> textArea.setCaretPosition(
                Math.max(0, Math.min(offset, textArea.getDocument().getLength()))
        ));
    }

    public void setReadOnly(boolean readOnly) {
        runOnSwingThread(() -> textArea.setEditable(!readOnly));
    }

    /** 放大一级（基准字号的 10%），仅影响此编辑器，不缩放应用窗口。 */
    public void zoomIn() {
        runOnSwingThread(() -> updateZoom(zoom.level() + 1));
    }

    /** 缩小一级（基准字号的 10%）。 */
    public void zoomOut() {
        runOnSwingThread(() -> updateZoom(zoom.level() - 1));
    }

    /** 恢复 UiStyles 配置的基准字号，不修改主题或文档。 */
    public void resetZoom() {
        setZoomLevel(0);
    }

    /** 设置缩放级别，0 为 100%；范围 -5～20，越界自动限制，支持小数。 */
    public void setZoomLevel(double level) {
        if (!Double.isFinite(level)) {
            throw new IllegalArgumentException("editor zoom level must be finite");
        }
        runOnSwingThread(() -> updateZoom(level));
    }

    public double zoomLevel() {
        AtomicReference<Double> level = new AtomicReference<>();
        runOnSwingThread(() -> level.set(zoom.level()));
        return level.get();
    }

    /** 缩放倍率，1 为 100%。 */
    public double zoomFactor() {
        return 1 + zoomLevel() * 0.1;
    }

    /** Ctrl＋滚轮缩放默认启用；关闭后不拦截滚轮，不影响键盘和 API 缩放。 */
    public void setMouseWheelZoomEnabled(boolean enabled) {
        mouseWheelZoomEnabled = enabled;
    }

    public boolean isMouseWheelZoomEnabled() {
        return mouseWheelZoomEnabled;
    }

    private void handleZoomScroll(ScrollEvent event) {
        if (!mouseWheelZoomEnabled || !event.isControlDown() || event.isAltDown()
                || event.isMetaDown() || event.isShiftDown() || event.getDeltaY() == 0) {
            return;
        }
        event.consume();
        if (event.getDeltaY() > 0) {
            zoomIn();
        } else {
            zoomOut();
        }
    }

    private void updateZoom(double level) {
        if (zoom.setLevel(level)) {
            applyZoomFont();
        }
    }

    /** 始终从主题基准字体计算，避免多次缩放和换主题产生累计误差。仅由 EDT 调用。 */
    private void applyZoomFont() {
        Point oldScroll = scrollPane.getViewport().getViewPosition();
        int oldLineHeight = textArea.getLineHeight();
        float oldFontSize = textArea.getFont().getSize2D();
        Font font = baseFont.deriveFont((float) (baseFont.getSize2D() * zoom.factor()));
        textArea.setFont(font);
        if (keywordStyle != null) {
            applyKeywordStyle(keywordStyle);
        }
        Font lineNumberFont = baseFont.deriveFont((float) (13 * zoom.factor()));
        scrollPane.getGutter().setLineNumberFont(lineNumberFont);
        breakpointGutter.setFont(lineNumberFont);
        // 行号列和文本布局一起刷新，保留原来的可视行，不修改光标/选区/撤销历史。
        scrollPane.doLayout();
        scrollPane.getViewport().doLayout();
        if (scrollPane.getRowHeader() != null) {
            scrollPane.getRowHeader().doLayout();
        }
        Rectangle view = scrollPane.getViewport().getViewRect();
        int x = (int) Math.round(oldScroll.x * font.getSize2D() / oldFontSize);
        int y = (int) Math.round(oldScroll.y * (double) textArea.getLineHeight() / oldLineHeight);
        scrollPane.getViewport().setViewPosition(new Point(
                Math.max(0, Math.min(x, textArea.getWidth() - view.width)),
                Math.max(0, Math.min(y, textArea.getHeight() - view.height))
        ));
        scrollPane.revalidate();
        scrollPane.repaint();
        if (caretList != null && baseStyle != null) {
            caretList.applyStyle(baseStyle, zoom.factor());
        }
    }

    /** 返回当前断点所在的 1-based 源码行。 */
    public Set<Integer> breakpointLines() {
        AtomicReference<Set<Integer>> lines = new AtomicReference<>();
        runOnSwingThread(() -> lines.set(breakpointGutter.lines()));
        return lines.get();
    }

    /** 获取按源码行排序的断点信息。 */
    public List<UiCodeEditorBreakpoint> breakpoints() {
        AtomicReference<List<UiCodeEditorBreakpoint>> breakpoints = new AtomicReference<>();
        runOnSwingThread(() -> breakpoints.set(breakpointGutter.breakpoints()));
        return breakpoints.get();
    }

    /** 以 1-based 源码行设置或清除断点。 */
    public void setBreakpoint(int line, boolean enabled) {
        runOnSwingThread(() -> breakpointGutter.set(line, enabled));
    }

    public void clearBreakpoints() {
        runOnSwingThread(breakpointGutter::clear);
    }

    /** 用红色波浪线替换当前所有错误标记。范围使用 IDE 的行号和 UTF-8 字节坐标。 */
    public void setErrorUnderlines(Collection<SourceRange> ranges) {
        runOnSwingThread(() -> decorations.setErrorUnderlines(ranges));
    }

    public void clearErrorUnderlines() {
        runOnSwingThread(decorations::clearErrorUnderlines);
    }

    /**
     * 用黄色半透明背景显示外部传入的 SourceRange，并替换当前所有区域标记。
     * 编辑器内部不会自动创建该标记；范围使用 IDE 的行号和 UTF-8 字节坐标。
     */
    public void setRangeHighlights(Collection<SourceRange> ranges) {
        runOnSwingThread(() -> decorations.setRangeHighlights(ranges));
    }

    public void clearRangeHighlights() {
        runOnSwingThread(decorations::clearRangeHighlights);
    }

    public void clearDecorations() {
        runOnSwingThread(decorations::clear);
    }

    /**
     * 在当前光标处显示列表。需在编辑器已显示后调用；空列表会关闭浮窗。
     * 选中回调在 JavaFX 线程执行，组件不自动插入文本。
     * 文本变更会关闭旧列表，调用者可用新条目再次调用本方法。
     */
    public void showCaretList(
            List<UiCodeEditorListItem> items,
            Consumer<UiCodeEditorListItem> onSelected
    ) {
        caretList.show(List.copyOf(items), Objects.requireNonNull(onSelected, "onSelected"));
    }

    public void hideCaretList() {
        caretList.hide();
    }

    /** 设置浮窗固定宽度（JavaFX 逻辑像素），默认 430；不随条目长度改变。 */
    public void setCaretListWidth(double width) {
        if (!Double.isFinite(width) || width <= 0) {
            throw new IllegalArgumentException("caret list width must be finite and positive");
        }
        caretList.setWidth(width);
    }

    /** 默认最多显示 12 项；可用空间不足时进一步缩短并启用垂直滚动。 */
    public void setCaretListMaxVisibleItems(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("caret list visible item count must be positive");
        }
        caretList.setMaxVisibleItems(count);
    }

    /** 自定义所有 C 关键词（含类型关键词）的显示样式。 */
    public void setKeywordStyle(UiCodeEditorKeywordStyle style) {
        Objects.requireNonNull(style, "style");
        runOnSwingThread(() -> {
            keywordStyle = style;
            applyKeywordStyle(style);
        });
    }

    /** 获取当前编辑器快捷键映射表。 */
    public Map<UiCodeEditorAction, KeyCombination> shortcutMappings() {
        AtomicReference<Map<UiCodeEditorAction, KeyCombination>> mappings =
                new AtomicReference<>();
        runOnSwingThread(() -> mappings.set(shortcuts.mappings()));
        return mappings.get();
    }

    /** 整体替换快捷键映射；未提供的动作将不再绑定快捷键。 */
    public void setShortcutMappings(
            Map<UiCodeEditorAction, KeyCombination> mappings
    ) {
        runOnSwingThread(() -> shortcuts.replace(mappings));
    }

    /** 由 UiStyles 统一调用，不在页面或业务代码中直接选择主题。 */
    public void applyStyle(UiCodeEditorStyle style) {
        Objects.requireNonNull(style, "style");
        runOnSwingThread(() -> {
            // 拆分容器会使编辑器暂时离开再进入同一 Scene；同一主题无需重装。
            if (style.equals(baseStyle) && swingNode.getContent() != null) {
                return;
            }
            /*
             * RSyntaxTextArea 4.x 在组件已经 displayable、但 SwingNode 尚未完成
             * 首次绘制时调用 Theme.apply()，会用空 Graphics 刷新字体度量。
             * 因此先完成样式初始化，再把 Swing 内容挂到 JavaFX 场景中。
             */
            style.apply(textArea, scrollPane);
            baseStyle = style;
            baseFont = style.font();
            breakpointGutter.applyStyle(
                    style.font().deriveFont(13f),
                    style.background(),
                    style.mutedForeground(),
                    style.foreground(),
                    style.border(),
                    style.breakpoint()
            );
            decorations.applyStyle(
                    style.errorUnderline(),
                    style.rangeHighlight()
            );
            applyZoomFont();
            if (swingNode.getContent() == null) {
                swingNode.setContent(scrollPane);
            }
            UiSwingNodeSurface.prepare(scrollPane);
        });
    }

    public void requestEditorFocus() {
        focus.requestFocus();
    }

    private void applyKeywordStyle(UiCodeEditorKeywordStyle style) {
        int fontStyle = Font.PLAIN;
        if (style.bold()) {
            fontStyle |= Font.BOLD;
        }
        if (style.italic()) {
            fontStyle |= Font.ITALIC;
        }
        java.awt.Color background = style.background().getOpacity() == 0
                ? null
                : awtColor(style.background());
        Style syntaxStyle = new Style(
                awtColor(style.foreground()),
                background,
                textArea.getFont().deriveFont(fontStyle),
                style.underline()
        );
        SyntaxScheme scheme = (SyntaxScheme) textArea.getSyntaxScheme().clone();
        scheme.setStyle(TokenTypes.RESERVED_WORD, syntaxStyle);
        scheme.setStyle(TokenTypes.RESERVED_WORD_2, (Style) syntaxStyle.clone());
        scheme.setStyle(TokenTypes.DATA_TYPE, (Style) syntaxStyle.clone());
        textArea.setSyntaxScheme(scheme);
    }

    private static java.awt.Color awtColor(javafx.scene.paint.Color color) {
        return new java.awt.Color(
                (float) color.getRed(),
                (float) color.getGreen(),
                (float) color.getBlue(),
                (float) color.getOpacity()
        );
    }

    private static void runOnSwingThread(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while updating code editor", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("failed to update code editor", exception.getCause());
        }
    }
}
