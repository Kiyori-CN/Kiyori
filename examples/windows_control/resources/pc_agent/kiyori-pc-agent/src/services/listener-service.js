const http = require("http");
const { validateBindAddress } = require("./network-service");

/** 一个执行监听所有者；管理监听和 PTY 不随绑定修改重启。旧 HTTP 请求只排空，不重放。 */
function createListenerService({ handler, validate = validateBindAddress }) {
  let active = null;
  let binding = null;
  let failure = null;
  let busy = false;
  let stopping = false;
  const draining = new Set();

  function retire(server) {
    if (!server) return;
    draining.add(server);
    server.close(() => draining.delete(server));
    server.closeIdleConnections?.();
  }

  function listen(target) {
    return new Promise((resolve, reject) => {
      const server = http.createServer((req, res) => {
        // close 后既有 keep-alive 连接也不能再提交新操作；已开始的请求继续完成。
        if (active !== server || busy || stopping) {
          res.writeHead(503, { "Content-Type": "application/json", Connection: "close" });
          res.end(JSON.stringify({ ok: false, error: "LISTENER_CHANGING" }));
          return;
        }
        handler(req, res);
      });
      server.once("error", reject);
      server.listen(target.port, target.bindAddress, () => {
        server.removeListener("error", reject);
        server.on("error", error => { failure = error.code || error.message; });
        resolve(server);
      });
    });
  }

  async function apply(config, persist = () => {}) {
    if (busy || stopping) throw new Error("CONFIG_UPDATE_BUSY: retry after checking current state");
    busy = true;
    const previous = binding;
    let replaced = false;
    try {
      validate(config.bindAddress);
      if (!Number.isInteger(config.port) || config.port < 1 || config.port > 65535) throw new Error("Invalid port");
      const changed = !active || previous.bindAddress !== config.bindAddress || previous.port !== config.port;
      if (changed) {
        // 先释放旧监听的端口，支持同端口 LAN/回环/通配切换；失败显式恢复旧监听，不换任意端点。
        retire(active);
        active = null;
        binding = null;
        replaced = true;
        active = await listen(config);
        if (stopping) throw new Error("LISTENER_STOPPING");
        binding = { bindAddress: config.bindAddress, port: active.address().port };
      }
      persist();
      failure = null;
      return snapshot();
    } catch (error) {
      if (replaced) {
        retire(active);
        active = null;
        binding = null;
        if (previous && !stopping) {
          try { active = await listen(previous); binding = previous; }
          catch (restoreError) {
            failure = `RESTORE_FAILED: ${restoreError.code || restoreError.message}`;
            throw new Error(`${error.code || error.message}; ${failure}; management remains available`);
          }
        }
      }
      failure = error.code || error.message;
      throw new Error(`${failure}; ${binding ? "previous listener and configuration preserved" : "execution listener unavailable; management remains available"}`);
    } finally { busy = false; }
  }

  function snapshot() {
    return { listening: !!active?.listening, bindAddress: binding?.bindAddress || "", port: binding?.port || null, error: failure, applying: busy };
  }

  function close() {
    stopping = true;
    retire(active);
    active = null;
    binding = null;
    for (const server of draining) server.closeAllConnections?.();
  }
  return { apply, snapshot, close };
}

module.exports = { createListenerService };
