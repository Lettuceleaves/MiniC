import { MiniCControlCommand } from "./MiniCControlCommand";
import type { JavaMirrorFile } from "../translation/javaMirror";
import { requireValue } from "../translation/uiTypes";

export const miniCCommandRegistryMirror = {
  "javaPath": "src/main/java/minic/uilocal/control/MiniCCommandRegistry.java",
  "webPath": "uiweb/src/control/MiniCCommandRegistry.ts",
  "packageName": "minic.uilocal.control",
  "exportName": "MiniCCommandRegistry",
  "kind": "class",
  "imports": [
    "java.util.LinkedHashMap",
    "java.util.Map",
    "java.util.Objects",
    "java.util.Optional"
  ],
  "fields": [
    {
      "name": "commands",
      "signature": "private final Map<String,MiniCControlCommand>commands="
    }
  ],
  "methods": [
    {
      "name": "command",
      "signature": "command(String id)"
    },
    {
      "name": "enabled",
      "signature": "enabled(String id)"
    },
    {
      "name": "execute",
      "signature": "execute(String id)"
    },
    {
      "name": "register",
      "signature": "register(MiniCControlCommand command)"
    }
  ]
} as const satisfies JavaMirrorFile;

export class MiniCCommandRegistry {
  static readonly mirror = miniCCommandRegistryMirror;

  readonly mirror = miniCCommandRegistryMirror;

  private readonly commands = new Map<string, MiniCControlCommand>();

  register(command: MiniCControlCommand): void {
    const safeCommand = requireValue(command, "command");
    this.commands.set(safeCommand.id(), safeCommand);
  }

  command(id: string): MiniCControlCommand | undefined {
    return this.commands.get(id);
  }

  enabled(id: string): boolean {
    return this.command(id)?.enabled()() ?? false;
  }

  execute(id: string): boolean {
    const command = this.commands.get(id);
    if (!command || !command.enabled()()) {
      return false;
    }
    command.action()();
    return true;
  }

  commandIds(): readonly string[] {
    return [...this.commands.keys()];
  }

  summary(): string {
    return `MiniCCommandRegistry: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCCommandRegistry;
