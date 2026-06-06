import type {
  UiDebugAsmViewDto,
  UiDebugAstViewDto,
  UiDebugDataStructureViewDto,
  UiDebugIrViewDto,
  UiDebugMetadataViewDto,
  UiDebugStateDto,
  UiGlobalDataDto,
  UiRealtimeAnalysisDto,
  UiStageDataDto,
  UiStageVisualDto,
  UiControlResultDto,
  UiCurrentStateDto,
  UiLexerTokenVisualDto,
} from "../translation/uiapi";

export interface MiniCUiApiErrorBody {
  readonly status: number;
  readonly method: string;
  readonly path: string;
  readonly message: string;
}

export class MiniCUiApiError extends Error {
  constructor(readonly body: MiniCUiApiErrorBody) {
    super(body.message);
    this.name = "MiniCUiApiError";
  }
}

export interface MiniCUiApiSessionResponse {
  readonly sessionId: string;
}

export interface MiniCUiApiStatusResponse {
  readonly status: string;
}

export interface MiniCUiApiSourceRequest {
  readonly sourceName: string;
  readonly sourceText: string;
}

export interface MiniCUiApiRealtimeAnalyzeRequest extends MiniCUiApiSourceRequest {
  readonly version: number;
}

export interface MiniCUiApiExecutionInputRequest {
  readonly standardInput: string;
}

export interface MiniCUiApiClientDtoMap {
  readonly control: UiControlResultDto;
  readonly currentState: UiCurrentStateDto;
  readonly stageData: UiStageDataDto;
  readonly stageVisual: UiStageVisualDto;
  readonly globalData: UiGlobalDataDto;
  readonly realtimeAnalysis: UiRealtimeAnalysisDto;
  readonly lexerTokens: readonly UiLexerTokenVisualDto[];
  readonly debugState: UiDebugStateDto;
  readonly debugMetadata: UiDebugMetadataViewDto;
  readonly debugDataStructure: UiDebugDataStructureViewDto;
  readonly debugAst: UiDebugAstViewDto;
  readonly debugIr: UiDebugIrViewDto;
  readonly debugAsm: UiDebugAsmViewDto;
}

let sharedClient: MiniCUiApiClient | null = null;

export function defaultMiniCUiApiBaseUrl(): string {
  const configured = import.meta.env.VITE_MINIC_UIAPI_BASE_URL;
  if (typeof configured === "string" && configured.trim().length > 0) {
    return configured.trim();
  }
  return "http://127.0.0.1:18080";
}

export function defaultMiniCUiApiClient(): MiniCUiApiClient {
  if (sharedClient === null) {
    sharedClient = new MiniCUiApiClient(defaultMiniCUiApiBaseUrl());
  }
  return sharedClient;
}

export class MiniCUiApiClient {
  private readonly baseUrl: URL;

  constructor(baseUrl: string) {
    const normalized = baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`;
    this.baseUrl = new URL(normalized);
  }

  get<T>(path: string): Promise<T> {
    return this.request("GET", path);
  }

  post<T>(path: string, body: object = {}): Promise<T> {
    return this.request("POST", path, body);
  }

  delete<T>(path: string): Promise<T> {
    return this.request("DELETE", path);
  }

  private async request<T>(method: string, path: string, body?: object): Promise<T> {
    const response = await fetch(this.resolve(path), {
      method,
      headers: body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    if (!response.ok) {
      throw new MiniCUiApiError(errorBody(response, text, method, path));
    }
    if (text.trim().length === 0) {
      throw new MiniCUiApiError({
        status: 502,
        method,
        path,
        message: "UIAPI returned an empty JSON response",
      });
    }
    return parseJson(text) as T;
  }

  private resolve(path: string): string {
    return new URL(path.replace(/^\/+/, ""), this.baseUrl).toString();
  }
}

function errorBody(response: Response, text: string, method: string, path: string): MiniCUiApiErrorBody {
  const parsed = text.trim().length === 0 ? null : parseJson(text);
  if (isMiniCUiApiErrorBody(parsed)) {
    return parsed;
  }
  return {
    status: response.status,
    method,
    path,
    message: response.statusText || `UIAPI request failed with HTTP ${response.status}`,
  };
}

function parseJson(text: string): unknown {
  return JSON.parse(text) as unknown;
}

function isMiniCUiApiErrorBody(value: unknown): value is MiniCUiApiErrorBody {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.status === "number" &&
    typeof candidate.method === "string" &&
    typeof candidate.path === "string" &&
    typeof candidate.message === "string"
  );
}

export default MiniCUiApiClient;
