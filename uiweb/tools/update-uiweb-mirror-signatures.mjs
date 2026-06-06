import fs from "node:fs";
import path from "node:path";

const projectRoot = path.resolve(import.meta.dirname, "..", "..");
const javaRoot = path.join(projectRoot, "src", "main", "java", "minic", "uilocal");
const webRoot = path.join(projectRoot, "uiweb", "src");
const componentNamePattern = /(App|Pane|Panel|View|Shell|Renderer|CodeEditor|SourceRows|TextFlowFactory)$/;

function walkFiles(root, predicate = () => true) {
  if (!fs.existsSync(root)) {
    return [];
  }
  const output = [];
  for (const entry of fs.readdirSync(root, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
    const fullPath = path.join(root, entry.name);
    if (entry.isDirectory()) {
      output.push(...walkFiles(fullPath, predicate));
    } else if (predicate(fullPath)) {
      output.push(fullPath);
    }
  }
  return output;
}

function mirrorFileFor(javaFile) {
  const relative = path.relative(javaRoot, javaFile);
  const name = path.basename(javaFile, ".java");
  const extension = componentNamePattern.test(name) ? ".tsx" : ".ts";
  return path.join(webRoot, path.dirname(relative), `${name}${extension}`);
}

function importsFromJava(source) {
  return [...source.matchAll(/^import\s+([^;]+);/gm)].map((match) => match[1]).sort();
}

function fieldsFromJava(source) {
  return [...source.matchAll(/^\s*((?:private|protected|public)\s+(?:static\s+)?(?:final\s+)?[\w<>, ?.[\]]+\s+(\w+)\s*(?:=|;))/gm)]
    .map((match) => ({
      name: match[2],
      signature: normalizeSignature(match[1]),
    }))
    .filter((field) => field.name !== "serialVersionUID")
    .sort((left, right) => left.name.localeCompare(right.name));
}

function methodsFromJava(source) {
  return [...source.matchAll(/^\s*(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?(?:synchronized\s+)?(?:[\w<>, ?.[\]]+\s+)?(\w+)\s*\(([^)]*)\)/gm)]
    .map((match) => ({
      name: match[1],
      signature: normalizeSignature(`${match[1]}(${match[2]})`),
    }))
    .filter((method) => !method.name.startsWith("MiniC"))
    .sort((left, right) => left.name.localeCompare(right.name) || left.signature.localeCompare(right.signature));
}

function normalizeSignature(signature) {
  return signature
    .replace(/\s+/g, " ")
    .replace(/\s*([(),;=<>])\s*/g, "$1")
    .replace(/;$/, "")
    .trim();
}

function replaceMirrorArray(source, propertyName, rendered) {
  const range = mirrorArrayRange(source, propertyName);
  if (range !== null) {
    const key = source.slice(range.keyStart, range.openBracket).replace(/:+\s*$/, "");
    return `${source.slice(0, range.keyStart)}${key}${rendered}${source.slice(range.closeBracket + 1)}`;
  }
  const insertAfter = mirrorArrayRange(source, previousProperty(propertyName)) ?? mirrorScalarRange(source, "kind");
  if (insertAfter === null) {
    throw new Error(`cannot find insertion point for ${propertyName}`);
  }
  const insertion = `,\n  "${propertyName}"${rendered}`;
  return `${source.slice(0, insertAfter.closeBracket + 1)}${insertion}${source.slice(insertAfter.closeBracket + 1)}`;
}

function previousProperty(propertyName) {
  if (propertyName === "fields") {
    return "imports";
  }
  if (propertyName === "methods") {
    return "fields";
  }
  return "kind";
}

function mirrorScalarRange(source, propertyName) {
  const match = source.match(new RegExp(`(?:"${propertyName}"|${propertyName})\\s*:\\s*[^,\\n]+`, "m"));
  if (!match) {
    return null;
  }
  return {
    keyStart: match.index,
    openBracket: match.index,
    closeBracket: match.index + match[0].length - 1,
  };
}

function mirrorArrayRange(source, propertyName) {
  const match = source.match(new RegExp(`(?:"${propertyName}"|${propertyName})\\s*:+\\s*\\[`, "m"));
  if (!match) {
    return null;
  }
  const openBracket = match.index + match[0].lastIndexOf("[");
  let depth = 0;
  let inString = false;
  let quote = "";
  let escaped = false;
  for (let index = openBracket; index < source.length; index++) {
    const character = source[index];
    if (inString) {
      if (escaped) {
        escaped = false;
      } else if (character === "\\") {
        escaped = true;
      } else if (character === quote) {
        inString = false;
      }
      continue;
    }
    if (character === "\"" || character === "'") {
      inString = true;
      quote = character;
      continue;
    }
    if (character === "[") {
      depth += 1;
      continue;
    }
    if (character === "]") {
      depth -= 1;
      if (depth === 0) {
        return {
          keyStart: match.index,
          openBracket,
          closeBracket: index,
        };
      }
    }
  }
  return null;
}

function renderStringArray(values) {
  if (values.length === 0) {
    return ": []";
  }
  return `: [\n${values.map((value) => `    ${JSON.stringify(value)}`).join(",\n")}\n  ]`;
}

function renderNamedEntries(entries) {
  if (entries.length === 0) {
    return ": []";
  }
  return `: [\n${entries.map(renderNamedEntry).join(",\n")}\n  ]`;
}

function renderNamedEntry(entry) {
  return `    {\n      "name": ${JSON.stringify(entry.name)},\n      "signature": ${JSON.stringify(entry.signature)}\n    }`;
}

let updated = 0;

for (const javaFile of walkFiles(javaRoot, (file) => file.endsWith(".java"))) {
  const webFile = mirrorFileFor(javaFile);
  if (!fs.existsSync(webFile)) {
    continue;
  }
  const javaSource = fs.readFileSync(javaFile, "utf8");
  let webSource = fs.readFileSync(webFile, "utf8");
  const before = webSource;
  webSource = replaceMirrorArray(webSource, "imports", renderStringArray(importsFromJava(javaSource)));
  webSource = replaceMirrorArray(webSource, "fields", renderNamedEntries(fieldsFromJava(javaSource)));
  webSource = replaceMirrorArray(webSource, "methods", renderNamedEntries(methodsFromJava(javaSource)));
  if (webSource !== before) {
    fs.writeFileSync(webFile, webSource);
    updated += 1;
  }
}

console.log(`Updated ${updated} UIWeb mirror signature blocks.`);
