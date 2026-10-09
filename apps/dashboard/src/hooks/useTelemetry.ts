import { useEffect, useState } from "react";
import { mergeReading, parseReading, type Readings } from "../telemetry";

const CLOCK_TICK_MS = 1_000;
const DEMO_INTERVAL_MS = 1_000;
const RETRY_DELAY_MS = 2_000;
const DEFAULT_WS_URL = "ws://localhost:8181/ws/telemetry/stream";

/** `true` unless `VITE_TELEMETRY_MODE` is set to something other than "demo". */
export const demoMode =
  (import.meta.env.VITE_TELEMETRY_MODE ?? "demo") === "demo";

/** Fake sensors used in demo mode. Each value follows a gentle sine wave. */
const DEMO_SENSORS = [
  { sensorId: "3", baseline: 22, amplitude: 0.6, period: 4 }, // Temperature, °C
  { sensorId: "4", baseline: 55, amplitude: 2, period: 5 }, // Air humidity, %
] as const;

/** What {@link useTelemetry} returns. */
export interface TelemetryState {
  /** Latest reading per sensor, keyed by sensor ID. */
  readings: Readings;
  /** Whether readings are currently flowing. */
  connected: boolean;
  /** User-facing error message, or an empty string. */
  error: string;
  /** Current time in ms, refreshed every second to detect stale readings. */
  now: number;
}

/**
 * Provides live sensor readings to a component.
 *
 * In demo mode it emits simulated readings. Otherwise it connects to the
 * telemetry WebSocket and reconnects automatically if the connection drops.
 *
 * @param paused - Pauses the simulated readings (demo mode only).
 */
export function useTelemetry(paused: boolean): TelemetryState {
  const [readings, setReadings] = useState<Readings>({});
  const [socketConnected, setSocketConnected] = useState(false);
  const [error, setError] = useState("");
  const [now, setNow] = useState(() => Date.now());

  // Clock: re-render every second so stale readings are detected.
  useEffect(() => startInterval(() => setNow(Date.now()), CLOCK_TICK_MS), []);

  // Demo mode: emit simulated readings until paused.
  useEffect(() => {
    if (!demoMode || paused) return;

    let tick = 0;
    const emit = () => {
      const receivedAt = Date.now();
      const timestamp = new Date(receivedAt).toISOString();

      setReadings((previous) => {
        const next = { ...previous };
        for (const sensor of DEMO_SENSORS) {
          const wave = Math.sin(tick / sensor.period) * sensor.amplitude;
          next[sensor.sensorId] = {
            sensorId: sensor.sensorId,
            value: Math.round((sensor.baseline + wave) * 10) / 10,
            timestamp,
            receivedAt,
          };
        }
        return next;
      });
      tick++;
    };

    emit();
    return startInterval(emit, DEMO_INTERVAL_MS);
  }, [paused]);

  // Live mode: stream readings from the WebSocket, reconnecting on close.
  useEffect(() => {
    if (demoMode) return;

    // Socket events can still fire after cleanup; this flag ignores them.
    let disposed = false;
    let socket: WebSocket | undefined;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;

    const connect = () => {
      try {
        socket = new WebSocket(
          import.meta.env.VITE_TELEMETRY_WS_URL ?? DEFAULT_WS_URL,
        );
      } catch {
        setError("Invalid WebSocket URL. Check your configuration.");
        return;
      }

      socket.onopen = () => {
        if (disposed) return;
        setSocketConnected(true);
        setError("");
      };

      socket.onmessage = (event) => {
        if (disposed || typeof event.data !== "string") return;
        const reading = parseReading(event.data);
        if (reading) setReadings((previous) => mergeReading(previous, reading));
      };

      // Browsers always fire `close` after `error`, so this handles both.
      socket.onclose = () => {
        if (disposed) return;
        setSocketConnected(false);
        setError("Telemetry connection lost. Retrying…");
        retryTimer = setTimeout(connect, RETRY_DELAY_MS);
      };
    };

    connect();

    return () => {
      disposed = true;
      clearTimeout(retryTimer);
      socket?.close();
    };
  }, []);

  // In demo mode there is no real connection: "connected" means "not paused".
  const connected = demoMode ? !paused : socketConnected;

  return { readings, connected, error, now };
}

/**
 * Calls `callback` every `intervalMs` milliseconds.
 *
 * @returns A function that stops the interval, ready to be returned
 *   from a `useEffect` as its cleanup.
 */
function startInterval(callback: () => void, intervalMs: number): () => void {
  const id = window.setInterval(callback, intervalMs);
  return () => window.clearInterval(id);
}
