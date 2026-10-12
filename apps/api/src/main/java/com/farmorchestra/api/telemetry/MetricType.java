package com.farmorchestra.api.telemetry;

/**
 * The measurements this API accepts, each with its unit and accepted value range.
 *
 * An enum is used since the set of metrics is small and fixed.
 * So there is only one place to add a new metric,
 * and compiler stops anyone from inventing a new metric.
 *
 * There sanity check like the range filter on the {@link #accepts(double)}
 * or filtering unknown devices from {@link #parse(String)}.
 *
 * <p>To add a metric: add a constant here. Nothing else needs to change for ingestion,
 * storage or history. Update the error message in {@link #parse(String)} and the README table.
 */
public enum MetricType {
    /** Nutrient-solution temperature in a DWC tank. Rejects common failed-probe values. */
    WATER_TEMPERATURE("water_temperature", "°C", -5, 60),
    /** Relative air humidity. Physically limited to 0..100 %. */
    AIR_HUMIDITY("air_humidity", "%", 0, 100);

    private final String wireName;
    private final String unit;
    private final double minimum;
    private final double maximum;

    MetricType(String wireName, String unit, double minimum, double maximum) {
        this.wireName = wireName;
        this.unit = unit;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    /**
     * The name used in JSON and in the database, for example {@code "water_temperature"}.
     *
     * @return the stable external name of this metric
     */
    public String wireName() { return wireName; }

    /**
     * The unit shown next to the value, for example {@code "°C"}.
     *
     * @return the unit this API assigns to every reading of this metric
     */
    public String unit() { return unit; }

    /**
     * Checks that a value is a real number inside this metric's sane range (inclusive).
     *
     * <p><b>Why {@code Double.isFinite}:</b> {@code NaN} and {@code Infinity} are valid doubles but
     * not valid measurements. {@code NaN} would also slip past {@code >=} / {@code <=} checks,
     * because every comparison with {@code NaN} is {@code false}.
     *
     * @param value the measured value
     * @return {@code true} if the value can be stored
     */
    public boolean accepts(double value) {
        return Double.isFinite(value) && value >= minimum && value <= maximum;
    }

    /**
     * Converts a wire name such as {@code "air_humidity"} into a {@code MetricType}.
     *
     * @param value the metric name sent by the device or read from the database
     * @return the matching metric
     * @throws IllegalArgumentException if the name is unknown; the web layer turns this into
     *         HTTP 400 (see {@code ApiErrors})
     */
    public static MetricType parse(String value) {
        for (MetricType metric : values()) {
            if (metric.wireName.equals(value)) return metric;
        }
        throw new IllegalArgumentException("metricType must be water_temperature or air_humidity");
    }
}
