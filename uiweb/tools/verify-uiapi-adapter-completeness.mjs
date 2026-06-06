import fs from "node:fs";
import path from "node:path";

const projectRoot = path.resolve(import.meta.dirname, "..", "..");
const javaApiRoot = path.join(projectRoot, "src", "main", "java", "minic", "uiapi", "api");
const uiwebRoot = path.join(projectRoot, "uiweb", "src");

const apiChecks = [
  {
    javaFile: path.join(javaApiRoot, "MiniCObservationApi.java"),
    adapterName: "MiniCObservationApiAdapter",
    aliases: {},
  },
  {
    javaFile: path.join(javaApiRoot, "MiniCDebugApi.java"),
    adapterName: "MiniCDebugApiAdapter",
    aliases: {
      currentState: "state",
      astDebugView: "astView",
      irDebugView: "irView",
      asmDebugView: "asmView",
      dataStructureDebugView: "dataStructureView",
    },
  },
  {
    javaFile: path.join(javaApiRoot, "MiniCRealtimeAnalysisApi.java"),
    adapterName: "MiniCRealtimeAnalysisApiAdapter",
    aliases: {},
  },
];

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

function publicJavaMethods(file) {
  const source = fs.readFileSync(file, "utf8");
  return [...source.matchAll(/^\s*public\s+(?:static\s+)?(?:final\s+)?(?:[\w<>, ?.[\]]+\s+)?(\w+)\s*\(/gm)]
    .map((match) => match[1])
    .filter((name) => !name.startsWith("MiniC"));
}

function uiwebSourceText() {
  return walkFiles(uiwebRoot, (file) => file.endsWith(".ts") || file.endsWith(".tsx"))
    .map((file) => fs.readFileSync(file, "utf8"))
    .join("\n");
}

function adapterMethods(source, adapterName) {
  const match = source.match(new RegExp(`export\\s+interface\\s+${adapterName}\\s*\\{(?<body>[\\s\\S]*?)\\n\\}`, "m"));
  if (!match?.groups?.body) {
    return null;
  }
  return [...match.groups.body.matchAll(/^\s*(\w+)\s*\(/gm)].map((method) => method[1]);
}

const source = uiwebSourceText();
const failures = [];

for (const check of apiChecks) {
  const javaMethods = [...new Set(publicJavaMethods(check.javaFile))];
  const tsMethods = adapterMethods(source, check.adapterName);
  if (tsMethods === null) {
    failures.push(`${check.adapterName}: missing adapter interface`);
    continue;
  }
  const expectedTsMethods = javaMethods.map((method) => check.aliases[method] ?? method);
  for (const method of expectedTsMethods) {
    if (!tsMethods.includes(method)) {
      failures.push(`${check.adapterName}: missing method ${method}`);
    }
  }
  for (const method of tsMethods) {
    const canonical = Object.entries(check.aliases).find(([, alias]) => alias === method)?.[0] ?? method;
    if (!javaMethods.includes(canonical)) {
      failures.push(`${check.adapterName}: extra or unsupported method ${method}`);
    }
  }
}

if (failures.length > 0) {
  console.error("UIAPI adapter interfaces are incomplete.");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Verified UIAPI adapter interfaces cover all Java facade methods.");
