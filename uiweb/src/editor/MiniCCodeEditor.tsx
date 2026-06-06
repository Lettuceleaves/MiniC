import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type KeyboardEvent,
} from "react";
import { MiniCTextViewportAdapter } from "../control/MiniCTextViewportAdapter";
import type { MiniCViewportAdapter } from "../control/MiniCViewportAdapter";
import { MiniCCompletionSuggester } from "./MiniCCompletionSuggester";
import { MiniCEditorFormatter } from "./MiniCEditorFormatter";
import { MiniCEditorTyping, type MiniCEditResult } from "./MiniCEditorTyping";
import { MiniCSyntaxTextStyleMapper } from "../text/MiniCSyntaxTextStyleMapper";
import { MiniCTextStyleRole } from "../text/MiniCTextStyleRole";
import { MiniCTextStyleState } from "../text/MiniCTextStyleState";
import { MiniCTextStyles } from "../text/MiniCTextStyles";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiDiagnosticDto, UiLexerTokenVisualDto, UiRealtimeAnalysisDto, UiSourceSpanDto } from "../translation/uiTypes";
import { clampNumber, sourcePosition } from "../translation/uiTypes";

export const miniCCodeEditorMirror = {
  "javaPath": "src/main/java/minic/uilocal/editor/MiniCCodeEditor.java",
  "webPath": "uiweb/src/editor/MiniCCodeEditor.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCCodeEditor",
  "kind": "component",
  "imports": [
    "java.util.ArrayList",
    "java.util.Collection",
    "java.util.Comparator",
    "java.util.LinkedHashSet",
    "java.util.List",
    "java.util.Objects",
    "java.util.Set",
    "java.util.function.IntFunction",
    "javafx.application.Platform",
    "javafx.beans.value.ObservableValue",
    "javafx.geometry.Bounds",
    "javafx.geometry.Pos",
    "javafx.scene.Node",
    "javafx.scene.control.Label",
    "javafx.scene.control.ListCell",
    "javafx.scene.control.ListView",
    "javafx.scene.control.ScrollPane",
    "javafx.scene.input.KeyCode",
    "javafx.scene.input.KeyEvent",
    "javafx.scene.input.MouseButton",
    "javafx.scene.layout.HBox",
    "javafx.scene.layout.Pane",
    "javafx.scene.layout.StackPane",
    "javafx.scene.layout.VBox",
    "javafx.scene.shape.Polyline",
    "minic.color.ThemeRegistry",
    "minic.uiapi.UiDiagnosticDto",
    "minic.uiapi.UiLexerTokenVisualDto",
    "minic.uiapi.UiRealtimeAnalysisDto",
    "minic.uiapi.UiSourceSpanDto",
    "minic.uilocal.control.MiniCTextViewportAdapter",
    "minic.uilocal.control.MiniCViewportAdapter",
    "minic.uilocal.text.MiniCSyntaxTextStyleMapper",
    "minic.uilocal.text.MiniCTextStyleRole",
    "minic.uilocal.text.MiniCTextStyleState",
    "minic.uilocal.text.MiniCTextStyles",
    "org.fxmisc.flowless.VirtualizedScrollPane",
    "org.fxmisc.richtext.LineNumberFactory",
    "org.fxmisc.richtext.StyleClassedTextArea",
    "org.fxmisc.richtext.model.StyleSpans",
    "org.fxmisc.richtext.model.StyleSpansBuilder"
  ],
  "fields": [
    {
      "name": "breakpointChangeAction",
      "signature": "private Runnable breakpointChangeAction="
    },
    {
      "name": "breakpointLines",
      "signature": "private final Set<Integer>breakpointLines="
    },
    {
      "name": "completionList",
      "signature": "private final ListView<String>completionList="
    },
    {
      "name": "currentExecutionLine",
      "signature": "private int currentExecutionLine"
    },
    {
      "name": "currentExecutionRangeEnd",
      "signature": "private int currentExecutionRangeEnd="
    },
    {
      "name": "currentExecutionRangeStart",
      "signature": "private int currentExecutionRangeStart="
    },
    {
      "name": "DEFAULT_EDITOR_FONT_SIZE",
      "signature": "private static final double DEFAULT_EDITOR_FONT_SIZE="
    },
    {
      "name": "diagnosticDetails",
      "signature": "private final VBox diagnosticDetails="
    },
    {
      "name": "diagnosticLayer",
      "signature": "private final Pane diagnosticLayer="
    },
    {
      "name": "editorFontSize",
      "signature": "private double editorFontSize="
    },
    {
      "name": "formatter",
      "signature": "private final MiniCEditorFormatter formatter="
    },
    {
      "name": "input",
      "signature": "private final StyleClassedTextArea input="
    },
    {
      "name": "latestAnalysis",
      "signature": "private UiRealtimeAnalysisDto latestAnalysis"
    },
    {
      "name": "latestDiagnostics",
      "signature": "private List<UiDiagnosticDto>latestDiagnostics="
    },
    {
      "name": "lineNumberFactory",
      "signature": "private final IntFunction<Node>lineNumberFactory="
    },
    {
      "name": "MAX_EDITOR_FONT_SIZE",
      "signature": "private static final double MAX_EDITOR_FONT_SIZE="
    },
    {
      "name": "MIN_EDITOR_FONT_SIZE",
      "signature": "private static final double MIN_EDITOR_FONT_SIZE="
    },
    {
      "name": "requestedScrollY",
      "signature": "private double requestedScrollY"
    },
    {
      "name": "scrollPane",
      "signature": "private final VirtualizedScrollPane<StyleClassedTextArea>scrollPane="
    },
    {
      "name": "syntaxTextStyleMapper",
      "signature": "private final MiniCSyntaxTextStyleMapper syntaxTextStyleMapper="
    },
    {
      "name": "viewportAdapter",
      "signature": "private final MiniCViewportAdapter viewportAdapter="
    }
  ],
  "methods": [
    {
      "name": "addDiagnosticWave",
      "signature": "addDiagnosticWave(Bounds bounds)"
    },
    {
      "name": "addScrollContainerStyleClass",
      "signature": "addScrollContainerStyleClass(String styleClass)"
    },
    {
      "name": "addStyledRange",
      "signature": "addStyledRange(StyleSpansBuilder<Collection<String>>builder,String source,int start,int end,Collection<String>baseStyles)"
    },
    {
      "name": "adjustEditorFontSize",
      "signature": "adjustEditorFontSize(double delta)"
    },
    {
      "name": "applyCompletion",
      "signature": "applyCompletion(String suggestion)"
    },
    {
      "name": "applyEditorFontSize",
      "signature": "applyEditorFontSize()"
    },
    {
      "name": "applyGutterSize",
      "signature": "applyGutterSize(Label label)"
    },
    {
      "name": "applySelectedCompletion",
      "signature": "applySelectedCompletion()"
    },
    {
      "name": "boundsForRange",
      "signature": "boundsForRange(String source,int start,int end)"
    },
    {
      "name": "breakpointLines",
      "signature": "breakpointLines()"
    },
    {
      "name": "centerCurrentExecution",
      "signature": "centerCurrentExecution()"
    },
    {
      "name": "centerCurrentExecutionIfNeeded",
      "signature": "centerCurrentExecutionIfNeeded()"
    },
    {
      "name": "centerY",
      "signature": "centerY()"
    },
    {
      "name": "clampScrollY",
      "signature": "clampScrollY(double scrollY)"
    },
    {
      "name": "clearCurrentExecutionRange",
      "signature": "clearCurrentExecutionRange()"
    },
    {
      "name": "configureCompletionList",
      "signature": "configureCompletionList()"
    },
    {
      "name": "currentExecutionViewport",
      "signature": "currentExecutionViewport()"
    },
    {
      "name": "diagnosticDetail",
      "signature": "diagnosticDetail(String source,UiDiagnosticDto diagnostic)"
    },
    {
      "name": "diagnosticDetailsHeight",
      "signature": "diagnosticDetailsHeight()"
    },
    {
      "name": "documentHeight",
      "signature": "documentHeight()"
    },
    {
      "name": "drawDiagnostics",
      "signature": "drawDiagnostics()"
    },
    {
      "name": "editorLineHeight",
      "signature": "editorLineHeight()"
    },
    {
      "name": "estimatedScrollY",
      "signature": "estimatedScrollY()"
    },
    {
      "name": "executionViewport",
      "signature": "executionViewport(int startLine,int endLine)"
    },
    {
      "name": "ExecutionViewport",
      "signature": "ExecutionViewport(int startLine,double topY,double bottomY)"
    },
    {
      "name": "getLength",
      "signature": "getLength()"
    },
    {
      "name": "getText",
      "signature": "getText()"
    },
    {
      "name": "handleCompletionKeys",
      "signature": "handleCompletionKeys(KeyEvent event)"
    },
    {
      "name": "handleFontZoomKey",
      "signature": "handleFontZoomKey(KeyEvent event)"
    },
    {
      "name": "hideCompletion",
      "signature": "hideCompletion()"
    },
    {
      "name": "isCompletionShowing",
      "signature": "isCompletionShowing()"
    },
    {
      "name": "isCurrentExecutionFullyVisible",
      "signature": "isCurrentExecutionFullyVisible()"
    },
    {
      "name": "isExecutionViewportFullyVisible",
      "signature": "isExecutionViewportFullyVisible(ExecutionViewport viewport)"
    },
    {
      "name": "isIdentifierPart",
      "signature": "isIdentifierPart(char value)"
    },
    {
      "name": "layoutCompletionList",
      "signature": "layoutCompletionList()"
    },
    {
      "name": "layoutDiagnosticDetails",
      "signature": "layoutDiagnosticDetails()"
    },
    {
      "name": "lineCount",
      "signature": "lineCount()"
    },
    {
      "name": "lineForOffset",
      "signature": "lineForOffset(String source,int offset)"
    },
    {
      "name": "maxScrollY",
      "signature": "maxScrollY()"
    },
    {
      "name": "overlapsDiagnostic",
      "signature": "overlapsDiagnostic(int start,int end,List<UiDiagnosticDto>diagnostics)"
    },
    {
      "name": "paragraphGraphic",
      "signature": "paragraphGraphic(int paragraphIndex)"
    },
    {
      "name": "Prefix",
      "signature": "Prefix(String text,int startOffset,int endOffset)"
    },
    {
      "name": "prefixAtCaret",
      "signature": "prefixAtCaret()"
    },
    {
      "name": "refreshParagraphGraphics",
      "signature": "refreshParagraphGraphics()"
    },
    {
      "name": "render",
      "signature": "render(UiRealtimeAnalysisDto analysis)"
    },
    {
      "name": "replaceBreakpoints",
      "signature": "replaceBreakpoints(List<Integer>lines)"
    },
    {
      "name": "safeOffset",
      "signature": "safeOffset(String source,int offset)"
    },
    {
      "name": "scrollVerticalBy",
      "signature": "scrollVerticalBy(double pixels)"
    },
    {
      "name": "scrollYToPixel",
      "signature": "scrollYToPixel(double scrollY)"
    },
    {
      "name": "selectCompletionOffset",
      "signature": "selectCompletionOffset(int offset)"
    },
    {
      "name": "selectRange",
      "signature": "selectRange(int start,int end)"
    },
    {
      "name": "setBreakpoint",
      "signature": "setBreakpoint(int line,boolean enabled)"
    },
    {
      "name": "setBreakpointChangeAction",
      "signature": "setBreakpointChangeAction(Runnable breakpointChangeAction)"
    },
    {
      "name": "setCurrentExecutionLine",
      "signature": "setCurrentExecutionLine(int line)"
    },
    {
      "name": "setCurrentExecutionRange",
      "signature": "setCurrentExecutionRange(int startOffset,int endOffset)"
    },
    {
      "name": "setCurrentExecutionRange",
      "signature": "setCurrentExecutionRange(UiSourceSpanDto range)"
    },
    {
      "name": "setScrollBarPolicies",
      "signature": "setScrollBarPolicies(ScrollPane.ScrollBarPolicy horizontalPolicy,ScrollPane.ScrollBarPolicy verticalPolicy)"
    },
    {
      "name": "setText",
      "signature": "setText(String text)"
    },
    {
      "name": "showCompletion",
      "signature": "showCompletion()"
    },
    {
      "name": "sourcePosition",
      "signature": "sourcePosition(String source,int offset)"
    },
    {
      "name": "SourcePosition",
      "signature": "SourcePosition(int line,int byteOffsetInLine)"
    },
    {
      "name": "styleSpans",
      "signature": "styleSpans(String source,UiRealtimeAnalysisDto analysis)"
    },
    {
      "name": "textProperty",
      "signature": "textProperty()"
    },
    {
      "name": "toggleBreakpoint",
      "signature": "toggleBreakpoint(int line)"
    },
    {
      "name": "tokenStyles",
      "signature": "tokenStyles(String kind,boolean diagnostic)"
    },
    {
      "name": "updateCompletion",
      "signature": "updateCompletion(boolean force)"
    },
    {
      "name": "updateDiagnosticDetails",
      "signature": "updateDiagnosticDetails()"
    },
    {
      "name": "updateItem",
      "signature": "updateItem(String item,boolean empty)"
    },
    {
      "name": "viewportAdapter",
      "signature": "viewportAdapter()"
    },
    {
      "name": "visibleViewportHeight",
      "signature": "visibleViewportHeight()"
    },
    {
      "name": "withDebugExecutionRange",
      "signature": "withDebugExecutionRange(Collection<String>baseStyles)"
    },
    {
      "name": "zoomFontBy",
      "signature": "zoomFontBy(double delta)"
    }
  ]
} as const satisfies JavaMirrorFile;

interface Prefix {
  readonly text: string;
  readonly startOffset: number;
  readonly endOffset: number;
}

interface ExecutionViewport {
  readonly startLine: number;
  readonly topY: number;
  readonly bottomY: number;
}

interface EditorToken {
  readonly kind: string;
  readonly startOffset: number;
  readonly endOffset: number;
}

interface EditorLineSegment {
  readonly text: string;
  readonly className: string;
}

interface EditorLine {
  readonly lineNumber: number;
  readonly startOffset: number;
  readonly endOffset: number;
  readonly text: string;
  readonly segments: readonly EditorLineSegment[];
}

export interface MiniCCodeEditorProps {
  readonly value?: string;
  readonly initialText?: string;
  readonly sourceName?: string;
  readonly analysis?: UiRealtimeAnalysisDto | null;
  readonly breakpoints?: readonly number[];
  readonly currentExecutionLine?: number;
  readonly currentExecutionRange?: UiSourceSpanDto | null;
  readonly readOnly?: boolean;
  readonly className?: string;
  readonly scrollContainerClassName?: string;
  readonly ariaLabel?: string;
  readonly onTextChange?: (text: string) => void;
  readonly onBreakpointsChange?: (lines: readonly number[]) => void;
  readonly onSubmitRealtimeSource?: (sourceName: string, sourceText: string) => void;
}

export class MiniCCodeEditorModel {
  static readonly mirror = miniCCodeEditorMirror;
  static readonly DEFAULT_EDITOR_FONT_SIZE = 12;
  static readonly MIN_EDITOR_FONT_SIZE = 10;
  static readonly MAX_EDITOR_FONT_SIZE = 24;

  readonly mirror = miniCCodeEditorMirror;

  private source = "";
  private readonly localBreakpointLines = new Set<number>();
  private viewport = new MiniCTextViewportAdapter(this);
  private breakpointChangeAction: (() => void) | null = null;
  private currentExecutionLine = 0;
  private currentExecutionRangeStart = -1;
  private currentExecutionRangeEnd = -1;
  private editorFontSize = MiniCCodeEditorModel.DEFAULT_EDITOR_FONT_SIZE;
  private requestedScrollY = 0;
  private latestAnalysis: UiRealtimeAnalysisDto | null = null;
  private latestDiagnostics: readonly UiDiagnosticDto[] = [];
  private horizontalScrollBarPolicy = "AS_NEEDED";
  private verticalScrollBarPolicy = "AS_NEEDED";
  private readonly scrollContainerStyleClasses = new Set<string>();
  private selectionStart = 0;
  private selectionEnd = 0;

  textProperty(): string {
    return this.source;
  }

  setText(text: string): void {
    this.source = text;
    this.clearCurrentExecutionRange();
    this.requestedScrollY = 0;
    this.render(null);
  }

  viewportAdapter(): MiniCViewportAdapter {
    return this.viewport;
  }

  setScrollBarPolicies(horizontalPolicy = "AS_NEEDED", verticalPolicy = "AS_NEEDED"): void {
    this.horizontalScrollBarPolicy = horizontalPolicy;
    this.verticalScrollBarPolicy = verticalPolicy;
  }

  addScrollContainerStyleClass(styleClass: string): void {
    const trimmed = styleClass.trim();
    if (trimmed.length > 0) {
      this.scrollContainerStyleClasses.add(trimmed);
    }
  }

  getText(): string {
    return this.source;
  }

  getLength(): number {
    return this.source.length;
  }

  selectRange(start = 0, end = start): void {
    this.selectionStart = this.safeOffset(start);
    this.selectionEnd = this.safeOffset(end);
  }

  scrollBarPolicies(): readonly [string, string] {
    return [this.horizontalScrollBarPolicy, this.verticalScrollBarPolicy];
  }

  scrollContainerStyleClassNames(): readonly string[] {
    return [...this.scrollContainerStyleClasses];
  }

  selectedRange(): readonly [number, number] {
    return [this.selectionStart, this.selectionEnd];
  }

  breakpointLines(): readonly number[] {
    return [...this.localBreakpointLines].sort((left, right) => left - right);
  }

  setBreakpoint(line: number, enabled: boolean): void {
    if (line < 1) {
      return;
    }
    if (enabled) {
      this.localBreakpointLines.add(line);
    } else {
      this.localBreakpointLines.delete(line);
    }
    this.breakpointChangeAction?.();
  }

  replaceBreakpoints(lines: readonly number[]): void {
    this.localBreakpointLines.clear();
    for (const line of lines) {
      if (Number.isInteger(line) && line >= 1) {
        this.localBreakpointLines.add(line);
      }
    }
  }

  toggleBreakpoint(line: number): void {
    if (line < 1) {
      return;
    }
    if (this.localBreakpointLines.has(line)) {
      this.localBreakpointLines.delete(line);
    } else {
      this.localBreakpointLines.add(line);
    }
    this.breakpointChangeAction?.();
  }

  setBreakpointChangeAction(breakpointChangeAction: () => void): void {
    this.breakpointChangeAction = breakpointChangeAction;
  }

  setCurrentExecutionLine(line: number): void {
    this.currentExecutionLine = Math.max(0, Math.trunc(line));
  }

  setCurrentExecutionRange(range: UiSourceSpanDto | null): void;
  setCurrentExecutionRange(startOffset: number, endOffset: number): void;
  setCurrentExecutionRange(rangeOrStart: UiSourceSpanDto | number | null, maybeEnd?: number): void {
    if (rangeOrStart === null) {
      this.clearCurrentExecutionRange();
      return;
    }
    const startOffset = typeof rangeOrStart === "number" ? rangeOrStart : rangeOrStart.startOffset;
    const endOffset = typeof rangeOrStart === "number" ? requireNumber(maybeEnd, "endOffset") : rangeOrStart.endOffset;
    const start = this.safeOffset(startOffset);
    const end = this.safeOffset(endOffset);
    if (end <= start) {
      this.clearCurrentExecutionRange();
      return;
    }
    this.currentExecutionRangeStart = start;
    this.currentExecutionRangeEnd = end;
  }

  zoomFontBy(delta: number): void {
    if (!Number.isFinite(delta)) {
      return;
    }
    this.editorFontSize = clampNumber(
      this.editorFontSize + delta,
      MiniCCodeEditorModel.MIN_EDITOR_FONT_SIZE,
      MiniCCodeEditorModel.MAX_EDITOR_FONT_SIZE,
    );
  }

  scrollVerticalBy(pixels: number): void {
    if (!Number.isFinite(pixels) || pixels === 0) {
      return;
    }
    this.requestedScrollY = this.clampScrollY(this.requestedScrollY + pixels);
  }

  isCurrentExecutionFullyVisible(): boolean {
    const viewport = this.currentExecutionViewport();
    return viewport ? this.isExecutionViewportFullyVisible(viewport) : true;
  }

  centerCurrentExecutionIfNeeded(): void {
    if (!this.isCurrentExecutionFullyVisible()) {
      this.centerCurrentExecution();
    }
  }

  centerCurrentExecution(): void {
    const viewport = this.currentExecutionViewport();
    if (!viewport) {
      return;
    }
    this.requestedScrollY = this.clampScrollY((viewport.topY + viewport.bottomY) / 2 - this.visibleViewportHeight() / 2);
  }

  render(analysis: UiRealtimeAnalysisDto | null): void {
    if (analysis && analysis.sourceText === this.source) {
      this.latestAnalysis = analysis;
      this.latestDiagnostics = analysis.diagnostics;
    } else if (!analysis) {
      this.latestDiagnostics = [];
    }
  }

  diagnostics(): readonly UiDiagnosticDto[] {
    return this.latestDiagnostics;
  }

  completionSuggestions(caret: number, force: boolean): readonly string[] {
    const prefix = MiniCCodeEditorModel.prefixAtCaret(this.source, caret);
    if (!force && prefix.text.length === 0) {
      return [];
    }
    return MiniCCompletionSuggester.suggestions(
      prefix.text,
      this.source,
      this.latestAnalysis?.tokens ?? [],
    );
  }

  fontSize(): number {
    return this.editorFontSize;
  }

  static prefixAtCaret(source: string, caret: number): Prefix {
    const safeCaret = clampNumber(Math.trunc(caret), 0, source.length);
    let start = safeCaret;
    while (start > 0 && MiniCCodeEditorModel.isIdentifierPart(source[start - 1])) {
      start -= 1;
    }
    if (start < safeCaret && /[0-9]/u.test(source[start])) {
      return { text: "", startOffset: safeCaret, endOffset: safeCaret };
    }
    return {
      text: source.slice(start, safeCaret),
      startOffset: start,
      endOffset: safeCaret,
    };
  }

  static isIdentifierPart(value: string): boolean {
    return /[A-Za-z0-9_]/u.test(value);
  }

  static overlapsDiagnostic(start: number, end: number, diagnostics: readonly UiDiagnosticDto[]): boolean {
    return diagnostics.some((diagnostic) =>
      Math.max(start, diagnostic.startOffset) < Math.min(end, diagnostic.endOffset),
    );
  }

  static sourcePosition(source: string, offset: number): { readonly line: number; readonly byteOffsetInLine: number } {
    const position = sourcePosition(source, offset);
    return {
      line: position.line,
      byteOffsetInLine: position.column,
    };
  }

  private clearCurrentExecutionRange(): void {
    this.currentExecutionRangeStart = -1;
    this.currentExecutionRangeEnd = -1;
  }

  private currentExecutionViewport(): ExecutionViewport | null {
    if (this.currentExecutionRangeStart >= 0 && this.currentExecutionRangeEnd > this.currentExecutionRangeStart) {
      const startLine = this.lineForOffset(this.currentExecutionRangeStart);
      const endLine = this.lineForOffset(Math.max(this.currentExecutionRangeStart, this.currentExecutionRangeEnd - 1));
      return this.executionViewport(startLine, endLine);
    }
    if (this.currentExecutionLine > 0) {
      const line = Math.min(this.currentExecutionLine, this.lineCount());
      return this.executionViewport(line, line);
    }
    return null;
  }

  private executionViewport(startLine: number, endLine: number): ExecutionViewport {
    const safeStart = Math.max(1, Math.min(startLine, this.lineCount()));
    const safeEnd = Math.max(safeStart, Math.min(endLine, this.lineCount()));
    return {
      startLine: safeStart,
      topY: (safeStart - 1) * this.editorLineHeight(),
      bottomY: safeEnd * this.editorLineHeight(),
    };
  }

  private lineForOffset(offset: number): number {
    const safeOffset = this.safeOffset(offset);
    let line = 1;
    for (let index = 0; index < safeOffset; index += 1) {
      if (this.source[index] === "\n") {
        line += 1;
      }
    }
    return line;
  }

  private lineCount(): number {
    if (this.source.length === 0) {
      return 1;
    }
    let count = 1;
    for (let index = 0; index < this.source.length; index += 1) {
      if (this.source[index] === "\n") {
        count += 1;
      }
    }
    return count;
  }

  private isExecutionViewportFullyVisible(viewport: ExecutionViewport): boolean {
    const bottom = this.requestedScrollY + this.visibleViewportHeight();
    return viewport.topY >= this.requestedScrollY && viewport.bottomY <= bottom;
  }

  private clampScrollY(scrollY: number): number {
    return clampNumber(scrollY, 0, this.maxScrollY());
  }

  private maxScrollY(): number {
    return Math.max(0, this.documentHeight() - this.visibleViewportHeight());
  }

  private documentHeight(): number {
    return Math.max(this.editorLineHeight(), this.lineCount() * this.editorLineHeight());
  }

  private visibleViewportHeight(): number {
    return this.editorLineHeight() * 16;
  }

  private editorLineHeight(): number {
    return this.editorFontSize * 1.5;
  }

  private safeOffset(offset: number): number {
    return clampNumber(Math.trunc(offset), 0, this.source.length);
  }
}

function MiniCCodeEditorComponent({
  value,
  initialText = "",
  sourceName = "untitled.mc",
  analysis = null,
  breakpoints = [],
  currentExecutionLine = 0,
  currentExecutionRange = null,
  readOnly = false,
  className = "",
  scrollContainerClassName = "",
  ariaLabel = "MiniC source editor",
  onTextChange,
  onBreakpointsChange,
  onSubmitRealtimeSource,
}: MiniCCodeEditorProps) {
  const controlled = value !== undefined;
  const [localText, setLocalText] = useState(value ?? initialText);
  const [suggestions, setSuggestions] = useState<readonly string[]>([]);
  const [selectedSuggestion, setSelectedSuggestion] = useState(0);
  const [scrollTop, setScrollTop] = useState(0);
  const [scrollLeft, setScrollLeft] = useState(0);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const model = useMemo(() => new MiniCCodeEditorModel(), []);
  const syntaxMapper = useMemo(() => new MiniCSyntaxTextStyleMapper(), []);
  const text = controlled ? value : localText;
  const diagnostics = analysis?.sourceText === text ? analysis.diagnostics : [];
  const lineHeight = model.fontSize() * 1.5;
  const editorLines = useMemo(
    () => renderLines(text, analysis, diagnostics, currentExecutionRange, syntaxMapper),
    [analysis, currentExecutionRange, diagnostics, syntaxMapper, text],
  );
  const breakpointSet = useMemo(() => new Set(breakpoints), [breakpoints]);

  useEffect(() => {
    model.setText(text);
    model.replaceBreakpoints(breakpoints);
    model.setCurrentExecutionLine(currentExecutionLine);
    model.setCurrentExecutionRange(currentExecutionRange);
    model.render(analysis);
  }, [analysis, breakpoints, currentExecutionLine, currentExecutionRange, model, text]);

  const publishText = (nextText: string): void => {
    if (!controlled) {
      setLocalText(nextText);
    }
    model.setText(nextText);
    onTextChange?.(nextText);
    onSubmitRealtimeSource?.(sourceName, nextText);
  };

  const applyEdit = (result: MiniCEditResult): void => {
    publishText(result.source);
    window.requestAnimationFrame(() => {
      textareaRef.current?.setSelectionRange(result.selectionStart, result.selectionEnd);
      updateCompletion(false);
    });
  };

  const updateCompletion = (force: boolean): void => {
    const textarea = textareaRef.current;
    if (!textarea) {
      setSuggestions([]);
      return;
    }
    model.setText(text);
    model.render(analysis);
    const next = model.completionSuggestions(textarea.selectionStart, force);
    setSuggestions(next);
    setSelectedSuggestion(0);
  };

  const applySuggestion = (suggestion: string): void => {
    const textarea = textareaRef.current;
    if (!textarea) {
      return;
    }
    const prefix = MiniCCodeEditorModel.prefixAtCaret(text, textarea.selectionStart);
    applyEdit(MiniCEditorTyping.replace(text, prefix.startOffset, prefix.endOffset, suggestion, prefix.startOffset + suggestion.length));
    setSuggestions([]);
  };

  const toggleBreakpoint = (line: number): void => {
    const next = new Set(breakpoints);
    if (next.has(line)) {
      next.delete(line);
    } else {
      next.add(line);
    }
    onBreakpointsChange?.([...next].sort((left, right) => left - right));
  };

  const onChange = (event: ChangeEvent<HTMLTextAreaElement>): void => {
    publishText(event.target.value);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>): void => {
    if (readOnly) {
      return;
    }
    if (suggestions.length > 0) {
      if (event.key === "ArrowDown") {
        event.preventDefault();
        setSelectedSuggestion((current) => Math.min(suggestions.length - 1, current + 1));
        return;
      }
      if (event.key === "ArrowUp") {
        event.preventDefault();
        setSelectedSuggestion((current) => Math.max(0, current - 1));
        return;
      }
      if (event.key === "Enter" || event.key === "Tab") {
        event.preventDefault();
        applySuggestion(suggestions[selectedSuggestion] ?? suggestions[0]);
        return;
      }
      if (event.key === "Escape") {
        event.preventDefault();
        setSuggestions([]);
        return;
      }
    }
    if (event.ctrlKey && event.key === " ") {
      event.preventDefault();
      updateCompletion(true);
      return;
    }
    const textarea = event.currentTarget;
    if (event.key === "Tab") {
      event.preventDefault();
      applyEdit(MiniCEditorTyping.replace(text, textarea.selectionStart, textarea.selectionEnd, MiniCEditorFormatter.TAB_TEXT, textarea.selectionStart + MiniCEditorFormatter.TAB_TEXT.length));
      return;
    }
    if (event.key === "Backspace") {
      event.preventDefault();
      applyEdit(MiniCEditorTyping.backspace(text, textarea.selectionStart, textarea.selectionEnd));
      return;
    }
    if (event.key === "Enter") {
      event.preventDefault();
      applyEdit(new MiniCEditorFormatter().insertNewlineWithIndentAt(text, textarea.selectionStart));
      return;
    }
    if (event.key.length === 1 && !event.ctrlKey && !event.altKey && !event.metaKey) {
      event.preventDefault();
      applyEdit(MiniCEditorTyping.type(text, textarea.selectionStart, textarea.selectionEnd, event.key));
    }
  };

  return (
    <section className={`code-editor ${className}`.trim()} data-java-source={miniCCodeEditorMirror.javaPath}>
      <div className={`source-editor-scroll ${scrollContainerClassName}`.trim()}>
        <div className="editor-gutter-column">
          <div className="editor-gutter-column-inner" style={{ transform: `translateY(${-scrollTop}px)` }}>
            {editorLines.map((lineModel) => {
              const line = lineModel.lineNumber;
              const active = breakpointSet.has(line);
              const current = currentExecutionLine === line;
              return (
                <div
                  className={`editor-gutter${current ? " current-execution" : ""}`}
                  key={line}
                  style={{ height: `${lineHeight}px`, minHeight: `${lineHeight}px`, maxHeight: `${lineHeight}px` }}
                >
                  <span className="execution-gutter">{current ? "▶" : ""}</span>
                  <button
                    aria-label={`${active ? "清除" : "设置"}第 ${line} 行断点`}
                    className={`breakpoint-gutter${active ? " active" : ""}`}
                    onClick={() => toggleBreakpoint(line)}
                    tabIndex={-1}
                    type="button"
                  >
                    ●
                  </button>
                  <span className="lineno">{line}</span>
                </div>
              );
            })}
          </div>
        </div>
        <div className="source-editor-viewport">
          <div
            aria-hidden="true"
            className="source-editor-render"
            style={{
              fontSize: `${model.fontSize()}px`,
              lineHeight: `${lineHeight}px`,
              transform: `translate(${-scrollLeft}px, ${-scrollTop}px)`,
            }}
          >
            {editorLines.map((lineModel) => (
              <div
                className={`source-editor-render-line${currentExecutionLine === lineModel.lineNumber ? " current-execution" : ""}`}
                key={`${lineModel.lineNumber}-${lineModel.startOffset}`}
                style={{ height: `${lineHeight}px`, minHeight: `${lineHeight}px`, maxHeight: `${lineHeight}px` }}
              >
                {lineModel.segments.map((segment, index) => (
                  <span className={segment.className} key={`${lineModel.lineNumber}-${index}`}>
                    {segment.text}
                  </span>
                ))}
              </div>
            ))}
          </div>
          <textarea
            aria-label={ariaLabel}
            className="source-editor source-editor-input"
            onChange={onChange}
            onClick={() => updateCompletion(false)}
            onKeyDown={onKeyDown}
            onScroll={(event) => {
              setScrollTop(event.currentTarget.scrollTop);
              setScrollLeft(event.currentTarget.scrollLeft);
            }}
            onSelect={() => updateCompletion(false)}
            readOnly={readOnly}
            ref={textareaRef}
            spellCheck={false}
            style={{ fontSize: `${model.fontSize()}px`, lineHeight: `${lineHeight}px` }}
            value={text}
            wrap="off"
          />
        </div>
      </div>
      {suggestions.length > 0 && (
        <ol className="completion-list">
          {suggestions.map((suggestion, index) => (
            <li className={`completion-item${index === selectedSuggestion ? " active" : ""}`} key={suggestion}>
              <button onClick={() => applySuggestion(suggestion)} type="button">
                {suggestion}
              </button>
            </li>
          ))}
        </ol>
      )}
      {diagnostics.length > 0 && (
        <div className="editor-diagnostic-details">
          {diagnostics.slice(0, 4).map((diagnostic) => {
            const position = MiniCCodeEditorModel.sourcePosition(text, diagnostic.startOffset);
            return (
              <p className="editor-diagnostic-detail" key={`${diagnostic.code}-${diagnostic.startOffset}-${diagnostic.endOffset}`}>
                第 {position.line} 行，第 {position.byteOffsetInLine} 列: {diagnostic.message}
              </p>
            );
          })}
        </div>
      )}
    </section>
  );
}

function renderLines(
  source: string,
  analysis: UiRealtimeAnalysisDto | null,
  diagnostics: readonly UiDiagnosticDto[],
  currentExecutionRange: UiSourceSpanDto | null,
  syntaxMapper: MiniCSyntaxTextStyleMapper,
): readonly EditorLine[] {
  const tokens = tokensForSource(source, analysis);
  return sourceLines(source).map((line) => ({
    ...line,
    segments: segmentsForLine(line, tokens, diagnostics, currentExecutionRange, syntaxMapper),
  }));
}

function sourceLines(source: string): readonly Omit<EditorLine, "segments">[] {
  const lines: Array<Omit<EditorLine, "segments">> = [];
  let start = 0;
  let lineNumber = 1;
  for (let index = 0; index <= source.length; index += 1) {
    if (index === source.length || source[index] === "\n") {
      lines.push({
        lineNumber,
        startOffset: start,
        endOffset: index,
        text: source.slice(start, index),
      });
      start = index + 1;
      lineNumber += 1;
    }
  }
  return lines.length > 0 ? lines : [{ lineNumber: 1, startOffset: 0, endOffset: 0, text: "" }];
}

function tokensForSource(source: string, analysis: UiRealtimeAnalysisDto | null): readonly EditorToken[] {
  if (analysis !== null && analysis.sourceText === source && analysis.tokens.length > 0) {
    return analysis.tokens
      .filter((token) => token.startOffset >= 0 && token.endOffset > token.startOffset)
      .map((token) => tokenFromAnalysis(token))
      .sort((left, right) => left.startOffset - right.startOffset);
  }
  return [];
}

function tokenFromAnalysis(token: UiLexerTokenVisualDto): EditorToken {
  return {
    kind: token.kind,
    startOffset: token.startOffset,
    endOffset: token.endOffset,
  };
}

function segmentsForLine(
  line: Omit<EditorLine, "segments">,
  tokens: readonly EditorToken[],
  diagnostics: readonly UiDiagnosticDto[],
  currentExecutionRange: UiSourceSpanDto | null,
  syntaxMapper: MiniCSyntaxTextStyleMapper,
): readonly EditorLineSegment[] {
  if (line.startOffset === line.endOffset) {
    return [{ text: "\u00a0", className: MiniCTextStyles.className(MiniCTextStyleRole.CODE_PLAIN) }];
  }
  const boundaries = new Set([line.startOffset, line.endOffset]);
  for (const token of tokens) {
    if (rangesOverlap(line.startOffset, line.endOffset, token.startOffset, token.endOffset)) {
      boundaries.add(clampNumber(token.startOffset, line.startOffset, line.endOffset));
      boundaries.add(clampNumber(token.endOffset, line.startOffset, line.endOffset));
    }
  }
  for (const diagnostic of diagnostics) {
    if (rangesOverlap(line.startOffset, line.endOffset, diagnostic.startOffset, diagnostic.endOffset)) {
      boundaries.add(clampNumber(diagnostic.startOffset, line.startOffset, line.endOffset));
      boundaries.add(clampNumber(diagnostic.endOffset, line.startOffset, line.endOffset));
    }
  }
  if (currentExecutionRange && rangesOverlap(line.startOffset, line.endOffset, currentExecutionRange.startOffset, currentExecutionRange.endOffset)) {
    boundaries.add(clampNumber(currentExecutionRange.startOffset, line.startOffset, line.endOffset));
    boundaries.add(clampNumber(currentExecutionRange.endOffset, line.startOffset, line.endOffset));
  }
  const sorted = [...boundaries].sort((left, right) => left - right);
  const segments: EditorLineSegment[] = [];
  for (let index = 0; index < sorted.length - 1; index += 1) {
    const start = sorted[index];
    const end = sorted[index + 1];
    if (end <= start) {
      continue;
    }
    const text = line.text.slice(start - line.startOffset, end - line.startOffset);
    segments.push({
      text,
      className: classesForRange(start, end, tokens, diagnostics, currentExecutionRange, syntaxMapper),
    });
  }
  return segments;
}

function classesForRange(
  start: number,
  end: number,
  tokens: readonly EditorToken[],
  diagnostics: readonly UiDiagnosticDto[],
  currentExecutionRange: UiSourceSpanDto | null,
  syntaxMapper: MiniCSyntaxTextStyleMapper,
): string {
  const token = tokens.find((candidate) => candidate.startOffset <= start && candidate.endOffset >= end);
  const diagnostic = diagnostics.some((candidate) => rangesOverlap(start, end, candidate.startOffset, candidate.endOffset));
  const baseClasses =
    token === undefined
      ? MiniCTextStyles.classes(MiniCTextStyleRole.CODE_PLAIN, ...(diagnostic ? [MiniCTextStyleState.DIAGNOSTIC] : []))
      : syntaxMapper.styleClassesFor(token.kind, diagnostic);
  const executionClasses =
    currentExecutionRange && rangesOverlap(start, end, currentExecutionRange.startOffset, currentExecutionRange.endOffset)
      ? MiniCTextStyles.stateClasses(MiniCTextStyleState.DEBUG_EXECUTION)
      : [];
  return [...baseClasses, ...executionClasses].join(" ");
}

function rangesOverlap(leftStart: number, leftEnd: number, rightStart: number, rightEnd: number): boolean {
  return Math.max(leftStart, rightStart) < Math.min(leftEnd, rightEnd);
}

function requireNumber(value: number | undefined, name: string): number {
  if (value === undefined) {
    throw new TypeError(`${name} must not be undefined`);
  }
  return value;
}

export const MiniCCodeEditor = Object.assign(MiniCCodeEditorComponent, {
  mirror: miniCCodeEditorMirror,
});

export default MiniCCodeEditor;
