

export const sensors = [
  { id: "3", title: "Temperature", unit: "°C" },
  { id: "4", title: "Air humidity", unit: "%" },
  { id: "2", title: "Soil moisture", unit: "ADC" },
] as const;

export const STALE_MS = 30_000;

export interface Reading {
  sensorId: string;
  value: number;
  timestamp: string;
  receivedAt: number;
}

export type Readings = Record<string, Reading>;

/**
 * Parses a supported sensor reading, or returns null for invalid input.
 * @param raw - Incoming JSON text.
 * @param now - Reception time in milliseconds since the Unix epoch.
 */
export function parseReading(
  raw: string,
  now = Date.now(),
): Reading | null {
  try {
    const data: unknown = JSON.parse(raw);

    if (
      typeof data !== "object" ||
      data === null ||
      !("sensorId" in data) ||
      !("value" in data) ||
      !("timestamp" in data)
    ) {
      return null;
    }

    const sensorId = String(data.sensorId);

    if (!isSensorIdKnown(sensorId)) {
      return null;
    }

    if (
      !isValidSensorIdType(sensorId) ||
      !isValidReadingValue(data.value) ||
      !isValidReadingTimestamp(data.timestamp)
    ) {
      return null;
    }

    return {
      sensorId,
      value: data.value,
      timestamp: data.timestamp,
      receivedAt: now,
    };
  } catch {
    return null;
  }
}

/**
 * Updates the stored value for one sensor. Checks whether the incoming reading is older than the current stored one. 
 * 
 * @param {Readings} previous - Existing readings
 * @param {Reading} reading - incoming Reading
 * 
 * @returns {Readings} - the updated readings
 */
export function mergeReading(previous: Readings, reading: Reading): Readings {
  const existing = previous[reading.sensorId];
  if (
    existing &&
    Date.parse(reading.timestamp) < Date.parse(existing.timestamp)
  )
    return previous;
  return { ...previous, [reading.sensorId]: reading };
}

/**
 * Checks whether a reading can be considered current. 
 * 
 * @param {Reading | undefined} reading - incoming Reading
 * @param {boolean} connected - whether connection is working 
 * @param {number} now - current time in milliseconds
 * 
 * @returns {boolean} - true only when connected, a reading exists and it arrived less than 30 seconds ago. 
 */
export function isActive(
  reading: Reading | undefined,
  connected: boolean,
  now: number,
) {
  return connected && !!reading && now - reading.receivedAt < STALE_MS;
}

function isValidSensorIdType(value: unknown): value is string | number {
  return typeof value === "string" || typeof value === "number";
}

function isValidReadingValue(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

function isValidReadingTimestamp(value: unknown): value is string {
  return typeof value === "string" && Number.isFinite(Date.parse(value));
}

function isSensorIdKnown(sensorId: string): boolean {
  return sensors.some((sensor) => sensor.id === sensorId);
}