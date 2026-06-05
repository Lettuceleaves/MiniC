import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, test, vi } from "vitest";
import { createElement, type ReactNode } from "react";

import {
  createMiniCClient,
  MiniCWebError,
  type MiniCClient,
} from "./minicClient";
import { queryKeys } from "./queryKeys";
import { useCompileCommand } from "./useCompileSession";
import { useDebugCommand } from "./useDebugSession";
import { connectMiniCWebSocket, type MiniCWebSocketLike } from "./useMiniCWebSocket";

describe("MiniC client", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  test("returns typed health responses", async () => {
    const fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockResolvedValueOnce(jsonResponse({ status: "ok" }));

    const client = createMiniCClient();

    await expect(client.getHealth()).resolves.toEqual({ status: "ok" });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/health",
      expect.objectContaining({ method: "GET" }),
    );
  });

  test("throws structured web errors", async () => {
    const fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockResolvedValueOnce(jsonResponse({
      code: "session-not-found",
      message: "compile session not found: missing",
      status: 404,
    }, 404));

    const client = createMiniCClient();
    const request = client.getCompileState("missing");

    await expect(request).rejects.toMatchObject({
      status: 404,
      code: "session-not-found",
      message: "compile session not found: missing",
    });
    await expect(request).rejects.toBeInstanceOf(MiniCWebError);
  });

  test("invalidates compile queries after command mutations", async () => {
    const queryClient = new QueryClient();
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");
    const client: Pick<MiniCClient, "runCompileCommand"> = {
      runCompileCommand: vi.fn<MiniCClient["runCompileCommand"]>().mockResolvedValue({
        outcome: "OK",
        stage: "lexer",
        title: "next",
        description: "advanced",
        diagnostics: [],
      }),
    };

    const { result } = renderHook(() => useCompileCommand({ client }), {
      wrapper: wrapperFor(queryClient),
    });

    await act(async () => {
      await result.current.mutateAsync({ sessionId: "c1", command: "next" });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.compile.session("c1") });
  });

  test("invalidates debug queries after command mutations", async () => {
    const queryClient = new QueryClient();
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");
    const client: Pick<MiniCClient, "runDebugCommand"> = {
      runDebugCommand: vi.fn<MiniCClient["runDebugCommand"]>().mockResolvedValue(
        {} as Awaited<ReturnType<MiniCClient["runDebugCommand"]>>,
      ),
    };

    const { result } = renderHook(() => useDebugCommand({ client }), {
      wrapper: wrapperFor(queryClient),
    });

    await act(async () => {
      await result.current.mutateAsync({ sessionId: "d1", command: "step-over" });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.debug.session("d1") });
  });

  test("invalidates on events and refetches subscribed snapshots after reconnect", async () => {
    vi.useFakeTimers();
    const sockets: FakeSocket[] = [];
    const queryClient = new QueryClient();
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");
    const refetchSpy = vi.spyOn(queryClient, "refetchQueries");

    const controller = connectMiniCWebSocket({
      maxReconnects: 1,
      queryClient,
      reconnectDelayMillis: 10,
      socketFactory: (url) => {
        const socket = new FakeSocket(url);
        sockets.push(socket);
        return socket;
      },
      subscriptions: [{ scope: "compile", sessionId: "c1" }],
      url: "/ws",
    });

    sockets[0]?.open();
    expect(sockets[0]?.sent).toContain("{\"type\":\"subscribe\",\"scope\":\"compile\",\"sessionId\":\"c1\"}");

    sockets[0]?.message("{\"type\":\"compile.state.changed\",\"scope\":\"compile\",\"sessionId\":\"c1\",\"version\":2}");
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.compile.session("c1") });

    sockets[0]?.close();
    await vi.advanceTimersByTimeAsync(10);
    sockets[1]?.open();

    expect(sockets).toHaveLength(2);
    expect(refetchSpy).toHaveBeenCalledWith({ queryKey: queryKeys.compile.session("c1") });

    controller.close();
  });
});

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status,
  });
}

function wrapperFor(queryClient: QueryClient): (props: { children: ReactNode }) => ReactNode {
  return function Wrapper({ children }: { children: ReactNode }): ReactNode {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

class FakeSocket extends EventTarget implements MiniCWebSocketLike {
  readonly sent: string[] = [];

  constructor(readonly url: string) {
    super();
  }

  send(data: string): void {
    this.sent.push(data);
  }

  close(): void {
    this.dispatchEvent(new Event("close"));
  }

  open(): void {
    this.dispatchEvent(new Event("open"));
  }

  message(data: string): void {
    this.dispatchEvent(new MessageEvent("message", { data }));
  }
}
