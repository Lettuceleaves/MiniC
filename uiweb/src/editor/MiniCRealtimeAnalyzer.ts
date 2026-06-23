import type { JavaMirrorFile } from "../translation/javaMirror";
import type { UiRealtimeAnalysisDto } from "../translation/uiapi";
import { requireValue } from "../translation/uiTypes";
import type { MiniCRealtimeAnalysisApiAdapter } from "../workbench/MiniCWorkbenchViewModel";

export const miniCRealtimeAnalyzerMirror = {
  "javaPath": "src/main/java/minic/uilocal/editor/MiniCRealtimeAnalyzer.java",
  "webPath": "uiweb/src/editor/MiniCRealtimeAnalyzer.ts",
  "packageName": "minic.uilocal",
  "exportName": "MiniCRealtimeAnalyzer",
  "kind": "class",
  "imports": [
    "java.util.Objects",
    "java.util.concurrent.BlockingQueue",
    "java.util.concurrent.LinkedBlockingQueue",
    "javafx.application.Platform",
    "minic.uiapi.MiniCRealtimeAnalysisApi",
    "minic.uiapi.UiRealtimeAnalysisDto"
  ],
  "fields": [
    {
      "name": "api",
      "signature": "private final MiniCRealtimeAnalysisApi api="
    },
    {
      "name": "nextVersion",
      "signature": "private long nextVersion"
    },
    {
      "name": "queue",
      "signature": "private final BlockingQueue<Request>queue="
    },
    {
      "name": "resultSink",
      "signature": "private final ResultSink resultSink"
    },
    {
      "name": "running",
      "signature": "private volatile boolean running="
    },
    {
      "name": "worker",
      "signature": "private Thread worker"
    }
  ],
  "methods": [
    {
      "name": "close",
      "signature": "close()"
    },
    {
      "name": "drainLatest",
      "signature": "drainLatest(Request request)"
    },
    {
      "name": "ensureStarted",
      "signature": "ensureStarted()"
    },
    {
      "name": "Request",
      "signature": "Request(String sourceName,String sourceText,long version)"
    },
    {
      "name": "runLoop",
      "signature": "runLoop()"
    },
    {
      "name": "submit",
      "signature": "submit(String sourceName,String sourceText)"
    }
  ]
} as const satisfies JavaMirrorFile;

export type MiniCRealtimeResultSink = (result: UiRealtimeAnalysisDto) => void;

interface Request {
  readonly sourceName: string;
  readonly sourceText: string;
  readonly version: number;
}

export class MiniCRealtimeAnalyzer {
  static readonly mirror = miniCRealtimeAnalyzerMirror;

  readonly mirror = miniCRealtimeAnalyzerMirror;

  private running = true;

  private nextVersion = 0;

  private pendingRequest: Request | null = null;

  private scheduledHandle: number | null = null;

  constructor(
    private readonly api: MiniCRealtimeAnalysisApiAdapter,
    private readonly resultSink: MiniCRealtimeResultSink,
  ) {
    requireValue(api, "api");
    requireValue(resultSink, "resultSink");
  }

  submit(sourceName: string, sourceText: string): void {
    if (!this.running) {
      return;
    }
    this.pendingRequest = {
      sourceName: requireValue(sourceName, "sourceName"),
      sourceText: requireValue(sourceText, "sourceText"),
      version: this.nextVersion + 1,
    };
    this.nextVersion += 1;
    this.ensureStarted();
  }

  close(): void {
    this.running = false;
    if (this.scheduledHandle !== null) {
      window.clearTimeout(this.scheduledHandle);
      this.scheduledHandle = null;
    }
    this.pendingRequest = null;
  }

  ensureStarted(): void {
    if (this.scheduledHandle !== null) {
      return;
    }
    this.scheduledHandle = window.setTimeout(() => {
      void this.runLoop().catch(() => {
        this.scheduledHandle = null;
      });
    }, 0);
  }

  async runLoop(): Promise<void> {
    this.scheduledHandle = null;
    if (!this.running || this.pendingRequest === null) {
      return;
    }
    const request = this.drainLatest(this.pendingRequest);
    this.pendingRequest = null;
    try {
      const result = await this.api.analyze(request.sourceName, request.sourceText, request.version);
      if (this.running && result.version === request.version) {
        this.resultSink(result);
      }
    } catch {
      // Realtime diagnostics are opportunistic; the workbench ViewModel owns user-facing failures.
    }
    if (this.pendingRequest !== null) {
      this.ensureStarted();
    }
  }

  drainLatest(request: Request): Request {
    return this.pendingRequest ?? request;
  }

  summary(): string {
    return `MiniCRealtimeAnalyzer: ${this.mirror.methods.length} methods, ${this.mirror.fields.length} fields`;
  }
}

export default MiniCRealtimeAnalyzer;
