import type { JavaMirrorFile } from "../translation/javaMirror";
import darkTheme from "../resources/minic/themes/dark.json";
import lightTheme from "../resources/minic/themes/light.json";
import sepiaTheme from "../resources/minic/themes/sepia.json";
import solarizedTheme from "../resources/minic/themes/solarized.json";
import MiniCSettings from "./MiniCSettings";

export const themeManagerMirror = {
  javaPath: "src/main/java/minic/color/ThemeManager.java",
  webPath: "uiweb/src/settings/ThemeManager.ts",
  packageName: "minic.color",
  exportName: "ThemeManager",
  kind: "class",
  imports: [
    "javafx.application.Platform",
    "javafx.scene.Scene",
    "minic.settings.MiniCSettings",
    "java.io.IOException",
    "java.nio.charset.StandardCharsets",
    "java.nio.file.Files",
    "java.nio.file.Path",
    "java.util.List",
  ],
  fields: [
    { name: "THEMES_DIR", signature: 'private static final Path THEMES_DIR = Path.of("config", "themes");' },
    { name: "DEFAULT_THEME", signature: 'private static final String DEFAULT_THEME = "dark";' },
    { name: "currentThemeName", signature: "private static String currentThemeName;" },
    { name: "scene", signature: "private static Scene scene;" },
  ],
  methods: [
    { name: "bind", signature: "bind(Scene target)" },
    { name: "refresh", signature: "refresh()" },
    { name: "setTheme", signature: "setTheme(String themeName)" },
    { name: "currentTheme", signature: "currentTheme()" },
    { name: "availableThemes", signature: "availableThemes()" },
    { name: "themesDirectory", signature: "themesDirectory()" },
    { name: "applyStylesheet", signature: "applyStylesheet()" },
  ],
} as const satisfies JavaMirrorFile;

export type MiniCThemeTokens = Record<string, string>;

const DEFAULT_THEME = "dark";
const CUSTOM_THEMES_STORAGE_KEY = "minic.uiweb.themes";
const builtInThemes = new Map<string, MiniCThemeTokens>([
  ["dark", darkTheme as MiniCThemeTokens],
  ["light", lightTheme as MiniCThemeTokens],
  ["sepia", sepiaTheme as MiniCThemeTokens],
  ["solarized", solarizedTheme as MiniCThemeTokens],
]);

let currentThemeName = MiniCSettings.theme();
let bound = false;

export class ThemeManager {
  static readonly mirror = themeManagerMirror;

  static bind(): void {
    MiniCSettings.load();
    currentThemeName = MiniCSettings.theme();
    if (!bound) {
      MiniCSettings.addUiScaleChangeListener(applyUiScale);
      bound = true;
    }
    ThemeManager.refresh();
  }

  static refresh(): void {
    const theme = themeFor(currentThemeName) ?? themeFor(DEFAULT_THEME);
    if (theme !== null) {
      applyTokens(theme);
    }
    applyUiScale();
  }

  static setTheme(themeName: string): void {
    currentThemeName = themeFor(themeName) === null ? DEFAULT_THEME : themeName;
    MiniCSettings.setTheme(currentThemeName);
    ThemeManager.refresh();
  }

  static currentTheme(): string {
    return currentThemeName;
  }

  static availableThemes(): readonly string[] {
    return [...new Set([...builtInThemes.keys(), ...customThemes().keys()])].sort();
  }

  static themesDirectory(): string {
    return "config/themes";
  }

  static async importTheme(file: File): Promise<string> {
    const text = await file.text();
    const tokens = parseTheme(text);
    const name = themeNameFromFile(file.name);
    const themes = customThemes();
    themes.set(name, tokens);
    saveCustomThemes(themes);
    ThemeManager.setTheme(name);
    return name;
  }
}

function themeFor(name: string): MiniCThemeTokens | null {
  return builtInThemes.get(name) ?? customThemes().get(name) ?? null;
}

function customThemes(): Map<string, MiniCThemeTokens> {
  if (typeof window === "undefined") {
    return new Map();
  }
  const raw = window.localStorage.getItem(CUSTOM_THEMES_STORAGE_KEY);
  if (raw === null) {
    return new Map();
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!isRecord(parsed)) {
      return new Map();
    }
    const themes = new Map<string, MiniCThemeTokens>();
    Object.entries(parsed).forEach(([name, value]) => {
      if (isThemeTokens(value)) {
        themes.set(name, value);
      }
    });
    return themes;
  } catch {
    return new Map();
  }
}

function saveCustomThemes(themes: ReadonlyMap<string, MiniCThemeTokens>): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(CUSTOM_THEMES_STORAGE_KEY, JSON.stringify(Object.fromEntries(themes), null, 2));
}

function parseTheme(text: string): MiniCThemeTokens {
  const parsed: unknown = JSON.parse(text);
  if (!isThemeTokens(parsed)) {
    throw new Error("主题 JSON 必须是字符串 token 映射。");
  }
  return parsed;
}

function isThemeTokens(value: unknown): value is MiniCThemeTokens {
  return isRecord(value) && Object.values(value).every((entry) => typeof entry === "string");
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function themeNameFromFile(name: string): string {
  const withoutExtension = name.replace(/\.json$/i, "").trim();
  return withoutExtension.length > 0 ? withoutExtension : `theme-${Date.now()}`;
}

function applyTokens(theme: MiniCThemeTokens): void {
  if (typeof document === "undefined") {
    return;
  }
  Object.entries(theme).forEach(([key, value]) => {
    document.documentElement.style.setProperty(cssVariableName(key), value);
  });
  document.documentElement.style.setProperty(
    "--minic-accent-green",
    theme["accent.green"] ?? theme["accent.done_border"] ?? theme["graph.leaf_stroke"] ?? "#345b34",
  );
}

function cssVariableName(tokenName: string): string {
  return `--minic-${tokenName.replace(/[._]/g, "-")}`;
}

function applyUiScale(): void {
  if (typeof document === "undefined") {
    return;
  }
  const scale = MiniCSettings.uiScale();
  document.documentElement.style.setProperty("--minic-ui-scale", String(scale));
  document.body?.style.setProperty("zoom", String(scale));
}

ThemeManager.bind();

export default ThemeManager;
