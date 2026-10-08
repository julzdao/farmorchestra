import { useEffect, useState } from "react";
import { mergeReading, parseReading, type Readings } from "../telemetry";

export const demoMode =
  (import.meta.env.VITE_TELEMETRY_MODE ?? "demo") === "demo";

export function useTelemetry(paused: boolean) {
  const [readings, setReadings] = useState<Readings>({}); 
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState("");
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    return startInterval(() => setNow(Date.now()), 1000);
  }, []);

  useEffect(() => {
    if (!demoMode) return;
    setConnected(!paused);
    if (paused) return;

    let tick = 0;
    const emit = () => {
      const receivedAt = Date.now();
      setReadings((previous) => ({
        ...previous,
        "3": {
          sensorId: "3",
          value: Number((22 + Math.sin(tick++ / 4) * 0.6).toFixed(1)),
          timestamp: new Date(receivedAt).toISOString(),
          receivedAt,
        },
        "4": {
          sensorId: "4",
          value: Number((55 + Math.sin(tick / 5) * 2).toFixed(1)),
          timestamp: new Date(receivedAt).toISOString(),
          receivedAt,
        },
      }));
    };
    emit();
    return startInterval(emit, 1000);
  }, [paused]);

  useEffect(() => {
    if (demoMode) return;
    let disposed = false;
    let socket: WebSocket | undefined;
    let retry: ReturnType<typeof setTimeout> | undefined;
    const connect = () => {
      if (disposed) return;
      setConnected(false);
      try {
        socket = new WebSocket(
          import.meta.env.VITE_TELEMETRY_WS_URL ??
            "ws://localhost:8181/ws/telemetry/stream",
        );
      } catch {
        setError("Invalid WebSocket URL. Check your configuration.");
        return;
      }
      socket.onopen = () => {
        if (!disposed) {
          setConnected(true);
          setError("");
        }
      };
      socket.onmessage = (event) => {
        if (disposed || typeof event.data !== "string") return;
        const reading = parseReading(event.data);
        if (reading) setReadings((previous) => mergeReading(previous, reading));
      };
      socket.onerror = () => {
        if (!disposed) setError("Telemetry connection unavailable. Retrying…");
      };
      socket.onclose = () => {
        if (disposed) return;
        setConnected(false);
        setError("Telemetry disconnected. Retrying…");
        retry = setTimeout(connect, 2000);
      };
    };
    connect();
    return () => {
      disposed = true;
      clearTimeout(retry);
      if (socket) {
        socket.onopen =
          socket.onmessage =
          socket.onerror =
          socket.onclose =
            null;
        socket.close();
      }
    };
  }, []);
  return { readings, connected, error, now };
}

function startInterval(fn: () => void, intervalInMs: number): () => void {
  const id = window.setInterval(fn, intervalInMs);
  return () => window.clearInterval(id);
}

