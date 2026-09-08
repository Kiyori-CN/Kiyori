const fs = require("fs");
const crypto = require("crypto");
const { ensureDir } = require("../lib/fs-utils");

function pickDefined(...values) {
  for (const value of values) {
    if (value !== undefined) {
      return value;
    }
  }
  return undefined;
}

function createConfigStore({ dataDir, configPath, defaultConfig, presetCommands }) {
  function generateApiToken() {
    return crypto.randomBytes(24).toString("base64url");
  }

  function ensureApiToken(value) {
    const token = String(value || "").trim();
    if (token) {
      return token;
    }
    return generateApiToken();
  }

  function normalizeAllowedPresets(value) {
    if (!Array.isArray(value)) {
      return [...defaultConfig.allowedPresets];
    }

    return value.filter((name) => presetCommands[name]);
  }

  function loadConfig() {
    ensureDir(dataDir);

    if (!fs.existsSync(configPath)) {
      const initialConfig = {
        ...defaultConfig,
        apiToken: ensureApiToken(defaultConfig.apiToken)
      };
      fs.writeFileSync(configPath, JSON.stringify(initialConfig, null, 2), "utf8");
      return initialConfig;
    }

    try {
      const parsed = JSON.parse(fs.readFileSync(configPath, "utf8"));
      const bindAddress = pickDefined(parsed.bindAddress, parsed.bind_address);
      const port = pickDefined(parsed.port);
      const maxCommandMs = pickDefined(parsed.maxCommandMs, parsed.max_command_ms);
      const apiToken = pickDefined(parsed.apiToken, parsed.api_token);
      const allowedPresets = pickDefined(parsed.allowedPresets, parsed.allowed_presets);

      const normalized = {
        connectionMode: parsed.connectionMode === "frp" ? "frp" : "lan",
        publicUrl: typeof parsed.publicUrl === "string" ? parsed.publicUrl : "",
        bindAddress: typeof bindAddress === "string" && bindAddress.trim() ? bindAddress.trim() : defaultConfig.bindAddress,
        port:
          Number.isFinite(Number(port)) && Number(port) > 0 && Number(port) <= 65535
            ? Math.floor(Number(port))
            : defaultConfig.port,
        apiToken: ensureApiToken(apiToken),
        maxCommandMs:
          Number.isFinite(Number(maxCommandMs)) && Number(maxCommandMs) >= 1000 && Number(maxCommandMs) <= 600000
            ? Math.floor(Number(maxCommandMs))
            : defaultConfig.maxCommandMs,
        allowedPresets: normalizeAllowedPresets(allowedPresets)
      };

      saveConfig(normalized);
      return normalized;
    } catch (error) {
      throw new Error(`Cannot load PC Agent configuration; original file preserved (${error.code || error.name})`);
    }
  }

  function saveConfig(config) {
    ensureDir(dataDir);
    const temporary = `${configPath}.${crypto.randomUUID()}.tmp`;
    try {
      fs.writeFileSync(temporary, JSON.stringify(config, null, 2), { encoding: "utf8", flag: "wx", mode: 0o600 });
      fs.renameSync(temporary, configPath);
    } finally {
      if (fs.existsSync(temporary)) fs.unlinkSync(temporary);
    }
  }

  return {
    loadConfig,
    saveConfig,
    normalizeAllowedPresets,
    generateApiToken,
    ensureApiToken
  };
}

module.exports = {
  createConfigStore
};
