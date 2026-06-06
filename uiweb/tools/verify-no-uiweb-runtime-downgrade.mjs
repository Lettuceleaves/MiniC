import fs from "node:fs";
import path from "node:path";

const projectRoot = path.resolve(import.meta.dirname, "..", "..");
const srcRoot = path.join(projectRoot, "uiweb", "src");

const forbiddenPatterns = [
  { pattern: /UIWeb 尚未连接/u, reason: "disconnected runtime message" },
  { pattern: /\bnoApiResult\b/u, reason: "fabricated API result" },
  { pattern: /\bmock\b/i, reason: "mock runtime path" },
  { pattern: /\bstub\b/i, reason: "stub runtime path" },
  { pattern: /\bdummy\b/i, reason: "dummy runtime path" },
  { pattern: /\bTODO\b/u, reason: "unfinished production path" },
  { pattern: /@ts-ignore/u, reason: "suppressed TypeScript error" },
  { pattern: /@ts-expect-error/u, reason: "suppressed TypeScript error" },
  { pattern: /\bas\s+any\b/u, reason: "unsafe TypeScript escape" },
  { pattern: /\bclass\s+MiniCLexer\b/u, reason: "local compiler emulation" },
  { pattern: /\banalyzeNow\s*\(/u, reason: "local realtime analysis implementation" },
  { pattern: /\bnew\s+MiniCRealtimeAnalyzer\s*\(/u, reason: "local realtime analyzer instead of UIAPI adapter" },
];

const allowedFiles = new Set([
  "translation/createMirrorComponent.tsx",
]);

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
  return path.relative(srcRoot, file).replaceAll(path.sep, "/");
}

const failures = [];

for (const file of walkFiles(srcRoot, (entry) => entry.endsWith(".ts") || entry.endsWith(".tsx"))) {
  const relative = projectPath(file);
  if (allowedFiles.has(relative)) {
    continue;
  }
  const lines = fs.readFileSync(file, "utf8").split(/\r?\n/);
  lines.forEach((line, index) => {
    for (const check of forbiddenPatterns) {
      if (check.pattern.test(line)) {
        failures.push(`${relative}:${index + 1}: ${check.reason}: ${line.trim()}`);
      }
    }
  });
}

if (failures.length > 0) {
  console.error("UIWeb production code contains runtime downgrades or unsafe escapes.");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Verified UIWeb production code contains no runtime downgrades.");
