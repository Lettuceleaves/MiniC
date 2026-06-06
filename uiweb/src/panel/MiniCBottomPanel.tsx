import { useEffect, useMemo, useState, type MouseEvent as ReactMouseEvent } from "react";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiSourceSpanDto } from "../translation/uiapi";
import type { MiniCWorkbenchSnapshot, MiniCWorkbenchViewModel } from "../workbench/MiniCWorkbenchViewModel";
import { MiniCBottomPanelModelFactory } from "./MiniCBottomPanelModelFactory";
import { MiniCHoverInspector } from "./MiniCHoverInspector";
import { MiniCHoverInspectorContent } from "./MiniCHoverInspectorContent";

export const miniCBottomPanelMirror = {
  "javaPath": "src/main/java/minic/uilocal/panel/MiniCBottomPanel.java",
  "webPath": "uiweb/src/panel/MiniCBottomPanel.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCBottomPanel",
  "kind": "component",
  "imports": [
    "javafx.application.Platform",
    "javafx.scene.Cursor",
    "javafx.scene.control.Button",
    "javafx.scene.control.Label",
    "javafx.scene.control.ScrollPane",
    "javafx.scene.input.MouseEvent",
    "javafx.scene.layout.HBox",
    "javafx.scene.layout.Priority",
    "javafx.scene.layout.Region",
    "javafx.scene.layout.VBox",
    "javafx.scene.text.TextFlow",
    "minic.compiler.lexer.Lexer",
    "minic.compiler.lexer.Token",
    "minic.uilocal.text.MiniCExplanationTextHighlighter",
    "minic.uilocal.text.MiniCSyntaxTextStyleMapper",
    "minic.uilocal.text.MiniCTextFlowFactory",
    "minic.uilocal.text.MiniCTextStyleRole",
    "minic.uilocal.text.MiniCTextStyles",
    "minic.uiapi.UiSourceSpanDto",
    "minic.source.SourceFile",
    "minic.settings.MiniCSettings",
    "java.util.ArrayList",
    "java.util.Collection",
    "java.util.Comparator",
    "java.util.List",
    "java.util.Objects"
  ],
  "fields": [
    {
      "name": "COLLAPSED_HEIGHT",
      "signature": "private static final double COLLAPSED_HEIGHT ="
    },
    {
      "name": "DEFAULT_EXPANDED_HEIGHT",
      "signature": "private static final double DEFAULT_EXPANDED_HEIGHT ="
    },
    {
      "name": "MIN_EXPANDED_HEIGHT",
      "signature": "private static final double MIN_EXPANDED_HEIGHT ="
    },
    {
      "name": "MAX_EXPANDED_HEIGHT",
      "signature": "private static final double MAX_EXPANDED_HEIGHT ="
    },
    {
      "name": "DRAG_START_Y_KEY",
      "signature": "private static final String DRAG_START_Y_KEY ="
    },
    {
      "name": "DRAG_START_HEIGHT_KEY",
      "signature": "private static final String DRAG_START_HEIGHT_KEY ="
    },
    {
      "name": "inspector",
      "signature": "private final MiniCHoverInspector inspector;"
    },
    {
      "name": "explanationTextHighlighter",
      "signature": "private final MiniCExplanationTextHighlighter explanationTextHighlighter ="
    },
    {
      "name": "syntaxTextStyleMapper",
      "signature": "private final MiniCSyntaxTextStyleMapper syntaxTextStyleMapper ="
    },
    {
      "name": "resizeHandle",
      "signature": "private final Region resizeHandle ="
    },
    {
      "name": "body",
      "signature": "private final HBox body ="
    },
    {
      "name": "toggle",
      "signature": "private final Button toggle ="
    },
    {
      "name": "uiScaleChangeListener",
      "signature": "private final Runnable uiScaleChangeListener ="
    },
    {
      "name": "expandedHeight",
      "signature": "private double expandedHeight ="
    },
    {
      "name": "expanded",
      "signature": "private boolean expanded;"
    }
  ],
  "methods": [
    {
      "name": "render",
      "signature": "render(MiniCHoverInspectorContent content)"
    },
    {
      "name": "leftContent",
      "signature": "leftContent(MiniCHoverInspectorContent content)"
    },
    {
      "name": "rightContent",
      "signature": "rightContent(MiniCHoverInspectorContent content)"
    },
    {
      "name": "explanationText",
      "signature": "explanationText(String text)"
    },
    {
      "name": "lines",
      "signature": "lines(List<String> rows, String styleClass)"
    },
    {
      "name": "sourceLines",
      "signature": "sourceLines(String source, UiSourceSpanDto range)"
    },
    {
      "name": "sourceLineText",
      "signature": "sourceLineText(String line, int lineStartOffset, UiSourceSpanDto range, List<SourceTokenStyle> tokenStyles)"
    },
    {
      "name": "sourceChar",
      "signature": "sourceChar(String text, Collection<String> textStyleClasses)"
    },
    {
      "name": "sourceTokenStyles",
      "signature": "sourceTokenStyles(String source)"
    },
    {
      "name": "sourceTokenStyle",
      "signature": "sourceTokenStyle(Token token)"
    },
    {
      "name": "sourceStyleClasses",
      "signature": "sourceStyleClasses(int absoluteOffset, List<SourceTokenStyle> tokenStyles)"
    },
    {
      "name": "SourceTokenStyle",
      "signature": "SourceTokenStyle(int startOffset, int endOffset, Collection<String> styleClasses)"
    },
    {
      "name": "lineSeparatorLength",
      "signature": "lineSeparatorLength(String source, int separatorOffset)"
    },
    {
      "name": "centerSourceRangeLater",
      "signature": "centerSourceRangeLater(ScrollPane scrollPane, UiSourceSpanDto range, int lineCount)"
    },
    {
      "name": "configureResizeHandle",
      "signature": "configureResizeHandle()"
    },
    {
      "name": "setExpanded",
      "signature": "setExpanded(boolean expanded)"
    },
    {
      "name": "applyHeight",
      "signature": "applyHeight()"
    },
    {
      "name": "clampHeight",
      "signature": "clampHeight(double height)"
    },
    {
      "name": "applyHeightOnFxThread",
      "signature": "applyHeightOnFxThread()"
    },
    {
      "name": "scaled",
      "signature": "scaled(double value)"
    },
    {
      "name": "uiScale",
      "signature": "uiScale()"
    }
  ]
} as const satisfies JavaMirrorFile;

export interface MiniCBottomPanelProps {
  readonly viewModel: MiniCWorkbenchViewModel;
  readonly inspector?: MiniCHoverInspector;
}

const COLLAPSED_HEIGHT = 24;
const DEFAULT_EXPANDED_HEIGHT = 220;
const MIN_EXPANDED_HEIGHT = 120;
const MAX_EXPANDED_HEIGHT = 520;

export function MiniCBottomPanel({ viewModel, inspector }: MiniCBottomPanelProps) {
  const snapshot = useBottomPanelSnapshot(viewModel);
  const localInspector = useMemo(() => inspector ?? new MiniCHoverInspector(), [inspector]);
  const hoverContent = useHoverContent(localInspector);
  const factory = useMemo(() => new MiniCBottomPanelModelFactory(), []);
  const model = useMemo(
    () => factory.create(snapshot.currentStageData, snapshot.globalData, snapshot.realtimeAnalysis),
    [factory, snapshot.currentStageData, snapshot.globalData, snapshot.realtimeAnalysis],
  );
  const [expanded, setExpanded] = useState(false);
  const [expandedHeight, setExpandedHeight] = useState(DEFAULT_EXPANDED_HEIGHT);

  const startResize = (event: ReactMouseEvent<HTMLDivElement>): void => {
    event.preventDefault();
    const startY = event.clientY;
    const startHeight = expandedHeight;
    const move = (moveEvent: MouseEvent): void => {
      setExpandedHeight(clampHeight(startHeight + startY - moveEvent.clientY));
      setExpanded(true);
    };
    const up = (): void => {
      window.removeEventListener("mousemove", move);
      window.removeEventListener("mouseup", up);
    };
    window.addEventListener("mousemove", move);
    window.addEventListener("mouseup", up);
  };

  const emptyHover = hoverContent.emptyContent();
  const panelHeight = expanded ? expandedHeight : COLLAPSED_HEIGHT;

  return (
    <section
      className={`bottom-panel ${expanded ? "expanded" : "collapsed"}`}
      data-java-source={miniCBottomPanelMirror.javaPath}
      style={{ height: `${panelHeight}px` }}
    >
      <div className="bottom-resize-handle" onMouseDown={startResize} role="separator" aria-orientation="horizontal" />
      <div className="bottom-bar">
        <button className="bottom-toggle" onClick={() => setExpanded((current) => !current)} type="button">
          {expanded ? "v" : "^"}
        </button>
        <span className="panel-title">{emptyHover ? "输出" : hoverContent.title}</span>
      </div>
      {expanded && (
        <div className="bottom-body">
          {emptyHover ? (
            <div className="hover-inspector-left">
              {lines(["Problems", ...model.problems], "hover-inspector-line")}
              {lines(["Output", ...model.output], "hover-inspector-line")}
              {lines(["Terminal", ...model.terminal], "hover-inspector-line")}
            </div>
          ) : (
            <>
              {leftContent(hoverContent)}
              {rightContent(hoverContent)}
            </>
          )}
        </div>
      )}
    </section>
  );
}

MiniCBottomPanel.mirror = miniCBottomPanelMirror;

export function leftContent(content: MiniCHoverInspectorContent) {
  return (
    <section className="hover-inspector-left">
      <h2 className="hover-inspector-title">{content.title}</h2>
      <div className="hover-inspector-meta">{lines(content.metadata, "hover-inspector-line")}</div>
      <div className="hover-explanation-scroll">{explanationText(content.explanation)}</div>
    </section>
  );
}

export function rightContent(content: MiniCHoverInspectorContent) {
  return (
    <section className="hover-inspector-right">
      <h2 className="hover-inspector-title">源码</h2>
      {sourceLines(content.source, content.range)}
    </section>
  );
}

export function explanationText(text: string) {
  return <p className="hover-inspector-explanation">{text || "无解释文本"}</p>;
}

export function lines(rows: readonly string[], styleClass: string) {
  return (
    <div>
      {rows.map((row, index) => (
        <p className={styleClass} key={`${row}-${index}`}>
          {row}
        </p>
      ))}
    </div>
  );
}

export function sourceLines(source: string, range: UiSourceSpanDto | null) {
  const rows = source.split(/\r?\n/);
  let offset = 0;
  return (
    <div className="hover-source">
      {rows.map((row, index) => {
        const lineStart = offset;
        const lineEnd = lineStart + row.length;
        offset = lineEnd + lineSeparatorLength(source, lineEnd);
        return (
          <div className="hover-source-row" key={`${index}-${row}`}>
            <span className="hover-source-line-number">{index + 1}</span>
            <span className="hover-source-text-flow">{sourceLineText(row, lineStart, range)}</span>
          </div>
        );
      })}
    </div>
  );
}

export function sourceLineText(line: string, lineStartOffset: number, range: UiSourceSpanDto | null) {
  return Array.from(line.length === 0 ? " " : line).map((char, index) => {
    const absolute = lineStartOffset + index;
    const masked = range !== null && absolute >= range.startOffset && absolute < range.endOffset;
    return (
      <span className={`hover-source-text${masked ? " masked" : ""}`} key={`${absolute}-${char}`}>
        {char === " " ? "\u00a0" : char}
      </span>
    );
  });
}

export function lineSeparatorLength(source: string, separatorOffset: number): number {
  if (source[separatorOffset] === "\r" && source[separatorOffset + 1] === "\n") {
    return 2;
  }
  return source[separatorOffset] === "\n" || source[separatorOffset] === "\r" ? 1 : 0;
}

export function centerSourceRangeLater(): void {
  return undefined;
}

export function clampHeight(height: number): number {
  return Math.max(MIN_EXPANDED_HEIGHT, Math.min(MAX_EXPANDED_HEIGHT, height));
}

export function scaled(value: number): number {
  return value;
}

export function uiScale(): number {
  return 1;
}

function useBottomPanelSnapshot(viewModel: MiniCWorkbenchViewModel): MiniCWorkbenchSnapshot {
  const [snapshot, setSnapshot] = useState(() => viewModel.snapshot());

  useEffect(() => {
    setSnapshot(viewModel.snapshot());
    return viewModel.subscribe(() => {
      setSnapshot(viewModel.snapshot());
    });
  }, [viewModel]);

  return snapshot;
}

function useHoverContent(inspector: MiniCHoverInspector): MiniCHoverInspectorContent {
  const [content, setContent] = useState(() => inspector.contentProperty());

  useEffect(() => {
    setContent(inspector.contentProperty());
    return inspector.subscribe(setContent);
  }, [inspector]);

  return content;
}

export default MiniCBottomPanel;
