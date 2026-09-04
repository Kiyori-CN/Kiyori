/*
METADATA
{
    "name": "super_admin",

    "display_name": {
        "zh": "超级管理员",
        "en": "Super Admin"
    },
    "description": { "zh": "在已挂载 Android 存储的 Ubuntu 终端中运行命令，或经 Shizuku/Root 执行 Android Shell；用于明确授权的系统管理任务。", "en": "Run commands in an Ubuntu terminal with Android storage mounted, or execute Android shell commands through Shizuku/Root for explicitly authorized system administration." },
    "enabledByDefault": true,
    "category": "System",
    "tools": [
        {
            "name": "terminal",
            "description": { "zh": "在Ubuntu环境中执行命令并收集输出结果。运行环境：完整的Ubuntu系统，已正确挂载sdcard和storage目录，可访问Android存储空间。所有命令使用同一逻辑会话；正常时保留 cwd 和 export 等 shell 上下文。前台未传 timeoutMs 时默认15秒；background=true 始终不设工具超时，显式传入的 timeoutMs 仅做参数校验后会被忽略，并在返回体中标记。前台超时或 shell 退出时会在同一 sessionId 下恢复；如果必须重建 shell，sessionRecovered/contextPreserved 会明确表示上下文是否保留。", "en": "Execute commands in a full Ubuntu environment with sdcard/storage mounted. Commands use one logical session and normally preserve shell state such as cwd and exported variables. Foreground mode defaults to a 15-second timeout when timeoutMs is omitted. background=true never applies a tool timeout; an explicit timeoutMs is validated and then ignored, with that policy reported in the result. Foreground timeout or shell exit is recovered under the same sessionId. If the shell had to be rebuilt, sessionRecovered/contextPreserved explicitly report whether shell context was retained." },
            "parameters": [
                {
                    "name": "command",
                    "description": { "zh": "要执行的命令", "en": "Command to execute." },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "background",
                    "description": { "zh": "是否在后台运行命令。true 表示提交后立即返回，适合启动服务器等长时间运行的任务；后台不设置工具超时，显式 timeoutMs 会被忽略并在返回体中说明；false 或未提供则前台执行并等待命令结果。", "en": "Run the command in the background. true returns immediately for long-running tasks and never applies a tool timeout; an explicit timeoutMs is ignored and reported in the result. false or omitted waits for the foreground command result." },
                    "type": "boolean",
                    "required": false
                },
                {
                    "name": "timeoutMs",
                    "description": { "zh": "前台命令可选超时（毫秒，最低3000ms）。未传时前台默认15000ms；background=true 时不设置工具超时，显式值只校验后忽略，并返回 timeoutMsIgnored。", "en": "Optional foreground timeout in milliseconds (minimum 3000). Foreground defaults to 15 seconds when omitted; background=true never applies a tool timeout, and an explicit value is validated then reported as timeoutMsIgnored." },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "terminal_wait",
            "description": { "zh": "等待同一终端会话回到 shell 空闲边界（通过队列末尾 marker 确认提示符可用）。它不跟踪 detached/background 任务的进程完成状态；后台任务请自行轮询副作用或使用命令内的完成标记。超时时会取消当前前台命令；如果 shell 无法在取消后回到提示符，会在同一 sessionId 下重建，并通过 sessionRecovered/contextPreserved 报告上下文状态。", "en": "Wait for the same terminal session to reach a shell-idle boundary (a queue-tail marker confirms that the prompt is usable). It does not track detached/background process completion; poll an explicit side effect or completion marker for those tasks. On timeout, the current foreground command is cancelled. If the shell cannot return to a prompt, it is rebuilt under the same sessionId and sessionRecovered/contextPreserved report the shell-context state." },
            "parameters": [
                {
                    "name": "sessionId",
                    "description": { "zh": "可选目标会话ID。不传则使用当前对话的默认会话；无 chatId 时为 super_admin_default_session。", "en": "Optional target session ID. If omitted, uses the current chat's default session; without chatId it is super_admin_default_session." },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "timeoutMs",
                    "description": { "zh": "等待 shell 空闲边界的超时（毫秒，最低3000ms）。未传时默认300000ms（5分钟）；不代表后台进程的执行时长。", "en": "Timeout for reaching the shell-idle boundary (minimum 3000ms). Defaults to 300000ms (5 minutes) when omitted; it is not a background-process lifetime limit." },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "terminal_getscreen",
            "description": { "zh": "获取目标终端会话可见屏幕内容（仅一屏，不包含历史滚动缓冲）。可通过 sessionId 指定会话；不传则使用当前对话的默认会话。", "en": "Get the visible screen content for the target terminal session (single screen only, no scrollback history). Pass sessionId to target a session; if omitted, uses the current chat's default session." },
            "parameters": [
                {
                    "name": "sessionId",
                    "description": { "zh": "可选目标会话ID。不传则使用当前对话的默认会话；无 chatId 时为 super_admin_default_session。", "en": "Optional target session ID. If omitted, uses the current chat's default session; without chatId it is super_admin_default_session." },
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "terminal_input",
            "description": { "zh": "向目标终端会话写入输入。可通过 sessionId 指定会话；不传则使用当前对话的默认会话。input 与 control 至少传一个。常见用法：先写 input，再写 control=enter 提交；control=ctrl 且 input=c 可发送 Ctrl+C。", "en": "Write input to the target terminal session. Pass sessionId to target a session; if omitted, uses the current chat's default session. Provide at least one of input or control. Typical usage: send input first, then control=enter to submit; use control=ctrl with input=c for Ctrl+C." },
            "parameters": [
                {
                    "name": "sessionId",
                    "description": { "zh": "可选目标会话ID。不传则使用当前对话的默认会话；无 chatId 时为 super_admin_default_session。", "en": "Optional target session ID. If omitted, uses the current chat's default session; without chatId it is super_admin_default_session." },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "input",
                    "description": { "zh": "写入终端的文本", "en": "Text to write to terminal." },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "control",
                    "description": { "zh": "控制键，例如 enter / tab / esc / ctrl", "en": "Control key, e.g. enter / tab / esc / ctrl." },
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "shell",
            "description": { "zh": "通过Shizuku/Root权限直接在Android系统中执行Shell命令。运行环境：直接访问Android系统，具有系统级权限，适用于需要操作Android系统底层的场景（如pm、am等系统命令）。", "en": "Execute shell commands directly on Android with Shizuku/Root. Environment: direct Android system access with system-level privileges, suitable for low-level commands such as pm/am." },
            "parameters": [
                {
                    "name": "command",
                    "description": { "zh": "要执行的Shell命令", "en": "Shell command to execute." },
                    "type": "string",
                    "required": true
                }
            ]
        }
    ]
}*/
const superAdmin = (function () {
    const MAX_INLINE_TERMINAL_OUTPUT_CHARS = 12000;
    const DEFAULT_FOREGROUND_TIMEOUT_MS = 15000;
    const DEFAULT_WAIT_TIMEOUT_MS = 300000;
    const MIN_TIMEOUT_MS = 3000;
    const DEFAULT_TERMINAL_SESSION_NAME = "super_admin_default_session";
    const BACKGROUND_TERMINAL_SESSION_PREFIX = "super_admin_background";
    let backgroundSessionSequence = 0;
    function parseTimeout(timeoutMs, defaultTimeoutMs) {
        if (timeoutMs === undefined) {
            return defaultTimeoutMs;
        }
        if (!Number.isInteger(timeoutMs) || timeoutMs < MIN_TIMEOUT_MS) {
            throw new Error(`timeoutMs必须是整数且不少于${MIN_TIMEOUT_MS}毫秒`);
        }
        return timeoutMs;
    }
    function getCurrentChatSessionSuffix() {
        const chatId = getChatId();
        if (chatId === undefined) {
            return "";
        }
        const normalizedChatId = chatId.trim();
        if (!normalizedChatId) {
            return "";
        }
        return normalizedChatId.replace(/[^a-zA-Z0-9._-]+/g, "_");
    }
    function getDefaultTerminalSessionName() {
        const chatSuffix = getCurrentChatSessionSuffix();
        return chatSuffix
            ? `${DEFAULT_TERMINAL_SESSION_NAME}_${chatSuffix}`
            : DEFAULT_TERMINAL_SESSION_NAME;
    }
    function getBackgroundTerminalSessionName() {
        const chatSuffix = getCurrentChatSessionSuffix();
        const prefix = chatSuffix
            ? `${BACKGROUND_TERMINAL_SESSION_PREFIX}_${chatSuffix}`
            : BACKGROUND_TERMINAL_SESSION_PREFIX;
        backgroundSessionSequence += 1;
        return `${prefix}_${Date.now()}_${backgroundSessionSequence}`;
    }
    function utf8ByteLength(value) {
        let bytes = 0;
        for (let index = 0; index < value.length; index += 1) {
            const code = value.charCodeAt(index);
            if (code >= 0xd800 && code <= 0xdbff && index + 1 < value.length) {
                const low = value.charCodeAt(index + 1);
                if (low >= 0xdc00 && low <= 0xdfff) {
                    bytes += 4;
                    index += 1;
                    continue;
                }
            }
            if (code <= 0x7f) {
                bytes += 1;
            }
            else if (code <= 0x7ff) {
                bytes += 2;
            }
            else {
                bytes += 3;
            }
        }
        return bytes;
    }
    function lineCount(value) {
        if (value.length === 0) {
            return 0;
        }
        let count = 0;
        for (const character of value) {
            if (character === "\n") {
                count += 1;
            }
        }
        return value.endsWith("\n") ? count : count + 1;
    }
    function describeSensitiveTextForLog(value) {
        // Commands may contain credentials, user paths, or inline file contents. Keep logs
        // useful for correlation without persisting the command itself.
        const hasShellOperators = /[;&|<>`$]/.test(value);
        return `chars=${value.length}, bytes=${utf8ByteLength(value)}, shellOperators=${hasShellOperators}`;
    }
    function describeErrorForLog(error) {
        const type = error instanceof Error ? error.name : typeof error;
        const message = error instanceof Error ? error.message : String(error);
        return `type=${type}, messageChars=${message.length}`;
    }
    async function persistTerminalOutputIfTooLong(command, result) {
        const outputStr = result.output;
        if (outputStr.length <= MAX_INLINE_TERMINAL_OUTPUT_CHARS) {
            return null;
        }
        await Tools.Files.mkdir(OPERIT_CLEAN_ON_EXIT_DIR, true);
        const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
        const rand = Math.floor(Math.random() * 1000000);
        const filePath = `${OPERIT_CLEAN_ON_EXIT_DIR}/terminal_output_${timestamp}_${rand}.log`;
        await Tools.Files.write(filePath, outputStr, false);
        const outputTruncated = result.outputTruncated === true;
        return {
            command,
            output: "(saved_to_file)",
            exitCode: result.exitCode,
            sessionId: result.sessionId,
            timedOut: result.timedOut === true,
            outputTruncated,
            originalOutputChars: result.originalOutputChars ?? outputStr.length,
            sessionHealthy: result.sessionHealthy === true,
            sessionRecovered: result.sessionRecovered === true,
            contextPreserved: result.contextPreserved !== false,
            context_preserved: result.contextPreserved !== false,
            output_saved_to: filePath,
            output_chars: outputStr.length,
            output_bytes: utf8ByteLength(outputStr),
            output_lines: lineCount(outputStr),
            output_is_preview: outputTruncated,
            operit_clean_on_exit_dir: OPERIT_CLEAN_ON_EXIT_DIR,
            hint: outputTruncated
                ? "Output exceeded the 4 MiB terminal capture limit; this clean-on-exit file contains only the explicit head/tail preview. Redirect the command to a file and use read_file_part or grep_code for the complete output."
                : "Output exceeded 12,000 JavaScript characters and is saved in this clean-on-exit file. Use read_file_part or grep_code to inspect it; the file contains the complete captured output.",
        };
    }
    /**
     * 在Ubuntu环境中执行终端命令并收集输出结果
     * 运行环境：完整的Ubuntu系统，已正确挂载sdcard和storage目录
     * shell 退出后保留逻辑 sessionId；底层 shell 重建时通过结果字段报告上下文已重置
     * @param command - 要执行的命令
     * @param background - 是否后台运行（true 为提交后立即返回，适合启动服务器等长时间运行任务，AI 不会收到该命令的输出结果）
     * @param timeoutMs - 前台可选超时时间（毫秒，最低 3000ms）；前台未传时默认 15000ms，后台模式只校验显式值后忽略。
     */
    async function terminal(params) {
        try {
            if (typeof params.command !== "string" || params.command.trim() === "") {
                throw new Error("命令不能为空");
            }
            const command = params.command;
            const background = params.background;
            const timeoutMs = params.timeoutMs;
            if (background !== undefined && typeof background !== "boolean") {
                throw new Error("background必须是布尔值");
            }
            console.log(`执行终端命令 (${describeSensitiveTextForLog(command)})`);
            const isBackground = background === true;
            let timeout;
            let timeoutMsIgnored;
            if (isBackground && timeoutMs !== undefined) {
                timeoutMsIgnored = parseTimeout(timeoutMs, MIN_TIMEOUT_MS);
            }
            else if (!isBackground) {
                timeout = parseTimeout(timeoutMs, DEFAULT_FOREGROUND_TIMEOUT_MS);
            }
            if (isBackground) {
                const session = await Tools.System.terminal.create(getBackgroundTerminalSessionName());
                const sessionId = session.sessionId;
                // 调用系统工具执行终端命令
                (async () => {
                    try {
                        // Detached mode has no tool deadline. Passing an explicit timeout here
                        // would kill a still-valid background job while its caller already holds
                        // only the started response and cannot observe the failure.
                        await Tools.System.terminal.exec(sessionId, command, undefined, { timeoutPolicy: "none" });
                    }
                    catch (error) {
                        console.error(`[terminal/background] 错误 (${describeErrorForLog(error)})`);
                    }
                })();
                return {
                    command: command,
                    background: true,
                    sessionId: sessionId,
                    started: true,
                    timeoutPolicy: timeoutMsIgnored === undefined ? "none" : "ignored",
                    ...(timeoutMsIgnored === undefined ? {} : { timeoutMsIgnored }),
                };
            }
            // 创建或获取一个默认会话
            const session = await Tools.System.terminal.create(getDefaultTerminalSessionName());
            const sessionId = session.sessionId;
            // 调用系统工具执行终端命令
            const result = await Tools.System.terminal.exec(sessionId, command, timeout);
            const timedOut = result.timedOut === true;
            const persistedResult = await persistTerminalOutputIfTooLong(command, result);
            if (persistedResult) {
                persistedResult.timeoutMsUsed = timeout;
                return persistedResult;
            }
            return {
                command: command,
                output: result.output,
                exitCode: result.exitCode,
                sessionId: result.sessionId,
                timedOut: timedOut,
                outputTruncated: result.outputTruncated === true,
                originalOutputChars: result.originalOutputChars ?? result.output.length,
                sessionHealthy: result.sessionHealthy === true,
                sessionRecovered: result.sessionRecovered === true,
                contextPreserved: result.contextPreserved !== false,
                timeoutMsUsed: timeout,
                context_preserved: result.contextPreserved !== false,
            };
        }
        catch (error) {
            console.error(`[terminal] 错误 (${describeErrorForLog(error)})`);
            throw error;
        }
    }
    async function bash(params) {
        return terminal(params);
    }
    /**
     * 等待同一终端会话回到 shell 空闲边界
     * 原理：向同会话追加一个内部 marker 命令。它确认的是 shell 队列边界，不跟踪 detached/background 进程。
     * @param sessionId - 可选会话ID；不传时使用当前对话的默认会话，无 chatId 时为 super_admin_default_session
     * @param timeoutMs - 可选超时（毫秒，最低 3000ms）；未传默认 300000ms
     */
    async function terminal_wait(params = {}) {
        try {
            const timeoutMs = params.timeoutMs;
            const timeout = parseTimeout(timeoutMs, DEFAULT_WAIT_TIMEOUT_MS);
            const session = params.sessionId
                ? { sessionId: params.sessionId }
                : await Tools.System.terminal.create(getDefaultTerminalSessionName());
            const sessionId = session.sessionId;
            const marker = `__OPERIT_TERMINAL_WAIT_DONE_${Date.now()}_${Math.floor(Math.random() * 1000000)}__`;
            const waitCommand = `printf '${marker}\\n'`;
            const startedAt = Date.now();
            const result = await Tools.System.terminal.exec(sessionId, waitCommand, timeout);
            const elapsedMs = Date.now() - startedAt;
            const timedOut = result?.timedOut === true;
            const outputStr = result.output;
            const markerSeen = outputStr.includes(marker);
            return {
                sessionId,
                timedOut,
                timeoutMsUsed: timeout,
                elapsedMs,
                waitCompleted: !timedOut && markerSeen && result.exitCode === 0,
                waitScope: "shell_idle",
                markerSeen,
                exitCode: result.exitCode,
                outputTruncated: result.outputTruncated === true,
                originalOutputChars: result.originalOutputChars ?? outputStr.length,
                sessionHealthy: result.sessionHealthy === true,
                sessionRecovered: result.sessionRecovered === true,
                contextPreserved: result.contextPreserved !== false,
                context_preserved: result.contextPreserved !== false,
            };
        }
        catch (error) {
            console.error(`[terminal_wait] 错误 (${describeErrorForLog(error)})`);
            throw error;
        }
    }
    /**
     * 通过Shizuku/Root权限在Android系统中执行Shell命令
     * 运行环境：直接访问Android系统，具有系统级权限
     * @param command - 要执行的Shell命令
     */
    async function shell(params) {
        try {
            if (!params.command) {
                throw new Error("命令不能为空");
            }
            const command = params.command;
            console.log(`执行Shell命令 (${describeSensitiveTextForLog(command)})`);
            // 通过Shizuku/Root权限执行shell操作
            const result = await Tools.System.shell(`${command}`);
            return {
                command: command,
                output: result.output,
                exitCode: result.exitCode
            };
        }
        catch (error) {
            console.error(`[shell] 错误 (${describeErrorForLog(error)})`);
            throw error;
        }
    }
    /**
     * 获取目标终端会话可见屏幕内容（仅一屏，不包含历史）
     * @param sessionId - 可选会话ID；不传时使用当前对话的默认会话，无 chatId 时为 super_admin_default_session
     */
    async function terminal_getscreen(params = {}) {
        try {
            const session = params.sessionId
                ? { sessionId: params.sessionId }
                : await Tools.System.terminal.create(getDefaultTerminalSessionName());
            const sessionId = session.sessionId;
            const result = await Tools.System.terminal.screen(sessionId);
            return {
                sessionId: result.sessionId ?? sessionId,
                rows: result.rows,
                cols: result.cols,
                content: result.content
            };
        }
        catch (error) {
            console.error(`[terminal_getscreen] 错误 (${describeErrorForLog(error)})`);
            throw error;
        }
    }
    /**
     * 向目标终端会话写入输入
     * @param sessionId - 可选会话ID；不传时使用当前对话的默认会话，无 chatId 时为 super_admin_default_session
     * @param input - 文本输入
     * @param control - 控制键
     */
    async function terminal_input(params = {}) {
        try {
            if (params.input === undefined && params.control === undefined) {
                throw new Error("input和control至少需要提供一个");
            }
            if (params.control === undefined && params.input !== undefined && params.input.length === 0) {
                throw new Error("input不能为空");
            }
            const session = params.sessionId
                ? { sessionId: params.sessionId }
                : await Tools.System.terminal.create(getDefaultTerminalSessionName());
            const sessionId = session.sessionId;
            const result = await Tools.System.terminal.input(sessionId, {
                input: params.input,
                control: params.control
            });
            return {
                sessionId,
                input: params.input,
                control: params.control,
                result
            };
        }
        catch (error) {
            console.error(`[terminal_input] 错误 (${describeErrorForLog(error)})`);
            throw error;
        }
    }
    return {
        terminal,
        bash,
        terminal_wait,
        terminal_getscreen,
        terminal_input,
        shell
    };
})();
// 逐个导出
exports.terminal = superAdmin.terminal;
exports.bash = superAdmin.bash;
exports.terminal_wait = superAdmin.terminal_wait;
exports.terminal_getscreen = superAdmin.terminal_getscreen;
exports.terminal_input = superAdmin.terminal_input;
exports.shell = superAdmin.shell;
