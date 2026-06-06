import fs from "node:fs";
import path from "node:path";

const projectRoot = path.resolve(import.meta.dirname, "..", "..");
const javaRoot = path.join(projectRoot, "src", "main", "java", "minic", "uilocal");
const resourceRoot = path.join(projectRoot, "src", "main", "resources", "minic", "uilocal");
const webRoot = path.join(projectRoot, "uiweb", "src");

const componentNamePattern = /(App|Pane|Panel|View|Shell|Renderer|CodeEditor|SourceRows|TextFlowFactory)$/;

function walkFiles(root, predicate = () => true) {
  if (!fs.existsSync(root)) {
    return [];
  }
  const output = [];
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    const fullPath = path.join(root, entry.name);
    if (entry.isDirectory()) {
      output.push(...walkFiles(fullPath, predicate));
    } else if (predicate(fullPath)) {
      output.push(fullPath);
    }
  }
  return output;
}

function toPosix(value) {
  return value.split(path.sep).join("/");
}

function relativeImport(fromFile, targetFile) {
  let relative = toPosix(path.relative(path.dirname(fromFile), targetFile));
  if (!relative.startsWith(".")) {
    relative = `./${relative}`;
  }
  return relative.replace(/\.(ts|tsx)$/, "");
}

function parseJava(javaPath) {
  const source = fs.readFileSync(javaPath, "utf8");
  const packageName = source.match(/^\s*package\s+([\w.]+)\s*;/m)?.[1] ?? "minic.uilocal";
  const imports = [...source.matchAll(/^\s*import\s+([\w.*]+)\s*;/gm)].map((match) => match[1]);
  const declaration = source.match(/\b(public\s+)?(final\s+|abstract\s+)?(class|enum|interface|record)\s+(\w+)/);
  const kind = declaration?.[3] ?? "class";
  const exportName = declaration?.[4] ?? path.basename(javaPath, ".java");
  const fields = [...source.matchAll(/^\s*(?:private|protected|public)\s+(?:static\s+)?(?:final\s+)?([\w<>, ?.[\]]+)\s+(\w+)\s*(?:=|;)/gm)]
    .map((match) => ({ name: match[2], signature: match[0].trim().replace(/\s+/g, " ") }))
    .slice(0, 80);
  const methods = [...source.matchAll(/^\s*(?:private|protected|public)\s+(?:static\s+)?(?:final\s+)?(?:synchronized\s+)?([\w<>, ?.[\]]+)\s+(\w+)\s*\(([^)]*)\)/gm)]
    .filter((match) => !["if", "for", "while", "switch"].includes(match[2]))
    .map((match) => ({ name: match[2], signature: `${match[2]}(${match[3].trim().replace(/\s+/g, " ")})` }))
    .slice(0, 120);
  return { packageName, imports, kind, exportName, fields, methods };
}

function literal(value) {
  return JSON.stringify(value, null, 2);
}

function writeFile(filePath, content) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, content, "utf8");
}

function generateTs(javaPath) {
  const parsed = parseJava(javaPath);
  const relativeJava = toPosix(path.relative(projectRoot, javaPath));
  const relativeSource = toPosix(path.relative(javaRoot, javaPath));
  const subdir = path.dirname(relativeSource) === "." ? "" : path.dirname(relativeSource);
  const isComponent = componentNamePattern.test(parsed.exportName);
  const extension = isComponent ? ".tsx" : ".ts";
  const webPath = path.join(webRoot, subdir, `${parsed.exportName}${extension}`);
  const mirrorImport = relativeImport(webPath, path.join(webRoot, "translation", "javaMirror.ts"));
  const mirrorObjectName = `${parsed.exportName[0].toLowerCase()}${parsed.exportName.slice(1)}Mirror`;
  const mirror = {
    javaPath: relativeJava,
    webPath: toPosix(path.relative(projectRoot, webPath)),
    packageName: parsed.packageName,
    exportName: parsed.exportName,
    kind: isComponent ? "component" : parsed.kind,
    imports: parsed.imports,
    fields: parsed.fields,
    methods: parsed.methods,
  };

  if (isComponent) {
    const componentImport = relativeImport(webPath, path.join(webRoot, "translation", "createMirrorComponent.tsx"));
    writeFile(webPath, `import { createMirrorComponent } from "${componentImport}";\nimport type { JavaMirrorFile } from "${mirrorImport}";\n\nexport const ${mirrorObjectName} = ${literal(mirror)} as const satisfies JavaMirrorFile;\n\nexport const ${parsed.exportName} = createMirrorComponent(${mirrorObjectName});\n\nexport default ${parsed.exportName};\n`);
    return webPath;
  }

  writeFile(webPath, `import type { JavaMirrorFile } from "${mirrorImport}";\n\nexport const ${mirrorObjectName} = ${literal(mirror)} as const satisfies JavaMirrorFile;\n\nexport class ${parsed.exportName} {\n  static readonly mirror = ${mirrorObjectName};\n\n  readonly mirror = ${mirrorObjectName};\n\n  summary(): string {\n    return \`${parsed.exportName}: \${this.mirror.methods.length} methods, \${this.mirror.fields.length} fields\`;\n  }\n}\n\nexport default ${parsed.exportName};\n`);
  return webPath;
}

function generateIndexes(generatedFiles) {
  const byDir = new Map();
  for (const file of generatedFiles) {
    const dir = path.dirname(file);
    if (!byDir.has(dir)) {
      byDir.set(dir, []);
    }
    byDir.get(dir).push(file);
  }
  for (const [dir, files] of byDir) {
    const exports = files
      .sort()
      .map((file) => `export * from "./${path.basename(file).replace(/\.(ts|tsx)$/, "")}";`)
      .join("\n");
    writeFile(path.join(dir, "index.ts"), `${exports}\n`);
  }
  const rootExports = [...byDir.keys()]
    .filter((dir) => dir !== webRoot)
    .sort()
    .map((dir) => `export * as ${path.basename(dir)} from "./${toPosix(path.relative(webRoot, dir))}";`)
    .join("\n");
  writeFile(path.join(webRoot, "index.ts"), `${rootExports}\n`);
}

function copyResources() {
  const files = walkFiles(resourceRoot);
  for (const source of files) {
    const relative = path.relative(resourceRoot, source);
    const target = path.join(webRoot, "resources", "minic", "uilocal", relative);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.copyFileSync(source, target);
  }
}

function generateManifest(generatedFiles) {
  const mappings = generatedFiles.map((webPath) => {
    const source = fs.readFileSync(webPath, "utf8");
    const javaPath = source.match(/"javaPath": "([^"]+)"/)?.[1] ?? "";
    return {
      javaPath,
      webPath: toPosix(path.relative(projectRoot, webPath)),
    };
  });
  writeFile(path.join(webRoot, "translation", "uiwebManifest.ts"), `export const uiwebManifest = ${literal(mappings)} as const;\n`);
}

const javaFiles = walkFiles(javaRoot, (file) => file.endsWith(".java"));
const generatedFiles = javaFiles.map(generateTs);
generateIndexes(generatedFiles);
copyResources();
generateManifest(generatedFiles);

console.log(`Generated ${generatedFiles.length} UIWeb mirror files.`);
