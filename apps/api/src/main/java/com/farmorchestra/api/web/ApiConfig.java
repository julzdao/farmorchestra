package com.farmorchestra.api.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Web wiring: which browser origins may call the API, and where the WebSocket lives.
 */
@Configuration
@EnableWebSocket
public class ApiConfig implements WebMvcConfigurer, WebSocketConfigurer {
    private final TelemetrySocket socket;
    private final String[] origins;

    /**
     * @param socket  the WebSocket handler to register
     * @param origins allowed browser origins, from {@code farm.dashboard-origins}
     *                (env {@code DASHBOARD_ORIGINS}, comma-separated; Spring splits it into an array)
     */
    public ApiConfig(TelemetrySocket socket, @Value("${farm.dashboard-origins}") String[] origins) {
        this.socket = socket;
        this.origins = origins;
    }

    /**
     * Allows the dashboard origins to call {@code /api/**} with GET and POST only.
     *
     * @param registry Spring's CORS registry
     */
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET", "POST")
            .allowedHeaders("Content-Type", "DEVICE-API-KEY");
    }

    /**
     * Serves {@link TelemetrySocket} at {@code /ws/telemetry/stream}.
     *
     * <p>
     * Browsers do not apply CORS to WebSockets.
     * Without this check, any website open in your browser could connect and read your sensor data.
     * A disallowed origin fails the handshake with HTTP 403.
     *
     * @param registry Spring's WebSocket registry
     */
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(socket, "/ws/telemetry/stream").setAllowedOrigins(origins);
    }
}
