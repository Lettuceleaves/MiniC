import { useEffect, useMemo, useState } from "react";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiAssemblyLineVisualDto, UiIrLineVisualDto, UiStageVisualDto } from "../translation/uiapi";
import type { MiniCWorkbenchSnapshot, MiniCWorkbenchViewModel } from "../workbench/MiniCWorkbenchViewModel";
import { MiniCVisualAstGraphRenderer } from "./MiniCVisualAstGraphRenderer";
import { MiniCVisualModelFactory } from "./MiniCVisualModelFactory";
import { MiniCVisualSourceRows } from "./MiniCVisualSourceRows";

export const miniCVisualPaneMirror = {
  "javaPath": "src/main/java/minic/uilocal/visual/MiniCVisualPane.java",
  "webPath": "uiweb/src/visual/MiniCVisualPane.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCVisualPane",
  "kind": "component",
  "imports": [
    "javafx.application.Platform",
    "javafx.geometry.BoundingBox",
    "javafx.geometry.Bounds",
    "javafx.geometry.Orientation",
    "javafx.scene.Node",
    "javafx.scene.Parent",
    "javafx.scene.control.Label",
    "javafx.scene.control.ScrollPane",
    "javafx.scene.control.Slider",
    "javafx.scene.control.SplitPane",
    "javafx.scene.control.TextArea",
    "javafx.scene.input.ScrollEvent",
    "javafx.scene.layout.HBox",
    "javafx.scene.layout.Pane",
    "javafx.scene.layout.Priority",
    "javafx.scene.layout.VBox",
    "javafx.scene.text.TextFlow",
    "minic.uilocal.control.MiniCControlTargetType",
    "minic.uilocal.control.MiniCViewportAdapter",
    "minic.uilocal.control.MiniCWorkbenchControlHub",
    "minic.uilocal.text.MiniCAssemblyTextHighlighter",
    "minic.uilocal.text.MiniCIrTextHighlighter",
    "minic.uilocal.text.MiniCTextFlowFactory",
    "minic.uiapi.UiAstNodeVisualDto",
    "minic.uiapi.UiAssemblyLineVisualDto",
    "minic.uiapi.UiIrLineVisualDto",
    "minic.uiapi.UiSemanticScopeVisualDto",
    "minic.uiapi.UiSourceSpanDto",
    "minic.uiapi.UiStageVisualDto",
    "java.util.ArrayList",
    "java.util.List",
    "java.util.Objects",
    "java.util.stream.Collectors"
  ],
  "fields": [
    {
      "name": "ACTIVE_CENTER_Y_KEY",
      "signature": "private static final String ACTIVE_CENTER_Y_KEY ="
    },
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
      "name": "AST_ZOOM_STEP",
      "signature": "private static final double AST_ZOOM_STEP ="
    },
    {
      "name": "STAGE_SCROLL_FILTER_INSTALLED_KEY",
      "signature": "private static final String STAGE_SCROLL_FILTER_INSTALLED_KEY ="
    },
    {
      "name": "viewModel",
      "signature": "private final MiniCWorkbenchViewModel viewModel;"
    },
    {
      "name": "hoverInspector",
      "signature": "private final MiniCHoverInspector hoverInspector;"
    },
    {
      "name": "modelFactory",
      "signature": "private final MiniCVisualModelFactory modelFactory ="
    },
    {
      "name": "semanticScopeTreeModelFactory",
      "signature": "private final MiniCSemanticScopeTreeModelFactory semanticScopeTreeModelFactory ="
    },
    {
      "name": "assemblyTextModelFactory",
      "signature": "private final MiniCAssemblyTextModelFactory assemblyTextModelFactory ="
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
      "name": "explanationFormatter",
      "signature": "private final MiniCVisualExplanationFormatter explanationFormatter;"
    },
    {
      "name": "astGraphRenderer",
      "signature": "private final MiniCVisualAstGraphRenderer astGraphRenderer;"
    },
    {
      "name": "header",
      "signature": "private final Label header ="
    },
    {
      "name": "splitPane",
      "signature": "private final SplitPane splitPane ="
    },
    {
      "name": "leftColumn",
      "signature": "private final StageColumn leftColumn ="
    },
    {
      "name": "rightColumn",
      "signature": "private final StageColumn rightColumn ="
    },
    {
      "name": "astZoom",
      "signature": "private final Slider astZoom ="
    },
    {
      "name": "executionStdin",
      "signature": "private final TextArea executionStdin ="
    },
    {
      "name": "controlHub",
      "signature": "private MiniCWorkbenchControlHub controlHub;"
    },
    {
      "name": "selectedSemanticScopeId",
      "signature": "private String selectedSemanticScopeId ="
    },
    {
      "name": "refreshScheduled",
      "signature": "private boolean refreshScheduled;"
    },
    {
      "name": "activeVisualStage",
      "signature": "private String activeVisualStage ="
    },
    {
      "name": "columnId",
      "signature": "private final String columnId;"
    },
    {
      "name": "root",
      "signature": "private final VBox root ="
    },
    {
      "name": "title",
      "signature": "private final Label title ="
    },
    {
      "name": "body",
      "signature": "private final VBox body ="
    },
    {
      "name": "scrollPane",
      "signature": "private final ScrollPane scrollPane ="
    },
    {
      "name": "viewportAdapter",
      "signature": "private final MiniCViewportAdapter viewportAdapter ="
    },
    {
      "name": "autoCenter",
      "signature": "private final boolean autoCenter;"
    },
    {
      "name": "viewportKey",
      "signature": "private String viewportKey ="
    },
    {
      "name": "restoringViewport",
      "signature": "private boolean restoringViewport;"
    },
    {
      "name": "hasSavedViewport",
      "signature": "private boolean hasSavedViewport;"
    }
  ],
  "methods": [
    {
      "name": "requestRefresh",
      "signature": "requestRefresh()"
    },
    {
      "name": "refresh",
      "signature": "refresh()"
    },
    {
      "name": "visualForStage",
      "signature": "visualForStage(String stage)"
    },
    {
      "name": "stageName",
      "signature": "stageName(String stage)"
    },
    {
      "name": "zoomAstIn",
      "signature": "zoomAstIn()"
    },
    {
      "name": "zoomAstOut",
      "signature": "zoomAstOut()"
    },
    {
      "name": "installViewportTargets",
      "signature": "installViewportTargets(MiniCWorkbenchControlHub controlHub)"
    },
    {
      "name": "activeViewportAdapters",
      "signature": "activeViewportAdapters()"
    },
    {
      "name": "setAstZoom",
      "signature": "setAstZoom(double value)"
    },
    {
      "name": "astScopeInput",
      "signature": "astScopeInput()"
    },
    {
      "name": "configureExecutionInputControls",
      "signature": "configureExecutionInputControls()"
    },
    {
      "name": "executionInputPane",
      "signature": "executionInputPane()"
    },
    {
      "name": "executionOutputRows",
      "signature": "executionOutputRows()"
    },
    {
      "name": "globalRows",
      "signature": "globalRows(String stage)"
    },
    {
      "name": "preprocessRows",
      "signature": "preprocessRows()"
    },
    {
      "name": "codegenIrRows",
      "signature": "codegenIrRows(UiStageVisualDto codegenVisual)"
    },
    {
      "name": "irRow",
      "signature": "irRow(UiIrLineVisualDto line, UiStageVisualDto visual)"
    },
    {
      "name": "monoLabel",
      "signature": "monoLabel(String text)"
    },
    {
      "name": "section",
      "signature": "section(String title, List<? extends Node> rows)"
    },
    {
      "name": "sourceRows",
      "signature": "sourceRows(UiStageVisualDto visual)"
    },
    {
      "name": "fallbackRows",
      "signature": "fallbackRows()"
    },
    {
      "name": "node",
      "signature": "node(MiniCVisualItem item)"
    },
    {
      "name": "assemblyRows",
      "signature": "assemblyRows(UiStageVisualDto visual)"
    },
    {
      "name": "assemblyRow",
      "signature": "assemblyRow(MiniCAssemblyTextLine line, UiStageVisualDto visual)"
    },
    {
      "name": "semanticRows",
      "signature": "semanticRows(UiStageVisualDto visual)"
    },
    {
      "name": "activeScopeRows",
      "signature": "activeScopeRows(UiStageVisualDto visual)"
    },
    {
      "name": "selectedScope",
      "signature": "selectedScope(UiSemanticScopeVisualDto root)"
    },
    {
      "name": "scopeById",
      "signature": "scopeById(UiSemanticScopeVisualDto scope, String id)"
    },
    {
      "name": "activeScope",
      "signature": "activeScope(UiSemanticScopeVisualDto scope)"
    },
    {
      "name": "astNodeContent",
      "signature": "astNodeContent(UiAstNodeVisualDto node, UiStageVisualDto visual)"
    },
    {
      "name": "semanticScopeContent",
      "signature": "semanticScopeContent(UiSemanticScopeVisualDto scope, int depth, UiStageVisualDto visual)"
    },
    {
      "name": "inspectorContent",
      "signature": "inspectorContent(String title, List<String> metadata, UiSourceSpanDto range, String explanation)"
    },
    {
      "name": "inspectorContent",
      "signature": "inspectorContent(String title, List<String> metadata, UiSourceSpanDto range, String explanation, UiStageVisualDto visual)"
    },
    {
      "name": "sourceTextForRange",
      "signature": "sourceTextForRange(UiSourceSpanDto range, UiStageVisualDto preferredVisual)"
    },
    {
      "name": "sourceSnippetForRange",
      "signature": "sourceSnippetForRange(UiSourceSpanDto range, UiStageVisualDto preferredVisual)"
    },
    {
      "name": "sourceTextFromVisual",
      "signature": "sourceTextFromVisual(UiSourceSpanDto range, UiStageVisualDto visual)"
    },
    {
      "name": "visualContainsSourceName",
      "signature": "visualContainsSourceName(UiStageVisualDto visual, String sourceName)"
    },
    {
      "name": "astContainsSourceName",
      "signature": "astContainsSourceName(UiAstNodeVisualDto node, String sourceName)"
    },
    {
      "name": "scopeContainsSourceName",
      "signature": "scopeContainsSourceName(UiSemanticScopeVisualDto scope, String sourceName)"
    },
    {
      "name": "sameSource",
      "signature": "sameSource(UiSourceSpanDto range, String sourceName)"
    },
    {
      "name": "attachInspectorClick",
      "signature": "attachInspectorClick(Node node, MiniCHoverInspectorContent content)"
    },
    {
      "name": "semanticRow",
      "signature": "semanticRow(MiniCSemanticScopeTreeLine line)"
    },
    {
      "name": "tokenRows",
      "signature": "tokenRows(UiStageVisualDto visual)"
    },
    {
      "name": "textRow",
      "signature": "textRow(String text, String rowStyle, String textStyle)"
    },
    {
      "name": "installViewportTarget",
      "signature": "installViewportTarget(MiniCWorkbenchControlHub controlHub)"
    },
    {
      "name": "setContent",
      "signature": "setContent(String titleText, List<? extends Node> rows)"
    },
    {
      "name": "hasSavedViewport",
      "signature": "hasSavedViewport(String key)"
    },
    {
      "name": "restoreViewportLater",
      "signature": "restoreViewportLater()"
    },
    {
      "name": "saveViewport",
      "signature": "saveViewport()"
    },
    {
      "name": "centerActiveLater",
      "signature": "centerActiveLater()"
    },
    {
      "name": "centerActive",
      "signature": "centerActive()"
    },
    {
      "name": "isActiveFullyVisible",
      "signature": "isActiveFullyVisible()"
    },
    {
      "name": "activeBounds",
      "signature": "activeBounds()"
    },
    {
      "name": "activeNode",
      "signature": "activeNode(Node node)"
    },
    {
      "name": "activeCenterY",
      "signature": "activeCenterY()"
    },
    {
      "name": "hasActiveStyle",
      "signature": "hasActiveStyle(Node node)"
    },
    {
      "name": "scrollVertical",
      "signature": "scrollVertical(double delta)"
    },
    {
      "name": "scrollHorizontal",
      "signature": "scrollHorizontal(double delta)"
    },
    {
      "name": "scrollAxis",
      "signature": "scrollAxis(double delta, boolean horizontal)"
    },
    {
      "name": "type",
      "signature": "type()"
    },
    {
      "name": "canScrollVertical",
      "signature": "canScrollVertical()"
    },
    {
      "name": "scrollVertical",
      "signature": "scrollVertical(double delta)"
    },
    {
      "name": "canScrollHorizontal",
      "signature": "canScrollHorizontal()"
    },
    {
      "name": "scrollHorizontal",
      "signature": "scrollHorizontal(double delta)"
    },
    {
      "name": "isActiveFullyVisible",
      "signature": "isActiveFullyVisible()"
    },
    {
      "name": "centerActive",
      "signature": "centerActive()"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCVisualPaneProps {
  readonly viewModel: MiniCWorkbenchViewModel;
}

const DEFAULT_AST_ZOOM = 1;
const MIN_AST_ZOOM = 0.5;
const MAX_AST_ZOOM = 2;
const AST_ZOOM_STEP = 0.1;

export function MiniCVisualPane({ viewModel }: MiniCVisualPaneProps) {
  const snapshot = useVisualSnapshot(viewModel);
  const [astZoom, setAstZoomState] = useState(DEFAULT_AST_ZOOM);
  const visual = visualForStage(snapshot);
  const modelFactory = useMemo(() => new MiniCVisualModelFactory(), []);

  const setAstZoom = (value: number): void => {
    setAstZoomState(Math.max(MIN_AST_ZOOM, Math.min(MAX_AST_ZOOM, value)));
  };

  return (
    <section className="visual-canvas" data-java-source={miniCVisualPaneMirror.javaPath}>
      <header className="pane-head">{stageName(visual?.stage ?? snapshot.currentState?.currentStage ?? "source")}</header>
      <div className="stage-flow">
        <section className="stage-flow-column">
          <h2 className="stage-flow-title">源码与阶段数据</h2>
          <div className="stage-flow-body">{sourceRows(visual)}</div>
        </section>
        <section className="stage-flow-column">
          <h2 className="stage-flow-title">可视化</h2>
          <div className="stage-flow-body">
            {visualContent(visual, astZoom, setAstZoom, modelFactory)}
          </div>
        </section>
      </div>
    </section>
  );
}

MiniCVisualPane.mirror = miniCVisualPaneMirror;

export function visualForStage(snapshot: MiniCWorkbenchSnapshot): UiStageVisualDto | null {
  const selected = snapshot.selectedVisualStage;
  if (selected === "lexer") return snapshot.lexerVisualData;
  if (selected === "parser") return snapshot.astVisualData;
  if (selected === "semantic") return snapshot.semanticVisualData;
  if (selected === "codegen") return snapshot.codegenVisualData;
  return snapshot.currentStageVisualData ?? snapshot.lexerVisualData ?? snapshot.astVisualData;
}

export function stageName(stage: string): string {
  switch (stage) {
    case "source":
      return "源码";
    case "preprocess":
      return "预编译";
    case "lexer":
      return "词法分析";
    case "parser":
      return "语法分析";
    case "semantic":
      return "语义分析";
    case "ir":
      return "IR 降级";
    case "codegen":
      return "代码生成";
    case "toolchain":
      return "工具链";
    case "execution":
      return "执行";
    default:
      return stage;
  }
}

export function zoomAstIn(current: number): number {
  return Math.min(MAX_AST_ZOOM, current + AST_ZOOM_STEP);
}

export function zoomAstOut(current: number): number {
  return Math.max(MIN_AST_ZOOM, current - AST_ZOOM_STEP);
}

export function sourceRows(visual: UiStageVisualDto | null) {
  if (!visual) {
    return fallbackRows();
  }
  return (
    <div className="stage-flow-scroll">
      <MiniCVisualSourceRows visual={visual} />
    </div>
  );
}

export function fallbackRows() {
  return <p className="body-text">等待开始观测会话</p>;
}

function visualContent(
  visual: UiStageVisualDto | null,
  astZoom: number,
  setAstZoom: (value: number) => void,
  modelFactory: MiniCVisualModelFactory,
) {
  if (!visual) {
    return fallbackRows();
  }
  if (visual.astRoot) {
    return (
      <div className="ast-zoom-box">
        <div className="ast-zoom-controls">
          <button className="control-secondary" onClick={() => setAstZoom(zoomAstOut(astZoom))} type="button">
            -
          </button>
          <span className="ast-zoom-label">AST</span>
          <input
            className="ast-zoom-slider"
            max={MAX_AST_ZOOM}
            min={MIN_AST_ZOOM}
            onChange={(event) => setAstZoom(Number(event.target.value))}
            step={AST_ZOOM_STEP}
            type="range"
            value={astZoom}
          />
          <span className="ast-zoom-value">{Math.round(astZoom * 100)}%</span>
          <button className="control-secondary" onClick={() => setAstZoom(zoomAstIn(astZoom))} type="button">
            +
          </button>
        </div>
        <MiniCVisualAstGraphRenderer semanticMasks={visual.visualType === "semantic-scope"} visual={visual} zoom={astZoom} />
      </div>
    );
  }
  if (visual.lexerTokens.length > 0) {
    return tokenRows(visual);
  }
  if (visual.irLines.length > 0) {
    return codegenIrRows(visual);
  }
  if (visual.assemblyLines.length > 0) {
    return assemblyRows(visual);
  }
  return (
    <div>
      {modelFactory.createFromVisual(visual).map((item) => (
        <div className={`visual-node${item.hot ? " hot" : ""}`} key={item.label}>
          {item.label}
        </div>
      ))}
    </div>
  );
}

export function codegenIrRows(visual: UiStageVisualDto) {
  return <div>{visual.irLines.map((line) => irRow(line))}</div>;
}

export function irRow(line: UiIrLineVisualDto) {
  return (
    <div className={`debug-code-row${line.active ? " active" : ""}`} key={line.lineNumber}>
      <span className="debug-code-line-number">{line.lineNumber}</span>
      <code className="debug-code-text">{line.text}</code>
    </div>
  );
}

export function assemblyRows(visual: UiStageVisualDto) {
  return <div>{visual.assemblyLines.map((line) => assemblyRow(line))}</div>;
}

export function assemblyRow(line: UiAssemblyLineVisualDto) {
  return (
    <div className="assembly-row" key={line.lineNumber}>
      <span className={`assembly-line-number${line.active ? " active" : ""}`}>{line.lineNumber}</span>
      <code className={`assembly-text${line.active ? " active" : ""}`}>{line.text}</code>
    </div>
  );
}

export function tokenRows(visual: UiStageVisualDto) {
  return (
    <div>
      {visual.lexerTokens.map((token, index) => (
        <div className="token-row" key={`${token.text}-${index}`}>
          <span className={`token-kind${token.active ? " active" : ""}`}>{token.kind}</span>
          <span className={`token-text${token.active ? " active" : ""}`}>{token.text}</span>
          <span className={`token-range${token.active ? " active" : ""}`}>
            {token.range ? `${token.range.startOffset}-${token.range.endOffset}` : "-"}
          </span>
        </div>
      ))}
    </div>
  );
}

function useVisualSnapshot(viewModel: MiniCWorkbenchViewModel): MiniCWorkbenchSnapshot {
  const [snapshot, setSnapshot] = useState(() => viewModel.snapshot());

  useEffect(() => {
    setSnapshot(viewModel.snapshot());
    return viewModel.subscribe(() => {
      setSnapshot(viewModel.snapshot());
    });
  }, [viewModel]);

  return snapshot;
}

export default MiniCVisualPane;
