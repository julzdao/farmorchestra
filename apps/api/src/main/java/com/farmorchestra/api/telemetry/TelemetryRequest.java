package com.farmorchestra.api.telemetry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * The JSON body a device sends to {@code POST /api/events/telemetry}.
 *
 * <pre>
 * { "sensorId": "5", "metricType": "water_temperature", "value": 21.5,
 *   "timestamp": "2026-10-02T14:00:00Z" }
 * </pre>
 *
 * <p><b>Two levels of validation:</b>
 * <ol>
 *   <li>The annotations here check <i>shape</i> (present, not too long). Spring runs them
 *       because the controller marks the parameter with {@code @Valid}.</li>
 *   <li>{@link TelemetryService#ingest} checks <i>meaning</i> (known metric, value in range,
 *       timestamp not in the future).</li>
 * </ol>
 * Both failures return HTTP 400.
 *
 * <p>There is deliberately no {@code unit} field: the API decides the unit (see {@link MetricType}).
 *
 * @param sensorId   stable ID chosen by the collector for one physical sensor, 1..100 characters.
 *                   The limit matches the {@code VARCHAR(100)} column.
 * @param metricType wire name of the metric, for example {@code "water_temperature"}
 * @param value      the measured number. Boxed {@code Double} (not {@code double}) so a missing
 *                   value is {@code null} and rejected, instead of silently becoming {@code 0.0}.
 * @param timestamp  when the sensor measured the value, ISO 8601 with a timezone. Optional:
 *                   when absent, server time is used. Devices that buffer readings offline
 *                   <b>must</b> send it, or old readings would look new.
 */
public record TelemetryRequest(
    @NotBlank @Size(max = 100) String sensorId,
    @NotBlank @Size(max = 32) String metricType,
    @NotNull Double value,
    OffsetDateTime timestamp
) {}
