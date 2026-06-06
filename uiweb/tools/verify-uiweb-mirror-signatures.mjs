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

function projectPath(file) {
  return path.relative(projectRoot, file).replaceAll(path.sep, "/");
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
  return [...source.matchAll(/^\s*(?:private|protected|public)\s+(?:static\s+)?(?:final\s+)?[\w<>, ?.[\]]+\s+(\w+)\s*(?:=|;)/gm)]
    .map((match) => match[1])
    .filter((name) => !["serialVersionUID"].includes(name))
    .sort();
}

function methodsFromJava(source) {
  return [...source.matchAll(/^\s*(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?(?:synchronized\s+)?(?:[\w<>, ?.[\]]+\s+)?(\w+)\s*\(([^)]*)\)/gm)]
    .map((match) => ({
      name: match[1],
      normalized: normalizeSignature(`${match[1]}(${match[2]})`),
    }))
    .filter((method) => !method.name.startsWith("MiniC"))
    .sort((a, b) => a.normalized.localeCompare(b.normalized));
}

function mirrorArray(source, propertyName) {
  const match = source.match(new RegExp(`"${propertyName}"\\s*:\\s*\\[(?<body>[\\s\\S]*?)\\]\\s*,\\s*"`, "m"));
  if (!match?.groups?.body) {
    return null;
  }
  return match.groups.body;
}

function importsFromMirror(source) {
  const body = mirrorArray(source, "imports");
  if (body === null) {
    return null;
  }
  return [...body.matchAll(/"([^"]+)"/g)].map((match) => match[1]).sort();
}

function namedEntriesFromMirror(source, propertyName) {
  const body = mirrorArray(source, propertyName);
  if (body === null) {
    return null;
  }
  return [...body.matchAll(/"name"\s*:\s*"([^"]+)"[\s\S]*?"signature"\s*:\s*"([^"]+)"/g)]
    .map((match) => ({
      name: match[1],
      signature: match[2],
      normalized: normalizeSignature(match[2]),
    }))
    .sort((a, b) => a.name.localeCompare(b.name) || a.normalized.localeCompare(b.normalized));
}

function normalizeSignature(signature) {
  return signature
    .replace(/\s+/g, " ")
    .replace(/\s*([(),;=<>])\s*/g, "$1")
    .replace(/;$/, "")
    .trim();
}

function compareValues(kind, javaFile, javaValues, mirrorValues, failures) {
  if (mirrorValues === null) {
    failures.push(`${projectPath(javaFile)}: missing mirror ${kind} array`);
    return;
  }
  const missing = javaValues.filter((value) => !mirrorValues.includes(value));
  const stale = mirrorValues.filter((value) => !javaValues.includes(value));
  for (const value of missing) {
    failures.push(`${projectPath(javaFile)}: missing mirror ${kind}: ${value}`);
  }
  for (const value of stale) {
    failures.push(`${projectPath(javaFile)}: stale mirror ${kind}: ${value}`);
  }
}

function compareMethods(javaFile, javaMethods, mirrorMethods, failures) {
  if (mirrorMethods === null) {
    failures.push(`${projectPath(javaFile)}: missing mirror methods array`);
    return;
  }
  const javaByName = new Map();
  for (const method of javaMethods) {
    if (!javaByName.has(method.name)) {
      javaByName.set(method.name, []);
    }
    javaByName.get(method.name).push(method.normalized);
  }
  const mirrorByName = new Map();
  for (const method of mirrorMethods) {
    if (!mirrorByName.has(method.name)) {
      mirrorByName.set(method.name, []);
    }
    mirrorByName.get(method.name).push(method.normalized);
  }
  for (const [name, signatures] of javaByName) {
    if (!mirrorByName.has(name)) {
      failures.push(`${projectPath(javaFile)}: missing mirror method: ${name}`);
      continue;
    }
    for (const signature of signatures) {
      if (!mirrorByName.get(name).includes(signature)) {
        failures.push(`${projectPath(javaFile)}: missing mirror method signature: ${signature}`);
      }
    }
  }
  for (const [name, signatures] of mirrorByName) {
    if (!javaByName.has(name)) {
      failures.push(`${projectPath(javaFile)}: stale mirror method: ${name}`);
      continue;
    }
    for (const signature of signatures) {
      if (!javaByName.get(name).includes(signature)) {
        failures.push(`${projectPath(javaFile)}: stale mirror method signature: ${signature}`);
      }
    }
  }
}

const failures = [];

for (const javaFile of walkFiles(javaRoot, (file) => file.endsWith(".java"))) {
  const webFile = mirrorFileFor(javaFile);
  if (!fs.existsSync(webFile)) {
    failures.push(`${projectPath(javaFile)}: missing UIWeb file ${projectPath(webFile)}`);
    continue;
  }
  const javaSource = fs.readFileSync(javaFile, "utf8");
  const webSource = fs.readFileSync(webFile, "utf8");
  compareValues("import", javaFile, importsFromJava(javaSource), importsFromMirror(webSource), failures);
  compareValues(
    "field",
    javaFile,
    fieldsFromJava(javaSource),
    namedEntriesFromMirror(webSource, "fields")?.map((entry) => entry.name).sort() ?? null,
    failures,
  );
  compareMethods(javaFile, methodsFromJava(javaSource), namedEntriesFromMirror(webSource, "methods"), failures);
}

if (failures.length > 0) {
  console.error("UIWeb mirror signatures are stale or incomplete.");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Verified UIWeb mirror imports, fields, and methods match UILocal.");
