import { MiniCSourceLine } from "./MiniCSourceLine";
import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiSourceRangeDto } from "../translation/uiTypes";

export const miniCSourceLineFactoryMirror = {
  "javaPath": "src/main/java/minic/uilocal/source/MiniCSourceLineFactory.java",
  "webPath": "uiweb/src/source/MiniCSourceLineFactory.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCSourceLineFactory",
  "kind": "class",
  "imports": [
    "minic.uiapi.UiSourceRangeDto",
    "java.util.ArrayList",
    "java.util.List"
  ],
  "fields": [],
  "methods": [
    {
      "name": "create",
      "signature": "create(String source, UiSourceRangeDto range)"
    },
    {
      "name": "intersects",
      "signature": "intersects(int lineStart, int lineEnd, UiSourceRangeDto range)"
    },
    {
      "name": "newlineWidth",
      "signature": "newlineWidth(String source, int lineEnd)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCSourceLineFactory {
  static readonly mirror = miniCSourceLineFactoryMirror;

  readonly mirror = miniCSourceLineFactoryMirror;

  create(source: string | null | undefined, range: UiSourceRangeDto | null | undefined): readonly MiniCSourceLine[] {
    const safeSource = source ?? "";
    const result: MiniCSourceLine[] = [];
    let lineNumber = 1;
    let lineStart = 0;
    for (let offset = 0; offset <= safeSource.length; offset += 1) {
      if (offset < safeSource.length && safeSource[offset] !== "\n" && safeSource[offset] !== "\r") {
        continue;
      }
      const lineEnd = offset;
      const focused = this.intersects(lineStart, lineEnd, range);
      result.push(new MiniCSourceLine(lineNumber, safeSource.slice(lineStart, lineEnd), focused));
      if (offset >= safeSource.length) {
        break;
      }
      const width = this.newlineWidth(safeSource, lineEnd);
      offset += Math.max(0, width - 1);
      lineStart = offset + 1;
      lineNumber += 1;
    }
    if (result.length === 0) {
      result.push(new MiniCSourceLine(1, "", false));
    }
    return result;
  }

  intersects(lineStart: number, lineEnd: number, range: UiSourceRangeDto | null | undefined): boolean {
    if (!range) {
      return false;
    }
    if (range.startOffset === range.endOffset) {
      return range.startOffset >= lineStart && range.startOffset <= lineEnd;
    }
    return range.startOffset <= lineEnd && range.endOffset >= lineStart;
  }

  newlineWidth(source: string, lineEnd: number): number {
    if (lineEnd >= source.length) {
      return 0;
    }
    if (source[lineEnd] === "\r" && lineEnd + 1 < source.length && source[lineEnd + 1] === "\n") {
      return 2;
    }
    return 1;
  }

  summary(): string {
    return `MiniCSourceLineFactory: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCSourceLineFactory;
