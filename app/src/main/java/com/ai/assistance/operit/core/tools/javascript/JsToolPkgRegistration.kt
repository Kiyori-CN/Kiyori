package com.ai.assistance.operit.core.tools.javascript

import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_REGISTRATION_CHAT_MESSAGE_MENU_ITEM
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_REGISTRATION_CHAT_RUNTIME_HOOK
import org.json.JSONObject
import org.json.JSONTokener

data class ToolPkgMainRegistrationCapture(
    val toolboxUiModules: List<String>,
    val uiRoutes: List<String>,
    val navigationEntries: List<String>,
    val desktopWidgets: List<String>,
    val appLifecycleHooks: List<String>,
    val messageProcessingPlugins: List<String>,
    val xmlRenderPlugins: List<String>,
    val inputMenuTogglePlugins: List<String>,
    val chatInputHooks: List<String>,
    val chatViewHooks: List<String>,
    val chatMessageHooks: List<String>,
    val chatMessageMenuItems: List<String>,
    val chatRuntimeHooks: List<String>,
    val toolLifecycleHooks: List<String>,
    val promptInputHooks: List<String>,
    val promptHistoryHooks: List<String>,
    val promptEstimateHistoryHooks: List<String>,
    val systemPromptComposeHooks: List<String>,
    val toolPromptComposeHooks: List<String>,
    val promptFinalizeHooks: List<String>,
    val promptEstimateFinalizeHooks: List<String>,
    val summaryGenerateHooks: List<String>,
    val aiProviders: List<String>
)

private enum class RegistrationBucket(
    val nativeRegistrationName: String? = null
) {
    TOOLBOX_UI,
    UI_ROUTE,
    NAVIGATION_ENTRY,
    DESKTOP_WIDGET,
    APP_LIFECYCLE,
    MESSAGE_PROCESSING,
    XML_RENDER,
    INPUT_MENU_TOGGLE,
    CHAT_INPUT,
    CHAT_VIEW,
    CHAT_MESSAGE,
    CHAT_MESSAGE_MENU_ITEM(TOOLPKG_REGISTRATION_CHAT_MESSAGE_MENU_ITEM),
    CHAT_RUNTIME(TOOLPKG_REGISTRATION_CHAT_RUNTIME_HOOK),
    TOOL_LIFECYCLE,
    PROMPT_INPUT,
    PROMPT_HISTORY,
    PROMPT_ESTIMATE_HISTORY,
    SYSTEM_PROMPT_COMPOSE,
    TOOL_PROMPT_COMPOSE,
    PROMPT_FINALIZE,
    PROMPT_ESTIMATE_FINALIZE,
    SUMMARY_GENERATE,
    AI_PROVIDER
}

internal class JsToolPkgRegistrationSession {
    private val lock = Any()
    private var capture: MutableMap<RegistrationBucket, MutableList<String>>? = null

    fun begin() {
        synchronized(lock) {
            capture = mutableMapOf()
        }
    }

    fun appendToolboxUiModule(specJson: String) = append(RegistrationBucket.TOOLBOX_UI, specJson)
    fun appendUiRoute(specJson: String) = append(RegistrationBucket.UI_ROUTE, specJson)
    fun appendNavigationEntry(specJson: String) = append(RegistrationBucket.NAVIGATION_ENTRY, specJson)
    fun appendDesktopWidget(specJson: String) = append(RegistrationBucket.DESKTOP_WIDGET, specJson)
    fun appendAppLifecycleHook(specJson: String) = append(RegistrationBucket.APP_LIFECYCLE, specJson)
    fun appendMessageProcessingPlugin(specJson: String) =
        append(RegistrationBucket.MESSAGE_PROCESSING, specJson)

    fun appendXmlRenderPlugin(specJson: String) = append(RegistrationBucket.XML_RENDER, specJson)
    fun appendInputMenuTogglePlugin(specJson: String) =
        append(RegistrationBucket.INPUT_MENU_TOGGLE, specJson)

    fun appendChatInputHook(specJson: String) =
        append(RegistrationBucket.CHAT_INPUT, specJson)

    fun appendChatViewHook(specJson: String) =
        append(RegistrationBucket.CHAT_VIEW, specJson)

    fun appendChatMessageHook(specJson: String) =
        append(RegistrationBucket.CHAT_MESSAGE, specJson)

    fun appendChatMessageMenuItem(specJson: String) =
        append(RegistrationBucket.CHAT_MESSAGE_MENU_ITEM, specJson)

    fun appendChatRuntimeHook(specJson: String) =
        append(RegistrationBucket.CHAT_RUNTIME, specJson)

    fun appendToolLifecycleHook(specJson: String) =
        append(RegistrationBucket.TOOL_LIFECYCLE, specJson)

    fun appendPromptInputHook(specJson: String) = append(RegistrationBucket.PROMPT_INPUT, specJson)
    fun appendPromptHistoryHook(specJson: String) =
        append(RegistrationBucket.PROMPT_HISTORY, specJson)
    fun appendPromptEstimateHistoryHook(specJson: String) =
        append(RegistrationBucket.PROMPT_ESTIMATE_HISTORY, specJson)

    fun appendSystemPromptComposeHook(specJson: String) =
        append(RegistrationBucket.SYSTEM_PROMPT_COMPOSE, specJson)

    fun appendToolPromptComposeHook(specJson: String) =
        append(RegistrationBucket.TOOL_PROMPT_COMPOSE, specJson)

    fun appendPromptFinalizeHook(specJson: String) =
        append(RegistrationBucket.PROMPT_FINALIZE, specJson)
    fun appendPromptEstimateFinalizeHook(specJson: String) =
        append(RegistrationBucket.PROMPT_ESTIMATE_FINALIZE, specJson)
    fun appendSummaryGenerateHook(specJson: String) =
        append(RegistrationBucket.SUMMARY_GENERATE, specJson)
    fun appendAiProvider(specJson: String) =
        append(RegistrationBucket.AI_PROVIDER, specJson)

    fun finish(executionResult: Any?): ToolPkgMainRegistrationCapture {
        val errorMessage = extractJsExecutionErrorMessage(executionResult)
        if (errorMessage != null) {
            throw IllegalStateException(errorMessage)
        }
        synchronized(lock) {
            val current = capture.orEmpty()
            fun read(bucket: RegistrationBucket): List<String> = current[bucket]?.toList().orEmpty()
            return ToolPkgMainRegistrationCapture(
                toolboxUiModules = read(RegistrationBucket.TOOLBOX_UI),
                uiRoutes = read(RegistrationBucket.UI_ROUTE),
                navigationEntries = read(RegistrationBucket.NAVIGATION_ENTRY),
                desktopWidgets = read(RegistrationBucket.DESKTOP_WIDGET),
                appLifecycleHooks = read(RegistrationBucket.APP_LIFECYCLE),
                messageProcessingPlugins = read(RegistrationBucket.MESSAGE_PROCESSING),
                xmlRenderPlugins = read(RegistrationBucket.XML_RENDER),
                inputMenuTogglePlugins = read(RegistrationBucket.INPUT_MENU_TOGGLE),
                chatInputHooks = read(RegistrationBucket.CHAT_INPUT),
                chatViewHooks = read(RegistrationBucket.CHAT_VIEW),
                chatMessageHooks = read(RegistrationBucket.CHAT_MESSAGE),
                chatMessageMenuItems = read(RegistrationBucket.CHAT_MESSAGE_MENU_ITEM),
                chatRuntimeHooks = read(RegistrationBucket.CHAT_RUNTIME),
                toolLifecycleHooks = read(RegistrationBucket.TOOL_LIFECYCLE),
                promptInputHooks = read(RegistrationBucket.PROMPT_INPUT),
                promptHistoryHooks = read(RegistrationBucket.PROMPT_HISTORY),
                promptEstimateHistoryHooks = read(RegistrationBucket.PROMPT_ESTIMATE_HISTORY),
                systemPromptComposeHooks = read(RegistrationBucket.SYSTEM_PROMPT_COMPOSE),
                toolPromptComposeHooks = read(RegistrationBucket.TOOL_PROMPT_COMPOSE),
                promptFinalizeHooks = read(RegistrationBucket.PROMPT_FINALIZE),
                promptEstimateFinalizeHooks = read(RegistrationBucket.PROMPT_ESTIMATE_FINALIZE),
                summaryGenerateHooks = read(RegistrationBucket.SUMMARY_GENERATE),
                aiProviders = read(RegistrationBucket.AI_PROVIDER)
            )
        }
    }

    fun end() {
        synchronized(lock) {
            capture = null
        }
    }

    private fun append(
        bucket: RegistrationBucket,
        specJson: String
    ) {
        val normalized = normalizeRegistrationSpec(specJson)
        synchronized(lock) {
            val target = capture ?: error("toolpkg registration session is not active")
            target.getOrPut(bucket) { mutableListOf() }.add(normalized)
        }
    }

    private fun normalizeRegistrationSpec(specJson: String): String {
        val trimmed = specJson.trim()
        require(trimmed.isNotEmpty()) { "toolpkg registration payload is empty" }
        val parsed = JSONTokener(trimmed).nextValue()
        require(parsed is JSONObject) { "toolpkg registration payload must be a JSON object" }
        return parsed.toString()
    }
}

internal fun buildToolPkgRegistrationBridgeScript(): String {
    return """
        (function() {
            var root = typeof globalThis !== 'undefined'
                ? globalThis
                : (typeof window !== 'undefined' ? window : this);
            var moduleRefFunctionCounter = 0;
            var openAIWebSearchCallbackCounter = 0;
            var bilibiliCallbackCounter = 0;

            function installGlobal(name, value) {
                var key = String(name || '').trim();
                if (!key || value === undefined) {
                    return;
                }
                try { globalThis[key] = value; } catch (_e) {}
                try { window[key] = value; } catch (_e2) {}
            }

            function requireNative(name) {
                if (
                    typeof NativeInterface === 'undefined' ||
                    !NativeInterface ||
                    typeof NativeInterface[name] !== 'function'
                ) {
                    throw new Error('NativeInterface.' + name + ' is unavailable');
                }
                return NativeInterface[name].bind(NativeInterface);
            }

            function requireToolPkgApiRuntime() {
                var runtime = root.__operitToolPkgApi;
                if (
                    !runtime ||
                    typeof runtime.namespace !== 'function' ||
                    typeof runtime.method !== 'function'
                ) {
                    throw new Error('ToolPkg API runtime is unavailable');
                }
                return runtime;
            }

            function currentExecutionCallId() {
                var callId = String(root.__operitCurrentCallId || '').trim();
                if (!callId) {
                    throw new Error('ToolPkg host service requires an active execution call');
                }
                return callId;
            }

            function nextOpenAIWebSearchCallbackId() {
                openAIWebSearchCallbackCounter += 1;
                return '__operit_openai_web_search_callback_' +
                    Date.now() + '_' + openAIWebSearchCallbackCounter;
            }

            function parseOpenAIWebSearchEnvelope(raw) {
                var parsed;
                try {
                    parsed = JSON.parse(String(raw || ''));
                } catch (error) {
                    throw new Error(
                        'OpenAI Web Search host returned invalid JSON: ' +
                            String(error && error.message ? error.message : error)
                    );
                }
                if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
                    throw new Error('OpenAI Web Search host returned an invalid envelope');
                }
                return parsed;
            }

            function openAIWebSearchError(envelope) {
                var errorPayload =
                    envelope && envelope.error && typeof envelope.error === 'object'
                        ? envelope.error
                        : null;
                var message =
                    errorPayload && typeof errorPayload.message === 'string'
                        ? errorPayload.message
                        : (envelope && typeof envelope.message === 'string'
                            ? envelope.message
                            : 'OpenAI Web Search host operation failed');
                var code =
                    errorPayload && typeof errorPayload.code === 'string'
                        ? errorPayload.code.trim()
                        : '';
                if (code && message.indexOf('[' + code + ']') !== 0) {
                    message = '[' + code + '] ' + message;
                }
                var error = new Error(message);
                if (code) {
                    error.code = code;
                }
                if (
                    errorPayload &&
                    errorPayload.source_diagnostics &&
                    typeof errorPayload.source_diagnostics === 'object'
                ) {
                    error.sourceDiagnostics = errorPayload.source_diagnostics;
                }
                error.details = envelope;
                return error;
            }

            function invokeOpenAIWebSearchAsync(nativeMethodName, args) {
                return new Promise(function(resolve, reject) {
                    var callbackId = nextOpenAIWebSearchCallbackId();
                    root[callbackId] = function(resultJson, isError) {
                        try {
                            delete root[callbackId];
                        } catch (_deleteError) {
                            root[callbackId] = undefined;
                        }
                        if (isError === true) {
                            reject(new Error(String(resultJson || 'OpenAI Web Search host call failed')));
                            return;
                        }
                        try {
                            var envelope = parseOpenAIWebSearchEnvelope(resultJson);
                            if (envelope.success !== true) {
                                reject(openAIWebSearchError(envelope));
                                return;
                            }
                            resolve(envelope);
                        } catch (error) {
                            reject(error);
                        }
                    };
                    try {
                        requireNative(nativeMethodName).apply(
                            null,
                            [currentExecutionCallId(), callbackId].concat(args || [])
                        );
                    } catch (error) {
                        try {
                            delete root[callbackId];
                        } catch (_deleteError2) {
                            root[callbackId] = undefined;
                        }
                        reject(error);
                    }
                });
            }

            function startOpenAIWebSearchRequest(nativeMethodName, args) {
                var resolveResult;
                var rejectResult;
                var resultPromise = new Promise(function(resolve, reject) {
                    resolveResult = resolve;
                    rejectResult = reject;
                });
                var callbackId = nextOpenAIWebSearchCallbackId();
                root[callbackId] = function(resultJson, isError) {
                    try {
                        delete root[callbackId];
                    } catch (_deleteError) {
                        root[callbackId] = undefined;
                    }
                    if (isError === true) {
                        rejectResult(new Error(String(resultJson || 'OpenAI Web Search host call failed')));
                        return;
                    }
                    try {
                        var envelope = parseOpenAIWebSearchEnvelope(resultJson);
                        if (envelope.success !== true) {
                            rejectResult(openAIWebSearchError(envelope));
                            return;
                        }
                        resolveResult(envelope);
                    } catch (error) {
                        rejectResult(error);
                    }
                };
                try {
                    var started = parseOpenAIWebSearchEnvelope(
                        requireNative(nativeMethodName).apply(
                            null,
                            [currentExecutionCallId(), callbackId].concat(args || [])
                        )
                    );
                    if (started.success !== true) {
                        try {
                            delete root[callbackId];
                        } catch (_deleteError2) {
                            root[callbackId] = undefined;
                        }
                        rejectResult(openAIWebSearchError(started));
                        return resultPromise;
                    }
                    Object.defineProperty(resultPromise, 'requestId', {
                        value: String(started.request_id || ''),
                        enumerable: true,
                        configurable: false,
                        writable: false
                    });
                } catch (error) {
                    try {
                        delete root[callbackId];
                    } catch (_deleteError3) {
                        root[callbackId] = undefined;
                    }
                    rejectResult(error);
                }
                return resultPromise;
            }

            function createOpenAIWebSearchService() {
                return {
                    getStatus: function() {
                        return invokeOpenAIWebSearchAsync('openAIWebSearchGetStatus', []);
                    },
                    validateLocalConfiguration: function() {
                        return invokeOpenAIWebSearchAsync(
                            'openAIWebSearchValidateLocalConfiguration',
                            []
                        );
                    },
                    openConfiguration: function() {
                        var envelope = parseOpenAIWebSearchEnvelope(
                            requireNative('openAIWebSearchOpenConfiguration')(
                                currentExecutionCallId()
                            )
                        );
                        if (envelope.success !== true) {
                            return Promise.reject(openAIWebSearchError(envelope));
                        }
                        return Promise.resolve(envelope);
                    },
                    search: function(request) {
                        var requestJson;
                        try {
                            requestJson = JSON.stringify(request || {});
                        } catch (error) {
                            return Promise.reject(error);
                        }
                        return startOpenAIWebSearchRequest(
                            'openAIWebSearchSearch',
                            [requestJson]
                        );
                    },
                    cancel: function(requestId) {
                        var envelope = parseOpenAIWebSearchEnvelope(
                            requireNative('openAIWebSearchCancel')(
                                currentExecutionCallId(),
                                String(requestId || '').trim()
                            )
                        );
                        if (envelope.success !== true) {
                            return Promise.reject(openAIWebSearchError(envelope));
                        }
                        return Promise.resolve(envelope);
                    },
                    runCompatibilityProbe: function() {
                        return startOpenAIWebSearchRequest(
                            'openAIWebSearchRunCompatibilityProbe',
                            []
                        );
                    }
                };
            }

            function nextBilibiliCallbackId() {
                bilibiliCallbackCounter += 1;
                return '__operit_bilibili_callback_' +
                    Date.now() + '_' + bilibiliCallbackCounter;
            }

            function parseBilibiliEnvelope(raw) {
                var parsed;
                try {
                    parsed = JSON.parse(String(raw || ''));
                } catch (error) {
                    throw new Error(
                        'Bilibili host returned invalid JSON: ' +
                            String(error && error.message ? error.message : error)
                    );
                }
                if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
                    throw new Error('Bilibili host returned an invalid envelope');
                }
                return parsed;
            }

            function bilibiliError(envelope) {
                var payload =
                    envelope && envelope.error && typeof envelope.error === 'object'
                        ? envelope.error
                        : null;
                var code =
                    payload && typeof payload.code === 'string'
                        ? payload.code.trim()
                        : '';
                var message =
                    payload && typeof payload.message === 'string'
                        ? payload.message
                        : 'Bilibili host operation failed';
                var error = new Error(code ? '[' + code + '] ' + message : message);
                if (code) {
                    error.code = code;
                }
                error.details = envelope;
                return error;
            }

            function invokeBilibiliRequest(request) {
                return new Promise(function(resolve, reject) {
                    var callbackId = nextBilibiliCallbackId();
                    root[callbackId] = function(resultJson, isError) {
                        try {
                            delete root[callbackId];
                        } catch (_deleteError) {
                            root[callbackId] = undefined;
                        }
                        if (isError === true) {
                            reject(new Error(String(resultJson || 'Bilibili host call failed')));
                            return;
                        }
                        try {
                            var envelope = parseBilibiliEnvelope(resultJson);
                            if (envelope.success !== true) {
                                reject(bilibiliError(envelope));
                                return;
                            }
                            resolve(envelope);
                        } catch (error) {
                            reject(error);
                        }
                    };
                    try {
                        var requestJson = JSON.stringify(request || {});
                        requireNative('bilibiliRequest')(
                            currentExecutionCallId(),
                            callbackId,
                            requestJson
                        );
                    } catch (error) {
                        try {
                            delete root[callbackId];
                        } catch (_deleteError2) {
                            root[callbackId] = undefined;
                        }
                        reject(error);
                    }
                });
            }

            function createBilibiliService() {
                return {
                    get: function(request) {
                        return invokeBilibiliRequest(request);
                    }
                };
            }

            function copyObject(source, excludedKey) {
                var output = {};
                var keys = Object.keys(source || {});
                for (var i = 0; i < keys.length; i += 1) {
                    var key = keys[i];
                    if (key !== excludedKey) {
                        output[key] = source[key];
                    }
                }
                return output;
            }

            function getActiveExports() {
                return typeof root.__operitGetActiveModuleExports === 'function'
                    ? root.__operitGetActiveModuleExports()
                    : null;
            }

            function resolveExportedFunctionName(fn) {
                var exportsRef = getActiveExports();
                if (!exportsRef || typeof exportsRef !== 'object') {
                    return '';
                }
                var keys = Object.keys(exportsRef);
                for (var i = 0; i < keys.length; i += 1) {
                    if (exportsRef[keys[i]] === fn) {
                        return keys[i];
                    }
                }
                return '';
            }

            function buildGeneratedFunctionName(definition) {
                moduleRefFunctionCounter += 1;
                var rawId = String((definition && definition.id) || 'hook');
                var safeId = rawId.replace(/[^a-zA-Z0-9_$]/g, '_') || 'hook';
                return '__operit_module_ref_hook_' + safeId + '_' + moduleRefFunctionCounter;
            }

            function activeModulePath() {
                var exportsRef = getActiveExports();
                if (!exportsRef || typeof exportsRef !== 'object') {
                    return '';
                }
                return typeof exportsRef.__operit_toolpkg_module_path === 'string'
                    ? exportsRef.__operit_toolpkg_module_path.trim().replace(/\\/g, '/')
                    : '';
            }

            function dirname(path) {
                var normalized = String(path || '').replace(/\\/g, '/');
                var slash = normalized.lastIndexOf('/');
                return slash >= 0 ? normalized.slice(0, slash) : '';
            }

            function relativeRequirePath(fromModulePath, targetModulePath) {
                var fromDir = dirname(fromModulePath);
                var target = String(targetModulePath || '').replace(/\\/g, '/');
                if (!fromDir) {
                    return './' + target;
                }
                var fromParts = fromDir.split('/').filter(Boolean);
                var targetParts = target.split('/').filter(Boolean);
                while (fromParts.length > 0 && targetParts.length > 0 && fromParts[0] === targetParts[0]) {
                    fromParts.shift();
                    targetParts.shift();
                }
                var up = fromParts.map(function() { return '..'; });
                var parts = up.concat(targetParts);
                var rel = parts.join('/');
                return rel.startsWith('.') ? rel : './' + rel;
            }

            function buildModuleRefFunctionSource(requirePath, exportName) {
                return 'function() {' +
                    'var moduleRef = require(' + JSON.stringify(requirePath) + ');' +
                    'var fn = moduleRef && moduleRef[' + JSON.stringify(exportName) + '];' +
                    'if (typeof fn !== "function") {' +
                        'throw new Error("ToolPkg registered function export not found: ' + exportName.replace(/"/g, '\\"') + '");' +
                    '}' +
                    'return fn.apply(null, arguments);' +
                '}';
            }

            function resolveDurableFunctionRef(fn, definition, label) {
                var exportedName = resolveExportedFunctionName(fn);
                if (exportedName) {
                    return {
                        name: exportedName,
                        source: ''
                    };
                }
                var modulePath = typeof fn.__operit_toolpkg_module_path === 'string'
                    ? fn.__operit_toolpkg_module_path.trim().replace(/\\/g, '/')
                    : '';
                var exportName = typeof fn.__operit_toolpkg_export_name === 'string'
                    ? fn.__operit_toolpkg_export_name.trim()
                    : '';
                if (!modulePath || !exportName) {
                    throw new Error(label + ' function must be exported from a toolpkg module');
                }
                var fromModulePath = activeModulePath();
                var functionName = buildGeneratedFunctionName(definition);
                return {
                    name: functionName,
                    source: buildModuleRefFunctionSource(relativeRequirePath(fromModulePath, modulePath), exportName)
                };
            }

            function normalizeFunctionField(definition, fieldName, label) {
                if (!definition || typeof definition !== 'object' || Array.isArray(definition)) {
                    throw new Error(label + ' expects an object');
                }
                var normalized = copyObject(definition, fieldName);
                var fn = definition[fieldName];
                if (typeof fn !== 'function') {
                    throw new Error(label + ' requires a function reference');
                }
                var functionRef = resolveDurableFunctionRef(fn, definition, label);
                normalized[fieldName] = functionRef.name;
                if (functionRef.source) {
                    normalized.function_source = functionRef.source;
                }
                return normalized;
            }

            function normalizeNestedFunctionField(definition, fieldName, label) {
                if (!definition || typeof definition !== 'object' || Array.isArray(definition)) {
                    throw new Error(label + ' expects an object');
                }
                var fieldValue = definition[fieldName];
                if (!fieldValue || typeof fieldValue !== 'object' || Array.isArray(fieldValue)) {
                    throw new Error(label + ' requires an object field: ' + fieldName);
                }
                var fn = fieldValue.function;
                if (typeof fn !== 'function') {
                    throw new Error(label + '.' + fieldName + '.function must be a function reference');
                }
                var functionRef = resolveDurableFunctionRef(fn, {
                    id: String((definition && definition.id) || 'provider') + '_' + fieldName
                }, label + '.' + fieldName);
                var normalizedField = copyObject(fieldValue, 'function');
                normalizedField.function = functionRef.name;
                if (functionRef.source) {
                    normalizedField.function_source = functionRef.source;
                }
                return normalizedField;
            }

            function normalizeAiProviderDefinition(definition, label) {
                var normalized = copyObject(definition, '');
                [
                    'listModels',
                    'sendMessage',
                    'testConnection',
                    'calculateInputTokens'
                ].forEach(function(fieldName) {
                    normalized[fieldName] = normalizeNestedFunctionField(definition, fieldName, label);
                });
                return normalized;
            }

            function normalizeDialogScreenField(dialogDefinition, label) {
                var normalizedDialog = normalizeScreenField(dialogDefinition, label);
                return normalizedDialog;
            }

            function normalizeScreenField(definition, label) {
                if (!definition || typeof definition !== 'object' || Array.isArray(definition)) {
                    throw new Error(label + ' expects an object');
                }
                var normalized = copyObject(definition, 'screen');
                var screen = definition.screen;
                var path = '';
                if (typeof screen === 'string') {
                    path = screen.trim().replace(/\\/g, '/');
                } else if (typeof screen === 'function' && typeof screen.__operit_toolpkg_module_path === 'string') {
                    path = screen.__operit_toolpkg_module_path.trim().replace(/\\/g, '/');
                } else if (
                    screen &&
                    typeof screen === 'object' &&
                    typeof screen.default === 'function' &&
                    typeof screen.default.__operit_toolpkg_module_path === 'string'
                ) {
                    path = screen.default.__operit_toolpkg_module_path.trim().replace(/\\/g, '/');
                }
                if (!path) {
                    throw new Error(label + ' requires a serializable screen reference');
                }
                normalized.screen = path;
                return normalized;
            }

            function normalizeChatMessageMenuItemDefinition(definition, label) {
                var normalized = normalizeFunctionField(definition, 'function', label);
                if (definition.dialog !== undefined && definition.dialog !== null) {
                    if (typeof definition.dialog !== 'object' || Array.isArray(definition.dialog)) {
                        throw new Error(label + '.dialog expects an object');
                    }
                    normalized.dialog = normalizeDialogScreenField(definition.dialog, label + '.dialog');
                }
                return normalized;
            }

            function registerWithNative(definition, label, nativeMethod, fieldName) {
                var normalized = fieldName
                    ? normalizeFunctionField(definition, fieldName, label)
                    : normalizeScreenField(definition, label);
                requireNative(nativeMethod)(JSON.stringify(normalized));
            }

            function registerNormalizedWithNative(definition, label, nativeMethod, normalizer) {
                var normalized = normalizer(definition, label);
                requireNative(nativeMethod)(JSON.stringify(normalized));
            }

            function normalizeNavigationEntryDefinition(definition, label) {
                if (!definition || typeof definition !== 'object' || Array.isArray(definition)) {
                    throw new Error(label + ' expects an object');
                }
                if (typeof definition.action === 'function') {
                    return normalizeFunctionField(definition, 'action', label);
                }
                return copyObject(definition, '');
            }

            function resolveCurrentCallParams() {
                var callId = String(root.__operitCurrentCallId || '').trim();
                var callState =
                    callId && typeof root.__operitGetCallState === 'function'
                        ? root.__operitGetCallState(callId)
                        : null;
                var params =
                    callState && callState.params && typeof callState.params === 'object'
                        ? callState.params
                        : null;
                return params;
            }

            function resolveCurrentToolPkgTarget() {
                var params = resolveCurrentCallParams();
                if (!params) {
                    return '';
                }
                var candidates = [
                    params.__operit_ui_package_name,
                    params.toolPkgId,
                    params.containerPackageName,
                    params.__operit_toolpkg_subpackage_id,
                    params.__operit_package_name
                ];
                for (var i = 0; i < candidates.length; i += 1) {
                    var value = String(candidates[i] || '').trim();
                    if (value) {
                        return value;
                    }
                }
                return '';
            }

            function buildFunctionRegistration(apiName, nativeMethod) {
                return function(definition) {
                    registerWithNative(definition, apiName, nativeMethod, 'function');
                };
            }

            function buildCustomRegistration(apiName, nativeMethod, normalizer) {
                return function(definition) {
                    registerNormalizedWithNative(definition, apiName, nativeMethod, normalizer);
                };
            }

            function apiMethod(apiName, nativeMethod, normalizer) {
                return {
                    apiName: apiName,
                    nativeMethod: nativeMethod,
                    normalizer: normalizer || null
                };
            }

            function installFunctionRegistration(entry) {
                var apiName = entry.apiName;
                var nativeMethod = entry.nativeMethod;
                var registration = entry.normalizer
                    ? buildCustomRegistration(apiName, nativeMethod, entry.normalizer)
                    : buildFunctionRegistration(apiName, nativeMethod);
                api[apiName] = registration;
            }

            function readToolPkgResource(key, outputFileName, internal) {
                var resourceKey = String(key || '').trim();
                if (!resourceKey) {
                    return Promise.reject(new Error('resource key is required'));
                }
                var target = resolveCurrentToolPkgTarget();
                if (!target) {
                    return Promise.reject(new Error('package/toolpkg runtime target is empty'));
                }
                var path = requireNative('readToolPkgResource')(
                    target,
                    resourceKey,
                    outputFileName == null ? '' : String(outputFileName).trim(),
                    internal === true ? 'true' : ''
                );
                if (typeof path === 'string' && path.trim()) {
                    return Promise.resolve(path);
                }
                return Promise.reject(new Error('resource not found: ' + resourceKey));
            }

            function getToolPkgConfigDir(pluginId) {
                var explicitId = String(pluginId || '').trim();
                var target = explicitId || resolveCurrentToolPkgTarget();
                if (!target) {
                    throw new Error('package/toolpkg runtime target is empty');
                }
                var path = requireNative('getPluginConfigDir')(target);
                if (typeof path === 'string' && path.trim()) {
                    return path;
                }
                throw new Error('plugin config dir is unavailable for ' + target);
            }

            function parseStorageResponse(raw, operation) {
                var parsed;
                try {
                    parsed = JSON.parse(String(raw || ''));
                } catch (error) {
                    throw new Error(
                        'ToolPkg storage returned invalid JSON for ' +
                            operation +
                            ': ' +
                            String(error && error.message ? error.message : error)
                    );
                }
                if (!parsed || parsed.success !== true) {
                    throw new Error(
                        parsed && typeof parsed.message === 'string' && parsed.message.trim()
                            ? parsed.message.trim()
                            : 'ToolPkg storage operation failed: ' + operation
                    );
                }
                return parsed;
            }

            function createStorageNamespace(namespace) {
                return {
                    writeText: function(relativePath, text) {
                        var raw = requireNative('writeToolPkgStorageText')(
                            namespace,
                            String(relativePath || ''),
                            String(text == null ? '' : text)
                        );
                        parseStorageResponse(raw, namespace + '.writeText');
                        return Promise.resolve();
                    },
                    readText: function(relativePath) {
                        var raw = requireNative('readToolPkgStorageText')(
                            namespace,
                            String(relativePath || '')
                        );
                        var result = parseStorageResponse(raw, namespace + '.readText');
                        return Promise.resolve(result.exists === true ? String(result.text || '') : null);
                    },
                    writeJson: function(relativePath, value) {
                        var serialized;
                        try {
                            serialized = JSON.stringify(value);
                        } catch (error) {
                            return Promise.reject(error);
                        }
                        if (serialized === undefined) {
                            return Promise.reject(
                                new Error('ToolPkg storage JSON value is not serializable')
                            );
                        }
                        return this.writeText(relativePath, serialized);
                    },
                    readJson: function(relativePath) {
                        return this.readText(relativePath).then(function(text) {
                            if (text === null) {
                                return null;
                            }
                            return JSON.parse(text);
                        });
                    },
                    exists: function(relativePath) {
                        var raw = requireNative('toolPkgStorageFileExists')(
                            namespace,
                            String(relativePath || '')
                        );
                        var result = parseStorageResponse(raw, namespace + '.exists');
                        return Promise.resolve(result.exists === true);
                    },
                    delete: function(relativePath) {
                        var raw = requireNative('deleteToolPkgStorageFile')(
                            namespace,
                            String(relativePath || '')
                        );
                        var result = parseStorageResponse(raw, namespace + '.delete');
                        return Promise.resolve(result.deleted === true);
                    }
                };
            }

            function createToolPkgStorage() {
                return {
                    privateData: createStorageNamespace('privateData'),
                    cache: createStorageNamespace('cache')
                };
            }

            function buildToolPkgArtifact(options) {
                var sourceDirectory =
                    options && typeof options === 'object'
                        ? String(options.sourceDirectory || '').trim()
                        : '';
                if (!sourceDirectory) {
                    return Promise.reject(
                        new Error('ToolPkg.buildArtifact requires sourceDirectory')
                    );
                }
                var raw = requireNative('buildToolPkgArtifact')(sourceDirectory);
                var result = parseStorageResponse(raw, 'buildArtifact');
                return Promise.resolve({
                    archivePath: String(result.archivePath || ''),
                    artifactSha256: String(result.artifactSha256 || ''),
                    toolPkgId: String(result.toolPkgId || ''),
                    toolPkgVersion: String(result.toolPkgVersion || ''),
                    entryCount: Number(result.entryCount || 0),
                    unpackedBytes: Number(result.unpackedBytes || 0)
                });
            }

            var toolPkgApi = requireToolPkgApiRuntime();
            var api = toolPkgApi.namespace('ToolPkg', {
                registerToolboxUiModule: function(definition) {
                    registerWithNative(
                        definition,
                        'registerToolPkgToolboxUiModule',
                        'registerToolPkgToolboxUiModule',
                        ''
                    );
                },
                registerUiRoute: function(definition) {
                    registerWithNative(
                        definition,
                        'registerToolPkgUiRoute',
                        'registerToolPkgUiRoute',
                        ''
                    );
                },
                registerNavigationEntry: function(definition) {
                    var normalized = normalizeNavigationEntryDefinition(
                        definition,
                        'registerToolPkgNavigationEntry'
                    );
                    requireNative('registerToolPkgNavigationEntry')(
                        JSON.stringify(normalized)
                    );
                },
                registerDesktopWidget: function(definition) {
                    requireNative('registerToolPkgDesktopWidget')(
                        JSON.stringify(copyObject(definition, ''))
                    );
                },
                readResource: readToolPkgResource,
                getConfigDir: getToolPkgConfigDir,
                storage: createToolPkgStorage,
                buildArtifact: buildToolPkgArtifact,
                services: {
                    openAIWebSearch: createOpenAIWebSearchService(),
                    bilibili: createBilibiliService()
                },
                registerChatMessageMenuItem: toolPkgApi.method().since(
                    '1.0.1',
                    buildCustomRegistration(
                        'registerChatMessageMenuItem',
                        'registerToolPkgChatMessageMenuItem',
                        normalizeChatMessageMenuItemDefinition
                    )
                ),
                registerChatRuntimeHook: toolPkgApi.method().since(
                    '1.0.1',
                    buildFunctionRegistration(
                        'registerChatRuntimeHook',
                        'registerToolPkgChatRuntimeHook'
                    )
                )
            });


            [
                apiMethod('registerAppLifecycleHook', 'registerToolPkgAppLifecycleHook'),
                apiMethod('registerMessageProcessingPlugin', 'registerToolPkgMessageProcessingPlugin'),
                apiMethod('registerXmlRenderPlugin', 'registerToolPkgXmlRenderPlugin'),
                apiMethod('registerInputMenuTogglePlugin', 'registerToolPkgInputMenuTogglePlugin'),
                apiMethod('registerChatInputHook', 'registerToolPkgChatInputHook'),
                apiMethod('registerChatViewHook', 'registerToolPkgChatViewHook'),
                apiMethod('registerChatMessageHook', 'registerToolPkgChatMessageHook'),
                apiMethod('registerToolLifecycleHook', 'registerToolPkgToolLifecycleHook'),
                apiMethod('registerPromptInputHook', 'registerToolPkgPromptInputHook'),
                apiMethod('registerPromptHistoryHook', 'registerToolPkgPromptHistoryHook'),
                apiMethod('registerPromptEstimateHistoryHook', 'registerToolPkgPromptEstimateHistoryHook'),
                apiMethod('registerSystemPromptComposeHook', 'registerToolPkgSystemPromptComposeHook'),
                apiMethod('registerToolPromptComposeHook', 'registerToolPkgToolPromptComposeHook'),
                apiMethod('registerPromptFinalizeHook', 'registerToolPkgPromptFinalizeHook'),
                apiMethod('registerPromptEstimateFinalizeHook', 'registerToolPkgPromptEstimateFinalizeHook'),
                apiMethod('registerSummaryGenerateHook', 'registerToolPkgSummaryGenerateHook')
            ].forEach(installFunctionRegistration);

            api.registerAiProvider = function(definition) {
                var normalized = normalizeAiProviderDefinition(definition, 'registerAiProvider');
                requireNative('registerToolPkgAiProvider')(JSON.stringify(normalized));
            };

            installGlobal('ToolPkg', api);
        })();
    """.trimIndent()
}
