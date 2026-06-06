import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "@playwright/test";
import { findFreePort, startUiApiServer, stopProcessTree } from "./run-uiapi-server.mjs";

const toolDir = path.dirname(fileURLToPath(import.meta.url));
const uiwebRoot = path.resolve(toolDir, "..");
const projectRoot = path.resolve(uiwebRoot, "..");
const reportRoot = path.resolve(projectRoot, "uiweb-render-check", "parity-report");
const screenshotRoot = path.join(reportRoot, "uiweb");

export const viewports = [
  { id: "desktop-1920x1080", width: 1920, height: 1080 },
  { id: "desktop-1366x768", width: 1366, height: 768 },
  { id: "mobile-390x844", width: 390, height: 844 },
];

export const matrixStates = [
  { id: "pipeline-before-start", title: "编译页启动前", required: [".sidebar", ".inspector", "textarea.source-editor-input"] },
  { id: "source-before-start", title: "源码页启动前", required: ["textarea.source-editor-input", ".editor-gutter .lineno"] },
  { id: "source-long-scroll-breakpoint", title: "源码长文件滚动断点", required: ["textarea.source-editor-input", ".source-editor-render .token-keyword", 'button[aria-label="清除第 12 行断点"].active'] },
  { id: "pipeline-after-start", title: "编译页启动后", required: [".sidebar", ".inspector", ".panel-title"] },
  { id: "pipeline-stage-source", title: "Pipeline 源码阶段", required: ['.stage-card[data-stage-id="source"]', "textarea.source-editor-input"] },
  { id: "pipeline-stage-preprocess", title: "Pipeline 预编译阶段", required: [".visual-canvas", ".pane-head", '.stage-card[data-stage-id="preprocess"]'] },
  { id: "pipeline-stage-lexer", title: "Pipeline 词法阶段", required: [".visual-canvas", ".token-row", '.stage-card[data-stage-id="lexer"]'] },
  { id: "pipeline-stage-parser", title: "Pipeline AST 阶段", required: [".visual-canvas", ".ast-zoom-box", '.stage-card[data-stage-id="parser"]'] },
  { id: "pipeline-stage-semantic", title: "Pipeline 语义阶段", required: [".visual-canvas", ".semantic-row", '.stage-card[data-stage-id="semantic"]'] },
  { id: "pipeline-stage-ir", title: "Pipeline IR 阶段", required: [".visual-canvas", ".stage-flow-column", '.stage-card[data-stage-id="ir"]'] },
  { id: "pipeline-stage-codegen", title: "Pipeline 代码生成阶段", required: [".visual-canvas", ".assembly-row", '.stage-card[data-stage-id="codegen"]'] },
  { id: "pipeline-stage-toolchain", title: "Pipeline 工具链阶段", required: [".visual-canvas", ".stage-flow-column", '.stage-card[data-stage-id="toolchain"]'] },
  { id: "pipeline-stage-execution", title: "Pipeline 执行阶段", required: [".visual-canvas", ".execution-stdin", '.stage-card[data-stage-id="execution"]'] },
  { id: "debug-before-start", title: "Debugger 启动前", required: [".debug-pane", ".debug-view-selector", "textarea.source-editor-input"] },
  { id: "debug-metadata", title: "Debugger 元数据", required: [".debug-pane", ".debug-metadata", ".debug-summary-grid"] },
  { id: "debug-source", title: "Debugger 源码与断点", required: [".debug-pane", ".debug-source-panel", ".debug-source-editor-scroll", ".editor-gutter.current-execution", ".breakpoint-gutter.active"] },
  { id: "debug-data-structure", title: "Debugger 数据结构", required: [".debug-pane", ".debug-data-space", ".debug-process-section"] },
  { id: "debug-visual-diagram", title: "Debugger 数据结构图", required: [".debug-pane", ".debug-data-space", ".debug-visuals", ".debug-visual-diagram"] },
  { id: "debug-ast", title: "Debugger AST", required: [".debug-pane", ".debug-ast-view", ".debug-section-title"] },
  { id: "debug-ir", title: "Debugger IR", required: [".debug-pane", ".debug-code-view", ".debug-code-row"] },
  { id: "debug-asm", title: "Debugger ASM", required: [".debug-pane", ".debug-code-view", ".debug-code-row"] },
  { id: "settings", title: "设置页", required: [".settings-scroll", "#minic-theme", ".key-binding-button"] },
  { id: "info", title: "介绍页", required: [".info-scroll", ".info-markdown", ".info-code-block"] },
  { id: "bottom-panel-collapsed", title: "底部面板收起", required: [".bottom-panel:not(.expanded)", ".bottom-toggle"] },
  { id: "bottom-panel-expanded", title: "底部面板展开", required: [".bottom-panel.expanded", ".bottom-body"] },
];

const workflowSource = [
  "// @visual root=node kind=binary-tree label=key",
  "struct Node { int key; struct Node *left; struct Node *right; };",
  "",
  "int inc(int value) {",
  "    int next = value + 1;",
  "    return next;",
  "}",
  "",
  "int main() {",
  "    struct Node node;",
  "    int x = 0;",
  "    node.key = inc(1);",
  "    node.left = NULL;",
  "    node.right = NULL;",
  "    x = node.key;",
  "    x = inc(x);",
  "    x = inc(x);",
  "    return x;",
  "}",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
].join("\n");

const metricSelectors = [
  ".workbench-root",
  ".activity-bar",
  ".status-bar",
  ".sidebar",
  ".editor-area",
  ".source-editor-scroll",
  ".editor-gutter-column",
  ".inspector",
  ".visual-canvas",
  ".debug-pane",
  ".debug-view-selector",
  ".debug-source-panel",
  ".debug-view-content",
  ".settings-scroll",
  ".info-scroll",
  ".bottom-panel",
];

export async function captureUiwebScreenshots() {
  cleanUiwebReportRoot();
  const uiApi = await startUiApiServer();
  const vite = await startViteServer(uiApi.baseUrl);
  const browser = await chromium.launch();
  const manifest = {
    generatedAt: new Date().toISOString(),
    uiApiBaseUrl: uiApi.baseUrl,
    appBaseUrl: vite.baseUrl,
    viewports,
    states: matrixStates,
    captures: [],
  };
  try {
    for (const viewport of viewports) {
      const context = await browser.newContext({ viewport: { width: viewport.width, height: viewport.height } });
      await context.addInitScript(() => window.localStorage.clear());
      const page = await context.newPage();
      const pageErrors = [];
      page.on("console", (message) => {
        if (message.type() === "error") {
          pageErrors.push(message.text());
        }
      });
      page.on("pageerror", (error) => pageErrors.push(error.message));
      await driveAndCaptureViewport(page, viewport, vite.baseUrl, uiApi.baseUrl, manifest);
      if (pageErrors.length > 0) {
        throw new Error(`browser errors while capturing ${viewport.id}:\n${pageErrors.join("\n")}`);
      }
      await context.close();
    }
  } finally {
    await browser.close().catch(() => {});
    await vite.stop();
    await uiApi.stop();
  }
  fs.mkdirSync(reportRoot, { recursive: true });
  fs.writeFileSync(path.join(reportRoot, "manifest.json"), JSON.stringify(manifest, null, 2));
  writeReportHtml(manifest);
  return manifest;
}

async function driveAndCaptureViewport(page, viewport, appBaseUrl, apiBaseUrl, manifest) {
  await page.goto(appBaseUrl, { waitUntil: "domcontentloaded" });
  await page.locator(".workbench-root").waitFor({ state: "visible" });
  await prepareLongSource(page, apiBaseUrl);
  await captureState(page, viewport, "pipeline-before-start", manifest);
  await captureState(page, viewport, "source-before-start", manifest);

  await page.locator("textarea.source-editor-input").first().evaluate((node) => {
    node.scrollTop = 160;
    node.dispatchEvent(new Event("scroll", { bubbles: true }));
  });
  await page.locator('button[aria-label="设置第 12 行断点"]').first().click();
  await page.locator('button[aria-label="清除第 12 行断点"].active').first().waitFor({ state: "visible" });
  await captureState(page, viewport, "source-long-scroll-breakpoint", manifest);

  await Promise.all([
    waitForApiResponse(page, apiBaseUrl, "POST", ["/api/observation/", "/start"]),
    page.getByRole("button", { name: "开始", exact: true }).click(),
  ]);
  await page.locator(".inspector").waitFor({ state: "visible" });
  await captureState(page, viewport, "pipeline-after-start", manifest);

  await clickControlForApi(page, apiBaseUrl, ".inspector", "到执行", "POST", ["/api/observation/", "/next-stage"], 90_000);
  await page.waitForFunction(() => {
    const execution = document.querySelector('.stage-card[data-stage-id="execution"]');
    return execution !== null && !execution.hasAttribute("disabled");
  }, undefined, { timeout: 90_000 });

  const stageIds = ["source", "preprocess", "lexer", "parser", "semantic", "ir", "codegen", "toolchain", "execution"];
  for (const stageId of stageIds) {
    await page.locator(`.stage-card[data-stage-id="${stageId}"]`).click();
    await waitForStageView(page, stageId);
    await captureState(page, viewport, `pipeline-stage-${stageId}`, manifest);
  }

  await page.getByRole("button", { name: "调试", exact: true }).click();
  await page.locator(".debug-pane").waitFor({ state: "visible" });
  await captureState(page, viewport, "debug-before-start", manifest);
  const debugBreakpoint = page.locator('button[aria-label="设置第 12 行断点"]').first();
  if (await debugBreakpoint.count() > 0) {
    await debugBreakpoint.click();
  }
  await Promise.all([
    waitForApiResponse(page, apiBaseUrl, "POST", ["/api/debug/", "/start"]),
    waitForApiResponse(page, apiBaseUrl, "GET", ["/api/debug/", "/metadata"]),
    waitForApiResponse(page, apiBaseUrl, "GET", ["/api/debug/", "/data-structure"]),
    waitForApiResponse(page, apiBaseUrl, "GET", ["/api/debug/", "/ast"]),
    waitForApiResponse(page, apiBaseUrl, "GET", ["/api/debug/", "/ir"]),
    waitForApiResponse(page, apiBaseUrl, "GET", ["/api/debug/", "/asm"]),
    page.getByRole("button", { name: "从头开始", exact: true }).click(),
  ]);
  await page.locator(".debug-status").waitFor({ state: "visible" });
  await clickControlForApi(page, apiBaseUrl, ".debug-pane", "下一句", "POST", ["/api/debug/", "/step-into"], 60_000);
  await page.locator(".editor-gutter.current-execution").first().waitFor({ state: "visible" });
  await captureState(page, viewport, "debug-metadata", manifest);
  await captureState(page, viewport, "debug-source", manifest);
  for (const [viewTitle, stateId] of [
    ["数据结构", "debug-data-structure"],
    ["数据结构", "debug-visual-diagram"],
    ["AST", "debug-ast"],
    ["IR", "debug-ir"],
    ["ASM", "debug-asm"],
  ]) {
    await page.locator(".debug-view-button").filter({ hasText: exactText(viewTitle) }).click();
    await page.locator(".debug-view-content").waitFor({ state: "visible" });
    await captureState(page, viewport, stateId, manifest);
  }

  await page.getByRole("button", { name: "设置", exact: true }).click();
  await page.locator(".settings-scroll").waitFor({ state: "visible" });
  await captureState(page, viewport, "settings", manifest);

  const tokenizeWait = waitForApiResponse(page, apiBaseUrl, "POST", ["/api/realtime/tokenize"], 30_000).catch(() => null);
  await page.getByRole("button", { name: "信息", exact: true }).click();
  await page.locator(".info-scroll").waitFor({ state: "visible" });
  await tokenizeWait;
  await captureState(page, viewport, "info", manifest);

  await page.getByRole("button", { name: "代码区", exact: true }).click();
  await page.locator(".bottom-panel:not(.expanded)").waitFor({ state: "visible" });
  await captureState(page, viewport, "bottom-panel-collapsed", manifest);
  await page.locator(".bottom-toggle").click();
  await page.locator(".bottom-panel.expanded").waitFor({ state: "visible" });
  await captureState(page, viewport, "bottom-panel-expanded", manifest);
}

async function prepareLongSource(page, apiBaseUrl) {
  const editor = page.locator("textarea.source-editor-input").first();
  await editor.waitFor({ state: "visible" });
  await Promise.all([
    waitForApiResponse(page, apiBaseUrl, "POST", ["/api/realtime/analyze"], 60_000),
    editor.fill(workflowSource),
  ]);
  await page.waitForFunction(() => document.querySelectorAll(".source-editor-render .token-keyword").length > 0);
  await page.waitForFunction(() => document.querySelectorAll(".editor-gutter .lineno").length >= 40);
}

async function captureState(page, viewport, stateId, manifest) {
  const state = matrixStates.find((item) => item.id === stateId);
  if (!state) {
    throw new Error(`unknown screenshot state: ${stateId}`);
  }
  await assertRequiredSelectors(page, state);
  await assertCoreLayout(page, stateId);
  const metrics = await collectMetrics(page);
  const relativePath = path.join("uiweb", viewport.id, `${stateId}.png`).replaceAll(path.sep, "/");
  const absolutePath = path.join(reportRoot, relativePath);
  fs.mkdirSync(path.dirname(absolutePath), { recursive: true });
  await page.screenshot({ path: absolutePath, fullPage: false });
  const stat = fs.statSync(absolutePath);
  if (stat.size < 8_000) {
    throw new Error(`screenshot looks blank or truncated: ${relativePath} (${stat.size} bytes)`);
  }
  manifest.captures.push({
    viewport: viewport.id,
    state: stateId,
    title: state.title,
    path: relativePath,
    bytes: stat.size,
    metrics,
  });
}

async function assertRequiredSelectors(page, state) {
  for (const selector of [".workbench-root", ".activity-bar", ".status-bar", ...state.required]) {
    const locator = page.locator(selector).first();
    await locator.waitFor({ state: "visible", timeout: 30_000 });
    const box = await locator.boundingBox();
    if (box === null || box.width < 1 || box.height < 1) {
      throw new Error(`${state.id}: selector has no visible box: ${selector}`);
    }
  }
  await assertStateHasRealContent(page, state);
}

async function assertStateHasRealContent(page, state) {
  const contentSelector = contentSelectorForState(state.id);
  if (contentSelector === null) {
    return;
  }
  const text = await page.locator(contentSelector).first().evaluate((element) => {
    if (element instanceof HTMLTextAreaElement || element instanceof HTMLInputElement) {
      return element.value;
    }
    return element.textContent ?? "";
  });
  if (text.trim().length < 8) {
    throw new Error(`${state.id}: ${contentSelector} does not contain real rendered content`);
  }
}

function contentSelectorForState(stateId) {
  if (stateId.startsWith("pipeline-stage-") && stateId !== "pipeline-stage-source") {
    return ".visual-canvas";
  }
  if (stateId.startsWith("debug-") && stateId !== "debug-before-start") {
    return ".debug-pane";
  }
  if (stateId === "settings") {
    return ".settings-scroll";
  }
  if (stateId === "info") {
    return ".info-scroll";
  }
  if (stateId.startsWith("source-") || stateId === "pipeline-before-start" || stateId === "pipeline-stage-source") {
    return "textarea.source-editor-input";
  }
  return null;
}

async function assertCoreLayout(page, stateId) {
  const metrics = await collectMetrics(page);
  assertNear(metrics[".activity-bar"]?.width, 48, 1, `${stateId} activity bar width`);
  assertNear(metrics[".status-bar"]?.height, 22, 1, `${stateId} status bar height`);
  if (metrics[".debug-view-selector"]?.visible) {
    assertNear(metrics[".debug-view-selector"].width, 88, 2, `${stateId} debug view selector width`);
  }
  if (metrics[".editor-gutter-column"]?.visible) {
    assertNear(metrics[".editor-gutter-column"].width, 82, 2, `${stateId} editor gutter width`);
  }
}

async function collectMetrics(page) {
  return page.evaluate((selectors) => {
    const output = {};
    for (const selector of selectors) {
      const element = document.querySelector(selector);
      if (!element) {
        output[selector] = { visible: false };
        continue;
      }
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      output[selector] = {
        visible: rect.width > 0 && rect.height > 0 && style.visibility !== "hidden" && style.display !== "none",
        x: Math.round(rect.x * 100) / 100,
        y: Math.round(rect.y * 100) / 100,
        width: Math.round(rect.width * 100) / 100,
        height: Math.round(rect.height * 100) / 100,
        display: style.display,
        overflow: `${style.overflowX}/${style.overflowY}`,
        textLength: (element.textContent ?? "").trim().length,
      };
    }
    return output;
  }, metricSelectors);
}

async function waitForStageView(page, stageId) {
  if (stageId === "source") {
    await page.locator("textarea.source-editor-input").first().waitFor({ state: "visible" });
    return;
  }
  await page.locator(".visual-canvas").waitFor({ state: "visible" });
  const expected = {
    preprocess: "预编译",
    lexer: "词法分析",
    parser: "语法分析",
    semantic: "语义分析",
    ir: "IR 降级",
    codegen: "代码生成",
    toolchain: "工具链",
    execution: "执行",
  }[stageId];
  if (expected) {
    await page.locator(".pane-head", { hasText: expected }).waitFor({ state: "visible" });
  }
}

async function clickControlForApi(page, baseUrl, scopeSelector, name, method, pathFragments, timeoutMillis = 30_000) {
  const button = page.locator(`${scopeSelector} button`).filter({ hasText: exactText(name) }).first();
  await button.waitFor({ state: "visible" });
  await page.waitForFunction((node) => !node.disabled, await button.elementHandle());
  await Promise.all([
    waitForApiResponse(page, baseUrl, method, pathFragments, timeoutMillis),
    button.click(),
  ]);
}

async function waitForApiResponse(page, baseUrl, method, pathFragments, timeoutMillis = 30_000) {
  return page.waitForResponse((response) => {
    if (!response.url().startsWith(baseUrl)) {
      return false;
    }
    if (response.request().method() !== method) {
      return false;
    }
    const pathName = new URL(response.url()).pathname;
    return response.ok() && pathFragments.every((fragment) => pathName.includes(fragment));
  }, { timeout: timeoutMillis });
}

async function startViteServer(uiApiBaseUrl) {
  const port = await findFreePort();
  const child = spawn("npm", ["run", "dev", "--", "--host", "127.0.0.1", "--port", String(port), "--strictPort"], {
    cwd: uiwebRoot,
    env: {
      ...process.env,
      VITE_MINIC_UIAPI_BASE_URL: uiApiBaseUrl,
    },
    shell: process.platform === "win32",
    stdio: ["ignore", "pipe", "pipe"],
    windowsHide: true,
  });
  let output = "";
  child.stdout.setEncoding("utf8");
  child.stderr.setEncoding("utf8");
  child.stdout.on("data", (chunk) => {
    output += chunk;
  });
  child.stderr.on("data", (chunk) => {
    output += chunk;
  });
  const baseUrl = `http://127.0.0.1:${port}`;
  await waitForHttp(baseUrl, 60_000, () => output, child);
  return {
    baseUrl,
    stop: () => stopProcessTree(child),
  };
}

async function waitForHttp(url, timeoutMillis, output, child) {
  const deadline = Date.now() + timeoutMillis;
  let lastError = null;
  while (Date.now() < deadline) {
    if (child.exitCode !== null) {
      throw new Error(`process exited before ${url} became ready\n${output()}`);
    }
    try {
      const response = await fetch(url);
      if (response.ok) {
        return;
      }
      lastError = new Error(`HTTP ${response.status} from ${url}`);
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolve) => setTimeout(resolve, 300));
  }
  throw new Error(`timed out waiting for ${url}${lastError ? `\nlast error: ${lastError.message}` : ""}\n${output()}`);
}

function writeReportHtml(manifest) {
  const rows = manifest.captures.map((capture) => `
    <article class="capture">
      <h2>${escapeHtml(capture.viewport)} · ${escapeHtml(capture.title)}</h2>
      <div class="pair">
        ${referenceImage(capture)}
        <figure>
          <figcaption>UIWeb</figcaption>
          <img src="${escapeHtml(capture.path)}" alt="${escapeHtml(capture.state)} UIWeb">
        </figure>
      </div>
      <p>${escapeHtml(capture.state)} · ${capture.bytes} bytes</p>
    </article>`).join("\n");
  const html = `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <title>MiniC UIWeb Screenshot Parity Report</title>
  <style>
    body { margin: 0; font-family: Segoe UI, Arial, sans-serif; background: #111; color: #eee; }
    header { padding: 20px; border-bottom: 1px solid #333; }
    main { display: grid; grid-template-columns: repeat(auto-fill, minmax(520px, 1fr)); gap: 16px; padding: 16px; }
    .capture { border: 1px solid #333; background: #1b1b1b; padding: 12px; }
    h1 { margin: 0 0 8px; font-size: 20px; }
    h2 { margin: 0 0 8px; font-size: 14px; }
    .pair { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
    figure { margin: 0; }
    figcaption { color: #ddd; font-size: 12px; margin-bottom: 4px; }
    img { width: 100%; border: 1px solid #444; background: #000; }
    p { color: #aaa; font-size: 12px; }
  </style>
</head>
<body>
  <header>
    <h1>MiniC UIWeb Screenshot Parity Report</h1>
    <p>Generated at ${escapeHtml(manifest.generatedAt)} · ${manifest.captures.length} captures</p>
  </header>
  <main>${rows}</main>
</body>
</html>`;
  fs.writeFileSync(path.join(reportRoot, "index.html"), html);
}

function referenceImage(capture) {
  const referencePath = `uilocal/${capture.viewport}/${capture.state}.png`;
  const absolutePath = path.join(reportRoot, referencePath);
  if (!fs.existsSync(absolutePath)) {
    return `<figure><figcaption>JavaFX reference missing</figcaption><div class="missing"></div></figure>`;
  }
  return `<figure>
          <figcaption>JavaFX</figcaption>
          <img src="${escapeHtml(referencePath)}" alt="${escapeHtml(capture.state)} JavaFX">
        </figure>`;
}

function cleanUiwebReportRoot() {
  const resolved = path.resolve(screenshotRoot);
  const allowedParent = path.resolve(projectRoot, "uiweb-render-check");
  if (!resolved.startsWith(allowedParent) || !resolved.endsWith(path.join("parity-report", "uiweb"))) {
    throw new Error(`refusing to remove unexpected report path: ${resolved}`);
  }
  fs.rmSync(resolved, { recursive: true, force: true });
  for (const fileName of ["manifest.json", "index.html", "verification.json"]) {
    fs.rmSync(path.join(reportRoot, fileName), { force: true });
  }
  fs.mkdirSync(screenshotRoot, { recursive: true });
}

function assertNear(actual, expected, tolerance, label) {
  if (typeof actual !== "number" || Math.abs(actual - expected) > tolerance) {
    throw new Error(`${label} expected ${expected} +/- ${tolerance}, got ${actual}`);
  }
}

function exactText(text) {
  return new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`);
}

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function escapeHtml(text) {
  return String(text)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

if (import.meta.url === `file://${process.argv[1]?.replace(/\\/g, "/")}`) {
  await captureUiwebScreenshots();
  console.log(`UIWeb screenshots captured at ${reportRoot}`);
}
