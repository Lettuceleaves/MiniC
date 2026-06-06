import { Fragment, useEffect, useMemo, useState, type ReactNode } from "react";
import { MiniCRealtimeAnalysisHttpAdapter } from "../api/MiniCRealtimeAnalysisHttpAdapter";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiLexerTokenVisualDto } from "../translation/uiapi";
import { MiniCSourceTextHighlighter } from "../text/MiniCSourceTextHighlighter";

export const miniCMarkdownRendererMirror = {
  "javaPath": "src/main/java/minic/uilocal/info/MiniCMarkdownRenderer.java",
  "webPath": "uiweb/src/info/MiniCMarkdownRenderer.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCMarkdownRenderer",
  "kind": "component",
  "imports": [
    "java.util.ArrayList",
    "java.util.List",
    "java.util.Locale",
    "java.util.regex.Matcher",
    "java.util.regex.Pattern",
    "javafx.scene.control.Label",
    "javafx.scene.layout.HBox",
    "javafx.scene.layout.Priority",
    "javafx.scene.layout.VBox",
    "javafx.scene.text.Text",
    "javafx.scene.text.TextFlow",
    "minic.uilocal.text.MiniCSourceTextHighlighter",
    "minic.uilocal.text.MiniCStyledTextSegment",
    "minic.uilocal.text.MiniCTextFlowFactory",
    "minic.uilocal.text.MiniCTextStyleRole"
  ],
  "fields": [
    {
      "name": "HEADING",
      "signature": "private static final Pattern HEADING="
    },
    {
      "name": "ORDERED_LIST",
      "signature": "private static final Pattern ORDERED_LIST="
    },
    {
      "name": "sourceTextHighlighter",
      "signature": "private final MiniCSourceTextHighlighter sourceTextHighlighter="
    },
    {
      "name": "UNORDERED_LIST",
      "signature": "private static final Pattern UNORDERED_LIST="
    }
  ],
  "methods": [
    {
      "name": "addCodeBlock",
      "signature": "addCodeBlock(VBox content,String code,String language)"
    },
    {
      "name": "addHeading",
      "signature": "addHeading(VBox content,int level,String text)"
    },
    {
      "name": "addListItem",
      "signature": "addListItem(VBox content,String marker,String text)"
    },
    {
      "name": "addText",
      "signature": "addText(TextFlow flow,String value,String styleClass)"
    },
    {
      "name": "codeLanguage",
      "signature": "codeLanguage(String fenceLine)"
    },
    {
      "name": "flushParagraph",
      "signature": "flushParagraph(VBox content,List<String>paragraph)"
    },
    {
      "name": "inlineFlow",
      "signature": "inlineFlow(String markdown)"
    },
    {
      "name": "InlineToken",
      "signature": "InlineToken(int start,int end,String value,String styleClass)"
    },
    {
      "name": "isMiniCCode",
      "signature": "isMiniCCode(String language)"
    },
    {
      "name": "nextToken",
      "signature": "nextToken(String markdown,int from)"
    },
    {
      "name": "normalize",
      "signature": "normalize(String markdown)"
    }
  ]
} as const satisfies JavaMirrorFile;

const HEADING = /^(#{1,3})\s+(.+)$/;
const UNORDERED_LIST = /^[-*]\s+(.+)$/;
const ORDERED_LIST = /^\d+[.)]\s+(.+)$/;

export interface MiniCMarkdownRendererProps {
  readonly markdown: string;
}

export function MiniCMarkdownRenderer({ markdown }: MiniCMarkdownRendererProps) {
  return render(markdown);
}

MiniCMarkdownRenderer.mirror = miniCMarkdownRendererMirror;

export function render(markdown: string | null | undefined) {
  const content: ReactNode[] = [];
  const paragraph: string[] = [];
  let inCodeBlock = false;
  let codeLanguage = "";
  let codeBlock = "";

  const flushParagraph = (): void => {
    if (paragraph.length === 0) {
      return;
    }
    content.push(
      <p className="info-paragraph" key={`p-${content.length}`}>
        {inlineFlow(paragraph.join(" "))}
      </p>,
    );
    paragraph.length = 0;
  };

  for (const rawLine of normalize(markdown).split("\n")) {
    const line = rawLine.trimEnd();
    const trimmed = line.trimStart();
    if (trimmed.startsWith("```")) {
      if (inCodeBlock) {
        content.push(addCodeBlock(codeBlock, codeLanguage, content.length));
        codeBlock = "";
        codeLanguage = "";
        inCodeBlock = false;
      } else {
        flushParagraph();
        codeLanguage = codeLanguageFromFence(trimmed);
        inCodeBlock = true;
      }
      continue;
    }
    if (inCodeBlock) {
      codeBlock += `${rawLine}\n`;
      continue;
    }
    if (trimmed.length === 0) {
      flushParagraph();
      continue;
    }
    const heading = HEADING.exec(trimmed);
    if (heading) {
      flushParagraph();
      content.push(addHeading(heading[1].length, heading[2], content.length));
      continue;
    }
    const unordered = UNORDERED_LIST.exec(trimmed);
    const ordered = ORDERED_LIST.exec(trimmed);
    if (unordered) {
      flushParagraph();
      content.push(addListItem("•", unordered[1], content.length));
      continue;
    }
    if (ordered) {
      flushParagraph();
      content.push(addListItem("•", ordered[1], content.length));
      continue;
    }
    paragraph.push(trimmed);
  }

  if (inCodeBlock) {
    content.push(addCodeBlock(codeBlock, codeLanguage, content.length));
  }
  flushParagraph();
  return <section className="info-markdown">{content}</section>;
}

export function normalize(markdown: string | null | undefined): string {
  return markdown === null || markdown === undefined ? "" : markdown.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
}

export function addHeading(level: number, text: string, key: number): ReactNode {
  const Tag = (`h${Math.min(3, Math.max(1, level))}` as "h1" | "h2" | "h3");
  return (
    <Tag className={`info-heading-${level}`} key={`h-${key}`}>
      {text.trim()}
    </Tag>
  );
}

export function addCodeBlock(code: string, language: string, key: number): ReactNode {
  const normalized = code.trimEnd();
  if (isMiniCCode(language)) {
    return <MiniCCodeBlock code={normalized} key={`code-${key}`} />;
  }
  return (
    <pre className="info-code-block" key={`code-${key}`}>
      {normalized.length === 0 ? " " : normalized}
    </pre>
  );
}

function MiniCCodeBlock({ code }: { readonly code: string }) {
  const highlighter = useMemo(() => new MiniCSourceTextHighlighter(), []);
  const realtimeApi = useMemo(() => new MiniCRealtimeAnalysisHttpAdapter(), []);
  const [tokens, setTokens] = useState<readonly UiLexerTokenVisualDto[]>([]);

  useEffect(() => {
    let active = true;
    void realtimeApi.tokenize("guide-code.mc", code).then((result) => {
      if (active) {
        setTokens(result);
      }
    });
    return () => {
      active = false;
    };
  }, [code, realtimeApi]);

  const segments = highlighter.highlight(code, tokens);
  return (
    <pre className="info-code-block">
      {segments.map((segment, index) => (
        <span className={segment.role.legacyClasses.join(" ") || segment.role.cssClass} key={`${segment.text}-${index}`}>
          {segment.text}
        </span>
      ))}
    </pre>
  );
}

export function codeLanguageFromFence(fenceLine: string): string {
  const language = fenceLine.length <= 3 ? "" : fenceLine.slice(3).trim().toLowerCase();
  const whitespace = language.search(/\s/);
  return whitespace < 0 ? language : language.slice(0, whitespace);
}

export function isMiniCCode(language: string): boolean {
  return language.length === 0 || language === "c" || language === "h" || language === "mc" || language === "minic";
}

export function addListItem(marker: string, text: string, key: number): ReactNode {
  return (
    <div className="info-list-item" key={`li-${key}`}>
      <span className="info-list-marker">{marker}</span>
      <span>{inlineFlow(text)}</span>
    </div>
  );
}

export function inlineFlow(markdown: string): ReactNode {
  const nodes: ReactNode[] = [];
  let index = 0;
  while (index < markdown.length) {
    const token = nextToken(markdown, index);
    if (token.start > index) {
      nodes.push(addText(markdown.slice(index, token.start), "info-text", nodes.length));
    }
    if (token.closed) {
      nodes.push(addText(token.value, token.styleClass, nodes.length));
      index = token.end;
    } else {
      nodes.push(addText(markdown.slice(token.start), "info-text", nodes.length));
      index = markdown.length;
    }
  }
  return <>{nodes}</>;
}

interface InlineToken {
  readonly start: number;
  readonly end: number;
  readonly value: string;
  readonly styleClass: string;
  readonly closed: boolean;
}

export function nextToken(markdown: string, from: number): InlineToken {
  const codeStart = markdown.indexOf("`", from);
  const strongStart = markdown.indexOf("**", from);
  if (codeStart < 0 && strongStart < 0) {
    return { start: markdown.length, end: markdown.length, value: "", styleClass: "info-text", closed: true };
  }
  if (codeStart >= 0 && (strongStart < 0 || codeStart < strongStart)) {
    const end = markdown.indexOf("`", codeStart + 1);
    return end < 0
      ? { start: codeStart, end: markdown.length, value: "", styleClass: "info-text", closed: false }
      : { start: codeStart, end: end + 1, value: markdown.slice(codeStart + 1, end), styleClass: "info-inline-code", closed: true };
  }
  const end = markdown.indexOf("**", strongStart + 2);
  return end < 0
    ? { start: strongStart, end: markdown.length, value: "", styleClass: "info-text", closed: false }
    : { start: strongStart, end: end + 2, value: markdown.slice(strongStart + 2, end), styleClass: "info-strong", closed: true };
}

export function addText(value: string, styleClass: string, key: number): ReactNode {
  if (value.length === 0) {
    return <Fragment key={`empty-${key}`} />;
  }
  return (
    <span className={styleClass} key={`${styleClass}-${key}`}>
      {value}
    </span>
  );
}

export default MiniCMarkdownRenderer;
