import { useEffect, useMemo, useState, type MouseEvent as ReactMouseEvent } from "react";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiSourceSpanDto } from "../translation/uiapi";
import { MiniCSettings } from "../settings/MiniCSettings";
import { MiniCExplanationTextHighlighter } from "../text/MiniCExplanationTextHighlighter";
import { textFlow } from "../text/MiniCTextFlowFactory";
import { MiniCTextStyleRole } from "../text/MiniCTextStyleRole";
import { MiniCTextStyles } from "../text/MiniCTextStyles";
import type { MiniCWorkbenchViewModel } from "../workbench/MiniCWorkbenchViewModel";
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
const DEFAULT_EXPANDED_HEIGHT = 212;
const MIN_EXPANDED_HEIGHT = 120;
const MAX_EXPANDED_HEIGHT = 520;
const explanationTextHighlighter = new MiniCExplanationTextHighlighter();

export function MiniCBottomPanel({ viewModel: _viewModel, inspector }: MiniCBottomPanelProps) {
  const localInspector = useMemo(() => inspector ?? new MiniCHoverInspector(), [inspector]);
  const hoverContent = useHoverContent(localInspector);
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
          {expanded ? "-" : "+"}
        </button>
      </div>
      {expanded && (
        <div className="bottom-body">
          {!hoverContent.emptyContent() ? (
            <>
              {leftContent(hoverContent)}
              {rightContent(hoverContent)}
            </>
          ) : null}
        </div>
      )}
    </section>
  );
}

MiniCBottomPanel.mirror = miniCBottomPanelMirror;

export function leftContent(content: MiniCHoverInspectorContent) {
  return (
    <section className="hover-inspector-left">
      <div className="hover-left-scroll">
        <div className="hover-left-content">
          <h2 className="hover-inspector-title">{content.title}</h2>
          {lines(content.metadata, "hover-inspector-meta")}
          {sourceLines(content.source, content.range)}
        </div>
      </div>
    </section>
  );
}

export function rightContent(content: MiniCHoverInspectorContent) {
  return (
    <section className="hover-inspector-right">
      <h2 className="hover-inspector-title">说明</h2>
      <div className="hover-explanation-scroll">{explanationText(content.explanation.length > 0 ? content.explanation : "暂无说明。")}</div>
    </section>
  );
}

export function explanationText(text: string) {
  return textFlow(explanationTextHighlighter.highlight(text), "hover-inspector-explanation", false);
}

export function lines(rows: readonly string[], styleClass: string) {
  return (
    <div className={styleClass}>
      {rows.map((row, index) => (
        <p className="hover-inspector-line" key={`${row}-${index}`}>
          {row && row.trim().length > 0 ? row : " "}
        </p>
      ))}
    </div>
  );
}

export function sourceLines(source: string, range: UiSourceSpanDto | null) {
  const rows = source.split(/\r?\n/);
  const tokenStyles = sourceTokenStyles(source);
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
            <span className="hover-source-text-flow">{sourceLineText(row, lineStart, range, tokenStyles)}</span>
          </div>
        );
      })}
    </div>
  );
}

export function sourceLineText(
  line: string,
  lineStartOffset: number,
  range: UiSourceSpanDto | null,
  tokenStyles: readonly SourceTokenStyle[] = [],
) {
  return Array.from(line.length === 0 ? " " : line).map((char, index) => {
    const absolute = lineStartOffset + index;
    const masked = range !== null && absolute >= range.startOffset && absolute < range.endOffset;
    const styleClasses = sourceStyleClasses(absolute, tokenStyles);
    return (
      <span className={["hover-source-text", ...styleClasses, masked ? "masked" : ""].filter(Boolean).join(" ")} key={`${absolute}-${char}`}>
        {char === " " ? "\u00a0" : char}
      </span>
    );
  });
}

interface SourceTokenStyle {
  readonly startOffset: number;
  readonly endOffset: number;
  readonly styleClasses: readonly string[];
}

export function sourceTokenStyles(source: string | null | undefined): readonly SourceTokenStyle[] {
  if (source === null || source === undefined || source.trim().length === 0) {
    return [];
  }
  return [];
}

export function sourceStyleClasses(absoluteOffset: number, tokenStyles: readonly SourceTokenStyle[]): readonly string[] {
  return tokenStyles.find((style) => absoluteOffset >= style.startOffset && absoluteOffset < style.endOffset)?.styleClasses
    ?? MiniCTextStyles.classes(MiniCTextStyleRole.CODE_PLAIN);
}

export function lineSeparatorLength(source: string, separatorOffset: number): number {
  if (separatorOffset >= source.length) {
    return 0;
  }
  if (source[separatorOffset] === "\r" && source[separatorOffset + 1] === "\n") {
    return 2;
  }
  return 1;
}

export function centerSourceRangeLater(scrollPane: HTMLElement | null, range: UiSourceSpanDto | null, lineCount: number): void {
  if (scrollPane === null || range === null || lineCount <= 0) {
    return;
  }
  window.requestAnimationFrame(() => {
    const lineHeight = scrollPane.scrollHeight / lineCount;
    const targetTop = Math.max(0, (range.startLine - 1) * lineHeight - scrollPane.clientHeight / 2);
    scrollPane.scrollTop = Math.min(targetTop, Math.max(0, scrollPane.scrollHeight - scrollPane.clientHeight));
  });
}

export function clampHeight(height: number): number {
  return Math.max(MIN_EXPANDED_HEIGHT, Math.min(MAX_EXPANDED_HEIGHT, height));
}

export function scaled(value: number): number {
  return value * uiScale();
}

export function uiScale(): number {
  return MiniCSettings.uiScale();
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
