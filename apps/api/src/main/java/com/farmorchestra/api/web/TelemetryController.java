package com.farmorchestra.api.web;

import com.farmorchestra.api.telemetry.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints under {@code /api/events/telemetry}.
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Purpose</th><th>Auth</th></tr>
 *   <tr><td>POST</td><td>{@code /api/events/telemetry}</td><td>Store one reading</td><td>{@code DEVICE-API-KEY}</td></tr>
 *   <tr><td>GET</td><td>{@code /api/events/telemetry?sensorId=&limit=}</td><td>Recent history</td><td>none</td></tr>
 *   <tr><td>GET</td><td>{@code /api/events/telemetry/recent}</td><td>Legacy alias of the above</td><td>none</td></tr>
 *   <tr><td>GET</td><td>{@code /api/events/telemetry/{sensorId}}</td><td>Legacy per-sensor history</td><td>none</td></tr>
 * </table>
 * 
 */
@RestController
@RequestMapping("/api/events/telemetry")
public class TelemetryController {
    private final TelemetryService service;
    private final TelemetrySocket socket;

    /**
     * @param service validation and storage
     * @param socket  live delivery to connected dashboards
     */
    public TelemetryController(TelemetryService service, TelemetrySocket socket) {
        this.service = service;
        this.socket = socket;
    }

    /**
     * Stores one reading from a device and, if it is fresh, pushes it to live dashboards.
     *
     * <p>Flow: {@code @Valid} checks the JSON shape, the service checks the rules and saves,
     * then the reading is broadcast.
     *
     * @param request the device payload
     * @return the stored reading, with HTTP 201 Created
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TelemetryReading ingest(@Valid @RequestBody TelemetryRequest request) {
        TelemetryReading saved = service.ingest(request);

        if (saved.timestamp().isAfter(java.time.OffsetDateTime.now().minusSeconds(30))) {
            socket.broadcast(saved);
        }
        return saved;
    }

    /**
     * Returns recent readings for all sensors, or for one when {@code sensorId} is given.
     *
     * @param sensorId optional sensor filter
     * @param limit    number of readings, 1..100, default 20
     * @return newest readings first
     */
    @GetMapping({"", "/recent"})
    public List<TelemetryReading> recent(@RequestParam(required = false) String sensorId,
                                        @RequestParam(defaultValue = "20") int limit) {
        return service.recent(sensorId, limit);
    }

    /**
     * Legacy form of {@code ?sensorId=}: returns recent readings for one sensor.
     *
     * @param sensorId the sensor to read
     * @param limit    number of readings, 1..100, default 20
     * @return newest readings first
     */
    @GetMapping("/{sensorId}")
    public List<TelemetryReading> bySensor(@PathVariable String sensorId,
                                          @RequestParam(defaultValue = "20") int limit) {
        return service.recent(sensorId, limit);
    }
}
