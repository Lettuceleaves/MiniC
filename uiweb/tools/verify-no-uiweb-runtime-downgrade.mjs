import fs from "node:fs";
import path from "node:path";

const projectRoot = path.resolve(import.meta.dirname, "..", "..");
const uiwebRoot = path.join(projectRoot, "uiweb");

const forbiddenPatterns = [
  { id: "disconnected", pattern: /UIWeb 尚未连接/u, reason: "disconnected runtime message" },
  { id: "fabricated-api", pattern: /\bnoApiResult\b/u, reason: "fabricated API result" },
  { id: "mock", pattern: /\bmock\b/i, reason: "mock runtime path" },
  { id: "stub", pattern: /\bstub\b/i, reason: "stub runtime path" },
  { id: "dummy", pattern: /\bdummy\b/i, reason: "dummy runtime path" },
  { id: "todo", pattern: /\bTODO\b/u, reason: "unfinished code path" },
  { id: "placeholder", pattern: /\bplaceholder\b/i, reason: "placeholder code path" },
  { id: "ts-ignore", pattern: /@ts-ignore/u, reason: "suppressed TypeScript error" },
  { id: "ts-expect-error", pattern: /@ts-expect-error/u, reason: "suppressed TypeScript error" },
  { id: "as-any", pattern: /\bas\s+any\b/u, reason: "unsafe TypeScript escape" },
  { id: "local-lexer", pattern: /\bclass\s+MiniCLexer\b/u, reason: "local compiler emulation" },
  { id: "local-parser", pattern: /\bclass\s+MiniCParser\b/u, reason: "local parser emulation" },
  { id: "local-semantic", pattern: /\bclass\s+MiniCSemanticAnalyzer\b/u, reason: "local semantic emulation" },
  { id: "local-debugger", pattern: /\bclass\s+MiniCDebug(Session|Runtime|Interpreter)\b/u, reason: "local debugger emulation" },
  { id: "local-realtime", pattern: /\banalyzeNow\s*\(/u, reason: "local realtime analysis implementation" },
];

const scanExtensions = new Set([
  ".css",
  ".html",
  ".js",
  ".json",
  ".jsx",
  ".mjs",
  ".ps1",
  ".ts",
  ".tsx",
]);

const excludedDirectories = new Set([
  ".vite",
  "coverage",
  "dist",
  "node_modules",
  "uiweb-render-check",
]);

const excludedFiles = new Set([
  "package-lock.json",
  "tools/verify-no-uiweb-runtime-downgrade.mjs",
  "tools/verify-no-mirror-placeholders.mjs",
  "tools/verify-uiweb-runtime-workflows.mjs",
]);

function walkFiles(root, predicate = () => true) {
  if (!fs.existsSync(root)) {
    return [];
  }
  const output = [];
  for (const entry of fs.readdirSync(root, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
    const fullPath = path.join(root, entry.name);
    if (entry.isDirectory()) {
      if (excludedDirectories.has(entry.name)) {
        continue;
      }
      output.push(...walkFiles(fullPath, predicate));
    } else if (predicate(fullPath)) {
      output.push(fullPath);
    }
  }
  return output;
}

function projectPath(file) {
  return path.relative(uiwebRoot, file).replaceAll(path.sep, "/");
}

const failures = [];

for (const file of walkFiles(uiwebRoot, shouldScanFile)) {
  const relative = projectPath(file);
  if (excludedFiles.has(relative)) {
    continue;
  }
  const lines = fs.readFileSync(file, "utf8").split(/\r?\n/);
  lines.forEach((line, index) => {
    for (const check of forbiddenPatterns) {
      if (check.pattern.test(line) && !isAllowedMatch(relative, line, check.id)) {
        failures.push(`${relative}:${index + 1}: ${check.reason}: ${line.trim()}`);
      }
    }
  });
}

if (failures.length > 0) {
  console.error("UIWeb code contains runtime downgrades, placeholders, mocks, or unsafe escapes.");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Verified all UIWeb code contains no runtime downgrades, mocks, placeholders, or unsafe escapes.");

function shouldScanFile(file) {
  const relative = projectPath(file);
  if (relative.startsWith("uiweb-render-check/")) {
    return false;
  }
  return scanExtensions.has(path.extname(file));
}

function isAllowedMatch(relative, line, checkId) {
  if (checkId !== "placeholder") {
    return false;
  }
  if (line.includes("activity-placeholder")) {
    return true;
  }
  if (relative === "src/workbench/MiniCWorkbenchShell.tsx"
      && (line.includes('"placeholder"')
        || line.includes('"placeholderPage"')
        || line.includes("private final String placeholder")
        || line.includes("placeholderPage(ActivitySection section)"))) {
    return true;
  }
  if (relative === "package.json" && line.includes("verify:placeholders")) {
    return true;
  }
  return false;
}
