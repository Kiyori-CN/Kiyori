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
  envelopeFailure,
  toFailure
} from "./protocol";

export const PACKAGE_ID = "com.kiyori.office_suite";
export const EXECUTOR_KEY = "kiyori_office";
const RUNTIME_RESOURCE_KEY = "kiyori_office_runtime";
const RUNTIME_ZIP_NAME = "kiyori_office_runtime.zip";

/**
 * 宿主会向 ToolPkg 函数注入内部参数（见 JsToolManager.buildRuntimeParams）。
 * 这些字段属于宿主控制面，既不是业务参数，也不能转发给 Python 侧 schema。
 */
const HOST_INJECTED_PARAMS = new Set([
  "__operit_package_name",
  "__operit_package_state",
  "__operit_package_caller_name",
  "__operit_package_chat_id",
  "__operit_package_caller_card_id",
  "__operit_toolpkg_runtime_kind",
  "__operit_toolpkg_subpackage_id",
  "__operit_ui_package_name",
  "__operit_script_screen",
  "__operit_execution_context_key",
  "containerPackageName",
  "toolPkgId"
]);

/**
 * 仅允许转发 schema 已登记的字段，避免宿主注入项污染 Python 校验。
 *
 * 未登记的「投递控制」参数（output_env/overwrite/in_place/task_id/timeoutMs）在
 * 只读命令上会被静默忽略；其余未登记字段仍然保留，交给 Python schema 报出拼写错误。
 */
const DELIVERY_CONTROL_PARAMS = new Set([
  "env",
  "output_env",
  "overwrite",
  "in_place",
  "task_id",
  "timeoutMs",
  "allow_roots"
]);

function collectBusinessParams(
  spec: ToolSpec,
  input: Record<string, unknown>
): Record<string, unknown> {
  const allowed = new Set<string>();
  for (const param of spec.params || []) {
    allowed.add(param);
  }
  if (spec.inputPaths) {
    for (const field of spec.inputPaths) {
      allowed.add(field);
    }
  }
  const result: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(input)) {
    if (HOST_INJECTED_PARAMS.has(key) || key.startsWith("__")) {
      continue;
    }
    if (!allowed.has(key) && DELIVERY_CONTROL_PARAMS.has(key)) {
      continue;
    }
    result[key] = value;
  }
  return result;
}

export type FileEnv = "android" | "linux";

export type ToolSpec = {
  command: string;
  /** 该命令在 Python schema 中已登记的参数名，用于过滤宿主注入字段。 */
  params?: string[];
  inputPaths?: string[];
  outputKind?: "single" | "multi";
  defaultOutputName: string;
  /** 是否要求显式 env；纯环境/清理类命令不接触文件路径。 */
  requiresEnv?: boolean;
  timeoutMs?: number;
};

function declaresParam(spec: ToolSpec, name: string): boolean {
  return Array.isArray(spec.params) && spec.params.indexOf(name) >= 0;
}

/**
 * 命令是否涉及文件路径。只有这类命令才需要 task_id / allow_roots：
 * 前者定位 Linux 暂存区，后者把该暂存区与显式 Linux 输出目录纳入路径白名单。
 */
function usesFilePaths(spec: ToolSpec): boolean {
  return (
    (Array.isArray(spec.inputPaths) && spec.inputPaths.length > 0) ||
    declaresParam(spec, "output_path") ||
    declaresParam(spec, "dir")
  );
}

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
  "import hashlib",
  "",
  "zip_path, target = sys.argv[1], sys.argv[2]",
  "marker = os.path.join(target, '.zip-sha256')",
  "digest = hashlib.sha256()",
  "with open(zip_path, 'rb') as source:",
  "    for chunk in iter(lambda: source.read(1048576), b''):",
  "        digest.update(chunk)",
  "size = digest.hexdigest()",
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
  const raw = asText(params[field]).trim();
  // 大小写归一：宿主系统层不做 enum 校验，若这里区分大小写就会出现
  // 「系统层放行、业务层报错」的两层职责错位。
  const value = raw.toLowerCase();
  if (value !== "android" && value !== "linux") {
    throw new Error(
      `E_INPUT_SCHEMA: ${field} 必须显式传入 android 或 linux，实际 ${raw || "(空)"}`
    );
  }
  return value as FileEnv;
}

export function newTaskId(): string {
  return `t${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
}

export function baseName(path: string): string {
  const normalized = asText(path).replace(/\\/g, "/");
  const index = normalized.lastIndexOf("/");
  return index >= 0 ? normalized.slice(index + 1) : normalized;
}

/**
 * 规范化 Android 路径。
 *
 * 真机反馈：同一文件用 `/sdcard/...` 复制失败、用 `/storage/emulated/0/...`
 * 才能成功，说明 `/sdcard` 这类兼容别名在应用进程里不一定可见。这里统一映射到
 * 主存储真实路径，并拒绝相对路径（相对路径在 Android 侧没有可靠基准目录）。
 */
export function androidPathCandidates(raw: unknown): string[] {
  const value = asText(raw).trim().replace(/\\/g, "/");
  if (!value) {
    throw new Error("E_INPUT_SCHEMA: 路径不能为空");
  }
  if (!value.startsWith("/")) {
    throw new Error(
      `E_INPUT_SCHEMA: Android 路径必须是绝对路径，实际收到 ${value}；` +
        "请用 read_file/list_files 确认完整路径"
    );
  }
  if (value.includes("\0") || value.split("/").some(part => part === ".." || part === ".")) {
    throw new Error("E_PATH_INVALID: Android 路径不能包含 NUL、. 或 .. 路径段");
  }
  // Android 上同一文件可能有多个可见路径；按「原样 → 真实路径 → 兼容别名」
  // 顺序探测，命中哪个就报告哪个，不做静默替换。
  const candidates = [value];
  const mappings: Array<[RegExp, string]> = [
    [/^\/sdcard(?=\/|$)/, "/storage/emulated/0"],
    [/^\/storage\/self\/primary(?=\/|$)/, "/storage/emulated/0"],
    [/^\/mnt\/sdcard(?=\/|$)/, "/storage/emulated/0"]
  ];
  for (const [pattern, replacement] of mappings) {
    if (pattern.test(value)) {
      const mapped = value.replace(pattern, replacement);
      if (candidates.indexOf(mapped) < 0) {
        candidates.push(mapped);
      }
    }
  }
  const emulated = value.match(/^\/storage\/emulated\/0(?=\/|$)/);
  if (emulated) {
    const alias = value.replace(/^\/storage\/emulated\/0/, "/sdcard");
    if (candidates.indexOf(alias) < 0) {
      candidates.push(alias);
    }
  }
  return candidates;
}

export function normalizeAndroidPath(raw: unknown): string {
  return androidPathCandidates(raw)[0];
}

/**
 * 解析 Android 源文件路径：返回实际存在的候选，并报告尝试过的所有候选。
 * 全部不存在时抛出 E_PATH_INVALID，附带每个候选的探测结果，便于定位。
 */
async function resolveAndroidSource(raw: unknown): Promise<{ path: string; tried: string[] }> {
  const candidates = androidPathCandidates(raw);
  for (const candidate of candidates) {
    const exists = await Tools.Files.exists(candidate, "android");
    if (exists && exists.exists === true) {
      return { path: candidate, tried: candidates };
    }
  }
  throw new Error(
    `E_PATH_INVALID: Android 源文件不存在，已探测 ${candidates.join(" / ")}；` +
      "请用 read_file/list_files 确认完整路径，或改用正确的存储别名"
  );
}

async function runHidden(command: string, timeoutMs: number): Promise<{ output: string; exitCode: number }> {
  // office 运行时只存在于本地 Ubuntu/PRoot；显式 localOnly，避免文档被送到
  // 未安装运行时的远端 SSH 环境后产生难以诊断的失败。
  const result = await Tools.System.terminal.hiddenExec(command, {
    executorKey: EXECUTOR_KEY,
    timeoutMs,
    localOnly: true
  });
  if (result.timedOut === true) {
    throw new Error("E_TIMEOUT: office 命令超时，已请求取消；重试前检查产物状态");
  }
  return { output: asText(result.output), exitCode: Number(result.exitCode == null ? 1 : result.exitCode) };
}

type FileOperationLike = {
  successful?: boolean;
  success?: boolean;
  error?: string;
  details?: string;
  message?: string;
};

/**
 * Tools.Files.* 返回结构化结果而不是抛异常；忽略它会让后续步骤拿到「文件不存在」
 * 这类无法定位的错误（历史故障：Failed to read source file）。
 */
function assertFileOperation(
  result: unknown,
  operation: string,
  target: string
): void {
  const value = (result || {}) as FileOperationLike;
  const ok = value.successful === true || value.success === true;
  if (!ok) {
    const reason =
      asText(value.error).trim() ||
      asText(value.details).trim() ||
      asText(value.message).trim() ||
      "no diagnostic returned";
    throw new Error(
      `E_PATH_INVALID: ${operation} 失败 target=${target} reason=${reason}`
    );
  }
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
  await verifyLocalFileProvider(paths);
  if (runtimeReady) {
    return paths;
  }
  const resourcePath = await ToolPkg.readResource(RUNTIME_RESOURCE_KEY, RUNTIME_ZIP_NAME, true);
  if (!asText(resourcePath).trim()) {
    throw new Error("E_ENV_MISSING: 无法读取 kiyori_office 运行时资源");
  }
  assertFileOperation(await Tools.Files.mkdir(`${paths.home}/kiyori_office`, true, "linux"), "创建运行时目录", paths.runtimeDir);
  assertFileOperation(
    await Tools.Files.copy(resourcePath, paths.zipPath, false, "android", "linux"),
    "复制 office 运行时资源",
    paths.zipPath
  );
  assertFileOperation(
    await Tools.Files.write(paths.bootstrapPath, BOOTSTRAP_SCRIPT, false, "linux"),
    "写入运行时引导脚本",
    paths.bootstrapPath
  );
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

async function verifyLocalFileProvider(paths: CachedPaths): Promise<void> {
  const token = newTaskId();
  const marker = `${paths.home}/kiyori_office/.local-provider-${token}`;
  const created = await runHidden(`mkdir -p ${shellQuote(paths.home + "/kiyori_office")} && printf %s ${shellQuote(token)} > ${shellQuote(marker)}`, 20000);
  if (created.exitCode !== 0) throw new Error("E_ENV_MISSING: 本地工作目录不可写");
  try {
    const read = await Tools.Files.read({ path: marker, environment: "linux" });
    if (read.content !== token) throw new Error("文件环境不一致");
  } catch (error) {
    throw new Error("E_PATH_INVALID: Linux 文件工具未指向本地 Ubuntu；请断开 SSH 文件连接后重试，未搬运用户文档");
  } finally {
    await runHidden(`rm -f -- ${shellQuote(marker)}`, 20000);
  }
}

async function stageInput(
  paths: CachedPaths,
  taskDir: string,
  value: string,
  env: FileEnv,
  slot: string
): Promise<{ staged: string; source: string; aliases: string[] }> {
  if (env === "linux") {
    const source = asText(value).trim();
    const exists = await Tools.Files.exists(source, "linux");
    if (!exists || exists.exists !== true) {
      throw new Error(
        `E_PATH_INVALID: Linux 源文件不存在 path=${source}；请确认该文件位于 Linux 工作区`
      );
    }
    return { staged: source, source, aliases: [source] };
  }
  const resolved = await resolveAndroidSource(value);
  // 同一命令可能有多个输入（如 office_diff 的 left/right）且基名相同，
  // 用「参数名 + 基名」区分暂存路径，避免后者静默覆盖前者。
  const staged = `${taskDir}/in/${slot}-${baseName(resolved.path)}`;
  assertFileOperation(await Tools.Files.mkdir(`${taskDir}/in`, true, "linux"), "创建输入暂存目录", taskDir);
  assertFileOperation(
    await Tools.Files.copy(resolved.path, staged, false, "android", "linux"),
    "复制输入文件到 Linux 暂存区",
    staged
  );
  return { staged, source: resolved.path, aliases: resolved.tried };
}

async function androidTarget(
  params: Record<string, unknown>,
  spec: ToolSpec,
  taskId: string
): Promise<string> {
  const explicit = asText(params.output_path).trim();
  if (explicit) {
    return normalizeAndroidPath(explicit);
  }
  const root = getArtifactPaths().android;
  const name =
    spec.outputKind === "multi"
      ? `${stripExtension(spec.defaultOutputName)}-${taskId}`
      : spec.defaultOutputName;
  return normalizeAndroidPath(`${root}/office/${taskId}/${name}`);
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
  if (outputEnv === "linux" && (asText(params.output_path).trim() || params.in_place === true)) {
    return artifacts;
  }
  if (artifacts.length === 0) {
    return artifacts;
  }
  let target = outputEnv === "android"
    ? await androidTarget(params, spec, taskId)
    : `${getArtifactPaths().linux}/office/${taskId}/${spec.outputKind === "multi" ? stripExtension(spec.defaultOutputName) : spec.defaultOutputName}`;
  // 默认交付名必须来自实际产物（例如 convert 到 PDF），不能用固定 output.docx。
  if (!asText(params.output_path).trim() && spec.outputKind !== "multi") {
    target = target.replace(/\/[^/]*$/, `/${baseName(artifacts[0].path)}`);
  }
  const overwrite = params.overwrite === true;
  const multi = spec.outputKind === "multi";
  const targetDir = multi ? target : target.replace(/\/[^/]*$/, "");
  const baseDir = multi && typeof data.target_dir === "string" ? asText(data.target_dir) : "";

  if (multi) {
    const exists = await Tools.Files.exists(target, outputEnv);
    if (exists && exists.exists && !overwrite) {
      throw new Error(`E_PATH_EXISTS: 目标目录已存在且未设置 overwrite=true: ${target}`);
    }
  }
  assertFileOperation(await Tools.Files.mkdir(targetDir, true, outputEnv), "创建交付目录", targetDir);

  const delivered: OfficeArtifact[] = [];
  for (const item of artifacts) {
    const destination = multi
      ? `${target}/${baseDir ? relativeTo(baseDir, item.path) : baseName(item.path)}`
      : target;
    if (!multi) {
      const exists = await Tools.Files.exists(destination, outputEnv);
      if (exists && exists.exists && !overwrite) {
        throw new Error(
          `E_PATH_EXISTS: 目标文件已存在且未设置 overwrite=true: ${destination}`
        );
      }
    }
    const parent = destination.replace(/\/[^/]*$/, "");
    if (parent) {
      assertFileOperation(await Tools.Files.mkdir(parent, true, outputEnv), "创建产物目录", parent);
    }
    const pending = `${destination}.office-${newTaskId()}.tmp`;
    try {
      assertFileOperation(
        await Tools.Files.copy(item.path, pending, false, "linux", outputEnv),
        "复制办公产物到交付目录",
        pending
      );
      await verifyDeliveredSize(item, pending, outputEnv);
      // 验证副本后再调用宿主 move；宿主存储后端的最终发布仍需设备验证。
      assertFileOperation(await Tools.Files.move(pending, destination, outputEnv), "发布办公产物", destination);
    } catch (error) {
      try {
        const exists = await Tools.Files.exists(pending, outputEnv);
        if (exists.exists) assertFileOperation(await Tools.Files.deleteFile(pending, false, outputEnv), "清理本次临时产物", pending);
      } catch (cleanupError) {
        throw new Error(`${asText(error instanceof Error ? error.message : error)}；临时副本清理失败：${pending}`);
      }
      throw error;
    }
    delivered.push({ ...item, path: destination, env: outputEnv });
  }
  return delivered;
}

function hasArtifacts(envelope: OfficeEnvelope): boolean {
  return Array.isArray(envelope.artifacts) && envelope.artifacts.length > 0;
}

/**
 * 回搬后校验落盘大小与 Python 侧声明的字节数一致。
 *
 * 历史故障：跨环境复制曾用文本模式读写，二进制产物被替换成 U+FFFD 后体积变大，
 * 但工具仍返回 success。这里用 file_info 做交付前自检，让损坏无法静默通过。
 */
async function verifyDeliveredSize(
  artifact: OfficeArtifact,
  destination: string,
  environment: FileEnv
): Promise<void> {
  if (!Number.isFinite(artifact.bytes) || artifact.bytes < 0) {
    throw new Error("E_PROTOCOL: 运行时产物缺少有效 bytes，不能跳过交付校验");
  }
  const info = await Tools.Files.info(destination, environment);
  if (!info || info.exists === false) {
    throw new Error(`E_PATH_INVALID: 交付产物未落盘 target=${destination}`);
  }
  const actual = Number(info.size);
  if (!Number.isFinite(actual)) {
    throw new Error(`E_PATH_INVALID: 无法核验交付产物大小 target=${destination}`);
  }
  if (actual !== artifact.bytes) {
    throw new Error(
      `E_PATH_INVALID: 交付产物大小不一致（疑似二进制损坏）target=${destination} ` +
        `expected=${artifact.bytes} actual=${actual}`
    );
  }
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
  const target = normalizeAndroidPath(input.dir);
  if (!target) {
    return toFailure("office_workspace_init", new Error("E_INPUT_SCHEMA: dir 不能为空"));
  }
  const created: string[] = [];
  for (const name of WORKSPACE_DIRECTORIES) {
    assertFileOperation(
      await Tools.Files.mkdir(`${target}/${name}`, true, "android"),
      "创建工作区目录",
      `${target}/${name}`
    );
    created.push(`${target}/${name}`);
  }
  const agentsPath = `${target}/AGENTS.md`;
  const exists = await Tools.Files.exists(agentsPath, "android");
  if (!exists || !exists.exists || input.overwrite === true) {
    assertFileOperation(
      await Tools.Files.write(agentsPath, WORKSPACE_AGENTS_MARKDOWN, false, "android"),
      "写入工作区规则",
      agentsPath
    );
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
    if (spec.defaultOutputName === "office_read_guide") {
      return await readOfficeGuide(input);
    }
    throw new Error(`E_PROTOCOL: ${spec.defaultOutputName} 缺少 Python 命令映射`);
  }
  const env: FileEnv = spec.requiresEnv === false ? "linux" : requireEnv(input, "env");
  const taskId = asText(input.task_id).trim() || newTaskId();
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(taskId) ||
      (command === "office_workspace_clean" && !asText(input.task_id).trim())) {
    throw new Error("E_INPUT_SCHEMA: 必须使用真实 task_id，以字母或数字开头且不超过 64 字符；清理前先扫描工作文件");
  }
  if (input.in_place === true && !declaresParam(spec, "in_place")) {
    throw new Error(`E_INPUT_SCHEMA: ${command} 不支持 in_place`);
  }
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
  if (["office_workspace_status", "office_workspace_clean", "office_env_check", "pptx_measure_text"].includes(command)) {
    return await runControlCommand(paths, spec, input);
  }
  const leaseToken = newTaskId();
  await changeTaskLease(paths, taskId, leaseToken, "acquire");
  let outcome: Record<string, unknown>;
  try {
    outcome = await runPreparedOfficeTool(spec, input, env, paths, taskId, leaseToken);
  } catch (error) {
    outcome = { ...toFailure(command, error) };
  }
  // timeout 可能只结束了等待，不能假定外部进程已停止并开放清理。
  if (outcome.code === "E_TIMEOUT") {
    outcome.data = { ...(outcome.data as Record<string, unknown> ?? {}), task_id: taskId,
      work_dir: `${paths.home}/kiyori_office/work/${taskId}`, lease_retained: true };
  } else {
    try { await changeTaskLease(paths, taskId, leaseToken, "release"); }
    catch (error) {
      const warning = { code: "TASK_LEASE_RELEASE_FAILED", message: "任务占用未释放；结果保留，清理前需核实执行状态。" };
      console.error("office task lease release failed", taskId, error instanceof Error ? error.name : "Error");
      outcome.warnings = [...(Array.isArray(outcome.warnings) ? outcome.warnings : []), warning];
    }
  }
  if (outcome.success === false) outcome.data = { ...(outcome.data as Record<string, unknown> ?? {}),
    task_id: taskId, work_dir: `${paths.home}/kiyori_office/work/${taskId}` };
  return outcome;
}

async function changeTaskLease(paths: CachedPaths, taskId: string, token: string, action: "acquire" | "release"): Promise<void> {
  const executed = await runHidden(`PYTHONPATH=${shellQuote(paths.runtimeDir)} ${shellQuote(paths.python)} -m kiyori_office.storage ${action} ${shellQuote(taskId)} ${shellQuote(token)}`, 20000);
  const result = parseEnvelope(executed.output);
  if (!result.ok || executed.exitCode !== 0) throw new Error(`${result.error?.code ?? "E_ENGINE_FAILED"}: ${result.error?.message ?? "任务占用操作失败"}`);
}

async function runControlCommand(paths: CachedPaths, spec: ToolSpec, input: Record<string, unknown>): Promise<Record<string, unknown>> {
  const args = collectBusinessParams(spec, input);
  if (spec.command === "office_workspace_clean" && !asText(args.task_id).trim()) {
    throw new Error("E_INPUT_SCHEMA: 清理必须指定现有 task_id，请先调用 office_workspace_status");
  }
  const controlDir = `${paths.home}/kiyori_office/control`;
  const argsPath = `${controlDir}/args-${newTaskId()}.json`;
  assertFileOperation(await Tools.Files.mkdir(controlDir, true, "linux"), "创建办公管理目录", controlDir);
  assertFileOperation(await Tools.Files.write(argsPath, JSON.stringify(args), false, "linux"), "写入管理参数", argsPath);
  let completed = false;
  let outcome: Record<string, unknown>;
  try {
    const executed = await runHidden(`PYTHONPATH=${shellQuote(paths.runtimeDir)} ${shellQuote(paths.python)} -m kiyori_office ${shellQuote(spec.command)} --args-file ${shellQuote(argsPath)}`, spec.timeoutMs ?? 120000);
    completed = true;
    const envelope = parseEnvelope(executed.output);
    if (envelope.ok && executed.exitCode !== 0) throw new Error(`E_ENGINE_FAILED: 管理命令退出码 ${executed.exitCode}`);
    const base = getArtifactPaths().android;
    outcome = !envelope.ok ? { ...envelopeFailure(envelope) } : { success: true, command: spec.command, message: `${spec.command} 执行完成`,
      data: { ...envelope.data, execution_env: "linux", runtime_root: paths.runtimeDir,
        work_root: `${paths.home}/kiyori_office/work`, control_root: controlDir,
        default_delivery_dir: normalizeAndroidPath(`${base}/office`) },
      artifacts: [], warnings: envelope.warnings ?? [], next_actions: envelope.next_actions ?? [] };
  } catch (error) {
    outcome = { ...toFailure(spec.command, error) };
  }
  if (completed) {
    try { assertFileOperation(await Tools.Files.deleteFile(argsPath, false, "linux"), "清理本次管理参数", argsPath); }
    catch (error) {
      console.error("office control args cleanup failed", error instanceof Error ? error.name : "Error");
      outcome.warnings = [...(Array.isArray(outcome.warnings) ? outcome.warnings : []),
        { code: "CONTROL_ARGS_CLEANUP_FAILED", message: `管理参数未移除：${argsPath}` }];
    }
  } else {
    outcome.data = { ...(outcome.data as Record<string, unknown> ?? {}), control_args_path: argsPath };
  }
  return outcome;
}

async function runPreparedOfficeTool(spec: ToolSpec, input: Record<string, unknown>, env: FileEnv,
  paths: CachedPaths, taskId: string, leaseToken: string): Promise<Record<string, unknown>> {
  const command = spec.command;
  const taskDir = `${paths.home}/kiyori_office/work/${taskId}`;
  assertFileOperation(await Tools.Files.mkdir(`${taskDir}/out`, true, "linux"), "创建任务输出目录", taskDir);

  const business = collectBusinessParams(spec, input);
  const payload: Record<string, unknown> = {};
  const allowRoots: string[] = [];
  for (const [key, value] of Object.entries(business)) {
    if (key === "env" || key === "output_env" || key === "timeoutMs" || key === "allow_roots") {
      continue;
    }
    payload[key] = value;
  }
  payload.task_id = taskId;

  for (const field of spec.inputPaths || []) {
    const raw = business[field];
    if (Array.isArray(raw)) {
      const stagedList: string[] = [];
      for (let index = 0; index < raw.length; index += 1) {
        const item = raw[index];
        const text = asText(item).trim();
        if (!text) {
          throw new Error(`E_INPUT_SCHEMA: ${field} 不能包含空路径`);
        }
        const staged = await stageInput(paths, taskDir, text, env, `${field}${index}`);
        stagedList.push(staged.staged);
        allowRoots.push(staged.staged.replace(/\/[^/]*$/, ""));
      }
      payload[field] = stagedList;
      continue;
    }
    const text = asText(raw).trim();
    if (!text) {
      continue;
    }
    const staged = await stageInput(paths, taskDir, text, env, field);
    payload[field] = staged.staged;
    allowRoots.push(staged.staged.replace(/\/[^/]*$/, ""));
  }

  // 结构化块中的图片同样走既有跨环境搬运；不能把 Android 路径透传给 Python。
  async function stageElements(value: unknown, slot: string): Promise<unknown> {
    if (Array.isArray(value)) {
      const staged: unknown[] = [];
      for (let index = 0; index < value.length; index++) staged.push(await stageElements(value[index], `${slot}-${index}`));
      return staged;
    }
    if (!value || typeof value !== "object") return value;
    const object = { ...(value as Record<string, unknown>) };
    if (object.type === "image" && typeof object.image_path === "string") {
      const staged = await stageInput(paths, taskDir, object.image_path, env, slot);
      object.image_path = staged.staged;
      allowRoots.push(staged.staged.replace(/\/[^/]*$/, ""));
    }
    for (const key of ["blocks", "elements"]) {
      if (key in object) object[key] = await stageElements(object[key], `${slot}-${key}`);
    }
    return object;
  }
  for (const key of ["spec", "slides", "elements", "blocks"]) {
    if (key in payload) payload[key] = await stageElements(payload[key], key);
  }

  if (command === "office_workspace_init") {
    // dir 指向「待创建」的目录，不能作为输入暂存（暂存要求源文件已存在），
    // 但必须进入 allow_roots，否则 Linux 工作区会被 Python 判为越出允许根目录。
    const dir = asText(business.dir).trim();
    if (!dir) {
      throw new Error("E_INPUT_SCHEMA: dir 不能为空");
    }
    payload.dir = dir;
    allowRoots.push(dir);
  }

  const declaredOutputEnv = asText(input.output_env).trim();
  if (declaredOutputEnv && declaredOutputEnv !== "android" && declaredOutputEnv !== "linux") {
    throw new Error(
      `E_INPUT_SCHEMA: output_env 必须显式传入 android 或 linux，实际 ${declaredOutputEnv}`
    );
  }
  if (declaresParam(spec, "output_path")) {
    // 原地编辑优先于默认交付：output_path 必须指向同一个 Linux 文件，
    // 否则会在暂存区写出一份副本，用户看到「执行成功」但源文件没变。
    if (input.in_place === true) {
      const firstPath = spec.inputPaths && spec.inputPaths.length > 0
        ? asText(payload[spec.inputPaths[0]]).trim()
        : "";
      if (!firstPath) {
        throw new Error("E_INPUT_SCHEMA: in_place=true 需要 Linux 输入路径");
      }
      payload.output_path = firstPath;
      payload.in_place = true;
      allowRoots.push(firstPath.replace(/\/[^/]*$/, ""));
    } else if (declaredOutputEnv === "linux" && asText(input.output_path).trim()) {
      // 由 Python 侧直接写入调用方指定的 Linux 路径，仍需通过 allow_roots 校验。
      // 多产物命令的 output_path 是目录本身，必须整条加入白名单（去掉末段会
      // 把目录的父级当成允许根，既过宽也可能漏掉该目录）。
      const declaredPath = asText(input.output_path).trim();
      allowRoots.push(
        spec.outputKind === "multi" ? declaredPath : declaredPath.replace(/\/[^/]*$/, "")
      );
    } else {
      // 默认交付到 Android：Python 先写暂存区，再由 JS 回搬，避免跨环境路径混用。
      delete payload.output_path;
      if (declaresParam(spec, "in_place")) {
        payload.in_place = false;
      }
    }
  }
  // allow_roots 是 Python 侧路径校验的支撑字段；只对文件类命令注入，
  // 否则会被 additionalProperties:false 拒绝。
  if (usesFilePaths(spec)) {
    allowRoots.push(`${taskDir}/out`);
    // allow_roots 是协议层字段，不出现在工具 METADATA 中，但 Python schema 接受它。
    payload.allow_roots = allowRoots;
  }

  const argsPath = `${taskDir}/args-${newTaskId()}.json`;
  assertFileOperation(
    await Tools.Files.write(argsPath, JSON.stringify(payload), false, "linux"),
    "写入 office 参数文件",
    argsPath
  );

  const timeoutMs = Number(input.timeoutMs || spec.timeoutMs || 300000);
  const commandLine = [
    `PYTHONPATH=${shellQuote(paths.runtimeDir)}`,
    `KIYORI_OFFICE_LEASE_TOKEN=${shellQuote(leaseToken)}`,
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
    return envelopeFailure(envelope);
  }
  if (executed.exitCode !== 0) {
    return toFailure(command, new Error(`E_ENGINE_FAILED: 运行时退出码 ${executed.exitCode}，未发布产物`));
  }

  const data = (envelope.data || {}) as Record<string, unknown>;
  if (command === "office_env_setup" && input.confirm === true) {
    return await executeSetup(paths, taskDir, taskId, data);
  }
  let artifacts = envelope.artifacts || [];
  if (hasArtifacts(envelope)) {
    try {
      artifacts = await deliverArtifacts(artifacts, input.in_place === true ? { ...input, output_env: "linux" } : input, spec, taskId, data);
    } catch (error) {
      return toFailure(command, error);
    }
  }
  // 返回真实池链接而不只是磁盘路径。宿主解析器支持 JSON 中转义的 link；
  // 注册不等于视觉审阅完成，失败保留产物并明确指出未获得图像。
  if (command === "office_render_preview" || command === "pdf_to_images") {
    const visualPages: Array<{ path: string; env: string; image: string; page?: number }> = [];
    for (const item of artifacts.slice(0, 8)) {
      if (item.env !== "android" && item.env !== "linux") throw new Error("E_PROTOCOL: 图片产物缺少有效环境");
      let content: string;
      try {
        const read = await Tools.Files.read({ path: item.path, environment: item.env, direct_image: true });
        content = read.content ?? "";
      } catch (error) {
        console.error("office visual image registration failed", error instanceof Error ? error.name : "unknown");
        return { success: false, command, code: "E_ENGINE_FAILED", artifacts,
          message: "页面已渲染，但图像读取失败；未完成视觉检查。", data: { ...data, visual_pages: visualPages } };
      }
      if (!/<link\b[^>]*type=["']image["'][^>]*>/.test(content)) {
        return { success: false, command, code: "E_ENGINE_FAILED", artifacts,
          message: "页面已渲染，但图像注册失败；未完成视觉检查。", data: { ...data, visual_pages: visualPages } };
      }
      visualPages.push({ path: item.path, env: item.env, page: item.page, image: content });
    }
    data.visual_pages = visualPages;
    data.visual_review_status = "images_attached_review_required";
    data.remaining_visual_pages = Math.max(0, artifacts.length - visualPages.length);
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

async function readOfficeGuide(input: Record<string, unknown>): Promise<object> {
  const format = typeof input.format === "string" ? input.format : "core";
  if (!["core", "docx", "xlsx", "pptx", "pdf"].includes(format)) {
    throw new Error("E_INPUT_SCHEMA: format 必须是 core/docx/xlsx/pptx/pdf");
  }
  const path = await ToolPkg.readResource(`office_guide_${format}`, `kiyori-office-${format}.md`, true);
  const file = await Tools.Files.read({ path, environment: "android" });
  if (!file.content || file.content.length < 20) {
    throw new Error("E_PROTOCOL: 内置办公 Skill 内容不可读");
  }
  return { success: true, command: "office_read_guide", data: { format, content: file.content },
    next_actions: ["office_env_check"], artifacts: [] };
}

async function executeSetup(paths: CachedPaths, taskDir: string, taskId: string,
  plan: Record<string, unknown>): Promise<Record<string, unknown>> {
  if (!Array.isArray(plan.commands) || !plan.commands.every((item) => typeof item === "string")) {
    throw new Error("E_PROTOCOL: 安装计划缺少 commands");
  }
  const available = Number(plan.disk_free_bytes);
  if (plan.disk_free_bytes != null && available < Number(plan.estimated_bytes)) {
    throw new Error("E_ENV_MISSING: 可用空间小于安装计划预估，请释放空间后重试");
  }
  // 可见终端可能配置了 SSH。随机标记只写在已确认的本地运行时中，逐条执行前
  // 验证同一文件的内容，防止把安装命令投递到另一台机器或错误环境。
  const marker = `${taskDir}/local-install-marker`;
  const token = newTaskId();
  assertFileOperation(await Tools.Files.write(marker, token, false, "linux"), "写入本地安装身份", marker);
  const session = await Tools.System.terminal.create(`Office setup ${taskId}`);
  if (!session.sessionId) throw new Error("E_ENGINE_FAILED: 无法创建可见安装终端");
  for (const command of plan.commands as string[]) {
    const guarded = `test "$(cat ${shellQuote(marker)} 2>/dev/null)" = ${shellQuote(token)} && (${command})`;
    const result = await Tools.System.terminal.execStreaming(session.sessionId, guarded, { timeoutMs: 1200000 });
    if (result.timedOut || result.exitCode !== 0) {
      return { success: false, command: "office_env_setup", code: result.timedOut ? "E_TIMEOUT" : "E_ENGINE_FAILED",
        message: "安装未完成，请查看可见终端；不会自动重试或切换环境", data: { ...plan, executed: true, completed: false, session_id: session.sessionId },
        remedy: "确认可见终端是本地 Ubuntu，检查网络、磁盘和命令输出后重新获取计划" };
    }
  }
  // 重新生成计划读取实际组件状态，随后显式探测；退出码 0 不代表依赖可导入。
  assertFileOperation(await Tools.Files.write(`${taskDir}/check.json`, "{}", false, "linux"), "写入环境复检参数", taskDir);
  const probe = await runHidden(`PYTHONPATH=${shellQuote(paths.runtimeDir)} ${shellQuote(paths.python)} -m kiyori_office office_env_check --args-file ${shellQuote(taskDir + "/check.json")}`, 120000);
  const checked = parseEnvelope(probe.output);
  if (probe.exitCode !== 0) return toFailure("office_env_setup", new Error("E_ENGINE_FAILED: 安装复检进程异常退出，未确认安装完成"));
  if (!checked.ok) return envelopeFailure(checked);
  const tiers = checked.data?.tiers as Record<string, { complete?: boolean; components?: Record<string, { available?: boolean }> }> | undefined;
  const tier = tiers?.[`tier${plan.tier}`];
  const completed = Array.isArray(plan.components) && plan.components.every((name) =>
    typeof name === "string" && tier?.components?.[name]?.available === true);
  return { success: completed, command: "office_env_setup", message: completed ? "安装完成并已复检" : "安装命令结束，但组件复检未通过",
    ...(completed ? {} : { code: "E_ENV_MISSING" }),
    data: { ...plan, executed: true, completed, session_id: session.sessionId, environment: checked.data }, next_actions: ["office_env_check"] };
}
