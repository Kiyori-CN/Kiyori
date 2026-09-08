async function requestJson(url, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), options.timeoutMs || (url.startsWith("/api/config") || url.startsWith("/api/startup") || url === "/api/health" ? 15000 : 650000));
  let response;
  try {
  response = await fetch(url, {
    signal: controller.signal,
    redirect: "error",
    method: options.method || "GET",
    headers: {
      Accept: "application/json",
      ...(options.body ? { "Content-Type": "application/json" } : {})
    },
    body: options.body ? JSON.stringify(options.body) : undefined
  });

  const text = await response.text();
  let data = null;

  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    throw new Error("Invalid JSON response; check the current service state before retrying a write.");
  }

  if (!response.ok) {
    throw Object.assign(new Error((data && data.error) || `HTTP ${response.status}`), {
      status: response.status, code: typeof data?.code === "string" ? data.code : ""
    });
  }

  if (!data || typeof data !== "object" || Array.isArray(data)) throw new Error("Invalid response from PC Agent");
  if (data.ok === false) throw Object.assign(new Error(data.error || "Operation failed"), { code: typeof data.code === "string" ? data.code : "" });
  return data;
  } finally { clearTimeout(timer); }
}

export const api = {
  getHealth() {
    return requestJson("/api/health");
  },
  getConfig() {
    return requestJson("/api/config");
  },
  updateConfig(payload) {
    return requestJson("/api/config", { method: "POST", body: payload });
  },
  getPresets() {
    return requestJson("/api/presets");
  },
  getStartupState() {
    return requestJson("/api/startup/state");
  },
  applyRecommendedBind() {
    return requestJson("/api/startup/apply_recommended_bind", { method: "POST", body: {} });
  },
  executeCommand(payload) {
    return requestJson("/api/command/execute", { method: "POST", body: payload });
  },
  listFiles(payload) {
    return requestJson("/api/file/list", { method: "POST", body: payload });
  },
  readFile(payload) {
    return requestJson("/api/file/read", { method: "POST", body: payload });
  },
  readFileSegment(payload) {
    return requestJson("/api/file/read_segment", { method: "POST", body: payload });
  },
  writeFile(payload) {
    return requestJson("/api/file/write", { method: "POST", body: payload });
  },
  editFile(payload) {
    return requestJson("/api/file/edit", { method: "POST", body: payload });
  },
  readFileBase64(payload) {
    return requestJson("/api/file/read_base64", { method: "POST", body: payload });
  },
  writeFileBase64(payload) {
    return requestJson("/api/file/write_base64", { method: "POST", body: payload });
  },
  startProcessSession(payload) {
    return requestJson("/api/process/start", { method: "POST", body: payload });
  },
  readProcessSession(payload) {
    return requestJson("/api/process/read", { method: "POST", body: payload });
  },
  writeProcessSession(payload) {
    return requestJson("/api/process/write", { method: "POST", body: payload });
  },
  resizeProcessSession(payload) {
    return requestJson("/api/process/resize", { method: "POST", body: payload });
  },
  listProcessSessions(payload) {
    return requestJson("/api/process/list", { method: "POST", body: payload });
  },
  terminateProcessSession(payload) {
    return requestJson("/api/process/terminate", { method: "POST", body: payload });
  }
};
