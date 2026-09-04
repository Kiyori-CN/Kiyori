import { optionalRecord, parseJson, recordAt, stringAt } from "./json";
import type { JsonRecord } from "./types";

export class BilibiliError extends Error {
  constructor(readonly code: string, message: string, readonly context: JsonRecord = {}) {
    super(message);
    this.name = "BilibiliError";
  }
}

export function failureDetails(error: unknown): JsonRecord {
  if (error instanceof BilibiliError) {
    return { code: error.code, message: error.message, ...error.context };
  }
  if (error instanceof Error) {
    if ("details" in error && typeof error.details === "object" && error.details !== null) {
      const envelope = optionalRecord(parseJson(JSON.stringify(error.details), "宿主错误"));
      const detail = envelope === null ? null : recordAt(envelope, "error");
      if (detail !== null && stringAt(detail, "message") !== null) return detail;
    }
    return { code: "OPERATION_FAILED", message: error.message || "操作失败，未提供错误消息。" };
  }
  if (typeof error === "object" && error !== null && "message" in error && typeof error.message === "string" && error.message.length > 0) {
    return { code: "OPERATION_FAILED", message: error.message };
  }
  return { code: "OPERATION_FAILED", message: typeof error === "string" && error.length > 0 ? error : "操作失败：非标准异常。" };
}

export function toolOperation<Params>(action: (params: Params) => Promise<JsonRecord>): (params: Params) => Promise<JsonRecord> {
  return async (params) => {
    try {
      const result = await action(params);
      if (result.success === false) {
        const message = stringAt(result, "message") ?? "Bilibili 操作部分失败，请查看步骤明细。";
        return { ...result, message, data: result };
      }
      return result;
    } catch (error) {
      const detail = failureDetails(error);
      console.error("Bilibili 操作失败：" + JSON.stringify(detail));
      return { success: false, message: detail.message, error: detail, data: { error: detail } };
    }
  };
}
