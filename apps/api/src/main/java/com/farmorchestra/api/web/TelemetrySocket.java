package com.farmorchestra.api.web;

import com.farmorchestra.api.telemetry.TelemetryReading;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read-only WebSocket at {@code /ws/telemetry/stream} that pushes each fresh reading to every
 * connected dashboard. Each message is one {@link TelemetryReading} as JSON, with no wrapper.
 *
 * <p><b>Threading:</b> {@link #broadcast} runs on the HTTP request thread of the ingest call,
 * while connects and disconnects run on WebSocket threads. Hence a concurrent map and
 * thread-safe session wrappers. Registered by {@link ApiConfig}.
 */
@Component
public class TelemetrySocket extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(TelemetrySocket.class);
    private final JsonMapper mapper;

    /**
     * Open dashboard connections, keyed by session ID.
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * @param mapper Spring's configured JSON mapper, so WebSocket JSON matches REST JSON exactly
     *               (same date format, same field names)
     */
    public TelemetrySocket(JsonMapper mapper) { 
        this.mapper = mapper; 
    }

    /**
     * Registers a newly connected dashboard.
     *
     * @param session the new connection
     */
    @Override public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 5000, 65536));
    }

    /**
     * Forgets a client that disconnected, so we stop sending to it.
     *
     * @param session the closed connection
     * @param status  why it closed
     */
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    /**
     * Drops a client whose network connection failed.
     *
     * @param session the failing connection
     * @param error   the underlying error
     */
    @Override public void handleTransportError(WebSocketSession session, Throwable error) {
        removeAndClose(session, CloseStatus.SERVER_ERROR);
    }

    /**
     * Rejects any message from a client by closing its connection.
     *
     * @param session the sender
     * @param message ignored
     */
    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // This endpoint only delivers readings; it cannot accept device commands.
        removeAndClose(session, CloseStatus.POLICY_VIOLATION);
    }

    /**
     * Sends one reading to every open dashboard.
     *
     * <p>The JSON is built once and reused for all clients. A failure for one client closes only
     * that client; the loop continues for the others. Nothing here throws, because the reading is
     * already saved and the device must still get its {@code 201}.
     *
     * @param reading a reading that is already committed to the database
     */
    public void broadcast(TelemetryReading reading) {
        final TextMessage message;
        try { 
            message = new TextMessage(mapper.writeValueAsString(reading)); 
        } catch (RuntimeException error) {
            log.warn("Stored reading {} could not be serialized for live delivery", reading.id(), error);
            return;
        }
        sessions.values().forEach(session -> {
            if (!session.isOpen()) { 
                sessions.remove(session.getId()); 
                return; 
            }
            try { 
                session.sendMessage(message); 
            } catch (IOException | RuntimeException error) {
                log.debug("Closing failed telemetry connection {}", session.getId());
                removeAndClose(session, CloseStatus.SERVER_ERROR);
            }
        });
    }

    /** Removes a client from the map first (so no more sends), then closes it quietly. */
    private void removeAndClose(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        try { session.close(status); } catch (IOException ignored) { /* already closed */ }
    }

    /**
     * Closes all connections with {@code 1001 GOING_AWAY} when the app stops.
     */
    @PreDestroy public void shutdown() {
        sessions.values().forEach(session -> removeAndClose(session, CloseStatus.GOING_AWAY));
    }
}
