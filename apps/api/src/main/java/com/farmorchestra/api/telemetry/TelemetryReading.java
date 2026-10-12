package com.farmorchestra.api.telemetry;

import java.time.OffsetDateTime;

/**
 * A stored sensor reading. It is returned by the REST API and pushed over the WebSocket,
 * so the JSON shape is a contract with the dashboard. Do not rename fields casually.
 *
 * The reading contains two timestamps: {@code timestamp} is when the sensor measured, {@code receivedAt} is
 * when the server got it. On a flaky farm network a Pi may buffer readings and send them minutes
 * later. Keeping both lets you see that delay, and lets the API skip old readings in the live stream.
 *
 * @param id         database-generated ID, unique per reading
 * @param sensorId   collector-assigned sensor ID
 * @param metricType wire name of the metric (see {@link MetricType#wireName()})
 * @param value      the measured value, already range-checked
 * @param unit       unit assigned by the API (see {@link MetricType#unit()})
 * @param timestamp  measurement time, in UTC
 * @param receivedAt server receipt time, in UTC
 */
public record TelemetryReading(
    long id, String sensorId, String metricType, double value,
    String unit, OffsetDateTime timestamp, OffsetDateTime receivedAt
) {}
