import type { JavaMirrorFile } from "../translation/javaMirror";
import type { BooleanSupplier, Runnable } from "../translation/uiTypes";
import { requireValue } from "../translation/uiTypes";

export const miniCControlCommandMirror = {
  "javaPath": "src/main/java/minic/uilocal/control/MiniCControlCommand.java",
  "webPath": "uiweb/src/control/MiniCControlCommand.ts",
  "packageName": "minic.uilocal.control",
  "exportName": "MiniCControlCommand",
  "kind": "record",
  "imports": [
    "java.util.Objects",
    "java.util.function.BooleanSupplier"
  ],
  "fields": [],
  "methods": []
} as const satisfies JavaMirrorFile;

export class MiniCControlCommand {
  static readonly mirror = miniCControlCommandMirror;

  readonly mirror = miniCControlCommandMirror;

  private readonly commandId: string;

  private readonly commandLabel: string;

  private readonly enabledSupplier: BooleanSupplier;

  private readonly commandAction: Runnable;

  constructor(id: string, label: string, enabled: BooleanSupplier, action: Runnable) {
    this.commandId = requireValue(id, "id");
    this.commandLabel = requireValue(label, "label");
    this.enabledSupplier = requireValue(enabled, "enabled");
    this.commandAction = requireValue(action, "action");
  }

  id(): string {
    return this.commandId;
  }

  label(): string {
    return this.commandLabel;
  }

  enabled(): BooleanSupplier {
    return this.enabledSupplier;
  }

  action(): Runnable {
    return this.commandAction;
  }

  isEnabled(): boolean {
    return this.enabledSupplier();
  }

  run(): void {
    this.commandAction();
  }

  summary(): string {
    return `MiniCControlCommand: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCControlCommand;
