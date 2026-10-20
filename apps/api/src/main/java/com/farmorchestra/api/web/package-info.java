/**
 * The edge of the application: HTTP endpoints, WebSocket stream, device authentication,
 * browser origin rules and error responses.
 *
 * <ul>
 *   <li>{@link com.farmorchestra.api.web.TelemetryController} - REST endpoints for ingest and history.</li>
 *   <li>{@link com.farmorchestra.api.web.TelemetrySocket} - pushes fresh readings to dashboards.</li>
 *   <li>{@link com.farmorchestra.api.web.DeviceKeyFilter} - only devices with the key may POST.</li>
 *   <li>{@link com.farmorchestra.api.web.ApiConfig} - CORS and WebSocket route registration.</li>
 *   <li>{@link com.farmorchestra.api.web.ApiErrors} - turns business errors into HTTP 400.</li>
 * </ul>
 *
 */
package com.farmorchestra.api.web;
