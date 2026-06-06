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

const javaFiles = walkFiles(javaRoot, (file) => file.endsWith(".java"));
const missing = [];

for (const javaFile of javaFiles) {
  const relative = path.relative(javaRoot, javaFile);
  const name = path.basename(javaFile, ".java");
  const extension = componentNamePattern.test(name) ? ".tsx" : ".ts";
  const expected = path.join(webRoot, path.dirname(relative), `${name}${extension}`);
  if (!fs.existsSync(expected)) {
    missing.push(path.relative(projectRoot, expected));
  }
}

if (missing.length > 0) {
  console.error("Missing UIWeb mirror files:");
  for (const file of missing) {
    console.error(`- ${file}`);
  }
  process.exit(1);
}

console.log(`Verified ${javaFiles.length} UIWeb mirror files.`);
