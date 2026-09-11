const packageJson = require("../../package.json");

const AGENT_VERSION = String(packageJson.version || "0.0.0").trim() || "0.0.0";
// 包版本可在未发布阶段独立调整；认证后的执行协议不能跟随产品版本重置。
const AGENT_PROTOCOL_VERSION = "1.1.0";

module.exports = {
  AGENT_VERSION,
  AGENT_PROTOCOL_VERSION
};
