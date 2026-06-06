import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
const css = readFileSync(resolve(root, "src/styles/index.css"), "utf8");
const editor = readFileSync(resolve(root, "src/editor/MiniCCodeEditor.tsx"), "utf8");

const textareaRule = css.match(/\.code-editor textarea\.source-editor-input\s*\{(?<body>[^}]*)\}/);

if (!textareaRule?.groups?.body) {
  throw new Error("Missing textarea source editor CSS rule.");
}

if (!/overflow\s*:\s*auto\s*;/.test(textareaRule.groups.body)) {
  throw new Error("textarea.source-editor-input must set overflow: auto so editor content can scroll.");
}

if (!editor.includes('className="source-editor source-editor-input"')) {
  throw new Error("MiniCCodeEditor textarea class changed; revisit the scroll regression guard.");
}
