import { spawn } from "node:child_process";
import net from "node:net";
import path from "node:path";
import { fileURLToPath } from "node:url";

const toolDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(toolDir, "..", "..");

export async function findFreePort(host = "127.0.0.1") {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once("error", reject);
    server.listen(0, host, () => {
      const address = server.address();
      if (address === null || typeof address === "string") {
        server.close(() => reject(new Error("unable to allocate a TCP port")));
        return;
      }
      const { port } = address;
      server.close(() => resolve(port));
    });
  });
}

export async function startUiApiServer(options = {}) {
  const port = options.port ?? await findFreePort();
  const host = options.host ?? "127.0.0.1";
  const timeoutMillis = options.timeoutMillis ?? 120_000;
  const gradle = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
  const child = spawn(gradle, ["--no-daemon", "-q", `-PuiApiPort=${port}`, "runUiApi"], {
    cwd: repoRoot,
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

  const baseUrl = `http://${host}:${port}`;
  try {
    await waitForJson(`${baseUrl}/api/health`, timeoutMillis, () => output, child);
  } catch (error) {
    await stopProcessTree(child);
    throw error;
  }

  return {
    baseUrl,
    port,
    process: child,
    output: () => output,
    stop: () => stopProcessTree(child),
  };
}

export async function waitForJson(url, timeoutMillis = 30_000, output = () => "", child = null) {
  const deadline = Date.now() + timeoutMillis;
  let lastError = null;
  while (Date.now() < deadline) {
    if (child?.exitCode !== null) {
      throw new Error(`process exited before ${url} became ready\n${output()}`);
    }
    try {
      const response = await fetch(url);
      if (response.ok) {
        return await response.json();
      }
      lastError = new Error(`HTTP ${response.status} from ${url}`);
    } catch (error) {
      lastError = error;
    }
    await delay(500);
  }
  throw new Error(`timed out waiting for ${url}${lastError ? `\nlast error: ${lastError.message}` : ""}\n${output()}`);
}

export async function stopProcessTree(child) {
  if (child.exitCode !== null) {
    return;
  }
  if (process.platform === "win32" && child.pid !== undefined) {
    await new Promise((resolve) => {
      const killer = spawn("taskkill", ["/pid", String(child.pid), "/T", "/F"], {
        stdio: "ignore",
        windowsHide: true,
      });
      killer.once("close", resolve);
      killer.once("error", resolve);
    });
    return;
  }
  child.kill("SIGTERM");
  await Promise.race([
    new Promise((resolve) => child.once("close", resolve)),
    delay(3_000).then(() => {
      if (child.exitCode === null) {
        child.kill("SIGKILL");
      }
    }),
  ]);
}

function delay(millis) {
  return new Promise((resolve) => setTimeout(resolve, millis));
}

if (import.meta.url === `file://${process.argv[1]?.replace(/\\/g, "/")}`) {
  const portArg = process.argv.find((arg) => arg.startsWith("--port="));
  const port = portArg ? Number(portArg.slice("--port=".length)) : undefined;
  const server = await startUiApiServer({ port });
  console.log(server.baseUrl);
  process.on("SIGINT", async () => {
    await server.stop();
    process.exit(0);
  });
  process.on("SIGTERM", async () => {
    await server.stop();
    process.exit(0);
  });
}
