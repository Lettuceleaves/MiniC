import { spawn } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "@playwright/test";
import { findFreePort, startUiApiServer, stopProcessTree } from "./run-uiapi-server.mjs";

const toolDir = path.dirname(fileURLToPath(import.meta.url));
const uiwebRoot = path.resolve(toolDir, "..");

async function main() {
  await verifyOfflineUiApiState();
  await verifyRealtimeFailureDoesNotBlockStart();
  await verifyDebugFailureIsModeled();
  await verifyTokenizeFailureFallsBack();
  console.log("uiweb failure resilience verification passed");
}

async function verifyOfflineUiApiState() {
  const offlineBaseUrl = `http://127.0.0.1:${await findFreePort()}`;
  let vite = null;
  let browser = null;
  try {
    vite = await startViteServer(offlineBaseUrl);
    browser = await chromium.launch();
    const page = await newCheckedPage(browser);
    await page.goto(vite.baseUrl, { waitUntil: "domcontentloaded" });
    await page.locator(".workbench-root").waitFor({ state: "visible" });
    await page.locator("textarea.source-editor-input").first().waitFor({ state: "visible" });
    await page.waitForFunction(() => document.querySelector(".status-bar")?.textContent?.includes("失败") ?? false);
    assertNoConsoleProblems(page, [/ERR_CONNECTION_REFUSED/]);
    await page.close();
  } finally {
    await browser?.close().catch(() => {});
    await vite?.stop();
  }
}

async function verifyRealtimeFailureDoesNotBlockStart() {
  let uiApi = null;
  let vite = null;
  let browser = null;
  try {
    uiApi = await startUiApiServer();
    vite = await startViteServer(uiApi.baseUrl);
    browser = await chromium.launch();
    const page = await newCheckedPage(browser);
    let analyzeRequests = 0;
    await page.route(`${uiApi.baseUrl}/api/realtime/analyze`, (route) => {
      analyzeRequests += 1;
      return route.fulfill({
        status: 500,
        contentType: "application/json",
        body: JSON.stringify({ status: 500, method: "POST", path: "/api/realtime/analyze", message: "forced realtime failure" }),
      });
    });
    await page.goto(vite.baseUrl, { waitUntil: "domcontentloaded" });
    await page.locator(".workbench-root").waitFor({ state: "visible" });
    await Promise.all([
      waitForApiResponse(page, uiApi.baseUrl, "POST", ["/api/observation/", "/start"]),
      page.getByRole("button", { name: "开始", exact: true }).click(),
    ]);
    await page.locator(".inspector").waitFor({ state: "visible" });
    const bodyText = await page.locator("body").innerText();
    assert(analyzeRequests > 0, "realtime analyze failure route should be exercised");
    assert(bodyText.includes("当前状态"), "compiler pipeline should start even when realtime analysis fails");
    assertNoConsoleProblems(page, [/Failed to load resource: the server responded with a status of 500/]);
    await page.close();
  } finally {
    await browser?.close().catch(() => {});
    await vite?.stop();
    await uiApi?.stop();
  }
}

async function verifyDebugFailureIsModeled() {
  let uiApi = null;
  let vite = null;
  let browser = null;
  try {
    uiApi = await startUiApiServer();
    vite = await startViteServer(uiApi.baseUrl);
    browser = await chromium.launch();
    const page = await newCheckedPage(browser);
    await page.route(new RegExp(`${escapeRegExp(uiApi.baseUrl)}/api/debug/[^/]+/start$`), (route) => route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({ status: 409, method: "POST", path: new URL(route.request().url()).pathname, message: "forced debug failure" }),
    }));
    await page.goto(vite.baseUrl, { waitUntil: "domcontentloaded" });
    await page.locator(".workbench-root").waitFor({ state: "visible" });
    await page.getByRole("button", { name: "调试", exact: true }).click();
    await page.locator(".debug-pane").waitFor({ state: "visible" });
    await page.getByRole("button", { name: "从头开始", exact: true }).click();
    await page.waitForFunction(() => document.querySelector(".status-bar")?.textContent?.includes("forced debug failure") ?? false);
    assertNoConsoleProblems(page, [/Failed to load resource: the server responded with a status of 409/]);
    await page.close();
  } finally {
    await browser?.close().catch(() => {});
    await vite?.stop();
    await uiApi?.stop();
  }
}

async function verifyTokenizeFailureFallsBack() {
  let uiApi = null;
  let vite = null;
  let browser = null;
  try {
    uiApi = await startUiApiServer();
    vite = await startViteServer(uiApi.baseUrl);
    browser = await chromium.launch();
    const page = await newCheckedPage(browser);
    let tokenizeRequests = 0;
    await page.route(`${uiApi.baseUrl}/api/realtime/tokenize`, (route) => {
      tokenizeRequests += 1;
      return route.fulfill({
        status: 500,
        contentType: "application/json",
        body: JSON.stringify({ status: 500, method: "POST", path: "/api/realtime/tokenize", message: "forced tokenize failure" }),
      });
    });
    await page.goto(vite.baseUrl, { waitUntil: "domcontentloaded" });
    await page.locator(".workbench-root").waitFor({ state: "visible" });
    await page.getByRole("button", { name: "信息", exact: true }).click();
    await page.locator(".info-scroll").waitFor({ state: "visible" });
    await page.waitForFunction(() => document.querySelector(".info-code-block") !== null);
    await page.waitForTimeout(500);
    assert(tokenizeRequests > 0, "info code blocks should request UIAPI tokenization");
    assertNoConsoleProblems(page, [/Failed to load resource: the server responded with a status of 500/]);
    await page.close();
  } finally {
    await browser?.close().catch(() => {});
    await vite?.stop();
    await uiApi?.stop();
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
  try {
    await waitForVite(baseUrl, 60_000, () => output, child);
  } catch (error) {
    await stopProcessTree(child);
    throw error;
  }
  return {
    baseUrl,
    stop: () => stopProcessTree(child),
  };
}

async function newCheckedPage(browser) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 820 } });
  await context.addInitScript(() => {
    window.localStorage.clear();
  });
  const page = await context.newPage();
  const consoleMessages = [];
  page.consoleMessages = consoleMessages;
  page.on("console", (message) => {
    if (message.type() === "error" || message.type() === "warning") {
      consoleMessages.push(`${message.type()}: ${message.text()}`);
    }
  });
  page.on("pageerror", (error) => consoleMessages.push(`pageerror: ${error.message}`));
  return page;
}

function assertNoConsoleProblems(page, allowedPatterns = []) {
  const messages = page.consoleMessages ?? [];
  const unexpectedMessages = messages.filter((message) => !allowedPatterns.some((pattern) => pattern.test(message)));
  assert(unexpectedMessages.length === 0, `browser console/page problems:\n${unexpectedMessages.join("\n")}`);
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

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

await main();
