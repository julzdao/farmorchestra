package com.farmorchestra.api.telemetry;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the telemetry business rules, then delegates storage to {@link TelemetryRepository}.
 *
 * <p><b>Error contract:</b> invalid input throws {@link IllegalArgumentException} with a message
 * that is safe to show to the client. {@code ApiErrors} maps it to HTTP 400.
 */
@Service
public class TelemetryService {
    private final TelemetryRepository repository;

    /**
     * @param repository database access, injected by Spring through the constructor.
     *                   <b>Why constructor injection:</b> the field can be {@code final}, and tests
     *                   can pass a fake with plain {@code new}, no Spring needed.
     */
    public TelemetryService(TelemetryRepository repository) { this.repository = repository; }

    /**
     * Validates and stores one reading.
     *
     * <p>Rules, in order:
     * <ol>
     *   <li>The metric must be known ({@link MetricType#parse}).</li>
     *   <li>The value must be in the metric's sane range ({@link MetricType#accepts}).</li>
     *   <li>A missing timestamp means "now". A given timestamp is converted to UTC.</li>
     *   <li>The timestamp may be at most 5 minutes in the future.</li>
     * </ol>
     *
     * <p><b>Why a 5-minute future tolerance:</b> small clocks drift, and a Pi without a real-time
     * clock may be slightly off after boot. More than 5 minutes means a broken clock; accepting it
     * would keep that reading at the top of the history forever.
     *
     * @param request the already shape-validated device input
     * @return the stored reading, including its new ID, unit and timestamps
     * @throws IllegalArgumentException if any rule above fails (becomes HTTP 400)
     */
    @Transactional
    public TelemetryReading ingest(TelemetryRequest request) {
        MetricType metric = MetricType.parse(request.metricType());
        if (!metric.accepts(request.value())) {
            throw new IllegalArgumentException("Value is outside the supported sensor range");
        }
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var measuredAt = request.timestamp() == null ? now : request.timestamp().withOffsetSameInstant(ZoneOffset.UTC);
        if (measuredAt.isAfter(now.plusMinutes(5))) {
            throw new IllegalArgumentException("Timestamp must not be more than five minutes in the future");
        }
        return repository.save(request.sensorId(), metric, request.value(), measuredAt, now);
    }

    /**
     * Returns the newest readings, newest first, optionally for a single sensor.
     *
     * <p>
     * It contains a <b>hard limit of 100</b>. Since the table grows every few seconds, forever. An unbounded
     * query (as in the legacy API) eventually returns millions of rows and can take down a small
     * server or a Raspberry Pi. A cap keeps every request cheap. This is not a full export API.
     *
     * @param sensorId sensor to filter by, or {@code null} for all sensors
     * @param limit    number of rows to return, 1..100
     * @return up to {@code limit} readings, ordered by measurement time then ID, descending
     * @throws IllegalArgumentException if {@code limit} or {@code sensorId} is out of bounds
     */
    @Transactional(readOnly = true)
    public List<TelemetryReading> recent(String sensorId, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        if (sensorId != null && (sensorId.isBlank() || sensorId.length() > 100)) {
            throw new IllegalArgumentException("sensorId must contain 1 to 100 characters");
        }
        return repository.recent(sensorId, limit);
    }
}
