import { test } from "node:test";
import { strict as assert } from "node:assert";
import {
  parseReading,
  mergeReading,
  isActive,
  STALE_MS,
  type Reading,
} from "./telemetry";

/** 
 * Incoming API message: sensor IDs may be numbers. 
 */ 
const validMessage = {
  sensorId: 3,
  value: 22,
  timestamp: "2026-10-02T12:00:00Z",
};

/** 
 * Internal reading: sensor IDs are normalized to strings.
 */ 
const reading: Reading = {
  sensorId: "3",
  value: 22,
  timestamp: validMessage.timestamp,
  receivedAt: 100,
};

/** Tests for @see parseReading */

test("parseReading converts a numeric sensor ID to a string", () => {
  const result = parseReading(JSON.stringify(validMessage), 100);

  assert.equal(result?.sensorId, "3");
});

test("parseReading rejects an unknown sensor ID", () => {
  const readingWithUnknownSensorId = JSON.stringify({ ...validMessage, sensorId: 999 });

  assert.equal(parseReading(readingWithUnknownSensorId), null);
});

test("parseReading rejects malformed JSON", () => {
  assert.equal(parseReading("invalid"), null);
});

test("parseReading rejects a null payload", () => {
  assert.equal(parseReading("null"), null);
});

test("parseReading rejects a string measurement value", () => {
  const readingWithStringValue = JSON.stringify({ ...validMessage, value: "22" });

  assert.equal(parseReading(readingWithStringValue), null);
});

test("parseReading rejects an invalid timestamp", () => {
  const readingWithInvalidTimeStamp = JSON.stringify({ ...validMessage, timestamp: "bad" });

  assert.equal(parseReading(readingWithInvalidTimeStamp), null);
});

/** Tests for @see isActive */

test("isActive returns true for a connected, fresh reading", () => {
  assert.equal(isActive(reading, true, 101), true);
});

test("isActive returns false when disconnected", () => {
  assert.equal(isActive(reading, false, 101), false);
});

test("isActive returns true just before the stale threshold", () => {
  const now = reading.receivedAt + STALE_MS - 1;

  assert.equal(isActive(reading, true, now), true);
});

test("isActive returns false at the stale threshold", () => {
  const now = reading.receivedAt + STALE_MS;

  assert.equal(isActive(reading, true, now), false);
});

test("isActive returns false when no reading exists", () => {
  assert.equal(isActive(undefined, true, 101), false);
});

/** Tests for @see mergeReading */

test("mergeReading adds a reading to an empty state", () => {
  const result = mergeReading({}, reading);

  assert.deepEqual(result, { "3": reading });
});

test("mergeReading preserves other sensors when adding a reading", () => {
  const previous = { "3": reading };
  const humidityReading: Reading = {
    ...reading,
    sensorId: "4",
    value: 55,
  };

  const result = mergeReading(previous, humidityReading);

  assert.deepEqual(result, {
    "3": reading,
    "4": humidityReading,
  });
});

test("mergeReading ignores an older reading", () => {
  const previous = { "3": reading };
  const olderReading: Reading = {
    ...reading,
    value: 1,
    timestamp: "2026-10-01T12:00:00Z",
  };

  assert.equal(mergeReading(previous, olderReading), previous);
});

test("mergeReading replaces a reading with a newer measurement", () => {
  const previous = { "3": reading };
  const newerReading: Reading = {
    ...reading,
    value: 23,
    timestamp: "2026-10-02T12:01:00Z",
  };

  assert.deepEqual(mergeReading(previous, newerReading), {
    "3": newerReading,
  });
});