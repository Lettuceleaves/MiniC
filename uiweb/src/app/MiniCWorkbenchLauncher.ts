import type { JavaMirrorFile } from "../translation/javaMirror";

export const miniCWorkbenchLauncherMirror = {
  "javaPath": "src/main/java/minic/uilocal/app/MiniCWorkbenchLauncher.java",
  "webPath": "uiweb/src/app/MiniCWorkbenchLauncher.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCWorkbenchLauncher",
  "kind": "class",
  "imports": [
    "javafx.application.Application"
  ],
  "fields": [],
  "methods": [
    {
      "name": "main",
      "signature": "main(String[] args)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCWorkbenchLauncher {
  static readonly mirror = miniCWorkbenchLauncherMirror;

  readonly mirror = miniCWorkbenchLauncherMirror;

  summary(): string {
    return `MiniCWorkbenchLauncher: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCWorkbenchLauncher;
