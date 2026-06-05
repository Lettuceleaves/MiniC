import type { components, operations } from "./generated/schema";

type JsonContent<Response> = Response extends { content: { "application/json": infer Body } } ? Body : never;
type OperationResponses<Operation> = Operation extends { responses: infer Responses } ? Responses : never;
type OperationResponse<Operation, Status extends keyof OperationResponses<Operation>> =
  JsonContent<OperationResponses<Operation>[Status]>;
type OperationRequest<Operation> =
  Operation extends { requestBody?: { content: { "application/json": infer Body } } } ? Body : never;

export type HealthResponse = OperationResponse<operations["getHealth"], 200>;
export type CreateSessionRequest = OperationRequest<operations["createCompileSession"]>;
export type SessionCreatedResponse = OperationResponse<operations["createCompileSession"], 201>;
export type SessionClosedResponse = OperationResponse<operations["closeCompileSession"], 200>;
export type CommandInputRequest = OperationRequest<operations["runCompileCommand"]>;
export type CompileSnapshotResponse = OperationResponse<operations["getCompileSnapshot"], 200>;
export type CompileStateResponse = OperationResponse<operations["getCompileState"], 200>;
export type CompileCommandResponse = OperationResponse<operations["runCompileCommand"], 200>;
export type DebugSnapshotResponse = OperationResponse<operations["getDebugSnapshot"], 200>;
export type DebugStateResponse = OperationResponse<operations["getDebugState"], 200>;
export type DebugCommandResponse = OperationResponse<operations["runDebugCommand"], 200>;
export type BreakpointRequest = OperationRequest<operations["addDebugBreakpoint"]>;
export type RealtimeAnalysisRequest = OperationRequest<operations["analyzeRealtime"]>;
export type RealtimeAnalysisResponse = OperationResponse<operations["analyzeRealtime"], 200>;
export type SettingsSnapshot = OperationResponse<operations["getSettings"], 200>;
export type SettingsUpdateRequest = OperationRequest<operations["updateSettings"]>;
export type ThemeListResponse = OperationResponse<operations["listThemes"], 200>;
type WebErrorBody = components["schemas"]["WebError"];

type JsonBody = Record<string, unknown> | undefined;
type RequestOptions = Omit<RequestInit, "body"> & {
  body?: BodyInit | null;
  json?: JsonBody;
};

export type MiniCClient = {
  getHealth: () => Promise<HealthResponse>;
  createCompileSession: (request: CreateSessionRequest) => Promise<SessionCreatedResponse>;
  updateCompileSource: (sessionId: string, request: CreateSessionRequest) => Promise<CompileSnapshotResponse>;
  startCompileSession: (sessionId: string) => Promise<CompileSnapshotResponse>;
  runCompileCommand: (
    sessionId: string,
    command: string,
    input?: CommandInputRequest,
  ) => Promise<CompileCommandResponse>;
  getCompileState: (sessionId: string) => Promise<CompileStateResponse>;
  getCompileSnapshot: (sessionId: string) => Promise<CompileSnapshotResponse>;
  closeCompileSession: (sessionId: string) => Promise<SessionClosedResponse>;
  createDebugSession: (request: CreateSessionRequest) => Promise<SessionCreatedResponse>;
  updateDebugSource: (sessionId: string, request: CreateSessionRequest) => Promise<DebugSnapshotResponse>;
  startDebugSession: (sessionId: string) => Promise<DebugSnapshotResponse>;
  addDebugBreakpoint: (sessionId: string, line: number) => Promise<DebugStateResponse>;
  removeDebugBreakpoint: (sessionId: string, line: number) => Promise<DebugStateResponse>;
  runDebugCommand: (sessionId: string, command: string) => Promise<DebugCommandResponse>;
  getDebugState: (sessionId: string) => Promise<DebugStateResponse>;
  getDebugSnapshot: (sessionId: string) => Promise<DebugSnapshotResponse>;
  closeDebugSession: (sessionId: string) => Promise<SessionClosedResponse>;
  analyzeRealtime: (request: RealtimeAnalysisRequest) => Promise<RealtimeAnalysisResponse>;
  getSettings: () => Promise<SettingsSnapshot>;
  updateSettings: (request: SettingsUpdateRequest) => Promise<SettingsSnapshot>;
  listThemes: () => Promise<ThemeListResponse>;
};

export class MiniCWebError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "MiniCWebError";
  }
}

export function createMiniCClient(options: { baseUrl?: string } = {}): MiniCClient {
  const baseUrl = options.baseUrl ?? "";

  async function request<T>(path: string, init: RequestOptions = {}): Promise<T> {
    const { json, headers: initHeaders, ...rest } = init;
    const headers = new Headers(initHeaders);
    const body = json === undefined ? rest.body : JSON.stringify(json);
    if (json !== undefined && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }

    const fetchInit: RequestInit = {
      ...rest,
      headers,
    };
    if (body !== undefined) {
      fetchInit.body = body;
    }

    const response = await fetch(resolvePath(baseUrl, path), fetchInit);
    const responseBody = await readJson(response);
    if (!response.ok) {
      if (isWebErrorBody(responseBody)) {
        throw new MiniCWebError(response.status, responseBody.code, responseBody.message);
      }
      throw new MiniCWebError(response.status, "http-error", response.statusText || "MiniC web request failed");
    }
    return responseBody as T;
  }

  return {
    getHealth: () => request<HealthResponse>("/api/health", { method: "GET" }),
    createCompileSession: (input) =>
      request<SessionCreatedResponse>("/api/compile/sessions", { json: input, method: "POST" }),
    updateCompileSource: (sessionId, input) =>
      request<CompileSnapshotResponse>(`/api/compile/sessions/${encodePath(sessionId)}/source`, {
        json: input,
        method: "POST",
      }),
    startCompileSession: (sessionId) =>
      request<CompileSnapshotResponse>(`/api/compile/sessions/${encodePath(sessionId)}/start`, { method: "POST" }),
    runCompileCommand: (sessionId, command, input) =>
      request<CompileCommandResponse>(
        `/api/compile/sessions/${encodePath(sessionId)}/commands/${encodePath(command)}`,
        { json: input, method: "POST" },
      ),
    getCompileState: (sessionId) =>
      request<CompileStateResponse>(`/api/compile/sessions/${encodePath(sessionId)}/state`, { method: "GET" }),
    getCompileSnapshot: (sessionId) =>
      request<CompileSnapshotResponse>(`/api/compile/sessions/${encodePath(sessionId)}/snapshot`, { method: "GET" }),
    closeCompileSession: (sessionId) =>
      request<SessionClosedResponse>(`/api/compile/sessions/${encodePath(sessionId)}`, { method: "DELETE" }),
    createDebugSession: (input) =>
      request<SessionCreatedResponse>("/api/debug-sessions", { json: input, method: "POST" }),
    updateDebugSource: (sessionId, input) =>
      request<DebugSnapshotResponse>(`/api/debug-sessions/${encodePath(sessionId)}/source`, {
        json: input,
        method: "POST",
      }),
    startDebugSession: (sessionId) =>
      request<DebugSnapshotResponse>(`/api/debug-sessions/${encodePath(sessionId)}/start`, { method: "POST" }),
    addDebugBreakpoint: (sessionId, line) =>
      request<DebugStateResponse>(`/api/debug-sessions/${encodePath(sessionId)}/breakpoints/${String(line)}`, {
        method: "POST",
      }),
    removeDebugBreakpoint: (sessionId, line) =>
      request<DebugStateResponse>(`/api/debug-sessions/${encodePath(sessionId)}/breakpoints/${String(line)}`, {
        method: "DELETE",
      }),
    runDebugCommand: (sessionId, command) =>
      request<DebugCommandResponse>(`/api/debug-sessions/${encodePath(sessionId)}/${encodePath(command)}`, {
        method: "POST",
      }),
    getDebugState: (sessionId) =>
      request<DebugStateResponse>(`/api/debug-sessions/${encodePath(sessionId)}/state`, { method: "GET" }),
    getDebugSnapshot: (sessionId) =>
      request<DebugSnapshotResponse>(`/api/debug-sessions/${encodePath(sessionId)}/snapshot`, { method: "GET" }),
    closeDebugSession: (sessionId) =>
      request<SessionClosedResponse>(`/api/debug-sessions/${encodePath(sessionId)}`, { method: "DELETE" }),
    analyzeRealtime: (input) =>
      request<RealtimeAnalysisResponse>("/api/analysis/realtime", { json: input, method: "POST" }),
    getSettings: () => request<SettingsSnapshot>("/api/settings", { method: "GET" }),
    updateSettings: (input) => request<SettingsSnapshot>("/api/settings", { json: input, method: "PATCH" }),
    listThemes: () => request<ThemeListResponse>("/api/settings/themes", { method: "GET" }),
  };
}

function resolvePath(baseUrl: string, path: string): string {
  if (baseUrl.length === 0) {
    return path;
  }
  return `${baseUrl.replace(/\/$/, "")}${path}`;
}

function encodePath(value: string): string {
  return encodeURIComponent(value);
}

async function readJson(response: Response): Promise<unknown> {
  const text = await response.text();
  if (text.trim().length === 0) {
    return undefined;
  }
  return JSON.parse(text) as unknown;
}

function isWebErrorBody(value: unknown): value is WebErrorBody {
  if (!isRecord(value)) {
    return false;
  }
  return typeof value.code === "string"
    && typeof value.message === "string"
    && typeof value.status === "number";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
