const http = require("http");
const os = require("os");
const { PROJECT_ROOT, PUBLIC_DIR, DATA_DIR, LOGS_DIR, CONFIG_PATH, RUNTIME_PATH, RUNTIME_LOG_PATH } = require("./config/paths");
const { DEFAULT_CONFIG, PRESET_COMMANDS, STATIC_CONTENT_TYPES } = require("./config/constants");
const { AGENT_VERSION, AGENT_PROTOCOL_VERSION } = require("./config/version");
const { createRuntimeLogger } = require("./lib/logger");
const { createStaticFileServer, sendNotFound, sendJson } = require("./lib/http-utils");
const { allowManagementRequest } = require("./lib/request-policy");
const { createConfigStore } = require("./stores/config-store");
const { createRuntimeStore } = require("./stores/runtime-store");
const { createProcessService } = require("./services/process-service");
const { createFileService } = require("./services/file-service");
const { createListenerService } = require("./services/listener-service");
const { createApiHandler } = require("./handlers/api-handler");

const logger = createRuntimeLogger({ logsDir: LOGS_DIR, runtimeLogPath: RUNTIME_LOG_PATH });
const configStore = createConfigStore({ dataDir: DATA_DIR, configPath: CONFIG_PATH, defaultConfig: DEFAULT_CONFIG, presetCommands: PRESET_COMMANDS });
const runtimeStore = createRuntimeStore({ dataDir: DATA_DIR, runtimePath: RUNTIME_PATH });
const processService = createProcessService({ projectRoot: PROJECT_ROOT, logger });
const fileService = createFileService({ projectRoot: PROJECT_ROOT });
const staticFileServer = createStaticFileServer({ publicDir: PUBLIC_DIR, staticContentTypes: STATIC_CONTENT_TYPES });
const state = { config: configStore.loadConfig() };
const listener = createListenerService({ handler: handleRequest(false) });
let managementUrl = "";
let shuttingDown = false;

function writeRuntime() {
  runtimeStore.writeRuntimeFile({ port: listener.snapshot().port, pid: process.pid, host: os.hostname(), managementUrl });
}

async function applyConfig(nextConfig) {
  await listener.apply(nextConfig, () => {
    configStore.saveConfig(nextConfig);
    state.config = nextConfig;
  });
  // 配置已提交后运行提示文件失败不能被谎报为配置失败。
  try { writeRuntime(); } catch (error) { logger.error("runtime.write.failed", { error: error.code || error.message }); }
}

const apiHandler = createApiHandler({
  state, configStore, applyConfig, processService, fileService, logger, presetCommands: PRESET_COMMANDS,
  runtimeInfo: {
    pid: () => process.pid, host: () => os.hostname(), uptimeSec: () => Math.floor(process.uptime()),
    runtimePort: () => listener.snapshot().port,
    runtimeBindAddress: () => listener.snapshot().bindAddress,
    listenerState: () => listener.snapshot()
  },
  versionInfo: { agentVersion: AGENT_VERSION, protocolVersion: AGENT_PROTOCOL_VERSION }
});

function handleRequest(management) {
  return async (req, res) => {
    try {
      if (management && !allowManagementRequest(req)) {
        sendJson(res, 403, { ok: false, error: "MANAGEMENT_ORIGIN_REJECTED" });
        return;
      }
      const url = new URL(req.url, "http://127.0.0.1");
      logger.info("http.request", { method: req.method, path: url.pathname });
      if (management && req.method === "GET" && !url.pathname.startsWith("/api/") && staticFileServer.tryServePublicFile(res, url.pathname)) return;
      if (await apiHandler.handleApiRequest(req, res, url, { management })) return;
      sendNotFound(res);
    } catch (error) {
      logger.error("http.failure", { error: error.message });
      if (!res.headersSent) sendJson(res, 400, { ok: false, error: "Invalid request" });
      else res.end();
    }
  };
}

const managementServer = http.createServer(handleRequest(true));
managementServer.on("error", error => {
  logger.error("management.listen.failed", { error: error.code || error.message });
  shutdown("MANAGEMENT_ERROR");
});
// 管理入口先启动。坏地址/端口占用只禁用执行监听，用户可在同一页面修复；不开放静默替代入口。
managementServer.listen(0, "127.0.0.1", async () => {
  managementUrl = `http://127.0.0.1:${managementServer.address().port}`;
  try { await listener.apply(state.config); }
  catch (error) { logger.error("execution.listen.failed", { error: error.message }); }
  try {
    writeRuntime();
    console.log(`Kiyori PC Agent: ${managementUrl}`);
  } catch (error) { logger.error("runtime.write.failed", { error: error.message }); shutdown("RUNTIME_ERROR"); }
});

function shutdown(signal) {
  if (shuttingDown) return;
  shuttingDown = true;
  logger.info("server.shutdown", { signal });
  processService.terminateAllSessions();
  runtimeStore.removeRuntimeFile();
  listener.close();
  managementServer.close(() => process.exit(0));
  managementServer.closeIdleConnections?.();
  setTimeout(() => process.exit(1), 2000).unref();
}
process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("uncaughtException", error => { logger.error("process.uncaughtException", { error: error.message }); shutdown("UNCAUGHT_EXCEPTION"); });
process.on("unhandledRejection", reason => { logger.error("process.unhandledRejection", { reason: String(reason) }); shutdown("UNHANDLED_REJECTION"); });
process.on("exit", () => runtimeStore.removeRuntimeFile());
