/**
 * ToolPkg 薄层协议：sentinel 解析、固定错误码、结果归一化。
 *
 * 本文件不含任何 OOXML/PDF 格式逻辑；格式处理全部在 Python 侧。
 */

export const OFFICE_BEGIN = "__KIYORI_OFFICE_BEGIN__";
export const OFFICE_END = "__KIYORI_OFFICE_END__";

export const OFFICE_ERROR_CODES = [
  "E_ENV_MISSING",
  "E_PATH_INVALID",
  "E_PATH_EXISTS",
  "E_INPUT_SCHEMA",
  "E_FORMAT_UNSUPPORTED",
  "E_DOC_CORRUPT",
  "E_ANCHOR_NOT_FOUND",
  "E_VALIDATION_FAILED",
  "E_ENGINE_FAILED",
  "E_TIMEOUT",
  "E_BUDGET_EXCEEDED",
  "E_PROTOCOL"
] as const;

export type OfficeErrorCode = (typeof OFFICE_ERROR_CODES)[number];

export type OfficeEnvelope = {
  ok: boolean;
  command: string;
  engine?: string;
  engine_version?: string;
  artifacts?: OfficeArtifact[];
  data?: Record<string, unknown>;
  warnings?: Array<{ code: string; message: string }>;
  metrics?: { elapsed_ms?: number };
  truncated?: boolean;
  full_output_path?: string | null;
  next_actions?: string[];
  error?: {
    code: string;
    message: string;
    detail?: string;
    remedy?: string;
  };
};

export type OfficeArtifact = {
  page?: number;
  role: string;
  path: string;
  env: string;
  bytes: number;
  sha256: string;
};

export type OfficeFailure = {
  success: false;
  message: string;
  code: string;
  detail?: string;
  remedy?: string;
  command: string;
};

export function asText(value: unknown): string {
  return typeof value === "string" ? value : value == null ? "" : String(value);
}

export function parseEnvelope(output: string): OfficeEnvelope {
  const start = output.indexOf(OFFICE_BEGIN);
  const end = output.lastIndexOf(OFFICE_END);
  if (start < 0 || end < 0) {
    throw new Error(
      `E_PROTOCOL: office runtime sentinel missing; output tail=${output.slice(-400)}`
    );
  }
  const raw = output.slice(start + OFFICE_BEGIN.length, end);
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch (error) {
    throw new Error(
      `E_PROTOCOL: office runtime envelope is not valid JSON: ${asText(
        error && (error as Error).message
      )}`
    );
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)
      || typeof (parsed as OfficeEnvelope).ok !== "boolean"
      || typeof (parsed as OfficeEnvelope).command !== "string") {
    throw new Error("E_PROTOCOL: office runtime envelope root must be an object");
  }
  return parsed as OfficeEnvelope;
}

export function isRegisteredCode(code: string): boolean {
  return (OFFICE_ERROR_CODES as readonly string[]).indexOf(code) >= 0;
}

/** 把失败信封归一化成模型可读的固定结构，避免错误码在 JS 侧被改写。 */
export function toFailure(command: string, error: unknown): OfficeFailure {
  const raw = asText(error && (error as Error).message ? (error as Error).message : error);
  const match = raw.match(/^(E_[A-Z_]+):\s*([\s\S]*)$/);
  const code = match && isRegisteredCode(match[1]) ? match[1] : "E_ENGINE_FAILED";
  return {
    success: false,
    message: match ? match[2] : raw,
    code,
    command
  };
}

export function envelopeFailure(envelope: OfficeEnvelope): OfficeFailure & { data?: Record<string, unknown> } {
  const error = envelope.error;
  if (!error || !isRegisteredCode(error.code)) {
    return toFailure(envelope.command, new Error("E_PROTOCOL: 失败信封缺少已登记的错误码"));
  }
  return { success: false, command: envelope.command, code: error.code,
    message: `${error.code}: ${error.message}${error.remedy ? `\n${error.remedy}` : ""}`,
    detail: error.detail, remedy: error.remedy,
    data: { ...envelope.data, error: { ...error } } };
}
