/** 手机配置与工具执行共用地址规则，避免 FRP 的 HTTPS 默认端口被改写。 */
export function normalizeAgentUrl(input: string): string {
  const raw = input.trim();
  if (!raw || /[\s\\?#]/.test(raw)) throw new Error("地址不能为空或包含空白、反斜线、查询串和片段");
  const explicitScheme = /^[a-z][a-z\d+.-]*:\/\//i.test(raw);
  const match = (explicitScheme ? raw : `http://${raw}`).match(/^(https?):\/\/(\[[a-f\d:]+\]|[^/:@]+)(?::(\d+))?(\/[^?#]*)?$/i);
  if (!match) throw new Error("请输入有效的 HTTP/HTTPS 地址，不要包含用户名或密码");
  const host = match[2].toLowerCase();
  if (!host.startsWith("[") && !/^(?:[a-z\d](?:[a-z\d-]*[a-z\d])?)(?:\.[a-z\d](?:[a-z\d-]*[a-z\d])?)*\.?$/i.test(host)) {
    throw new Error("主机名无效");
  }
  if (host.startsWith("[") && !host.includes(":")) throw new Error("IPv6 地址无效");
  const port = match[3] ? Number(match[3]) : explicitScheme ? null : 58321;
  if (port !== null && (!Number.isInteger(port) || port < 1 || port > 65535)) throw new Error("端口必须为 1–65535");
  const prefix = (match[4] || "").replace(/\/+$/, "");
  if (/%(?![a-f\d]{2})/i.test(prefix) || /(?:^|\/)(?:\.|\.\.)(?:\/|$)/.test(prefix)) throw new Error("地址路径无效");
  return `${match[1].toLowerCase()}://${host}${port === null ? "" : `:${port}`}${prefix}`;
}

export function validateConnectionConfig(baseUrl: string, token: string, shell = "powershell", timeout = "30000") {
  const normalized = normalizeAgentUrl(baseUrl);
  if (!token.trim() || /[\r\n]/.test(token)) throw new Error("请填写有效的访问令牌");
  if (!["powershell", "pwsh", "cmd"].includes(shell)) throw new Error("Shell 必须为 powershell、pwsh 或 cmd");
  const timeoutMs = Number(timeout);
  if (!Number.isInteger(timeoutMs) || timeoutMs < 1000 || timeoutMs > 600000) throw new Error("超时必须为 1000–600000 毫秒的整数");
  return { baseUrl: normalized, token: token.trim(), shell, timeout: `${timeoutMs}` };
}
