const crypto = require("crypto");

function tokenMatches(expected, provided) {
  if (typeof expected !== "string" || !expected || typeof provided !== "string") return false;
  const a = Buffer.from(expected);
  const b = Buffer.from(provided);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

// 管理端口独立且只监听 loopback；不根据代理转发后的 remoteAddress 授予管理权。
function allowManagementRequest(req) {
  const expectedHost = `127.0.0.1:${req.socket.localPort}`;
  if (req.headers.host !== expectedHost) return false;
  if (req.headers.origin && req.headers.origin !== `http://${expectedHost}`) return false;
  if (req.headers["sec-fetch-site"] === "cross-site") return false;
  if (req.method === "POST" && !/^application\/json(?:\s*;|$)/i.test(req.headers["content-type"] || "")) return false;
  return true;
}

module.exports = { tokenMatches, allowManagementRequest };
