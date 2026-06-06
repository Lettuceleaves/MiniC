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

interface MiniCLexToken {
  readonly kind: string;
  readonly startOffset: number;
  readonly endOffset: number;
}

interface MiniCLexResult {
  readonly tokens: readonly UiLexerTokenVisualDto[];
  readonly diagnostics: readonly UiDiagnosticDto[];
}

const KEYWORD_KINDS = new Map<string, string>([
  ["bool", "BOOL"],
  ["char", "CHAR"],
  ["int", "INT"],
  ["long", "LONG"],
  ["float", "FLOAT"],
  ["double", "DOUBLE"],
  ["extern", "EXTERN"],
  ["struct", "STRUCT"],
  ["return", "RETURN"],
  ["if", "IF"],
  ["else", "ELSE"],
  ["while", "WHILE"],
  ["do", "DO"],
  ["for", "FOR"],
  ["break", "BREAK"],
  ["continue", "CONTINUE"],
  ["switch", "SWITCH"],
  ["case", "CASE"],
  ["default", "DEFAULT"],
  ["sizeof", "SIZEOF"],
  ["true", "BOOL_LITERAL"],
  ["false", "BOOL_LITERAL"],
  ["NULL", "NULL_LITERAL"],
]);

const INTEGER_MAX = 2_147_483_647n;
const LONG_MAX = 9_223_372_036_854_775_807n;
const FLOAT_MAX = 3.4028235e38;

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
    const lexResult = lexMiniCSource(sourceName, sourceText);
    return MiniCRealtimeAnalyzer.realtimeResult(sourceName, sourceText, lexResult.diagnostics, lexResult.tokens, version);
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

function lexMiniCSource(sourceName: string, sourceText: string): MiniCLexResult {
  return new MiniCLexer(sourceName, sourceText).lex();
}

class MiniCLexer {
  private readonly tokens: MiniCLexToken[] = [];

  private readonly diagnostics: UiDiagnosticDto[] = [];

  private currentOffset = 0;

  constructor(
    private readonly sourceName: string,
    private readonly sourceText: string,
  ) {
  }

  lex(): MiniCLexResult {
    while (!this.isAtEnd()) {
      const startOffset = this.currentOffset;
      const character = this.advanceChar();
      switch (character) {
        case " ":
        case "\r":
        case "\t":
        case "\n":
          break;
        case "+":
          this.addToken(this.match("+") ? "PLUS_PLUS" : this.match("=") ? "PLUS_EQUAL" : "PLUS", startOffset);
          break;
        case "-":
          this.addToken(this.match(">") ? "ARROW" : this.match("-") ? "MINUS_MINUS" : this.match("=") ? "MINUS_EQUAL" : "MINUS", startOffset);
          break;
        case "*":
          this.addToken(this.match("=") ? "STAR_EQUAL" : "STAR", startOffset);
          break;
        case "&":
          this.addToken(this.match("&") ? "AMPERSAND_AMPERSAND" : this.match("=") ? "AMPERSAND_EQUAL" : "AMPERSAND", startOffset);
          break;
        case "/":
          if (this.match("/")) {
            this.skipLineComment();
          } else {
            this.addToken(this.match("=") ? "SLASH_EQUAL" : "SLASH", startOffset);
          }
          break;
        case "%":
          this.addToken(this.match("=") ? "PERCENT_EQUAL" : "PERCENT", startOffset);
          break;
        case "|":
          this.addToken(this.match("|") ? "PIPE_PIPE" : this.match("=") ? "PIPE_EQUAL" : "PIPE", startOffset);
          break;
        case "^":
          this.addToken(this.match("=") ? "CARET_EQUAL" : "CARET", startOffset);
          break;
        case "~":
          this.addToken("TILDE", startOffset);
          break;
        case "=":
          this.addToken(this.match("=") ? "EQUAL_EQUAL" : "EQUAL", startOffset);
          break;
        case "!":
          this.addToken(this.match("=") ? "BANG_EQUAL" : "BANG", startOffset);
          break;
        case "<":
          this.addToken(this.match("<") ? (this.match("=") ? "LESS_LESS_EQUAL" : "LESS_LESS") : this.match("=") ? "LESS_EQUAL" : "LESS", startOffset);
          break;
        case ">":
          this.addToken(this.match(">") ? (this.match("=") ? "GREATER_GREATER_EQUAL" : "GREATER_GREATER") : this.match("=") ? "GREATER_EQUAL" : "GREATER", startOffset);
          break;
        case "(":
          this.addToken("LEFT_PAREN", startOffset);
          break;
        case ")":
          this.addToken("RIGHT_PAREN", startOffset);
          break;
        case "{":
          this.addToken("LEFT_BRACE", startOffset);
          break;
        case "}":
          this.addToken("RIGHT_BRACE", startOffset);
          break;
        case "[":
          this.addToken("LEFT_BRACKET", startOffset);
          break;
        case "]":
          this.addToken("RIGHT_BRACKET", startOffset);
          break;
        case ";":
          this.addToken("SEMICOLON", startOffset);
          break;
        case ",":
          this.addToken("COMMA", startOffset);
          break;
        case ".":
          this.lexDotOrEllipsis(startOffset);
          break;
        case "?":
          this.addToken("QUESTION", startOffset);
          break;
        case ":":
          this.addToken("COLON", startOffset);
          break;
        case "\"":
          this.lexStringLiteral(startOffset);
          break;
        case "'":
          this.lexCharLiteral(startOffset);
          break;
        default:
          if (this.isIdentifierStart(character)) {
            this.lexIdentifier(startOffset);
          } else if (this.isAsciiDigit(character)) {
            this.lexIntegerLiteral(startOffset);
          } else {
            this.addInvalidCharacterDiagnostic(startOffset);
          }
      }
    }
    this.tokens.push({ kind: "EOF", startOffset: this.currentOffset, endOffset: this.currentOffset });
    return {
      diagnostics: [...this.diagnostics],
      tokens: this.tokens.map((token) => this.tokenDto(token)),
    };
  }

  private isAtEnd(): boolean {
    return this.currentOffset >= this.sourceText.length;
  }

  private advanceChar(): string {
    const character = this.sourceText[this.currentOffset] ?? "";
    this.currentOffset += 1;
    return character;
  }

  private match(expected: string): boolean {
    if (this.isAtEnd() || this.sourceText[this.currentOffset] !== expected) {
      return false;
    }
    this.currentOffset += 1;
    return true;
  }

  private skipLineComment(): void {
    while (!this.isAtEnd() && this.sourceText[this.currentOffset] !== "\n") {
      this.currentOffset += 1;
    }
  }

  private lexIdentifier(startOffset: number): void {
    while (!this.isAtEnd() && this.isIdentifierPart(this.sourceText[this.currentOffset] ?? "")) {
      this.currentOffset += 1;
    }
    const lexeme = this.sourceText.slice(startOffset, this.currentOffset);
    this.addToken(KEYWORD_KINDS.get(lexeme) ?? "IDENTIFIER", startOffset);
  }

  private lexIntegerLiteral(startOffset: number): void {
    while (!this.isAtEnd() && this.isAsciiDigit(this.sourceText[this.currentOffset] ?? "")) {
      this.currentOffset += 1;
    }
    let floating = false;
    if (!this.isAtEnd() && this.sourceText[this.currentOffset] === "." && this.hasNextAsciiDigit()) {
      floating = true;
      this.currentOffset += 1;
      while (!this.isAtEnd() && this.isAsciiDigit(this.sourceText[this.currentOffset] ?? "")) {
        this.currentOffset += 1;
      }
    }
    let longLiteral = false;
    let floatLiteral = false;
    if (!this.isAtEnd()) {
      const suffix = this.sourceText[this.currentOffset];
      if (!floating && (suffix === "l" || suffix === "L")) {
        longLiteral = true;
        this.currentOffset += 1;
      } else if (floating && (suffix === "f" || suffix === "F")) {
        floatLiteral = true;
        this.currentOffset += 1;
      }
    }
    const lexeme = this.sourceText.slice(startOffset, this.currentOffset);
    if (floating) {
      const valueLexeme = floatLiteral ? lexeme.slice(0, -1) : lexeme;
      const literalValue = Number.parseFloat(valueLexeme);
      if (!Number.isFinite(literalValue) || (floatLiteral && Math.abs(literalValue) > FLOAT_MAX)) {
        this.addNumericOverflowDiagnostic(startOffset, this.currentOffset, "浮点字面量超出范围");
        return;
      }
      this.addToken(floatLiteral ? "FLOAT_LITERAL" : "DOUBLE_LITERAL", startOffset);
      return;
    }
    if (longLiteral) {
      const valueLexeme = lexeme.slice(0, -1);
      if (!this.decimalFits(valueLexeme, LONG_MAX)) {
        this.addNumericOverflowDiagnostic(startOffset, this.currentOffset, "long 字面量超出范围");
        return;
      }
      this.addToken("LONG_LITERAL", startOffset);
      return;
    }
    if (!this.decimalFits(lexeme, INTEGER_MAX)) {
      this.addNumericOverflowDiagnostic(startOffset, this.currentOffset, "整数字面量超出范围");
      return;
    }
    this.addToken("INTEGER_LITERAL", startOffset);
  }

  private lexDotOrEllipsis(startOffset: number): void {
    if (this.match(".")) {
      if (this.match(".")) {
        this.addToken("ELLIPSIS", startOffset);
      } else {
        this.addDiagnostic("LEX001", "ERROR", "不完整的省略号：..", startOffset, this.currentOffset);
      }
      return;
    }
    this.addToken("DOT", startOffset);
  }

  private hasNextAsciiDigit(): boolean {
    const nextOffset = this.currentOffset + 1;
    return nextOffset < this.sourceText.length && this.isAsciiDigit(this.sourceText[nextOffset] ?? "");
  }

  private lexStringLiteral(startOffset: number): void {
    while (!this.isAtEnd() && this.sourceText[this.currentOffset] !== "\"") {
      const character = this.advanceChar();
      if (character === "\n" || character === "\r") {
        this.addDiagnostic("LEX002", "ERROR", "字符串字面量不能跨行", startOffset, this.currentOffset);
        return;
      }
      if (character === "\\") {
        if (this.isAtEnd()) {
          this.addUnterminatedStringDiagnostic(startOffset);
          return;
        }
        this.lexEscape(startOffset);
      }
    }
    if (this.isAtEnd()) {
      this.addUnterminatedStringDiagnostic(startOffset);
      return;
    }
    this.currentOffset += 1;
    this.addToken("STRING_LITERAL", startOffset);
  }

  private lexEscape(startOffset: number): void {
    const escaped = this.advanceChar();
    if (escaped === "n" || escaped === "r" || escaped === "t" || escaped === "\\" || escaped === "\"" || escaped === "0") {
      return;
    }
    this.addDiagnostic("LEX003", "ERROR", `不支持的字符串转义：${escaped}`, startOffset, this.currentOffset);
  }

  private lexCharLiteral(startOffset: number): void {
    if (this.isAtEnd()) {
      this.addUnterminatedCharDiagnostic(startOffset);
      return;
    }
    let value = this.advanceChar();
    if (value === "\n" || value === "\r") {
      this.addDiagnostic("LEX004", "ERROR", "字符字面量不能跨行", startOffset, this.currentOffset);
      return;
    }
    if (value === "\\") {
      if (this.isAtEnd()) {
        this.addUnterminatedCharDiagnostic(startOffset);
        return;
      }
      value = this.advanceChar();
      if (value !== "n" && value !== "r" && value !== "t" && value !== "\\" && value !== "\"" && value !== "0") {
        this.addDiagnostic("LEX003", "ERROR", `不支持的字符串转义：${value}`, startOffset, this.currentOffset);
      }
    }
    if (this.isAtEnd() || this.advanceChar() !== "'") {
      this.addDiagnostic("LEX004", "ERROR", "字符字面量必须只包含一个字符", startOffset, this.currentOffset);
      while (
        !this.isAtEnd() &&
        this.sourceText[this.currentOffset] !== "'" &&
        this.sourceText[this.currentOffset] !== "\n" &&
        this.sourceText[this.currentOffset] !== "\r"
      ) {
        this.currentOffset += 1;
      }
      this.match("'");
      return;
    }
    this.addToken("CHAR_LITERAL", startOffset);
  }

  private addUnterminatedStringDiagnostic(startOffset: number): void {
    this.addDiagnostic("LEX002", "ERROR", "字符串字面量缺少结束引号", startOffset, this.currentOffset);
  }

  private addUnterminatedCharDiagnostic(startOffset: number): void {
    this.addDiagnostic("LEX004", "ERROR", "字符字面量缺少结束引号", startOffset, this.currentOffset);
  }

  private isIdentifierStart(character: string): boolean {
    return character === "_" || this.isAsciiLetter(character);
  }

  private isIdentifierPart(character: string): boolean {
    return this.isIdentifierStart(character) || this.isAsciiDigit(character);
  }

  private isAsciiLetter(character: string): boolean {
    return (character >= "a" && character <= "z") || (character >= "A" && character <= "Z");
  }

  private isAsciiDigit(character: string): boolean {
    return character >= "0" && character <= "9";
  }

  private addInvalidCharacterDiagnostic(startOffset: number): void {
    this.addDiagnostic("LEX001", "ERROR", `非法字符：${this.sourceText[startOffset] ?? ""}`, startOffset, this.currentOffset);
  }

  private addNumericOverflowDiagnostic(startOffset: number, endOffset: number, message: string): void {
    this.addDiagnostic("LEX005", "ERROR", message, startOffset, endOffset);
  }

  private addToken(kind: string, startOffset: number): void {
    this.tokens.push({
      kind,
      startOffset,
      endOffset: this.currentOffset,
    });
  }

  private addDiagnostic(code: string, severity: string, message: string, startOffset: number, endOffset: number): void {
    this.diagnostics.push({
      code,
      severity,
      message,
      sourceName: this.sourceName,
      startOffset,
      endOffset,
    });
  }

  private tokenDto(token: MiniCLexToken): UiLexerTokenVisualDto {
    const range = sourceSpan(this.sourceName, this.sourceText, token.startOffset, token.endOffset);
    return {
      kind: token.kind,
      text: this.sourceText.slice(token.startOffset, token.endOffset),
      range,
      startOffset: range.startOffset,
      endOffset: range.endOffset,
      startLine: range.startLine,
      startColumn: range.startColumn,
      endLine: range.endLine,
      endColumn: range.endColumn,
      active: false,
    };
  }

  private decimalFits(lexeme: string, maxValue: bigint): boolean {
    try {
      return BigInt(lexeme) <= maxValue;
    } catch {
      return false;
    }
  }
}

export default MiniCRealtimeAnalyzer;
