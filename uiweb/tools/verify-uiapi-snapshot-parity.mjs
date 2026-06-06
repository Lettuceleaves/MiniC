import fs from "node:fs";
import path from "node:path";

const projectRoot = path.resolve(import.meta.dirname, "..", "..");
const snapshotRoot = path.join(projectRoot, "build", "uiapi-snapshots");
const failures = [];

const requiredDtoTypes = new Set([
  "UiRealtimeAnalysisDto",
  "List<UiLexerTokenVisualDto>",
  "UiCurrentStateDto",
  "UiStageDataDto",
  "UiStageVisualDto",
  "UiGlobalDataDto",
  "UiDebugStateDto",
  "UiDebugMetadataViewDto",
  "UiDebugDataStructureViewDto",
  "UiDebugAstViewDto",
  "UiDebugIrViewDto",
  "UiDebugAsmViewDto",
]);

if (!fs.existsSync(snapshotRoot)) {
  fail("snapshot directory is missing; run .\\gradlew.bat test --tests minic.uiapi.MiniCUiApiSnapshotParityTest first");
} else {
  const files = fs.readdirSync(snapshotRoot).filter((file) => file.endsWith(".json")).sort();
  if (files.length < 12) {
    fail(`expected at least 12 snapshot files, found ${files.length}`);
  }
  const seenTypes = new Set();
  for (const file of files) {
    validateSnapshotFile(path.join(snapshotRoot, file), seenTypes);
  }
  for (const type of requiredDtoTypes) {
    if (!seenTypes.has(type)) {
      fail(`missing snapshot dtoType ${type}`);
    }
  }
}

if (failures.length > 0) {
  console.error("UIAPI snapshot parity verification failed.");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Verified UIAPI DTO snapshots can be consumed by UIWeb validators.");

function validateSnapshotFile(file, seenTypes) {
  let envelope;
  try {
    envelope = JSON.parse(fs.readFileSync(file, "utf8"));
  } catch (error) {
    fail(`${path.basename(file)}: invalid JSON: ${error.message}`);
    return;
  }
  const label = path.basename(file);
  expectObject(envelope, `${label}: envelope`);
  expectString(envelope.scenario, `${label}: scenario`);
  expectString(envelope.dtoType, `${label}: dtoType`);
  if (!("value" in envelope)) {
    fail(`${label}: missing value`);
    return;
  }
  seenTypes.add(envelope.dtoType);
  validateDto(envelope.dtoType, envelope.value, `${label}: value`, envelope.scenario);
}

function validateDto(dtoType, value, label, scenario) {
  switch (dtoType) {
    case "UiRealtimeAnalysisDto":
      validateRealtimeAnalysis(value, label, scenario);
      return;
    case "List<UiLexerTokenVisualDto>":
      expectArray(value, label).forEach((token, index) => validateLexerToken(token, `${label}[${index}]`));
      return;
    case "UiCurrentStateDto":
      validateCurrentState(value, label);
      return;
    case "UiStageDataDto":
      validateStageData(value, label);
      return;
    case "UiStageVisualDto":
      validateStageVisual(value, label, scenario);
      return;
    case "UiGlobalDataDto":
      validateGlobalData(value, label);
      return;
    case "UiDebugStateDto":
      validateDebugState(value, label);
      return;
    case "UiDebugMetadataViewDto":
      validateDebugMetadata(value, label);
      return;
    case "UiDebugDataStructureViewDto":
      validateDebugDataStructure(value, label);
      return;
    case "UiDebugAstViewDto":
      validateDebugAst(value, label);
      return;
    case "UiDebugIrViewDto":
      validateDebugIr(value, label);
      return;
    case "UiDebugAsmViewDto":
      validateDebugAsm(value, label);
      return;
    default:
      fail(`${label}: unsupported dtoType ${dtoType}`);
  }
}

function validateRealtimeAnalysis(value, label, scenario) {
  expectObject(value, label);
  expectString(value.sourceName, `${label}.sourceName`);
  expectString(value.sourceText, `${label}.sourceText`);
  expectNumber(value.version, `${label}.version`);
  expectArray(value.diagnostics, `${label}.diagnostics`).forEach((diagnostic, index) =>
    validateDiagnostic(diagnostic, `${label}.diagnostics[${index}]`));
  const tokens = expectArray(value.tokens, `${label}.tokens`);
  tokens.forEach((token, index) => validateLexerToken(token, `${label}.tokens[${index}]`));
  if (scenario.includes("valid") && tokens.length === 0) {
    fail(`${label}: valid realtime snapshot has no tokens`);
  }
  if (scenario.includes("diagnostic") && value.diagnostics.length === 0) {
    fail(`${label}: diagnostic realtime snapshot has no diagnostics`);
  }
}

function validateCurrentState(value, label) {
  expectObject(value, label);
  expectString(value.sourceName, `${label}.sourceName`);
  expectString(value.currentStage, `${label}.currentStage`);
  expectNumber(value.globalStepIndex, `${label}.globalStepIndex`);
  expectNumber(value.stageStepIndex, `${label}.stageStepIndex`);
  expectString(value.playbackMode, `${label}.playbackMode`);
  expectNumber(value.frameIntervalMillis, `${label}.frameIntervalMillis`);
  validateNullableSourceRange(value.sourceRange, `${label}.sourceRange`);
  expectString(value.title, `${label}.title`);
  expectString(value.description, `${label}.description`);
  expectArray(value.diagnostics, `${label}.diagnostics`).forEach((diagnostic, index) =>
    validateDiagnostic(diagnostic, `${label}.diagnostics[${index}]`));
  for (const key of ["canNext", "canPrevious", "canPlay", "canPlayFast", "canPause", "canReversePlay"]) {
    expectBoolean(value[key], `${label}.${key}`);
  }
}

function validateStageData(value, label) {
  expectObject(value, label);
  expectString(value.stage, `${label}.stage`);
  expectNumber(value.completedSteps, `${label}.completedSteps`);
  expectNumber(value.totalSteps, `${label}.totalSteps`);
  expectBoolean(value.completed, `${label}.completed`);
  expectArray(value.inputSummary, `${label}.inputSummary`).forEach((item, index) => expectString(item, `${label}.inputSummary[${index}]`));
  expectString(value.currentItem, `${label}.currentItem`);
  expectArray(value.accumulatedOutput, `${label}.accumulatedOutput`).forEach((item, index) =>
    expectString(item, `${label}.accumulatedOutput[${index}]`));
  expectArray(value.diagnostics, `${label}.diagnostics`).forEach((diagnostic, index) =>
    validateDiagnostic(diagnostic, `${label}.diagnostics[${index}]`));
}

function validateGlobalData(value, label) {
  expectObject(value, label);
  expectString(value.source, `${label}.source`);
  for (const key of [
    "stageSummaries",
    "diagnostics",
    "preprocessSummary",
    "tokenSummary",
    "astSummary",
    "semanticSummary",
    "irSummary",
    "assemblySummary",
    "artifactSummary",
    "executionInputSummary",
    "executionOutputSummary",
  ]) {
    expectArray(value[key], `${label}.${key}`);
  }
}

function validateStageVisual(value, label, scenario) {
  expectObject(value, label);
  expectString(value.stage, `${label}.stage`);
  expectString(value.visualType, `${label}.visualType`);
  expectString(value.sourceText, `${label}.sourceText`);
  expectArray(value.genericItems, `${label}.genericItems`);
  const tokens = expectArray(value.lexerTokens, `${label}.lexerTokens`);
  tokens.forEach((token, index) => validateLexerToken(token, `${label}.lexerTokens[${index}]`));
  validateNullableAstNode(value.astRoot, `${label}.astRoot`);
  validateNullableSemanticScope(value.semanticRoot, `${label}.semanticRoot`);
  expectBoolean(value.semanticEdgesPointChildToParent, `${label}.semanticEdgesPointChildToParent`);
  expectArray(value.irLines, `${label}.irLines`).forEach((line, index) => validateIrLine(line, `${label}.irLines[${index}]`));
  expectArray(value.assemblyLines, `${label}.assemblyLines`).forEach((line, index) =>
    validateAssemblyLine(line, `${label}.assemblyLines[${index}]`));
  if (scenario.includes("lexer") && tokens.length === 0) {
    fail(`${label}: lexer visual snapshot has no tokens`);
  }
  if (scenario.includes("ast") && value.astRoot === null) {
    fail(`${label}: AST visual snapshot has no root`);
  }
  if (scenario.includes("semantic") && value.semanticRoot === null) {
    fail(`${label}: semantic visual snapshot has no scope root`);
  }
  if (scenario.includes("codegen") && value.assemblyLines.length === 0) {
    fail(`${label}: codegen visual snapshot has no assembly lines`);
  }
}

function validateDebugState(value, label) {
  expectObject(value, label);
  expectString(value.sourceName, `${label}.sourceName`);
  expectString(value.executionState, `${label}.executionState`);
  validateDebugSnapshot(value.currentSnapshot, `${label}.currentSnapshot`);
  const snapshots = expectArray(value.snapshots, `${label}.snapshots`);
  snapshots.forEach((snapshot, index) => validateDebugSnapshot(snapshot, `${label}.snapshots[${index}]`));
  if (snapshots.length === 0) {
    fail(`${label}: debug state has no snapshots`);
  }
  expectArray(value.events, `${label}.events`).forEach((event, index) => validateDebugEvent(event, `${label}.events[${index}]`));
  expectArray(value.breakpoints, `${label}.breakpoints`).forEach((breakpoint, index) =>
    validateBreakpoint(breakpoint, `${label}.breakpoints[${index}]`));
}

function validateDebugMetadata(value, label) {
  expectObject(value, label);
  expectString(value.executionState, `${label}.executionState`);
  expectString(value.stopReason, `${label}.stopReason`);
  expectString(value.currentFunction, `${label}.currentFunction`);
  validateNullableSourceSpan(value.currentSourceRange, `${label}.currentSourceRange`);
  expectArray(value.callStack, `${label}.callStack`).forEach((frame, index) => validateFrame(frame, `${label}.callStack[${index}]`));
  expectArray(value.variables, `${label}.variables`).forEach((variable, index) => validateVariable(variable, `${label}.variables[${index}]`));
  expectString(value.stdout, `${label}.stdout`);
  expectString(value.stderr, `${label}.stderr`);
  expectArray(value.breakpoints, `${label}.breakpoints`).forEach((breakpoint, index) =>
    validateBreakpoint(breakpoint, `${label}.breakpoints[${index}]`));
  expectArray(value.events, `${label}.events`).forEach((event, index) => validateDebugEvent(event, `${label}.events[${index}]`));
  expectArray(value.timeline, `${label}.timeline`).forEach((item, index) => validateTimelineItem(item, `${label}.timeline[${index}]`));
}

function validateDebugDataStructure(value, label) {
  expectObject(value, label);
  validateProcessSpace(value.processSpace, `${label}.processSpace`);
  expectArray(value.visuals, `${label}.visuals`).forEach((visual, index) => validateVisualStructure(visual, `${label}.visuals[${index}]`));
  expectArray(value.warnings, `${label}.warnings`).forEach((warning, index) => expectString(warning, `${label}.warnings[${index}]`));
}

function validateDebugAst(value, label) {
  expectObject(value, label);
  validateAstNode(value.root, `${label}.root`);
  if (value.activeNode !== null) {
    expectObject(value.activeNode, `${label}.activeNode`);
    expectString(value.activeNode.nodeId, `${label}.activeNode.nodeId`);
    expectString(value.activeNode.kind, `${label}.activeNode.kind`);
    expectString(value.activeNode.label, `${label}.activeNode.label`);
    validateNullableSourceSpan(value.activeNode.sourceRange, `${label}.activeNode.sourceRange`);
    expectString(value.activeNode.explanation, `${label}.activeNode.explanation`);
  }
  expectArray(value.relatedIrIds, `${label}.relatedIrIds`);
  expectArray(value.relatedAsmIds, `${label}.relatedAsmIds`);
}

function validateDebugIr(value, label) {
  expectObject(value, label);
  const lines = expectArray(value.lines, `${label}.lines`);
  lines.forEach((line, index) => validateIrLine(line, `${label}.lines[${index}]`));
  if (lines.length === 0) {
    fail(`${label}: IR view has no lines`);
  }
  expectString(value.currentInstructionId, `${label}.currentInstructionId`);
  validateNullableSourceSpan(value.currentSourceRange, `${label}.currentSourceRange`);
  expectString(value.explanation, `${label}.explanation`);
  expectArray(value.operands, `${label}.operands`).forEach((operand, index) => {
    expectObject(operand, `${label}.operands[${index}]`);
    for (const key of ["name", "typeName", "valueSummary", "valueRef"]) {
      expectString(operand[key], `${label}.operands[${index}].${key}`);
    }
  });
}

function validateDebugAsm(value, label) {
  expectObject(value, label);
  const lines = expectArray(value.lines, `${label}.lines`);
  lines.forEach((line, index) => validateAssemblyLine(line, `${label}.lines[${index}]`));
  if (lines.length === 0) {
    fail(`${label}: ASM view has no lines`);
  }
  expectString(value.explanation, `${label}.explanation`);
  expectArray(value.relatedIrIds, `${label}.relatedIrIds`);
}

function validateDebugSnapshot(value, label) {
  expectObject(value, label);
  expectNumber(value.snapshotId, `${label}.snapshotId`);
  expectNumber(value.visibleStepIndex, `${label}.visibleStepIndex`);
  expectString(value.functionName, `${label}.functionName`);
  expectString(value.blockLabel, `${label}.blockLabel`);
  expectString(value.instructionId, `${label}.instructionId`);
  validateNullableSourceSpan(value.sourceRange, `${label}.sourceRange`);
  expectArray(value.callStackSummary, `${label}.callStackSummary`).forEach((item, index) =>
    expectString(item, `${label}.callStackSummary[${index}]`));
  validateProcessSpace(value.processSpace, `${label}.processSpace`);
  expectBoolean(value.breakpointHit, `${label}.breakpointHit`);
  expectString(value.stopReason, `${label}.stopReason`);
}

function validateProcessSpace(value, label) {
  expectObject(value, label);
  for (const key of ["currentFunctionName", "currentInstructionId", "stdin", "stdout", "stderr"]) {
    expectString(value[key], `${label}.${key}`);
  }
  expectArray(value.functions, `${label}.functions`);
  expectArray(value.staticValues, `${label}.staticValues`).forEach((variable, index) => validateVariable(variable, `${label}.staticValues[${index}]`));
  expectArray(value.stackFrames, `${label}.stackFrames`).forEach((frame, index) => validateFrame(frame, `${label}.stackFrames[${index}]`));
  expectArray(value.heapValues, `${label}.heapValues`).forEach((variable, index) => validateVariable(variable, `${label}.heapValues[${index}]`));
}

function validateFrame(value, label) {
  expectObject(value, label);
  expectString(value.frameId, `${label}.frameId`);
  expectString(value.functionName, `${label}.functionName`);
  expectArray(value.parameters, `${label}.parameters`).forEach((variable, index) => validateVariable(variable, `${label}.parameters[${index}]`));
  expectArray(value.locals, `${label}.locals`).forEach((variable, index) => validateVariable(variable, `${label}.locals[${index}]`));
  if (value.returnTarget !== null) {
    expectString(value.returnTarget, `${label}.returnTarget`);
  }
  validateNullableSourceSpan(value.activeRange, `${label}.activeRange`);
}

function validateVariable(value, label) {
  expectObject(value, label);
  for (const key of ["name", "address", "typeName", "valueKind", "valueSummary", "pointerTarget", "typeShape", "explanation"]) {
    expectString(value[key], `${label}.${key}`);
  }
  expectBoolean(value.highlightedChange, `${label}.highlightedChange`);
  expectArray(value.fields, `${label}.fields`).forEach((field, index) => validateVariable(field, `${label}.fields[${index}]`));
  expectArray(value.elements, `${label}.elements`).forEach((element, index) => validateVariable(element, `${label}.elements[${index}]`));
}

function validateVisualStructure(value, label) {
  expectObject(value, label);
  for (const key of ["id", "name", "type", "kind", "layoutHint", "summary", "explanation"]) {
    expectString(value[key], `${label}.${key}`);
  }
  expectArray(value.elements, `${label}.elements`).forEach((element, index) => {
    expectObject(element, `${label}.elements[${index}]`);
    expectString(element.id, `${label}.elements[${index}].id`);
    expectString(element.kind, `${label}.elements[${index}].kind`);
    expectString(element.label, `${label}.elements[${index}].label`);
    expectObject(element.metadata, `${label}.elements[${index}].metadata`);
  });
}

function validateDebugEvent(value, label) {
  expectObject(value, label);
  expectNumber(value.eventId, `${label}.eventId`);
  expectNumber(value.snapshotId, `${label}.snapshotId`);
  expectString(value.type, `${label}.type`);
  expectString(value.title, `${label}.title`);
  expectString(value.description, `${label}.description`);
  validateNullableSourceSpan(value.sourceRange, `${label}.sourceRange`);
  expectArray(value.affectedValueRefs, `${label}.affectedValueRefs`);
}

function validateTimelineItem(value, label) {
  expectObject(value, label);
  expectNumber(value.snapshotId, `${label}.snapshotId`);
  expectNumber(value.visibleStepIndex, `${label}.visibleStepIndex`);
  expectString(value.stopReason, `${label}.stopReason`);
  expectBoolean(value.breakpointHit, `${label}.breakpointHit`);
  validateNullableSourceSpan(value.sourceRange, `${label}.sourceRange`);
}

function validateBreakpoint(value, label) {
  expectObject(value, label);
  expectNumber(value.line, `${label}.line`);
  expectBoolean(value.enabled, `${label}.enabled`);
}

function validateAstNode(value, label) {
  expectObject(value, label);
  expectString(value.id, `${label}.id`);
  expectString(value.label, `${label}.label`);
  expectString(value.kind, `${label}.kind`);
  validateNullableSourceSpan(value.range, `${label}.range`);
  expectBoolean(value.active, `${label}.active`);
  expectArray(value.children, `${label}.children`).forEach((child, index) => validateAstNode(child, `${label}.children[${index}]`));
}

function validateNullableAstNode(value, label) {
  if (value !== null) {
    validateAstNode(value, label);
  }
}

function validateNullableSemanticScope(value, label) {
  if (value === null) {
    return;
  }
  expectObject(value, label);
  expectString(value.id, `${label}.id`);
  expectString(value.label, `${label}.label`);
  expectArray(value.symbols, `${label}.symbols`);
  validateNullableSourceSpan(value.range, `${label}.range`);
  expectBoolean(value.active, `${label}.active`);
  expectArray(value.children, `${label}.children`).forEach((child, index) =>
    validateNullableSemanticScope(child, `${label}.children[${index}]`));
}

function validateLexerToken(value, label) {
  expectObject(value, label);
  expectString(value.kind, `${label}.kind`);
  expectString(value.text, `${label}.text`);
  validateNullableSourceSpan(value.range, `${label}.range`);
  expectBoolean(value.active, `${label}.active`);
}

function validateIrLine(value, label) {
  expectObject(value, label);
  expectNumber(value.lineNumber, `${label}.lineNumber`);
  expectString(value.text, `${label}.text`);
  validateNullableSourceSpan(value.range, `${label}.range`);
  expectBoolean(value.active, `${label}.active`);
}

function validateAssemblyLine(value, label) {
  expectObject(value, label);
  expectNumber(value.lineNumber, `${label}.lineNumber`);
  expectString(value.text, `${label}.text`);
  expectString(value.kind, `${label}.kind`);
  expectString(value.section, `${label}.section`);
  expectString(value.label, `${label}.label`);
  validateNullableSourceSpan(value.range, `${label}.range`);
  expectBoolean(value.active, `${label}.active`);
}

function validateDiagnostic(value, label) {
  expectObject(value, label);
  for (const key of ["code", "severity", "message", "sourceName"]) {
    expectString(value[key], `${label}.${key}`);
  }
  expectNumber(value.startOffset, `${label}.startOffset`);
  expectNumber(value.endOffset, `${label}.endOffset`);
}

function validateNullableSourceRange(value, label) {
  if (value === null) {
    return;
  }
  expectObject(value, label);
  expectString(value.sourceName, `${label}.sourceName`);
  expectNumber(value.startOffset, `${label}.startOffset`);
  expectNumber(value.endOffset, `${label}.endOffset`);
}

function validateNullableSourceSpan(value, label) {
  if (value === null) {
    return;
  }
  validateNullableSourceRange(value, label);
  expectNumber(value.startLine, `${label}.startLine`);
  expectNumber(value.startColumn, `${label}.startColumn`);
  expectNumber(value.endLine, `${label}.endLine`);
  expectNumber(value.endColumn, `${label}.endColumn`);
}

function expectObject(value, label) {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    fail(`${label} must be an object`);
    return {};
  }
  return value;
}

function expectArray(value, label) {
  if (!Array.isArray(value)) {
    fail(`${label} must be an array`);
    return [];
  }
  return value;
}

function expectString(value, label) {
  if (typeof value !== "string") {
    fail(`${label} must be a string`);
  }
}

function expectNumber(value, label) {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    fail(`${label} must be a finite number`);
  }
}

function expectBoolean(value, label) {
  if (typeof value !== "boolean") {
    fail(`${label} must be a boolean`);
  }
}

function fail(message) {
  failures.push(message);
}
