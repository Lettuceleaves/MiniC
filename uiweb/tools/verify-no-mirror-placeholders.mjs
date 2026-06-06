import fs from "node:fs";
import path from "node:path";
import ts from "typescript";

const projectRoot = path.resolve(import.meta.dirname, "..", "..");
const srcRoot = path.join(projectRoot, "uiweb", "src");
const allowedHelperFile = path.join(srcRoot, "translation", "createMirrorComponent.tsx");

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

function hasExportModifier(node) {
  return node.modifiers?.some((modifier) => modifier.kind === ts.SyntaxKind.ExportKeyword) ?? false;
}

function isCreateMirrorComponentCall(node) {
  return ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === "createMirrorComponent";
}

function projectPath(file) {
  return path.relative(projectRoot, file).replaceAll(path.sep, "/");
}

function location(sourceFile, node) {
  const { line, character } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
  return { line: line + 1, column: character + 1 };
}

function addFailure(failures, seen, sourceFile, file, name, node, detail = "exported placeholder") {
  const loc = location(sourceFile, node);
  const key = `${file}:${name}:${loc.line}:${loc.column}`;
  if (seen.has(key)) {
    return;
  }
  seen.add(key);
  failures.push({
    file: projectPath(file),
    line: loc.line,
    column: loc.column,
    name,
    detail,
  });
}

function verifyFile(file) {
  const text = fs.readFileSync(file, "utf8");
  const sourceFile = ts.createSourceFile(
    file,
    text,
    ts.ScriptTarget.Latest,
    true,
    file.endsWith(".tsx") ? ts.ScriptKind.TSX : ts.ScriptKind.TS,
  );
  const placeholders = new Map();
  const failures = [];
  const seen = new Set();

  for (const node of sourceFile.statements) {
    if (ts.isVariableStatement(node)) {
      for (const declaration of node.declarationList.declarations) {
        if (!declaration.initializer || !isCreateMirrorComponentCall(declaration.initializer)) {
          continue;
        }
        const name = declaration.name.getText(sourceFile);
        placeholders.set(name, { declaration, exported: hasExportModifier(node) });
        if (hasExportModifier(node)) {
          addFailure(failures, seen, sourceFile, file, name, declaration.initializer);
        }
      }
      continue;
    }

    if (ts.isExportAssignment(node) && !node.isExportEquals) {
      if (isCreateMirrorComponentCall(node.expression)) {
        addFailure(failures, seen, sourceFile, file, "default", node.expression, "default placeholder export");
      } else if (ts.isIdentifier(node.expression) && placeholders.has(node.expression.text)) {
        const placeholder = placeholders.get(node.expression.text);
        if (!placeholder.exported) {
          addFailure(
            failures,
            seen,
            sourceFile,
            file,
            "default",
            placeholder.declaration.initializer,
            `default export of ${node.expression.text}`,
          );
        }
      }
      continue;
    }

    if (ts.isExportDeclaration(node) && !node.moduleSpecifier && node.exportClause && ts.isNamedExports(node.exportClause)) {
      for (const element of node.exportClause.elements) {
        const localName = (element.propertyName ?? element.name).text;
        const placeholder = placeholders.get(localName);
        if (placeholder && !placeholder.exported) {
          addFailure(
            failures,
            seen,
            sourceFile,
            file,
            element.name.text,
            placeholder.declaration.initializer,
            `named export of ${localName}`,
          );
        }
      }
    }
  }

  return failures;
}

const sourceFiles = walkFiles(
  srcRoot,
  (file) => (file.endsWith(".ts") || file.endsWith(".tsx")) && path.resolve(file) !== path.resolve(allowedHelperFile),
);
const failures = sourceFiles.flatMap(verifyFile);

if (failures.length > 0) {
  console.error("Mirror placeholder exports are not allowed in UIWeb business components.");
  console.error("Replace these createMirrorComponent(...) exports with real implementations:");
  for (const failure of failures) {
    console.error(`- ${failure.file}:${failure.line}:${failure.column} ${failure.name} (${failure.detail})`);
  }
  console.error(`Allowed helper file: ${projectPath(allowedHelperFile)}`);
  process.exit(1);
}

console.log(`Verified ${sourceFiles.length} UIWeb source files contain no mirror placeholder exports.`);
