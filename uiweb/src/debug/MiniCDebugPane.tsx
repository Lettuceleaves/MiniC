import { useEffect, useMemo, useState, type ReactNode } from "react";
import MiniCSourceLoaderView from "../source/MiniCSourceLoaderView";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type {
  UiAssemblyLineVisualDto,
  UiDebugAsmViewDto,
  UiDebugAstViewDto,
  UiDebugBreakpointDto,
  UiDebugDataStructureViewDto,
  UiDebugEventDto,
  UiDebugFrameDto,
  UiDebugIrViewDto,
  UiDebugIrOperandDto,
  UiDebugMetadataViewDto,
  UiDebugProcessSpaceDto,
  UiDebugTimelineItemDto,
  UiDebugVariableDto,
  UiIrLineVisualDto,
  UiSourceSpanDto,
} from "../translation/uiapi";
import type { MiniCWorkbenchSnapshot, MiniCWorkbenchViewModel } from "../workbench/MiniCWorkbenchViewModel";
import { MiniCAssemblyTextHighlighter } from "../text/MiniCAssemblyTextHighlighter";
import { MiniCIrTextHighlighter } from "../text/MiniCIrTextHighlighter";
import { textFlow } from "../text/MiniCTextFlowFactory";
import { MiniCDebugAstGraphRenderer } from "./MiniCDebugAstGraphRenderer";
import { MiniCDebugTextFormatter } from "./MiniCDebugTextFormatter";
import { MiniCDebugVisualDiagramRenderer } from "./MiniCDebugVisualDiagramRenderer";

export const miniCDebugPaneMirror = {
  "javaPath": "src/main/java/minic/uilocal/debug/MiniCDebugPane.java",
  "webPath": "uiweb/src/debug/MiniCDebugPane.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCDebugPane",
  "kind": "component",
  "imports": [
    "java.util.ArrayList",
    "java.util.LinkedHashSet",
    "java.util.List",
    "java.util.Objects",
    "java.util.stream.Collectors",
    "javafx.beans.binding.Bindings",
    "javafx.geometry.Orientation",
    "javafx.geometry.Point2D",
    "javafx.geometry.Pos",
    "javafx.scene.Node",
    "javafx.scene.control.Button",
    "javafx.scene.control.Label",
    "javafx.scene.control.ScrollPane",
    "javafx.scene.control.Slider",
    "javafx.scene.control.SplitPane",
    "javafx.scene.control.Tooltip",
    "javafx.scene.input.KeyCode",
    "javafx.scene.input.KeyEvent",
    "javafx.scene.input.ScrollEvent",
    "javafx.scene.layout.GridPane",
    "javafx.scene.layout.HBox",
    "javafx.scene.layout.Pane",
    "javafx.scene.layout.Priority",
    "javafx.scene.layout.VBox",
    "javafx.scene.text.TextFlow",
    "minic.uiapi.UiAssemblyLineVisualDto",
    "minic.uiapi.UiDebugAsmViewDto",
    "minic.uiapi.UiDebugAstViewDto",
    "minic.uiapi.UiDebugDataStructureViewDto",
    "minic.uiapi.UiDebugIrViewDto",
    "minic.uiapi.UiDebugMetadataViewDto",
    "minic.uiapi.UiDebugVisualElementDto",
    "minic.uiapi.UiDebugVisualStructureDto",
    "minic.uiapi.UiIrLineVisualDto",
    "minic.uiapi.UiSourceSpanDto",
    "minic.uilocal.control.MiniCActiveTrackingService",
    "minic.uilocal.control.MiniCControlTargetType",
    "minic.uilocal.control.MiniCWorkbenchControlHub",
    "minic.uilocal.text.MiniCAssemblyTextHighlighter",
    "minic.uilocal.text.MiniCExplanationTextHighlighter",
    "minic.uilocal.text.MiniCIrTextHighlighter",
    "minic.uilocal.text.MiniCTextFlowFactory"
  ],
  "fields": [
    {
      "name": "assemblyTextHighlighter",
      "signature": "private final MiniCAssemblyTextHighlighter assemblyTextHighlighter="
    },
    {
      "name": "astGraphRenderer",
      "signature": "private final MiniCDebugAstGraphRenderer astGraphRenderer"
    },
    {
      "name": "astZoom",
      "signature": "private final Slider astZoom="
    },
    {
      "name": "controlHub",
      "signature": "private final MiniCWorkbenchControlHub controlHub"
    },
    {
      "name": "DEBUG_BUTTON_HEIGHT",
      "signature": "private static final double DEBUG_BUTTON_HEIGHT="
    },
    {
      "name": "DEBUG_CONTROL_BUTTON_WIDTH",
      "signature": "private static final double DEBUG_CONTROL_BUTTON_WIDTH="
    },
    {
      "name": "DEBUG_SHORTCUT_ACTIONS",
      "signature": "private static final List<String>DEBUG_SHORTCUT_ACTIONS="
    },
    {
      "name": "DEBUG_VIEWS",
      "signature": "private static final List<DebugView>DEBUG_VIEWS="
    },
    {
      "name": "debugBody",
      "signature": "private final HBox debugBody="
    },
    {
      "name": "DEFAULT_AST_ZOOM",
      "signature": "private static final double DEFAULT_AST_ZOOM="
    },
    {
      "name": "explanationTextHighlighter",
      "signature": "private final MiniCExplanationTextHighlighter explanationTextHighlighter="
    },
    {
      "name": "irTextHighlighter",
      "signature": "private final MiniCIrTextHighlighter irTextHighlighter="
    },
    {
      "name": "keyBindings",
      "signature": "private final MiniCKeyBindingConfig keyBindings="
    },
    {
      "name": "MAX_AST_ZOOM",
      "signature": "private static final double MAX_AST_ZOOM="
    },
    {
      "name": "METADATA_LIST_LIMIT",
      "signature": "private static final int METADATA_LIST_LIMIT="
    },
    {
      "name": "MIN_AST_ZOOM",
      "signature": "private static final double MIN_AST_ZOOM="
    },
    {
      "name": "pressedKeys",
      "signature": "private final LinkedHashSet<KeyCode>pressedKeys="
    },
    {
      "name": "primaryContent",
      "signature": "private final VBox primaryContent="
    },
    {
      "name": "selectedSplitViewId",
      "signature": "private String selectedSplitViewId="
    },
    {
      "name": "selectedViewId",
      "signature": "private String selectedViewId="
    },
    {
      "name": "sourceView",
      "signature": "private final MiniCSourceLoaderView sourceView"
    },
    {
      "name": "splitContent",
      "signature": "private final VBox splitContent="
    },
    {
      "name": "splitVisible",
      "signature": "private boolean splitVisible"
    },
    {
      "name": "status",
      "signature": "private final Label status="
    },
    {
      "name": "TEXT_ZOOM_STEP",
      "signature": "private static final double TEXT_ZOOM_STEP="
    },
    {
      "name": "viewModel",
      "signature": "private final MiniCWorkbenchViewModel viewModel"
    },
    {
      "name": "VIEWPORT_KEY_SCROLL_DELTA",
      "signature": "private static final double VIEWPORT_KEY_SCROLL_DELTA="
    },
    {
      "name": "viewportController",
      "signature": "private final MiniCDebugViewportController viewportController"
    },
    {
      "name": "viewSelector",
      "signature": "private final VBox viewSelector="
    },
    {
      "name": "viewSplitPane",
      "signature": "private final SplitPane viewSplitPane="
    },
    {
      "name": "visualDiagramRenderer",
      "signature": "private final MiniCDebugVisualDiagramRenderer visualDiagramRenderer="
    },
    {
      "name": "workspaceSplitPane",
      "signature": "private final SplitPane workspaceSplitPane="
    }
  ],
  "methods": [
    {
      "name": "addSummaryRow",
      "signature": "addSummaryRow(GridPane grid,int row,String key,String value)"
    },
    {
      "name": "asmContent",
      "signature": "asmContent(UiDebugAsmViewDto view,String viewportKey)"
    },
    {
      "name": "asmLineRow",
      "signature": "asmLineRow(UiAssemblyLineVisualDto line)"
    },
    {
      "name": "asmText",
      "signature": "asmText(UiDebugAsmViewDto view)"
    },
    {
      "name": "astContent",
      "signature": "astContent(UiDebugAstViewDto view,String viewportKey)"
    },
    {
      "name": "astSummary",
      "signature": "astSummary(UiDebugAstViewDto view)"
    },
    {
      "name": "astText",
      "signature": "astText(UiDebugAstViewDto view)"
    },
    {
      "name": "boundedLines",
      "signature": "boundedLines(List<String>lines)"
    },
    {
      "name": "button",
      "signature": "button(String text,String commandId,String tooltipText)"
    },
    {
      "name": "compactVisualCounts",
      "signature": "compactVisualCounts(List<UiDebugVisualElementDto>elements)"
    },
    {
      "name": "compactVisualElementLines",
      "signature": "compactVisualElementLines(UiDebugVisualStructureDto visual)"
    },
    {
      "name": "configureDebugBody",
      "signature": "configureDebugBody()"
    },
    {
      "name": "contentFor",
      "signature": "contentFor(String viewId,boolean split)"
    },
    {
      "name": "controls",
      "signature": "controls()"
    },
    {
      "name": "currentSourceLine",
      "signature": "currentSourceLine()"
    },
    {
      "name": "currentSourceRange",
      "signature": "currentSourceRange()"
    },
    {
      "name": "dataContent",
      "signature": "dataContent(UiDebugDataStructureViewDto view,String viewportKey)"
    },
    {
      "name": "dataText",
      "signature": "dataText(UiDebugDataStructureViewDto view)"
    },
    {
      "name": "debugStarted",
      "signature": "debugStarted()"
    },
    {
      "name": "DebugView",
      "signature": "DebugView(String id,String title)"
    },
    {
      "name": "debugViewportKey",
      "signature": "debugViewportKey(String viewId,boolean split)"
    },
    {
      "name": "explanationText",
      "signature": "explanationText(String text,String styleClass)"
    },
    {
      "name": "explanationTooltip",
      "signature": "explanationTooltip(String text)"
    },
    {
      "name": "formatPairedButton",
      "signature": "formatPairedButton(Button button)"
    },
    {
      "name": "formatSingleButton",
      "signature": "formatSingleButton(Button button)"
    },
    {
      "name": "handleDebugCommandShortcut",
      "signature": "handleDebugCommandShortcut(KeyEvent event)"
    },
    {
      "name": "handleDebugCommandShortcut",
      "signature": "handleDebugCommandShortcut(ScrollEvent event)"
    },
    {
      "name": "handleKeyPressed",
      "signature": "handleKeyPressed(KeyEvent event)"
    },
    {
      "name": "handleKeyReleased",
      "signature": "handleKeyReleased(KeyEvent event)"
    },
    {
      "name": "handleShortcut",
      "signature": "handleShortcut(KeyEvent event)"
    },
    {
      "name": "handleShortcut",
      "signature": "handleShortcut(ScrollEvent event)"
    },
    {
      "name": "handleViewportShortcut",
      "signature": "handleViewportShortcut(KeyEvent event)"
    },
    {
      "name": "handleViewportShortcut",
      "signature": "handleViewportShortcut(ScrollEvent event)"
    },
    {
      "name": "installExplanationTooltip",
      "signature": "installExplanationTooltip(Node node,UiDebugVisualElementDto element)"
    },
    {
      "name": "irContent",
      "signature": "irContent(UiDebugIrViewDto view,String viewportKey)"
    },
    {
      "name": "irLineRow",
      "signature": "irLineRow(UiIrLineVisualDto line)"
    },
    {
      "name": "irText",
      "signature": "irText(UiDebugIrViewDto view)"
    },
    {
      "name": "isModifier",
      "signature": "isModifier(KeyCode code)"
    },
    {
      "name": "label",
      "signature": "label(String text,String styleClass)"
    },
    {
      "name": "lockButtonSize",
      "signature": "lockButtonSize(Button button,double width)"
    },
    {
      "name": "metadataContent",
      "signature": "metadataContent(UiDebugMetadataViewDto view,String viewportKey)"
    },
    {
      "name": "metadataSection",
      "signature": "metadataSection(String title,List<String>lines)"
    },
    {
      "name": "metadataSummary",
      "signature": "metadataSummary(UiDebugMetadataViewDto view)"
    },
    {
      "name": "metadataText",
      "signature": "metadataText(UiDebugMetadataViewDto view)"
    },
    {
      "name": "processSpaceSection",
      "signature": "processSpaceSection(String title,List<String>lines)"
    },
    {
      "name": "refresh",
      "signature": "refresh()"
    },
    {
      "name": "refreshViewButtons",
      "signature": "refreshViewButtons()"
    },
    {
      "name": "registerDebuggerCommands",
      "signature": "registerDebuggerCommands()"
    },
    {
      "name": "rememberViewport",
      "signature": "rememberViewport(ScrollPane scroll,String viewportKey)"
    },
    {
      "name": "runtimeSummary",
      "signature": "runtimeSummary(UiDebugDataStructureViewDto view)"
    },
    {
      "name": "scroll",
      "signature": "scroll(String text,String viewportKey)"
    },
    {
      "name": "scroll",
      "signature": "scroll(String text)"
    },
    {
      "name": "setPrimaryContent",
      "signature": "setPrimaryContent(Node content)"
    },
    {
      "name": "setSplitContent",
      "signature": "setSplitContent(Node content)"
    },
    {
      "name": "startFromBeginning",
      "signature": "startFromBeginning()"
    },
    {
      "name": "toggleSplit",
      "signature": "toggleSplit()"
    },
    {
      "name": "viewButton",
      "signature": "viewButton(DebugView view)"
    },
    {
      "name": "viewportZoomDelta",
      "signature": "viewportZoomDelta(double direction)"
    },
    {
      "name": "visualCard",
      "signature": "visualCard(UiDebugVisualStructureDto visual)"
    },
    {
      "name": "visualCards",
      "signature": "visualCards(List<UiDebugVisualStructureDto>visuals)"
    },
    {
      "name": "wrap",
      "signature": "wrap(Node content,String viewportKey)"
    },
    {
      "name": "wrap",
      "signature": "wrap(Node content)"
    }
  ]
} as const satisfies JavaMirrorFile;

const irTextHighlighter = new MiniCIrTextHighlighter();
const assemblyTextHighlighter = new MiniCAssemblyTextHighlighter();

export interface MiniCDebugPaneProps {
  readonly viewModel: MiniCWorkbenchViewModel;
  readonly sourceView?: ReactNode;
}

type DebugViewId = "metadata" | "data" | "ast" | "ir" | "asm";

const DEBUG_VIEWS: readonly { readonly id: DebugViewId; readonly title: string }[] = [
  { id: "metadata", title: "元数据" },
  { id: "data", title: "数据结构" },
  { id: "ast", title: "AST" },
  { id: "ir", title: "IR" },
  { id: "asm", title: "ASM" },
];

export function MiniCDebugPane({ viewModel, sourceView }: MiniCDebugPaneProps) {
  const snapshot = useDebugSnapshot(viewModel);
  const formatter = useMemo(() => new MiniCDebugTextFormatter(), []);
  const [selectedViewId, setSelectedViewId] = useState<DebugViewId>("metadata");
  const debugSourceView = sourceView ?? (
    <MiniCSourceLoaderView
      className="debug-source-view"
      editorScrollClassName="debug-source-editor-scroll"
      showControls={false}
      viewModel={viewModel}
    />
  );

  return (
    <section className="debug-pane" data-java-source={miniCDebugPaneMirror.javaPath}>
      {controls(viewModel, snapshot)}
      <div className="body-text debug-status">{statusText(snapshot)}</div>
      <div className="debug-workspace">
        <nav className="debug-view-selector">
          {DEBUG_VIEWS.map((view) => viewButton(view, selectedViewId, setSelectedViewId))}
        </nav>
        <div className="debug-workspace-split">
          <div className="debug-source-panel">{debugSourceView}</div>
          <div className="debug-view-split">
            <div className="debug-view-content">{contentFor(selectedViewId, snapshot, formatter)}</div>
          </div>
        </div>
      </div>
    </section>
  );
}

MiniCDebugPane.mirror = miniCDebugPaneMirror;

export function controls(viewModel: MiniCWorkbenchViewModel, snapshot: MiniCWorkbenchSnapshot) {
  const started = snapshot.debugStarted;
  return (
    <div className="controls debug-controls">
      <div className="debug-paired-controls">
        <div className="debug-paired-row">
          {button("从头开始", () => viewModel.runInBackground(viewModel.startDebug(), "启动 Debug 失败"), true, "debug-control-single-button")}
          {button("下个断点", () => viewModel.runInBackground(viewModel.debugRunToBreakpoint(), "运行到断点失败"), started, "debug-control-paired-button")}
          {button("本层下一句", () => viewModel.runInBackground(viewModel.debugStepOver(), "本层下一句失败"), started, "debug-control-paired-button")}
          {button("下一句", () => viewModel.runInBackground(viewModel.debugStepInto(), "下一句失败"), started, "debug-control-paired-button")}
        </div>
        <div className="debug-paired-row">
          {button("运行到结束", () => viewModel.runInBackground(viewModel.debugRunToEnd(), "运行到结束失败"), started, "debug-control-single-button")}
          {button("上个断点", () => viewModel.runInBackground(viewModel.debugBackToBreakpoint(), "上个断点失败"), started, "debug-control-paired-button")}
          {button("本层上一句", () => viewModel.runInBackground(viewModel.debugStepBackOver(), "本层上一句失败"), started, "debug-control-paired-button")}
          {button("上一句", () => viewModel.runInBackground(viewModel.debugStepBack(), "上一句失败"), started, "debug-control-paired-button")}
        </div>
      </div>
    </div>
  );
}

export function button(text: string, action: () => void, enabled: boolean, styleClass = "debug-control-single-button") {
  return (
    <button className={`${styleClass} control-secondary`} disabled={!enabled} onClick={action} type="button">
      {text}
    </button>
  );
}

export function debugStarted(snapshot: MiniCWorkbenchSnapshot): boolean {
  return snapshot.debugStarted;
}

export function viewButton(
  view: { readonly id: DebugViewId; readonly title: string },
  selectedViewId: DebugViewId,
  select: (id: DebugViewId) => void,
) {
  return (
    <button
      className={`debug-view-button${view.id === selectedViewId ? " active" : ""}`}
      key={view.id}
      onClick={() => select(view.id)}
      type="button"
    >
      {view.title}
    </button>
  );
}

export function contentFor(
  viewId: DebugViewId,
  snapshot: MiniCWorkbenchSnapshot,
  formatter: MiniCDebugTextFormatter,
) {
  if (!snapshot.debugStarted || snapshot.debugState === null) return emptyDebugContent();
  if (viewId === "metadata") return metadataContent(snapshot.debugMetadataView, formatter);
  if (viewId === "data") return dataContent(snapshot.debugDataStructureView, formatter);
  if (viewId === "ast") return astContent(snapshot.debugAstView, formatter);
  if (viewId === "ir") return irContent(snapshot.debugIrView, formatter);
  return asmContent(snapshot.debugAsmView);
}

export function statusText(snapshot: MiniCWorkbenchSnapshot): string {
  if (!snapshot.debugStarted || snapshot.debugState === null) {
    return "Debug 未启动";
  }
  const current = snapshot.debugState.currentSnapshot;
  return `Debug ${snapshot.debugState.executionState} · ${current.stopReason} · step ${current.visibleStepIndex} · ${current.functionName}`;
}

export function emptyDebugContent() {
  return <div className="visual-scroll debug-empty-content" />;
}

export function metadataContent(view: UiDebugMetadataViewDto | null, formatter: MiniCDebugTextFormatter) {
  if (view === null) {
    return <div className="debug-metadata" />;
  }
  return (
    <div className="debug-metadata">
      {metadataSummary(view, formatter)}
      {metadataSection("调用栈", view.callStack.map((frame) => formatter.frameText(frame)))}
      {metadataSection("变量", variableLines(view.variables, formatter))}
      {metadataSection("断点", view.breakpoints.map((breakpoint) => formatter.breakpointText(breakpoint)))}
      {metadataSection("事件日志", boundedLines(view.events.map((event) => formatter.eventText(event))))}
      {metadataSection("Snapshot 时间线", boundedLines(view.timeline.map((item) => formatter.timelineText(item))))}
      {metadataSection("stdout", [view.stdout.trim().length === 0 ? "(empty)" : view.stdout])}
      {metadataSection("stderr", [view.stderr.trim().length === 0 ? "(empty)" : view.stderr])}
    </div>
  );
}

export function metadataSummary(view: UiDebugMetadataViewDto, formatter: MiniCDebugTextFormatter) {
  return (
    <div className="debug-summary-grid">
      {summaryRow("状态", view.executionState)}
      {summaryRow("停止原因", view.stopReason)}
      {summaryRow("函数", view.currentFunction)}
      {summaryRow("源码", formatter.rangeText(view.currentSourceRange))}
    </div>
  );
}

export function summaryRow(key: string, value: string | null | undefined) {
  return (
    <div className="debug-summary-row" key={key}>
      <span className="debug-summary-key">{key}</span>
      <span className="debug-summary-value">{value && value.trim().length > 0 ? value : "(empty)"}</span>
    </div>
  );
}

export function metadataSection(title: string, lines: readonly string[]) {
  return (
    <section className="debug-section">
      <h3 className="debug-section-title">{title}</h3>
      <div className="debug-section-body">{boundedLines(lines).map((line) => label(line, "debug-section-line"))}</div>
    </section>
  );
}

export function boundedLines(lines: readonly string[]): readonly string[] {
  if (lines.length === 0) {
    return ["(empty)"];
  }
  if (lines.length <= 200) {
    return lines;
  }
  const omitted = lines.length - 200;
  return [`(已省略较早的 ${omitted} 条，显示最近 200 条)`, ...lines.slice(omitted)];
}

export function dataContent(view: UiDebugDataStructureViewDto | null, formatter: MiniCDebugTextFormatter) {
  if (view === null) {
    return <div className="debug-data-space" />;
  }
  return (
    <div className="debug-data-space">
      {processSpaceSection("runtime", runtimeSummary(view.processSpace, formatter))}
      <MiniCDebugVisualDiagramRenderer view={view} />
      {metadataSection("warnings", view.warnings)}
    </div>
  );
}

export function runtimeSummary(processSpace: UiDebugProcessSpaceDto, formatter: MiniCDebugTextFormatter): readonly string[] {
  return [
    `current: ${processSpace.currentFunctionName} / ${processSpace.currentInstructionId}`,
    `functions=${processSpace.functions.length} · stackFrames=${processSpace.stackFrames.length} · heapEntries=${processSpace.heapValues.length}`,
    `stdout: ${formatter.emptyText(processSpace.stdout)}`,
  ];
}

export function processSpaceSection(title: string, lines: readonly string[]) {
  return (
    <section className="debug-process-section">
      <h3 className="debug-process-title">{title}</h3>
      <div className="debug-section-body">{boundedLines(lines).map((line) => label(line, "debug-section-line"))}</div>
    </section>
  );
}

export function astContent(view: UiDebugAstViewDto | null, formatter: MiniCDebugTextFormatter) {
  return (
    <div className="debug-ast-view">
      {astSummary(view, formatter)}
      <MiniCDebugAstGraphRenderer view={view} />
    </div>
  );
}

export function astSummary(view: UiDebugAstViewDto | null, formatter: MiniCDebugTextFormatter) {
  if (view === null || view.activeNode === null) {
    return metadataSection("当前 AST 节点", ["(empty)"]);
  }
  return metadataSection("当前 AST 节点", [
    `${view.activeNode.kind} ${view.activeNode.label}`,
    `range: ${formatter.rangeText(view.activeNode.sourceRange)}`,
    view.activeNode.explanation,
    `IR: ${view.relatedIrIds.join(", ")}`,
    `ASM: ${view.relatedAsmIds.join(", ")}`,
  ]);
}

export function irContent(view: UiDebugIrViewDto | null, formatter: MiniCDebugTextFormatter) {
  if (view === null) {
    return <div className="debug-code-view" />;
  }
  return (
    <div className="debug-code-view">
      {metadataSection("IR", [
        view.explanation,
        `current: ${view.currentInstructionId}`,
        `range: ${formatter.rangeText(view.currentSourceRange)}`,
      ])}
      <div className="debug-code-rows">{view.lines.map(irLineRow)}</div>
      {metadataSection("operands", view.operands.map(operandText))}
    </div>
  );
}

export function irLineRow(line: UiIrLineVisualDto) {
  return (
    <div className={`debug-code-row${line.active ? " active" : ""}`} key={line.lineNumber}>
      <span className="debug-code-line-number">{line.lineNumber}</span>
      {textFlow(irTextHighlighter.highlight(line.text), "debug-code-text", line.active)}
    </div>
  );
}

export function operandText(operand: UiDebugIrOperandDto): string {
  return `${operand.name} ${operand.typeName} = ${operand.valueSummary} @ ${operand.valueRef}`;
}

export function asmContent(view: UiDebugAsmViewDto | null) {
  if (view === null) {
    return <div className="debug-code-view" />;
  }
  return (
    <div className="debug-code-view">
      {metadataSection("ASM", [view.explanation, `IR: ${view.relatedIrIds.join(", ")}`])}
      <div className="debug-code-rows">{view.lines.map(asmLineRow)}</div>
    </div>
  );
}

export function asmLineRow(line: UiAssemblyLineVisualDto) {
  return (
    <div className={`debug-code-row${line.active ? " active" : ""}`} key={line.lineNumber}>
      <span className="debug-code-line-number">{line.lineNumber}</span>
      {textFlow(assemblyTextHighlighter.highlight(line.text), "debug-code-text", line.active)}
    </div>
  );
}

export function label(text: string, styleClass: string) {
  return (
    <p className={styleClass} key={text}>
      {text}
    </p>
  );
}

export function variableLines(variables: readonly UiDebugVariableDto[], formatter: MiniCDebugTextFormatter): readonly string[] {
  const lines: string[] = [];
  for (const variable of variables) {
    addVariableLines(lines, variable, 0, formatter);
  }
  return lines;
}

function addVariableLines(
  lines: string[],
  variable: UiDebugVariableDto,
  depth: number,
  formatter: MiniCDebugTextFormatter,
): void {
  lines.push(`${"  ".repeat(depth)}${formatter.variableText(variable).trimStart()}`);
  variable.fields.forEach((field) => addVariableLines(lines, field, depth + 1, formatter));
  variable.elements.forEach((element) => addVariableLines(lines, element, depth + 1, formatter));
}

function useDebugSnapshot(viewModel: MiniCWorkbenchViewModel): MiniCWorkbenchSnapshot {
  const [snapshot, setSnapshot] = useState(() => viewModel.snapshot());

  useEffect(() => {
    setSnapshot(viewModel.snapshot());
    return viewModel.subscribe(() => {
      setSnapshot(viewModel.snapshot());
    });
  }, [viewModel]);

  return snapshot;
}

export default MiniCDebugPane;
