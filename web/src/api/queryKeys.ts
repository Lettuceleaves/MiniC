export const queryKeys = {
  health: ["health"] as const,
  compile: {
    all: ["compile"] as const,
    session: (sessionId: string) => ["compile", "session", sessionId] as const,
    snapshot: (sessionId: string) => ["compile", "session", sessionId, "snapshot"] as const,
    state: (sessionId: string) => ["compile", "session", sessionId, "state"] as const,
  },
  debug: {
    all: ["debug"] as const,
    session: (sessionId: string) => ["debug", "session", sessionId] as const,
    snapshot: (sessionId: string) => ["debug", "session", sessionId, "snapshot"] as const,
    state: (sessionId: string) => ["debug", "session", sessionId, "state"] as const,
  },
  settings: {
    all: ["settings"] as const,
    snapshot: ["settings", "snapshot"] as const,
    themes: ["settings", "themes"] as const,
  },
};
