package minic.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket subscription hub for session state events.
 */
public final class MiniCWebSocketHub {
    private static final TypeReference<Map<String, String>> STRING_FIELDS = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<WsContext> sockets = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<WsContext, Set<Subscription>> subscriptions = new ConcurrentHashMap<>();

    public void register(WsConfig config) {
        config.onConnect(context -> {
            sockets.add(context);
            subscriptions.put(context, ConcurrentHashMap.newKeySet());
        });
        config.onMessage(context -> handleMessage(context, context.message()));
        config.onClose(context -> remove(context));
        config.onError(context -> remove(context));
    }

    public void publish(String type, String scope, String sessionId, long version) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(sessionId, "sessionId");
        String event = eventJson(type, scope, sessionId, version);
        for (WsContext socket : sockets) {
            if (subscriptions.getOrDefault(socket, Set.of()).contains(new Subscription(scope, sessionId))) {
                send(socket, event);
            }
        }
    }

    private void handleMessage(WsContext context, String message) {
        try {
            Map<String, String> fields = parseMessage(message);
            String type = field(fields, "type");
            if ("ping".equals(type)) {
                send(context, json(Map.of("type", "pong")));
                return;
            }
            if ("subscribe".equals(type)) {
                String scope = field(fields, "scope");
                String sessionId = field(fields, "sessionId");
                subscriptions.computeIfAbsent(context, ignored -> ConcurrentHashMap.newKeySet())
                        .add(new Subscription(scope, sessionId));
                send(context, eventJson("subscribed", scope, sessionId, 0));
                return;
            }
            if ("unsubscribe".equals(type)) {
                String scope = field(fields, "scope");
                String sessionId = field(fields, "sessionId");
                subscriptions.computeIfAbsent(context, ignored -> ConcurrentHashMap.newKeySet())
                        .remove(new Subscription(scope, sessionId));
                send(context, eventJson("unsubscribed", scope, sessionId, 0));
                return;
            }
            send(context, errorJson("unknown websocket message"));
        } catch (IllegalArgumentException exception) {
            send(context, errorJson(exception.getMessage()));
        }
    }

    private void remove(WsContext context) {
        sockets.remove(context);
        subscriptions.remove(context);
    }

    private Map<String, String> parseMessage(String message) {
        try {
            return objectMapper.readValue(message == null ? "{}" : message, STRING_FIELDS);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("invalid websocket message", exception);
        }
    }

    private String field(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value != null && !value.isBlank()) {
            return value;
        }
        throw new IllegalArgumentException("missing websocket field: " + name);
    }

    private void send(WsContext context, String message) {
        try {
            context.send(message);
        } catch (RuntimeException ignored) {
            remove(context);
        }
    }

    private String eventJson(String type, String scope, String sessionId, long version) {
        return json(Map.of("type", type, "scope", scope, "sessionId", sessionId, "version", version));
    }

    private String errorJson(String message) {
        return json(Map.of("type", "error", "message", message));
    }

    private String json(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to encode websocket payload", exception);
        }
    }

    private record Subscription(String scope, String sessionId) {
    }
}
