import type { JavaMirrorFile } from "../translation/javaMirror";
import type {
  UiAstNodeVisualDto,
  UiIrLineVisualDto,
  UiLexerTokenVisualDto,
  UiSourceSpanDto,
} from "../translation/uiapi";
import type { MiniCAssemblyTextLine } from "./MiniCAssemblyTextLine";

export const miniCVisualExplanationFormatterMirror = {
  "javaPath": "src/main/java/minic/uilocal/visual/MiniCVisualExplanationFormatter.java",
  "webPath": "uiweb/src/visual/MiniCVisualExplanationFormatter.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCVisualExplanationFormatter",
  "kind": "class",
  "imports": [
    "java.util.LinkedHashMap",
    "java.util.Map",
    "java.util.function.Function",
    "minic.uiapi.ExplanationTemplates",
    "minic.uiapi.UiAstNodeVisualDto",
    "minic.uiapi.UiIrLineVisualDto",
    "minic.uiapi.UiLexerTokenVisualDto",
    "minic.uiapi.UiSourceSpanDto"
  ],
  "fields": [
    {
      "name": "sourceSnippetProvider",
      "signature": "private final Function<UiSourceSpanDto,String>sourceSnippetProvider"
    }
  ],
  "methods": [
    {
      "name": "assemblyVariables",
      "signature": "assemblyVariables(MiniCAssemblyTextLine line)"
    },
    {
      "name": "astVariables",
      "signature": "astVariables(UiAstNodeVisualDto node)"
    },
    {
      "name": "irVariables",
      "signature": "irVariables(UiIrLineVisualDto line)"
    },
    {
      "name": "isControlKeyword",
      "signature": "isControlKeyword(String kind)"
    },
    {
      "name": "isTypeKeyword",
      "signature": "isTypeKeyword(String kind)"
    },
    {
      "name": "rangeValue",
      "signature": "rangeValue(UiSourceSpanDto range)"
    },
    {
      "name": "tokenRole",
      "signature": "tokenRole(String kind,Map<String,String>variables)"
    },
    {
      "name": "tokenVariables",
      "signature": "tokenVariables(UiLexerTokenVisualDto token)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCVisualExplanationFormatter {
  static readonly mirror = miniCVisualExplanationFormatterMirror;

  readonly mirror = miniCVisualExplanationFormatterMirror;

  constructor(private readonly sourceSnippetProvider: (range: UiSourceSpanDto | null) => string = () => "") {}

  explainToken(token: UiLexerTokenVisualDto): string {
    const variables = this.tokenVariables(token);
    return this.explanation("lexer", this.tokenRole(token.kind, variables), variables);
  }

  explainAstNode(node: UiAstNodeVisualDto): string {
    const variables = this.astVariables(node);
    return this.explanation("parser", `AST 节点 ${this.blankValue(node.kind)} 表示 ${this.blankValue(node.label)}。`, variables);
  }

  explainIrLine(line: UiIrLineVisualDto): string {
    const variables = this.irVariables(line);
    const lower = line.text.toLowerCase().trim();
    let role = "IR 指令记录当前中间表示步骤。";
    if (lower.includes("call")) role = "IR 调用指令连接函数调用与参数传递。";
    else if (lower.includes("ret") || lower.startsWith("return")) role = "IR 返回指令描述函数结果。";
    else if (lower.includes("br") || lower.includes("jump")) role = "IR 分支指令描述控制流跳转。";
    else if (lower.includes("cmp") || lower.includes("<") || lower.includes(">") || lower.includes("==")) role = "IR 比较指令产生条件判断结果。";
    else if (lower.includes("store") || lower.includes("=")) role = "IR 存储指令把值写入目标位置。";
    else if (lower.includes("load")) role = "IR 读取指令从源位置取值。";
    return this.explanation("ir", role, variables);
  }

  explainAssemblyLine(line: MiniCAssemblyTextLine): string {
    const variables = this.assemblyVariables(line);
    const text = line.text.trim();
    const lower = text.toLowerCase();
    let role = "汇编行对应代码生成阶段的机器级动作。";
    if (line.kind === "LABEL" || text.endsWith(":")) role = "标签标记可跳转的位置。";
    else if (lower.startsWith("mov")) role = "mov 指令在寄存器或内存之间移动数据。";
    else if (lower.startsWith("add") || lower.startsWith("sub") || lower.startsWith("imul")) role = "算术指令执行数值计算。";
    else if (lower.startsWith("cmp") || lower.startsWith("test")) role = "比较指令设置后续跳转需要的条件标志。";
    else if (lower.startsWith("j")) role = "跳转指令根据控制流移动执行位置。";
    else if (lower.startsWith("call")) role = "call 指令进入被调用函数。";
    else if (lower.startsWith("ret")) role = "ret 指令返回调用点。";
    else if (lower.startsWith("push") || lower.startsWith("pop")) role = "栈指令维护调用栈数据。";
    return this.explanation("codegen", role, variables);
  }

  rangeLine(range: UiSourceSpanDto | null): string {
    return `源码范围: ${this.rangeValue(range)}`;
  }

  tokenRole(kind: string, variables: ReadonlyMap<string, string> = new Map()): string {
    if (this.isTypeKeyword(kind)) {
      return "类型关键字声明值或对象的类型。";
    }
    if (this.isControlKeyword(kind)) {
      return "控制流关键字改变语句执行路径。";
    }
    if (kind === "EXTERN") {
      return "extern 声明外部符号。";
    }
    switch (kind) {
      case "IDENTIFIER":
        return `标识符 ${this.blankValue(variables.get("text"))} 引用变量、函数或类型名。`;
      case "INTEGER_LITERAL":
      case "LONG_LITERAL":
      case "FLOAT_LITERAL":
      case "DOUBLE_LITERAL":
      case "CHAR_LITERAL":
      case "BOOL_LITERAL":
      case "NULL_LITERAL":
        return "字面量直接提供源代码中的常量值。";
      case "STRING_LITERAL":
        return "字符串字面量提供字符序列常量。";
      case "PLUS":
      case "MINUS":
      case "STAR":
      case "SLASH":
      case "PERCENT":
      case "EQUAL":
      case "PLUS_EQUAL":
      case "MINUS_EQUAL":
      case "PLUS_PLUS":
      case "MINUS_MINUS":
      case "EQUAL_EQUAL":
      case "BANG_EQUAL":
      case "LESS":
      case "LESS_EQUAL":
      case "GREATER":
      case "GREATER_EQUAL":
      case "AMPERSAND":
      case "BANG":
      case "DOT":
        return "运算符连接表达式中的操作数并产生计算或比较动作。";
      case "LEFT_PAREN":
      case "RIGHT_PAREN":
      case "LEFT_BRACE":
      case "RIGHT_BRACE":
      case "LEFT_BRACKET":
      case "RIGHT_BRACKET":
      case "COMMA":
      case "SEMICOLON":
        return "分隔符划分语法结构的边界。";
      case "EOF":
        return "EOF 标记输入结束。";
      default:
        return "词法单元参与后续语法分析。";
    }
  }

  isTypeKeyword(kind: string): boolean {
    return ["VOID", "BOOL", "CHAR", "INT", "LONG", "FLOAT", "DOUBLE", "STRUCT"].includes(kind);
  }

  isControlKeyword(kind: string): boolean {
    return ["RETURN", "IF", "ELSE", "WHILE", "FOR", "BREAK", "CONTINUE"].includes(kind);
  }

  displayTokenText(token: UiLexerTokenVisualDto): string {
    return token.text === "" ? "<EOF>" : token.text;
  }

  tokenVariables(token: UiLexerTokenVisualDto): Map<string, string> {
    const variables = new Map<string, string>();
    variables.set("kind", token.kind);
    variables.set("text", this.displayTokenText(token));
    variables.set("source", this.sourceSnippetProvider(token.range));
    variables.set("range", this.rangeValue(token.range));
    variables.set("active", this.yesNo(token.active));
    this.addRangeVariables(variables, token.range);
    return variables;
  }

  astVariables(node: UiAstNodeVisualDto): Map<string, string> {
    const variables = new Map<string, string>();
    variables.set("kind", node.kind);
    variables.set("label", node.label);
    variables.set("id", node.id);
    variables.set("source", this.sourceSnippetProvider(node.range));
    variables.set("range", this.rangeValue(node.range));
    variables.set("childCount", String(node.children.length));
    variables.set("active", this.yesNo(node.active));
    return variables;
  }

  irVariables(line: UiIrLineVisualDto): Map<string, string> {
    const variables = new Map<string, string>();
    variables.set("lineNumber", String(line.lineNumber));
    variables.set("text", line.text);
    variables.set("source", this.sourceSnippetProvider(line.range));
    variables.set("range", this.rangeValue(line.range));
    variables.set("active", this.yesNo(line.active));
    return variables;
  }

  assemblyVariables(line: MiniCAssemblyTextLine): Map<string, string> {
    const variables = new Map<string, string>();
    variables.set("lineNumber", String(line.lineNumber));
    variables.set("text", line.text);
    variables.set("kind", line.kind);
    variables.set("section", this.blankValue(line.section));
    variables.set("label", this.blankValue(line.label));
    variables.set("source", this.sourceSnippetProvider(line.range));
    variables.set("range", this.rangeValue(line.range));
    variables.set("active", this.yesNo(line.active));
    return variables;
  }

  rangeValue(range: UiSourceSpanDto | null): string {
    if (range === null) {
      return "不可用";
    }
    return `${range.sourceName} ${range.startLine}:${range.startColumn} - ${range.endLine}:${range.endColumn} offset ${range.startOffset}..${range.endOffset}`;
  }

  blankValue(value: string | null | undefined): string {
    return value === undefined || value === null || value.trim() === "" ? "<无>" : value;
  }

  yesNo(value: boolean): string {
    return value ? "是" : "否";
  }

  private explanation(stage: string, role: string, variables: ReadonlyMap<string, string>): string {
    return [`阶段: ${stage}`, "", `解释: ${role}`, "", `源码: ${this.blankValue(variables.get("source"))}`, `范围: ${this.blankValue(variables.get("range"))}`].join("\n");
  }

  private addRangeVariables(variables: Map<string, string>, range: UiSourceSpanDto | null): void {
    if (range === null) {
      return;
    }
    variables.set("startLine", String(range.startLine));
    variables.set("startColumn", String(range.startColumn));
    variables.set("endLine", String(range.endLine));
    variables.set("endColumn", String(range.endColumn));
    variables.set("startOffset", String(range.startOffset));
    variables.set("endOffset", String(range.endOffset));
  }
}

export default MiniCVisualExplanationFormatter;
