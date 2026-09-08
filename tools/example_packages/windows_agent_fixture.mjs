import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import net from "node:net";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { fileURLToPath } from "node:url";

/** 真正启动隔离的 PC Agent，只在明确创建的临时目录写入运行配置和日志。 */
export async function startAgentFixture(options = {}) {
  const source = fileURLToPath(new URL("../../examples/windows_control/resources/pc_agent/kiyori-pc-agent", import.meta.url));
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "kiyori-windows-fixture-"));
  fs.cpSync(source, directory, { recursive: true, filter: entry => !["node_modules", "logs", "data"].includes(path.basename(entry)) });
  const reservation = net.createServer();
  reservation.listen(0, "127.0.0.1"); await once(reservation, "listening");
  const port = reservation.address().port;
  await new Promise(resolve => reservation.close(resolve));
  fs.mkdirSync(path.join(directory, "data"));
  fs.writeFileSync(path.join(directory, "data/config.json"), JSON.stringify({ bindAddress: options.bindAddress || "127.0.0.1", port, apiToken: "isolated-fixture-token", maxCommandMs: 30000, allowedPresets: ["whoami"], connectionMode: "frp", publicUrl: "https://pc.example.com/bridge" }));
  const child = spawn(process.execPath, ["src/server.js"], { cwd: directory, windowsHide: true, stdio: ["pipe", "pipe", "pipe"], env: { ...process.env, NODE_OPTIONS: "", KIYORI_BIND_ADDRESS_OVERRIDE: "" } });
  let errors = "";
  child.stderr.on("data", data => { errors += data; });
  const closed = once(child, "close");
  async function stop() {
    if (child.exitCode === null && child.signalCode === null) child.kill();
    await closed;
    if (path.dirname(directory) !== os.tmpdir() || !path.basename(directory).startsWith("kiyori-windows-fixture-")) throw new Error("Unexpected fixture path");
    fs.rmSync(directory, { recursive: true, force: true });
  }
  try {
    const managementUrl = await new Promise((resolve, reject) => {
      let output = "";
      const timer = setTimeout(() => reject(new Error(`Agent fixture startup timeout: ${errors}`)), 10000);
      child.stdout.on("data", data => {
        output += data;
        const match = output.match(/Kiyori PC Agent: (http:\/\/127\.0\.0\.1:\d+)/);
        if (match) { clearTimeout(timer); resolve(match[1]); }
      });
      child.once("error", error => { clearTimeout(timer); reject(error); });
      child.once("exit", () => { clearTimeout(timer); reject(new Error(`Agent fixture exited: ${errors}`)); });
    });
    return { directory, port, managementUrl, executionUrl: `http://127.0.0.1:${port}`, stop };
  } catch (error) { await stop(); throw error; }
}

if (process.argv.includes("--preview")) {
  const fixture = await startAgentFixture();
  console.log(JSON.stringify({ managementUrl: fixture.managementUrl, executionUrl: fixture.executionUrl }));
  process.stdin.resume();
  let stopping = false;
  const stop = async () => { if (stopping) return; stopping = true; await fixture.stop(); process.exit(0); };
  process.on("SIGINT", stop); process.on("SIGTERM", stop); process.stdin.on("data", stop);
}
