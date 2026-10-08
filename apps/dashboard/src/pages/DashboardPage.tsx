import { useState } from "react";
import { SensorReadingCard } from "../components/SensorReadingCard";
import { demoMode, useTelemetry } from "../hooks/useTelemetry";
import { isActive, sensors } from "../telemetry";
export function DashboardPage() {
  const [paused, setPaused] = useState(false);
  const { readings, connected, error, now } = useTelemetry(paused);
  return (
    <main className="dashboard">
      <header className="dashboard__header">
        <a className="brand" href="#/">
          FarmOrchestra
        </a>
        <a className="text-link" href="#/">
          ← Home
        </a>
      </header>
      <section className="dashboard__intro">
        <p className="eyebrow">Your growing space</p>
        <h1>Sensor readings</h1>
        <p>
          {demoMode
            ? "Demo mode · Simulated readings. Soil moisture has no demo sensor."
            : "Live mode · Readings from your telemetry server."}
        </p>
        <div className="connection" role="status">
          {connected ? "Connected" : "Disconnected"}
          {error && ` · ${error}`}
        </div>
        {demoMode && (
          <button
            className="button"
            onClick={() => setPaused((value) => !value)}
          >
            {paused ? "Resume demo" : "Pause demo"}
          </button>
        )}
      </section>
      <section className="sensor-grid" aria-label="Sensors">
        {sensors.map((sensor) => {
          const reading = readings[sensor.id];
          return (
            <SensorReadingCard
              key={sensor.id}
              title={sensor.title}
              unit={sensor.unit}
              value={reading?.value}
              timestamp={reading?.timestamp}
              active={isActive(reading, connected, now)}
            />
          );
        })}
      </section>
      <p className="dashboard__note">
        Inactive cards keep their last reading. Live readings become inactive
        after 30 seconds without an update.
      </p>
    </main>
  );
}
