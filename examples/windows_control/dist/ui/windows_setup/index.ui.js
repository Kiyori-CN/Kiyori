"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = Screen;
const i18n_1 = require("../../i18n");
const connection_1 = require("../../connection");
const KEYS = ["WINDOWS_AGENT_BASE_URL", "WINDOWS_AGENT_TOKEN", "WINDOWS_AGENT_DEFAULT_SHELL", "WINDOWS_AGENT_TIMEOUT_MS"];
function readDraft(ctx) {
    return { baseUrl: ctx.getEnv(KEYS[0]) ?? "", token: ctx.getEnv(KEYS[1]) ?? "",
        shell: ctx.getEnv(KEYS[2]) || "powershell", timeout: ctx.getEnv(KEYS[3]) || "30000" };
}
function Screen(ctx) {
    const text = (0, i18n_1.resolveWindowsSetupI18n)((getLang() ?? "").toLowerCase().startsWith("en") ? "en-US" : "zh-CN");
    const [draft, setDraft] = ctx.useState("connectionDraft", readDraft(ctx));
    const liveDraft = ctx.useRef("liveDraft", draft);
    const [status, setStatus] = ctx.useState("connectionStatus", { state: "idle", detail: "" });
    const [busy, setBusy] = ctx.useState("busy", false);
    const lock = ctx.useRef("operationLock", false);
    const initialized = ctx.useRef("initialized", false);
    const [setupVisible, setSetupVisible] = ctx.useState("setupVisible", !draft.baseUrl);
    const [importVisible, setImportVisible] = ctx.useState("importVisible", false);
    const [advanced, setAdvanced] = ctx.useState("advanced", false);
    const [showToken, setShowToken] = ctx.useState("showToken", false);
    const [json, setJson] = ctx.useState("importJson", "");
    const [notice, setNotice] = ctx.useState("notice", "");
    function updateDraft(next) {
        liveDraft.current = next;
        setDraft(next);
        setStatus({ state: "idle", detail: text.changed });
        setNotice("");
    }
    function errorText(error) {
        console.error("[windows_setup] Operation failed", error instanceof Error ? error.name : typeof error);
        const message = error instanceof Error ? error.message : text.operationFailed;
        return liveDraft.current.token ? message.split(liveDraft.current.token).join("[redacted]") : message;
    }
    async function run(action) {
        if (lock.current)
            return;
        lock.current = true;
        setBusy(true);
        setNotice("");
        try {
            await action();
        }
        catch (error) {
            setStatus({ state: "failed", detail: errorText(error) });
        }
        finally {
            lock.current = false;
            setBusy(false);
        }
    }
    function packageName() {
        const current = ctx.getCurrentPackageName?.();
        return current && current !== ctx.getCurrentToolPkgId?.() ? current : "windows_control";
    }
    async function activate() {
        const name = packageName();
        if (!ctx.isPackageImported || !ctx.importPackage || !ctx.usePackage)
            throw new Error(text.hostUnavailable);
        if (!await ctx.isPackageImported(name)) {
            const result = await ctx.importPackage(name);
            if (/error|failed|not found/i.test(result ?? "") || !await ctx.isPackageImported(name))
                throw new Error(text.activationFailed);
        }
        const result = await ctx.usePackage(name);
        if (/error|failed|not found/i.test(result ?? ""))
            throw new Error(text.activationFailed);
    }
    async function testSaved() {
        const saved = readDraft(ctx);
        if (!saved.baseUrl || !saved.token) {
            setStatus({ state: "idle", detail: text.notConfigured });
            return;
        }
        const validated = (0, connection_1.validateConnectionConfig)(saved.baseUrl, saved.token, saved.shell, saved.timeout);
        setStatus({ state: "checking", detail: text.checking });
        const toolName = await ctx.resolveToolName?.({ packageName: packageName(), toolName: "windows_test_connection", preferImported: true });
        if (!toolName)
            throw new Error(text.activationFailed);
        const result = await ctx.callTool(toolName, { timeout_ms: Math.min(Number(validated.timeout), 30000) });
        let parsed = result;
        if (typeof result === "string") {
            try {
                parsed = JSON.parse(result);
            }
            catch {
                throw new Error(text.invalidResponse);
            }
        }
        if (!parsed || typeof parsed !== "object" || Array.isArray(parsed))
            throw new Error(text.invalidResponse);
        const data = parsed;
        if (data.success !== true)
            throw new Error(typeof data.error === "string" ? data.error : text.invalidResponse);
        const current = readDraft(ctx);
        if (current.baseUrl !== saved.baseUrl || current.token !== saved.token || current.timeout !== saved.timeout || current.shell !== saved.shell) {
            setStatus({ state: "idle", detail: text.changed });
            return;
        }
        setStatus({ state: "success", detail: text.connected, baseUrl: saved.baseUrl,
            version: typeof data.agentVersion === "string" ? data.agentVersion : undefined,
            duration: typeof data.durationMs === "number" ? data.durationMs : undefined });
    }
    async function save(next) {
        const config = (0, connection_1.validateConnectionConfig)(next.baseUrl, next.token, next.shell, next.timeout);
        // 整份校验后再交给宿主批量写入，避免半份新配置混入旧凭据。
        if (!ctx.setEnvs)
            throw new Error(text.hostUnavailable);
        await ctx.setEnvs({ [KEYS[0]]: config.baseUrl, [KEYS[1]]: config.token, [KEYS[2]]: config.shell, [KEYS[3]]: config.timeout });
        const saved = readDraft(ctx);
        if (saved.baseUrl !== config.baseUrl || saved.token !== config.token || saved.shell !== config.shell || saved.timeout !== config.timeout)
            throw new Error(text.hostUnavailable);
        liveDraft.current = config;
        setDraft(config);
        setJson("");
        setImportVisible(false);
        setShowToken(false);
        setNotice(text.saved);
        await activate();
        await testSaved();
    }
    async function importConfig() {
        let value;
        try {
            value = JSON.parse(json);
        }
        catch {
            throw new Error(text.invalidConfig);
        }
        if (!value || typeof value !== "object" || Array.isArray(value))
            throw new Error(text.invalidConfig);
        const data = value;
        const baseUrl = data[KEYS[0]], token = data[KEYS[1]];
        const shell = data[KEYS[2]] ?? "powershell", timeout = data[KEYS[3]] ?? "30000";
        if (typeof baseUrl !== "string" || typeof token !== "string" || typeof shell !== "string" || (typeof timeout !== "string" && typeof timeout !== "number"))
            throw new Error(text.invalidConfig);
        await save({ baseUrl, token, shell, timeout: `${timeout}` });
    }
    const U = ctx.UI;
    const textButton = (label, onClick) => U.TextButton({ enabled: !busy, onClick }, [U.Text({ text: label })]);
    const paragraph = (value) => U.Text({ text: value, style: "bodySmall", color: "onSurfaceVariant" });
    const button = (label, action) => U.Button({ text: label, enabled: !busy, onClick: () => run(action), fillMaxWidth: true });
    const card = (title, children) => U.Card({ fillMaxWidth: true }, [U.Column({ padding: 16, spacing: 12 }, [U.Text({ text: title, style: "titleMedium", fontWeight: "semiBold" }), ...children])]);
    const input = (key, label, placeholder) => U.TextField({ label, placeholder, value: draft[key], singleLine: true,
        readOnly: busy, isPassword: key === "token" && !showToken, onValueChange: value => updateDraft({ ...liveDraft.current, [key]: value }) });
    const statusTitle = status.state === "success" ? text.connected : status.state === "checking" ? text.checking : status.state === "failed" ? text.failed : text.notConfigured;
    const children = [
        U.Row({ verticalAlignment: "center" }, [U.Icon({ name: "computer", tint: "primary" }), U.Spacer({ width: 12 }), U.Text({ text: text.title, style: "titleLarge", fontWeight: "semiBold" })]),
        paragraph(text.subtitle),
        U.Card({ fillMaxWidth: true, containerColor: status.state === "failed" ? "errorContainer" : "surfaceVariant" }, [U.Column({ padding: 16, spacing: 8 }, [
                U.Text({ text: statusTitle, style: "titleMedium" }),
                ...(busy ? [U.LinearProgressIndicator({ fillMaxWidth: true })] : []),
                ...(status.detail && status.detail !== statusTitle ? [U.Text({ text: status.detail, style: "bodySmall" })] : []),
                ...(status.baseUrl ? [U.Text({ text: status.baseUrl, style: "bodySmall" })] : []),
                ...(status.version ? [paragraph(`PC Agent ${status.version}${status.duration !== undefined ? ` · ${status.duration} ms` : ""}`)] : [])
            ])]),
        card(text.connection, [paragraph(text.addressHelp), input("baseUrl", text.address, "https://pc.example.com / http://192.168.1.8:58321"), input("token", text.token),
            textButton(showToken ? text.hideToken : text.showToken, () => setShowToken(!showToken)),
            textButton(advanced ? text.hideAdvanced : text.advanced, () => setAdvanced(!advanced)),
            ...(advanced ? [input("shell", "Shell", "powershell / pwsh / cmd"), input("timeout", text.timeout, "30000")] : []),
            button(text.save, () => save(liveDraft.current)), button(text.recheck, testSaved), ...(notice ? [paragraph(notice)] : [])]),
        textButton(importVisible ? text.hideImport : text.import, () => setImportVisible(!importVisible)),
        ...(importVisible ? [card(text.import, [paragraph(text.importHelp), U.TextField({ label: text.config, value: json, minLines: 3, maxLines: 5, readOnly: busy, onValueChange: setJson }), button(text.applyImport, importConfig)])] : []),
        textButton(setupVisible ? text.hideSetup : text.setup, () => setSetupVisible(!setupVisible)),
        ...(setupVisible ? [card(text.setup, [paragraph(text.setupHelp), button(text.export, async () => {
                    const resource = await ToolPkg.readResource("pc_agent_zip");
                    if (typeof resource !== "string" || !resource.trim())
                        throw new Error(text.resourceMissing);
                    await ctx.callTool("share_file", { path: resource, title: "Kiyori PC Agent" });
                    setNotice(text.exported);
                }), paragraph(text.networkHelp)])] : []),
        card(text.usage, [paragraph(text.usageHelp)])
    ];
    return U.LazyColumn({ fillMaxSize: true, padding: 16, spacing: 12, onLoad: async () => {
            if (initialized.current)
                return;
            initialized.current = true;
            if (draft.baseUrl && draft.token)
                await run(testSaved);
        } }, children);
}
