package craken.ui.component.visualization;

import craken.visualization.api.ViewLocation;
import craken.visualization.layout.LayoutRequest;
import craken.visualization.layout.LayoutRequest.Orientation;
import craken.visualization.model.ViewNode;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeType;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import java.util.*;
import java.util.function.Function;
import static craken.visualization.layout.LayoutRequest.*;

public final class ViewNodeRenderer {
    public record RenderedNode(Pane view, LayoutRequest.Unit unit, Map<ViewLocation, Pane> members) {
        public RenderedNode { members = Map.copyOf(members); }
    }
    /**
     * Page presentation choice. The default keeps the card-in-card look; BUCKETS renders an array
     * of point cells as one contiguous list with shared dividers and index labels, and draws the
     * surrounding cards in the sectioned design style (centred title, large values, corner labels).
     */
    public record ArrayStyle(boolean contiguous, Orientation orientation, double minimumSlotHeight, boolean sectioned) {
        public static final ArrayStyle CARDS = new ArrayStyle(false, Orientation.HORIZONTAL, 0, false);
        public ArrayStyle {
            Objects.requireNonNull(orientation);
            if (!Double.isFinite(minimumSlotHeight) || minimumSlotHeight < 0)
                throw new IllegalArgumentException("Invalid minimum slot height");
        }
        public static ArrayStyle slots(double minimumSlotHeight) {
            return new ArrayStyle(true, Orientation.VERTICAL, minimumSlotHeight, true);
        }
        /** Sectioned cards without a contiguous list; used for the chain entries of a bucket page. */
        public static ArrayStyle sectionedCards() {
            return new ArrayStyle(false, Orientation.HORIZONTAL, 0, true);
        }
    }
    private static final double PADDING = 10, GAP = 8, MIN_WIDTH = 64;
    private static final double SLOT_WIDTH = 140;
    private static final double SECTIONED_WIDTH = 150;
    private static final double DESIGN_BORDER_WIDTH = 1.25;
    /** 字段值（类型名等）截断长度：长类型不能把卡片撑到超过窄页宽度，否则槽里的文字会被裁掉。 */
    private static final int MAX_FIELD_CHARS = 18;
    private final Font font;
    public ViewNodeRenderer() { this(Font.font("System", 14)); }
    public ViewNodeRenderer(Font font) { this.font = Objects.requireNonNull(font); }
    public RenderedNode render(ViewNode owner, Map<Long, ViewNode> pageNodes, VisualizationTheme theme, Set<ViewLocation> highlights) {
        return render(owner, pageNodes, theme, highlights, ArrayStyle.CARDS);
    }
    public RenderedNode render(ViewNode owner, Map<Long, ViewNode> pageNodes, VisualizationTheme theme,
                               Set<ViewLocation> highlights, ArrayStyle style) {
        requireFxThread(); Objects.requireNonNull(theme);
        return render(owner, pageNodes, highlights, location -> theme, style);
    }
    /** A per-member theme map allows, for example, red and black tree nodes in a single measured macro. */
    public RenderedNode render(ViewNode owner, Map<Long, ViewNode> pageNodes,
                               Map<ViewLocation, VisualizationTheme> themes, Set<ViewLocation> highlights) {
        Objects.requireNonNull(themes, "themes");
        return render(owner, pageNodes, highlights, themes::get, ArrayStyle.CARDS);
    }
    /** The caller's page snapshot stays stable for the duration of one render; no full-page copies are made. */
    private RenderedNode render(ViewNode owner, Map<Long, ViewNode> pageNodes, Set<ViewLocation> highlights,
                                Function<ViewLocation, VisualizationTheme> themes, ArrayStyle style) {
        requireFxThread(); Objects.requireNonNull(owner);
        var selected = Set.copyOf(highlights);
        if (pageNodes.get(owner.location().nodeId()) != owner)
            throw new IllegalArgumentException("Owner must belong to the supplied immutable page snapshot");
        var root = build(owner, pageNodes, selected, themes, style);
        var members = new ArrayList<Member>(); var ports = new ArrayList<Port>(); var obstacles = new ArrayList<Rect>();
        var views = new LinkedHashMap<ViewLocation, Pane>();
        collect(root, 0, 0, members, ports, obstacles, views);
        var unit = new Unit(owner.location(), new Size(root.width, root.height), members, ports, obstacles);
        return new RenderedNode(root.view, unit, views);
    }
    private Card build(ViewNode owner, Map<Long, ViewNode> nodes, Set<ViewLocation> highlights,
                       Function<ViewLocation, VisualizationTheme> themes, ArrayStyle style) {
        var order = new ArrayList<ViewNode>(); var pending = new ArrayDeque<ViewNode>(); pending.push(owner);
        var visited = new HashSet<ViewLocation>();
        while (!pending.isEmpty()) {
            var node = pending.pop();
            if (!visited.add(node.location())) throw new IllegalArgumentException("Cyclic or repeated composition member");
            order.add(node);
            for (int index = node.children().size() - 1; index >= 0; index--) {
                var location = node.children().get(index); var child = nodes.get(location.nodeId());
                if (!location.page().equals(node.location().page()) || child == null || !child.location().equals(location))
                    throw new IllegalArgumentException("Unknown/foreign composition member");
                pending.push(child);
            }
        }
        // A contiguous slot array renders its point children as one list of bare cells.
        var slotCells = new HashSet<ViewLocation>();
        for (var node : order) {
            if (style.contiguous() && node.content().kind() == ViewNode.Kind.ARRAY && !node.children().isEmpty()
                    && node.children().stream().allMatch(child -> {
                        var member = nodes.get(child.nodeId());
                        return member != null && member.content().kind() == ViewNode.Kind.POINT;
                    }))
                slotCells.addAll(node.children());
        }
        var cards = new HashMap<ViewLocation, Card>();
        for (int index = order.size() - 1; index >= 0; index--) {
            var node = order.get(index); var children = node.children().stream().map(cards::get).toList();
            var theme = node.content().color() == null ? themes.apply(node.location()) : VisualizationTheme.of(node.content().color());
            if (theme == null) throw new IllegalArgumentException("Missing validated member theme");
            cards.put(node.location(), new Card(node, children, theme, highlights, style, slotCells.contains(node.location())));
        }
        return cards.get(owner.location());
    }
    private final class Card {
        final ViewNode node;
        final Pane view = new Pane();
        final List<Card> children;
        final List<Text> texts = new ArrayList<>();
        final Map<String, Double> fieldCenters = new LinkedHashMap<>();
        final Rectangle body = new Rectangle(), header = new Rectangle(), clip = new Rectangle();
        final boolean slotArray, slotCell;
        double headerHeight, childrenTop, ownWidth;
        double width, height;
        private VisualizationTheme.Srgb outlineColor;
        private double outlineWidth;
        private double boxX, boxY, boxWidth, slotHeight;
        private Text cellLabel;
        Card(ViewNode node, List<Card> children, VisualizationTheme theme, Set<ViewLocation> highlights,
             ArrayStyle style, boolean slotCell) {
            this.node = node; this.children = List.copyOf(children);
            this.slotCell = slotCell;
            this.slotArray = !slotCell && style.contiguous() && node.content().kind() == ViewNode.Kind.ARRAY
                    && !children.isEmpty() && children.stream().allMatch(child -> child.node.content().kind() == ViewNode.Kind.POINT);
            boolean selected = highlights.contains(node.location());
            boolean ide = theme.ideCard();
            boolean design = style.sectioned();
            view.setManaged(false); view.setUserData(node.location());
            body.setFill(color(theme.bodyFill()));
            body.setStroke(color(selected ? VisualizationTheme.IDE_SELECTION
                    : design ? VisualizationTheme.DESIGN_BORDER
                    : ide ? VisualizationTheme.IDE_BORDER : VisualizationTheme.NODE_BORDER));
            body.setStrokeType(StrokeType.INSIDE);
            body.setStrokeWidth(selected ? VisualizationTheme.SELECTED_BORDER_WIDTH
                    : design ? DESIGN_BORDER_WIDTH : VisualizationTheme.BORDER_WIDTH);
            outlineColor = selected ? VisualizationTheme.IDE_SELECTION
                    : design ? VisualizationTheme.DESIGN_BORDER
                    : ide ? VisualizationTheme.IDE_BORDER : VisualizationTheme.NODE_BORDER;
            outlineWidth = selected ? VisualizationTheme.SELECTED_BORDER_WIDTH
                    : design ? DESIGN_BORDER_WIDTH : VisualizationTheme.BORDER_WIDTH;
            body.setArcWidth(VisualizationTheme.NODE_RADIUS * 2); body.setArcHeight(VisualizationTheme.NODE_RADIUS * 2);
            header.setFill(color(theme.headerFill()));
            clip.setArcWidth(VisualizationTheme.NODE_RADIUS * 2); clip.setArcHeight(VisualizationTheme.NODE_RADIUS * 2);
            view.setClip(clip);
            if (slotCell) { buildSlotCell(); return; }
            if (style.sectioned() && !slotArray) { buildSectioned(selected); return; }
            view.getChildren().add(body);
            double lineHeight = Math.ceil(metrics("Ag").height());
            if (slotArray) {
                // The bucket list carries its title as a caption above the frame, not as a header strip.
                headerHeight = Math.ceil(metrics(node.content().label()).height());
            } else {
                view.getChildren().add(header);
                var title = text(node.content().label());
                Metrics titleMetrics = metrics(title.getText());
                headerHeight = Math.max(lineHeight, Math.ceil(titleMetrics.height())) + 2 * GAP;
                place(title, PADDING, GAP); view.getChildren().add(title); texts.add(title);
            }
            double current = headerHeight + GAP, widest = Math.ceil(metrics(node.content().label()).width());
            for (var field : node.content().fields().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                if (ide) {
                    var name = text(field.getKey()); name.setFill(color(VisualizationTheme.IDE_MUTED_TEXT));
                    var value = text(nullPointer(field.getValue()) ? "NULL" : fieldValue(field.getValue()));
                    if ("NULL".equals(value.getText())) value.setFill(color(VisualizationTheme.IDE_MUTED_TEXT));
                    Metrics nameMetrics = metrics(name.getText()), valueMetrics = metrics(value.getText());
                    double fieldHeight = Math.max(lineHeight,
                            Math.ceil(Math.max(nameMetrics.height(), valueMetrics.height())));
                    double nameWidth = Math.ceil(nameMetrics.width());
                    double valueWidth = Math.ceil(valueMetrics.width());
                    place(name, PADDING, current); place(value, PADDING + nameWidth + GAP, current);
                    texts.add(name); texts.add(value); view.getChildren().addAll(name, value);
                    fieldCenters.put(field.getKey(), current + fieldHeight / 2);
                    widest = Math.max(widest, nameWidth + GAP + valueWidth); current += fieldHeight + GAP;
                    continue;
                }
                var label = text(field.getKey() + ": " + fieldValue(field.getValue()));
                Metrics labelMetrics = metrics(label.getText());
                double fieldHeight = Math.max(lineHeight, Math.ceil(labelMetrics.height()));
                place(label, PADDING, current); texts.add(label); view.getChildren().add(label);
                fieldCenters.put(field.getKey(), current + fieldHeight / 2);
                widest = Math.max(widest, Math.ceil(labelMetrics.width())); current += fieldHeight + GAP;
            }
            childrenTop = current; ownWidth = Math.max(MIN_WIDTH, widest + 2 * PADDING);
            if (slotArray) layoutSlots(style, selected, highlights);
            else { alignMatrixColumns(); reflow(); cardOutline(width, height); }
        }
        /** Bare cell of a contiguous list: the enclosing array draws the frame, dividers and index labels. */
        private void buildSlotCell() {
            cellLabel = text(cellText());
            cellLabel.setFill(color("NULL".equals(node.content().label())
                    ? VisualizationTheme.IDE_MUTED_TEXT : VisualizationTheme.TEXT));
            texts.add(cellLabel); view.getChildren().add(cellLabel);
            fieldCenters.putAll(centeredFieldNames());
            headerHeight = 0; childrenTop = 0; ownWidth = 0;
            width = MIN_WIDTH; height = Math.ceil(metrics("Ag").height()) + 2 * GAP; size();
        }
        private Map<String, Double> centeredFieldNames() {
            var centers = new LinkedHashMap<String, Double>();
            node.content().fields().keySet().forEach(name -> centers.put(name, 0.0));
            return centers;
        }
        private record SectionRow(String key, String valueText, Metrics name, Metrics value) {}
        /** Sectioned design card: centred title strip and muted field names with large white values. */
        private void buildSectioned(boolean selected) {
            view.getChildren().addAll(body, header);
            Font nameFont = sized(-2), valueFont = sized(3);
            var title = text(node.content().label());
            Metrics titleBounds = metrics(title.getText(), font);
            double lineHeight = Math.ceil(metrics("Ag").height());
            headerHeight = Math.ceil(titleBounds.height()) + 2 * GAP;
            var rows = new ArrayList<SectionRow>();
            for (var field : node.content().fields().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                String shown = fieldValue(field.getValue());
                rows.add(new SectionRow(field.getKey(), shown,
                        metrics(field.getKey(), nameFont), metrics(shown, valueFont)));
            }
            double widest = titleBounds.width();
            for (var row : rows) widest = Math.max(widest, row.name().width() + GAP + row.value().width());
            ownWidth = Math.max(SECTIONED_WIDTH, Math.ceil(widest) + 2 * PADDING);
            double current = headerHeight + GAP;
            for (var row : rows) {
                double rowHeight = Math.max(lineHeight, Math.max(row.name().height(), row.value().height()));
                var name = text(row.key(), nameFont); name.setFill(color(VisualizationTheme.IDE_MUTED_TEXT));
                var value = text(row.valueText(), valueFont);
                place(name, PADDING, current + (rowHeight - row.name().height()) / 2);
                // The design centres a short value in the card while keeping it clear of the field name.
                double valueX = Math.max(PADDING + row.name().width() + GAP, (width - row.value().width()) / 2);
                place(value, valueX, current + (rowHeight - row.value().height()) / 2);
                view.getChildren().addAll(name, value); texts.add(name); texts.add(value);
                fieldCenters.put(row.key(), current + rowHeight / 2);
                current += rowHeight + GAP * .75;
            }
            childrenTop = current;
            // 组合子节点（嵌套数组、取值卡片）沿用与普通卡片相同的矩阵排版，不能堆在原点。
            if (children.isEmpty()) { width = ownWidth; height = current + PADDING; }
            else { alignMatrixColumns(); reflow(); }
            place(title, (width - titleBounds.width()) / 2, (headerHeight - titleBounds.height()) / 2);
            view.getChildren().add(title); texts.add(title);
            var separator = new Line(0, headerHeight, width, headerHeight);
            separator.setStroke(color(VisualizationTheme.DESIGN_BORDER)); separator.setStrokeWidth(VisualizationTheme.BORDER_WIDTH);
            view.getChildren().add(separator);
            size();
            cardOutline(width, height);
        }
        private String cellText() {
            var content = node.content();
            // 结构体成员的槽位：名字是字段名，值单独放在 value 字段里，必须一并显示，
            // 否则成员槽只有名字，看起来像未解析的指针/入口。
            String value = content.fields().get("value");
            if (!content.label().isBlank() && value != null && !value.isBlank())
                return content.label() + "  " + value;
            if (!content.label().isBlank()) return content.label();
            if (!content.fields().isEmpty()) return String.join(" ", content.fields().values());
            return "";
        }
        /** Vertical contiguous list with an outside index gutter, a caption and shared dividers. */
        private void layoutSlots(ArrayStyle style, boolean selected, Set<ViewLocation> highlights) {
            double lineHeight = Math.ceil(metrics("Ag").height());
            double widest = SLOT_WIDTH - 2 * PADDING;
            for (var child : children) widest = Math.max(widest, metrics(child.cellText()).width() + 24);
            double gutter = 0;
            for (int index = 0; index < children.size(); index++) gutter = Math.max(gutter, metrics("[" + index + "]").width());
            slotHeight = Math.max(style.minimumSlotHeight(), lineHeight + 2 * GAP);
            boxWidth = Math.max(SLOT_WIDTH, widest + 2 * PADDING);
            boxX = PADDING + gutter + GAP; boxY = childrenTop;
            double captionWidth = metrics(node.content().label()).width();
            if (captionWidth > boxWidth) boxWidth = captionWidth;
            // 同一张卡上也可能有字段行（例如 type=std::map<...>）：帧与卡片宽度必须盖住它们，
            // 否则文本障碍越出单元，布局校验抛异常，页面就画不出来。
            boxWidth = Math.max(boxWidth, ownWidth - boxX - PADDING);
            width = Math.max(ownWidth, boxX + boxWidth + PADDING);
            height = boxY + slotHeight * children.size() + PADDING;
            var caption = text(node.content().label());
            place(caption, boxX, PADDING);
            caption.setFill(color(selected ? VisualizationTheme.IDE_SELECTION : VisualizationTheme.TEXT));
            view.getChildren().add(caption); texts.add(caption);
            for (int index = 0; index < children.size(); index++) {
                double top = boxY + index * slotHeight;
                if (index > 0) {
                    var divider = new Line(boxX, top, boxX + boxWidth, top);
                    divider.setStroke(color(VisualizationTheme.DESIGN_BORDER)); divider.setStrokeWidth(VisualizationTheme.BORDER_WIDTH);
                    view.getChildren().add(divider);
                }
                var child = children.get(index);
                if (highlights.contains(child.node.location())) {
                    var tint = new Rectangle(boxX, top, boxWidth, slotHeight);
                    tint.setFill(color(VisualizationTheme.IDE_SELECTION_TINT));
                    tint.setStroke(color(VisualizationTheme.IDE_SELECTION)); tint.setStrokeType(StrokeType.INSIDE);
                    tint.setStrokeWidth(VisualizationTheme.SELECTED_BORDER_WIDTH);
                    tint.setArcWidth(VisualizationTheme.NODE_RADIUS * 2); tint.setArcHeight(VisualizationTheme.NODE_RADIUS * 2);
                    view.getChildren().add(tint);
                }
                var indexLabel = indexLabel(index);
                view.getChildren().add(indexLabel); texts.add(indexLabel);
                child.setSlotSize(boxWidth, slotHeight);
                child.view.relocate(boxX, top);
            }
            fieldCenters.putAll(centeredFieldNames());
            size();
            // The shared frame, not the card, carries the contiguous list geometry.
            body.setWidth(boxWidth); body.setHeight(slotHeight * children.size());
            body.relocate(boxX, boxY);
            if (selected) cardOutline(width, height);
        }
        /**
         * The complete card frame. It is drawn last so the header strip cannot cover the top edge,
         * and its inside stroke keeps every edge within the measured bounds.
         */
        private void cardOutline(double cardWidth, double cardHeight) {
            double radius = VisualizationTheme.NODE_RADIUS;
            var outline = new javafx.scene.shape.Path();
            outline.setFill(null); outline.setStroke(color(outlineColor));
            outline.setStrokeWidth(this.outlineWidth);
            outline.setStrokeType(StrokeType.INSIDE); outline.setMouseTransparent(true);
            outline.getElements().addAll(
                    new javafx.scene.shape.MoveTo(radius, 0),
                    new javafx.scene.shape.LineTo(cardWidth - radius, 0),
                    new javafx.scene.shape.ArcTo(radius, radius, 0, cardWidth, radius, false, true),
                    new javafx.scene.shape.LineTo(cardWidth, cardHeight - radius),
                    new javafx.scene.shape.ArcTo(radius, radius, 0, cardWidth - radius, cardHeight, false, true),
                    new javafx.scene.shape.LineTo(radius, cardHeight),
                    new javafx.scene.shape.ArcTo(radius, radius, 0, 0, cardHeight - radius, false, true),
                    new javafx.scene.shape.LineTo(0, radius),
                    new javafx.scene.shape.ArcTo(radius, radius, 0, radius, 0, false, true),
                    new javafx.scene.shape.ClosePath());
            view.getChildren().add(outline);
        }
        private Text indexLabel(int index) {
            var label = text("[" + index + "]");
            label.setFill(color(VisualizationTheme.IDE_MUTED_TEXT));
            Metrics bounds = metrics(label.getText());
            double gutterRight = boxX - GAP;
            place(label, gutterRight - bounds.width(), boxY + index * slotHeight + (slotHeight - bounds.height()) / 2);
            return label;
        }
        void setSlotSize(double slotWidth, double slotHeight) {
            width = slotWidth; height = slotHeight; size();
            fieldCenters.replaceAll((name, center) -> slotHeight / 2);
            if (cellLabel != null) {
                Metrics bounds = metrics(cellLabel.getText());
                place(cellLabel, Math.max(PADDING, (slotWidth - bounds.width()) / 2),
                        Math.max(GAP, (slotHeight - bounds.height()) / 2));
            }
        }
        private boolean matrix() {
            return node.content().kind() == ViewNode.Kind.ARRAY && !children.isEmpty()
                    && children.stream().allMatch(c -> c.node.content().kind() == ViewNode.Kind.ARRAY);
        }
        private void alignMatrixColumns() {
            if (!matrix()) return;
            int count = children.stream().mapToInt(c -> c.children.size()).max().orElse(0);
            var columns = new double[count];
            for (var row : children) for (int i = 0; i < row.children.size(); i++) columns[i] = Math.max(columns[i], row.children.get(i).width);
            for (var row : children) {
                for (int i = 0; i < row.children.size(); i++) row.children.get(i).expandWidth(columns[i]);
                row.reflow();
            }
            double rowWidth = children.stream().mapToDouble(c -> c.width).max().orElse(0);
            children.forEach(c -> c.expandWidth(rowWidth));
        }
        private void reflow() {
            boolean vertical = matrix();
            double x = PADDING, y = childrenTop, childWidth = 0, childHeight = 0;
            for (var child : children) {
                child.view.relocate(x, y);
                childWidth = Math.max(childWidth, x + child.width - PADDING);
                childHeight = Math.max(childHeight, y + child.height - childrenTop);
                if (vertical) y += child.height + GAP; else x += child.width + GAP;
            }
            width = Math.max(ownWidth, childWidth + 2 * PADDING);
            height = childrenTop + Math.max(children.isEmpty() ? GAP : 0, childHeight) + PADDING;
            size();
        }
        private void expandWidth(double minimum) { width = Math.max(width, minimum); size(); }
        private void size() {
            view.setMinSize(width, height); view.setPrefSize(width, height); view.setMaxSize(width, height); view.resize(width, height);
            body.setWidth(width); body.setHeight(height); header.setWidth(width); header.setHeight(headerHeight);
            clip.setWidth(width); clip.setHeight(height);
        }
    }
    private Text text(String value) {
        return text(value, font);
    }
    /** 字段值超过上限时截断并加省略号；卡片宽度因此有界，窄页里的槽文字不会被挤到可视区外。 */
    private static String fieldValue(String value) {
        return value.length() <= MAX_FIELD_CHARS ? value
                : value.substring(0, MAX_FIELD_CHARS - 1) + "…";
    }
    private Text text(String value, Font typeface) {
        var text = new Text(value); text.setFont(typeface); text.setFill(color(VisualizationTheme.TEXT)); text.setManaged(false);
        return text;
    }
    private Font sized(double delta) { return Font.font(font.getFamily(), Math.max(8, font.getSize() + delta)); }
    private void place(Text text, double x, double y) {
        Metrics bounds = metrics(text.getText(), text.getFont());
        text.setLayoutX(x - bounds.minX()); text.setLayoutY(y - bounds.minY());
    }
    private record Metrics(double width, double height, double minX, double minY) { }
    private record MetricsKey(String font, String text) { }
    private static final Map<MetricsKey, Metrics> METRICS =
            Collections.synchronizedMap(new LinkedHashMap<>(1024, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<MetricsKey, Metrics> eldest) { return size() > 8192; }
            });
    private Metrics metrics(String value) { return metrics(value, font); }
    private Metrics metrics(String value, Font typeface) {
        MetricsKey key = new MetricsKey(typeface.toString(), value);
        Metrics cached = METRICS.get(key);
        if (cached != null) return cached;
        Bounds bounds = text(value, typeface).getLayoutBounds();
        Metrics created = new Metrics(bounds.getWidth(), bounds.getHeight(), bounds.getMinX(), bounds.getMinY());
        METRICS.put(key, created);
        return created;
    }
    private record Placed(Card card, double x, double y) {}
    private void collect(Card root, double rootX, double rootY, List<Member> members, List<Port> ports,
                         List<Rect> obstacles, Map<ViewLocation, Pane> views) {
        var pending = new ArrayDeque<Placed>(); pending.push(new Placed(root, rootX, rootY));
        while (!pending.isEmpty()) {
        var placed = pending.pop(); var card = placed.card(); double x = placed.x(), y = placed.y();
        var location = card.node.location(); var rect = new Rect(x, y, card.width, card.height);
        members.add(new Member(location, rect)); views.put(location, card.view);
        ports.add(new Port(PortRef.node(location), rect.center(), Side.AUTO));
        ports.add(new Port(new PortRef(location, "north"), new Point(rect.center().x(), y), Side.NORTH));
        ports.add(new Port(new PortRef(location, "east"), new Point(rect.right(), rect.center().y()), Side.EAST));
        ports.add(new Port(new PortRef(location, "south"), new Point(rect.center().x(), rect.bottom()), Side.SOUTH));
        ports.add(new Port(new PortRef(location, "west"), new Point(x, rect.center().y()), Side.WEST));
        card.fieldCenters.forEach((key, center) -> {
            ports.add(new Port(new PortRef(location, "field:" + key),
                    new Point(rect.right(), y + center), Side.EAST));
            // 西侧同名字段端口让链表/回边可以水平接入同一行，而不是斜接到卡片左中点。
            ports.add(new Port(new PortRef(location, "field-west:" + key),
                    new Point(x, y + center), Side.WEST));
        });
        for (var text : card.texts) {
            Metrics metrics = metrics(text.getText(), text.getFont());
            if (metrics.width() > 0 && metrics.height() > 0)
                obstacles.add(new Rect(x + text.getLayoutX() + metrics.minX(), y + text.getLayoutY() + metrics.minY(),
                        metrics.width(), metrics.height()));
        }
        for (int index = card.children.size() - 1; index >= 0; index--) {
            var child = card.children.get(index);
            pending.push(new Placed(child, x + child.view.getLayoutX(), y + child.view.getLayoutY()));
        }
        if (card != root) { card.view.relocate(x, y); root.view.getChildren().add(card.view); }
        }
    }
    static Color color(VisualizationTheme.Srgb value) { return Color.rgb(value.red(), value.green(), value.blue(), value.opacity()); }
    /** A zero pointer is shown as an explicit NULL instead of the unreadable 0x0. */
    private static boolean nullPointer(String value) { return "0x0".equalsIgnoreCase(value); }
    static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Visualization rendering and measurement require the JavaFX application thread");
    }
}
