import { useEffect, useMemo, useState } from "react";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { KeyboardEvent as ReactKeyboardEvent, MouseEvent as ReactMouseEvent, ReactElement } from "react";
import type {
  UiAssemblyLineVisualDto,
  UiAstNodeVisualDto,
  UiIrLineVisualDto,
  UiLexerTokenVisualDto,
  UiSemanticScopeVisualDto,
  UiSourceSpanDto,
  UiStageVisualDto,
} from "../translation/uiapi";
import { MiniCHoverInspector } from "../panel/MiniCHoverInspector";
import { MiniCHoverInspectorContent } from "../panel/MiniCHoverInspectorContent";
import { MiniCAssemblyTextHighlighter } from "../text/MiniCAssemblyTextHighlighter";
import { MiniCIrTextHighlighter } from "../text/MiniCIrTextHighlighter";
import { textFlow } from "../text/MiniCTextFlowFactory";
import type { MiniCWorkbenchSnapshot, MiniCWorkbenchViewModel } from "../workbench/MiniCWorkbenchViewModel";
import { MiniCAssemblyTextModelFactory } from "./MiniCAssemblyTextModelFactory";
import type { MiniCAssemblyTextLine } from "./MiniCAssemblyTextLine";
import { MiniCSemanticScopeTreeModelFactory } from "./MiniCSemanticScopeTreeModelFactory";
import type { MiniCSemanticScopeTreeLine } from "./MiniCSemanticScopeTreeLine";
import { MiniCVisualAstGraphRenderer } from "./MiniCVisualAstGraphRenderer";
import { MiniCVisualExplanationFormatter } from "./MiniCVisualExplanationFormatter";
import { MiniCVisualModelFactory } from "./MiniCVisualModelFactory";
import { MiniCVisualSourceRows } from "./MiniCVisualSourceRows";

export const miniCVisualPaneMirror = {
  "javaPath": "src/main/java/minic/uilocal/visual/MiniCVisualPane.java",
  "webPath": "uiweb/src/visual/MiniCVisualPane.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCVisualPane",
  "kind": "component",
  "imports": [
    "java.util.ArrayList",
    "java.util.List",
    "java.util.Objects",
    "java.util.stream.Collectors",
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
    "minic.uiapi.UiAssemblyLineVisualDto",
    "minic.uiapi.UiAstNodeVisualDto",
    "minic.uiapi.UiIrLineVisualDto",
    "minic.uiapi.UiSemanticScopeVisualDto",
    "minic.uiapi.UiSourceSpanDto",
    "minic.uiapi.UiStageVisualDto",
    "minic.uilocal.control.MiniCControlTargetType",
    "minic.uilocal.control.MiniCViewportAdapter",
    "minic.uilocal.control.MiniCWorkbenchControlHub",
    "minic.uilocal.text.MiniCAssemblyTextHighlighter",
    "minic.uilocal.text.MiniCIrTextHighlighter",
    "minic.uilocal.text.MiniCTextFlowFactory"
  ],
  "fields": [
    {
      "name": "ACTIVE_CENTER_Y_KEY",
      "signature": "private static final String ACTIVE_CENTER_Y_KEY="
    },
    {
      "name": "activeVisualStage",
      "signature": "private String activeVisualStage="
    },
    {
      "name": "assemblyTextHighlighter",
      "signature": "private final MiniCAssemblyTextHighlighter assemblyTextHighlighter="
    },
    {
      "name": "assemblyTextModelFactory",
      "signature": "private final MiniCAssemblyTextModelFactory assemblyTextModelFactory="
    },
    {
      "name": "AST_ZOOM_STEP",
      "signature": "private static final double AST_ZOOM_STEP="
    },
    {
      "name": "astGraphRenderer",
      "signature": "private final MiniCVisualAstGraphRenderer astGraphRenderer"
    },
    {
      "name": "astZoom",
      "signature": "private final Slider astZoom="
    },
    {
      "name": "autoCenter",
      "signature": "private final boolean autoCenter"
    },
    {
      "name": "body",
      "signature": "private final VBox body="
    },
    {
      "name": "columnId",
      "signature": "private final String columnId"
    },
    {
      "name": "controlHub",
      "signature": "private MiniCWorkbenchControlHub controlHub"
    },
    {
      "name": "DEFAULT_AST_ZOOM",
      "signature": "private static final double DEFAULT_AST_ZOOM="
    },
    {
      "name": "executionStdin",
      "signature": "private final TextArea executionStdin="
    },
    {
      "name": "stageOverride",
      "signature": "private final String stageOverride"
    },
    {
      "name": "visualSide",
      "signature": "private final VisualSide visualSide"
    },
    {
      "name": "explanationFormatter",
      "signature": "private final MiniCVisualExplanationFormatter explanationFormatter"
    },
    {
      "name": "hasSavedViewport",
      "signature": "private boolean hasSavedViewport"
    },
    {
      "name": "header",
      "signature": "private final Label header="
    },
    {
      "name": "id",
      "signature": "private final String id"
    },
    {
      "name": "hoverInspector",
      "signature": "private final MiniCHoverInspector hoverInspector"
    },
    {
      "name": "irTextHighlighter",
      "signature": "private final MiniCIrTextHighlighter irTextHighlighter="
    },
    {
      "name": "label",
      "signature": "private final String label"
    },
    {
      "name": "leftColumn",
      "signature": "private final StageColumn leftColumn="
    },
    {
      "name": "MAX_AST_ZOOM",
      "signature": "private static final double MAX_AST_ZOOM="
    },
    {
      "name": "MIN_AST_ZOOM",
      "signature": "private static final double MIN_AST_ZOOM="
    },
    {
      "name": "modelFactory",
      "signature": "private final MiniCVisualModelFactory modelFactory="
    },
    {
      "name": "refreshScheduled",
      "signature": "private boolean refreshScheduled"
    },
    {
      "name": "restoringViewport",
      "signature": "private boolean restoringViewport"
    },
    {
      "name": "rightColumn",
      "signature": "private final StageColumn rightColumn="
    },
    {
      "name": "root",
      "signature": "private final VBox root="
    },
    {
      "name": "scrollPane",
      "signature": "private final ScrollPane scrollPane="
    },
    {
      "name": "selectedSemanticScopeId",
      "signature": "private String selectedSemanticScopeId="
    },
    {
      "name": "semanticScopeTreeModelFactory",
      "signature": "private final MiniCSemanticScopeTreeModelFactory semanticScopeTreeModelFactory="
    },
    {
      "name": "splitPane",
      "signature": "private final SplitPane splitPane="
    },
    {
      "name": "STAGE_SCROLL_FILTER_INSTALLED_KEY",
      "signature": "private static final String STAGE_SCROLL_FILTER_INSTALLED_KEY="
    },
    {
      "name": "title",
      "signature": "private final Label title="
    },
    {
      "name": "viewModel",
      "signature": "private final MiniCWorkbenchViewModel viewModel"
    },
    {
      "name": "viewportAdapter",
      "signature": "private final MiniCViewportAdapter viewportAdapter="
    },
    {
      "name": "viewportKey",
      "signature": "private String viewportKey="
    }
  ],
  "methods": [
    {
      "name": "activeBounds",
      "signature": "activeBounds()"
    },
    {
      "name": "activeCenterY",
      "signature": "activeCenterY()"
    },
    {
      "name": "activeNode",
      "signature": "activeNode(Node node)"
    },
    {
      "name": "activeScope",
      "signature": "activeScope(UiSemanticScopeVisualDto scope)"
    },
    {
      "name": "activeScopeRows",
      "signature": "activeScopeRows(UiStageVisualDto visual)"
    },
    {
      "name": "activeViewportAdapters",
      "signature": "activeViewportAdapters()"
    },
    {
      "name": "applyVisibleColumns",
      "signature": "applyVisibleColumns()"
    },
    {
      "name": "assemblyRow",
      "signature": "assemblyRow(MiniCAssemblyTextLine line,UiStageVisualDto visual)"
    },
    {
      "name": "assemblyRows",
      "signature": "assemblyRows(UiStageVisualDto visual)"
    },
    {
      "name": "astContainsSourceName",
      "signature": "astContainsSourceName(UiAstNodeVisualDto node,String sourceName)"
    },
    {
      "name": "astNodeContent",
      "signature": "astNodeContent(UiAstNodeVisualDto node,UiStageVisualDto visual)"
    },
    {
      "name": "astScopeInput",
      "signature": "astScopeInput()"
    },
    {
      "name": "attachInspectorClick",
      "signature": "attachInspectorClick(Node node,MiniCHoverInspectorContent content)"
    },
    {
      "name": "canScrollHorizontal",
      "signature": "canScrollHorizontal()"
    },
    {
      "name": "canScrollVertical",
      "signature": "canScrollVertical()"
    },
    {
      "name": "centerActive",
      "signature": "centerActive()"
    },
    {
      "name": "centerActive",
      "signature": "centerActive()"
    },
    {
      "name": "centerActiveLater",
      "signature": "centerActiveLater()"
    },
    {
      "name": "codegenIrRows",
      "signature": "codegenIrRows(UiStageVisualDto codegenVisual)"
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
      "name": "fallbackRows",
      "signature": "fallbackRows()"
    },
    {
      "name": "globalRows",
      "signature": "globalRows(String stage)"
    },
    {
      "name": "hasActiveStyle",
      "signature": "hasActiveStyle(Node node)"
    },
    {
      "name": "hasSavedViewport",
      "signature": "hasSavedViewport(String key)"
    },
    {
      "name": "id",
      "signature": "id()"
    },
    {
      "name": "inspectorContent",
      "signature": "inspectorContent(String title,List<String>metadata,UiSourceSpanDto range,String explanation,UiStageVisualDto visual)"
    },
    {
      "name": "inspectorContent",
      "signature": "inspectorContent(String title,List<String>metadata,UiSourceSpanDto range,String explanation)"
    },
    {
      "name": "installViewportTarget",
      "signature": "installViewportTarget(MiniCWorkbenchControlHub controlHub)"
    },
    {
      "name": "installViewportTargets",
      "signature": "installViewportTargets(MiniCWorkbenchControlHub controlHub)"
    },
    {
      "name": "irRow",
      "signature": "irRow(UiIrLineVisualDto line,UiStageVisualDto visual)"
    },
    {
      "name": "label",
      "signature": "label()"
    },
    {
      "name": "isActiveFullyVisible",
      "signature": "isActiveFullyVisible()"
    },
    {
      "name": "isActiveFullyVisible",
      "signature": "isActiveFullyVisible()"
    },
    {
      "name": "monoLabel",
      "signature": "monoLabel(String text)"
    },
    {
      "name": "node",
      "signature": "node(MiniCVisualItem item)"
    },
    {
      "name": "preprocessRows",
      "signature": "preprocessRows()"
    },
    {
      "name": "refresh",
      "signature": "refresh()"
    },
    {
      "name": "requestRefresh",
      "signature": "requestRefresh()"
    },
    {
      "name": "restoreViewportLater",
      "signature": "restoreViewportLater()"
    },
    {
      "name": "sameSource",
      "signature": "sameSource(UiSourceSpanDto range,String sourceName)"
    },
    {
      "name": "saveViewport",
      "signature": "saveViewport()"
    },
    {
      "name": "scopeById",
      "signature": "scopeById(UiSemanticScopeVisualDto scope,String id)"
    },
    {
      "name": "scopeContainsSourceName",
      "signature": "scopeContainsSourceName(UiSemanticScopeVisualDto scope,String sourceName)"
    },
    {
      "name": "scrollAxis",
      "signature": "scrollAxis(double delta,boolean horizontal)"
    },
    {
      "name": "scrollHorizontal",
      "signature": "scrollHorizontal(double delta)"
    },
    {
      "name": "scrollHorizontal",
      "signature": "scrollHorizontal(double delta)"
    },
    {
      "name": "scrollVertical",
      "signature": "scrollVertical(double delta)"
    },
    {
      "name": "scrollVertical",
      "signature": "scrollVertical(double delta)"
    },
    {
      "name": "section",
      "signature": "section(String title,List<? extends Node>rows)"
    },
    {
      "name": "selectedScope",
      "signature": "selectedScope(UiSemanticScopeVisualDto root)"
    },
    {
      "name": "semanticRow",
      "signature": "semanticRow(MiniCSemanticScopeTreeLine line)"
    },
    {
      "name": "semanticRows",
      "signature": "semanticRows(UiStageVisualDto visual)"
    },
    {
      "name": "semanticScopeContent",
      "signature": "semanticScopeContent(UiSemanticScopeVisualDto scope,int depth,UiStageVisualDto visual)"
    },
    {
      "name": "setAstZoom",
      "signature": "setAstZoom(double value)"
    },
    {
      "name": "setContent",
      "signature": "setContent(String titleText,List<? extends Node>rows)"
    },
    {
      "name": "sourceRows",
      "signature": "sourceRows(UiStageVisualDto visual)"
    },
    {
      "name": "sourceSnippetForRange",
      "signature": "sourceSnippetForRange(UiSourceSpanDto range,UiStageVisualDto preferredVisual)"
    },
    {
      "name": "sourceTextForRange",
      "signature": "sourceTextForRange(UiSourceSpanDto range,UiStageVisualDto preferredVisual)"
    },
    {
      "name": "sourceTextFromVisual",
      "signature": "sourceTextFromVisual(UiSourceSpanDto range,UiStageVisualDto visual)"
    },
    {
      "name": "StageColumn",
      "signature": "StageColumn(String columnId,boolean autoCenter)"
    },
    {
      "name": "stageName",
      "signature": "stageName(String stage)"
    },
    {
      "name": "textRow",
      "signature": "textRow(String text,String rowStyle,String textStyle)"
    },
    {
      "name": "tokenRows",
      "signature": "tokenRows(UiStageVisualDto visual)"
    },
    {
      "name": "type",
      "signature": "type()"
    },
    {
      "name": "visualContainsSourceName",
      "signature": "visualContainsSourceName(UiStageVisualDto visual,String sourceName)"
    },
    {
      "name": "visualForStage",
      "signature": "visualForStage(String stage)"
    },
    {
      "name": "zoomAstIn",
      "signature": "zoomAstIn()"
    },
    {
      "name": "zoomAstOut",
      "signature": "zoomAstOut()"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCVisualPaneProps {
  readonly viewModel: MiniCWorkbenchViewModel;
  readonly inspector?: MiniCHoverInspector;
  readonly stageOverride?: string;
  readonly side?: MiniCVisualSide;
}

export type MiniCVisualSide = "both" | "before" | "after";

const DEFAULT_AST_ZOOM = 1;
const MIN_AST_ZOOM = 0.05;
const MAX_AST_ZOOM = 1;
const AST_ZOOM_STEP = 0.025;

export function MiniCVisualPane({ viewModel, inspector, stageOverride = "", side = "both" }: MiniCVisualPaneProps) {
  const snapshot = useVisualSnapshot(viewModel);
  const [astZoom, setAstZoomState] = useState(DEFAULT_AST_ZOOM);
  const [selectedSemanticScopeId, setSelectedSemanticScopeId] = useState("");
  const localInspector = useMemo(() => inspector ?? new MiniCHoverInspector(), [inspector]);
  const modelFactory = useMemo(() => new MiniCVisualModelFactory(), []);
  const semanticScopeTreeModelFactory = useMemo(() => new MiniCSemanticScopeTreeModelFactory(), []);
  const assemblyTextModelFactory = useMemo(() => new MiniCAssemblyTextModelFactory(), []);
  const irTextHighlighter = useMemo(() => new MiniCIrTextHighlighter(), []);
  const assemblyTextHighlighter = useMemo(() => new MiniCAssemblyTextHighlighter(), []);
  const inspectorContext = useMemo<VisualInspectorContext>(() => ({
    formatter: new MiniCVisualExplanationFormatter((range) => sourceSnippetForRange(range, null, snapshot)),
    inspector: localInspector,
    snapshot,
  }), [localInspector, snapshot]);
  const currentStage = snapshot.currentStageData?.stage ?? "pending";
  const stage = stageOverride.length > 0
    ? stageOverride
    : snapshot.selectedVisualStage.length > 0 ? snapshot.selectedVisualStage : currentStage;
  const visual = visualForStage(stage, snapshot);

  useEffect(() => {
    if (stage !== "semantic" && stage !== "ir") {
      setSelectedSemanticScopeId("");
    }
  }, [stage]);

  const setAstZoom = (value: number): void => {
    setAstZoomState(Math.max(MIN_AST_ZOOM, Math.min(MAX_AST_ZOOM, value)));
  };

  const columns = stageColumns(
    stage,
    visual,
    astZoom,
    setAstZoom,
    snapshot,
    modelFactory,
    semanticScopeTreeModelFactory,
    assemblyTextModelFactory,
    irTextHighlighter,
    assemblyTextHighlighter,
    inspectorContext,
    selectedSemanticScopeId,
    setSelectedSemanticScopeId,
  );

  return (
    <section className="visual-canvas" data-java-source={miniCVisualPaneMirror.javaPath}>
      <header className="pane-head">
        图形视图 · {stageName(stage)}{side === "both" ? "" : ` · ${side}`}{stage === currentStage ? "" : " · 快照"}
      </header>
      <div className={`stage-flow${side === "both" ? "" : " single"}`}>
        {side !== "after" && (
          <section className="stage-flow-column">
            <h2 className="stage-flow-title">{columns.leftTitle}</h2>
            <div className="stage-flow-body">{columns.left}</div>
          </section>
        )}
        {side !== "before" && (
          <section className="stage-flow-column">
            <h2 className="stage-flow-title">{columns.rightTitle}</h2>
            <div className="stage-flow-body">{columns.right}</div>
          </section>
        )}
      </div>
    </section>
  );
}

MiniCVisualPane.mirror = miniCVisualPaneMirror;

export function visualForStage(stage: string, snapshot: MiniCWorkbenchSnapshot): UiStageVisualDto | null {
  if (stage === "lexer") return snapshot.lexerVisualData;
  if (stage === "parser") return snapshot.astVisualData;
  if (stage === "semantic") return snapshot.semanticVisualData;
  if (stage === "codegen") return snapshot.codegenVisualData;
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
    case "pending":
      return "等待中";
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

export function fallbackRows(items: readonly { readonly label: string; readonly hot: boolean }[] = []) {
  const rows = items.length > 0 ? items : [{ label: "等待开始观测会话", hot: true }];
  return (
    <div>
      {rows.map((item, index) => (
        <div className={`visual-node${item.hot ? " hot" : ""}`} key={`${item.label}-${index}`}>
          {item.label}
        </div>
      ))}
    </div>
  );
}

interface StageColumns {
  readonly leftTitle: string;
  readonly left: ReactElement;
  readonly rightTitle: string;
  readonly right: ReactElement;
}

function stageColumns(
  stage: string,
  visual: UiStageVisualDto | null,
  astZoom: number,
  setAstZoom: (value: number) => void,
  snapshot: MiniCWorkbenchSnapshot,
  modelFactory: MiniCVisualModelFactory,
  semanticScopeTreeModelFactory: MiniCSemanticScopeTreeModelFactory,
  assemblyTextModelFactory: MiniCAssemblyTextModelFactory,
  irTextHighlighter: MiniCIrTextHighlighter,
  assemblyTextHighlighter: MiniCAssemblyTextHighlighter,
  inspectorContext: VisualInspectorContext,
  selectedSemanticScopeId: string,
  setSelectedSemanticScopeId: (scopeId: string) => void,
): StageColumns {
  if (!visual) {
    return {
      leftTitle: stage,
      left: fallbackRows(modelFactory.create(snapshot.currentStageData, snapshot.globalData)),
      rightTitle: "输出",
      right: <div />,
    };
  }

  switch (stage) {
    case "preprocess":
      return {
        leftTitle: "源码",
        left: sourceRows(visual),
        rightTitle: "预处理后产物",
        right: preprocessRows(visual),
      };
    case "lexer":
      return {
        leftTitle: "预处理后产物",
        left: sourceRows(visual),
        rightTitle: "Token",
        right: tokenRows(visual, inspectorContext),
      };
    case "parser":
      return {
        leftTitle: "Token",
        left: tokenRows(snapshot.lexerVisualData, inspectorContext),
        rightTitle: "AST",
        right: astGraph(visual, astZoom, setAstZoom, false, inspectorContext),
      };
    case "semantic":
      return {
        leftTitle: "AST",
        left: astGraph(visual, astZoom, setAstZoom, true, inspectorContext, selectedSemanticScopeId, setSelectedSemanticScopeId),
        rightTitle: "作用域",
        right: activeScopeRows(visual, selectedSemanticScopeId),
      };
    case "codegen":
      return {
        leftTitle: "IR",
        left: codegenIrRows(visual, irTextHighlighter, inspectorContext),
        rightTitle: "汇编",
        right: assemblyRows(visual, assemblyTextModelFactory, assemblyTextHighlighter, inspectorContext),
      };
    case "source":
      return {
        leftTitle: "源码",
        left: sourceRows(visual),
        rightTitle: "输出",
        right: monoRows(["源码已加载。"]),
      };
    case "ir":
      return {
        leftTitle: "AST",
        left: astGraph(visual, astZoom, setAstZoom, true, inspectorContext, selectedSemanticScopeId, setSelectedSemanticScopeId),
        rightTitle: "IR",
        right: selectedSemanticScopeId.length === 0 ? globalRows("ir", snapshot) : activeScopeRows(visual, selectedSemanticScopeId),
      };
    case "toolchain":
      return {
        leftTitle: "汇编",
        left: assemblyRows(snapshot.codegenVisualData, assemblyTextModelFactory, assemblyTextHighlighter, inspectorContext),
        rightTitle: "工具链",
        right: globalRows("toolchain", snapshot),
      };
    case "execution":
      return {
        leftTitle: "STDIN",
        left: executionInputPane(snapshot.executionInputDraft),
        rightTitle: "输出",
        right: executionOutputRows(snapshot),
      };
    default:
      return {
        leftTitle: stage,
        left: fallbackRows(modelFactory.create(snapshot.currentStageData, snapshot.globalData)),
        rightTitle: "输出",
        right: <div />,
      };
  }
}

function astGraph(
  visual: UiStageVisualDto,
  astZoom: number,
  setAstZoom: (value: number) => void,
  semanticMasks: boolean,
  inspectorContext: VisualInspectorContext,
  selectedSemanticScopeId = "",
  setSelectedSemanticScopeId: (scopeId: string) => void = () => {},
) {
  return (
    <div className="ast-zoom-box">
      <div className="ast-zoom-controls">
        <span className="ast-zoom-label">缩放</span>
        <input
          className="ast-zoom-slider"
          max={MAX_AST_ZOOM}
          min={MIN_AST_ZOOM}
          onChange={(event) => setAstZoom(Number(event.target.value))}
          step={AST_ZOOM_STEP}
          type="range"
          value={astZoom}
        />
        <span className="ast-zoom-value">{astZoomPercent(astZoom)}</span>
      </div>
      <MiniCVisualAstGraphRenderer
        onAstNodeSelect={(node) => inspectorContext.inspector.show(astNodeContent(node, visual, inspectorContext))}
        onSemanticScopeSelect={(scopeId) => {
          const scope = scopeById(visual.semanticRoot, scopeId);
          if (scope) {
            setSelectedSemanticScopeId(scopeId);
            inspectorContext.inspector.show(semanticScopeContent(scope, scopeDepth(visual.semanticRoot, scopeId), visual, inspectorContext));
          }
        }}
        selectedSemanticScopeId={selectedSemanticScopeId}
        semanticMasks={semanticMasks}
        visual={visual}
        zoom={astZoom}
      />
    </div>
  );
}

function preprocessRows(visual: UiStageVisualDto | null) {
  if (!visual || visual.genericItems.length === 0) {
    return monoRows(["预处理产物会显示在这里。"]);
  }
  return monoRows(visual.genericItems);
}

function globalRows(stage: string, snapshot: MiniCWorkbenchSnapshot) {
  if (snapshot.globalData === null) {
    return monoRows(["暂无数据。"]);
  }
  const rows = stage === "ir" ? snapshot.globalData.irSummary : stage === "toolchain" ? snapshot.globalData.artifactSummary : [];
  return monoRows(rows.length > 0 ? rows : [`${stageName(stage)} 暂无输出。`]);
}

function executionInputPane(value: string) {
  return <textarea className="execution-stdin" readOnly value={value} />;
}

function executionOutputRows(snapshot: MiniCWorkbenchSnapshot) {
  const rows = snapshot.globalData?.executionOutputSummary ?? [];
  return monoRows(rows.length > 0 ? rows : ["执行输出会显示在这里。"]);
}

export function codegenIrRows(
  visual: UiStageVisualDto | null,
  highlighter = new MiniCIrTextHighlighter(),
  inspectorContext?: VisualInspectorContext,
) {
  if (!visual || visual.irLines.length === 0) {
    return <div>{textRow("IR 暂无输出。", "assembly-row", "assembly-text")}</div>;
  }
  return <div>{visual.irLines.map((line) => irRow(line, highlighter, visual, inspectorContext))}</div>;
}

export function irRow(
  line: UiIrLineVisualDto,
  highlighter = new MiniCIrTextHighlighter(),
  visual?: UiStageVisualDto | null,
  inspectorContext?: VisualInspectorContext,
) {
  const inspectProps = visual && inspectorContext
    ? attachInspectorClick(inspectorContent(
      `IR 行 ${line.lineNumber}`,
      [
        "类型: IR",
        `行号: ${line.lineNumber}`,
        `文本: ${line.text}`,
        inspectorContext.formatter.rangeLine(line.range),
      ],
      line.range,
      inspectorContext.formatter.explainIrLine(line),
      visual,
      inspectorContext,
    ), inspectorContext)
    : {};
  return (
    <div {...inspectProps} className={`assembly-row${line.active ? " active" : ""}`} key={line.lineNumber}>
      <span className={`assembly-line-number${line.active ? " active" : ""}`}>{line.lineNumber}</span>
      {textFlow(highlighter.highlight(line.text), `assembly-text${line.active ? " active" : ""}`, line.active)}
    </div>
  );
}

export function assemblyRows(
  visual: UiStageVisualDto | null,
  modelFactory = new MiniCAssemblyTextModelFactory(),
  highlighter = new MiniCAssemblyTextHighlighter(),
  inspectorContext?: VisualInspectorContext,
) {
  const lines = modelFactory.create(visual);
  if (lines.length === 0) {
    return <div>{textRow("汇编尚未就绪", "assembly-row", "assembly-text")}</div>;
  }
  return <div>{lines.map((line) => assemblyRow(line, highlighter, visual, inspectorContext))}</div>;
}

export function assemblyRow(
  line: UiAssemblyLineVisualDto | MiniCAssemblyTextLine,
  highlighter = new MiniCAssemblyTextHighlighter(),
  visual?: UiStageVisualDto | null,
  inspectorContext?: VisualInspectorContext,
) {
  const inspectProps = visual && inspectorContext
    ? attachInspectorClick(inspectorContent(
      `汇编行 ${line.lineNumber}`,
      [
        `类型: ${line.kind}`,
        `行号: ${line.lineNumber}`,
        `段: ${inspectorContext.formatter.blankValue(line.section)}`,
        `标签: ${inspectorContext.formatter.blankValue(line.label)}`,
        `文本: ${line.text}`,
        inspectorContext.formatter.rangeLine(line.range),
      ],
      line.range,
      inspectorContext.formatter.explainAssemblyLine(line as MiniCAssemblyTextLine),
      visual,
      inspectorContext,
    ), inspectorContext)
    : {};
  return (
    <div {...inspectProps} className="assembly-row" key={line.lineNumber}>
      <span className={`assembly-line-number${line.active ? " active" : ""}`}>{line.lineNumber}</span>
      {textFlow(highlighter.highlight(line.text), `assembly-text${line.active ? " active" : ""}`, line.active)}
    </div>
  );
}

function activeScopeRows(visual: UiStageVisualDto | null, selectedSemanticScopeId = "") {
  if (!visual || !visual.semanticRoot) {
    return monoRows(["暂无活动作用域。"]);
  }
  const scope = selectedScope(visual.semanticRoot, selectedSemanticScopeId);
  if (scope === null) {
    return monoRows(["暂无活动作用域。"]);
  }
  if (scope.symbols.length === 0) {
    return monoRows([`${scope.label} 暂无符号。`]);
  }
  return monoRows(scope.symbols);
}

export function semanticRows(visual: UiStageVisualDto | null, factory = new MiniCSemanticScopeTreeModelFactory()) {
  if (!visual || !visual.semanticRoot) {
    return <div>{textRow("作用域尚未就绪", "semantic-row", "semantic-scope-line")}</div>;
  }
  return <div>{factory.create(visual).map((line, index) => semanticRow(line, index))}</div>;
}

function semanticRow(line: MiniCSemanticScopeTreeLine, index: number) {
  const prefix = "  ".repeat(line.depth);
  const text = `${prefix}^ ${line.label}  ${line.symbols.join(", ")}`;
  return (
    <div className={`semantic-row${line.active ? " active" : ""}`} key={`${line.label}-${index}`}>
      <span className={`semantic-scope-line${line.active ? " active" : ""}${line.onActivePath ? " path" : ""}`}>{text}</span>
    </div>
  );
}

export interface VisualInspectorContext {
  readonly formatter: MiniCVisualExplanationFormatter;
  readonly inspector: MiniCHoverInspector;
  readonly snapshot: MiniCWorkbenchSnapshot;
}

export function astNodeContent(
  node: UiAstNodeVisualDto | null,
  visual: UiStageVisualDto,
  inspectorContext: VisualInspectorContext,
): MiniCHoverInspectorContent {
  if (node === null) {
    return MiniCHoverInspectorContent.empty();
  }
  return inspectorContent(
    `AST 节点 ${node.kind}`,
    [
      `id: ${node.id}`,
      `类型: ${node.kind}`,
      `标签: ${node.label}`,
      `子节点数: ${node.children.length}`,
      `当前节点: ${inspectorContext.formatter.yesNo(node.active)}`,
      inspectorContext.formatter.rangeLine(node.range),
    ],
    node.range,
    inspectorContext.formatter.explainAstNode(node),
    visual,
    inspectorContext,
  );
}

export function semanticScopeContent(
  scope: UiSemanticScopeVisualDto | null,
  depth: number,
  visual: UiStageVisualDto,
  inspectorContext: VisualInspectorContext,
): MiniCHoverInspectorContent {
  if (scope === null) {
    return MiniCHoverInspectorContent.empty();
  }
  return inspectorContent(
    `语义作用域 ${scope.label}`,
    [
      `id: ${scope.id}`,
      `深度: ${depth}`,
      `当前作用域: ${inspectorContext.formatter.yesNo(scope.active)}`,
      `符号数: ${scope.symbols.length}`,
      inspectorContext.formatter.rangeLine(scope.range),
    ],
    scope.range,
    "语义阶段右侧已经展示该作用域内的变量和符号，这里只显示作用域元数据与源码位置。",
    visual,
    inspectorContext,
  );
}

export function inspectorContent(
  title: string,
  metadata: readonly string[],
  range: UiSourceSpanDto | null,
  explanation: string,
  visual: UiStageVisualDto | null,
  inspectorContext: VisualInspectorContext,
): MiniCHoverInspectorContent {
  return new MiniCHoverInspectorContent(
    title,
    metadata,
    sourceTextForRange(range, visual, inspectorContext.snapshot),
    range,
    explanation,
  );
}

export function attachInspectorClick(
  content: MiniCHoverInspectorContent,
  inspectorContext: VisualInspectorContext,
) {
  const show = (): void => {
    inspectorContext.inspector.show(content);
  };
  return {
    onClick: (event: ReactMouseEvent<HTMLElement>) => {
      show();
      event.stopPropagation();
    },
    onKeyDown: (event: ReactKeyboardEvent<HTMLElement>) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        show();
      }
    },
    role: "button",
    tabIndex: 0,
  };
}

export function sourceTextForRange(
  range: UiSourceSpanDto | null,
  preferredVisual: UiStageVisualDto | null,
  snapshot: MiniCWorkbenchSnapshot,
): string {
  const preferredSource = sourceTextFromVisual(range, preferredVisual);
  if (preferredSource.trim().length > 0) {
    return preferredSource;
  }
  const visuals = [
    snapshot.currentStageVisualData,
    snapshot.semanticVisualData,
    snapshot.astVisualData,
    snapshot.lexerVisualData,
    snapshot.codegenVisualData,
  ];
  for (const visual of visuals) {
    const source = sourceTextFromVisual(range, visual);
    if (source.trim().length > 0) {
      return source;
    }
  }
  return snapshot.sourceText;
}

export function sourceSnippetForRange(
  range: UiSourceSpanDto | null,
  preferredVisual: UiStageVisualDto | null,
  snapshot: MiniCWorkbenchSnapshot,
): string {
  if (range === null) {
    return "<暂无源码片段>";
  }
  const source = sourceTextForRange(range, preferredVisual, snapshot);
  if (source.trim().length === 0) {
    return "<暂无源码片段>";
  }
  const start = Math.max(0, Math.min(range.startOffset, source.length));
  const end = Math.max(start, Math.min(range.endOffset, source.length));
  const snippet = source.slice(start, end).trim();
  if (snippet.length === 0) {
    return "<暂无源码片段>";
  }
  return snippet
    .replaceAll("\r\n", "\n")
    .replaceAll("\r", "\n")
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .join(" ");
}

export function sourceTextFromVisual(range: UiSourceSpanDto | null, visual: UiStageVisualDto | null): string {
  if (visual === null || visual.sourceText.trim().length === 0) {
    return "";
  }
  if (range === null || visualContainsSourceName(visual, range.sourceName)) {
    return visual.sourceText;
  }
  return "";
}

export function visualContainsSourceName(visual: UiStageVisualDto, sourceName: string): boolean {
  if (sourceName.trim().length === 0) {
    return true;
  }
  return visual.lexerTokens.some((token) => sameSource(token.range, sourceName))
    || astContainsSourceName(visual.astRoot, sourceName)
    || scopeContainsSourceName(visual.semanticRoot, sourceName)
    || visual.irLines.some((line) => sameSource(line.range, sourceName))
    || visual.assemblyLines.some((line) => sameSource(line.range, sourceName));
}

export function astContainsSourceName(node: UiAstNodeVisualDto | null, sourceName: string): boolean {
  if (node === null) {
    return false;
  }
  return sameSource(node.range, sourceName) || node.children.some((child) => astContainsSourceName(child, sourceName));
}

export function scopeContainsSourceName(scope: UiSemanticScopeVisualDto | null, sourceName: string): boolean {
  if (scope === null) {
    return false;
  }
  return sameSource(scope.range, sourceName) || scope.children.some((child) => scopeContainsSourceName(child, sourceName));
}

export function sameSource(range: UiSourceSpanDto | null, sourceName: string): boolean {
  return range !== null && range.sourceName === sourceName;
}

export function scopeById(scope: UiSemanticScopeVisualDto | null, id: string): UiSemanticScopeVisualDto | null {
  if (scope === null) {
    return null;
  }
  if (scope.id === id) {
    return scope;
  }
  for (const child of scope.children) {
    const found = scopeById(child, id);
    if (found !== null) {
      return found;
    }
  }
  return null;
}

export function selectedScope(scope: UiSemanticScopeVisualDto | null, selectedSemanticScopeId: string): UiSemanticScopeVisualDto | null {
  if (selectedSemanticScopeId.trim().length > 0) {
    const selected = scopeById(scope, selectedSemanticScopeId);
    if (selected !== null) {
      return selected;
    }
  }
  return activeScope(scope);
}

export function activeScope(scope: UiSemanticScopeVisualDto | null): UiSemanticScopeVisualDto | null {
  if (scope === null) {
    return null;
  }
  if (scope.active) {
    return scope;
  }
  for (const child of scope.children) {
    const active = activeScope(child);
    if (active !== null) {
      return active;
    }
  }
  return null;
}

export function scopeDepth(scope: UiSemanticScopeVisualDto | null, id: string, depth = 0): number {
  if (scope === null) {
    return 0;
  }
  if (scope.id === id) {
    return depth;
  }
  for (const child of scope.children) {
    const childDepth = scopeDepth(child, id, depth + 1);
    if (childDepth > 0 || child.id === id) {
      return childDepth;
    }
  }
  return 0;
}

export function tokenRows(visual: UiStageVisualDto | null, inspectorContext?: VisualInspectorContext) {
  if (!visual || visual.lexerTokens.length === 0) {
    return <div>{textRow("Token 尚未就绪", "token-row", "token-text")}</div>;
  }
  return (
    <div>
      {visual.lexerTokens.map((token, index) => tokenRow(token, visual, index, inspectorContext))}
    </div>
  );
}

export function tokenRow(
  token: UiLexerTokenVisualDto,
  visual: UiStageVisualDto,
  index: number,
  inspectorContext?: VisualInspectorContext,
) {
  const inspectProps = inspectorContext
    ? attachInspectorClick(inspectorContent(
      `Token ${token.kind}`,
      [
        `类型: ${token.kind}`,
        `文本: ${inspectorContext.formatter.displayTokenText(token)}`,
        token.range ? `offset: ${token.range.startOffset}..${token.range.endOffset}` : "offset: 不可用",
        token.range
          ? `位置: ${token.range.startLine}:${token.range.startColumn} - ${token.range.endLine}:${token.range.endColumn}`
          : "位置: 不可用",
      ],
      token.range,
      inspectorContext.formatter.explainToken(token),
      visual,
      inspectorContext,
    ), inspectorContext)
    : {};
  return (
    <div {...inspectProps} className={`token-row${token.active ? " active" : ""}`} key={`${token.text}-${index}`}>
      <span className={`token-kind${token.active ? " active" : ""}`}>{token.kind}</span>
      <span className={`token-text${token.active ? " active" : ""}`}>{token.text.length > 0 ? token.text : "<EOF>"}</span>
      <span className={`token-range${token.active ? " active" : ""}`}>
        {token.range ? `${token.range.startOffset}-${token.range.endOffset}` : "-"}
      </span>
    </div>
  );
}

function monoRows(rows: readonly string[]) {
  return (
    <div>
      {rows.map((row, index) => textRow(row, "assembly-row", "assembly-text", index))}
    </div>
  );
}

function textRow(text: string, rowStyle: string, textStyle: string, index = 0) {
  return (
    <div className={rowStyle} key={`${text}-${index}`}>
      <span className={textStyle}>{text.length > 0 ? text : " "}</span>
    </div>
  );
}

function astZoomPercent(value: number): string {
  return `${(value * 100).toFixed(1)}%`;
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
