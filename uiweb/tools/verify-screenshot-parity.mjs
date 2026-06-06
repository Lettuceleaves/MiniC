import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { captureUiwebScreenshots, matrixStates, viewports } from "./capture-uiweb-screenshots.mjs";

const toolDir = path.dirname(fileURLToPath(import.meta.url));
const uiwebRoot = path.resolve(toolDir, "..");
const projectRoot = path.resolve(toolDir, "..", "..");
const reportRoot = path.resolve(projectRoot, "uiweb-render-check", "parity-report");
const manifestPath = path.join(reportRoot, "manifest.json");

const skipCapture = process.argv.includes("--skip-capture");
const skipUiLocal = process.argv.includes("--skip-uilocal");

if (!skipCapture && !skipUiLocal) {
  await captureUiLocalScreenshots();
}
const manifest = skipCapture
  ? readManifest()
  : await captureUiwebScreenshots();

const failures = [];
const expectedCount = viewports.length * matrixStates.length;
if (manifest.captures.length !== expectedCount) {
  failures.push(`expected ${expectedCount} captures, got ${manifest.captures.length}`);
}

for (const viewport of viewports) {
  for (const state of matrixStates) {
    const capture = manifest.captures.find((item) => item.viewport === viewport.id && item.state === state.id);
    if (!capture) {
      failures.push(`missing capture ${viewport.id}/${state.id}`);
      continue;
    }
    const file = path.join(reportRoot, capture.path);
    if (!fs.existsSync(file)) {
      failures.push(`missing screenshot file ${capture.path}`);
      continue;
    }
    const size = fs.statSync(file).size;
    if (size < 8_000) {
      failures.push(`screenshot too small ${capture.path}: ${size} bytes`);
    }
    const dimensions = pngDimensions(file);
    if (dimensions.width !== viewport.width || dimensions.height !== viewport.height) {
      failures.push(`${capture.path}: expected ${viewport.width}x${viewport.height}, got ${dimensions.width}x${dimensions.height}`);
    }
    const referencePath = path.join("uilocal", viewport.id, `${state.id}.png`).replaceAll(path.sep, "/");
    const referenceFile = path.join(reportRoot, referencePath);
    if (!fs.existsSync(referenceFile)) {
      failures.push(`missing JavaFX reference screenshot ${referencePath}`);
    } else {
      const referenceSize = fs.statSync(referenceFile).size;
      if (referenceSize < 8_000) {
        failures.push(`JavaFX reference screenshot too small ${referencePath}: ${referenceSize} bytes`);
      }
      const referenceDimensions = pngDimensions(referenceFile);
      if (referenceDimensions.width !== viewport.width || referenceDimensions.height !== viewport.height) {
        failures.push(`${referencePath}: expected ${viewport.width}x${viewport.height}, got ${referenceDimensions.width}x${referenceDimensions.height}`);
      }
    }
    checkMetric(capture, ".activity-bar", "width", 48, 1);
    checkMetric(capture, ".status-bar", "height", 22, 1);
    if (capture.metrics[".debug-view-selector"]?.visible) {
      checkMetric(capture, ".debug-view-selector", "width", 88, 2);
    }
    if (capture.metrics[".editor-gutter-column"]?.visible) {
      checkMetric(capture, ".editor-gutter-column", "width", 82, 2);
    }
  }
}

const report = {
  checkedAt: new Date().toISOString(),
  captures: manifest.captures.length,
  expected: expectedCount,
  failures,
};
fs.writeFileSync(path.join(reportRoot, "verification.json"), JSON.stringify(report, null, 2));

if (failures.length > 0) {
  console.error("Screenshot parity verification failed.");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log(`Verified ${manifest.captures.length} UIWeb screenshot states and matching JavaFX references across ${viewports.length} viewports.`);
console.log(`Report: ${path.join(reportRoot, "index.html")}`);

async function captureUiLocalScreenshots() {
  const scriptPath = path.join(uiwebRoot, "tools", "capture-uilocal-screenshots.ps1");
  await runProcess("powershell", [
    "-NoProfile",
    "-ExecutionPolicy",
    "Bypass",
    "-File",
    scriptPath,
    path.join(reportRoot, "uilocal"),
  ], projectRoot);
}

async function runProcess(command, args, cwd) {
  await new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd,
      shell: process.platform === "win32",
      stdio: ["ignore", "pipe", "pipe"],
      windowsHide: true,
    });
    let output = "";
    child.stdout.setEncoding("utf8");
    child.stderr.setEncoding("utf8");
    child.stdout.on("data", (chunk) => {
      output += chunk;
      process.stdout.write(chunk);
    });
    child.stderr.on("data", (chunk) => {
      output += chunk;
      process.stderr.write(chunk);
    });
    child.on("error", reject);
    child.on("exit", (code) => {
      if (code === 0) {
        resolve();
      } else {
        reject(new Error(`${command} ${args.join(" ")} failed with exit ${code}\n${output}`));
      }
    });
  });
}

function readManifest() {
  if (!fs.existsSync(manifestPath)) {
    throw new Error(`missing manifest: ${manifestPath}`);
  }
  return JSON.parse(fs.readFileSync(manifestPath, "utf8"));
}

function checkMetric(capture, selector, property, expected, tolerance) {
  const metric = capture.metrics[selector];
  if (!metric?.visible) {
    failures.push(`${capture.viewport}/${capture.state}: missing visible metric ${selector}`);
    return;
  }
  const actual = metric[property];
  if (typeof actual !== "number" || Math.abs(actual - expected) > tolerance) {
    failures.push(`${capture.viewport}/${capture.state}: ${selector}.${property} expected ${expected} +/- ${tolerance}, got ${actual}`);
  }
}

function pngDimensions(file) {
  const buffer = fs.readFileSync(file);
  if (buffer.length < 24 || buffer[0] !== 0x89 || buffer[1] !== 0x50 || buffer[2] !== 0x4e || buffer[3] !== 0x47) {
    throw new Error(`not a PNG file: ${file}`);
  }
  return {
    width: buffer.readUInt32BE(16),
    height: buffer.readUInt32BE(20),
  };
}
