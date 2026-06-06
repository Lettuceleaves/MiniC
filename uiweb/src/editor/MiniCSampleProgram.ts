import type { JavaMirrorFile } from "../translation/javaMirror";
import { requireValue } from "../translation/uiTypes";

export const miniCSampleProgramMirror = {
  "javaPath": "src/main/java/minic/uilocal/editor/MiniCSampleProgram.java",
  "webPath": "uiweb/src/editor/MiniCSampleProgram.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCSampleProgram",
  "kind": "record",
  "imports": [
    "java.util.Objects"
  ],
  "fields": [],
  "methods": []
} as const satisfies JavaMirrorFile;

export class MiniCSampleProgram {
  static readonly mirror = miniCSampleProgramMirror;

  readonly mirror = miniCSampleProgramMirror;

  readonly name: string;

  readonly source: string;

  constructor(name: string, source: string) {
    this.name = requireValue(name, "name");
    this.source = requireValue(source, "source");
  }

  nameValue(): string {
    return this.name;
  }

  sourceValue(): string {
    return this.source;
  }

  summary(): string {
    return `MiniCSampleProgram: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCSampleProgram;
