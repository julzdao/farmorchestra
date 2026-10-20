package com.farmorchestra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.OffsetDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "farm.device-api-key=" + TelemetryApiTest.DEVICE_KEY,
    "spring.datasource.url=jdbc:h2:mem:api-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class TelemetryApiTest {
    static final String DEVICE_KEY = "test-only-key";
    private static final String TELEMETRY = "/api/events/telemetry";
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final int POLICY_VIOLATION = 1008;

    @Value("${local.server.port}") int port;
    @Autowired JsonMapper mapper;
    private final HttpClient client = HttpClient.newHttpClient();
    private TestSocket socket;

    @AfterEach
    void closeSocket() {
        if (socket != null) socket.ws.abort();
    }

    // --- Ingestion ---

    @Test
    void rejectsReadingWithoutDeviceKey() throws Exception {
        assertThat(post(waterReading("5", 21.5), null).statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsReadingWithWrongDeviceKey() throws Exception {
        assertThat(post(waterReading("5", 21.5), "wrong-key").statusCode()).isEqualTo(401);
    }

    static Stream<Arguments> invalidReadings() {
        return Stream.of(
            Arguments.of("empty body", "{}"),
            Arguments.of("failed-probe value", waterReading("5", -127)),
            Arguments.of("unsupported metric", reading("5", "light", 500)),
            Arguments.of("humidity above 100%", reading("4", "air_humidity", 101)),
            Arguments.of("unparseable timestamp", waterReadingAt("5", 21, "bad")),
            Arguments.of("timestamp in the future", waterReadingAt("5", 21, "2999-01-01T00:00:00Z")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidReadings")
    void rejectsInvalidReading(String description, String body) throws Exception {
        assertThat(post(body).statusCode()).isEqualTo(400);
    }

    @Test
    void storesReadingWithServerAssignedUnit() throws Exception {
        var response = post(waterReading("unit-test", 20.7));

        assertThat(response.statusCode()).isEqualTo(201);
        var saved = json(response);
        assertThat(saved.get("metricType").asString()).isEqualTo("water_temperature");
        assertThat(saved.get("unit").asString()).isEqualTo("°C");
        OffsetDateTime.parse(saved.get("timestamp").asString());
    }

    // --- History ---

    @Test
    void returnsStoredReadingInHistory() throws Exception {
        var saved = json(post(waterReading("history-test", 20.7)));

        var history = get(TELEMETRY + "?sensorId=history-test&limit=1");

        assertThat(history.statusCode()).isEqualTo(200);
        assertThat(idOf(json(history).get(0))).isEqualTo(idOf(saved));
    }

    @Test
    void rejectsHistoryLimitAbove100() throws Exception {
        assertThat(get(TELEMETRY + "?limit=101").statusCode()).isEqualTo(400);
    }

    // --- Live stream ---

    @Test
    void broadcastsFreshReadingAfterItIsStored() throws Exception {
        socket = connect(ALLOWED_ORIGIN);

        post(waterReading("stream-test", 22.2));

        var message = socket.messages.poll(5, TimeUnit.SECONDS);
        assertThat(message).as("streamed reading").isNotNull();
        var streamed = mapper.readTree(message);
        assertThat(streamed.get("sensorId").asString()).isEqualTo("stream-test");
        assertThat(streamed.get("timestamp").isString()).isTrue();
        var stored = json(get(TELEMETRY + "/stream-test?limit=1")).get(0);
        assertThat(idOf(streamed)).isEqualTo(idOf(stored));
    }

    @Test
    void doesNotBroadcastOldBufferedReading() throws Exception {
        socket = connect(ALLOWED_ORIGIN);

        var response = post(waterReadingAt("buffered-test", 19, "2020-01-01T00:00:00Z"));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(socket.messages.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void closesStreamWhenClientSendsCommand() throws Exception {
        socket = connect(ALLOWED_ORIGIN);

        socket.ws.sendText("turn-on-light", true).join();

        assertThat(socket.closeStatus.get(5, TimeUnit.SECONDS)).isEqualTo(POLICY_VIOLATION);
    }

    @Test
    void refusesStreamFromDisallowedOrigin() {
        assertThatThrownBy(() -> connect("http://untrusted.example"))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(WebSocketHandshakeException.class);
    }

    // --- Request bodies ---

    private static String waterReading(String sensorId, double value) {
        return reading(sensorId, "water_temperature", value);
    }

    private static String waterReadingAt(String sensorId, double value, String timestamp) {
        return """
            {"sensorId":"%s","metricType":"water_temperature","value":%s,"timestamp":"%s"}"""
            .formatted(sensorId, value, timestamp);
    }

    private static String reading(String sensorId, String metricType, double value) {
        return """
            {"sensorId":"%s","metricType":"%s","value":%s}""".formatted(sensorId, metricType, value);
    }

    // --- HTTP helpers ---

    private HttpResponse<String> post(String body) throws Exception {
        return post(body, DEVICE_KEY);
    }

    private HttpResponse<String> post(String body, String deviceKey) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + TELEMETRY))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (deviceKey != null) request.header("DEVICE-API-KEY", deviceKey);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String pathAndQuery) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + pathAndQuery)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) {
        return mapper.readTree(response.body());
    }

    private static long idOf(JsonNode reading) {
        return reading.get("id").asLong();
    }

    // --- WebSocket helper ---

    private TestSocket connect(String origin) throws Exception {
        var listener = new TestSocket();
        listener.ws = client.newWebSocketBuilder()
            .header("Origin", origin)
            .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws/telemetry/stream"), listener)
            .get(5, TimeUnit.SECONDS);
        return listener;
    }

    /** Collects every text frame the server sends and the close status, if any. */
    private static class TestSocket implements WebSocket.Listener {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closeStatus = new CompletableFuture<>();
        private final StringBuilder partial = new StringBuilder();
        WebSocket ws;

        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                messages.add(partial.toString());
                partial.setLength(0);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
            closeStatus.complete(status);
            return null;
        }
    }
}
