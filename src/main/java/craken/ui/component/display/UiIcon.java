package craken.ui.component.display;

import javafx.scene.Group;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Scale;

import java.util.Objects;

/** 工作台的细线矢量图标：24px 设计网格，默认以 24px 图标盒显示。 */
public final class UiIcon extends Region {
    private static final double SIZE = 24;
    private static final double GRID_SIZE = 24;

    public enum Kind {
        CODE("代码"),
        EXTENSIONS("扩展"),
        SETTINGS("设置"),
        PROFILE("个人主页"),
        RUN("运行"),
        PIPELINE("编译 Pipeline 运行"),
        DEBUGGER("Debugger"),
        FORMAT_CODE("自动整理代码"),
        MORE("更多操作"),
        PREPROCESS("预处理"),
        LEXER("词法分析"),
        PARSER("语法分析"),
        SEMANTIC("语义分析"),
        IR("生成 IR"),
        ASSEMBLY("生成汇编"),
        OBJECT_FILE("生成目标文件"),
        LINK("链接"),
        STEP_FORWARD("下一步"),
        NEXT_STAGE("下一阶段"),
        RESET("重置"),
        CHECK("已完成"),
        CHEVRON_RIGHT("当前阶段");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public UiIcon(Kind kind) {
        this(kind, SIZE);
    }

    public UiIcon(Kind kind, double size) {
        Objects.requireNonNull(kind, "kind");
        if (!Double.isFinite(size) || size <= 0) {
            throw new IllegalArgumentException("size must be positive and finite");
        }
        getStyleClass().add("ui-icon");
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        setMouseTransparent(true);
        setAccessibleText(kind.label());

        Group drawing = drawing(kind);
        drawing.setManaged(false);
        Group viewport = new Group(drawing);
        viewport.setManaged(false);
        viewport.getTransforms().add(new Scale(size / GRID_SIZE, size / GRID_SIZE));
        getChildren().add(viewport);
    }

    private static Group drawing(Kind kind) {
        return switch (kind) {
            case CODE -> new Group(
                    outline("M5 2.75h9l5 5v13.5H5zM14 2.75v5h5", StrokeLineCap.SQUARE),
                    outline("m9.5 11.5-3 3 3 3m5-6 3 3-3 3", StrokeLineCap.SQUARE));
            case EXTENSIONS -> extensions();
            case SETTINGS -> new Group(
                    outline("M10 2.5h4l.5 3 2 .9 2.5-1.2 2 3.4-2.3 1.8v3.2l2.3 1.8-2 3.4"
                            + "-2.5-1.2-2 .9-.5 3h-4l-.5-3-2-.9L5 18.8l-2-3.4 2.3-1.8v-3.2"
                            + "L3 8.6l2-3.4 2.5 1.2 2-.9z"),
                    outline("M15.1 12a3.1 3.1 0 1 1-6.2 0 3.1 3.1 0 1 1 6.2 0z"));
            case PROFILE -> new Group(
                    outline("M21 12a9 9 0 1 1-18 0 9 9 0 1 1 18 0z"),
                    outline("M14.75 8.75a2.75 2.75 0 1 1-5.5 0 2.75 2.75 0 1 1 5.5 0z"),
                    outline("M6.5 19v-1.5a5.5 4 0 0 1 11 0V19"));
            case RUN -> new Group(outline("M7 3.75 20 12 7 20.25z"));
            case PIPELINE -> pipeline();
            case DEBUGGER -> new Group(outline(
                    "M9 7V4.5h6V7M9 4.5 7.5 2.5M15 4.5l1.5-2M6.5 7h11v8a5.5 5.5 0 0 1-11 0z"
                            + "M12 10v10.5M6.5 9.5H3V7M6.5 13.5H2.5M6.5 17.5H3V20"
                            + "M17.5 9.5H21V7M17.5 13.5h4M17.5 17.5H21V20",
                    StrokeLineCap.SQUARE));
            case FORMAT_CODE -> new Group(
                    outline("M4 5h16M10 10h10M10 15h10M4 20h16"),
                    outline("M3 12.5h4M4 9.5l3 3-3 3"));
            case MORE -> new Group(dot(6), dot(12), dot(18));
            case PREPROCESS -> new Group(
                    outline("M5 3h9l5 5v13H5zM14 3v5h5"),
                    outline("m9.5 11-2.5 3 2.5 3m5-6 2.5 3-2.5 3", StrokeLineCap.SQUARE));
            case LEXER -> new Group(
                    outline("M3 7V3h4M17 3h4v4M21 17v4h-4M7 21H3v-4"),
                    outline("M7 7h10M7 12h10M7 17h7"),
                    outline("M3 10v4M21 10v4"));
            case PARSER -> new Group(
                    outline("M10 3h4v4h-4zM2 16h4v5H2zM10 16h4v5h-4zM18 16h4v5h-4z"),
                    outline("M12 7v9M4 16v-4h16v4"));
            case SEMANTIC -> new Group(
                    outline("m3 5 2 2 3-4m-5 9 2 2 3-4m-5 9 2 2 3-4", StrokeLineCap.ROUND),
                    outline("M12 5h9M12 12h9M12 19h9"));
            case IR -> new Group(
                    outline("M3 3h5v5H3zM16 9h5v5h-5zM3 16h5v5H3z"),
                    outline("M8 5.5h4v13H8M12 11.5h4"));
            case ASSEMBLY -> new Group(
                    outline("M3 4h2v4M3 8h4M3 12v-1h4v2l-4 3h4M3 18h4v4H3M4 20h3"),
                    outline("M11 6h10M11 13.5h10M11 20.5h10"));
            case OBJECT_FILE -> new Group(
                    outline("m12 3 9 4.5v9L12 21l-9-4.5v-9z"),
                    outline("m3 7.5 9 4.5 9-4.5M12 12v9M7.5 5.25l9 4.5v4.5"));
            case LINK -> new Group(
                    outline("m9.5 14.5 5-5", StrokeLineCap.ROUND),
                    outline("m8 11-2.5 2.5a4.25 4.25 0 0 0 6 6L14 17"
                            + "m-4-10 2.5-2.5a4.25 4.25 0 0 1 6 6L16 13", StrokeLineCap.ROUND));
            case STEP_FORWARD -> new Group(
                    outline("M4 5v14l10-7zM19 5v14", StrokeLineCap.SQUARE));
            case NEXT_STAGE -> new Group(
                    outline("M3 6v12l7-6zM10 6v12l7-6zM21 6v12", StrokeLineCap.SQUARE));
            case RESET -> new Group(
                    outline("M3.9 12a8.1 8.1 0 1 0 8.1-8.1 8.775 8.775 0 0 0-6.066 2.466L3.9 8.4",
                            StrokeLineCap.ROUND),
                    outline("M3.9 3.9v4.5h4.5", StrokeLineCap.ROUND));
            case CHECK -> new Group(outline("m5 12 4.5 4.5L19 7", StrokeLineCap.ROUND));
            case CHEVRON_RIGHT -> new Group(outline("m9 5 7 7-7 7", StrokeLineCap.ROUND));
        };
    }

    private static Circle dot(double x) {
        Circle dot = new Circle(x, 12, 1.5);
        dot.getStyleClass().add("ui-icon-fill");
        return dot;
    }

    private static Group extensions() {
        SVGPath puzzle = outline(
                "M9 4V3a3 3 0 0 1 6 0v1h3.5A1.5 1.5 0 0 1 20 5.5V9h-1a3 3 0 0 0 0 6h1v3.5"
                        + "a1.5 1.5 0 0 1-1.5 1.5H15v-1a3 3 0 0 0-6 0v1H5.5A1.5 1.5 0 0 1 4 18.5V15"
                        + "H3a3 3 0 0 1 0-6h1V5.5A1.5 1.5 0 0 1 5.5 4z",
                StrokeLineCap.ROUND);
        puzzle.setStrokeLineJoin(StrokeLineJoin.ROUND);
        puzzle.setStrokeWidth(1.666667);
        Group group = new Group(puzzle);
        group.getTransforms().add(new Affine(.9, 0, 1.1, 0, .9, 1.1));
        return group;
    }

    private static Group pipeline() {
        SVGPath path = outline("M15 6H9M6 9v6M9 18h6"
                + "M15 3h6v6h-6zM3 3h6v6H3zM3 15h6v6H3zM15 14.5 21.5 18 15 21.5z");
        // 按定稿连续缩小两次 5%，围绕网格中心缩放；第二次保留原线条粗细。
        path.setStrokeWidth(1.578947);
        Group group = new Group(path);
        group.getTransforms().add(new Affine(.9025, 0, 1.17, 0, .9025, 1.17));
        return group;
    }

    private static SVGPath outline(String content) {
        return outline(content, StrokeLineCap.BUTT);
    }

    private static SVGPath outline(String content, StrokeLineCap lineCap) {
        SVGPath path = new SVGPath();
        path.setContent(content);
        path.setFill(null);
        path.setStrokeWidth(1.5);
        path.setStrokeLineCap(lineCap);
        path.setStrokeLineJoin(StrokeLineJoin.MITER);
        path.getStyleClass().add("ui-icon-stroke");
        return path;
    }
}
