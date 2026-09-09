/**
 * ToolPkg 薄层运行时：环境校验、跨环境搬运、命令拼装、信封解析、产物回搬。
 *
 * 本文件不含任何 OOXML/PDF 格式逻辑（见 docs/TODO/office_document_suite/index.md）。
 */

import {
  OFFICE_BEGIN,
  OFFICE_END,
  OfficeArtifact,
  OfficeEnvelope,
  asText,
  parseEnvelope,
  toFailure
} from "./protocol";

export const PACKAGE_ID = "com.kiyori.office_suite";
export const EXECUTOR_KEY = "kiyori_office";
const RUNTIME_RESOURCE_KEY = "kiyori_office_runtime";
const RUNTIME_ZIP_NAME = "kiyori_office_runtime.zip";

export type FileEnv = "android" | "linux";

export type ToolSpec = {
  command: string;
  inputPaths?: string[];
  outputKind?: "single" | "multi";
  defaultOutputName: string;
  /** 是否要求显式 env；纯环境/清理类命令不接触文件路径。 */
  requiresEnv?: boolean;
  timeoutMs?: number;
};

type CachedPaths = {
  home: string;
  runtimeDir: string;
  zipPath: string;
  bootstrapPath: string;
  python: string;
};

let cachedPaths: CachedPaths | null = null;
let runtimeReady = false;

const BOOTSTRAP_SCRIPT = [
  "import os",
  "import shutil",
  "import sys",
  "import zipfile",
  "",
  "zip_path, target = sys.argv[1], sys.argv[2]",
  "marker = os.path.join(target, '.zip-size')",
  "size = str(os.path.getsize(zip_path))",
  "if os.path.isfile(marker):",
  "    with open(marker, 'r', encoding='utf-8') as handle:",
  "        if handle.read().strip() == size:",
  "            print('__KIYORI_OFFICE_READY__')",
  "            raise SystemExit(0)",
  "package_dir = os.path.join(target, 'kiyori_office')",
  "if os.path.isdir(package_dir):",
  "    shutil.rmtree(package_dir)",
  "with zipfile.ZipFile(zip_path) as archive:",
  "    archive.extractall(target)",
  "with open(marker, 'w', encoding='utf-8') as handle:",
  "    handle.write(size)",
  "print('__KIYORI_OFFICE_READY__')",
  ""
].join("\n");

export function shellQuote(value: unknown): string {
  const text = asText(value);
  return "'" + text.replace(/'/g, "'\\''") + "'";
}

export function requireEnv(params: Record<string, unknown>, field: string): FileEnv {
  const value = asText(params[field]).trim();
  if (value !== "android" && value !== "linux") {
    throw new Error(
      `E_INPUT_SCHEMA: ${field} 必须显式传入 android 或 linux，禁止推断环境`
    );
  }
  return value;
}

export function newTaskId(): string {
  return `t${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
}

export function baseName(path: string): string {
  const normalized = asText(path).replace(/\\/g, "/");
  const index = normalized.lastIndexOf("/");
  return index >= 0 ? normalized.slice(index + 1) : normalized;
}

async function runHidden(command: string, timeoutMs: number): Promise<{ output: string; exitCode: number }> {
  // office 运行时只存在于本地 Ubuntu/PRoot；显式 localOnly，避免文档被送到
  // 未安装运行时的远端 SSH 环境后产生难以诊断的失败。
  const result = await Tools.System.terminal.hiddenExec(command, {
    executorKey: EXECUTOR_KEY,
    timeoutMs,
    localOnly: true
  });
  return { output: asText(result.output), exitCode: Number(result.exitCode == null ? 1 : result.exitCode) };
}

export async function resolvePaths(): Promise<CachedPaths> {
  if (cachedPaths) {
    return cachedPaths;
  }
  const probe = await runHidden(
    'printf "%s\\n" "$HOME"; if [ -x "$HOME/.code_runner/py/bin/python" ]; then printf "%s\\n" "$HOME/.code_runner/py/bin/python"; else command -v python3; fi',
    20000
  );
  const lines = probe.output
    .split(/\r?\n/g)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
  const home = lines[0] || "";
  const python = lines[1] || "";
  if (!home || !python) {
    throw new Error(
      `E_ENV_MISSING: 无法解析 Linux HOME 或 Python 解释器（output=${probe.output.slice(0, 400)}）`
    );
  }
  cachedPaths = {
    home,
    runtimeDir: `${home}/kiyori_office/runtime`,
    zipPath: `${home}/kiyori_office/runtime.zip`,
    bootstrapPath: `${home}/kiyori_office/unzip_runtime.py`,
    python
  };
  return cachedPaths;
}

async function ensureRuntime(): Promise<CachedPaths> {
  const paths = await resolvePaths();
  if (runtimeReady) {
    return paths;
  }
  const resourcePath = await ToolPkg.readResource(RUNTIME_RESOURCE_KEY, RUNTIME_ZIP_NAME, true);
  if (!asText(resourcePath).trim()) {
    throw new Error("E_ENV_MISSING: 无法读取 kiyori_office 运行时资源");
  }
  await Tools.Files.mkdir(`${paths.home}/kiyori_office`, true, "linux");
  await Tools.Files.copy(resourcePath, paths.zipPath, false, "android", "linux");
  await Tools.Files.write(paths.bootstrapPath, BOOTSTRAP_SCRIPT, false, "linux");
  const result = await runHidden(
    `${shellQuote(paths.python)} ${shellQuote(paths.bootstrapPath)} ${shellQuote(paths.zipPath)} ${shellQuote(paths.runtimeDir)}`,
    120000
  );
  if (result.exitCode !== 0 || !result.output.includes("__KIYORI_OFFICE_READY__")) {
    throw new Error(
      `E_ENV_MISSING: kiyori_office 运行时解压失败: ${result.output.slice(0, 800)}`
    );
  }
  runtimeReady = true;
  return paths;
}

async function stageInput(
  paths: CachedPaths,
  taskDir: string,
  value: string,
  env: FileEnv
): Promise<string> {
  if (env === "linux") {
    return value;
  }
  const staged = `${taskDir}/in/${baseName(value)}`;
  await Tools.Files.mkdir(`${taskDir}/in`, true, "linux");
  await Tools.Files.copy(value, staged, false, "android", "linux");
  return staged;
}

async function androidTarget(
  params: Record<string, unknown>,
  spec: ToolSpec,
  taskId: string
): Promise<string> {
  const explicit = asText(params.output_path).trim();
  if (explicit) {
    return explicit;
  }
  const base = asText(typeof KIYORI_DOWNLOAD_DIR === "string" ? KIYORI_DOWNLOAD_DIR : "").trim();
  const root = base || "/sdcard/Download";
  const name =
    spec.outputKind === "multi"
      ? `${stripExtension(spec.defaultOutputName)}-${taskId}`
      : spec.defaultOutputName;
  return `${root}/Office/${name}`;
}

function stripExtension(name: string): string {
  const index = name.lastIndexOf(".");
  return index > 0 ? name.slice(0, index) : name;
}

function relativeTo(base: string, target: string): string {
  const normalizedBase = base.replace(/\/+$/, "");
  if (target.startsWith(`${normalizedBase}/`)) {
    return target.slice(normalizedBase.length + 1);
  }
  return baseName(target);
}

async function deliverArtifacts(
  artifacts: OfficeArtifact[],
  params: Record<string, unknown>,
  spec: ToolSpec,
  taskId: string,
  data: Record<string, unknown>
): Promise<OfficeArtifact[]> {
  const declared = asText(params.output_env).trim();
  if (declared && declared !== "android" && declared !== "linux") {
    throw new Error(
      "E_INPUT_SCHEMA: output_env 必须显式传入 android 或 linux，禁止推断环境"
    );
  }
  const outputEnv: FileEnv = declared === "linux" ? "linux" : "android";
  if (outputEnv === "linux") {
    return artifacts;
  }
  if (artifacts.length === 0) {
    return artifacts;
  }
  const target = await androidTarget(params, spec, taskId);
  const overwrite = params.overwrite === true;
  const multi = spec.outputKind === "multi";
  const targetDir = multi ? target : target.replace(/\/[^/]*$/, "");
  const baseDir = multi && typeof data.target_dir === "string" ? asText(data.target_dir) : "";

  if (multi) {
    const exists = await Tools.Files.exists(target, "android");
    if (exists && exists.exists && !overwrite) {
      throw new Error(`E_PATH_EXISTS: 目标目录已存在且未设置 overwrite=true: ${target}`);
    }
  }
  await Tools.Files.mkdir(targetDir, true, "android");

  const delivered: OfficeArtifact[] = [];
  for (const item of artifacts) {
    const destination = multi
      ? `${target}/${baseDir ? relativeTo(baseDir, item.path) : baseName(item.path)}`
      : target;
    if (!multi) {
      const exists = await Tools.Files.exists(destination, "android");
      if (exists && exists.exists && !overwrite) {
        throw new Error(
          `E_PATH_EXISTS: 目标文件已存在且未设置 overwrite=true: ${destination}`
        );
      }
    }
    const parent = destination.replace(/\/[^/]*$/, "");
    if (parent) {
      await Tools.Files.mkdir(parent, true, "android");
    }
    await Tools.Files.copy(item.path, destination, false, "linux", "android");
    delivered.push({ ...item, path: destination, env: "android" });
  }
  return delivered;
}

function hasArtifacts(envelope: OfficeEnvelope): boolean {
  return Array.isArray(envelope.artifacts) && envelope.artifacts.length > 0;
}

const WORKSPACE_DIRECTORIES = ["source", "output", "templates", "assets"];

const WORKSPACE_AGENTS_MARKDOWN = [
  "# 办公文档工作区规则",
  "",
  "- 先 `office_env_check`，再读对应格式 Skill；输入先 outline/read 再编辑。",
  "- 产物一律写入 `output/`，不要原地覆盖 `source/` 里的用户文件。",
  "- 含公式的 xlsx 交付前必须 `xlsx_recalc`，错误数为 0 才可交付。",
  "- 交付前执行 `office_validate` 与 `office_render_preview`，并用 direct_image 看图。",
  "- 模板与素材放 `templates/` 与 `assets/`，不要把中间文件混进 `output/`。",
  ""
].join("\n");

async function createAndroidWorkspace(input: Record<string, unknown>): Promise<unknown> {
  const target = asText(input.dir).trim();
  if (!target) {
    return toFailure("office_workspace_init", new Error("E_INPUT_SCHEMA: dir 不能为空"));
  }
  const created: string[] = [];
  for (const name of WORKSPACE_DIRECTORIES) {
    await Tools.Files.mkdir(`${target}/${name}`, true, "android");
    created.push(`${target}/${name}`);
  }
  const agentsPath = `${target}/AGENTS.md`;
  const exists = await Tools.Files.exists(agentsPath, "android");
  if (!exists || !exists.exists || input.overwrite === true) {
    await Tools.Files.write(agentsPath, WORKSPACE_AGENTS_MARKDOWN, false, "android");
    created.push(agentsPath);
  }
  return {
    success: true,
    message: "office_workspace_init 执行完成",
    command: "office_workspace_init",
    artifacts: [],
    data: {
      dir: target,
      created,
      directories: WORKSPACE_DIRECTORIES
    },
    warnings: [],
    truncated: false,
    full_output_path: null,
    next_actions: ["office_env_check"],
    metrics: {}
  };
}

export async function runOfficeTool(
  spec: ToolSpec,
  params: Record<string, unknown> | undefined
): Promise<unknown> {
  const input = params || {};
  const command = spec.command;
  if (!command) {
    throw new Error(`E_PROTOCOL: ${spec.defaultOutputName} 缺少 Python 命令映射`);
  }
  const env: FileEnv = spec.requiresEnv === false ? "linux" : requireEnv(input, "env");
  if (input.in_place === true && env === "android") {
    throw new Error(
      "E_INPUT_SCHEMA: in_place 只支持 Linux 工作区；Android 源文件一律先复制到暂存区"
    );
  }
  if (command === "office_workspace_init" && env === "android") {
    // Android 工作区不能交给 Linux 侧创建：/sdcard 路径不属于允许根。
    return await createAndroidWorkspace(input);
  }
  const paths = await ensureRuntime();
  const taskId = asText(input.task_id).trim() || newTaskId();
  const taskDir = `${paths.home}/kiyori_office/work/${taskId}`;
  await Tools.Files.mkdir(`${taskDir}/out`, true, "linux");

  const payload: Record<string, unknown> = { task_id: taskId };
  const allowRoots: string[] = [];
  for (const [key, value] of Object.entries(input)) {
    if (key === "env" || key === "output_env" || key === "timeoutMs") {
      continue;
    }
    payload[key] = value;
  }

  for (const field of spec.inputPaths || []) {
    const raw = input[field];
    if (Array.isArray(raw)) {
      const stagedList: string[] = [];
      for (const item of raw) {
        const text = asText(item).trim();
        if (!text) {
          throw new Error(`E_INPUT_SCHEMA: ${field} 不能包含空路径`);
        }
        const staged = await stageInput(paths, taskDir, text, env);
        stagedList.push(staged);
        allowRoots.push(staged.replace(/\/[^/]*$/, ""));
      }
      payload[field] = stagedList;
      continue;
    }
    const text = asText(raw).trim();
    if (!text) {
      continue;
    }
    const staged = await stageInput(paths, taskDir, text, env);
    payload[field] = staged;
    allowRoots.push(staged.replace(/\/[^/]*$/, ""));
  }

  const declaredOutputEnv = asText(input.output_env).trim();
  if (declaredOutputEnv) {
    if (declaredOutputEnv !== "android" && declaredOutputEnv !== "linux") {
      throw new Error(
        "E_INPUT_SCHEMA: output_env 必须显式传入 android 或 linux，禁止推断环境"
      );
    }
    if (declaredOutputEnv === "linux" && asText(input.output_path).trim()) {
      // 由 Python 侧直接写入调用方指定的 Linux 路径，仍需通过 allow_roots 校验。
      allowRoots.push(asText(input.output_path).trim().replace(/\/[^/]*$/, ""));
    } else {
      // Android 交付：Python 先写暂存区，再由 JS 回搬，避免跨环境路径混用。
      delete payload.output_path;
      payload.in_place = false;
    }
  }
  if (
    input.in_place === true &&
    asText(payload.output_path).trim() === "" &&
    declaredOutputEnv !== "android"
  ) {
    // 原地编辑必须显式指向同一个 Linux 文件，否则会静默写成暂存区副本。
    const firstPath = spec.inputPaths && spec.inputPaths.length > 0
      ? asText(payload[spec.inputPaths[0]]).trim()
      : "";
    if (!firstPath) {
      throw new Error("E_INPUT_SCHEMA: in_place=true 需要 Linux 输入路径");
    }
    payload.output_path = firstPath;
  }
  allowRoots.push(`${taskDir}/out`);
  if (allowRoots.length > 0) {
    payload.allow_roots = allowRoots;
  }

  const argsPath = `${taskDir}/args.json`;
  await Tools.Files.write(argsPath, JSON.stringify(payload), false, "linux");

  const timeoutMs = Number(input.timeoutMs || spec.timeoutMs || 300000);
  const commandLine = [
    `PYTHONPATH=${shellQuote(paths.runtimeDir)}`,
    shellQuote(paths.python),
    "-m",
    "kiyori_office",
    shellQuote(command),
    "--args-file",
    shellQuote(argsPath)
  ].join(" ");
  const executed = await runHidden(commandLine, timeoutMs);

  let envelope: OfficeEnvelope;
  try {
    envelope = parseEnvelope(executed.output);
  } catch (error) {
    const tail = executed.output.slice(-2000);
    return toFailure(
      command,
      new Error(
        `E_PROTOCOL: office 运行时信封缺失（exitCode=${executed.exitCode}）。输出尾部：${tail}`
      )
    );
  }
  if (!envelope.ok) {
    const error = envelope.error || { code: "E_ENGINE_FAILED", message: "unknown office failure" };
    return toFailure(command, new Error(`${error.code}: ${error.message}`));
  }

  const data = (envelope.data || {}) as Record<string, unknown>;
  let artifacts = envelope.artifacts || [];
  if (hasArtifacts(envelope)) {
    try {
      artifacts = await deliverArtifacts(artifacts, input, spec, taskId, data);
    } catch (error) {
      return toFailure(command, error);
    }
  }
  return {
    success: true,
    message: `${command} 执行完成`,
    command,
    engine: envelope.engine,
    engine_version: envelope.engine_version,
    artifacts,
    data: { ...data, work_dir: taskDir, task_id: taskId },
    warnings: envelope.warnings || [],
    truncated: envelope.truncated === true,
    full_output_path: envelope.full_output_path == null ? null : envelope.full_output_path,
    next_actions: envelope.next_actions || [],
    metrics: envelope.metrics || {}
  };
}

export async function safeRunOfficeTool(
  spec: ToolSpec,
  params: Record<string, unknown> | undefined
): Promise<unknown> {
  try {
    return await runOfficeTool(spec, params);
  } catch (error) {
    return toFailure(spec.command || spec.defaultOutputName, error);
  }
}

export const OFFICE_SENTINEL = { begin: OFFICE_BEGIN, end: OFFICE_END };
