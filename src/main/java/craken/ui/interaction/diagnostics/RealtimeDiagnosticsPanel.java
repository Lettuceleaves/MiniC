package craken.ui.interaction.diagnostics;

import craken.compiler.Diagnostic;
import craken.ui.component.layout.UiScroll;
import craken.ui.editor.realtime.RealtimeDiagnostic;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 报错信息面板：绑定到一个编辑器，只展示该编辑器最新分析出的诊断列表。
 * 面板只读、不自动改写源码；列表随分析结果整体刷新，固定按行号排序。
 */
public final class RealtimeDiagnosticsPanel extends BorderPane implements AutoCloseable {
    private final Label title = new Label();
    private final Label status = new Label("无问题");
    private final VBox rows = new VBox(6);
    private final Label empty = new Label("暂无报错信息");
    private volatile List<RealtimeDiagnostic> latest = List.of();
    private boolean closed;

    public RealtimeDiagnosticsPanel(String title) {
        Objects.requireNonNull(title, "title");
        setMinSize(0, 0);
        getStyleClass().addAll("terminal-panel", "realtime-diagnostics-panel");

        this.title.setText(title);
        this.title.getStyleClass().add("terminal-title");
        status.setId("realtime-diagnostics-status");
        status.getStyleClass().add("terminal-status");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, this.title, spacer, status);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 8, 0, 12));
        toolbar.setMinHeight(32);
        toolbar.setPrefHeight(32);
        toolbar.getStyleClass().add("interaction-toolbar");
        setTop(toolbar);

        rows.setMinSize(0, 0);
        rows.setPadding(new Insets(8, 10, 8, 10));
        UiScroll viewport = new UiScroll(rows);
        viewport.getStyleClass().add("realtime-diagnostics-viewport");
        viewport.setFitToWidth(true);
        viewport.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        empty.setId("realtime-diagnostics-empty");
        empty.getStyleClass().add("placeholder-title");
        empty.setMouseTransparent(true);
        empty.visibleProperty().bind(Bindings.isEmpty(rows.getChildren()));
        setCenter(new StackPane(viewport, empty));
    }

    /** 必须在 JavaFX 线程调用；诊断列表按起始行排序后整体替换当前展示。 */
    public void update(List<RealtimeDiagnostic> diagnostics) {
        if (closed) return;
        latest = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        rows.getChildren().clear();
        latest.stream()
                .sorted(Comparator.comparingInt((RealtimeDiagnostic diagnostic) -> diagnostic.range().startLine())
                        .thenComparingInt(diagnostic -> diagnostic.range().startByte()))
                .map(this::row)
                .forEach(rows.getChildren()::add);
        status.setText(summary(latest));
    }

    public List<RealtimeDiagnostic> diagnostics() { return List.copyOf(latest); }

    public String statusText() { return status.getText(); }

    public String titleText() { return title.getText(); }

    public int rowCount() { return rows.getChildren().size(); }

    private Label row(RealtimeDiagnostic diagnostic) {
        String level = switch (diagnostic.severity()) {
            case ERROR -> "错误";
            case WARNING -> "警告";
            case INFO -> "提示";
        };
        String advice = diagnostic.solution().isBlank() ? "" : " · 建议：" + diagnostic.solution();
        Label label = new Label("第 " + diagnostic.range().startLine() + " 行 · [" + diagnostic.code() + "] "
                + level + "：" + diagnostic.message() + advice);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().addAll("realtime-diagnostic-row", switch (diagnostic.severity()) {
            case ERROR -> "realtime-diagnostic-error";
            case WARNING -> "realtime-diagnostic-warning";
            case INFO -> "realtime-diagnostic-info";
        });
        return label;
    }

    private static String summary(List<RealtimeDiagnostic> diagnostics) {
        long errors = diagnostics.stream().filter(item -> item.severity() == Diagnostic.Severity.ERROR).count();
        long warnings = diagnostics.stream().filter(item -> item.severity() == Diagnostic.Severity.WARNING).count();
        long infos = diagnostics.stream().filter(item -> item.severity() == Diagnostic.Severity.INFO).count();
        if (errors == 0 && warnings == 0 && infos == 0) return "无问题";
        StringBuilder text = new StringBuilder();
        appendCount(text, errors, "错误");
        appendCount(text, warnings, "警告");
        appendCount(text, infos, "提示");
        return text.toString();
    }

    private static void appendCount(StringBuilder text, long count, String name) {
        if (count == 0) return;
        if (!text.isEmpty()) text.append(" · ");
        text.append(count).append(" 个").append(name);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        rows.getChildren().clear();
        latest = List.of();
        status.setText("已关闭");
    }
}
