import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiControlResultDto } from "../translation/uiapi";
import type { MiniCWorkbenchViewModel } from "./MiniCWorkbenchViewModel";

export const miniCWorkbenchControllerMirror = {
  "javaPath": "src/main/java/minic/uilocal/workbench/MiniCWorkbenchController.java",
  "webPath": "uiweb/src/workbench/MiniCWorkbenchController.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCWorkbenchController",
  "kind": "class",
  "imports": [
    "minic.uiapi.UiControlResultDto",
    "java.util.Objects"
  ],
  "fields": [
    {
      "name": "viewModel",
      "signature": "private final MiniCWorkbenchViewModel viewModel;"
    }
  ],
  "methods": [
    {
      "name": "startDefaultSession",
      "signature": "startDefaultSession()"
    },
    {
      "name": "next",
      "signature": "next()"
    },
    {
      "name": "nextStage",
      "signature": "nextStage()"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCWorkbenchController {
  static readonly mirror = miniCWorkbenchControllerMirror;

  readonly mirror = miniCWorkbenchControllerMirror;

  constructor(private readonly viewModel: MiniCWorkbenchViewModel) {}

  startDefaultSession(): void {
    this.viewModel.loadSource(DEFAULT_SAMPLE.name, DEFAULT_SAMPLE.source);
    this.viewModel.startSession();
  }

  next(): UiControlResultDto {
    return this.viewModel.next();
  }

  nextStage(): UiControlResultDto {
    return this.viewModel.nextStage();
  }

  summary(): string {
    return `MiniCWorkbenchController: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

const DEFAULT_SAMPLE = {
  name: "hello.mc",
  source: ["int main() {", "  return 0;", "}"].join("\n"),
} as const;

export default MiniCWorkbenchController;
