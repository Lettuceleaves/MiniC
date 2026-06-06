import { useEffect, useMemo, useState, type ReactNode } from "react";
import MiniCSourceView from "../source/MiniCSourceView";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type {
  UiAssemblyLineVisualDto,
  UiDebugAsmViewDto,
  UiDebugAstViewDto,
  UiDebugDataStructureViewDto,
  UiDebugIrViewDto,
  UiDebugMetadataViewDto,
  UiIrLineVisualDto,
} from "../translation/uiapi";
import type { MiniCWorkbenchSnapshot, MiniCWorkbenchViewModel } from "../workbench/MiniCWorkbenchViewModel";
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
    "javafx.beans.binding.Bindings",
    "javafx.scene.control.Button",
    "javafx.scene.control.Label",
    "javafx.scene.control.ScrollPane",
    "javafx.scene.control.Slider",
    "javafx.scene.control.SplitPane",
    "javafx.scene.control.Tooltip",
    "javafx.geometry.Orientation",
    "javafx.geometry.Pos",
    "javafx.geometry.Point2D",
    "javafx.scene.Node",
    "javafx.scene.input.KeyCode",
    "javafx.scene.input.KeyEvent",
    "javafx.scene.input.ScrollEvent",
    "javafx.scene.layout.GridPane",
    "javafx.scene.layout.HBox",
    "javafx.scene.layout.Pane",
    "javafx.scene.layout.Priority",
    "javafx.scene.layout.VBox",
    "javafx.scene.text.TextFlow",
    "minic.uilocal.control.MiniCActiveTrackingService",
    "minic.uilocal.control.MiniCControlTargetType",
    "minic.uilocal.control.MiniCWorkbenchControlHub",
    "minic.uilocal.text.MiniCAssemblyTextHighlighter",
    "minic.uilocal.text.MiniCExplanationTextHighlighter",
    "minic.uilocal.text.MiniCIrTextHighlighter",
    "minic.uilocal.text.MiniCTextFlowFactory",
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
    "java.util.ArrayList",
    "java.util.LinkedHashSet",
    "java.util.List",
    "java.util.Objects",
    "java.util.stream.Collectors"
  ],
  "fields": [
    {
      "name": "DEFAULT_AST_ZOOM",
      "signature": "private static final double DEFAULT_AST_ZOOM ="
    },
    {
      "name": "MIN_AST_ZOOM",
      "signature": "private static final double MIN_AST_ZOOM ="
    },
    {
      "name": "MAX_AST_ZOOM",
      "signature": "private static final double MAX_AST_ZOOM ="
    },
    {
      "name": "TEXT_ZOOM_STEP",
      "signature": "private static final double TEXT_ZOOM_STEP ="
    },
    {
      "name": "VIEWPORT_KEY_SCROLL_DELTA",
      "signature": "private static final double VIEWPORT_KEY_SCROLL_DELTA ="
    },
    {
      "name": "DEBUG_SHORTCUT_ACTIONS",
      "signature": "private static final List<String> DEBUG_SHORTCUT_ACTIONS ="
    },
    {
      "name": "DEBUG_CONTROL_BUTTON_WIDTH",
      "signature": "private static final double DEBUG_CONTROL_BUTTON_WIDTH ="
    },
    {
      "name": "DEBUG_BUTTON_HEIGHT",
      "signature": "private static final double DEBUG_BUTTON_HEIGHT ="
    },
    {
      "name": "METADATA_LIST_LIMIT",
      "signature": "private static final int METADATA_LIST_LIMIT ="
    },
    {
      "name": "DEBUG_VIEWS",
      "signature": "private static final List<DebugView> DEBUG_VIEWS ="
    },
    {
      "name": "viewModel",
      "signature": "private final MiniCWorkbenchViewModel viewModel;"
    },
    {
      "name": "sourceView",
      "signature": "private final MiniCSourceLoaderView sourceView;"
    },
    {
      "name": "controlHub",
      "signature": "private final MiniCWorkbenchControlHub controlHub;"
    },
    {
      "name": "debugBody",
      "signature": "private final HBox debugBody ="
    },
    {
      "name": "viewSelector",
      "signature": "private final VBox viewSelector ="
    },
    {
      "name": "workspaceSplitPane",
      "signature": "private final SplitPane workspaceSplitPane ="
    },
    {
      "name": "viewSplitPane",
      "signature": "private final SplitPane viewSplitPane ="
    },
    {
      "name": "primaryContent",
      "signature": "private final VBox primaryContent ="
    },
    {
      "name": "splitContent",
      "signature": "private final VBox splitContent ="
    },
    {
      "name": "irTextHighlighter",
      "signature": "private final MiniCIrTextHighlighter irTextHighlighter ="
    },
    {
      "name": "assemblyTextHighlighter",
      "signature": "private final MiniCAssemblyTextHighlighter assemblyTextHighlighter ="
    },
    {
      "name": "explanationTextHighlighter",
      "signature": "private final MiniCExplanationTextHighlighter explanationTextHighlighter ="
    },
    {
      "name": "visualDiagramRenderer",
      "signature": "private final MiniCDebugVisualDiagramRenderer visualDiagramRenderer ="
    },
    {
      "name": "viewportController",
      "signature": "private final MiniCDebugViewportController viewportController;"
    },
    {
      "name": "astGraphRenderer",
      "signature": "private final MiniCDebugAstGraphRenderer astGraphRenderer;"
    },
    {
      "name": "keyBindings",
      "signature": "private final MiniCKeyBindingConfig keyBindings ="
    },
    {
      "name": "pressedKeys",
      "signature": "private final LinkedHashSet<KeyCode> pressedKeys ="
    },
    {
      "name": "astZoom",
      "signature": "private final Slider astZoom ="
    },
    {
      "name": "status",
      "signature": "private final Label status ="
    },
    {
      "name": "selectedViewId",
      "signature": "private String selectedViewId ="
    },
    {
      "name": "selectedSplitViewId",
      "signature": "private String selectedSplitViewId ="
    },
    {
      "name": "splitVisible",
      "signature": "private boolean splitVisible;"
    }
  ],
  "methods": [
    {
      "name": "controls",
      "signature": "controls()"
    },
    {
      "name": "registerDebuggerCommands",
      "signature": "registerDebuggerCommands()"
    },
    {
      "name": "startFromBeginning",
      "signature": "startFromBeginning()"
    },
    {
      "name": "button",
      "signature": "button(String text, String commandId, String tooltipText)"
    },
    {
      "name": "debugStarted",
      "signature": "debugStarted()"
    },
    {
      "name": "formatSingleButton",
      "signature": "formatSingleButton(Button button)"
    },
    {
      "name": "formatPairedButton",
      "signature": "formatPairedButton(Button button)"
    },
    {
      "name": "lockButtonSize",
      "signature": "lockButtonSize(Button button, double width)"
    },
    {
      "name": "scroll",
      "signature": "scroll(String text)"
    },
    {
      "name": "scroll",
      "signature": "scroll(String text, String viewportKey)"
    },
    {
      "name": "refresh",
      "signature": "refresh()"
    },
    {
      "name": "configureDebugBody",
      "signature": "configureDebugBody()"
    },
    {
      "name": "viewButton",
      "signature": "viewButton(DebugView view)"
    },
    {
      "name": "refreshViewButtons",
      "signature": "refreshViewButtons()"
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
      "name": "toggleSplit",
      "signature": "toggleSplit()"
    },
    {
      "name": "contentFor",
      "signature": "contentFor(String viewId, boolean split)"
    },
    {
      "name": "debugViewportKey",
      "signature": "debugViewportKey(String viewId, boolean split)"
    },
    {
      "name": "metadataContent",
      "signature": "metadataContent(UiDebugMetadataViewDto view, String viewportKey)"
    },
    {
      "name": "metadataSummary",
      "signature": "metadataSummary(UiDebugMetadataViewDto view)"
    },
    {
      "name": "addSummaryRow",
      "signature": "addSummaryRow(GridPane grid, int row, String key, String value)"
    },
    {
      "name": "metadataSection",
      "signature": "metadataSection(String title, List<String> lines)"
    },
    {
      "name": "boundedLines",
      "signature": "boundedLines(List<String> lines)"
    },
    {
      "name": "wrap",
      "signature": "wrap(Node content)"
    },
    {
      "name": "wrap",
      "signature": "wrap(Node content, String viewportKey)"
    },
    {
      "name": "rememberViewport",
      "signature": "rememberViewport(ScrollPane scroll, String viewportKey)"
    },
    {
      "name": "metadataText",
      "signature": "metadataText(UiDebugMetadataViewDto view)"
    },
    {
      "name": "dataText",
      "signature": "dataText(UiDebugDataStructureViewDto view)"
    },
    {
      "name": "dataContent",
      "signature": "dataContent(UiDebugDataStructureViewDto view, String viewportKey)"
    },
    {
      "name": "runtimeSummary",
      "signature": "runtimeSummary(UiDebugDataStructureViewDto view)"
    },
    {
      "name": "processSpaceSection",
      "signature": "processSpaceSection(String title, List<String> lines)"
    },
    {
      "name": "visualCards",
      "signature": "visualCards(List<UiDebugVisualStructureDto> visuals)"
    },
    {
      "name": "visualCard",
      "signature": "visualCard(UiDebugVisualStructureDto visual)"
    },
    {
      "name": "compactVisualCounts",
      "signature": "compactVisualCounts(List<UiDebugVisualElementDto> elements)"
    },
    {
      "name": "compactVisualElementLines",
      "signature": "compactVisualElementLines(UiDebugVisualStructureDto visual)"
    },
    {
      "name": "astText",
      "signature": "astText(UiDebugAstViewDto view)"
    },
    {
      "name": "astContent",
      "signature": "astContent(UiDebugAstViewDto view, String viewportKey)"
    },
    {
      "name": "astSummary",
      "signature": "astSummary(UiDebugAstViewDto view)"
    },
    {
      "name": "irText",
      "signature": "irText(UiDebugIrViewDto view)"
    },
    {
      "name": "irContent",
      "signature": "irContent(UiDebugIrViewDto view, String viewportKey)"
    },
    {
      "name": "irLineRow",
      "signature": "irLineRow(UiIrLineVisualDto line)"
    },
    {
      "name": "asmText",
      "signature": "asmText(UiDebugAsmViewDto view)"
    },
    {
      "name": "asmContent",
      "signature": "asmContent(UiDebugAsmViewDto view, String viewportKey)"
    },
    {
      "name": "asmLineRow",
      "signature": "asmLineRow(UiAssemblyLineVisualDto line)"
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
      "name": "explanationText",
      "signature": "explanationText(String text, String styleClass)"
    },
    {
      "name": "explanationTooltip",
      "signature": "explanationTooltip(String text)"
    },
    {
      "name": "installExplanationTooltip",
      "signature": "installExplanationTooltip(Node node, UiDebugVisualElementDto element)"
    },
    {
      "name": "label",
      "signature": "label(String text, String styleClass)"
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
      "name": "handleDebugCommandShortcut",
      "signature": "handleDebugCommandShortcut(KeyEvent event)"
    },
    {
      "name": "handleViewportShortcut",
      "signature": "handleViewportShortcut(KeyEvent event)"
    },
    {
      "name": "handleShortcut",
      "signature": "handleShortcut(ScrollEvent event)"
    },
    {
      "name": "handleDebugCommandShortcut",
      "signature": "handleDebugCommandShortcut(ScrollEvent event)"
    },
    {
      "name": "handleViewportShortcut",
      "signature": "handleViewportShortcut(ScrollEvent event)"
    },
    {
      "name": "viewportZoomDelta",
      "signature": "viewportZoomDelta(double direction)"
    },
    {
      "name": "isModifier",
      "signature": "isModifier(KeyCode code)"
    },
    {
      "name": "DebugView",
      "signature": "DebugView(String id, String title)"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCDebugPaneProps {
  readonly viewModel: MiniCWorkbenchViewModel;
  readonly sourceView?: ReactNode;
}

type DebugViewId = "metadata" | "data" | "ast" | "ir" | "asm" | "source";

const DEBUG_VIEWS: readonly { readonly id: DebugViewId; readonly title: string }[] = [
  { id: "metadata", title: "元数据" },
  { id: "data", title: "数据" },
  { id: "ast", title: "AST" },
  { id: "ir", title: "IR" },
  { id: "asm", title: "ASM" },
  { id: "source", title: "源码" },
];

export function MiniCDebugPane({ viewModel, sourceView }: MiniCDebugPaneProps) {
  const snapshot = useDebugSnapshot(viewModel);
  const formatter = useMemo(() => new MiniCDebugTextFormatter(), []);
  const [selectedViewId, setSelectedViewId] = useState<DebugViewId>("metadata");
  const [splitVisible, setSplitVisible] = useState(false);
  const [selectedSplitViewId, setSelectedSplitViewId] = useState<DebugViewId>("source");

  return (
    <section className="debug-pane" data-java-source={miniCDebugPaneMirror.javaPath}>
      {controls(viewModel, snapshot)}
      <div className="debug-workspace">
        <nav className="debug-view-selector">
          {DEBUG_VIEWS.map((view) => viewButton(view, selectedViewId, setSelectedViewId))}
          <button className="debug-view-button" onClick={() => setSplitVisible((current) => !current)} type="button">
            {splitVisible ? "合并" : "分屏"}
          </button>
        </nav>
        <div className="debug-view-content">
          {contentFor(selectedViewId, snapshot, formatter, sourceView)}
        </div>
        {splitVisible && (
          <div className="debug-view-content">
            <select
              className="control-secondary"
              onChange={(event) => setSelectedSplitViewId(event.target.value as DebugViewId)}
              value={selectedSplitViewId}
            >
              {DEBUG_VIEWS.map((view) => (
                <option key={view.id} value={view.id}>
                  {view.title}
                </option>
              ))}
            </select>
            {contentFor(selectedSplitViewId, snapshot, formatter, sourceView)}
          </div>
        )}
      </div>
    </section>
  );
}

MiniCDebugPane.mirror = miniCDebugPaneMirror;

export function controls(viewModel: MiniCWorkbenchViewModel, snapshot: MiniCWorkbenchSnapshot) {
  const started = snapshot.debugStarted;
  return (
    <div className="controls debug-controls">
      {button("开始", () => viewModel.startDebug(), true)}
      {button("断点", () => viewModel.debugRunToBreakpoint(), started)}
      {button("运行", () => viewModel.debugRunToEnd(), started)}
      {button("快进", () => viewModel.debugFastForward(), started)}
      {button("步过", () => viewModel.debugStepOver(), started)}
      {button("步入", () => viewModel.debugStepInto(), started)}
      {button("步出", () => viewModel.debugStepOut(), started)}
      {button("暂停", () => viewModel.debugPause(), started)}
      {button("重启", () => viewModel.debugRestart(), started)}
      {button("关闭", () => viewModel.debugClose(), started)}
    </div>
  );
}

export function button(text: string, action: () => void, enabled: boolean) {
  return (
    <button className="debug-control-single-button control-secondary" disabled={!enabled} onClick={action} type="button">
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
  sourceView?: ReactNode,
) {
  if (viewId === "metadata") return metadataContent(snapshot.debugState ? metadataView(snapshot) : null);
  if (viewId === "data") return dataContent(snapshot.debugState ? dataView(snapshot) : null);
  if (viewId === "ast") return astContent(snapshot.debugState ? astView(snapshot) : null);
  if (viewId === "ir") return irContent(snapshot.debugState ? irView(snapshot) : null);
  if (viewId === "asm") return asmContent(snapshot.debugState ? asmView(snapshot) : null);
  return sourceView ?? <MiniCSourceView source={snapshot.sourceText} currentState={snapshot.currentState} />;
}

export function metadataContent(view: UiDebugMetadataViewDto | null) {
  return (
    <div className="debug-metadata">
      {metadataSummary(view)}
      {metadataSection("Timeline", view?.rows ?? [])}
    </div>
  );
}

export function metadataSummary(view: UiDebugMetadataViewDto | null) {
  return (
    <div className="debug-summary-grid">
      <span className="debug-summary-key">Rows</span>
      <span className="debug-summary-value">{view?.rows.length ?? 0}</span>
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
  return lines.length > 0 ? lines.slice(0, 200) : ["暂无数据"];
}

export function dataContent(view: UiDebugDataStructureViewDto | null) {
  return (
    <div className="debug-data-space">
      <MiniCDebugVisualDiagramRenderer view={view} />
      {runtimeSummary(view)}
    </div>
  );
}

export function runtimeSummary(view: UiDebugDataStructureViewDto | null) {
  return processSpaceSection(view?.title ?? "Runtime", view?.rows ?? []);
}

export function processSpaceSection(title: string, lines: readonly string[]) {
  return metadataSection(title, lines);
}

export function astContent(view: UiDebugAstViewDto | null) {
  return (
    <div className="visual-canvas">
      <MiniCDebugAstGraphRenderer view={view} />
      {metadataSection("Details", view?.details ?? [])}
    </div>
  );
}

export function irContent(view: UiDebugIrViewDto | null) {
  return <div className="debug-code-rows">{(view?.lines ?? []).map(irLineRow)}</div>;
}

export function irLineRow(line: UiIrLineVisualDto) {
  return (
    <div className={`debug-code-row${line.active ? " active" : ""}`} key={line.lineNumber}>
      <span className="debug-code-line-number">{line.lineNumber}</span>
      <code className="debug-code-text">{line.text}</code>
    </div>
  );
}

export function asmContent(view: UiDebugAsmViewDto | null) {
  return <div className="debug-code-rows">{(view?.lines ?? []).map(asmLineRow)}</div>;
}

export function asmLineRow(line: UiAssemblyLineVisualDto) {
  return (
    <div className={`debug-code-row${line.active ? " active" : ""}`} key={line.lineNumber}>
      <span className="debug-code-line-number">{line.lineNumber}</span>
      <code className="debug-code-text">{line.text}</code>
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

function metadataView(snapshot: MiniCWorkbenchSnapshot): UiDebugMetadataViewDto {
  return {
    rows: [
      `source: ${snapshot.sourceName}`,
      `line: ${snapshot.debugState?.currentLine ?? 0}`,
      `mode: ${snapshot.debugState?.playbackMode ?? "PAUSED"}`,
      ...((snapshot.debugState?.timeline ?? []) as readonly string[]),
    ],
  };
}

function dataView(snapshot: MiniCWorkbenchSnapshot): UiDebugDataStructureViewDto {
  return {
    title: "Process Space",
    rows: snapshot.globalData?.executionOutputSummary ?? [],
  };
}

function astView(snapshot: MiniCWorkbenchSnapshot): UiDebugAstViewDto {
  return {
    root: snapshot.astVisualData?.astRoot ?? null,
    details: snapshot.globalData?.astSummary ?? [],
  };
}

function irView(snapshot: MiniCWorkbenchSnapshot): UiDebugIrViewDto {
  return {
    lines: snapshot.codegenVisualData?.irLines ?? [],
  };
}

function asmView(snapshot: MiniCWorkbenchSnapshot): UiDebugAsmViewDto {
  return {
    lines: snapshot.codegenVisualData?.assemblyLines ?? [],
  };
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
