import { MiniCCompletionSuggester } from "./MiniCCompletionSuggester";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type {
  UiDiagnosticDto,
  UiLexerTokenVisualDto,
  UiRealtimeAnalysisDto,
  UiSourceSpanDto,
} from "../translation/uiTypes";
import { requireValue, sourceSpan } from "../translation/uiTypes";

export const miniCRealtimeAnalyzerMirror = {
  "javaPath": "src/main/java/minic/uilocal/editor/MiniCRealtimeAnalyzer.java",
  "webPath": "uiweb/src/editor/MiniCRealtimeAnalyzer.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCRealtimeAnalyzer",
  "kind": "class",
  "imports": [
    "javafx.application.Platform",
    "minic.compiler.lexer.LexResult",
    "minic.compiler.lexer.Lexer",
    "minic.compiler.parser.ParseResult",
    "minic.compiler.parser.Parser",
    "minic.compiler.preprocess.MiniCPreprocessor",
    "minic.compiler.preprocess.PreprocessResult",
    "minic.compiler.semantic.SemanticAnalyzer",
    "minic.compiler.semantic.SemanticResult",
    "minic.diagnostics.Diagnostic",
    "minic.source.SourceFile",
    "minic.source.SourceRange",
    "minic.uiapi.UiDiagnosticDto",
    "minic.uiapi.UiLexerTokenVisualDto",
    "minic.uiapi.UiRealtimeAnalysisDto",
    "minic.uiapi.UiSourceSpanDto",
    "java.util.ArrayList",
    "java.util.List",
    "java.util.Objects",
    "java.util.concurrent.BlockingQueue",
    "java.util.concurrent.LinkedBlockingQueue"
  ],
  "fields": [
    {
      "name": "queue",
      "signature": "private final BlockingQueue<Request> queue ="
    },
    {
      "name": "resultSink",
      "signature": "private final ResultSink resultSink;"
    },
    {
      "name": "running",
      "signature": "private volatile boolean running ="
    },
    {
      "name": "worker",
      "signature": "private Thread worker;"
    },
    {
      "name": "nextVersion",
      "signature": "private long nextVersion;"
    }
  ],
  "methods": [
    {
      "name": "submit",
      "signature": "submit(String sourceName, String sourceText)"
    },
    {
      "name": "close",
      "signature": "close()"
    },
    {
      "name": "ensureStarted",
      "signature": "ensureStarted()"
    },
    {
      "name": "runLoop",
      "signature": "runLoop()"
    },
    {
      "name": "drainLatest",
      "signature": "drainLatest(Request request)"
    },
    {
      "name": "realtimeResult",
      "signature": "realtimeResult(String sourceName, String sourceText, List<Diagnostic> diagnostics, List<UiLexerTokenVisualDto> tokens, long version)"
    },
    {
      "name": "diagnosticDto",
      "signature": "diagnosticDto(Diagnostic diagnostic)"
    },
    {
      "name": "mapDiagnostics",
      "signature": "mapDiagnostics(List<Diagnostic> diagnostics, SourceFile originalSource, PreprocessResult preprocessResult)"
    },
    {
      "name": "mapDiagnostic",
      "signature": "mapDiagnostic(Diagnostic diagnostic, SourceFile originalSource, int[] sourceMap)"
    },
    {
      "name": "mapRange",
      "signature": "mapRange(SourceRange range, SourceFile originalSource, int[] sourceMap)"
    },
    {
      "name": "mappedOffset",
      "signature": "mappedOffset(int[] sourceMap, int offset)"
    },
    {
      "name": "Request",
      "signature": "Request(String sourceName, String sourceText, long version)"
    }
  ]
} as const satisfies JavaMirrorFile;

export type MiniCRealtimeResultSink = (result: UiRealtimeAnalysisDto) => void;

interface Request {
  readonly sourceName: string;
  readonly sourceText: string;
  readonly version: number;
}

interface BracketFrame {
  readonly char: string;
  readonly offset: number;
}

const multiCharacterOperators = Object.freeze([
  "==",
  "!=",
  "<=",
  ">=",
  "&&",
  "||",
  "++",
  "--",
  "+=",
  "-=",
  "*=",
  "/=",
  "%=",
  "->",
]);

const singleCharacterOperators = new Set(["+", "-", "*", "/", "%", "<", ">", "=", "!", "&", "|"]);
const punctuation = new Set(["(", ")", "[", "]", "{", "}", ",", ";", ".", ":"]);
const openingBrackets = new Map<string, string>([
  ["(", ")"],
  ["[", "]"],
  ["{", "}"],
]);
const closingBrackets = new Map<string, string>([
  [")", "("],
  ["]", "["],
  ["}", "{"],
]);

export class MiniCRealtimeAnalyzer {
  static readonly mirror = miniCRealtimeAnalyzerMirror;

  readonly mirror = miniCRealtimeAnalyzerMirror;

  private readonly resultSink: MiniCRealtimeResultSink;

  private running = true;

  private nextVersion = 0;

  private pendingRequest: Request | null = null;

  private scheduledHandle: number | null = null;

  constructor(resultSink: MiniCRealtimeResultSink) {
    this.resultSink = requireValue(resultSink, "resultSink");
  }

  submit(sourceName: string, sourceText: string): void {
    if (!this.running) {
      return;
    }
    this.pendingRequest = {
      sourceName: requireValue(sourceName, "sourceName"),
      sourceText: requireValue(sourceText, "sourceText"),
      version: this.nextVersion + 1,
    };
    this.nextVersion += 1;
    this.ensureStarted();
  }

  close(): void {
    this.running = false;
    if (this.scheduledHandle !== null) {
      window.clearTimeout(this.scheduledHandle);
      this.scheduledHandle = null;
    }
    this.pendingRequest = null;
  }

  ensureStarted(): void {
    if (this.scheduledHandle !== null) {
      return;
    }
    this.scheduledHandle = window.setTimeout(() => this.runLoop(), 0);
  }

  runLoop(): void {
    this.scheduledHandle = null;
    if (!this.running || !this.pendingRequest) {
      return;
    }
    const request = this.drainLatest(this.pendingRequest);
    this.pendingRequest = null;
    this.resultSink(MiniCRealtimeAnalyzer.analyzeNow(request.sourceName, request.sourceText, request.version));
  }

  drainLatest(request: Request): Request {
    return this.pendingRequest ?? request;
  }

  static analyzeNow(sourceName: string, sourceText: string, version: number): UiRealtimeAnalysisDto {
    const tokens: UiLexerTokenVisualDto[] = [];
    const diagnostics: UiDiagnosticDto[] = [];
    const stack: BracketFrame[] = [];
    let offset = 0;

    const addToken = (kind: string, start: number, end: number): void => {
      const range = sourceSpan(sourceName, sourceText, start, end);
      tokens.push({
        kind,
        text: sourceText.slice(start, end),
        range,
        startOffset: range.startOffset,
        endOffset: range.endOffset,
        startLine: range.startLine,
        startColumn: range.startColumn,
        endLine: range.endLine,
        endColumn: range.endColumn,
        active: false,
      });
    };

    const addDiagnostic = (code: string, message: string, start: number, end: number): void => {
      diagnostics.push({
        code,
        severity: "ERROR",
        message,
        sourceName,
        startOffset: Math.max(0, Math.min(start, sourceText.length)),
        endOffset: Math.max(Math.max(0, Math.min(start, sourceText.length)) + 1, Math.min(Math.max(end, start + 1), sourceText.length)),
      });
    };

    while (offset < sourceText.length) {
      const value = sourceText[offset];
      if (/\s/u.test(value)) {
        offset += 1;
        continue;
      }

      if (isIdentifierStart(value)) {
        const start = offset;
        offset += 1;
        while (offset < sourceText.length && isIdentifierPart(sourceText[offset])) {
          offset += 1;
        }
        const text = sourceText.slice(start, offset);
        addToken(MiniCCompletionSuggester.KEYWORDS.includes(text) ? "KEYWORD" : "IDENTIFIER", start, offset);
        continue;
      }

      if (/[0-9]/u.test(value)) {
        const start = offset;
        offset += 1;
        while (offset < sourceText.length && /[0-9A-Za-z_.]/u.test(sourceText[offset])) {
          offset += 1;
        }
        addToken("NUMBER", start, offset);
        continue;
      }

      if (value === "/" && sourceText[offset + 1] === "/") {
        const start = offset;
        offset += 2;
        while (offset < sourceText.length && sourceText[offset] !== "\n") {
          offset += 1;
        }
        addToken("COMMENT", start, offset);
        continue;
      }

      if (value === "/" && sourceText[offset + 1] === "*") {
        const start = offset;
        offset += 2;
        while (offset + 1 < sourceText.length && !(sourceText[offset] === "*" && sourceText[offset + 1] === "/")) {
          offset += 1;
        }
        if (offset + 1 >= sourceText.length) {
          addToken("COMMENT", start, sourceText.length);
          addDiagnostic("MCUI001", "块注释没有闭合。", start, sourceText.length);
          offset = sourceText.length;
        } else {
          offset += 2;
          addToken("COMMENT", start, offset);
        }
        continue;
      }

      if (value === "\"" || value === "'") {
        const quote = value;
        const start = offset;
        offset += 1;
        let closed = false;
        while (offset < sourceText.length) {
          const current = sourceText[offset];
          if (current === "\\") {
            offset += 2;
            continue;
          }
          if (current === quote) {
            offset += 1;
            closed = true;
            break;
          }
          if (current === "\n") {
            break;
          }
          offset += 1;
        }
        addToken(quote === "\"" ? "STRING" : "CHAR", start, offset);
        if (!closed) {
          addDiagnostic("MCUI002", quote === "\"" ? "字符串字面量没有闭合。" : "字符字面量没有闭合。", start, offset);
        }
        continue;
      }

      const twoCharacterOperator = sourceText.slice(offset, offset + 2);
      if (multiCharacterOperators.includes(twoCharacterOperator)) {
        addToken("OPERATOR", offset, offset + 2);
        offset += 2;
        continue;
      }

      if (punctuation.has(value)) {
        addToken("PUNCTUATION", offset, offset + 1);
        if (openingBrackets.has(value)) {
          stack.push({ char: value, offset });
        } else {
          const expectedOpening = closingBrackets.get(value);
          if (expectedOpening) {
            const top = stack[stack.length - 1];
            if (top?.char === expectedOpening) {
              stack.pop();
            } else {
              addDiagnostic("MCUI003", `多余的闭合符号 ${value}。`, offset, offset + 1);
            }
          }
        }
        offset += 1;
        continue;
      }

      if (singleCharacterOperators.has(value)) {
        addToken("OPERATOR", offset, offset + 1);
        offset += 1;
        continue;
      }

      addToken("UNKNOWN", offset, offset + 1);
      addDiagnostic("MCUI004", `MiniC 编辑器暂不识别字符 ${value}。`, offset, offset + 1);
      offset += 1;
    }

    for (const frame of stack) {
      const expected = openingBrackets.get(frame.char) ?? "";
      addDiagnostic("MCUI005", `缺少闭合符号 ${expected}。`, frame.offset, frame.offset + 1);
    }

    return MiniCRealtimeAnalyzer.realtimeResult(sourceName, sourceText, diagnostics, tokens, version);
  }

  static realtimeResult(
    sourceName: string,
    sourceText: string,
    diagnostics: readonly UiDiagnosticDto[],
    tokens: readonly UiLexerTokenVisualDto[],
    version: number,
  ): UiRealtimeAnalysisDto {
    return {
      sourceName,
      sourceText,
      diagnostics: [...diagnostics],
      tokens: [...tokens],
      version,
    };
  }

  static diagnosticDto(diagnostic: UiDiagnosticDto): UiDiagnosticDto {
    return { ...diagnostic };
  }

  static mapRange(range: UiSourceSpanDto): UiSourceSpanDto {
    return { ...range };
  }

  static mappedOffset(sourceMap: readonly number[], offset: number): number {
    if (sourceMap.length === 0) {
      return -1;
    }
    const index = Math.max(0, Math.min(Math.trunc(offset), sourceMap.length - 1));
    return sourceMap[index] ?? -1;
  }

  summary(): string {
    return `MiniCRealtimeAnalyzer: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

function isIdentifierStart(value: string): boolean {
  return /[A-Za-z_]/u.test(value);
}

function isIdentifierPart(value: string): boolean {
  return /[A-Za-z0-9_]/u.test(value);
}

export default MiniCRealtimeAnalyzer;
