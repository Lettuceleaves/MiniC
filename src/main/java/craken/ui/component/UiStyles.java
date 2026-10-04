package craken.ui.component;

import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.Parent;
import craken.ui.component.editor.UiCodeEditor;
import craken.ui.component.editor.UiCodeEditorStyle;
import craken.ui.component.swing.UiSwingScrollBarUi;

import javax.swing.JScrollBar;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * 负责安装 UI 外观资源。
 *
 * <p>组件与其视觉类型是固定的一对一关系；主题切换只替换样式表，
 * 不改变组件类型或重新绑定角色。</p>
 */
public final class UiStyles {
    private static final String DEFAULT_THEME = "/craken/ui/default-theme.css";
    private static final Object INSTALLED_STYLES_KEY = new Object();
    private static final UiCodeEditorStyle DEFAULT_CODE_EDITOR_STYLE = new UiCodeEditorStyle(
            "/org/fife/ui/rsyntaxtextarea/themes/dark.xml",
            codeFont(),
            color("0d1117"),
            color("e6edf3"),
            color("8b949e"),
            color("30363d"),
            color("e6edf3"),
            color("1f6feb"),
            color("161b22"),
            color("264f78"),
            color("f85149"),
            color("f85149"),
            color("f2cc60", 72) // 仅供外部 SourceRange 区域标记使用。
    );
    private static final Font DEFAULT_TERMINAL_FONT = DEFAULT_CODE_EDITOR_STYLE.font().deriveFont(16f);

    private UiStyles() {
    }

    /** 终端复用代码编辑器的字形及中文补全，仅保留独立字号。 */
    public static Font terminalFont() {
        return DEFAULT_TERMINAL_FONT;
    }

    /** 面板列表与编辑器悬浮候选列表使用相同的基准字形和字号。 */
    public static javafx.scene.text.Font listFont() {
        Font font = DEFAULT_CODE_EDITOR_STYLE.font();
        return javafx.scene.text.Font.font(font.getFamily(), font.getSize2D());
    }

    /** Swing 内容也使用 App 的滚动条配色，不能依赖外层 JavaFX CSS。 */
    public static void installScrollBarStyle(JScrollBar scrollBar,
                                            BiConsumer<Graphics, Rectangle> trackDecoration) {
        UiSwingScrollBarUi.install(scrollBar, DEFAULT_CODE_EDITOR_STYLE.background(),
                DEFAULT_CODE_EDITOR_STYLE.mutedForeground(), DEFAULT_CODE_EDITOR_STYLE.foreground(),
                trackDecoration);
    }

    /** 为 Scene 安装默认主题。主题不包含会影响布局的几何参数。 */
    public static void install(Scene scene) {
        install(scene, DEFAULT_THEME, DEFAULT_CODE_EDITOR_STYLE);
    }

    /**
     * 同时安装 JavaFX 样式表和无法由 JavaFX CSS 控制的代码编辑器样式。
     * 几何信息仍由组件类固定，切换主题不改变布局。
     *
     * @param scene 目标 Scene
     * @param themeResource classpath 主题资源绝对路径
     * @param codeEditorStyle 与该主题配套的 Swing 编辑器样式
     */
    public static void install(
            Scene scene,
            String themeResource,
            UiCodeEditorStyle codeEditorStyle
    ) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(codeEditorStyle, "codeEditorStyle");
        scene.getProperties().put(
                INSTALLED_STYLES_KEY,
                new InstalledStyles(themeResource, codeEditorStyle)
        );
        scene.getStylesheets().setAll(resource(themeResource));
        applyCodeEditorStyles(scene.getRoot(), codeEditorStyle);
    }

    /** 让后加入 Scene 的编辑器自动使用该 Scene 已安装的主题。 */
    public static void manage(UiCodeEditor editor) {
        Objects.requireNonNull(editor, "editor");
        editor.sceneProperty().addListener((observable, previous, current) -> {
            if (current != null) {
                applyInstalledCodeEditorStyle(current, editor);
            }
        });
        if (editor.getScene() != null) {
            applyInstalledCodeEditorStyle(editor.getScene(), editor);
        }
    }

    private static String resource(String path) {
        URL resource = UiStyles.class.getResource(path);
        if (resource == null) {
            throw new IllegalStateException("Missing UI stylesheet: " + path);
        }
        return resource.toExternalForm();
    }

    private static void applyInstalledCodeEditorStyle(Scene scene, UiCodeEditor editor) {
        Object installed = scene.getProperties().get(INSTALLED_STYLES_KEY);
        if (installed instanceof InstalledStyles styles) {
            editor.applyStyle(styles.codeEditorStyle());
        }
    }

    private static void applyCodeEditorStyles(Node node, UiCodeEditorStyle style) {
        if (node instanceof UiCodeEditor editor) {
            editor.applyStyle(style);
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child ->
                    applyCodeEditorStyles(child, style));
        }
    }

    private static Color color(String rgb) {
        return new Color(Integer.parseInt(rgb, 16));
    }

    private static Color color(String rgb, int alpha) {
        Color color = color(rgb);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    /** Cascadia Mono 负责代码字形，系统组合字体只补充它缺失的中文字形。 */
    private static Font codeFont() {
        return monospacedFont(14, "Cascadia Mono", "Consolas");
    }

    private static Font monospacedFont(int size, String... families) {
        Font primary = null;
        for (String family : families) {
            primary = installedFont(family, size);
            if (primary != null) break;
        }
        if (primary != null) {
            try {
                Class<?> utilities = Class.forName("sun.font.FontUtilities");
                Method compose = utilities.getMethod(
                        "getCompositeFontUIResource",
                        Font.class
                );
                Font composite = (Font) compose.invoke(null, primary);
                if (composite.canDisplayUpTo("中文") == -1) {
                    return composite;
                }
            } catch (ReflectiveOperationException ignored) {
                // 非标准启动器未开放字体组合接口时，使用完整覆盖中文的等宽字体。
            }
        }
        return new Font("SimHei", Font.PLAIN, size);
    }

    private static Font installedFont(String family, int size) {
        Font font = new Font(family, Font.PLAIN, size);
        return font.getFamily(Locale.ROOT).equalsIgnoreCase(family) ? font : null;
    }

    private record InstalledStyles(
            String themeResource,
            UiCodeEditorStyle codeEditorStyle
    ) {
    }
}
