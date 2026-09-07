/******/ (() => { // webpackBootstrap
/******/ 	"use strict";
/******/ 	var __webpack_modules__ = ({

/***/ 137:
/***/ (function(__unused_webpack_module, exports, __nccwpck_require__) {


var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", ({ value: true }));
console.log('Bridge process started. Loading modules...');
/**
 * MCP TCP Bridge
 *
 * Creates a bridge that connects STDIO-based MCP servers to TCP clients
 */
const net = __importStar(__nccwpck_require__(278));
const child_process_1 = __nccwpck_require__(317);
const uuid_1 = __nccwpck_require__(914);
const path = __importStar(__nccwpck_require__(928));
const os = __importStar(__nccwpck_require__(857));
/**
 * MCP Bridge class
 */
class McpBridge {
    buildSpawnLogData(serviceName) {
        return {
            name: serviceName,
            active: this.isServiceActive(serviceName),
            ready: this.serviceReadyMap.get(serviceName) || false,
            lastError: this.mcpErrors.get(serviceName) || "",
            logs: this.getServiceLogText(serviceName)
        };
    }
    rejectPendingSpawn(serviceName, message, code = -32603) {
        const pending = this.pendingSpawnRequests.get(serviceName);
        if (!pending)
            return;
        const response = {
            id: pending.id,
            success: false,
            error: {
                code,
                message,
                data: this.buildSpawnLogData(serviceName)
            }
        };
        pending.socket.write(JSON.stringify(response) + '\n');
        this.pendingSpawnRequests.delete(serviceName);
    }
    resolvePendingSpawnSuccess(serviceName, toolCount) {
        const pending = this.pendingSpawnRequests.get(serviceName);
        if (!pending)
            return;
        const response = {
            id: pending.id,
            success: true,
            result: {
                status: "started",
                name: serviceName,
                toolCount,
                ready: true,
                ...this.buildSpawnLogData(serviceName)
            }
        };
        pending.socket.write(JSON.stringify(response) + '\n');
        this.pendingSpawnRequests.delete(serviceName);
        console.log(`[${serviceName}] Spawn request completed successfully`);
    }
    isFatalErrorText(text) {
        if (!text)
            return false;
        return /environment variable .* is required/i.test(text)
            || /api[_-]?key.*required/i.test(text)
            || /missing required.*(api[_-]?key|token)/i.test(text);
    }
    /**
     * 检查 spawn 请求超时
     */
    checkSpawnTimeouts() {
        const now = Date.now();
        for (const [serviceName, request] of this.pendingSpawnRequests.entries()) {
            const timeoutMs = request.timeoutMs ?? this.SPAWN_TIMEOUT;
            if (now - request.timestamp > timeoutMs) {
                console.log(`Spawn request timeout for service: ${serviceName}`);
                this.rejectPendingSpawn(serviceName, `Service '${serviceName}' failed to start within ${timeoutMs / 1000}s`);
            }
        }
    }
    /**
     * 检查并关闭闲置的服务
     */
    checkIdleServices() {
        const now = Date.now();
        for (const serviceName of this.serviceHelpers.keys()) {
            const serviceInfo = this.serviceRegistry.get(serviceName);
            if (serviceInfo && serviceInfo.lastUsed) {
                if (now - serviceInfo.lastUsed > this.IDLE_TIMEOUT_MS) {
                    console.log(`[${serviceName}] Service has been idle for over ${this.IDLE_TIMEOUT_MS / 1000}s. Unspawning...`);
                    // Manually unspawn the service
                    const helper = this.serviceHelpers.get(serviceName);
                    if (helper) {
                        // Temporarily remove from registry to prevent auto-restart logic from firing
                        this.serviceRegistry.delete(serviceName);
                        helper.kill();
                        // Re-add to registry after a short delay
                        setTimeout(() => this.serviceRegistry.set(serviceName, serviceInfo), 100);
                    }
                }
            }
        }
    }
    appendServiceLog(serviceName, line) {
        if (!serviceName)
            return;
        if (!line)
            return;
        const normalized = line.length > this.MAX_LOG_LINE_LENGTH
            ? line.slice(0, this.MAX_LOG_LINE_LENGTH)
            : line;
        const list = this.serviceLogs.get(serviceName) || [];
        list.push(normalized);
        while (list.length > this.MAX_LOG_LINES) {
            list.shift();
        }
        this.serviceLogs.set(serviceName, list);
    }
    getServiceLogText(serviceName) {
        const list = this.serviceLogs.get(serviceName) || [];
        return list.join('');
    }
    constructor(config = {}) {
        this.server = null;
        // 统一的服务客户端映射 (本地和远程都使用 MCPClient)
        this.serviceHelpers = new Map();
        this.mcpToolsMap = new Map();
        this.serviceReadyMap = new Map();
        // 服务注册表 (纯内存)
        this.serviceRegistry = new Map();
        // 活跃连接
        this.activeConnections = new Set();
        // 请求跟踪
        this.pendingRequests = new Map();
        this.pendingSpawnRequests = new Map(); // 跟踪等待启动的 spawn 请求
        // 请求超时(毫秒)
        this.REQUEST_TIMEOUT = 180000; // 180秒超时 (3分钟)
        this.SPAWN_TIMEOUT = 180000; // spawn命令180秒超时
        // 服务错误记录
        this.mcpErrors = new Map();
        this.serviceExitSignals = new Map();
        this.socketBuffers = new Map();
        this.fatalServices = new Set();
        this.serviceLogs = new Map();
        this.MAX_LOG_LINES = 400;
        this.MAX_LOG_LINE_LENGTH = 4000;
        // 重启跟踪
        this.restartAttempts = new Map();
        this.MAX_RESTART_ATTEMPTS = 5; // 最多重启5次
        this.RESTART_DELAY_MS = 5000; // 基础重启延迟5秒
        this.IDLE_TIMEOUT_MS = 5 * 60 * 1000; // 默认5分钟闲置超时
        // 默认配置
        this.config = {
            port: 8752,
            host: '127.0.0.1',
            mcpCommand: 'node',
            mcpArgs: ['../your-mcp-server.js'],
            ...config
        };
        // 设置超时检查
        setInterval(() => this.checkRequestTimeouts(), 5000);
        setInterval(() => this.checkSpawnTimeouts(), 5000); // 检查 spawn 请求超时
        setInterval(() => this.checkIdleServices(), 60 * 1000); // 每分钟检查一次
    }
    /**
     * 注册新的MCP服务
     */
    registerService(name, info) {
        if (!name || !info.type) {
            return false;
        }
        if (info.type === 'local' && !info.command) {
            return false;
        }
        if (info.type === 'remote') {
            if (!info.endpoint) {
                return false;
            }
        }
        const serviceInfo = {
            name,
            type: info.type,
            command: info.command,
            args: info.args || [],
            cwd: info.cwd,
            endpoint: info.endpoint,
            connectionType: info.connectionType || 'httpStream', // Default to httpStream
            bearerToken: info.bearerToken,
            headers: info.headers,
            description: info.description || `MCP Service: ${name}`,
            env: info.env || {},
            created: Date.now(),
            lastUsed: undefined
        };
        this.serviceRegistry.set(name, serviceInfo);
        return true;
    }
    /**
     * 注销MCP服务
     */
    unregisterService(name) {
        if (!this.serviceRegistry.has(name)) {
            return false;
        }
        this.serviceRegistry.delete(name);
        return true;
    }
    /**
     * 获取已注册MCP服务列表
     */
    getServiceList() {
        return Array.from(this.serviceRegistry.values());
    }
    /**
     * 检查服务是否激活 (运行中或已连接)
     */
    isServiceActive(serviceName) {
        // 统一检查：客户端是否存在
        return this.serviceHelpers.has(serviceName);
    }
    /**
     * 连接到远程MCP服务 (HTTP/SSE)
     */
    async connectToRemoteService(serviceName, endpoint, connectionType = 'httpStream') {
        if (this.isServiceActive(serviceName)) {
            console.log(`Service ${serviceName} is already connected`);
            return;
        }
        console.log(`Connecting to remote MCP service ${serviceName} at ${endpoint}`);
        this.spawnServiceHelper(serviceName);
    }
    /**
     * 处理服务连接关闭和重连 (本地和远程服务统一处理)
     */
    handleServiceClosure(serviceName, signal = null) {
        console.log(`Service ${serviceName} connection closed or failed.`);
        this.serviceHelpers.delete(serviceName);
        this.serviceReadyMap.set(serviceName, false);
        if (this.fatalServices.has(serviceName)) {
            console.error(`[${serviceName}] Fatal startup error detected. Will not attempt restart.`);
            return;
        }
        const serviceInfo = this.serviceRegistry.get(serviceName);
        if (!serviceInfo) {
            return; // Service unregistered, don't reconnect
        }
        const attempts = (this.restartAttempts.get(serviceName) || 0) + 1;
        this.restartAttempts.set(serviceName, attempts);
        if (attempts > this.MAX_RESTART_ATTEMPTS) {
            console.error(`Service ${serviceName} has failed too many times. Will not reconnect again.`);
            const errorMessage = this.mcpErrors.get(serviceName) || 'Service failed to start after multiple attempts';
            this.rejectPendingSpawn(serviceName, `Service '${serviceName}' failed to start: ${errorMessage}`);
            return;
        }
        let reconnectDelay;
        if (signal === 'SIGABRT') {
            reconnectDelay = 0; // 立刻重启
            console.log(`[${serviceName}] Abort (SIGABRT) detected. Restarting immediately (attempt ${attempts}/${this.MAX_RESTART_ATTEMPTS})...`);
        }
        else {
            reconnectDelay = this.RESTART_DELAY_MS * Math.pow(2, attempts - 1);
            console.log(`Attempting to reconnect to service ${serviceName} in ${reconnectDelay / 1000}s (attempt ${attempts})...`);
        }
        setTimeout(() => {
            if (this.serviceRegistry.has(serviceName)) { // Check if service is still registered
                this.spawnServiceHelper(serviceName);
            }
        }, reconnectDelay);
    }
    /**
     * 启动本地MCP服务 (使用官方 MCPClient 的 stdio 连接)
     */
    /**
     * 展开路径中的 ~ 符号为用户主目录
     */
    expandPath(filePath) {
        if (filePath.startsWith('~/') || filePath === '~') {
            return path.join(os.homedir(), filePath.slice(1));
        }
        return filePath;
    }
    async startLocalService(serviceName, command, args, env, cwd) {
        if (!command) {
            console.log(`[${serviceName}] No command specified, skipping startup`);
            return;
        }
        if (this.isServiceActive(serviceName)) {
            console.log(`[${serviceName}] Service is already running`);
            return;
        }
        console.log(`[${serviceName}] Starting local service via helper...`);
        this.spawnServiceHelper(serviceName);
    }
    /**
     * Spawns a helper process for a service
     */
    spawnServiceHelper(serviceName) {
        const serviceInfo = this.serviceRegistry.get(serviceName);
        if (!serviceInfo) {
            console.error(`[${serviceName}] Cannot spawn helper: service not registered.`);
            return;
        }
        // Clean up any existing stale helper
        if (this.serviceHelpers.has(serviceName)) {
            this.serviceHelpers.get(serviceName)?.kill();
            this.serviceHelpers.delete(serviceName);
        }
        const helperPath = path.join(__dirname, 'spawn-helper.js');
        console.log(`[${serviceName}] Forking helper process: ${helperPath}`);
        const helper = (0, child_process_1.fork)(helperPath, [], {
            stdio: ['pipe', 'pipe', 'pipe', 'ipc'] // Enable IPC
        });
        this.serviceHelpers.set(serviceName, helper);
        this.serviceReadyMap.set(serviceName, false);
        this.mcpToolsMap.delete(serviceName);
        this.mcpErrors.delete(serviceName);
        this.serviceExitSignals.delete(serviceName); // Initialize the new map
        helper.stdout?.on('data', (data) => {
            const text = data.toString();
            this.appendServiceLog(serviceName, text);
            console.log(`[${serviceName}-helper]: ${text.trim()}`);
        });
        helper.stderr?.on('data', (data) => {
            const stderrStr = data.toString();
            this.appendServiceLog(serviceName, stderrStr);
            console.error(`[${serviceName}-helper-stderr]: ${stderrStr.trim()}`);
            if (this.isFatalErrorText(stderrStr)) {
                this.fatalServices.add(serviceName);
                this.mcpErrors.set(serviceName, stderrStr.trim());
                const message = `Service '${serviceName}' failed to start: ${this.mcpErrors.get(serviceName) || "Fatal error"}`;
                this.rejectPendingSpawn(serviceName, message);
            }
            if (/SIGABRT/i.test(stderrStr)) {
                console.log(`[${serviceName}] SIGABRT detected in stderr stream. Flagging for immediate restart.`);
                this.serviceExitSignals.set(serviceName, 'SIGABRT');
            }
        });
        helper.on('message', (message) => {
            this.handleHelperMessage(message);
        });
        helper.on('exit', (code, signal) => {
            console.log(`[${serviceName}] Helper process exited with code ${code}, signal ${signal}`);
            // 检查是否有我们从'closed'事件中存储的合成信号
            const exitSignal = this.serviceExitSignals.get(serviceName) || signal;
            this.serviceExitSignals.delete(serviceName); // 清理
            this.serviceHelpers.delete(serviceName);
            this.serviceReadyMap.set(serviceName, false);
            // Dont unregister, allow reconnection logic to handle it
            if (this.serviceRegistry.has(serviceName)) {
                this.handleServiceClosure(serviceName, exitSignal);
            }
        });
        helper.on('error', (err) => {
            console.error(`[${serviceName}] Error on helper process: ${err.message}`);
            this.serviceHelpers.delete(serviceName);
            this.serviceReadyMap.set(serviceName, false);
            if (this.serviceRegistry.has(serviceName)) {
                this.handleServiceClosure(serviceName, null);
            }
        });
        // Send initialization info
        helper.send({
            command: 'init',
            params: {
                serviceName,
                serviceInfo
            }
        });
    }
    /**
     * Handles messages from helper processes
     */
    handleHelperMessage(message) {
        const { event, id, params, result } = message;
        const serviceName = params?.serviceName;
        switch (event) {
            case 'ready':
                console.log(`MCP service ${serviceName} is ready with ${params.tools.length} tools`);
                this.mcpToolsMap.set(serviceName, params.tools);
                this.serviceReadyMap.set(serviceName, true);
                this.restartAttempts.set(serviceName, 0); // Reset restart attempts on successful connection
                this.fatalServices.delete(serviceName);
                this.resolvePendingSpawnSuccess(serviceName, params.tools.length);
                break;
            case 'tool_result':
                const pendingRequest = this.pendingRequests.get(id);
                if (pendingRequest) {
                    const response = {
                        id,
                        success: result.success,
                        result: result.result,
                        error: result.error
                    };
                    pendingRequest.socket.write(JSON.stringify(response) + '\n');
                    this.pendingRequests.delete(id);
                }
                else {
                    console.warn(`Received tool result for unknown request ID: ${id}`);
                }
                break;
            case 'closed':
                console.log(`Service ${params.serviceName} connection closed by helper.`);
                if (params.error) {
                    this.mcpErrors.set(params.serviceName, params.error);
                }
                if (params.error && this.isFatalErrorText(params.error)) {
                    this.fatalServices.add(params.serviceName);
                }
                if (params.signal) { // The synthetic signal from the helper
                    this.serviceExitSignals.set(params.serviceName, params.signal);
                }
                if (this.fatalServices.has(params.serviceName)) {
                    const errorMessage = this.mcpErrors.get(params.serviceName) || params.error || 'Fatal startup error';
                    this.rejectPendingSpawn(params.serviceName, `Service '${params.serviceName}' failed to start: ${errorMessage}`);
                }
                // The 'exit' event on the helper process will trigger handleServiceClosure
                break;
            case 'error':
                console.error(`Received error from ${params.serviceName} helper: ${params.error}`);
                this.mcpErrors.set(params.serviceName, params.error);
                break;
        }
    }
    /**
     * 获取特定服务的MCP工具列表 (统一处理本地和远程服务)
     */
    async fetchMcpTools(serviceName) {
        // This is now handled by the helper process via IPC ('ready' event)
        console.log(`[${serviceName}] Tool fetch is now managed by the helper process.`);
        if (!this.isServiceActive(serviceName)) {
            this.serviceReadyMap.set(serviceName, true); // Mark as ready to avoid deadlocks
        }
    }
    /**
     * 处理客户端MCP命令
     */
    handleMcpCommand(command, socket) {
        const { id, command: cmdType, params } = command;
        let response;
        try {
            switch (cmdType) {
                case 'logs':
                    const logsServiceName = params?.name;
                    if (!logsServiceName) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing required parameter: name"
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    response = {
                        id,
                        success: true,
                        result: {
                            name: logsServiceName,
                            active: this.isServiceActive(logsServiceName),
                            ready: this.serviceReadyMap.get(logsServiceName) || false,
                            lastError: this.mcpErrors.get(logsServiceName) || "",
                            logs: this.getServiceLogText(logsServiceName)
                        }
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'listtools':
                    // 查询特定服务的可用工具列表
                    const serviceToList = params?.name;
                    if (serviceToList) {
                        const hasCachedTools = this.mcpToolsMap.has(serviceToList);
                        const cachedTools = this.mcpToolsMap.get(serviceToList) || [];
                        if (hasCachedTools) {
                            // If tools are in the cache, it means the service has been spawned at least once.
                            response = {
                                id,
                                success: true,
                                result: {
                                    active: this.isServiceActive(serviceToList),
                                    tools: cachedTools
                                }
                            };
                        }
                        else {
                            // If not in the cache, it has never been successfully spawned.
                            response = {
                                id,
                                success: false,
                                error: {
                                    code: -32603,
                                    message: `Service '${serviceToList}' has not been activated, tool list is unavailable.`
                                }
                            };
                        }
                    }
                    else {
                        // 未指定服务，列出所有曾经启动过的服务的工具
                        const allTools = {};
                        for (const [name, tools] of this.mcpToolsMap.entries()) {
                            allTools[name] = {
                                active: this.isServiceActive(name),
                                tools: tools
                            };
                        }
                        response = {
                            id,
                            success: true,
                            result: {
                                serviceTools: allTools
                            }
                        };
                    }
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'list':
                    // 列出已注册的MCP服务并附带运行状态，或查询单个服务
                    const queryServiceName = params?.name;
                    if (queryServiceName) {
                        // 查询单个服务
                        if (this.serviceRegistry.has(queryServiceName)) {
                            const serviceInfo = this.serviceRegistry.get(queryServiceName);
                            const isActive = this.isServiceActive(queryServiceName);
                            const isReady = this.serviceReadyMap.get(queryServiceName) || false;
                            const tools = this.mcpToolsMap.get(queryServiceName) || [];
                            response = {
                                id,
                                success: true,
                                result: {
                                    name: queryServiceName,
                                    type: serviceInfo?.type,
                                    description: serviceInfo?.description,
                                    active: isActive,
                                    ready: isReady,
                                    toolCount: tools.length,
                                    tools: tools,
                                    timestamp: Date.now()
                                }
                            };
                            // 更新最后使用时间
                            if (serviceInfo) {
                                serviceInfo.lastUsed = Date.now();
                            }
                        }
                        else {
                            response = {
                                id,
                                success: false,
                                error: {
                                    code: -32601,
                                    message: `Service '${queryServiceName}' not registered`
                                }
                            };
                        }
                    }
                    else {
                        // 列出所有服务
                        const services = this.getServiceList().map(service => {
                            const tools = this.mcpToolsMap.get(service.name) || [];
                            return {
                                ...service,
                                active: this.isServiceActive(service.name),
                                ready: this.serviceReadyMap.get(service.name) || false,
                                toolCount: tools.length,
                                tools: tools
                            };
                        });
                        response = {
                            id,
                            success: true,
                            result: {
                                services,
                                timestamp: Date.now()
                            }
                        };
                    }
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'spawn':
                    // 启动新的MCP服务，不关闭其他服务
                    if (!params) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing parameters"
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    const spawnServiceName = params.name;
                    let serviceCommand = params.command;
                    let serviceArgs = params.args || [];
                    let serviceEnv = params.env;
                    let serviceCwd = params.cwd;
                    const timeoutMs = typeof params.timeoutMs === 'number' ? params.timeoutMs : undefined;
                    if (this.fatalServices.has(spawnServiceName)) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32603,
                                message: this.mcpErrors.get(spawnServiceName) || `Service '${spawnServiceName}' failed with fatal error`,
                                data: {
                                    name: spawnServiceName,
                                    active: this.isServiceActive(spawnServiceName),
                                    ready: this.serviceReadyMap.get(spawnServiceName) || false,
                                    lastError: this.mcpErrors.get(spawnServiceName) || "",
                                    logs: this.getServiceLogText(spawnServiceName)
                                }
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    if ((this.serviceReadyMap.get(spawnServiceName) || false) && this.isServiceActive(spawnServiceName)) {
                        response = {
                            id,
                            success: true,
                            result: {
                                status: "ready",
                                name: spawnServiceName,
                                active: true,
                                ready: true,
                                toolCount: (this.mcpToolsMap.get(spawnServiceName) || []).length,
                                lastError: this.mcpErrors.get(spawnServiceName) || "",
                                logs: this.getServiceLogText(spawnServiceName)
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    const existingPending = this.pendingSpawnRequests.get(spawnServiceName);
                    if (existingPending) {
                        const cancelResponse = {
                            id: existingPending.id,
                            success: false,
                            error: {
                                code: -32603,
                                message: `Service '${spawnServiceName}' spawn request replaced`,
                                data: this.buildSpawnLogData(spawnServiceName)
                            }
                        };
                        existingPending.socket.write(JSON.stringify(cancelResponse) + '\n');
                        this.pendingSpawnRequests.delete(spawnServiceName);
                    }
                    // 优先从注册表查找服务信息
                    const serviceInfo = this.serviceRegistry.get(spawnServiceName);
                    // 如果未提供命令，但服务已注册，则使用注册表信息
                    if (serviceInfo) {
                        if (serviceInfo.type === 'local') {
                            this.startLocalService(spawnServiceName, serviceInfo.command, serviceInfo.args, serviceInfo.env, serviceInfo.cwd);
                        }
                        else if (serviceInfo.type === 'remote') {
                            this.connectToRemoteService(spawnServiceName, serviceInfo.endpoint, serviceInfo.connectionType);
                        }
                    }
                    else if (serviceCommand) {
                        // 如果服务未注册，但提供了command，则假定为本地服务并自动注册
                        this.registerService(spawnServiceName, {
                            type: 'local',
                            command: serviceCommand,
                            args: serviceArgs,
                            cwd: serviceCwd,
                            description: `Auto-registered service ${spawnServiceName}`,
                            env: serviceEnv,
                        });
                        console.log(`Auto-registered new service: ${spawnServiceName}`);
                        this.startLocalService(spawnServiceName, serviceCommand, serviceArgs, serviceEnv, serviceCwd);
                    }
                    else {
                        // 如果服务未注册且没有提供command，则无法启动
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: `Service '${spawnServiceName}' is not registered and no command provided.`
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    this.pendingSpawnRequests.set(spawnServiceName, {
                        id,
                        socket,
                        timestamp: Date.now(),
                        timeoutMs
                    });
                    break;
                case 'shutdown':
                    // 关闭特定的MCP服务
                    const serviceToShutdown = params?.name;
                    if (!serviceToShutdown) {
                        // 未指定服务，返回错误
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing required parameter: name"
                            }
                        };
                    }
                    else if (!this.isServiceActive(serviceToShutdown)) {
                        // 服务未运行
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: `Service '${serviceToShutdown}' not active`
                            }
                        };
                    }
                    else {
                        // 获取客户端并关闭（异步操作）
                        const helper = this.serviceHelpers.get(serviceToShutdown);
                        if (helper) {
                            console.log(`[${serviceToShutdown}] Closing MCP helper and unregistering...`);
                            // Prevent auto-restarting by removing from registry BEFORE killing
                            this.serviceRegistry.delete(serviceToShutdown);
                            helper.kill(); // This will trigger the 'exit' handler which cleans up the maps
                        }
                        response = {
                            id,
                            success: true,
                            result: {
                                status: "shutdown",
                                name: serviceToShutdown
                            }
                        };
                    }
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'unspawn':
                    // 只关闭服务进程，但不从注册表注销
                    const serviceToUnspawn = params?.name;
                    if (!serviceToUnspawn) {
                        response = { id, success: false, error: { code: -32602, message: "Missing required parameter: name" } };
                    }
                    else if (!this.isServiceActive(serviceToUnspawn)) {
                        response = { id, success: true, result: { status: "already_unspawned", name: serviceToUnspawn } };
                    }
                    else {
                        const helper = this.serviceHelpers.get(serviceToUnspawn);
                        if (helper) {
                            console.log(`[${serviceToUnspawn}] Unspawning service helper...`);
                            // Don't unregister, just kill the process. The 'exit' handler will clean up the helper map.
                            // Temporarily remove from registry to prevent immediate auto-restart
                            const serviceInfo = this.serviceRegistry.get(serviceToUnspawn);
                            this.serviceRegistry.delete(serviceToUnspawn);
                            helper.kill();
                            // Add it back to the registry after a short delay
                            if (serviceInfo) {
                                setTimeout(() => this.serviceRegistry.set(serviceToUnspawn, serviceInfo), 100);
                            }
                        }
                        response = { id, success: true, result: { status: "unspawned", name: serviceToUnspawn } };
                    }
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'register':
                    // 注册新的MCP服务
                    if (!params || !params.name || !params.type) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing required parameters: name, type"
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    if (params.type === 'local' && !params.command) {
                        response = {
                            id,
                            success: false,
                            error: { code: -32602, message: "Missing parameter 'command' for local service" }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    if (params.type === 'remote' && !params.endpoint) {
                        response = {
                            id,
                            success: false,
                            error: { code: -32602, message: "Missing 'endpoint' for remote service" }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    const registered = this.registerService(params.name, {
                        type: params.type,
                        command: params.command,
                        args: params.args || [],
                        cwd: params.cwd,
                        description: params.description,
                        env: params.env,
                        endpoint: params.endpoint,
                        connectionType: params.connectionType,
                        bearerToken: params.bearerToken,
                        headers: params.headers,
                    });
                    response = {
                        id,
                        success: registered,
                        result: registered ? {
                            status: 'registered',
                            name: params.name
                        } : undefined,
                        error: !registered ? {
                            code: -32602,
                            message: "Failed to register service"
                        } : undefined
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'unregister':
                    // 注销MCP服务
                    if (!params || !params.name) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing required parameter: name"
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    const serviceNameToUnregister = params.name;
                    if (!this.serviceRegistry.has(serviceNameToUnregister)) {
                        response = { id, success: false, error: { code: -32602, message: `Service '${serviceNameToUnregister}' not registered` } };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    // 如果运行中，先关闭
                    if (this.isServiceActive(serviceNameToUnregister)) {
                        const helper = this.serviceHelpers.get(serviceNameToUnregister);
                        helper?.kill();
                        this.serviceHelpers.delete(serviceNameToUnregister);
                    }
                    const unregistered = this.unregisterService(serviceNameToUnregister);
                    response = {
                        id,
                        success: unregistered,
                        result: unregistered ? {
                            status: 'unregistered',
                            name: serviceNameToUnregister
                        } : undefined,
                        error: !unregistered ? {
                            code: -32602,
                            message: `Service '${serviceNameToUnregister}' does not exist`
                        } : undefined
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'toolcall':
                    // 调用工具
                    this.handleToolCall(command, socket);
                    break;
                case 'cachetools':
                    // 缓存工具列表到bridge，用于已有缓存的插件
                    if (!params || !params.name || !params.tools) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing required parameters: name, tools"
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    const cacheServiceName = params.name;
                    const tools = params.tools;
                    // 验证服务是否已注册
                    if (!this.serviceRegistry.has(cacheServiceName)) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: `Service '${cacheServiceName}' is not registered. Please register it first.`
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    // 缓存工具列表
                    this.mcpToolsMap.set(cacheServiceName, tools);
                    console.log(`[${cacheServiceName}] Cached ${tools.length} tools from client cache`);
                    response = {
                        id,
                        success: true,
                        result: {
                            status: 'cached',
                            name: cacheServiceName,
                            toolCount: tools.length
                        }
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'reset':
                    // 重置所有服务：关闭所有客户端，清空注册表
                    console.log('Resetting bridge: closing all services and clearing registry...');
                    // 关闭所有活跃的服务客户端
                    for (const [name, helper] of this.serviceHelpers.entries()) {
                        try {
                            console.log(`Closing service: ${name}`);
                            helper.kill();
                        }
                        catch (error) {
                            console.error(`Error closing service ${name}: ${error instanceof Error ? error.message : String(error)}`);
                        }
                    }
                    // 清空所有映射和状态
                    this.serviceHelpers.clear();
                    this.mcpToolsMap.clear();
                    this.serviceReadyMap.clear();
                    this.mcpErrors.clear();
                    this.restartAttempts.clear();
                    this.serviceExitSignals.clear(); // Clear the new map
                    // 清空服务注册表
                    this.serviceRegistry.clear();
                    // 清空待处理的请求
                    this.pendingRequests.clear();
                    this.pendingSpawnRequests.clear();
                    console.log('Bridge reset complete: all services closed, registry cleared');
                    response = {
                        id,
                        success: true,
                        result: {
                            status: 'reset',
                            message: 'All services closed and registry cleared'
                        }
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                default:
                    // 未知命令
                    response = {
                        id,
                        success: false,
                        error: {
                            code: -32601,
                            message: `Unknown command: ${cmdType}`
                        }
                    };
                    socket.write(JSON.stringify(response) + '\n');
            }
        }
        catch (error) {
            // 通用错误处理
            const errorResponse = {
                id,
                success: false,
                error: {
                    code: -32603,
                    message: `Internal server error: ${error instanceof Error ? error.message : String(error)}`
                }
            };
            socket.write(JSON.stringify(errorResponse) + '\n');
        }
    }
    /**
     * 处理工具调用请求
     */
    async handleToolCall(command, socket) {
        const { id, params } = command;
        const { method, params: methodParams, name: requestedServiceName } = params || {};
        // 确定使用哪个服务
        const serviceName = requestedServiceName || (this.serviceHelpers.keys().next().value);
        if (!serviceName) {
            console.error(`Cannot handle tool call: No service specified and no default available`);
            const response = {
                id,
                success: false,
                error: {
                    code: -32602,
                    message: 'No service specified and no default available'
                }
            };
            socket.write(JSON.stringify(response) + '\n');
            return;
        }
        // 更新服务最后使用时间
        const serviceInfo = this.serviceRegistry.get(serviceName);
        if (serviceInfo) {
            serviceInfo.lastUsed = Date.now();
        }
        if (!this.isServiceActive(serviceName)) {
            const response = {
                id,
                success: false,
                error: { code: -32603, message: `Service '${serviceName}' is not active` }
            };
            socket.write(JSON.stringify(response) + '\n');
            return;
        }
        try {
            // 记录请求
            this.pendingRequests.set(id, {
                id,
                socket,
                timestamp: Date.now()
            });
            const helper = this.serviceHelpers.get(serviceName);
            if (!helper) {
                throw new Error(`Helper for service ${serviceName} not found`);
            }
            helper.send({
                command: 'toolcall',
                id: id,
                params: {
                    name: method,
                    args: methodParams || {}
                }
            });
        }
        catch (error) {
            console.error(`Error handling tool call for ${serviceName}: ${error instanceof Error ? error.message : String(error)}`);
            // 发送错误响应
            const response = {
                id,
                success: false,
                error: {
                    code: -32603,
                    message: `Internal error: ${error instanceof Error ? error.message : String(error)}`
                }
            };
            socket.write(JSON.stringify(response) + '\n');
            // 清理
            this.pendingRequests.delete(id);
        }
    }
    /**
     * 检查请求超时
     */
    checkRequestTimeouts() {
        const now = Date.now();
        for (const [requestId, request] of this.pendingRequests.entries()) {
            if (now - request.timestamp > this.REQUEST_TIMEOUT) {
                console.log(`Request timeout: ${requestId}`);
                // 发送超时响应
                const response = {
                    id: requestId,
                    success: false,
                    error: {
                        code: -32603,
                        message: "Request timeout"
                    }
                };
                request.socket.write(JSON.stringify(response) + '\n');
                // 清理
                this.pendingRequests.delete(requestId);
                if (request.toolCallId) {
                    // this.toolResponseMapping.delete(request.toolCallId); // No longer needed
                    // this.toolCallServiceMap.delete(request.toolCallId); // No longer needed
                }
            }
        }
    }
    /**
     * 启动TCP服务器
     */
    start() {
        try {
            console.log('Attempting to start TCP server...');
            // 创建TCP服务器 - 默认不启动任何MCP进程
            this.server = net.createServer((socket) => {
                console.log(`New client connection: ${socket.remoteAddress}:${socket.remotePort}`);
                this.activeConnections.add(socket);
                this.socketBuffers.set(socket, '');
                // 添加socket超时以防止客户端挂起
                // 设置为请求超时的 2 倍，确保有足够时间完成工具调用
                socket.setTimeout(120000); // 120秒超时 (REQUEST_TIMEOUT * 2)
                socket.on('timeout', () => {
                    console.log(`Socket timeout: ${socket.remoteAddress}:${socket.remotePort}`);
                    socket.end();
                    this.activeConnections.delete(socket);
                });
                // 处理来自客户端的数据
                socket.on('data', (data) => {
                    let buffer = (this.socketBuffers.get(socket) || '') + data.toString();
                    let newlineIndex;
                    while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
                        const message = buffer.substring(0, newlineIndex).trim();
                        buffer = buffer.substring(newlineIndex + 1);
                        if (!message) {
                            continue;
                        }
                        try {
                            // 解析命令
                            const command = JSON.parse(message);
                            // 确保命令有ID
                            if (!command.id) {
                                command.id = (0, uuid_1.v4)();
                            }
                            // 处理命令
                            if (command.command) {
                                this.handleMcpCommand(command, socket);
                            }
                            else {
                                // 非桥接命令，无默认服务转发
                                socket.write(JSON.stringify({
                                    jsonrpc: '2.0',
                                    id: this.extractId(message),
                                    error: {
                                        code: -32600,
                                        message: 'Invalid request: no service specified'
                                    }
                                }) + '\n');
                            }
                        }
                        catch (e) {
                            console.error(`Failed to parse client message: ${e}`);
                            // 发送错误响应
                            socket.write(JSON.stringify({
                                jsonrpc: '2.0',
                                id: null,
                                error: {
                                    code: -32700,
                                    message: `Invalid JSON: ${e}`
                                }
                            }) + '\n');
                        }
                    }
                    this.socketBuffers.set(socket, buffer);
                });
                // 处理客户端断开连接
                socket.on('close', () => {
                    console.log(`Client disconnected: ${socket.remoteAddress}:${socket.remotePort}`);
                    this.activeConnections.delete(socket);
                    this.socketBuffers.delete(socket);
                    // 清理此连接的待处理请求
                    for (const [requestId, request] of this.pendingRequests.entries()) {
                        if (request.socket === socket) {
                            const toolCallId = request.toolCallId;
                            this.pendingRequests.delete(requestId);
                            if (toolCallId) {
                                // this.toolResponseMapping.delete(toolCallId); // No longer needed
                                // this.toolCallServiceMap.delete(toolCallId); // No longer needed
                            }
                        }
                    }
                    // 清理此连接的待处理 spawn 请求
                    for (const [serviceName, request] of this.pendingSpawnRequests.entries()) {
                        if (request.socket === socket) {
                            this.pendingSpawnRequests.delete(serviceName);
                            console.log(`[${serviceName}] Spawn request cancelled due to client disconnect`);
                        }
                    }
                });
                // 处理客户端错误
                socket.on('error', (err) => {
                    console.error(`Client error: ${err.message}`);
                    this.activeConnections.delete(socket);
                    this.socketBuffers.delete(socket);
                });
            });
            // 启动TCP服务器
            this.server.listen(this.config.port, this.config.host, () => {
                console.log(`TCP bridge server running on ${this.config.host}:${this.config.port}`);
            });
            // 处理服务器错误
            this.server.on('error', (err) => {
                console.error(`Server error: ${err.message}`);
            });
            // 处理进程信号
            process.on('SIGINT', () => this.shutdown());
            process.on('SIGTERM', () => this.shutdown());
        }
        catch (error) {
            console.error('FATAL ERROR during server startup:', error);
            process.exit(1);
        }
    }
    /**
     * 关闭桥接器
     */
    shutdown() {
        console.log('Shutting down bridge...');
        // 关闭所有客户端连接
        for (const socket of this.activeConnections) {
            socket.end();
        }
        // 关闭服务器
        if (this.server) {
            this.server.close();
        }
        // 终止所有MCP进程
        for (const [name, helper] of this.serviceHelpers.entries()) {
            console.log(`Closing MCP helper: ${name}`);
            helper.kill();
        }
        this.serviceHelpers.clear();
        console.log('Bridge shut down');
        process.exit(0);
    }
    /**
     * 从JSON-RPC请求中提取ID
     */
    extractId(request) {
        try {
            const json = JSON.parse(request);
            return json.id || null;
        }
        catch (e) {
            return null;
        }
    }
}
// If running this script directly, create and start bridge
if (require.main === require.cache[eval('__filename')]) {
    try {
        console.log('Bridge script is main module. Initializing...');
        // Parse config from command line args
        const args = process.argv.slice(2);
        const port = parseInt(args[0]) || 8752;
        const mcpCommand = args[1] || 'node';
        const mcpArgs = args.slice(2);
        const bridge = new McpBridge({
            port,
            mcpCommand,
            mcpArgs: mcpArgs.length > 0 ? mcpArgs : undefined
        });
        bridge.start();
        console.log('Bridge initialization complete.');
    }
    catch (e) {
        console.error("FATAL ERROR in main execution block:", e);
        process.exit(1);
    }
}
// Export bridge class for use by other modules
exports["default"] = McpBridge;
//# sourceMappingURL=index.js.map

/***/ }),

/***/ 317:
/***/ ((module) => {

module.exports = require("child_process");

/***/ }),

/***/ 982:
/***/ ((module) => {

module.exports = require("crypto");

/***/ }),

/***/ 278:
/***/ ((module) => {

module.exports = require("net");

/***/ }),

/***/ 857:
/***/ ((module) => {

module.exports = require("os");

/***/ }),

/***/ 928:
/***/ ((module) => {

module.exports = require("path");

/***/ }),

/***/ 914:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.version = exports.validate = exports.v7 = exports.v6ToV1 = exports.v6 = exports.v5 = exports.v4 = exports.v3 = exports.v1ToV6 = exports.v1 = exports.stringify = exports.parse = exports.NIL = exports.MAX = void 0;
var max_js_1 = __nccwpck_require__(576);
Object.defineProperty(exports, "MAX", ({ enumerable: true, get: function () { return max_js_1.default; } }));
var nil_js_1 = __nccwpck_require__(805);
Object.defineProperty(exports, "NIL", ({ enumerable: true, get: function () { return nil_js_1.default; } }));
var parse_js_1 = __nccwpck_require__(713);
Object.defineProperty(exports, "parse", ({ enumerable: true, get: function () { return parse_js_1.default; } }));
var stringify_js_1 = __nccwpck_require__(687);
Object.defineProperty(exports, "stringify", ({ enumerable: true, get: function () { return stringify_js_1.default; } }));
var v1_js_1 = __nccwpck_require__(597);
Object.defineProperty(exports, "v1", ({ enumerable: true, get: function () { return v1_js_1.default; } }));
var v1ToV6_js_1 = __nccwpck_require__(92);
Object.defineProperty(exports, "v1ToV6", ({ enumerable: true, get: function () { return v1ToV6_js_1.default; } }));
var v3_js_1 = __nccwpck_require__(691);
Object.defineProperty(exports, "v3", ({ enumerable: true, get: function () { return v3_js_1.default; } }));
var v4_js_1 = __nccwpck_require__(834);
Object.defineProperty(exports, "v4", ({ enumerable: true, get: function () { return v4_js_1.default; } }));
var v5_js_1 = __nccwpck_require__(465);
Object.defineProperty(exports, "v5", ({ enumerable: true, get: function () { return v5_js_1.default; } }));
var v6_js_1 = __nccwpck_require__(544);
Object.defineProperty(exports, "v6", ({ enumerable: true, get: function () { return v6_js_1.default; } }));
var v6ToV1_js_1 = __nccwpck_require__(104);
Object.defineProperty(exports, "v6ToV1", ({ enumerable: true, get: function () { return v6ToV1_js_1.default; } }));
var v7_js_1 = __nccwpck_require__(631);
Object.defineProperty(exports, "v7", ({ enumerable: true, get: function () { return v7_js_1.default; } }));
var validate_js_1 = __nccwpck_require__(182);
Object.defineProperty(exports, "validate", ({ enumerable: true, get: function () { return validate_js_1.default; } }));
var version_js_1 = __nccwpck_require__(302);
Object.defineProperty(exports, "version", ({ enumerable: true, get: function () { return version_js_1.default; } }));


/***/ }),

/***/ 576:
/***/ ((__unused_webpack_module, exports) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports["default"] = 'ffffffff-ffff-ffff-ffff-ffffffffffff';


/***/ }),

/***/ 934:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
const crypto_1 = __nccwpck_require__(982);
function md5(bytes) {
    if (Array.isArray(bytes)) {
        bytes = Buffer.from(bytes);
    }
    else if (typeof bytes === 'string') {
        bytes = Buffer.from(bytes, 'utf8');
    }
    return (0, crypto_1.createHash)('md5').update(bytes).digest();
}
exports["default"] = md5;


/***/ }),

/***/ 831:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
const crypto_1 = __nccwpck_require__(982);
exports["default"] = { randomUUID: crypto_1.randomUUID };


/***/ }),

/***/ 805:
/***/ ((__unused_webpack_module, exports) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports["default"] = '00000000-0000-0000-0000-000000000000';


/***/ }),

/***/ 713:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
const validate_js_1 = __nccwpck_require__(182);
function parse(uuid) {
    if (!(0, validate_js_1.default)(uuid)) {
        throw TypeError('Invalid UUID');
    }
    let v;
    return Uint8Array.of((v = parseInt(uuid.slice(0, 8), 16)) >>> 24, (v >>> 16) & 0xff, (v >>> 8) & 0xff, v & 0xff, (v = parseInt(uuid.slice(9, 13), 16)) >>> 8, v & 0xff, (v = parseInt(uuid.slice(14, 18), 16)) >>> 8, v & 0xff, (v = parseInt(uuid.slice(19, 23), 16)) >>> 8, v & 0xff, ((v = parseInt(uuid.slice(24, 36), 16)) / 0x10000000000) & 0xff, (v / 0x100000000) & 0xff, (v >>> 24) & 0xff, (v >>> 16) & 0xff, (v >>> 8) & 0xff, v & 0xff);
}
exports["default"] = parse;


/***/ }),

/***/ 997:
/***/ ((__unused_webpack_module, exports) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports["default"] = /^(?:[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}|00000000-0000-0000-0000-000000000000|ffffffff-ffff-ffff-ffff-ffffffffffff)$/i;


/***/ }),

/***/ 983:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
const crypto_1 = __nccwpck_require__(982);
const rnds8Pool = new Uint8Array(256);
let poolPtr = rnds8Pool.length;
function rng() {
    if (poolPtr > rnds8Pool.length - 16) {
        (0, crypto_1.randomFillSync)(rnds8Pool);
        poolPtr = 0;
    }
    return rnds8Pool.slice(poolPtr, (poolPtr += 16));
}
exports["default"] = rng;


/***/ }),

/***/ 449:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
const crypto_1 = __nccwpck_require__(982);
function sha1(bytes) {
    if (Array.isArray(bytes)) {
        bytes = Buffer.from(bytes);
    }
    else if (typeof bytes === 'string') {
        bytes = Buffer.from(bytes, 'utf8');
    }
    return (0, crypto_1.createHash)('sha1').update(bytes).digest();
}
exports["default"] = sha1;


/***/ }),

/***/ 687:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.unsafeStringify = void 0;
const validate_js_1 = __nccwpck_require__(182);
const byteToHex = [];
for (let i = 0; i < 256; ++i) {
    byteToHex.push((i + 0x100).toString(16).slice(1));
}
function unsafeStringify(arr, offset = 0) {
    return (byteToHex[arr[offset + 0]] +
        byteToHex[arr[offset + 1]] +
        byteToHex[arr[offset + 2]] +
        byteToHex[arr[offset + 3]] +
        '-' +
        byteToHex[arr[offset + 4]] +
        byteToHex[arr[offset + 5]] +
        '-' +
        byteToHex[arr[offset + 6]] +
        byteToHex[arr[offset + 7]] +
        '-' +
        byteToHex[arr[offset + 8]] +
        byteToHex[arr[offset + 9]] +
        '-' +
        byteToHex[arr[offset + 10]] +
        byteToHex[arr[offset + 11]] +
        byteToHex[arr[offset + 12]] +
        byteToHex[arr[offset + 13]] +
        byteToHex[arr[offset + 14]] +
        byteToHex[arr[offset + 15]]).toLowerCase();
}
exports.unsafeStringify = unsafeStringify;
function stringify(arr, offset = 0) {
    const uuid = unsafeStringify(arr, offset);
    if (!(0, validate_js_1.default)(uuid)) {
        throw TypeError('Stringified UUID is invalid');
    }
    return uuid;
}
exports["default"] = stringify;


/***/ }),

/***/ 597:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.updateV1State = void 0;
const rng_js_1 = __nccwpck_require__(983);
const stringify_js_1 = __nccwpck_require__(687);
const _state = {};
function v1(options, buf, offset) {
    let bytes;
    const isV6 = options?._v6 ?? false;
    if (options) {
        const optionsKeys = Object.keys(options);
        if (optionsKeys.length === 1 && optionsKeys[0] === '_v6') {
            options = undefined;
        }
    }
    if (options) {
        bytes = v1Bytes(options.random ?? options.rng?.() ?? (0, rng_js_1.default)(), options.msecs, options.nsecs, options.clockseq, options.node, buf, offset);
    }
    else {
        const now = Date.now();
        const rnds = (0, rng_js_1.default)();
        updateV1State(_state, now, rnds);
        bytes = v1Bytes(rnds, _state.msecs, _state.nsecs, isV6 ? undefined : _state.clockseq, isV6 ? undefined : _state.node, buf, offset);
    }
    return buf ?? (0, stringify_js_1.unsafeStringify)(bytes);
}
function updateV1State(state, now, rnds) {
    state.msecs ??= -Infinity;
    state.nsecs ??= 0;
    if (now === state.msecs) {
        state.nsecs++;
        if (state.nsecs >= 10000) {
            state.node = undefined;
            state.nsecs = 0;
        }
    }
    else if (now > state.msecs) {
        state.nsecs = 0;
    }
    else if (now < state.msecs) {
        state.node = undefined;
    }
    if (!state.node) {
        state.node = rnds.slice(10, 16);
        state.node[0] |= 0x01;
        state.clockseq = ((rnds[8] << 8) | rnds[9]) & 0x3fff;
    }
    state.msecs = now;
    return state;
}
exports.updateV1State = updateV1State;
function v1Bytes(rnds, msecs, nsecs, clockseq, node, buf, offset = 0) {
    if (rnds.length < 16) {
        throw new Error('Random bytes length must be >= 16');
    }
    if (!buf) {
        buf = new Uint8Array(16);
        offset = 0;
    }
    else {
        if (offset < 0 || offset + 16 > buf.length) {
            throw new RangeError(`UUID byte range ${offset}:${offset + 15} is out of buffer bounds`);
        }
    }
    msecs ??= Date.now();
    nsecs ??= 0;
    clockseq ??= ((rnds[8] << 8) | rnds[9]) & 0x3fff;
    node ??= rnds.slice(10, 16);
    msecs += 12219292800000;
    const tl = ((msecs & 0xfffffff) * 10000 + nsecs) % 0x100000000;
    buf[offset++] = (tl >>> 24) & 0xff;
    buf[offset++] = (tl >>> 16) & 0xff;
    buf[offset++] = (tl >>> 8) & 0xff;
    buf[offset++] = tl & 0xff;
    const tmh = ((msecs / 0x100000000) * 10000) & 0xfffffff;
    buf[offset++] = (tmh >>> 8) & 0xff;
    buf[offset++] = tmh & 0xff;
    buf[offset++] = ((tmh >>> 24) & 0xf) | 0x10;
    buf[offset++] = (tmh >>> 16) & 0xff;
    buf[offset++] = (clockseq >>> 8) | 0x80;
    buf[offset++] = clockseq & 0xff;
    for (let n = 0; n < 6; ++n) {
        buf[offset++] = node[n];
    }
    return buf;
}
exports["default"] = v1;


/***/ }),

/***/ 92:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
const parse_js_1 = __nccwpck_require__(713);
const stringify_js_1 = __nccwpck_require__(687);
function v1ToV6(uuid) {
    const v1Bytes = typeof uuid === 'string' ? (0, parse_js_1.default)(uuid) : uuid;
    const v6Bytes = _v1ToV6(v1Bytes);
    return typeof uuid === 'string' ? (0, stringify_js_1.unsafeStringify)(v6Bytes) : v6Bytes;
}
exports["default"] = v1ToV6;
function _v1ToV6(v1Bytes) {
    return Uint8Array.of(((v1Bytes[6] & 0x0f) << 4) | ((v1Bytes[7] >> 4) & 0x0f), ((v1Bytes[7] & 0x0f) << 4) | ((v1Bytes[4] & 0xf0) >> 4), ((v1Bytes[4] & 0x0f) << 4) | ((v1Bytes[5] & 0xf0) >> 4), ((v1Bytes[5] & 0x0f) << 4) | ((v1Bytes[0] & 0xf0) >> 4), ((v1Bytes[0] & 0x0f) << 4) | ((v1Bytes[1] & 0xf0) >> 4), ((v1Bytes[1] & 0x0f) << 4) | ((v1Bytes[2] & 0xf0) >> 4), 0x60 | (v1Bytes[2] & 0x0f), v1Bytes[3], v1Bytes[8], v1Bytes[9], v1Bytes[10], v1Bytes[11], v1Bytes[12], v1Bytes[13], v1Bytes[14], v1Bytes[15]);
}


/***/ }),

/***/ 691:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.URL = exports.DNS = void 0;
const md5_js_1 = __nccwpck_require__(934);
const v35_js_1 = __nccwpck_require__(352);
var v35_js_2 = __nccwpck_require__(352);
Object.defineProperty(exports, "DNS", ({ enumerable: true, get: function () { return v35_js_2.DNS; } }));
Object.defineProperty(exports, "URL", ({ enumerable: true, get: function () { return v35_js_2.URL; } }));
function v3(value, namespace, buf, offset) {
    return (0, v35_js_1.default)(0x30, md5_js_1.default, value, namespace, buf, offset);
}
v3.DNS = v35_js_1.DNS;
v3.URL = v35_js_1.URL;
exports["default"] = v3;


/***/ }),

/***/ 352:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.URL = exports.DNS = exports.stringToBytes = void 0;
const parse_js_1 = __nccwpck_require__(713);
const stringify_js_1 = __nccwpck_require__(687);
function stringToBytes(str) {
    str = unescape(encodeURIComponent(str));
    const bytes = new Uint8Array(str.length);
    for (let i = 0; i < str.length; ++i) {
        bytes[i] = str.charCodeAt(i);
    }
    return bytes;
}
exports.stringToBytes = stringToBytes;
exports.DNS = '6ba7b810-9dad-11d1-80b4-00c04fd430c8';
exports.URL = '6ba7b811-9dad-11d1-80b4-00c04fd430c8';
function v35(version, hash, value, namespace, buf, offset) {
    const valueBytes = typeof value === 'string' ? stringToBytes(value) : value;
    const namespaceBytes = typeof namespace === 'string' ? (0, parse_js_1.default)(namespace) : namespace;
    if (typeof namespace === 'string') {
        namespace = (0, parse_js_1.default)(namespace);
    }
    if (namespace?.length !== 16) {
        throw TypeError('Namespace must be array-like (16 iterable integer values, 0-255)');
    }
    let bytes = new Uint8Array(16 + valueBytes.length);
    bytes.set(namespaceBytes);
    bytes.set(valueBytes, namespaceBytes.length);
    bytes = hash(bytes);
    bytes[6] = (bytes[6] & 0x0f) | version;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    if (buf) {
        offset = offset || 0;
        if (offset < 0 || offset + 16 > buf.length) {
            throw new RangeError(`UUID byte range ${offset}:${offset + 15} is out of buffer bounds`);
        }
        for (let i = 0; i < 16; ++i) {
            buf[offset + i] = bytes[i];
        }
        return buf;
    }
    return (0, stringify_js_1.unsafeStringify)(bytes);
}
exports["default"] = v35;


/***/ }),

/***/ 834:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
const native_js_1 = __nccwpck_require__(831);
const rng_js_1 = __nccwpck_require__(983);
const stringify_js_1 = __nccwpck_require__(687);
function v4(options, buf, offset) {
    if (native_js_1.default.randomUUID && !buf && !options) {
        return native_js_1.default.randomUUID();
    }
    options = options || {};
    const rnds = options.random ?? options.rng?.() ?? (0, rng_js_1.default)();
    if (rnds.length < 16) {
        throw new Error('Random bytes length must be >= 16');
    }
    rnds[6] = (rnds[6] & 0x0f) | 0x40;
    rnds[8] = (rnds[8] & 0x3f) | 0x80;
    if (buf) {
        offset = offset || 0;
        if (offset < 0 || offset + 16 > buf.length) {
            throw new RangeError(`UUID byte range ${offset}:${offset + 15} is out of buffer bounds`);
        }
        for (let i = 0; i < 16; ++i) {
            buf[offset + i] = rnds[i];
        }
        return buf;
    }
    return (0, stringify_js_1.unsafeStringify)(rnds);
}
exports["default"] = v4;


/***/ }),

/***/ 465:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.URL = exports.DNS = void 0;
const sha1_js_1 = __nccwpck_require__(449);
const v35_js_1 = __nccwpck_require__(352);
var v35_js_2 = __nccwpck_require__(352);
Object.defineProperty(exports, "DNS", ({ enumerable: true, get: function () { return v35_js_2.DNS; } }));
Object.defineProperty(exports, "URL", ({ enumerable: true, get: function () { return v35_js_2.URL; } }));
function v5(value, namespace, buf, offset) {
    return (0, v35_js_1.default)(0x50, sha1_js_1.default, value, namespace, buf, offset);
}
v5.DNS = v35_js_1.DNS;
v5.URL = v35_js_1.URL;
exports["default"] = v5;


/***/ }),

/***/ 544:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
const stringify_js_1 = __nccwpck_require__(687);
const v1_js_1 = __nccwpck_require__(597);
const v1ToV6_js_1 = __nccwpck_require__(92);
function v6(options, buf, offset) {
    options ??= {};
    offset ??= 0;
    let bytes = (0, v1_js_1.default)({ ...options, _v6: true }, new Uint8Array(16));
    bytes = (0, v1ToV6_js_1.default)(bytes);
    if (buf) {
        if (offset < 0 || offset + 16 > buf.length) {
            throw new RangeError(`UUID byte range ${offset}:${offset + 15} is out of buffer bounds`);
        }
        for (let i = 0; i < 16; i++) {
            buf[offset + i] = bytes[i];
        }
        return buf;
    }
    return (0, stringify_js_1.unsafeStringify)(bytes);
}
exports["default"] = v6;


/***/ }),

/***/ 104:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
const parse_js_1 = __nccwpck_require__(713);
const stringify_js_1 = __nccwpck_require__(687);
function v6ToV1(uuid) {
    const v6Bytes = typeof uuid === 'string' ? (0, parse_js_1.default)(uuid) : uuid;
    const v1Bytes = _v6ToV1(v6Bytes);
    return typeof uuid === 'string' ? (0, stringify_js_1.unsafeStringify)(v1Bytes) : v1Bytes;
}
exports["default"] = v6ToV1;
function _v6ToV1(v6Bytes) {
    return Uint8Array.of(((v6Bytes[3] & 0x0f) << 4) | ((v6Bytes[4] >> 4) & 0x0f), ((v6Bytes[4] & 0x0f) << 4) | ((v6Bytes[5] & 0xf0) >> 4), ((v6Bytes[5] & 0x0f) << 4) | (v6Bytes[6] & 0x0f), v6Bytes[7], ((v6Bytes[1] & 0x0f) << 4) | ((v6Bytes[2] & 0xf0) >> 4), ((v6Bytes[2] & 0x0f) << 4) | ((v6Bytes[3] & 0xf0) >> 4), 0x10 | ((v6Bytes[0] & 0xf0) >> 4), ((v6Bytes[0] & 0x0f) << 4) | ((v6Bytes[1] & 0xf0) >> 4), v6Bytes[8], v6Bytes[9], v6Bytes[10], v6Bytes[11], v6Bytes[12], v6Bytes[13], v6Bytes[14], v6Bytes[15]);
}


/***/ }),

/***/ 631:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.updateV7State = void 0;
const rng_js_1 = __nccwpck_require__(983);
const stringify_js_1 = __nccwpck_require__(687);
const _state = {};
function v7(options, buf, offset) {
    let bytes;
    if (options) {
        bytes = v7Bytes(options.random ?? options.rng?.() ?? (0, rng_js_1.default)(), options.msecs, options.seq, buf, offset);
    }
    else {
        const now = Date.now();
        const rnds = (0, rng_js_1.default)();
        updateV7State(_state, now, rnds);
        bytes = v7Bytes(rnds, _state.msecs, _state.seq, buf, offset);
    }
    return buf ?? (0, stringify_js_1.unsafeStringify)(bytes);
}
function updateV7State(state, now, rnds) {
    state.msecs ??= -Infinity;
    state.seq ??= 0;
    if (now > state.msecs) {
        state.seq = (rnds[6] << 23) | (rnds[7] << 16) | (rnds[8] << 8) | rnds[9];
        state.msecs = now;
    }
    else {
        state.seq = (state.seq + 1) | 0;
        if (state.seq === 0) {
            state.msecs++;
        }
    }
    return state;
}
exports.updateV7State = updateV7State;
function v7Bytes(rnds, msecs, seq, buf, offset = 0) {
    if (rnds.length < 16) {
        throw new Error('Random bytes length must be >= 16');
    }
    if (!buf) {
        buf = new Uint8Array(16);
        offset = 0;
    }
    else {
        if (offset < 0 || offset + 16 > buf.length) {
            throw new RangeError(`UUID byte range ${offset}:${offset + 15} is out of buffer bounds`);
        }
    }
    msecs ??= Date.now();
    seq ??= ((rnds[6] * 0x7f) << 24) | (rnds[7] << 16) | (rnds[8] << 8) | rnds[9];
    buf[offset++] = (msecs / 0x10000000000) & 0xff;
    buf[offset++] = (msecs / 0x100000000) & 0xff;
    buf[offset++] = (msecs / 0x1000000) & 0xff;
    buf[offset++] = (msecs / 0x10000) & 0xff;
    buf[offset++] = (msecs / 0x100) & 0xff;
    buf[offset++] = msecs & 0xff;
    buf[offset++] = 0x70 | ((seq >>> 28) & 0x0f);
    buf[offset++] = (seq >>> 20) & 0xff;
    buf[offset++] = 0x80 | ((seq >>> 14) & 0x3f);
    buf[offset++] = (seq >>> 6) & 0xff;
    buf[offset++] = ((seq << 2) & 0xff) | (rnds[10] & 0x03);
    buf[offset++] = rnds[11];
    buf[offset++] = rnds[12];
    buf[offset++] = rnds[13];
    buf[offset++] = rnds[14];
    buf[offset++] = rnds[15];
    return buf;
}
exports["default"] = v7;


/***/ }),

/***/ 182:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
const regex_js_1 = __nccwpck_require__(997);
function validate(uuid) {
    return typeof uuid === 'string' && regex_js_1.default.test(uuid);
}
exports["default"] = validate;


/***/ }),

/***/ 302:
/***/ ((__unused_webpack_module, exports, __nccwpck_require__) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
const validate_js_1 = __nccwpck_require__(182);
function version(uuid) {
    if (!(0, validate_js_1.default)(uuid)) {
        throw TypeError('Invalid UUID');
    }
    return parseInt(uuid.slice(14, 15), 16);
}
exports["default"] = version;


/***/ })

/******/ 	});
/************************************************************************/
/******/ 	// The module cache
/******/ 	var __webpack_module_cache__ = {};
/******/ 	
/******/ 	// The require function
/******/ 	function __nccwpck_require__(moduleId) {
/******/ 		// Check if module is in cache
/******/ 		var cachedModule = __webpack_module_cache__[moduleId];
/******/ 		if (cachedModule !== undefined) {
/******/ 			return cachedModule.exports;
/******/ 		}
/******/ 		// Create a new module (and put it into the cache)
/******/ 		var module = __webpack_module_cache__[moduleId] = {
/******/ 			// no module.id needed
/******/ 			// no module.loaded needed
/******/ 			exports: {}
/******/ 		};
/******/ 	
/******/ 		// Execute the module function
/******/ 		var threw = true;
/******/ 		try {
/******/ 			__webpack_modules__[moduleId].call(module.exports, module, module.exports, __nccwpck_require__);
/******/ 			threw = false;
/******/ 		} finally {
/******/ 			if(threw) delete __webpack_module_cache__[moduleId];
/******/ 		}
/******/ 	
/******/ 		// Return the exports of the module
/******/ 		return module.exports;
/******/ 	}
/******/ 	
/************************************************************************/
/******/ 	/* webpack/runtime/compat */
/******/ 	
/******/ 	if (typeof __nccwpck_require__ !== 'undefined') __nccwpck_require__.ab = __dirname + "/";
/******/ 	
/************************************************************************/
/******/ 	
/******/ 	// startup
/******/ 	// Load entry module and return exports
/******/ 	// This entry module is referenced by other modules so it can't be inlined
/******/ 	var __webpack_exports__ = __nccwpck_require__(137);
/******/ 	module.exports = __webpack_exports__;
/******/ 	
/******/ })()
;