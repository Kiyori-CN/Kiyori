import type { JsonRecord, JsonValue } from "./types";

export function parseJson(text: string, label: string): JsonValue {
  try {
    return JSON.parse(text) as JsonValue;
  } catch (error) {
    let detail = "non-Error exception";
    if (error instanceof Error) {
      detail = error.message;
    } else if (typeof error === "string") {
      detail = error;
    }
    console.error(label + " JSON parse failed: " + detail);
    throw new Error(label + " returned invalid JSON.");
  }
}

export function requireRecord(value: JsonValue, label: string): JsonRecord {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new Error(label + " must be a JSON object.");
  }
  return value;
}

export function optionalRecord(value: JsonValue | undefined): JsonRecord | null {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return null;
  }
  return value;
}

export function recordAt(value: JsonRecord, key: string): JsonRecord | null {
  return optionalRecord(value[key]);
}

export function arrayAt(value: JsonRecord, key: string): JsonValue[] {
  const candidate = value[key];
  return Array.isArray(candidate) ? candidate : [];
}

export function stringAt(value: JsonRecord, key: string): string | null {
  const candidate = value[key];
  return typeof candidate === "string" ? candidate : null;
}

export function numberAt(value: JsonRecord, key: string): number | null {
  const candidate = value[key];
  return typeof candidate === "number" && Number.isFinite(candidate) ? candidate : null;
}

export function booleanAt(value: JsonRecord, key: string): boolean | null {
  const candidate = value[key];
  return typeof candidate === "boolean" ? candidate : null;
}

export function integerAt(value: JsonRecord, key: string): number | null {
  const candidate = numberAt(value, key);
  return candidate !== null && Number.isInteger(candidate) ? candidate : null;
}

export function requireString(value: string | undefined, name: string): string {
  const normalized = value === undefined ? "" : value.trim();
  if (normalized.length === 0) {
    throw new Error(name + " is required.");
  }
  return normalized;
}

export function boundedInteger(
  value: number | undefined,
  name: string,
  minimum: number,
  maximum: number,
  defaultValue: number
): number {
  if (value === undefined) {
    return defaultValue;
  }
  if (!Number.isInteger(value) || value < minimum || value > maximum) {
    throw new Error(name + " must be an integer from " + minimum + " to " + maximum + ".");
  }
  return value;
}

export function safePathComponent(value: string, maximum = 64): string {
  const normalized = value
    .normalize("NFKC")
    .replace(/[\\/:*?"<>|\u0000-\u001f]/g, "_")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/[. ]+$/g, "");
  const result = normalized.slice(0, maximum);
  if (result.length === 0 || result === "." || result === "..") {
    throw new Error("A stable output path component could not be derived.");
  }
  return result;
}

export function requireSafeAbsoluteAndroidPath(value: string, label: string): string {
  const normalized = value.trim().replace(/\/+$/g, "");
  if (
    !normalized.startsWith("/") ||
    normalized.includes("\u0000") ||
    normalized.includes("\r") ||
    normalized.includes("\n") ||
    normalized.includes("\"") ||
    normalized.includes("'") ||
    normalized.split("/").includes("..")
  ) {
    throw new Error(label + " must be a safe absolute Android path.");
  }
  return normalized;
}
