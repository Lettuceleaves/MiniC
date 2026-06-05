import { useEffect } from "react";
import { useQueryClient, type QueryClient } from "@tanstack/react-query";

import { queryKeys } from "./queryKeys";

export type MiniCSubscriptionScope = "compile" | "debug" | "settings";

export type MiniCSubscription = {
  scope: MiniCSubscriptionScope;
  sessionId: string;
};

export type MiniCWebSocketEvent = {
  scope?: MiniCSubscriptionScope;
  sessionId?: string;
  type: string;
  version?: number;
};

export interface MiniCWebSocketLike {
  addEventListener(type: "open" | "message" | "close" | "error", listener: EventListener): void;
  close(): void;
  removeEventListener(type: "open" | "message" | "close" | "error", listener: EventListener): void;
  send(data: string): void;
}

export type MiniCWebSocketController = {
  close: () => void;
};

export type MiniCWebSocketOptions = {
  enabled?: boolean;
  maxReconnects?: number;
  reconnectDelayMillis?: number;
  socketFactory?: (url: string) => MiniCWebSocketLike;
  url?: string;
};

type ConnectOptions = Required<Pick<MiniCWebSocketOptions, "maxReconnects" | "reconnectDelayMillis" | "socketFactory" | "url">> & {
  queryClient: QueryClient;
  subscriptions: readonly MiniCSubscription[];
};

type QueryKey = readonly unknown[];

export function useMiniCWebSocket(
  subscriptions: readonly MiniCSubscription[],
  options: MiniCWebSocketOptions = {},
): void {
  const queryClient = useQueryClient();
  const subscriptionKey = JSON.stringify(subscriptions);

  useEffect(() => {
    if (options.enabled === false) {
      return undefined;
    }
    const controller = connectMiniCWebSocket({
      maxReconnects: options.maxReconnects ?? 5,
      queryClient,
      reconnectDelayMillis: options.reconnectDelayMillis ?? 1000,
      socketFactory: options.socketFactory ?? defaultSocketFactory,
      subscriptions,
      url: options.url ?? miniCWebSocketUrl("/ws"),
    });
    return () => {
      controller.close();
    };
  }, [
    options.enabled,
    options.maxReconnects,
    options.reconnectDelayMillis,
    options.socketFactory,
    options.url,
    queryClient,
    subscriptionKey,
    subscriptions,
  ]);
}

export function connectMiniCWebSocket(options: ConnectOptions): MiniCWebSocketController {
  let reconnectAttempts = 0;
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
  let socket: MiniCWebSocketLike | undefined;
  let stopped = false;

  const connect = () => {
    if (stopped) {
      return;
    }
    const currentReconnectAttempt = reconnectAttempts;
    const nextSocket = options.socketFactory(options.url);
    socket = nextSocket;

    const handleOpen: EventListener = () => {
      for (const subscription of options.subscriptions) {
        nextSocket.send(JSON.stringify({ type: "subscribe", ...subscription }));
      }
      if (currentReconnectAttempt > 0) {
        refetchSubscriptions(options.queryClient, options.subscriptions);
      }
    };
    const handleMessage: EventListener = (event) => {
      const message = parseMiniCEvent(messageData(event));
      if (message == null) {
        return;
      }
      for (const queryKey of affectedQueryKeys(message)) {
        void options.queryClient.invalidateQueries({ queryKey });
      }
    };
    const handleClose: EventListener = () => {
      cleanupSocket(nextSocket, handleOpen, handleMessage, handleClose);
      scheduleReconnect();
    };

    nextSocket.addEventListener("open", handleOpen);
    nextSocket.addEventListener("message", handleMessage);
    nextSocket.addEventListener("close", handleClose);
    nextSocket.addEventListener("error", handleClose);
  };

  const scheduleReconnect = () => {
    if (stopped || reconnectAttempts >= options.maxReconnects) {
      return;
    }
    reconnectAttempts += 1;
    reconnectTimer = setTimeout(connect, options.reconnectDelayMillis);
  };

  connect();

  return {
    close: () => {
      stopped = true;
      if (reconnectTimer !== undefined) {
        clearTimeout(reconnectTimer);
      }
      socket?.close();
    },
  };
}

export function miniCWebSocketUrl(path: string): string {
  const protocol = globalThis.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${globalThis.location.host}${path}`;
}

export function parseMiniCEvent(data: unknown): MiniCWebSocketEvent | null {
  if (typeof data !== "string") {
    return null;
  }
  try {
    const parsed = JSON.parse(data) as unknown;
    if (!isRecord(parsed) || typeof parsed.type !== "string") {
      return null;
    }
    const event: MiniCWebSocketEvent = { type: parsed.type };
    if (isScope(parsed.scope)) {
      event.scope = parsed.scope;
    }
    if (typeof parsed.sessionId === "string") {
      event.sessionId = parsed.sessionId;
    }
    if (typeof parsed.version === "number" && Number.isFinite(parsed.version)) {
      event.version = parsed.version;
    }
    return event;
  } catch {
    return null;
  }
}

export function affectedQueryKeys(event: MiniCWebSocketEvent): QueryKey[] {
  if (event.scope === "compile" && event.sessionId != null) {
    return [queryKeys.compile.session(event.sessionId)];
  }
  if (event.scope === "debug" && event.sessionId != null) {
    return [queryKeys.debug.session(event.sessionId)];
  }
  if (event.scope === "settings") {
    return [queryKeys.settings.all];
  }
  return [];
}

function refetchSubscriptions(queryClient: QueryClient, subscriptions: readonly MiniCSubscription[]): void {
  for (const subscription of subscriptions) {
    void queryClient.refetchQueries({ queryKey: subscriptionQueryKey(subscription) });
  }
}

function subscriptionQueryKey(subscription: MiniCSubscription): QueryKey {
  if (subscription.scope === "compile") {
    return queryKeys.compile.session(subscription.sessionId);
  }
  if (subscription.scope === "debug") {
    return queryKeys.debug.session(subscription.sessionId);
  }
  return queryKeys.settings.all;
}

function cleanupSocket(
  socket: MiniCWebSocketLike,
  handleOpen: EventListener,
  handleMessage: EventListener,
  handleClose: EventListener,
): void {
  socket.removeEventListener("open", handleOpen);
  socket.removeEventListener("message", handleMessage);
  socket.removeEventListener("close", handleClose);
  socket.removeEventListener("error", handleClose);
}

function messageData(event: Event): unknown {
  return "data" in event ? event.data : undefined;
}

function defaultSocketFactory(url: string): MiniCWebSocketLike {
  return new WebSocket(url);
}

function isScope(value: unknown): value is MiniCSubscriptionScope {
  return value === "compile" || value === "debug" || value === "settings";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
