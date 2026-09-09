type Location = "android" | "linux";
type Page = "files" | "storage" | "help";
type Action = "read" | "validate" | "preview" | "convert";
type Task = { task_id: string; path: string; bytes?: number; temporary_bytes?: number; output_bytes?: number; active?: boolean; cleanable: boolean; error?: string };
type Plan = { task_id: string; path: string; scope: "temporary" | "task"; plan_token: string; file_count: number; bytes: number; sample_files: string[] };
type Data = { content?: string; work_dir?: string; task_id?: string; work_root?: string; runtime_root?: string; default_delivery_dir?: string;
  tasks?: Task[]; total_tasks?: number; offset?: number; next_offset?: number | null; tier?: number; commands?: string[];
  tiers?: Record<string, { complete: boolean }>; [key: string]: unknown };
type Result = { success: boolean; command?: string; message?: string; remedy?: string; data?: Data; warnings?: Array<{ code: string; message: string }>;
  artifacts?: Array<{ path: string; env: string }>; truncated?: boolean; full_output_path?: string | null };
function bytes(value = 0): string {
  return value < 1024 ? `${value} B` : value < 1048576 ? `${(value / 1024).toFixed(1)} KB` : value < 1073741824 ? `${(value / 1048576).toFixed(1)} MB` : `${(value / 1073741824).toFixed(2)} GB`;
}

/** 顶栏由宿主持有；页面只呈现任务，复用主题控件与宿主文件选择器。 */
export default function Screen(ctx: ComposeDslContext): ComposeNode {
  const [tab, setTab] = ctx.useState<Page>("tab", "files");
  const [path, setPath] = ctx.useState("path", "");
  const [env, setEnv] = ctx.useState<Location>("env", "android");
  const [action, setAction] = ctx.useState<Action>("action", "read");
  const [pages, setPages] = ctx.useState("pages", "");
  const [outputEnv, setOutputEnv] = ctx.useState<Location>("outputEnv", "android");
  const [outputPath, setOutputPath] = ctx.useState("outputPath", "");
  const [showOutput, setShowOutput] = ctx.useState("showOutput", false);
  const [busy, setBusy] = ctx.useState("busy", false);
  const [result, setResult] = ctx.useState<Result | null>("result", null);
  const [title, setTitle] = ctx.useState("resultTitle", "");
  const [details, setDetails] = ctx.useState("details", false);
  const [storage, setStorage] = ctx.useState<Data | null>("storage", null);
  const [plan, setPlan] = ctx.useState<Plan | null>("cleanupPlan", null);
  const [planTier, setPlanTier] = ctx.useState<number | null>("planTier", null);
  const [environment, setEnvironment] = ctx.useState<Data | null>("environment", null);
  const [picked, setPicked] = ctx.useState<Array<{ path: string; name: string }>>("pickedCopies", []);
  const [confirmCopies, setConfirmCopies] = ctx.useState("confirmCopies", false);
  const lock = ctx.useRef("officeActionLock", false);
  const english = (getLang() ?? "").toLowerCase().startsWith("en");
  const t = (zh: string, en: string) => english ? en : zh;
  const text = (value: string, small = false) => ctx.UI.Text({ text: value, style: small ? "bodySmall" : "bodyMedium", color: small ? "onSurfaceVariant" : "onSurface" });
  const hint = (value: string) => text(value, true);
  const row = (children: ComposeNode[]) => ctx.UI.Row({ spacing: 8, fillMaxWidth: true, verticalAlignment: "center" }, children);
  const secondary = (label: string, click: () => void | Promise<void>, enabled = true) => ctx.UI.OutlinedButton({ onClick: click, enabled: !busy && enabled, shape: { cornerRadius: 10 } }, [text(label)]);
  const primary = (label: string, click: () => void | Promise<void>, enabled = true) => ctx.UI.Button({ text: label, onClick: click, enabled: !busy && enabled, fillMaxWidth: true, shape: { cornerRadius: 10 } });
  const chip = (label: string, selected: boolean, click: () => void) => ctx.UI.FilterChip({ label: [ctx.UI.Text({ text: label, style: "labelLarge" })], selected, onClick: click, enabled: !busy, weight: 1 });
  const card = (label: string, children: ComposeNode[]) => ctx.UI.Card({ fillMaxWidth: true, containerColor: "surface", elevation: 0,
    border: { width: 1, color: "outlineVariant" }, shape: { cornerRadius: 12 } }, [ctx.UI.Column({ padding: 14, spacing: 10 }, [ctx.UI.Text({ text: label, style: "titleMedium" }), ...children])]);
  function failure(error: unknown): Result {
    console.error("office console action failed", error instanceof Error ? error.name : "Error");
    const e = error && typeof error === "object" ? error as { message?: string; data?: Data | string } : {};
    let data: Data | undefined;
    if (typeof e.data === "string") { try { data = JSON.parse(e.data); } catch { data = { content: e.data }; } }
    else data = e.data;
    return { success: false, message: e.message ?? t("操作失败，请查看工具记录", "Action failed; inspect tool records"), data };
  }
  async function run(tool: string, params: Record<string, unknown>, label: string) {
    if (lock.current) return;
    lock.current = true; setBusy(true); setResult(null); setDetails(false); setTitle(label); setPlan(null); setPlanTier(null); setConfirmCopies(false);
    try {
      if (ctx.importPackage && ctx.isPackageImported && !(await ctx.isPackageImported("office"))) await ctx.importPackage("office");
      if (ctx.usePackage) await ctx.usePackage("office");
      const name = ctx.resolveToolName ? await ctx.resolveToolName({ packageName: "office", toolName: tool, preferImported: true }) : `office:${tool}`;
      const raw = await ctx.callTool<Result | string>(name, params);
      const value: Result = typeof raw === "string" ? JSON.parse(raw) : raw;
      if (!value || typeof value.success !== "boolean") throw new Error(t("工具未返回有效结果", "Invalid tool result"));
      setResult(value);
      if (value.success) {
        if (tool === "office_workspace_status") setStorage(value.data ?? null);
        if (tool === "office_env_check") setEnvironment(value.data ?? null);
        if (tool === "office_env_setup" && params.confirm !== true && Array.isArray(value.data?.commands)) { setPlanTier(value.data?.tier ?? null); setDetails(true); }
        if (tool === "office_workspace_clean") {
          if (params.confirm !== true) setPlan(value.data as Plan);
          else setStorage(null); // 完成后重新扫描，不能把旧占用量显示为当前状态。
        }
      }
    } catch (error) { setResult(failure(error)); }
    finally { lock.current = false; setBusy(false); }
  }
  function changeSource(next: Location) {
    if (next !== env) { setEnv(next); setPath(""); setPages(""); setResult(null); }
  }
  async function chooseFile() {
    if (lock.current) return;
    lock.current = true; setBusy(true);
    try {
      const selection = await ctx.openFilePicker({ picker: "document", allowMultiple: false, persistPermission: false });
      if (selection.cancelled) return;
      const file = selection.files[0];
      if (!file?.path) throw new Error(t("选择器未返回可读副本，请输入文件路径", "Picker returned no readable copy; enter a path"));
      setPicked([...picked, { path: file.path, name: file.name ?? file.path.split("/").pop() ?? "file" }]);
      setEnv("android"); setPath(file.path); setResult(null); setPages("");
    } catch (error) { setResult(failure(error)); }
    finally { lock.current = false; setBusy(false); }
  }
  async function clearCopies() {
    if (lock.current || !confirmCopies) return;
    lock.current = true; setBusy(true);
    const remaining = [...picked];
    try {
      // 只使用选择器实际返回并记录的副本清单，绝不删除可编辑 path 字段的内容。
      while (remaining.length) {
        const file = remaining[0];
        const removed = await Tools.Files.deleteFile(file.path, false, "android");
        if (!removed.successful) throw new Error(removed.details ?? t("移除副本失败", "Could not remove copy"));
        remaining.shift(); if (path === file.path) setPath("");
      }
      setResult({ success: true, message: t("导入副本已移除，原始文件保留", "Imported copies removed; originals preserved") });
    } catch (error) { setResult(failure(error)); }
    finally { setPicked(remaining); setConfirmCopies(false); lock.current = false; setBusy(false); }
  }
  async function open(item: { path: string; env: string }) {
    if (lock.current) return;
    lock.current = true; setBusy(true);
    try { const opened = await Tools.Files.open(item.path, "android"); if (!opened.successful) throw new Error(opened.details); }
    catch (error) { setResult({ ...result, ...failure(error), artifacts: result?.artifacts }); }
    finally { lock.current = false; setBusy(false); }
  }
  const actionLabel = { read: t("读取结构", "Read outline"), validate: t("检查文档", "Validate document"), preview: t("生成预览", "Render preview"), convert: t("转换为 PDF", "Convert to PDF") }[action];
  const produces = action === "preview" || action === "convert";
  const validPath = !!path.trim() && (path.trim().startsWith("/") || env === "linux" && path.trim().startsWith("~/"));
  const validPages = !pages.trim() || /^[1-9]\d*(?:-[1-9]\d*)?(?:\s*,\s*[1-9]\d*(?:-[1-9]\d*)?)*$/.test(pages.trim());
  const validOutput = !outputPath.trim() || outputPath.trim().startsWith("/") || outputEnv === "linux" && outputPath.trim().startsWith("~/");
  const isPdf = /\.pdf$/i.test(path.trim());
  function executeFile() {
    const output = { output_env: outputEnv, ...(outputPath.trim() ? { output_path: outputPath.trim() } : {}) };
    if (action === "read") return run("office_read", { path: path.trim(), env, mode: "outline", max_chars: 12000 }, actionLabel);
    if (action === "validate") return run("office_validate", { path: path.trim(), env, strict: true }, actionLabel);
    if (action === "preview") return run("office_render_preview", { path: path.trim(), env, max_pages: 8, ...output, ...(pages.trim() ? { pages: pages.trim() } : {}) }, actionLabel);
    return run("office_convert", { from_path: path.trim(), env, engine: "libreoffice", to_format: "pdf", ...output }, actionLabel);
  }
  const navigate = (next: Page) => { if (next === tab) return; setTab(next); setResult(null); setPlan(null); setPlanTier(null); setConfirmCopies(false); };
  const changeAction = (next: Action) => { if (next === action) return; setAction(next); setOutputPath(""); setResult(null); };
  const nodes: ComposeNode[] = [row([chip(t("文件操作", "Files"), tab === "files", () => navigate("files")), chip(t("存储管理", "Storage"), tab === "storage", () => navigate("storage")), chip(t("环境与帮助", "Help"), tab === "help", () => navigate("help"))])];
  if (tab === "files") {
    nodes.push(card(t("文件来源", "Input file"), [
      row([chip(t("手机文件", "Phone"), env === "android", () => changeSource("android")), chip(t("Ubuntu 文件", "Ubuntu"), env === "linux", () => changeSource("linux"))]),
      ...(env === "android" ? [secondary(t("选择文件", "Choose file"), chooseFile)] : []),
      ctx.UI.TextField({ value: path, onValueChange: value => { setPath(value); setResult(null); }, label: t("完整路径", "Full path"), singleLine: true, enabled: !busy, fillMaxWidth: true,
        placeholder: env === "android" ? "/storage/emulated/0/Download/report.docx" : "/root/report.docx", isError: !!path.trim() && !validPath }),
      hint(picked.some(p => p.path === path) ? t("已选导入副本，原始文件保留。可在存储管理中移除此副本。", "Using an imported copy; original preserved. Remove this copy in Storage.") : env === "android" ? t("从手机读取文件，再复制到 Ubuntu 处理。", "Read from phone storage, then copy into Ubuntu.") : t("填写 Ubuntu 内的路径，不是手机端 rootfs 挂载路径。", "Enter a path inside Ubuntu, not its phone-side rootfs mount."))
    ]));
    nodes.push(card(t("处理方式", "Operation"), [
      row([chip(t("读结构", "Outline"), action === "read", () => changeAction("read")), chip(t("校验", "Validate"), action === "validate", () => changeAction("validate"))]),
      row([chip(t("预览", "Preview"), action === "preview", () => changeAction("preview")), chip(t("转 PDF", "To PDF"), action === "convert", () => changeAction("convert"))]),
      hint({ read: t("读取标题、段落、表格或工作表，不修改原文件。", "Read document structure without editing the original."), validate: t("检查结构与公式缓存，列出具体问题；不代替看图验收。", "Check structure and formula caches; visual review remains separate."), preview: t("文档在 Ubuntu 转为图片，一次最多 8 页。", "Render images in Ubuntu, up to 8 pages per request."), convert: t("使用 Ubuntu LibreOffice 转 PDF，另存为新文件。", "Convert with Ubuntu LibreOffice and save a separate PDF.") }[action]),
      ...(action === "preview" ? [ctx.UI.TextField({ value: pages, onValueChange: setPages, label: t("预览页码", "Pages"), placeholder: "1-3,8", singleLine: true, enabled: !busy, fillMaxWidth: true, isError: !validPages,
        supportingText: [hint(t("留空取前 8 页；更多页请分批查看", "Blank: first 8 pages. Review longer files in batches"))] })] : []),
      ...(action === "convert" && isPdf ? [hint(t("已是 PDF，可直接校验或预览。", "Already PDF; validate or preview it."))] : []),
      ...(produces ? [secondary(t(showOutput ? "收起输出设置" : "输出设置", showOutput ? "Hide output settings" : "Output settings"), () => setShowOutput(!showOutput)),
        hint(outputEnv === "android" ? t("结果保存到手机，实际路径见结果。", "Save to phone; exact paths appear in the result.") : t("结果保留在 Ubuntu，手机打开前需交付副本。", "Keep in Ubuntu; deliver a copy to open on the phone.")),
        ...(showOutput ? [row([chip(t("保存到手机", "Phone output"), outputEnv === "android", () => { setOutputEnv("android"); setOutputPath(""); }), chip(t("保留在 Ubuntu", "Ubuntu output"), outputEnv === "linux", () => { setOutputEnv("linux"); setOutputPath(""); })]),
          ctx.UI.TextField({ value: outputPath, onValueChange: setOutputPath, label: action === "preview" ? t("输出目录（可选）", "Output folder (optional)") : t("PDF 路径（可选）", "PDF path (optional)"), singleLine: true, enabled: !busy, fillMaxWidth: true, isError: !validOutput }),
          hint(t("留空使用默认位置；同名输出会停止并提示，不自动覆盖。", "Blank uses the default location. Existing output is never overwritten automatically."))] : [])] : []),
      primary(actionLabel, executeFile, validPath && (action !== "preview" || validPages) && (action !== "convert" || !isPdf) && (!produces || validOutput)),
      hint(t("引擎始终在本机 Ubuntu 运行。文件来源不会切换引擎或迁移 Ubuntu。", "Engines always run in local Ubuntu. Input selection does not relocate Ubuntu."))
    ]));
  }
  if (tab === "storage") {
    nodes.push(card(t("Ubuntu 工作文件", "Ubuntu work files"), [hint(t("先看占用，再选择范围。临时清理保留输出；删除任务包含其输出。", "Inspect usage first. Temporary cleanup preserves outputs; deleting a task includes outputs.")),
      secondary(t("扫描工作文件", "Scan work files"), () => run("office_workspace_status", { offset: 0, limit: 20 }, t("存储扫描", "Storage scan"))),
      ...(storage ? [text(`${t("工作目录", "Work root")}: ${storage.work_root}`), hint(`${t("运行时（保留）", "Runtime (preserved)")}: ${storage.runtime_root}`), hint(`${t("默认手机交付", "Default phone delivery")}: ${storage.default_delivery_dir}`), hint(t(`共 ${storage.total_tasks ?? 0} 个任务，本页 ${(storage.tasks ?? []).length} 个`, `${storage.total_tasks ?? 0} tasks; ${(storage.tasks ?? []).length} on this page`))] : [])]));
    if (plan) nodes.push(card(t("确认清理范围", "Review cleanup"), [text(plan.path), text(plan.scope === "temporary" ? t("仅临时文件，保留 out 输出", "Temporary files only; preserve out outputs") : t("删除整个任务，包含 out 输出", "Delete the entire task, including out outputs")), text(`${plan.file_count} ${t("个文件", "files")} · ${bytes(plan.bytes)}`), hint(plan.sample_files.join("\n") || t("没有待清理文件", "No files to remove")),
      hint(t("不删除运行时、Android 交付或自建测试工作区。", "Runtime, Android delivery and custom workspaces are excluded.")),
      primary(t("确认执行清理", "Confirm cleanup"), () => run("office_workspace_clean", { task_id: plan.task_id, scope: plan.scope, confirm: true, plan_token: plan.plan_token }, t("清理结果", "Cleanup result"))), secondary(t("取消", "Cancel"), () => setPlan(null))]));
    for (const task of storage?.tasks ?? []) nodes.push(card(task.task_id, [hint(task.path), text(`${bytes(task.bytes)} · ${t("临时", "Temp")} ${bytes(task.temporary_bytes)} · ${t("输出", "Outputs")} ${bytes(task.output_bytes)}`),
      ...(task.active ? [hint(t("正在使用或中断未释放，暂不可清理。", "In use or interrupted without release; cleanup blocked."))] : []), ...(task.error ? [hint(task.error)] : []),
      row([secondary(t("清理临时", "Clean temp"), () => run("office_workspace_clean", { task_id: task.task_id, scope: "temporary" }, t("清理预览", "Cleanup preview")), task.cleanable), secondary(t("删除任务", "Delete task"), () => run("office_workspace_clean", { task_id: task.task_id, scope: "task" }, t("删除预览", "Deletion preview")), task.cleanable)])]));
    if (storage && ((storage.offset ?? 0) > 0 || storage.next_offset != null)) nodes.push(row([secondary(t("上一页", "Previous"), () => run("office_workspace_status", { offset: Math.max(0, (storage.offset ?? 0) - 20), limit: 20 }, t("存储扫描", "Storage scan")), (storage.offset ?? 0) > 0), secondary(t("下一页", "Next"), () => run("office_workspace_status", { offset: storage.next_offset, limit: 20 }, t("存储扫描", "Storage scan")), storage.next_offset != null)]));
    nodes.push(card(t("手机上的文件", "Phone files"), [hint(t("已交付文档、自建测试目录及 ZIP 不会被 Ubuntu 清理删除，请在文件管理中按实际路径检查后删除。", "Delivered documents, custom test folders and ZIPs are excluded. Review their paths in File Manager before deleting.")), hint(t("选择器副本由宿主暂存；下方仅列出本页选择的副本，退出后的清理由宿主管理。", "Picker copies are staged by the host. Below are this page's imports; exit cleanup belongs to the host.")),
      ...picked.map(file => hint(`${file.name}\n${file.path}`)), ...(picked.length ? [secondary(t("移除本页导入副本", "Remove imported copies"), () => setConfirmCopies(true)), ...(confirmCopies ? [text(t("仅移除上述副本，保留原文件。", "Remove only the listed copies; preserve originals.")), primary(t("确认移除副本", "Confirm removal"), clearCopies), secondary(t("取消", "Cancel"), () => setConfirmCopies(false))] : [])] : [])]));
  }
  if (tab === "help") {
    nodes.push(card(t("环境状态", "Environment"), [secondary(t("检查办公环境", "Check environment"), () => run("office_env_check", {}, t("环境检查", "Environment check"))),
      ...(environment ? [1,2,3,4].map(tier => text(`T${tier} · ${environment.tiers?.[`tier${tier}`]?.complete ? t("已就绪", "Ready") : t("有缺失项", "Missing components")}`)) : [hint(t("检测依赖，不会安装软件。", "Check dependencies without installing software."))]),
      hint(t("T1 基础读写 · T2 转换/图片/字体 · T3 LibreOffice · T4 OCR/TeX（按需）", "T1 libraries · T2 images/fonts/conversion · T3 LibreOffice · T4 OCR/TeX (optional)")),
      ...[1,2,3].map(tier => secondary(t(`查看 T${tier} 安装计划`, `View Tier ${tier} install plan`), () => run("office_env_setup", { tier }, t(`T${tier} 安装计划`, `Tier ${tier} install plan`))))]));
    nodes.push(card(t("路径与清理说明", "Paths and cleanup"), [text(t("手机文件 → Ubuntu 暂存与处理 → 手机交付（默认）", "Phone input → Ubuntu processing → phone delivery (default)")),
      hint(t("Ubuntu /root/kiyori_office 通常对应 rootfs 存放位置下的 root/kiyori_office；手机挂载路径因设备配置而异，不能直接当 Ubuntu 内路径使用。", "Ubuntu /root/kiyori_office maps under the rootfs location. Phone-side mounts vary and are not Ubuntu paths.")),
      hint(t("runtime、runtime.zip、unzip_runtime.py 是程序；work/<任务>/in 是输入副本，tmp 是中间文件，out 是输出。删除任务前先保留需要的输出。", "runtime, runtime.zip and unzip_runtime.py are program files. work/<task>/in holds inputs, tmp intermediates, out outputs. Preserve needed outputs before deletion."))]));
    nodes.push(card(t("AI 办公指引", "Agent guides"), [hint(t("创建或复杂编辑在 AI 对话中提出要求。指引只供阅读，不执行文档操作。", "Ask in chat for creation or advanced editing. Reading guides does not run document operations.")), ...["core","docx","xlsx","pptx","pdf"].map(format => secondary(t(`阅读 ${format === "core" ? "办公总纲" : format.toUpperCase()}`, `Read ${format} guide`), () => run("office_read_guide", { format }, t("办公指引", "Office guide"))))]));
  }
  if (busy) nodes.push(ctx.UI.LinearProgressIndicator({ fillMaxWidth: true }));
  if (result) {
    const body = result.data?.content ?? JSON.stringify(result.data ?? {}, null, 2);
    nodes.push(card(title || t("操作结果", "Result"), [ctx.UI.Text({ text: result.success ? t("操作成功", "Succeeded") : result.message ?? t("操作未完成", "Action incomplete"), color: result.success ? "onSurface" : "error", style: "bodyMedium" }),
      ...(result.success && result.message && !result.command ? [text(result.message)] : []), ...(result.remedy ? [text(result.remedy)] : []), ...(result.warnings ?? []).map(w => hint(`${w.code}: ${w.message}`)),
      ...(result.data?.work_dir ? [hint(`${t("Ubuntu 工作目录", "Ubuntu work directory")}: ${result.data.work_dir}`)] : []),
      ...(result.data?.content ? [ctx.UI.Markdown({ text: result.data.content })] : [secondary(t(details ? "收起详情" : "查看详情", details ? "Hide details" : "Show details"), () => setDetails(!details)), ...(details || !result.success ? [hint(body.slice(0,16000))] : [])]),
      ...(body.length > 16000 || result.truncated ? [hint(t("结果已截断，请按完整输出路径继续读取。", "Truncated; continue at the full output path."))] : []), ...(result.full_output_path ? [hint(`Ubuntu: ${result.full_output_path}`)] : []),
      ...(planTier != null ? [text(t("安装将下载依赖并修改 Ubuntu，请先查看详情核对计划；过程显示在终端。", "Installation downloads dependencies and modifies Ubuntu. Review details; execution appears in Terminal.")), primary(t("确认执行安装计划", "Confirm installation"), () => run("office_env_setup", { tier: planTier, confirm: true }, t("安装结果", "Installation result")))] : [])]));
    for (const item of result.artifacts ?? []) nodes.push(card(item.path.split("/").pop() ?? t("产物", "Artifact"), [hint(`${item.env === "android" ? t("手机", "Phone") : "Ubuntu"}: ${item.path}`),
      ...(item.env === "android" ? [secondary(t(/\.(png|jpe?g)$/i.test(item.path) ? "查看预览图" : "打开文件", "Open file"), () => open(item))] : []),
      ...(/\.(docx|xlsx|xlsm|pptx|pdf)$/i.test(item.path) && ["android","linux"].includes(item.env) ? [secondary(t("作为输入继续处理", "Use as input"), () => { setPath(item.path); setEnv(item.env as Location); setTab("files"); setAction("read"); setPages(""); setResult(null); })] : [])]));
  }
  return ctx.UI.LazyColumn({ fillMaxSize: true, padding: 16, spacing: 12 }, nodes);
}
