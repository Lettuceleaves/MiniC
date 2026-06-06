import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiControlResultDto } from "../translation/uiapi";
import { MiniCSamplePrograms } from "../editor/MiniCSamplePrograms";
import type { MiniCWorkbenchViewModel } from "./MiniCWorkbenchViewModel";

export const miniCWorkbenchControllerMirror = {
  "javaPath": "src/main/java/minic/uilocal/workbench/MiniCWorkbenchController.java",
  "webPath": "uiweb/src/workbench/MiniCWorkbenchController.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCWorkbenchController",
  "kind": "class",
  "imports": [
    "java.util.Objects",
    "minic.uiapi.UiControlResultDto"
  ],
  "fields": [
    {
      "name": "viewModel",
      "signature": "private final MiniCWorkbenchViewModel viewModel"
    }
  ],
  "methods": [
    {
      "name": "next",
      "signature": "next()"
    },
    {
      "name": "nextStage",
      "signature": "nextStage()"
    },
    {
      "name": "startDefaultSession",
      "signature": "startDefaultSession()"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCWorkbenchController {
  static readonly mirror = miniCWorkbenchControllerMirror;

  readonly mirror = miniCWorkbenchControllerMirror;

  constructor(private readonly viewModel: MiniCWorkbenchViewModel) {}

  startDefaultSession(): void {
    const sample = MiniCSamplePrograms.defaultSample();
    void this.viewModel.loadSource(sample.name, sample.source)
      .then(() => this.viewModel.startSession());
  }

  next(): Promise<UiControlResultDto> {
    return this.viewModel.next();
  }

  nextStage(): Promise<UiControlResultDto> {
    return this.viewModel.nextStage();
  }

  summary(): string {
    return `MiniCWorkbenchController: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCWorkbenchController;
