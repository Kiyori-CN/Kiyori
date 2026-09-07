package com.ai.assistance.operit.core.tools.defaultTool.websession.extension

import android.content.Context
import android.os.Looper
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.*
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.*
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolResult
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.json.JSONArray
import org.json.JSONObject

/** AI 包调用沿用 ToolExecutionManager 的工具权限流程；这里不创建第二个浏览器或安装注册表。 */
internal class BrowserDevelopmentTools(
    private val owner: StandardBrowserSessionTools,
    context: Context,
) {
    private val sourceTools = UserscriptSourceTools(context.applicationContext)
    private val scripts get() = owner.userscriptRepository
    private val extensions get() = owner.extensionRepository

    fun invoke(tool: AITool): ToolResult {
        return try {
            check(Looper.myLooper() != Looper.getMainLooper()) { "MAIN_THREAD_CALL_REJECTED" }
            val args = BrowserDevelopmentArguments(tool.parameters.associate { it.name to it.value })
            val payload = runBlocking(Dispatchers.IO) {
                if (tool.name == "browser_development_query") query(args) else apply(args)
            }
            ToolResult(tool.name, true, StringResultData(payload.put("schema_version", 1).toString()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // 不把源码、路径、Cookie 或完整异常对象记录到普通日志。
            val message = error.message?.take(2000) ?: error.javaClass.simpleName
            val payload = JSONObject().put("schema_version", 1).put("status", "error").put("error", message)
                .put("next_action", "Read current state; correct parameters or capability errors before retrying. Do not assume a timed-out mutation was not applied.")
            ToolResult(tool.name, false, StringResultData(payload.toString()), message)
        }
    }

    private suspend fun query(args: BrowserDevelopmentArguments): JSONObject = when (args.required("action")) {
        "help" -> help()
        "list" -> when (args.target()) {
            "extension" -> JSONObject().put("extensions", JSONArray(extensions.state.value.map(::extensionJson)))
            "userscript" -> JSONObject().put("scripts", JSONArray(scripts.listInstalledScripts().map { scriptJson(it) }))
                .put("runtime_allowed", scripts.userScriptsAllowedFlow.first())
            "plugin" -> JSONObject().put("plugins", JSONArray()
                .put(JSONObject().put("id", BUILT_IN_USERSCRIPT_PLUGIN_ID).put("built_in", true)
                    .put("enabled", scripts.userScriptsAllowedFlow.first()))
                .put(JSONObject().put("id", BUILT_IN_COOKIE_PLUGIN_ID).put("built_in", true)
                    .put("enabled", owner.browserSettingsStore.current.cookieReaderEnabled)))
            else -> error("INVALID_TARGET")
        }
        "read" -> when (args.target()) {
            "extension" -> {
                val item = extensions.get(args.required("id"))
                args.optional("expected_revision")?.let { require(it == item.revision) { "REVISION_CONFLICT" } }
                val result = extensionJson(item).put("manifest", JSONObject(BrowserExtensionPackage.codec.encodeToString(item.bundle.manifest)))
                    .put("files", JSONArray(item.bundle.files.keys.toList()))
                args.optional("file")?.let { path ->
                    val source = item.bundle.files[path] ?: error("FILE_NOT_FOUND")
                    result.put("source", slice(source, args))
                }
                result
            }
            "userscript" -> {
                val id = args.scriptId()
                val current = scripts.getInstalledScript(id) ?: error("SCRIPT_NOT_FOUND")
                val revisions = scripts.listRevisions(id)
                val revision = revisions.single { it.active }.revisionId
                args.optional("expected_revision")?.let { require(it == revision) { "REVISION_CONFLICT" } }
                val source = scripts.readRevisionSource(id, revision)
                scriptJson(current).put("revision", revision).put("source", slice(source, args))
                    .put("revisions", JSONArray(revisions.map { JSONObject().put("revision", it.revisionId).put("version", it.version).put("active", it.active) }))
            }
            else -> error("BUILT_IN_SOURCE_UNAVAILABLE")
        }
        "inspect" -> when (args.target()) {
            "extension" -> {
                val bundle = extensions.validate(readBundle(args))
                JSONObject().put("valid", true).put("id", bundle.id).put("revision", bundle.revision)
                    .put("manifest", JSONObject(BrowserExtensionPackage.codec.encodeToString(bundle.manifest)))
                    .put("runtime_supported", owner.runOnMainSync { owner.extensionRuntime.supported })
            }
            "userscript" -> {
                val source = readSource(args)
                val preview = scripts.prepareInstallPreview(source, UserscriptInstallSourceType.TOOL_INPUT)
                val syntax = sourceTools.validateSyntax(source)
                JSONObject().put("valid", syntax == null && preview.blockedReasons.isEmpty() && preview.unknownGrants.isEmpty())
                    .put("syntax_error", syntax ?: JSONObject.NULL).put("blocked_reasons", JSONArray(preview.blockedReasons))
                    .put("unknown_grants", JSONArray(preview.unknownGrants))
                    .put("metadata", JSONObject(BrowserExtensionPackage.codec.encodeToString(preview.metadata)))
            }
            else -> error("BUILT_IN_INSPECT_UNSUPPORTED")
        }
        "diagnostics" -> diagnostics(args)
        else -> error("UNKNOWN_QUERY_ACTION")
    }

    private suspend fun apply(args: BrowserDevelopmentArguments): JSONObject = when (args.required("action")) {
        "install" -> when (args.target()) {
            "extension" -> {
                val item = extensions.install(readBundle(args), args.optional("expected_revision"))
                owner.runOnMainSync { owner.extensionRuntime.reconcile() }
                extensionJson(item).put("reload_required", true)
            }
            "userscript" -> {
                val source = readSource(args)
                sourceTools.validateSyntax(source)?.let { error("SYNTAX_ERROR: $it") }
                val id = args.optional("id")?.let { args.scriptId() }
                val expected = args.optional("expected_revision")
                require((id == null) == (expected == null)) { "UPDATE_REQUIRES_ID_AND_REVISION" }
                val current = id?.let { scripts.getInstalledScript(it) ?: error("SCRIPT_NOT_FOUND") }
                val preview = scripts.prepareInstallPreview(source, UserscriptInstallSourceType.TOOL_INPUT,
                    sourceUrl = current?.sourceUrl, sourceDisplay = current?.sourceDisplay ?: "AI browser development",
                    isUpdate = id != null, existingScriptId = id)
                require(preview.blockedReasons.isEmpty() && preview.unknownGrants.isEmpty()) {
                    "UNSUPPORTED_SCRIPT: ${preview.blockedReasons + preview.unknownGrants}"
                }
                val installed = scripts.install(preview.copy(expectedRevisionId = expected, requireNew = id == null, enabledOnCommit = false))
                owner.userscriptManager.synchronizeRepositoryForTools()
                scriptJson(installed).put("reload_required", true)
            }
            else -> error("BUILT_IN_INSTALL_UNSUPPORTED")
        }
        "set_enabled" -> {
            val enabled = args.boolean("enabled")
            when (args.target()) {
                "extension" -> {
                    if (enabled) require(owner.runOnMainSync { owner.extensionRuntime.supported }) { "UNSUPPORTED_RUNTIME" }
                    val item = extensions.setEnabled(args.required("id"), enabled, args.required("expected_revision"))
                    owner.runOnMainSync { owner.extensionRuntime.reconcile() }
                    extensionJson(item).put("reload_required", true)
                }
                "userscript" -> {
                    val id = args.scriptId()
                    scripts.setEnabledForAgent(id, enabled, args.required("expected_revision"))
                    owner.userscriptManager.synchronizeRepositoryForTools()
                    scriptJson(scripts.getInstalledScript(id) ?: error("SCRIPT_NOT_FOUND")).put("reload_required", true)
                }
                else -> {
                    when (args.required("id")) {
                        BUILT_IN_USERSCRIPT_PLUGIN_ID -> {
                            scripts.setUserScriptsAllowed(enabled)
                            owner.userscriptManager.synchronizeRepositoryForTools()
                        }
                        BUILT_IN_COOKIE_PLUGIN_ID -> owner.runOnMainSync {
                            owner.browserSettingsStore.setCookieReaderEnabled(enabled)
                            if (!enabled) owner.browserHost?.updateBrowserCookieState(BrowserCookieUiState())
                            owner.refreshSessionUiOnMain()
                        }
                        else -> error("UNKNOWN_BUILT_IN_PLUGIN")
                    }
                    JSONObject().put("id", args.required("id")).put("enabled", enabled).put("reload_required", true)
                }
            }
        }
        "delete" -> {
            when (args.target()) {
                "extension" -> {
                    extensions.delete(args.required("id"), args.required("expected_revision"))
                    owner.runOnMainSync { owner.extensionRuntime.reconcile() }
                }
                "userscript" -> {
                    scripts.deleteForAgent(args.scriptId(), args.required("expected_revision"))
                    owner.userscriptManager.synchronizeRepositoryForTools()
                }
                else -> error("BUILT_IN_PLUGIN_CANNOT_BE_DELETED")
            }
            JSONObject().put("deleted", true).put("id", args.required("id")).put("reload_required", true)
        }
        "reload" -> owner.runOnMainSync {
            val session = StandardBrowserSessionTools.sessions[args.required("session_id")] ?: error("SESSION_NOT_FOUND")
            require(session.webView.url == args.required("expected_url")) { "PAGE_CHANGED" }
            owner.extensionRuntime.reconcile()
            session.webView.reload()
            JSONObject().put("status", "navigation_requested").put("session_id", session.id)
                .put("next_action", "Wait for navigation and read diagnostics, then assert the intended DOM effect with browser:evaluate/snapshot.")
        }
        "invoke_action" -> owner.runOnMainSync {
            val session = StandardBrowserSessionTools.sessions[args.required("session_id")] ?: error("SESSION_NOT_FOUND")
            require(session.webView.url == args.required("expected_url")) { "PAGE_CHANGED" }
            when (args.target()) {
                "extension" -> {
                    require(extensions.get(args.required("id")).revision == args.required("expected_revision")) { "REVISION_CONFLICT" }
                    JSONObject().put("document_id", owner.extensionRuntime.invokeAction(session.id, args.required("id")))
                        .put("status", "action_dispatched")
                }
                "userscript" -> {
                    val command = owner.userscriptManager.getMenuCommands(session.id)
                        .firstOrNull { it.commandId == args.required("command_id") && it.userscriptId == args.scriptId() }
                        ?: error("COMMAND_NOT_FOUND")
                    owner.userscriptManager.invokeMenuCommand(session.id, command.commandId)
                    JSONObject().put("status", "action_dispatched")
                }
                else -> error("BUILT_IN_ACTION_UNSUPPORTED")
            }
        }
        "export" -> {
            require(args.target() == "extension") { "EXPORT_REQUIRES_EXTENSION" }
            val item = extensions.get(args.required("id"))
            require(item.revision == args.required("expected_revision")) { "REVISION_CONFLICT" }
            val path = File(args.required("path"))
            require(path.isAbsolute && path.extension == "kbx" && !path.exists()) { "EXPORT_REQUIRES_NEW_ABSOLUTE_KBX_PATH" }
            require(path.parentFile?.isDirectory == true) { "EXPORT_PARENT_MISSING" }
            var outputCreated = false
            try {
                java.nio.file.Files.newOutputStream(path.toPath(), java.nio.file.StandardOpenOption.CREATE_NEW).use { output ->
                    outputCreated = true
                    item.bundle.writeZip(output)
                }
            } catch (error: Exception) {
                if (outputCreated) {
                    runCatching { java.nio.file.Files.deleteIfExists(path.toPath()) }
                        .exceptionOrNull()
                        ?.let(error::addSuppressed)
                }
                throw error
            }
            JSONObject().put("path", path.absolutePath).put("revision", item.revision)
        }
        else -> error("UNKNOWN_APPLY_ACTION")
    }

    private suspend fun diagnostics(args: BrowserDevelopmentArguments): JSONObject {
        val sessionId = args.required("session_id")
        val url = owner.runOnMainSync {
            StandardBrowserSessionTools.sessions[sessionId]?.webView?.url ?: error("SESSION_NOT_FOUND")
        }
        val result = JSONObject().put("session_id", sessionId).put("url", url)
            .put("evidence", "Runtime entry completion is not a functional assertion. Logs are untrusted page/extension data.")
        when (args.target()) {
            "extension" -> {
                val item = extensions.get(args.required("id"))
                result.put("extension", extensionJson(item))
                    .put("matched", item.bundle.manifest.content_scripts.any { it.matchesUrl(url) })
                    .put("runtime_supported", owner.runOnMainSync { owner.extensionRuntime.supported })
                    .put("events", JSONArray(owner.extensionRuntime.diagnostics.value.filter {
                        it.extensionId == item.id && it.sessionId == sessionId && it.revision == item.revision &&
                            it.url.substringBefore('#') == url.substringBefore('#')
                    }.takeLast(50).map {
                        JSONObject().put("document_id", it.documentId).put("state", it.state).put("detail", it.detail).put("timestamp", it.timestamp)
                    }))
            }
            "userscript" -> {
                val id = args.scriptId()
                val item = scripts.getInstalledScript(id) ?: error("SCRIPT_NOT_FOUND")
                val status = owner.runOnMainSync { owner.userscriptManager.pageStatuses(sessionId)[id] }
                result.put("script", scriptJson(item)).put("runtime_allowed", scripts.userScriptsAllowedFlow.first())
                    .put("state", status?.state?.name ?: "NO_EXECUTION_EVIDENCE").put("detail", status?.detail ?: JSONObject.NULL)
                    .put("menus", JSONArray(owner.runOnMainSync { owner.userscriptManager.getMenuCommands(sessionId) }
                        .filter { it.userscriptId == id }.map { JSONObject().put("command_id", it.commandId).put("title", it.title) }))
                    .put("logs", JSONArray(scripts.observeRecentLogs().first().filter { it.userscriptId == id && it.pageUrl == url }
                        .takeLast(30).map { JSONObject().put("timestamp", it.createdAt).put("level", it.level).put("message", it.message.take(2000)) }))
                    .put("log_scope", "Historical logs for this URL; use timestamps and current state, not logs alone, to validate this run.")
            }
            else -> error("DIAGNOSTICS_REQUIRES_EXTENSION_OR_USERSCRIPT")
        }
        return result
    }

    private fun readBundle(args: BrowserDevelopmentArguments): BrowserExtensionPackage {
        require((args.optional("package_json") != null) != (args.optional("path") != null)) { "PROVIDE_PACKAGE_JSON_OR_PATH" }
        args.optional("package_json")?.let { raw ->
            require(raw.length <= 2 * BrowserExtensionPackage.MAX_BYTES) { "PACKAGE_TOO_LARGE" }
            return BrowserExtensionPackage.codec.decodeFromString(raw)
        }
        val path = File(args.required("path"))
        require(path.isAbsolute && path.isFile && path.extension == "kbx" && path.length() <= 2 * BrowserExtensionPackage.MAX_BYTES) { "INVALID_KBX_PATH" }
        return path.inputStream().use(BrowserExtensionPackage::readZip)
    }

    private fun readSource(args: BrowserDevelopmentArguments): String {
        require((args.optional("source") != null) != (args.optional("path") != null)) { "PROVIDE_SOURCE_OR_PATH" }
        val source = args.optional("source") ?: File(args.required("path")).let { file ->
            require(file.isAbsolute && file.isFile && file.length() <= 1024 * 1024) { "INVALID_SOURCE_PATH" }
            file.readText(Charsets.UTF_8)
        }
        require(source.toByteArray(Charsets.UTF_8).size <= 1024 * 1024) { "SOURCE_TOO_LARGE" }
        return source
    }

    private fun extensionJson(item: InstalledBrowserExtension): JSONObject = JSONObject().put("id", item.id)
        .put("name", item.bundle.manifest.name).put("version", item.bundle.manifest.version)
        .put("enabled", item.enabled).put("revision", item.revision)
        .put("action", item.bundle.manifest.action?.default_title ?: JSONObject.NULL)

    private suspend fun scriptJson(item: UserscriptListItem): JSONObject = JSONObject().put("id", item.id)
        .put("name", item.name).put("version", item.version).put("enabled", item.enabled)
        .put("revision", scripts.listRevisions(item.id).firstOrNull { it.active }?.revisionId ?: JSONObject.NULL)
        .put("matches", JSONArray(item.matches)).put("includes", JSONArray(item.includes))
        .put("grants", JSONArray(item.grants)).put("connects", JSONArray(item.connects))
        .put("blocked_reasons", JSONArray(item.blockedReasons)).put("world", item.executionWorld?.name ?: JSONObject.NULL)

    private fun slice(source: String, args: BrowserDevelopmentArguments): JSONObject {
        val offset = args.integer("offset", 0, 0..source.length)
        val limit = args.integer("limit", 16000, 1..60000)
        require(
            offset == 0 || offset == source.length ||
                !Character.isSurrogatePair(source[offset - 1], source[offset]),
        ) { "OFFSET_SPLITS_UNICODE_CHARACTER" }
        var end = minOf(source.length, offset + limit)
        if (end < source.length && Character.isSurrogatePair(source[end - 1], source[end])) {
            end += 1
        }
        return JSONObject().put("text", source.substring(offset, end)).put("offset", offset).put("total", source.length)
            .put("next_offset", if (end < source.length) end else JSONObject.NULL)
    }

    private fun help(): JSONObject = JSONObject().put("runtime_version", 1)
        .put("userscript_grants", JSONArray(UserscriptCapabilityRegistry.declaredCapabilities().map { capability ->
            JSONObject().put("grant", capability.canonicalGrant).put("aliases", JSONArray(capability.aliases.toList()))
                .put("blocked_reasons", JSONArray(UserscriptCapabilityRegistry.blockedReasons(listOf(capability.canonicalGrant))))
        }))
        .put("limits", JSONObject().put("installed_extensions", 16).put("extension_bytes", BrowserExtensionPackage.MAX_BYTES)
            .put("extension_files", BrowserExtensionPackage.MAX_FILES)
            .put("match_pattern_characters", BrowserExtensionPackage.MAX_MATCH_PATTERN_CHARACTERS)
            .put("source_read_characters", 60000))
        .put("supported", JSONArray(listOf("kbx manifest v3 strict page subset", "top-frame isolated JS/CSS", "document_start/end/idle", "kiyori.log/onCleanup/onAction", "userscript metadata and declared GM APIs", "builtin provider toggles")))
        .put("unsupported", JSONArray(listOf("Chrome/Firefox packages", "background/service_worker", "popup", "all_frames", "incognito extension execution", "chrome.* / browser.* APIs", "extension privileged native APIs", "uninstall builtin providers")))
        .put("execution_contract", "Extensions run only in regular profiles after host origin and site-script permission checks. document_start means the earliest host-authorized callback, not a promise to precede page inline scripts. kiyori.onCleanup(function) registers best-effort cleanup; kiyori.onAction(function) registers the manifest action handler. No extension storage/native/network API is exposed; ordinary DOM/Web APIs retain WebView behavior.")
        .put("workflow", "list -> read current revision -> inspect -> install (disabled) -> set_enabled -> list browser tabs -> diagnostics -> reload exact session and expected_url -> wait -> diagnostics -> browser:evaluate/snapshot functional assertions. Update/delete require expected_revision. Global userscript permission is a separate explicit plugin toggle. Disabled/deleted code may require reload to undo page effects.")
        .put("query_actions", JSONArray(listOf("help", "list", "read", "inspect", "diagnostics")))
        .put("apply_actions", JSONArray(listOf("install", "set_enabled", "delete", "reload", "invoke_action", "export")))
        .put("extension_template", JSONObject("""{"manifest":{"manifest_version":3,"name":"Page marker","version":"1.0.0","kiyori":{"schema_version":1,"id":"com.example.page_marker"},"content_scripts":[{"matches":["https://example.com/*"],"js":["content/main.js"],"css":["content/main.css"]}],"action":{"default_title":"Toggle marker"}},"files":{"content/main.js":"const marker = document.createElement('div'); marker.id = 'kiyori-example-marker'; marker.textContent = 'Kiyori extension active'; document.body.appendChild(marker); kiyori.onCleanup(() => marker.remove()); kiyori.onAction(() => { marker.hidden = !marker.hidden; }); kiyori.log('marker created');","content/main.css":"#kiyori-example-marker { position:fixed; top:0; right:0; z-index:999999; background:white; color:blue; padding:8px; }"}}"""))
        .put("userscript_template", "// ==UserScript==\n// @name Page marker\n// @namespace com.example.kiyori\n// @version 1.0.0\n// @match https://example.com/*\n// @grant none\n// @run-at document-end\n// ==/UserScript==\n(() => { document.body.dataset.kiyoriMarker = 'active'; })();")
}

internal class BrowserDevelopmentArguments(private val values: Map<String, String>) {
    fun optional(name: String): String? = values[name]?.takeIf { it.isNotBlank() }
    fun required(name: String): String = optional(name) ?: throw IllegalArgumentException("MISSING_PARAMETER: $name")
    fun target(): String = required("target").also { require(it in setOf("extension", "userscript", "plugin")) { "INVALID_TARGET" } }
    fun scriptId(): Long = required("id").toLongOrNull()?.takeIf { it > 0 } ?: error("INVALID_SCRIPT_ID")
    fun boolean(name: String): Boolean = when (required(name)) { "true" -> true; "false" -> false; else -> error("INVALID_BOOLEAN: $name") }
    fun integer(name: String, default: Int, range: IntRange): Int =
        (optional(name)?.let { it.toIntOrNull() ?: error("INVALID_INTEGER: $name") } ?: default)
            .also { require(it in range) { "OUT_OF_RANGE: $name" } }
}
