import { MiniCSampleProgram } from "./MiniCSampleProgram";
import type { JavaMirrorFile } from "../translation/javaMirror";

export const miniCSampleProgramsMirror = {
  "javaPath": "src/main/java/minic/uilocal/editor/MiniCSamplePrograms.java",
  "webPath": "uiweb/src/editor/MiniCSamplePrograms.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCSamplePrograms",
  "kind": "class",
  "imports": [
    "java.util.List"
  ],
  "fields": [
    {
      "name": "SAMPLES",
      "signature": "private static final List<MiniCSampleProgram>SAMPLES="
    }
  ],
  "methods": [
    {
      "name": "all",
      "signature": "all()"
    },
    {
      "name": "defaultSample",
      "signature": "defaultSample()"
    }
  ]
} as const satisfies JavaMirrorFile;

const samples = Object.freeze([
  new MiniCSampleProgram("main.mc", "int main() {\n    return 0;\n}\n"),
  new MiniCSampleProgram("arithmetic.mc", "int main() {\n    int x = 1 + 2 * 3;\n    return x;\n}\n"),
  new MiniCSampleProgram("if_else.mc", "int main() {\n    int x = 7;\n    if (x > 3) {\n        return x;\n    }\n    return 0;\n}\n"),
]);

export class MiniCSamplePrograms {
  static readonly mirror = miniCSampleProgramsMirror;

  static readonly SAMPLES: readonly MiniCSampleProgram[] = samples;

  readonly mirror = miniCSampleProgramsMirror;

  static all(): readonly MiniCSampleProgram[] {
    return MiniCSamplePrograms.SAMPLES;
  }

  static defaultSample(): MiniCSampleProgram {
    return MiniCSamplePrograms.SAMPLES[0];
  }

  all(): readonly MiniCSampleProgram[] {
    return MiniCSamplePrograms.all();
  }

  defaultSample(): MiniCSampleProgram {
    return MiniCSamplePrograms.defaultSample();
  }

  summary(): string {
    return `MiniCSamplePrograms: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCSamplePrograms;
