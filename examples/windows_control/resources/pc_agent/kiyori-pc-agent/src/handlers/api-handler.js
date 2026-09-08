const { readJsonBody, sendJson, parseBoolean } = require("../lib/http-utils");
const { tokenMatches } = require("../lib/request-policy");

const FILE_WRITE_JSON_MAX_BODY_BYTES = 8 * 1024 * 1024;
const FILE_WRITE_BASE64_JSON_MAX_BODY_BYTES = 24 * 1024 * 1024;

function buildPublicConfig(config, versionInfo) {
  return {
    bindAddress: config.bindAddress,
    port: config.port,
    maxCommandMs: config.maxCommandMs,
    allowedPresets: config.allowedPresets,
    apiTokenConfigured: !!config.apiToken,
    apiToken: config.apiToken || "",
    version: versionInfo.agentVersion,
    connectionMode: config.connectionMode || "lan",
    publicUrl: config.publicUrl || ""
  };
}

function pickConfigInput(body, camelKey, snakeKey) {
  if (!body || typeof body !== "object") {
    return undefined;
  }

  if (body[camelKey] !== undefined) {
    return body[camelKey];
  }

  if (snakeKey && body[snakeKey] !== undefined) {
    return body[snakeKey];
  }

  return undefined;
}

function isAuthorized(config, token) {
  if (!config.apiToken) {
    return false;
  }

  return tokenMatches(config.apiToken, token);
}

function createApiHandler({
  state,
  configStore,
  applyConfig,
  processService,
  fileService,
  logger,
  presetCommands,
  runtimeInfo,
  versionInfo
}) {
  let configUpdating = false;
  function getStartupIssueState() {
    const current = runtimeInfo.listenerState?.();
    if (!current || (current.listening && current.bindAddress === state.config.bindAddress && current.port === state.config.port && !current.error)) return null;
    const network = processService.getNetworkSnapshot();
    return { issueType: "bindAddressUnavailable", configuredBindAddress: state.config.bindAddress,
      recommendedBindAddress: network.recommendedHost, ipv4Candidates: network.ipv4Candidates,
      error: current.error || "LISTENER_NOT_APPLIED" };
  }
  async function commitConfig(nextConfig) {
    if (applyConfig) await applyConfig(nextConfig);
    else { configStore.saveConfig(nextConfig); state.config = nextConfig; }
  }

  function getPresetList(config) {
    return Object.entries(presetCommands).map(([name, item]) => ({
      name,
      shell: item.shell,
      description: item.description,
      allowed: config.allowedPresets.includes(name)
    }));
  }

  function unauthorized(res, routeName, tokenProvided, extra = {}) {
    logger.warn(`${routeName}.unauthorized`, {
      tokenProvided: !!tokenProvided,
      ...extra
    });
    sendJson(res, 401, { ok: false, error: "Unauthorized" });
  }

  function normalizeInputToken(value) {
    return String(value || "")
      .trim()
      .toLowerCase()
      .replace(/[\s-]+/g, "_");
  }

  function controlTokenToSequence(tokenInput) {
    const token = normalizeInputToken(tokenInput);
    const map = {
      enter: "\r",
      return: "\r",
      newline: "\n",
      cr: "\r",
      lf: "\n",
      tab: "\t",
      backspace: "\b",
      esc: "\u001b",
      escape: "\u001b",
      delete: "\u001b[3~",
      del: "\u001b[3~",
      insert: "\u001b[2~",
      ins: "\u001b[2~",
      up: "\u001b[A",
      down: "\u001b[B",
      left: "\u001b[D",
      right: "\u001b[C",
      home: "\u001b[H",
      end: "\u001b[F",
      pageup: "\u001b[5~",
      pagedown: "\u001b[6~",
      ctrl_c: "\u0003",
      ctrl_d: "\u0004",
      ctrl_z: "\u001a"
    };

    if (!map[token]) {
      throw new Error(`Unsupported control: ${tokenInput}`);
    }

    return map[token];
  }

  function ctrlInputToSequence(keyInput) {
    const key = String(keyInput || "").trim();
    if (!key) {
      throw new Error("Invalid ctrl input key");
    }

    const normalized = key.toLowerCase();
    if (normalized === "space") {
      return "\u0000";
    }

    if (normalized.length === 1 && normalized >= "a" && normalized <= "z") {
      return String.fromCharCode(normalized.charCodeAt(0) - 96);
    }

    if (key.length === 1) {
      switch (key) {
        case "@":
        case "2":
          return "\u0000";
        case "[":
        case "3":
          return "\u001b";
        case "\\":
        case "4":
          return "\u001c";
        case "]":
        case "5":
          return "\u001d";
        case "^":
        case "6":
          return "\u001e";
        case "_":
        case "7":
        case "-":
          return "\u001f";
        case "?":
        case "8":
          return "\u007f";
        default:
          break;
      }
    }

    throw new Error(`Unsupported ctrl key: ${keyInput}`);
  }

  function parseModifierControl(controlInput) {
    const raw = String(controlInput || "").trim().toLowerCase();
    if (!raw) {
      return null;
    }

    const normalized = raw.replace(/\s+/g, "").replace(/_/g, "+").replace(/-/g, "+");
    const parts = normalized.split("+").filter((item) => !!item);
    if (!parts.length) {
      return null;
    }

    const set = new Set(parts);
    for (const item of set) {
      if (item !== "ctrl" && item !== "alt" && item !== "shift") {
        return null;
      }
    }

    return set;
  }

  function applyModifierControl(inputText, modifierSet) {
    if (!modifierSet || !modifierSet.size) {
      return inputText;
    }

    let sequence = String(inputText || "");

    if (modifierSet.has("ctrl")) {
      if (sequence.length !== 1) {
        throw new Error("ctrl modifier requires input to be a single character");
      }
      sequence = ctrlInputToSequence(sequence);
    } else if (modifierSet.has("shift")) {
      sequence = sequence.toUpperCase();
    }

    if (modifierSet.has("alt")) {
      sequence = `\u001b${sequence}`;
    }

    return sequence;
  }

  function resolveProcessWriteInput(body) {
    const hasInput = body.input !== undefined && body.input !== null;
    const hasControl = body.control !== undefined && body.control !== null && String(body.control).trim() !== "";

    if (!hasInput && !hasControl) {
      throw new Error("Missing input: provide input or control");
    }

    const inputText = hasInput ? body.input : "";
    if (hasInput && typeof inputText !== "string") {
      throw new Error("input must be a string");
    }

    let sequence = "";
    let inputConsumedByModifier = false;

    if (hasControl) {
      const modifierSet = parseModifierControl(body.control);
      if (modifierSet && modifierSet.size > 0) {
        if (!hasInput) {
          throw new Error("modifier control requires input");
        }
        sequence += applyModifierControl(inputText, modifierSet);
        inputConsumedByModifier = true;
      } else {
        sequence += controlTokenToSequence(body.control);
      }
    }

    if (hasInput && !inputConsumedByModifier) {
      sequence += inputText;
    }

    const repeatRaw = body.repeat !== undefined ? body.repeat : body.times;
    if (repeatRaw !== undefined && repeatRaw !== null && repeatRaw !== "") {
      const repeat = Number(repeatRaw);
      if (!Number.isFinite(repeat) || repeat < 1 || repeat > 1000) {
        throw new Error("repeat must be an integer in range 1..1000");
      }
      sequence = sequence.repeat(Math.floor(repeat));
    }

    if (!sequence) {
      throw new Error("Resolved input is empty");
    }

    return sequence;
  }

  async function handleApiRequest(req, res, url, { management = false } = {}) {
    const config = state.config;

    if (!management && (url.pathname === "/api/config" || url.pathname.startsWith("/api/startup/"))) {
      sendJson(res, 403, { ok: false, error: "LOCAL_MANAGEMENT_ONLY" });
      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/connection/test") {
      try {
        const body = await readJsonBody(req);
        if (!isAuthorized(config, body.token)) {
          unauthorized(res, "connection.test", !!body.token);
          return true;
        }
        sendJson(res, 200, { ok: true, version: versionInfo.agentVersion, mode: "http-agent",
          capabilities: ["files", "file-move", "file-copy", "file-mkdir", "process-sessions"],
          runtimeBindAddress: runtimeInfo.runtimeBindAddress ? runtimeInfo.runtimeBindAddress() : config.bindAddress,
          port: runtimeInfo.runtimePort ? runtimeInfo.runtimePort() : config.port });
      } catch (error) {
        sendJson(res, 400, { ok: false, error: error.message });
      }
      return true;
    }

    if (req.method === "GET" && url.pathname === "/api/health") {
      if (!management) {
        sendJson(res, 200, { ok: true, mode: "http-agent", version: versionInfo.agentVersion, port: runtimeInfo.runtimePort ? runtimeInfo.runtimePort() : config.port });
        return true;
      }
      const network = processService.getNetworkSnapshot();
      const user = processService.getUserSnapshot();
      const startupIssue = getStartupIssueState();

      sendJson(res, 200, {
        ok: true,
        pid: runtimeInfo.pid(),
        host: runtimeInfo.host(),
        uptimeSec: runtimeInfo.uptimeSec(),
        bindAddress: config.bindAddress,
        runtimeBindAddress: runtimeInfo.runtimeBindAddress ? runtimeInfo.runtimeBindAddress() : config.bindAddress,
        bindOverrideActive: false,
        listener: runtimeInfo.listenerState?.(),
        port: runtimeInfo.runtimePort ? runtimeInfo.runtimePort() : config.port,
        mode: "http-agent",
        version: versionInfo.agentVersion,
        network,
        user,
        startupIssue
      });
      return true;
    }

    if (req.method === "GET" && url.pathname === "/api/startup/state") {
      sendJson(res, 200, {
        ok: true,
        issue: getStartupIssueState()
      });
      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/startup/apply_recommended_bind") {
      if (configUpdating) { sendJson(res, 409, { ok: false, error: "CONFIG_UPDATE_BUSY" }); return true; }
      configUpdating = true;
      try {
        await readJsonBody(req);
        const recommendedHost = processService.getNetworkSnapshot().recommendedHost;
        if (!recommendedHost) throw new Error("No physical network address available; select a network explicitly");
        await commitConfig({ ...state.config, bindAddress: recommendedHost });
        sendJson(res, 200, { ok: true, restartScheduled: false, restartRequired: false,
          bindAddress: recommendedHost, config: buildPublicConfig(state.config, versionInfo) });
      } catch (error) {
        sendJson(res, 400, { ok: false, error: error.message });
      } finally { configUpdating = false; }
      return true;
    }

    if (req.method === "GET" && url.pathname === "/api/config") {
      sendJson(res, 200, buildPublicConfig(config, versionInfo));
      return true;
    }

    if (req.method === "GET" && url.pathname === "/api/presets") {
      sendJson(res, 200, {
        items: getPresetList(config)
      });
      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/config") {
      if (configUpdating) { sendJson(res, 409, { ok: false, error: "CONFIG_UPDATE_BUSY" }); return true; }
      configUpdating = true;
      try {
        const body = await readJsonBody(req);
        const nextConfig = { ...state.config };
        if (body.connectionMode !== undefined) {
          if (!["lan", "frp"].includes(body.connectionMode)) throw new Error("Invalid connection mode");
          nextConfig.connectionMode = body.connectionMode;
        }
        if (body.publicUrl !== undefined) {
          if (typeof body.publicUrl !== "string") throw new Error("Invalid public URL");
          const raw = body.publicUrl.trim();
          if (raw) {
            const endpoint = new URL(raw);
            if (!/^https?:$/.test(endpoint.protocol) || endpoint.username || endpoint.password || endpoint.search || endpoint.hash || /[\s\\]/.test(raw)) throw new Error("Invalid public URL");
          }
          nextConfig.publicUrl = raw;
        }

        const bindAddressInput = pickConfigInput(body, "bindAddress", "bind_address");
        if (bindAddressInput !== undefined) {
          if (typeof bindAddressInput !== "string" || !bindAddressInput.trim()) throw new Error("BIND_ADDRESS_REQUIRED");
          nextConfig.bindAddress = bindAddressInput.trim();
          if (!require("net").isIP(nextConfig.bindAddress) && nextConfig.bindAddress !== "localhost") throw new Error("Bind address must be a local IP address");
        }

        const portInput = pickConfigInput(body, "port", null);
        if (portInput !== undefined) {
          const parsed = Number(portInput);
          if (!Number.isInteger(parsed) || parsed <= 0 || parsed > 65535) {
            sendJson(res, 400, { ok: false, error: "Invalid port" });
            return true;
          }
          nextConfig.port = Math.floor(parsed);
        }

        const maxCommandMsInput = pickConfigInput(body, "maxCommandMs", "max_command_ms");
        if (maxCommandMsInput !== undefined) {
          const parsed = Number(maxCommandMsInput);
          if (!Number.isInteger(parsed) || parsed < 1000 || parsed > 600000) {
            sendJson(res, 400, { ok: false, error: "maxCommandMs must be 1000..600000" });
            return true;
          }
          nextConfig.maxCommandMs = Math.floor(parsed);
        }

        const apiTokenInput = pickConfigInput(body, "apiToken", "api_token");
        if (apiTokenInput !== undefined) {
          nextConfig.apiToken = configStore.ensureApiToken(apiTokenInput);
        }

        nextConfig.apiToken = configStore.ensureApiToken(nextConfig.apiToken);

        const allowedPresetsInput = pickConfigInput(body, "allowedPresets", "allowed_presets");
        if (Array.isArray(allowedPresetsInput)) {
          nextConfig.allowedPresets = configStore.normalizeAllowedPresets(allowedPresetsInput);
        }

        const restartRequired = nextConfig.port !== (runtimeInfo.runtimePort ? runtimeInfo.runtimePort() : state.config.port)
          || nextConfig.bindAddress !== (runtimeInfo.runtimeBindAddress ? runtimeInfo.runtimeBindAddress() : state.config.bindAddress);

        await commitConfig(nextConfig);

        logger.info("config.update.success", {
          bindAddress: state.config.bindAddress,
          port: state.config.port,
          maxCommandMs: state.config.maxCommandMs,
          allowedPresets: state.config.allowedPresets,
          restartRequired
        });

        sendJson(res, 200, {
          ok: true,
          restartRequired: applyConfig ? false : restartRequired,
          config: buildPublicConfig(state.config, versionInfo)
        });
      } catch (error) {
        logger.error("config.update.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      } finally { configUpdating = false; }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/command/execute") {
      try {
        const body = await readJsonBody(req);

        logger.info("command.execute.request", {
          hasPreset: !!body.preset,
          preset: body.preset || null,
          shell: body.shell || null,
          rawCommandLength: typeof body.command === "string" ? body.command.length : 0,
          timeoutMs: body.timeoutMs || null,
          tokenProvided: !!body.token
        });

        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "command.execute", body.token, {
            preset: body.preset || null
          });
          return true;
        }

        const timeout =
          typeof body.timeoutMs === "number" && body.timeoutMs > 0
            ? Math.min(body.timeoutMs, 600000)
            : state.config.maxCommandMs;

        let shell = "powershell";
        let command = "";

        if (body.preset) {
          if (!presetCommands[body.preset]) {
            logger.warn("command.execute.invalidPreset", { preset: body.preset });
            sendJson(res, 400, { ok: false, error: "Unknown preset" });
            return true;
          }

          if (!state.config.allowedPresets.includes(body.preset)) {
            logger.warn("command.execute.presetNotAllowed", { preset: body.preset });
            sendJson(res, 403, { ok: false, error: "Preset is not allowed by config" });
            return true;
          }

          shell = presetCommands[body.preset].shell;
          command = presetCommands[body.preset].command;
        } else {
          if (!body.command || typeof body.command !== "string") {
            logger.warn("command.execute.missingCommand", {});
            sendJson(res, 400, { ok: false, error: "Missing command" });
            return true;
          }

          shell = String(body.shell || "powershell").toLowerCase();
          command = body.command;
        }

        const result = await processService.runCommand(shell, command, timeout);

        logger.info("command.execute.result", {
          ok: result.exitCode === 0 && !result.timedOut,
          shell,
          exitCode: result.exitCode,
          timedOut: result.timedOut,
          durationMs: result.durationMs,
          stdoutLength: result.stdout.length,
          stderrLength: result.stderr.length
        });

        sendJson(res, 200, {
          ok: result.exitCode === 0 && !result.timedOut,
          shell,
          command,
          exitCode: result.exitCode,
          timedOut: result.timedOut,
          durationMs: result.durationMs,
          stdout: result.stdout,
          stderr: result.stderr
        });
      } catch (error) {
        logger.error("command.execute.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/process/start") {
      try {
        const body = await readJsonBody(req);

        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "process.start", body.token);
          return true;
        }

        const result = processService.startSession(body.shell, body.command, {
          maxRuntimeMs: body.max_runtime_ms !== undefined ? body.max_runtime_ms : body.maxRuntimeMs
        });

        logger.info("process.start.success", {
          sessionId: result.sessionId,
          shell: result.shell,
          pid: result.pid,
          status: result.status,
          hasCommand: typeof body.command === "string" && body.command.trim().length > 0
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("process.start.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/process/read") {
      try {
        const body = await readJsonBody(req);

        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "process.read", body.token);
          return true;
        }

        const sessionId = body.session_id !== undefined ? body.session_id : body.sessionId;
        const result = await processService.readSession(sessionId, {
          viewMode: body.view_mode !== undefined ? body.view_mode : body.viewMode,
          stdoutOffset: body.stdout_offset !== undefined ? body.stdout_offset : body.stdoutOffset,
          stderrOffset: body.stderr_offset !== undefined ? body.stderr_offset : body.stderrOffset,
          maxChars: body.max_chars !== undefined ? body.max_chars : body.maxChars
        });

        logger.info("process.read.success", {
          sessionId: result.sessionId,
          status: result.status,
          stdoutLength: result.stdout.length,
          stderrLength: result.stderr.length,
          stdoutOffset: result.stdoutOffset,
          stderrOffset: result.stderrOffset,
          hasMore: result.hasMore
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("process.read.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/process/write") {
      try {
        const body = await readJsonBody(req);

        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "process.write", body.token);
          return true;
        }

        const sessionId = body.session_id !== undefined ? body.session_id : body.sessionId;
        const resolvedInput = resolveProcessWriteInput(body);
        const result = await processService.writeSession(sessionId, resolvedInput);

        logger.info("process.write.success", {
          sessionId: result.sessionId,
          status: result.status,
          acceptedChars: result.acceptedChars
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("process.write.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/process/resize") {
      try {
        const body = await readJsonBody(req);

        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "process.resize", body.token);
          return true;
        }

        const sessionId = body.session_id !== undefined ? body.session_id : body.sessionId;
        const result = processService.resizeSession(sessionId, {
          cols: body.cols,
          rows: body.rows
        });

        logger.info("process.resize.success", {
          sessionId: result.sessionId,
          status: result.status,
          cols: result.cols,
          rows: result.rows
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("process.resize.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/process/terminate") {
      try {
        const body = await readJsonBody(req);

        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "process.terminate", body.token);
          return true;
        }

        const sessionId = body.session_id !== undefined ? body.session_id : body.sessionId;
        const result = processService.terminateSession(sessionId, {
          remove: parseBoolean(body.remove, false)
        });

        logger.info("process.terminate.success", {
          sessionId: result.sessionId,
          status: result.status,
          wasRunning: result.wasRunning,
          signalSent: result.signalSent,
          removed: result.removed
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("process.terminate.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/process/list") {
      try {
        const body = await readJsonBody(req);

        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "process.list", body.token);
          return true;
        }

        const includeExited = parseBoolean(
          body.include_exited !== undefined ? body.include_exited : body.includeExited,
          true
        );
        const result = processService.listSessions({ includeExited });

        logger.info("process.list.success", {
          includeExited,
          count: result.items.length
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("process.list.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && ["/api/file/move", "/api/file/copy", "/api/file/mkdir", "/api/file/stat"].includes(url.pathname)) {
      try {
        const body = await readJsonBody(req);
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.operation", !!body.token);
          return true;
        }
        const operation = url.pathname.split("/").pop();
        const result = operation === "move" ? fileService.movePath(body.path, body.destination)
          : operation === "copy" ? fileService.copyFile(body.path, body.destination)
          : operation === "mkdir" ? fileService.makeDirectory(body.path)
          : fileService.statPath(body.path);
        sendJson(res, 200, { ok: true, ...result });
      } catch (error) {
        sendJson(res, 400, { ok: false, error: error.message, code: error.code || "INVALID_ARGUMENT" });
      }
      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/list") {
      try {
        const body = await readJsonBody(req);
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.list", body.token);
          return true;
        }

        const result = fileService.listDirectory(body.path, body.depth);
        logger.info("file.list.success", {
          path: result.path,
          depth: result.depth,
          itemCount: result.items.length
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.list.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/read") {
      try {
        const body = await readJsonBody(req);
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.read", body.token);
          return true;
        }

        const result = fileService.readTextFile(body.path, body.encoding);
        logger.info("file.read.success", {
          path: result.path,
          sizeBytes: result.sizeBytes,
          encoding: result.encoding
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.read.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/read_segment") {
      try {
        const body = await readJsonBody(req);
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.read_segment", body.token);
          return true;
        }

        const result = fileService.readTextSegment(body.path, {
          offset: body.offset,
          length: body.length,
          encoding: body.encoding
        });

        logger.info("file.read_segment.success", {
          path: result.path,
          offset: result.offset,
          length: result.length,
          totalBytes: result.totalBytes,
          eof: result.eof,
          encoding: result.encoding
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.read_segment.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/read_lines") {
      try {
        const body = await readJsonBody(req);
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.read_lines", body.token);
          return true;
        }

        const result = fileService.readTextLines(body.path, {
          line_start: body.line_start,
          line_end: body.line_end,
          encoding: body.encoding
        });

        logger.info("file.read_lines.success", {
          path: result.path,
          lineStart: result.lineStart,
          lineEnd: result.lineEnd,
          totalLines: result.totalLines,
          eof: result.eof,
          encoding: result.encoding
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.read_lines.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/write") {
      try {
        const body = await readJsonBody(req, {
          maxBodyBytes: FILE_WRITE_JSON_MAX_BODY_BYTES
        });
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.write", body.token);
          return true;
        }

        const result = fileService.writeTextFile(body.path, body.content, body.encoding);
        logger.info("file.write.success", {
          path: result.path,
          sizeBytes: result.sizeBytes,
          encoding: result.encoding
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.write.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/edit") {
      try {
        const body = await readJsonBody(req, {
          maxBodyBytes: FILE_WRITE_JSON_MAX_BODY_BYTES
        });
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.edit", body.token);
          return true;
        }

        const result = fileService.editTextFile(
          body.path,
          body.old_text,
          body.new_text,
          body.expected_replacements,
          body.encoding
        );
        logger.info("file.edit.success", {
          path: result.path,
          sizeBytes: result.sizeBytes,
          encoding: result.encoding,
          replacements: result.replacements,
          expectedReplacements: result.expectedReplacements
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.edit.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/read_base64") {
      try {
        const body = await readJsonBody(req);
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.read_base64", body.token);
          return true;
        }

        const result = fileService.readBase64File(body.path, {
          offset: body.offset,
          length: body.length
        });

        logger.info("file.read_base64.success", {
          path: result.path,
          offset: result.offset,
          length: result.length,
          totalBytes: result.totalBytes,
          eof: result.eof
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.read_base64.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/write_base64") {
      try {
        const body = await readJsonBody(req, {
          maxBodyBytes: FILE_WRITE_BASE64_JSON_MAX_BODY_BYTES
        });
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.write_base64", body.token);
          return true;
        }

        const result = fileService.writeBase64File(body.path, body.base64);
        logger.info("file.write_base64.success", {
          path: result.path,
          sizeBytes: result.sizeBytes
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.write_base64.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    return false;
  }

  return {
    handleApiRequest
  };
}

module.exports = {
  createApiHandler
};
