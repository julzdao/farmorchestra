package com.farmorchestra.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farmorchestra.api.telemetry.TelemetryReading;
import com.farmorchestra.api.web.TelemetrySocket;
import java.io.IOException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

class TelemetrySocketTest {
    private final TelemetrySocket socket = new TelemetrySocket(JsonMapper.builder().build());
    private WebSocketSession broken;
    private WebSocketSession healthy;

    @BeforeEach
    void connectBrokenAndHealthyClients() throws Exception {
        broken = openSession("broken");
        healthy = openSession("healthy");
        doThrow(new IOException("Client disconnected")).when(broken).sendMessage(any());
        socket.afterConnectionEstablished(broken);
        socket.afterConnectionEstablished(healthy);
    }

    @Test
    void keepsDeliveringToHealthyClientWhenAnotherFails() throws Exception {
        socket.broadcast(waterReading());

        verify(healthy).sendMessage(any(TextMessage.class));
    }

    @Test
    void closesClientWhoseSendFails() throws Exception {
        socket.broadcast(waterReading());

        verify(broken).close(CloseStatus.SERVER_ERROR);
    }

    private static WebSocketSession openSession(String id) {
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private static TelemetryReading waterReading() {
        var now = OffsetDateTime.now();
        return new TelemetryReading(1, "5", "water_temperature", 22, "°C", now, now);
    }
}
