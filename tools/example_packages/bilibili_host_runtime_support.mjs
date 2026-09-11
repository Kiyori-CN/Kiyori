import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import vm from "node:vm";
import { repository, memoryTools, readMetadata } from "./bilibili_test_support.mjs";

const hostDirectory = "app/src/main/java/com/ai/assistance/operit/core/tools/javascript";

async function kotlinScript(file, functionName) {
  const source = await fs.readFile(path.join(repository, file), "utf8");
  const start = source.indexOf("fun " + functionName + "(");
  assert.notEqual(start, -1, functionName);
  const opening = source.indexOf('"""', start);
  const closing = source.indexOf('"""', opening + 3);
  return source.slice(opening + 3, closing);
}

export async function hostRuntime(service, tools = memoryTools().tools, modules = {}) {
  const completions = new Map();
  const calls = [];
  const logs = [];
  const context = vm.createContext({});
  context.NativeInterface = {
    __call(method, serialized) {
      const args = JSON.parse(serialized);
      calls.push({ method, args });
      if (method === "bilibiliRequest") {
        const [, callbackId, request] = args;
        service.get(JSON.parse(request)).then(
          result => context[callbackId](JSON.stringify(result), false),
          error => context[callbackId](JSON.stringify(error.details ?? { success: false, error: { code: "TEST_ADAPTER_ERROR", message: error.message } }), false)
        );
      } else if (method === "setCallResult" || method === "setCallError") {
        completions.get(args[0])({ method, result: JSON.parse(args[1]) });
      } else if (method === "logErrorForCall" || method === "logInfoForCall") {
        logs.push(args[1]);
      } else if (method === "getEnvForCall") {
        return "";
      } else if (method === "getArtifactPathForCall") {
        assert.equal(args[1], "android");
        return "/storage/emulated/0/Download/Kiyori/workspace";
      } else if (method === "getArtifactPathsForCall") {
        // 打包脚本在没有显式 output_root 时读取默认产物目录；夹具给出与偏好默认值一致的根。
        return JSON.stringify({
          android: "/storage/emulated/0/Download/Kiyori/workspace",
          linux: "/workspace",
          linuxIsLocal: true
        });
      } else if (method === "readToolPkgTextResource") {
        return modules[args[1]] ?? null;
      } else if (method === "callToolAsyncForExecution") {
        const [callbackId, , , name, serializedParams] = args;
        const params = JSON.parse(serializedParams);
        const handlers = {
          file_exists: () => tools.Files.exists(params.path),
          file_info: () => tools.Files.info(params.path),
          list_files: () => tools.Files.list(params.path),
          make_directory: () => tools.Files.mkdir(params.path, params.create_parents === "true"),
          write_file: () => tools.Files.write(params.path, params.content),
          move_file: () => tools.Files.move(params.source, params.destination),
          delete_file: () => tools.Files.deleteFile(params.path, params.recursive === "true"),
          download_file: () => tools.Files.download(params.url, params.destination, params.environment, params.headers === undefined ? undefined : JSON.parse(params.headers)),
          ffmpeg_info: () => tools.FFmpeg.info(params.type),
          ffmpeg_probe: () => tools.FFmpeg.probe(params.input_path),
          ffmpeg_execute: () => tools.FFmpeg.execute(params.command)
        };
        assert.equal(typeof handlers[name], "function", name);
        Promise.resolve().then(handlers[name]).then(
          data => context[callbackId](JSON.stringify({ success: true, data }), false),
          error => context[callbackId]({ success: false, message: error.message, data: { tool: name } }, true)
        );
      } else {
        throw new Error("Unexpected native call: " + method);
      }
      return null;
    }
  };
  const run = (source, filename) => vm.runInContext(source, context, { filename });
  run(await kotlinScript("quickjs/src/main/java/com/ai/assistance/operit/core/tools/javascript/QuickJsNativeCompatScriptBuilder.kt", "buildQuickJsCompatScript"), "compat.js");
  // 与 buildInitRuntimeModules 的装配顺序一致：ToolPkg API runtime 必须在 registration 与
  // JsTools 之前加载，否则 __operitToolPkgApi 缺失会让整组 host 测试无法启动。
  for (const name of ["buildRuntimeExposeScript", "buildRuntimeCallRegistryScript"]) {
    run(await kotlinScript(hostDirectory + "/JsInitRuntimeScriptBuilder.kt", name), name + ".js");
  }
  run(await kotlinScript(hostDirectory + "/JsToolPkgApiRuntime.kt", "buildToolPkgApiRuntimeScript"), "toolpkg-api-runtime.js");
  for (const name of ["buildRuntimeErrorScript", "buildRuntimeToolCallScript"]) {
    run(await kotlinScript(hostDirectory + "/JsInitRuntimeScriptBuilder.kt", name), name + ".js");
  }
  const prelude = await kotlinScript(hostDirectory + "/JsExecutionScriptBuilder.kt", "buildExecutionPreludeSource");
  const execution = (await kotlinScript(hostDirectory + "/JsExecutionScriptBuilder.kt", "buildExecutionRuntimeBridgeScript"))
    .replaceAll("$TOOLPKG_EXECUTION_ENTRY_FUNCTION", "__operitExecuteScriptFunction")
    .replace("$preludeSource", JSON.stringify(prelude));
  run(execution, "execution.js");
  run(await kotlinScript(hostDirectory + "/JsToolPkgRegistration.kt", "buildToolPkgRegistrationBridgeScript"), "toolpkg.js");
  run(await kotlinScript(hostDirectory + "/JsTools.kt", "getJsToolsDefinition"), "tools.js");
  run(await kotlinScript(hostDirectory + "/JsLibraries.kt", "getJsThirdPartyLibraries"), "third-party-libs.js");
  const bundle = await fs.readFile(path.join(repository, "examples/bilibili_toolkit/dist/packages/bilibili.js"), "utf8");
  return {
    calls, logs,
    execute(name, params, script = bundle) {
      const callId = "test-" + completions.size;
      return new Promise(resolve => {
        completions.set(callId, resolve);
        context.__operitExecuteScriptFunction(callId, params, script, name, null, null);
      });
    }
  };
}

export async function loadHostToolkit(service, tools) {
  const host = await hostRuntime(service, tools);
  const api = {};
  for (const { name } of (await readMetadata()).tools) {
    api[name] = async params => {
      const response = await host.execute(name, params);
      assert.equal(response.method, "setCallResult", JSON.stringify(response));
      return response.result;
    };
  }
  return api;
}
