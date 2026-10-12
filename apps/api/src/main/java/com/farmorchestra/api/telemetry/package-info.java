/**
 * The telemetry domain: what a sensor reading is, which readings are valid, and how they are stored.
 *
 * <ul>
 *   <li>{@link com.farmorchestra.api.telemetry.MetricType} - the list of supported measurements,
 *       their unit and their sane value range.</li>
 *   <li>{@link com.farmorchestra.api.telemetry.TelemetryRequest} - what the device sends (input).</li>
 *   <li>{@link com.farmorchestra.api.telemetry.TelemetryReading} - what we store and return (output).</li>
 *   <li>{@link com.farmorchestra.api.telemetry.TelemetryService} - the rules, applied in one place.</li>
 *   <li>{@link com.farmorchestra.api.telemetry.TelemetryRepository} - plain SQL to the database.</li>
 * </ul>
 *
 */
package com.farmorchestra.api.telemetry;
