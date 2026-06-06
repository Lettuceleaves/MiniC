import { useEffect } from "react";
import { MiniCSettings, ThemeManager } from "../settings";
import type { JavaMirrorFile } from "../translation/javaMirror";
import MiniCWorkbenchShell from "../workbench/MiniCWorkbenchShell";

export const miniCWorkbenchAppMirror = {
  "javaPath": "src/main/java/minic/uilocal/app/MiniCWorkbenchApp.java",
  "webPath": "uiweb/src/app/MiniCWorkbenchApp.tsx",
  "packageName": "minic.uilocal",
  "exportName": "MiniCWorkbenchApp",
  "kind": "component",
  "imports": [
    "javafx.application.Application",
    "javafx.scene.Scene",
    "javafx.stage.Stage",
    "minic.color.ThemeManager",
    "minic.settings.MiniCSettings"
  ],
  "fields": [
    {
      "name": "DEFAULT_HEIGHT",
      "signature": "public static final double DEFAULT_HEIGHT="
    },
    {
      "name": "DEFAULT_WIDTH",
      "signature": "public static final double DEFAULT_WIDTH="
    },
    {
      "name": "TITLE",
      "signature": "public static final String TITLE="
    }
  ],
  "methods": [
    {
      "name": "main",
      "signature": "main(String[] args)"
    },
    {
      "name": "start",
      "signature": "start(Stage stage)"
    }
  ]
} as const satisfies JavaMirrorFile;

export function MiniCWorkbenchApp() {
  useEffect(() => {
    MiniCSettings.load();
    ThemeManager.bind();
  }, []);

  return <MiniCWorkbenchShell title={MiniCWorkbenchApp.TITLE} />;
}

MiniCWorkbenchApp.TITLE = "MiniC Workbench";
MiniCWorkbenchApp.DEFAULT_WIDTH = 1280;
MiniCWorkbenchApp.DEFAULT_HEIGHT = 820;
MiniCWorkbenchApp.mirror = miniCWorkbenchAppMirror;

export default MiniCWorkbenchApp;
