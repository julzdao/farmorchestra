package com.farmorchestra.api.telemetry;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Reads and writes the {@code telemetry} table with plain SQL through Spring's {@link JdbcTemplate}.
 * <p>
 * With JDBC the SQL you read is the SQL that runs: no entity mapping, lazy loading or hidden queries to learn.
 * {@code JdbcTemplate} still handles connections, exceptions and transactions for us.
 *
 * <p><b>No validation here.</b> This class trusts its caller ({@link TelemetryService}).
 * One responsibility per class keeps each one small.
 *
 * <p>The table itself is created by Flyway from {@code db/migration/V1__create_telemetry.sql}.
 */
@Repository
public class TelemetryRepository {
    private final JdbcTemplate jdbc;

    /**
     * Turns one database row into a {@link TelemetryReading}.
     * <p>
     * Note: a row with an unknown {@code metric_type} makes {@link MetricType#parse} throw,
     * so only write rows through this API (or add the metric to {@link MetricType} first).
     */
    private static final RowMapper<TelemetryReading> MAPPER = (rs, row) -> new TelemetryReading(
        rs.getLong("id"), rs.getString("sensor_id"), rs.getString("metric_type"),
        rs.getDouble("reading_value"), MetricType.parse(rs.getString("metric_type")).unit(),
        rs.getObject("timestamp", OffsetDateTime.class), rs.getObject("received_at", OffsetDateTime.class));

    /**
     * @param jdbc Spring's JDBC helper, auto-configured from {@code spring.datasource.*}
     */
    public TelemetryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * Inserts one reading and returns it with its database-generated ID.
     *
     * <p>
     * The {@code ?} placeholders prevent SQL injection.
     * The {@code sensorId} or {@code metricType} are validated by the API and always come separately from the SQL text.
     *
     * @param sensorId   collector-assigned sensor ID
     * @param metric     validated metric; stored by its wire name
     * @param value      validated value
     * @param timestamp  measurement time (UTC)
     * @param receivedAt server receipt time (UTC)
     * @return the stored reading
     * @throws IllegalStateException if the database returns no ID (should never happen)
     */
    public TelemetryReading save(String sensorId, MetricType metric, double value,
                                 OffsetDateTime timestamp, OffsetDateTime receivedAt) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO telemetry (sensor_id, metric_type, reading_value, timestamp, received_at) VALUES (?, ?, ?, ?, ?)",
                new String[] { "id" });
            statement.setString(1, sensorId);
            statement.setString(2, metric.wireName());
            statement.setDouble(3, value);
            statement.setObject(4, timestamp);
            statement.setObject(5, receivedAt);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Database did not return a reading ID");
        return new TelemetryReading(key.longValue(), sensorId, metric.wireName(), value,
                                    metric.unit(), timestamp, receivedAt);
    }

    /**
     * Returns the newest readings, newest measurement first.
     *
     * <p>Ordering is made from {@code timestamp},
     * while {@code id} is used for keeping the order stable.
     *
     * @param sensorId sensor to filter by, or {@code null} for all sensors
     * @param limit    maximum rows; the caller must already have bounded it
     * @return the matching readings (possibly empty, never {@code null})
     */
    public List<TelemetryReading> recent(String sensorId, int limit) {
        if (sensorId == null) {
            return jdbc.query("SELECT * FROM telemetry ORDER BY timestamp DESC, id DESC LIMIT ?", MAPPER, limit);
        }
        return jdbc.query("SELECT * FROM telemetry WHERE sensor_id = ? ORDER BY timestamp DESC, id DESC LIMIT ?",
                          MAPPER, sensorId, limit);
    }
}
