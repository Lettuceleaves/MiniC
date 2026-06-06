import { spawn } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "@playwright/test";
import { findFreePort, startUiApiServer, stopProcessTree, waitForJson } from "./run-uiapi-server.mjs";

const toolDir = path.dirname(fileURLToPath(import.meta.url));
const uiwebRoot = path.resolve(toolDir, "..");

const requiredApiHits = [
  "POST /api/realtime/analyze",
  "POST /api/observation/sessions",
  "POST /api/observation/*/source",
  "POST /api/observation/*/start",
  "POST /api/observation/*/next",
  "POST /api/observation/*/next-stage",
  "GET /api/observation/*/visual/lexer",
  "GET /api/observation/*/visual/ast",
  "GET /api/observation/*/visual/semantic",
  "GET /api/observation/*/visual/codegen",
  "POST /api/debug/sessions",
  "POST /api/debug/*/source",
  "POST /api/debug/*/start",
  "POST /api/debug/*/breakpoints/12",
  "POST /api/debug/*/step-into",
  "POST /api/debug/*/step-over",
  "POST /api/debug/*/run-to-breakpoint",
  "POST /api/debug/*/step-back",
  "GET /api/debug/*/metadata",
  "GET /api/debug/*/data-structure",
  "GET /api/debug/*/ast",
  "GET /api/debug/*/ir",
  "GET /api/debug/*/asm",
  "POST /api/realtime/tokenize",
];

const forbiddenPageText = [
  "UIWeb 尚未连接",
  "noApiResult",
  "mock",
  "placeholder",
  "TODO",
];

const workflowSource = [
  "int add(int a, int b) {",
  "    return a + b;",
  "}",
  "",
  "int main() {",
  "    int x = 0;",
  "    x = add(x, 1);",
  "    x = add(x, 2);",
  "    x = add(x, 3);",
  "    x = add(x, 4);",
  "    x = add(x, 5);",
  "    x = add(x, 6);",
  "    x = add(x, 7);",
  "    x = add(x, 8);",
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

async function main() {
  const uiApi = await startUiApiServer();
  const vite = await startViteServer(uiApi.baseUrl);
  const browser = await chromium.launch();
  const apiHits = new Set();
  const pageErrors = [];
  const failedRequests = [];
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    await context.addInitScript(() => window.localStorage.clear());
    const page = await context.newPage();
    page.on("console", (message) => {
      if (message.type() === "error") {
        pageErrors.push(message.text());
      }
    });
    page.on("pageerror", (error) => pageErrors.push(error.message));
    page.on("requestfailed", (request) => {
      if (request.url().startsWith(uiApi.baseUrl)) {
        failedRequests.push(`${request.method()} ${request.url()} ${request.failure()?.errorText ?? ""}`.trim());
      }
    });
    page.on("response", (response) => {
      if (response.url().startsWith(uiApi.baseUrl) && response.ok()) {
        apiHits.add(apiPattern(response));
      }
    });

    await page.goto(vite.baseUrl, { waitUntil: "domcontentloaded" });
    await page.locator(".workbench-root").waitFor({ state: "visible" });
    await verifyEditor(page, uiApi.baseUrl);
    await verifyCompilerPipeline(page, uiApi.baseUrl);
    await verifyDebugger(page, uiApi.baseUrl);
    await verifySettingsAndInfo(page, uiApi.baseUrl);
    await verifyNoForbiddenPageText(page);
    verifyRequiredApiHits(apiHits);
    assert(pageErrors.length === 0, `browser console/page errors:\n${pageErrors.join("\n")}`);
    assert(failedRequests.length === 0, `failed UIAPI requests:\n${failedRequests.join("\n")}`);
    console.log(`uiweb runtime workflow verification passed (${apiHits.size} API patterns observed)`);
  } finally {
    await browser.close().catch(() => {});
    await vite.stop();
    await uiApi.stop();
  }
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
  await waitForVite(baseUrl, 60_000, () => output, child);
  return {
    baseUrl,
    stop: () => stopProcessTree(child),
  };
}

async function verifyEditor(page, baseUrl) {
  const editor = page.locator("textarea.source-editor-input").first();
  await editor.waitFor({ state: "visible" });
  await Promise.all([
    waitForApiResponse(page, baseUrl, "POST", ["/api/realtime/analyze"], 60_000),
    editor.fill(workflowSource),
  ]);
  await page.waitForFunction(() => document.querySelectorAll(".source-editor-render .token-keyword").length > 0);
  const lineCount = await page.locator(".editor-gutter .lineno").count();
  assert(lineCount >= 40, `source editor should expose JavaFX-like line numbers, got ${lineCount}`);
  const editorMetrics = await page.evaluate(() => {
    const lineNumber = document.querySelector(".editor-gutter .lineno")?.getBoundingClientRect();
    const renderLine = document.querySelector(".source-editor-render-line")?.getBoundingClientRect();
    const breakpoint = document.querySelector(".breakpoint-gutter")?.getBoundingClientRect();
    const gutterUserSelect = window.getComputedStyle(document.querySelector(".editor-gutter-column")).userSelect;
    const lineNumberUserSelect = window.getComputedStyle(document.querySelector(".editor-gutter .lineno")).userSelect;
    return lineNumber && renderLine && breakpoint
      ? {
          breakpointWidth: breakpoint.width,
          gapToLineNumber: lineNumber.left - breakpoint.right,
          gutterUserSelect,
          lineCenterY: lineNumber.top + lineNumber.height / 2,
          lineNumberUserSelect,
          renderCenterY: renderLine.top + renderLine.height / 2,
        }
      : null;
  });
  assert(editorMetrics !== null, "source editor should expose gutter and rendered line metrics");
  assert(Math.abs(editorMetrics.lineCenterY - editorMetrics.renderCenterY) <= 1.5, `line number and text should align vertically (${JSON.stringify(editorMetrics)})`);
  assert(editorMetrics.breakpointWidth >= 22, `breakpoint gutter should be large enough, got ${editorMetrics.breakpointWidth}`);
  assert(editorMetrics.gapToLineNumber <= 4, `breakpoint should sit next to line number, got gap ${editorMetrics.gapToLineNumber}`);
  assert(editorMetrics.gutterUserSelect === "none", `editor gutter must not be selectable, got ${editorMetrics.gutterUserSelect}`);
  assert(editorMetrics.lineNumberUserSelect === "none", `line numbers must not be selectable, got ${editorMetrics.lineNumberUserSelect}`);
  const initialScrollTop = await editor.evaluate((node) => node.scrollTop);
  await editor.evaluate((node) => {
    node.scrollTop = 420;
    node.dispatchEvent(new Event("scroll", { bubbles: true }));
  });
  await page.waitForFunction(
    (node) => node.scrollTop > 0,
    await editor.elementHandle(),
  );
  const scrolledTop = await editor.evaluate((node) => node.scrollTop);
  assert(scrolledTop > initialScrollTop, `source editor should scroll vertically (${initialScrollTop} -> ${scrolledTop})`);
  await page.waitForFunction(() => {
    const transform = window.getComputedStyle(document.querySelector(".source-editor-render")).transform;
    return transform !== "none" && transform.includes("-");
  });
  await editor.focus();
  await page.keyboard.press("Control+A");
  await page.waitForFunction(() => document.querySelectorAll(".source-editor-render .selection").length > 0);
  const nativeSelectionBackground = await editor.evaluate((node) => window.getComputedStyle(node, "::selection").backgroundColor);
  assert(
    nativeSelectionBackground === "rgba(0, 0, 0, 0)" || nativeSelectionBackground === "transparent",
    `native textarea selection should be hidden, got ${nativeSelectionBackground}`,
  );
  const keywordCount = await page.locator(".source-editor-render .token-keyword").count();
  assert(keywordCount > 0, "source editor should render UIAPI token highlighting");
}

async function verifyCompilerPipeline(page, baseUrl) {
  await Promise.all([
    waitForApiResponse(page, baseUrl, "POST", ["/api/observation/", "/start"]),
    page.getByRole("button", { name: "开始", exact: true }).click(),
  ]);
  await waitForApiResponse(page, baseUrl, "GET", ["/api/observation/", "/visual/lexer"]);
  await page.locator(".inspector").waitFor({ state: "visible" });

  await clickControlForApi(page, baseUrl, ".inspector", "下一步", "POST", ["/api/observation/", "/next"]);
  await page.locator(".visual-canvas").waitFor({ state: "visible" });
  await clickControlForApi(page, baseUrl, ".inspector", "下一阶段", "POST", ["/api/observation/", "/next-stage"]);
  await clickControlForApi(page, baseUrl, ".inspector", "到执行", "POST", ["/api/observation/", "/next-stage"], 90_000);
  await page.waitForFunction(() => {
    const execution = document.querySelector('.stage-card[data-stage-id="execution"]');
    return execution !== null && !execution.hasAttribute("disabled");
  });

  const stageExpectations = [
    ["preprocess", "预编译"],
    ["lexer", "词法分析"],
    ["parser", "语法分析"],
    ["semantic", "语义分析"],
    ["ir", "IR 降级"],
    ["codegen", "代码生成"],
    ["toolchain", "工具链"],
    ["execution", "执行"],
  ];
  for (const [stageId, stageTitle] of stageExpectations) {
    const card = page.locator(`.stage-card[data-stage-id="${stageId}"]`);
    await card.waitFor({ state: "visible" });
    assert(!(await card.isDisabled()), `stage card ${stageId} should be enabled after running to execution`);
    await card.click();
    await page.locator(".pane-head", { hasText: stageTitle }).waitFor({ state: "visible" });
    const visualText = await page.locator(".visual-canvas").innerText();
    assert(visualText.trim().length > stageTitle.length, `stage ${stageId} should render non-empty visual content`);
    if (stageId === "lexer") {
      assert(
        (await page.locator(".source-flow-text .token-keyword, .source-flow-text .mc-text-code-keyword").count()) > 0,
        "pipeline source flow should syntax-highlight source tokens",
      );
      await verifyPipelineInspector(page, ".token-row[role='button']", "Token");
    }
    if (stageId === "parser") {
      await verifyPipelineInspector(page, ".ast-graph g[role='button']", "AST 节点");
    }
  }
}

async function verifyDebugger(page, baseUrl) {
  await page.getByRole("button", { name: "调试", exact: true }).click();
  await page.locator(".debug-pane").waitFor({ state: "visible" });
  const breakpoint = page.locator('button[aria-label="设置第 12 行断点"]').first();
  await breakpoint.waitFor({ state: "visible" });
  await breakpoint.click();
  await page.locator('button[aria-label="清除第 12 行断点"].active').first().waitFor({ state: "visible" });

  await Promise.all([
    waitForApiResponse(page, baseUrl, "POST", ["/api/debug/", "/start"]),
    waitForApiResponse(page, baseUrl, "POST", ["/api/debug/", "/breakpoints/12"]),
    waitForApiResponse(page, baseUrl, "GET", ["/api/debug/", "/metadata"]),
    waitForApiResponse(page, baseUrl, "GET", ["/api/debug/", "/data-structure"]),
    waitForApiResponse(page, baseUrl, "GET", ["/api/debug/", "/ast"]),
    waitForApiResponse(page, baseUrl, "GET", ["/api/debug/", "/ir"]),
    waitForApiResponse(page, baseUrl, "GET", ["/api/debug/", "/asm"]),
    page.getByRole("button", { name: "从头开始", exact: true }).click(),
  ]);
  await page.locator(".debug-status").waitFor({ state: "visible" });
  const status = await page.locator(".debug-status").innerText();
  assert(!status.includes("未启动"), `debug status should be started, got: ${status}`);

  await clickControlForApi(page, baseUrl, ".debug-pane", "下一句", "POST", ["/api/debug/", "/step-into"]);
  await clickControlForApi(page, baseUrl, ".debug-pane", "本层下一句", "POST", ["/api/debug/", "/step-over"]);
  await clickControlForApi(page, baseUrl, ".debug-pane", "下个断点", "POST", ["/api/debug/", "/run-to-breakpoint"]);
  await clickControlForApi(page, baseUrl, ".debug-pane", "上一句", "POST", ["/api/debug/", "/step-back"]);

  const viewExpectations = [
    ["元数据", "调用栈"],
    ["数据结构", "runtime"],
    ["AST", "当前 AST 节点"],
    ["IR", "IR"],
    ["ASM", "ASM"],
  ];
  for (const [viewName, expectedText] of viewExpectations) {
    await page.locator(".debug-view-button", { hasText: viewName }).click();
    await page.locator(".debug-view-content").waitFor({ state: "visible" });
    await page.waitForFunction(
      ([selector, expected]) => document.querySelector(selector)?.textContent?.includes(expected) ?? false,
      [".debug-view-content", expectedText],
    );
    const viewText = await page.locator(".debug-view-content").innerText();
    assert(viewText.trim().length > expectedText.length, `debug view ${viewName} should render real DTO content`);
    if (viewName === "数据结构" && !viewText.includes("(empty)")) {
      const diagramElements = await page.locator(".debug-visual-diagram .debug-graph-node, .debug-visual-diagram .debug-array-cell, .debug-visual-diagram .debug-null-node").count();
      assert(diagramElements > 0, "debug data structure view should render SVG graph or array elements");
    }
    if (viewName === "IR" || viewName === "ASM") {
      const highlightedSegments = await page.locator(".debug-code-row .debug-code-text [class*='mc-text-code'], .debug-code-row .debug-code-text .token-keyword").count();
      assert(highlightedSegments > 0, `debug ${viewName} view should syntax-highlight code rows`);
    }
  }
}

async function verifyPipelineInspector(page, selector, expectedTitle) {
  const target = page.locator(selector).first();
  await target.waitFor({ state: "visible" });
  await target.click({ force: true });
  await page.locator(".bottom-panel.expanded").waitFor({ state: "visible" });
  await page.locator(".bottom-panel.expanded .hover-inspector-title", { hasText: expectedTitle }).first().waitFor({ state: "visible" });
  const bottomText = await page.locator(".bottom-panel.expanded").innerText();
  assert(bottomText.includes("说明"), `bottom inspector should include explanation area after clicking ${selector}`);
  assert(bottomText.includes("源码范围") || bottomText.includes("offset"), `bottom inspector should include source/range details after clicking ${selector}`);
  assert((await page.locator(".bottom-panel.expanded .hover-source-row").count()) > 0, `bottom inspector should render source rows after clicking ${selector}`);
  assert(
    (await page.locator(".bottom-panel.expanded .hover-inspector-meta [class*='mc-text-code'], .bottom-panel.expanded .hover-inspector-meta .token-keyword").count()) > 0,
    `bottom inspector metadata should render highlighted text after clicking ${selector}`,
  );
  assert(
    (await page.locator(".bottom-panel.expanded .hover-explanation-scroll [class*='mc-text-code'], .bottom-panel.expanded .hover-explanation-scroll .token-keyword").count()) > 0,
    `bottom inspector explanation should render highlighted text after clicking ${selector}`,
  );
}

async function verifySettingsAndInfo(page, baseUrl) {
  await page.getByRole("button", { name: "设置", exact: true }).click();
  await page.locator(".settings-scroll").waitFor({ state: "visible" });
  await page.locator("#minic-theme").waitFor({ state: "visible" });
  await page.locator(".key-binding-button").first().waitFor({ state: "visible" });
  await verifyKeyBindingCapture(page);

  const tokenizeWait = waitForApiResponse(page, baseUrl, "POST", ["/api/realtime/tokenize"], 30_000);
  await page.getByRole("button", { name: "信息", exact: true }).click();
  await page.locator(".info-scroll").waitFor({ state: "visible" });
  await tokenizeWait;
  await page.locator(".info-code-block .token-keyword").first().waitFor({ state: "visible" });
}

async function verifyKeyBindingCapture(page) {
  const bindingButtons = page.locator(".key-binding-button");
  const keyboardButton = bindingButtons.nth(0);
  await keyboardButton.click();
  await page.keyboard.press("Control+Shift+KeyK");
  await page.waitForFunction(
    (node) => node.textContent?.includes("Ctrl+Shift+K") ?? false,
    await keyboardButton.elementHandle(),
  );
  await page.keyboard.press("Enter");
  await page.waitForFunction(
    (node) => node.textContent?.trim() === "Ctrl+Shift+K",
    await keyboardButton.elementHandle(),
  );

  const mouseButton = bindingButtons.nth(1);
  await mouseButton.click();
  const box = await mouseButton.boundingBox();
  assert(box !== null, "second key-binding button should have a bounding box");
  await page.keyboard.down("Control");
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2, { button: "right" });
  await page.keyboard.up("Control");
  await page.waitForFunction(
    (node) => node.textContent?.includes("Ctrl+MouseRight") ?? false,
    await mouseButton.elementHandle(),
  );
  await page.keyboard.press("Enter");
  await page.waitForFunction(
    (node) => node.textContent?.trim() === "Ctrl+MouseRight",
    await mouseButton.elementHandle(),
  );
}

async function verifyNoForbiddenPageText(page) {
  const text = await page.locator("body").innerText();
  for (const forbidden of forbiddenPageText) {
    assert(!text.includes(forbidden), `page must not expose forbidden fallback text: ${forbidden}`);
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

async function waitForVite(url, timeoutMillis, output, child) {
  const deadline = Date.now() + timeoutMillis;
  let lastError = null;
  while (Date.now() < deadline) {
    if (child.exitCode !== null) {
      throw new Error(`vite exited before ${url} became ready\n${output()}`);
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

function verifyRequiredApiHits(apiHits) {
  const missing = requiredApiHits.filter((hit) => !apiHits.has(hit));
  assert(missing.length === 0, `missing required UIAPI interactions:\n${missing.join("\n")}\nobserved:\n${[...apiHits].sort().join("\n")}`);
}

function apiPattern(response) {
  const method = response.request().method();
  const parts = new URL(response.url()).pathname.split("/").filter(Boolean);
  if (parts[0] === "api" && (parts[1] === "observation" || parts[1] === "debug") && parts.length > 3) {
    return `${method} /api/${parts[1]}/*/${parts.slice(3).join("/")}`;
  }
  return `${method} /${parts.join("/")}`;
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function exactText(text) {
  return new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`);
}

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

await main();
