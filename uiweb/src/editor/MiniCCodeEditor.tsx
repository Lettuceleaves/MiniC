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
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiDiagnosticDto, UiRealtimeAnalysisDto, UiSourceSpanDto } from "../translation/uiTypes";
import { clampNumber, sourcePosition } from "../translation/uiTypes";

export const miniCCodeEditorMirror = {
  "javaPath": "src/main/java/minic/uilocal/editor/MiniCCodeEditor.java",
  "webPath": "uiweb/src/editor/MiniCCodeEditor.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCCodeEditor",
  "kind": "component",
  "imports": [
    "javafx.application.Platform",
    "javafx.beans.value.ObservableValue",
    "javafx.geometry.Pos",
    "javafx.geometry.Bounds",
    "javafx.scene.Node",
    "javafx.scene.control.ListCell",
    "javafx.scene.control.ListView",
    "javafx.scene.control.Label",
    "javafx.scene.control.ScrollPane",
    "javafx.scene.input.KeyCode",
    "javafx.scene.input.KeyEvent",
    "javafx.scene.input.MouseButton",
    "javafx.scene.layout.HBox",
    "javafx.scene.layout.Pane",
    "javafx.scene.layout.StackPane",
    "javafx.scene.layout.VBox",
    "minic.color.ThemeRegistry",
    "javafx.scene.shape.Polyline",
    "minic.uilocal.control.MiniCTextViewportAdapter",
    "minic.uilocal.control.MiniCViewportAdapter",
    "minic.uilocal.text.MiniCSyntaxTextStyleMapper",
    "minic.uilocal.text.MiniCTextStyleRole",
    "minic.uilocal.text.MiniCTextStyleState",
    "minic.uilocal.text.MiniCTextStyles",
    "minic.uiapi.UiDiagnosticDto",
    "minic.uiapi.UiLexerTokenVisualDto",
    "minic.uiapi.UiRealtimeAnalysisDto",
    "minic.uiapi.UiSourceSpanDto",
    "org.fxmisc.flowless.VirtualizedScrollPane",
    "org.fxmisc.richtext.LineNumberFactory",
    "org.fxmisc.richtext.StyleClassedTextArea",
    "org.fxmisc.richtext.model.StyleSpans",
    "org.fxmisc.richtext.model.StyleSpansBuilder",
    "java.util.Collection",
    "java.util.Comparator",
    "java.util.LinkedHashSet",
    "java.util.List",
    "java.util.ArrayList",
    "java.util.Objects",
    "java.util.function.IntFunction",
    "java.util.Set"
  ],
  "fields": [
    {
      "name": "DEFAULT_EDITOR_FONT_SIZE",
      "signature": "private static final double DEFAULT_EDITOR_FONT_SIZE ="
    },
    {
      "name": "MIN_EDITOR_FONT_SIZE",
      "signature": "private static final double MIN_EDITOR_FONT_SIZE ="
    },
    {
      "name": "MAX_EDITOR_FONT_SIZE",
      "signature": "private static final double MAX_EDITOR_FONT_SIZE ="
    },
    {
      "name": "input",
      "signature": "private final StyleClassedTextArea input ="
    },
    {
      "name": "scrollPane",
      "signature": "private final VirtualizedScrollPane<StyleClassedTextArea> scrollPane ="
    },
    {
      "name": "lineNumberFactory",
      "signature": "private final IntFunction<Node> lineNumberFactory ="
    },
    {
      "name": "breakpointLines",
      "signature": "private final Set<Integer> breakpointLines ="
    },
    {
      "name": "diagnosticLayer",
      "signature": "private final Pane diagnosticLayer ="
    },
    {
      "name": "diagnosticDetails",
      "signature": "private final VBox diagnosticDetails ="
    },
    {
      "name": "completionList",
      "signature": "private final ListView<String> completionList ="
    },
    {
      "name": "viewportAdapter",
      "signature": "private final MiniCViewportAdapter viewportAdapter ="
    },
    {
      "name": "syntaxTextStyleMapper",
      "signature": "private final MiniCSyntaxTextStyleMapper syntaxTextStyleMapper ="
    },
    {
      "name": "formatter",
      "signature": "private final MiniCEditorFormatter formatter ="
    },
    {
      "name": "latestAnalysis",
      "signature": "private UiRealtimeAnalysisDto latestAnalysis;"
    },
    {
      "name": "latestDiagnostics",
      "signature": "private List<UiDiagnosticDto> latestDiagnostics ="
    },
    {
      "name": "breakpointChangeAction",
      "signature": "private Runnable breakpointChangeAction ="
    },
    {
      "name": "currentExecutionLine",
      "signature": "private int currentExecutionLine;"
    },
    {
      "name": "currentExecutionRangeStart",
      "signature": "private int currentExecutionRangeStart ="
    },
    {
      "name": "currentExecutionRangeEnd",
      "signature": "private int currentExecutionRangeEnd ="
    },
    {
      "name": "editorFontSize",
      "signature": "private double editorFontSize ="
    },
    {
      "name": "requestedScrollY",
      "signature": "private double requestedScrollY;"
    }
  ],
  "methods": [
    {
      "name": "textProperty",
      "signature": "textProperty()"
    },
    {
      "name": "setText",
      "signature": "setText(String text)"
    },
    {
      "name": "viewportAdapter",
      "signature": "viewportAdapter()"
    },
    {
      "name": "setScrollBarPolicies",
      "signature": "setScrollBarPolicies(ScrollPane.ScrollBarPolicy horizontalPolicy, ScrollPane.ScrollBarPolicy verticalPolicy)"
    },
    {
      "name": "addScrollContainerStyleClass",
      "signature": "addScrollContainerStyleClass(String styleClass)"
    },
    {
      "name": "getText",
      "signature": "getText()"
    },
    {
      "name": "getLength",
      "signature": "getLength()"
    },
    {
      "name": "selectRange",
      "signature": "selectRange(int start, int end)"
    },
    {
      "name": "breakpointLines",
      "signature": "breakpointLines()"
    },
    {
      "name": "setBreakpoint",
      "signature": "setBreakpoint(int line, boolean enabled)"
    },
    {
      "name": "replaceBreakpoints",
      "signature": "replaceBreakpoints(List<Integer> lines)"
    },
    {
      "name": "toggleBreakpoint",
      "signature": "toggleBreakpoint(int line)"
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
      "signature": "setCurrentExecutionRange(UiSourceSpanDto range)"
    },
    {
      "name": "setCurrentExecutionRange",
      "signature": "setCurrentExecutionRange(int startOffset, int endOffset)"
    },
    {
      "name": "zoomFontBy",
      "signature": "zoomFontBy(double delta)"
    },
    {
      "name": "scrollVerticalBy",
      "signature": "scrollVerticalBy(double pixels)"
    },
    {
      "name": "isCurrentExecutionFullyVisible",
      "signature": "isCurrentExecutionFullyVisible()"
    },
    {
      "name": "centerCurrentExecutionIfNeeded",
      "signature": "centerCurrentExecutionIfNeeded()"
    },
    {
      "name": "centerCurrentExecution",
      "signature": "centerCurrentExecution()"
    },
    {
      "name": "render",
      "signature": "render(UiRealtimeAnalysisDto analysis)"
    },
    {
      "name": "styleSpans",
      "signature": "styleSpans(String source, UiRealtimeAnalysisDto analysis)"
    },
    {
      "name": "paragraphGraphic",
      "signature": "paragraphGraphic(int paragraphIndex)"
    },
    {
      "name": "refreshParagraphGraphics",
      "signature": "refreshParagraphGraphics()"
    },
    {
      "name": "tokenStyles",
      "signature": "tokenStyles(String kind, boolean diagnostic)"
    },
    {
      "name": "addStyledRange",
      "signature": "addStyledRange(StyleSpansBuilder<Collection<String>> builder, String source, int start, int end, Collection<String> baseStyles)"
    },
    {
      "name": "withDebugExecutionRange",
      "signature": "withDebugExecutionRange(Collection<String> baseStyles)"
    },
    {
      "name": "clearCurrentExecutionRange",
      "signature": "clearCurrentExecutionRange()"
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
      "name": "adjustEditorFontSize",
      "signature": "adjustEditorFontSize(double delta)"
    },
    {
      "name": "estimatedScrollY",
      "signature": "estimatedScrollY()"
    },
    {
      "name": "scrollYToPixel",
      "signature": "scrollYToPixel(double scrollY)"
    },
    {
      "name": "clampScrollY",
      "signature": "clampScrollY(double scrollY)"
    },
    {
      "name": "maxScrollY",
      "signature": "maxScrollY()"
    },
    {
      "name": "documentHeight",
      "signature": "documentHeight()"
    },
    {
      "name": "visibleViewportHeight",
      "signature": "visibleViewportHeight()"
    },
    {
      "name": "isExecutionViewportFullyVisible",
      "signature": "isExecutionViewportFullyVisible(ExecutionViewport viewport)"
    },
    {
      "name": "currentExecutionViewport",
      "signature": "currentExecutionViewport()"
    },
    {
      "name": "executionViewport",
      "signature": "executionViewport(int startLine, int endLine)"
    },
    {
      "name": "lineForOffset",
      "signature": "lineForOffset(String source, int offset)"
    },
    {
      "name": "lineCount",
      "signature": "lineCount()"
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
      "name": "editorLineHeight",
      "signature": "editorLineHeight()"
    },
    {
      "name": "updateCompletion",
      "signature": "updateCompletion(boolean force)"
    },
    {
      "name": "configureCompletionList",
      "signature": "configureCompletionList()"
    },
    {
      "name": "updateItem",
      "signature": "updateItem(String item, boolean empty)"
    },
    {
      "name": "showCompletion",
      "signature": "showCompletion()"
    },
    {
      "name": "hideCompletion",
      "signature": "hideCompletion()"
    },
    {
      "name": "drawDiagnostics",
      "signature": "drawDiagnostics()"
    },
    {
      "name": "boundsForRange",
      "signature": "boundsForRange(String source, int start, int end)"
    },
    {
      "name": "addDiagnosticWave",
      "signature": "addDiagnosticWave(Bounds bounds)"
    },
    {
      "name": "isCompletionShowing",
      "signature": "isCompletionShowing()"
    },
    {
      "name": "layoutCompletionList",
      "signature": "layoutCompletionList()"
    },
    {
      "name": "updateDiagnosticDetails",
      "signature": "updateDiagnosticDetails()"
    },
    {
      "name": "diagnosticDetail",
      "signature": "diagnosticDetail(String source, UiDiagnosticDto diagnostic)"
    },
    {
      "name": "layoutDiagnosticDetails",
      "signature": "layoutDiagnosticDetails()"
    },
    {
      "name": "diagnosticDetailsHeight",
      "signature": "diagnosticDetailsHeight()"
    },
    {
      "name": "selectCompletionOffset",
      "signature": "selectCompletionOffset(int offset)"
    },
    {
      "name": "applySelectedCompletion",
      "signature": "applySelectedCompletion()"
    },
    {
      "name": "applyCompletion",
      "signature": "applyCompletion(String suggestion)"
    },
    {
      "name": "prefixAtCaret",
      "signature": "prefixAtCaret()"
    },
    {
      "name": "isIdentifierPart",
      "signature": "isIdentifierPart(char value)"
    },
    {
      "name": "overlapsDiagnostic",
      "signature": "overlapsDiagnostic(int start, int end, List<UiDiagnosticDto> diagnostics)"
    },
    {
      "name": "safeOffset",
      "signature": "safeOffset(String source, int offset)"
    },
    {
      "name": "sourcePosition",
      "signature": "sourcePosition(String source, int offset)"
    },
    {
      "name": "Prefix",
      "signature": "Prefix(String text, int startOffset, int endOffset)"
    },
    {
      "name": "SourcePosition",
      "signature": "SourcePosition(int line, int byteOffsetInLine)"
    },
    {
      "name": "ExecutionViewport",
      "signature": "ExecutionViewport(int startLine, double topY, double bottomY)"
    },
    {
      "name": "centerY",
      "signature": "centerY()"
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
  ariaLabel = "MiniC source editor",
  onTextChange,
  onBreakpointsChange,
  onSubmitRealtimeSource,
}: MiniCCodeEditorProps) {
  const controlled = value !== undefined;
  const [localText, setLocalText] = useState(value ?? initialText);
  const [suggestions, setSuggestions] = useState<readonly string[]>([]);
  const [selectedSuggestion, setSelectedSuggestion] = useState(0);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const model = useMemo(() => new MiniCCodeEditorModel(), []);
  const text = controlled ? value : localText;
  const lines = useMemo(() => text.split("\n"), [text]);
  const diagnostics = analysis?.sourceText === text ? analysis.diagnostics : [];

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
      <div className="source-editor-scroll">
        <div className="editor-gutter" aria-hidden="true">
          {lines.map((_, index) => {
            const line = index + 1;
            const active = breakpoints.includes(line);
            const current = currentExecutionLine === line;
            return (
              <button
                className={`breakpoint-gutter${active ? " active" : ""}${current ? " current-execution" : ""}`}
                key={line}
                onClick={() => toggleBreakpoint(line)}
                tabIndex={-1}
                type="button"
              >
                <span className="execution-gutter">{current ? ">" : ""}</span>
                <span>{active ? "●" : ""}</span>
                <span>{line}</span>
              </button>
            );
          })}
        </div>
        <textarea
          aria-label={ariaLabel}
          className="source-editor"
          onChange={onChange}
          onClick={() => updateCompletion(false)}
          onKeyDown={onKeyDown}
          onSelect={() => updateCompletion(false)}
          readOnly={readOnly}
          ref={textareaRef}
          spellCheck={false}
          style={{ fontSize: `${model.fontSize()}px` }}
          value={text}
        />
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
