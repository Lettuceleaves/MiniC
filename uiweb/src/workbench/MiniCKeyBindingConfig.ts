import type { JavaMirrorFile } from "../translation/javaMirror";
import defaultKeyBindings from "../resources/minic/uilocal/keybindings.json";

export const miniCKeyBindingConfigMirror = {
  "javaPath": "src/main/java/minic/uilocal/workbench/MiniCKeyBindingConfig.java",
  "webPath": "uiweb/src/workbench/MiniCKeyBindingConfig.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCKeyBindingConfig",
  "kind": "class",
  "imports": [
    "javafx.scene.input.KeyCode",
    "javafx.scene.input.KeyEvent",
    "javafx.scene.input.MouseButton",
    "javafx.scene.input.MouseEvent",
    "javafx.scene.input.ScrollEvent",
    "minic.uilocal.control.MiniCWorkbenchControlHub",
    "java.io.IOException",
    "java.io.InputStream",
    "java.nio.charset.StandardCharsets",
    "java.nio.file.Files",
    "java.nio.file.Path",
    "java.util.ArrayList",
    "java.util.Collection",
    "java.util.Comparator",
    "java.util.LinkedHashMap",
    "java.util.List",
    "java.util.Locale",
    "java.util.Map",
    "java.util.Objects",
    "java.util.Optional",
    "java.util.Set",
    "java.util.regex.Matcher",
    "java.util.regex.Pattern"
  ],
  "fields": [
    {
      "name": "USER_BINDINGS_FILE",
      "signature": "private static final Path USER_BINDINGS_FILE ="
    },
    {
      "name": "LEGACY_AST_ZOOM_IN",
      "signature": "private static final String LEGACY_AST_ZOOM_IN ="
    },
    {
      "name": "LEGACY_AST_ZOOM_OUT",
      "signature": "private static final String LEGACY_AST_ZOOM_OUT ="
    },
    {
      "name": "BINDING_PATTERN",
      "signature": "private static final Pattern BINDING_PATTERN ="
    },
    {
      "name": "KEY_PATTERN",
      "signature": "private static final Pattern KEY_PATTERN ="
    },
    {
      "name": "ACTION_LABELS",
      "signature": "private static final LinkedHashMap<String, String> ACTION_LABELS ="
    },
    {
      "name": "ACTION_ORDER",
      "signature": "private static final List<String> ACTION_ORDER ="
    },
    {
      "name": "activeBindings",
      "signature": "private static volatile List<KeyBinding> activeBindings ="
    }
  ],
  "methods": [
    {
      "name": "loadDefault",
      "signature": "loadDefault()"
    },
    {
      "name": "matches",
      "signature": "matches(String action, KeyEvent event)"
    },
    {
      "name": "matches",
      "signature": "matches(String action, KeyEvent event, Set<KeyCode> heldKeys)"
    },
    {
      "name": "matches",
      "signature": "matches(String action, MouseEvent event)"
    },
    {
      "name": "matches",
      "signature": "matches(String action, MouseEvent event, Set<KeyCode> heldKeys)"
    },
    {
      "name": "matches",
      "signature": "matches(String action, ScrollEvent event)"
    },
    {
      "name": "matches",
      "signature": "matches(String action, ScrollEvent event, Set<KeyCode> heldKeys)"
    },
    {
      "name": "actions",
      "signature": "actions()"
    },
    {
      "name": "keysFor",
      "signature": "keysFor(String action)"
    },
    {
      "name": "labelFor",
      "signature": "labelFor(String action)"
    },
    {
      "name": "setKeys",
      "signature": "setKeys(String action, List<String> keys)"
    },
    {
      "name": "conflictingAction",
      "signature": "conflictingAction(String action, String key)"
    },
    {
      "name": "isReserved",
      "signature": "isReserved(String key)"
    },
    {
      "name": "comboFrom",
      "signature": "comboFrom(KeyEvent event)"
    },
    {
      "name": "comboFrom",
      "signature": "comboFrom(MouseEvent event)"
    },
    {
      "name": "comboFrom",
      "signature": "comboFrom(MouseEvent event, Set<KeyCode> heldKeys)"
    },
    {
      "name": "comboFrom",
      "signature": "comboFrom(ScrollEvent event)"
    },
    {
      "name": "comboFrom",
      "signature": "comboFrom(ScrollEvent event, Set<KeyCode> heldKeys)"
    },
    {
      "name": "normalizeCombo",
      "signature": "normalizeCombo(String key)"
    },
    {
      "name": "loadBindings",
      "signature": "loadBindings()"
    },
    {
      "name": "defaultBindings",
      "signature": "defaultBindings()"
    },
    {
      "name": "parse",
      "signature": "parse(String json)"
    },
    {
      "name": "fallbackBindings",
      "signature": "fallbackBindings()"
    },
    {
      "name": "activeBindingsByAction",
      "signature": "activeBindingsByAction()"
    },
    {
      "name": "bindingsByAction",
      "signature": "bindingsByAction(List<KeyBinding> bindings)"
    },
    {
      "name": "bindingsFrom",
      "signature": "bindingsFrom(Map<String, List<String>> map)"
    },
    {
      "name": "save",
      "signature": "save(Map<String, List<String>> map)"
    },
    {
      "name": "json",
      "signature": "json(Map<String, List<String>> map)"
    },
    {
      "name": "escape",
      "signature": "escape(String value)"
    },
    {
      "name": "normalizeAction",
      "signature": "normalizeAction(String action)"
    },
    {
      "name": "combo",
      "signature": "combo(boolean control, boolean alt, boolean shift, boolean meta, Collection<KeyCode> keyCodes, MouseButton mouseButton, WheelDirection wheelDirection)"
    },
    {
      "name": "orderedKeys",
      "signature": "orderedKeys(Collection<KeyCode> keyCodes)"
    },
    {
      "name": "isModifier",
      "signature": "isModifier(KeyCode code)"
    },
    {
      "name": "keyName",
      "signature": "keyName(KeyCode code)"
    },
    {
      "name": "mouseName",
      "signature": "mouseName(MouseButton button)"
    },
    {
      "name": "wheelName",
      "signature": "wheelName(WheelDirection direction)"
    },
    {
      "name": "wheelDirection",
      "signature": "wheelDirection(ScrollEvent event)"
    },
    {
      "name": "actionLabels",
      "signature": "actionLabels()"
    },
    {
      "name": "KeyBinding",
      "signature": "KeyBinding(String action, String key)"
    },
    {
      "name": "matches",
      "signature": "matches(KeyEvent event, Set<KeyCode> heldKeys)"
    },
    {
      "name": "matches",
      "signature": "matches(MouseEvent event, Set<KeyCode> heldKeys)"
    },
    {
      "name": "matches",
      "signature": "matches(ScrollEvent event, Set<KeyCode> heldKeys)"
    },
    {
      "name": "modifiersMatch",
      "signature": "modifiersMatch(ParsedInput parsed, boolean control, boolean alt, boolean shift, boolean meta)"
    },
    {
      "name": "keysMatch",
      "signature": "keysMatch(List<KeyCode> expected, Set<KeyCode> heldKeys, KeyCode eventCode)"
    },
    {
      "name": "keysMatch",
      "signature": "keysMatch(List<KeyCode> expected, Set<KeyCode> heldKeys)"
    },
    {
      "name": "ParsedInput",
      "signature": "ParsedInput(boolean control, boolean alt, boolean shift, boolean meta, List<KeyCode> keys, MouseButton mouseButton, WheelDirection wheelDirection)"
    },
    {
      "name": "parse",
      "signature": "parse(String key)"
    },
    {
      "name": "addKey",
      "signature": "addKey(List<KeyCode> keys, KeyCode code)"
    },
    {
      "name": "mouseButton",
      "signature": "mouseButton(String text)"
    },
    {
      "name": "wheelDirection",
      "signature": "wheelDirection(String text)"
    },
    {
      "name": "keyCode",
      "signature": "keyCode(String text)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCKeyBindingConfig {
  static readonly mirror = miniCKeyBindingConfigMirror;

  static activeBindings: readonly KeyBinding[] = loadBindings();

  readonly mirror = miniCKeyBindingConfigMirror;

  static loadDefault(): MiniCKeyBindingConfig {
    MiniCKeyBindingConfig.activeBindings = loadBindings();
    return new MiniCKeyBindingConfig();
  }

  matches(action: string, event: MiniCInputEvent, heldKeys: ReadonlySet<string> = new Set()): boolean {
    const normalizedAction = normalizeAction(action);
    return MiniCKeyBindingConfig.activeBindings
      .filter((binding) => binding.action === normalizedAction)
      .some((binding) => binding.matches(event, heldKeys));
  }

  actions(): readonly string[] {
    const actions = [...ACTION_ORDER];
    MiniCKeyBindingConfig.activeBindings
      .map((binding) => binding.action)
      .filter((action, index, all) => all.indexOf(action) === index)
      .filter((action) => !actions.includes(action))
      .forEach((action) => actions.push(action));
    return actions;
  }

  keysFor(action: string): readonly string[] {
    const normalizedAction = normalizeAction(action);
    return MiniCKeyBindingConfig.activeBindings
      .filter((binding) => binding.action === normalizedAction)
      .map((binding) => binding.key);
  }

  labelFor(action: string): string {
    const normalizedAction = normalizeAction(action);
    return ACTION_LABELS.get(normalizedAction) ?? normalizedAction;
  }

  static setKeys(action: string, keys: readonly string[]): void {
    const normalizedAction = normalizeAction(action);
    const normalized = [...new Set(keys.map(normalizeCombo).filter((key) => key !== ""))];
    if (normalized.length === 0) {
      throw new Error("keys must not be empty");
    }
    const map = activeBindingsByAction();
    map.set(normalizedAction, normalized);
    MiniCKeyBindingConfig.activeBindings = bindingsFrom(map);
    saveBindings(map);
  }

  static conflictingAction(action: string, key: string): string | null {
    const normalizedAction = normalizeAction(action);
    const normalized = normalizeCombo(key);
    if (normalized === "") {
      return null;
    }
    return (
      MiniCKeyBindingConfig.activeBindings.find(
        (binding) => binding.action !== normalizedAction && normalizeCombo(binding.key) === normalized,
      )?.action ?? null
    );
  }

  static isReserved(key: string): boolean {
    const parsed = ParsedInput.parse(key);
    return parsed.keys.includes("Enter") || parsed.keys.includes("Esc");
  }

  static comboFrom(event: MiniCInputEvent, heldKeys: ReadonlySet<string> = new Set()): string {
    return comboFromEvent(event, heldKeys);
  }

  static normalizeCombo(key: string): string {
    return normalizeCombo(key);
  }

  summary(): string {
    return `MiniCKeyBindingConfig: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export const MiniCWorkbenchActionIds = {
  viewportZoomIn: "viewport.zoom.in",
  viewportZoomOut: "viewport.zoom.out",
  viewportScrollUp: "viewport.scroll.up",
  viewportScrollDown: "viewport.scroll.down",
  viewportScrollLeft: "viewport.scroll.left",
  viewportScrollRight: "viewport.scroll.right",
  viewportCenterActive: "viewport.centerActive",
  debugStart: "debug.start",
  debugRunToEnd: "debug.runToEnd",
  debugRunToBreakpoint: "debug.runToBreakpoint",
  debugStepOver: "debug.stepOver",
  debugStepInto: "debug.stepInto",
  debugBackToBreakpoint: "debug.backToBreakpoint",
  debugStepBackOver: "debug.stepBackOver",
  debugStepBack: "debug.stepBack",
  compilerNext: "compiler.next",
  compilerNextStage: "compiler.nextStage",
  compilerRunToExecution: "compiler.runToExecution",
  compilerPlay: "compiler.play",
  compilerPlayFast: "compiler.playFast",
  compilerPause: "compiler.pause",
  settingsThemeSet: "settings.theme.set",
  settingsThemeNext: "settings.theme.next",
  settingsThemePrevious: "settings.theme.previous",
  settingsFrameIntervalSet: "settings.frameInterval.set",
  settingsFrameIntervalIncrease: "settings.frameInterval.increase",
  settingsFrameIntervalDecrease: "settings.frameInterval.decrease",
  settingsUiScaleIncrease: "settings.uiScale.increase",
  settingsUiScaleDecrease: "settings.uiScale.decrease",
} as const;

export type MiniCInputEvent = KeyboardEvent | MouseEvent | WheelEvent;

type WheelDirection = "UP" | "DOWN" | "LEFT" | "RIGHT";

interface KeyBinding {
  readonly action: string;
  readonly key: string;
  matches(event: MiniCInputEvent, heldKeys: ReadonlySet<string>): boolean;
}

interface ParsedInputShape {
  readonly control: boolean;
  readonly alt: boolean;
  readonly shift: boolean;
  readonly meta: boolean;
  readonly keys: readonly string[];
  readonly mouseButton: string | null;
  readonly wheelDirection: WheelDirection | null;
}

class ParsedInput implements ParsedInputShape {
  private constructor(
    readonly control: boolean,
    readonly alt: boolean,
    readonly shift: boolean,
    readonly meta: boolean,
    readonly keys: readonly string[],
    readonly mouseButton: string | null,
    readonly wheelDirection: WheelDirection | null,
  ) {}

  static parse(key: string): ParsedInput {
    let control = false;
    let alt = false;
    let shift = false;
    let meta = false;
    const keys: string[] = [];
    let mouseButton: string | null = null;
    let wheelDirectionValue: WheelDirection | null = null;
    const parts = key.split("+");
    parts.forEach((part, index) => {
      const normalized = part.trim();
      if (normalized === "" && index === parts.length - 1 && key.endsWith("+")) {
        addKey(keys, "+");
        return;
      }
      if (normalized === "") {
        return;
      }
      const lower = normalized.toLowerCase();
      if (lower === "ctrl" || lower === "control") {
        control = true;
      } else if (lower === "alt") {
        alt = true;
      } else if (lower === "shift") {
        shift = true;
      } else if (lower === "meta" || lower === "command") {
        meta = true;
      } else {
        const mouse = mouseName(normalized);
        const wheel = wheelDirectionName(normalized);
        if (mouse !== null) {
          mouseButton = mouse;
        } else if (wheel !== null) {
          wheelDirectionValue = wheel;
        } else {
          addKey(keys, keyName(normalized));
        }
      }
    });
    return new ParsedInput(control, alt, shift, meta, orderedKeys(keys), mouseButton, wheelDirectionValue);
  }
}

const LEGACY_AST_ZOOM_IN = "ast.zoom.in";
const LEGACY_AST_ZOOM_OUT = "ast.zoom.out";
const LOCAL_STORAGE_KEY = "minic.uiweb.keybindings";
const ACTION_LABELS = actionLabels();
const ACTION_ORDER = [...ACTION_LABELS.keys()];

function createKeyBinding(action: string, key: string): KeyBinding {
  return {
    action,
    key,
    matches(event, heldKeys) {
      const parsed = ParsedInput.parse(key);
      if (isWheelEvent(event)) {
        return (
          parsed.mouseButton === null &&
          parsed.wheelDirection !== null &&
          modifiersMatch(parsed, event.ctrlKey, event.altKey, event.shiftKey, event.metaKey) &&
          keysMatch(parsed.keys, heldKeys) &&
          wheelDirection(event) === parsed.wheelDirection
        );
      }
      if (isMouseEvent(event) && !isKeyboardEvent(event)) {
        return (
          parsed.mouseButton !== null &&
          parsed.wheelDirection === null &&
          modifiersMatch(parsed, event.ctrlKey, event.altKey, event.shiftKey, event.metaKey) &&
          keysMatch(parsed.keys, heldKeys) &&
          mouseButtonFromEvent(event) === parsed.mouseButton
        );
      }
      return (
        parsed.mouseButton === null &&
        parsed.wheelDirection === null &&
        modifiersMatch(parsed, event.ctrlKey, event.altKey, event.shiftKey, event.metaKey) &&
        keysMatch(parsed.keys, heldKeys, keyFromKeyboardEvent(event))
      );
    },
  };
}

function normalizeCombo(key: string): string {
  const parsed = ParsedInput.parse(key);
  if (parsed.keys.length === 0 && parsed.mouseButton === null && parsed.wheelDirection === null) {
    return "";
  }
  return combo(parsed.control, parsed.alt, parsed.shift, parsed.meta, parsed.keys, parsed.mouseButton, parsed.wheelDirection);
}

function comboFromEvent(event: MiniCInputEvent, heldKeys: ReadonlySet<string>): string {
  if (isWheelEvent(event)) {
    return combo(
      event.ctrlKey,
      event.altKey,
      event.shiftKey,
      event.metaKey,
      heldKeys,
      null,
      wheelDirection(event),
    );
  }
  if (isMouseEvent(event) && !isKeyboardEvent(event)) {
    return combo(
      event.ctrlKey,
      event.altKey,
      event.shiftKey,
      event.metaKey,
      heldKeys,
      mouseButtonFromEvent(event),
      null,
    );
  }
  return combo(
    event.ctrlKey,
    event.altKey,
    event.shiftKey,
    event.metaKey,
    [keyFromKeyboardEvent(event)],
    null,
    null,
  );
}

function combo(
  control: boolean,
  alt: boolean,
  shift: boolean,
  meta: boolean,
  keyCodes: Iterable<string>,
  mouseButton: string | null,
  wheelDirectionValue: WheelDirection | null,
): string {
  const parts: string[] = [];
  if (control) {
    parts.push("Ctrl");
  }
  if (alt) {
    parts.push("Alt");
  }
  if (shift) {
    parts.push("Shift");
  }
  if (meta) {
    parts.push("Meta");
  }
  parts.push(...orderedKeys([...keyCodes].map(keyName)));
  if (mouseButton !== null) {
    parts.push(mouseButton);
  } else if (wheelDirectionValue !== null) {
    parts.push(wheelName(wheelDirectionValue));
  }
  return parts.join("+");
}

function loadBindings(): readonly KeyBinding[] {
  const map = bindingsByAction(defaultBindings());
  readSavedBindings().forEach((keys, action) => {
    map.set(action, keys);
  });
  return bindingsFrom(map);
}

function defaultBindings(): readonly KeyBinding[] {
  return defaultKeyBindings.bindings.flatMap((entry) =>
    entry.keys.map((key) => createKeyBinding(normalizeAction(entry.action), normalizeCombo(key))).filter((binding) => binding.key !== ""),
  );
}

function readSavedBindings(): ReadonlyMap<string, readonly string[]> {
  if (typeof window === "undefined") {
    return new Map();
  }
  const raw = window.localStorage.getItem(LOCAL_STORAGE_KEY);
  if (raw === null) {
    return new Map();
  }
  try {
    return bindingsByAction(parseBindingsJson(raw));
  } catch {
    return new Map();
  }
}

function parseBindingsJson(json: string): readonly KeyBinding[] {
  const parsed: unknown = JSON.parse(json);
  if (!isBindingsConfig(parsed)) {
    return [];
  }
  return parsed.bindings.flatMap((entry) =>
    entry.keys.map((key) => createKeyBinding(normalizeAction(entry.action), normalizeCombo(key))).filter((binding) => binding.key !== ""),
  );
}

function isBindingsConfig(value: unknown): value is { readonly bindings: readonly { readonly action: string; readonly keys: readonly string[] }[] } {
  if (typeof value !== "object" || value === null || !("bindings" in value)) {
    return false;
  }
  const bindings = value.bindings;
  return (
    Array.isArray(bindings) &&
    bindings.every(
      (entry) =>
        typeof entry === "object" &&
        entry !== null &&
        "action" in entry &&
        "keys" in entry &&
        typeof entry.action === "string" &&
        Array.isArray(entry.keys) &&
        entry.keys.every((key: unknown) => typeof key === "string"),
    )
  );
}

function activeBindingsByAction(): Map<string, readonly string[]> {
  return bindingsByAction(MiniCKeyBindingConfig.activeBindings);
}

function bindingsByAction(bindings: readonly KeyBinding[]): Map<string, readonly string[]> {
  const map = new Map<string, string[]>();
  bindings.forEach((binding) => {
    const current = map.get(binding.action) ?? [];
    if (!current.includes(binding.key)) {
      current.push(binding.key);
    }
    map.set(binding.action, current);
  });
  return map;
}

function bindingsFrom(map: ReadonlyMap<string, readonly string[]>): readonly KeyBinding[] {
  const bindings: KeyBinding[] = [];
  map.forEach((keys, action) => {
    keys.forEach((key) => {
      bindings.push(createKeyBinding(action, key));
    });
  });
  return bindings;
}

function saveBindings(map: ReadonlyMap<string, readonly string[]>): void {
  if (typeof window === "undefined") {
    return;
  }
  const bindings = [...map.entries()].map(([action, keys]) => ({ action, keys: [...keys] }));
  window.localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify({ bindings }, null, 2));
}

function normalizeAction(action: string): string {
  if (action === LEGACY_AST_ZOOM_IN) {
    return MiniCWorkbenchActionIds.viewportZoomIn;
  }
  if (action === LEGACY_AST_ZOOM_OUT) {
    return MiniCWorkbenchActionIds.viewportZoomOut;
  }
  return action;
}

function actionLabels(): Map<string, string> {
  return new Map([
    [MiniCWorkbenchActionIds.viewportZoomIn, "当前视口 · 放大"],
    [MiniCWorkbenchActionIds.viewportZoomOut, "当前视口 · 缩小"],
    [MiniCWorkbenchActionIds.viewportScrollUp, "当前视口 · 向上滚动"],
    [MiniCWorkbenchActionIds.viewportScrollDown, "当前视口 · 向下滚动"],
    [MiniCWorkbenchActionIds.viewportScrollLeft, "当前视口 · 向左滚动"],
    [MiniCWorkbenchActionIds.viewportScrollRight, "当前视口 · 向右滚动"],
    [MiniCWorkbenchActionIds.viewportCenterActive, "当前视口 · 居中高亮"],
    [MiniCWorkbenchActionIds.debugStart, "调试 · 从头开始"],
    [MiniCWorkbenchActionIds.debugRunToEnd, "调试 · 运行到结束"],
    [MiniCWorkbenchActionIds.debugRunToBreakpoint, "调试 · 下个断点"],
    [MiniCWorkbenchActionIds.debugStepOver, "调试 · 本层下一句"],
    [MiniCWorkbenchActionIds.debugStepInto, "调试 · 下一句"],
    [MiniCWorkbenchActionIds.debugBackToBreakpoint, "调试 · 上个断点"],
    [MiniCWorkbenchActionIds.debugStepBackOver, "调试 · 本层上一句"],
    [MiniCWorkbenchActionIds.debugStepBack, "调试 · 上一句"],
    [MiniCWorkbenchActionIds.compilerNext, "编译器 · 下一步"],
    [MiniCWorkbenchActionIds.compilerNextStage, "编译器 · 下一阶段"],
    [MiniCWorkbenchActionIds.compilerRunToExecution, "编译器 · 到执行"],
    [MiniCWorkbenchActionIds.compilerPlay, "编译器 · 播放"],
    [MiniCWorkbenchActionIds.compilerPlayFast, "编译器 · 2x"],
    [MiniCWorkbenchActionIds.compilerPause, "编译器 · 暂停"],
    [MiniCWorkbenchActionIds.settingsThemeNext, "设置 · 下一个主题"],
    [MiniCWorkbenchActionIds.settingsThemePrevious, "设置 · 上一个主题"],
    [MiniCWorkbenchActionIds.settingsFrameIntervalIncrease, "设置 · 增加帧间隔"],
    [MiniCWorkbenchActionIds.settingsFrameIntervalDecrease, "设置 · 减少帧间隔"],
    [MiniCWorkbenchActionIds.settingsUiScaleIncrease, "设置 · 放大全局界面"],
    [MiniCWorkbenchActionIds.settingsUiScaleDecrease, "设置 · 缩小全局界面"],
  ]);
}

function modifiersMatch(
  parsed: ParsedInputShape,
  control: boolean,
  alt: boolean,
  shift: boolean,
  meta: boolean,
): boolean {
  return parsed.control === control && parsed.alt === alt && parsed.shift === shift && parsed.meta === meta;
}

function keysMatch(expected: readonly string[], heldKeys: ReadonlySet<string>, eventKey?: string): boolean {
  const actual = new Set([...heldKeys].map(keyName).filter((key) => !isModifier(key)));
  if (eventKey !== undefined && !isModifier(eventKey)) {
    actual.add(keyName(eventKey));
  }
  return listEquals(orderedKeys(expected), orderedKeys(actual));
}

function orderedKeys(keys: Iterable<string>): readonly string[] {
  return [...new Set([...keys].map(keyName).filter((key) => key !== "" && !isModifier(key)))].sort();
}

function addKey(keys: string[], key: string): void {
  const normalized = keyName(key);
  if (normalized !== "" && !isModifier(normalized) && !keys.includes(normalized)) {
    keys.push(normalized);
  }
}

function keyName(key: string): string {
  switch (key) {
    case "Control":
    case "Ctrl":
      return "Ctrl";
    case "Escape":
    case "Esc":
      return "Esc";
    case " ":
    case "Spacebar":
      return "Space";
    case "ArrowUp":
      return "Up";
    case "ArrowDown":
      return "Down";
    case "ArrowLeft":
      return "Left";
    case "ArrowRight":
      return "Right";
    case ".":
      return "Period";
    default:
      return key.length === 1 ? key.toUpperCase() : key;
  }
}

function isModifier(key: string): boolean {
  return key === "Ctrl" || key === "Control" || key === "Alt" || key === "Shift" || key === "Meta";
}

function mouseName(text: string): string | null {
  switch (text.toLowerCase()) {
    case "mouseleft":
    case "leftclick":
    case "primaryclick":
      return "MouseLeft";
    case "mousemiddle":
    case "middleclick":
      return "MouseMiddle";
    case "mouseright":
    case "rightclick":
    case "secondaryclick":
      return "MouseRight";
    case "mouseback":
    case "backclick":
      return "MouseBack";
    case "mouseforward":
    case "forwardclick":
      return "MouseForward";
    default:
      return null;
  }
}

function wheelDirectionName(text: string): WheelDirection | null {
  switch (text.toLowerCase()) {
    case "wheelup":
    case "scrollup":
      return "UP";
    case "wheeldown":
    case "scrolldown":
      return "DOWN";
    case "wheelleft":
    case "scrollleft":
      return "LEFT";
    case "wheelright":
    case "scrollright":
      return "RIGHT";
    default:
      return null;
  }
}

function wheelName(direction: WheelDirection): string {
  switch (direction) {
    case "UP":
      return "WheelUp";
    case "DOWN":
      return "WheelDown";
    case "LEFT":
      return "WheelLeft";
    case "RIGHT":
      return "WheelRight";
  }
}

function wheelDirection(event: WheelEvent): WheelDirection | null {
  if (Math.abs(event.deltaY) >= Math.abs(event.deltaX) && event.deltaY !== 0) {
    return event.deltaY < 0 ? "UP" : "DOWN";
  }
  if (event.deltaX !== 0) {
    return event.deltaX > 0 ? "RIGHT" : "LEFT";
  }
  return null;
}

function mouseButtonFromEvent(event: MouseEvent): string | null {
  switch (event.button) {
    case 0:
      return "MouseLeft";
    case 1:
      return "MouseMiddle";
    case 2:
      return "MouseRight";
    case 3:
      return "MouseBack";
    case 4:
      return "MouseForward";
    default:
      return null;
  }
}

function keyFromKeyboardEvent(event: KeyboardEvent): string {
  if (event.key !== "") {
    return keyName(event.key);
  }
  return keyName(event.code);
}

function isKeyboardEvent(event: MiniCInputEvent): event is KeyboardEvent {
  return "key" in event;
}

function isMouseEvent(event: MiniCInputEvent): event is MouseEvent {
  return "button" in event;
}

function isWheelEvent(event: MiniCInputEvent): event is WheelEvent {
  return "deltaY" in event;
}

function listEquals(left: readonly string[], right: readonly string[]): boolean {
  return left.length === right.length && left.every((value, index) => value === right[index]);
}

export default MiniCKeyBindingConfig;
