package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.storage

import android.content.Context
import android.util.Base64
import android.webkit.MimeTypeMap
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ParsedUserscriptMetadata
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptCapabilityRegistry
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptCompatibilityPolicy
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptBootstrapPayload
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptDraft
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptExecutionPayload
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptExecutionWorld
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptExecutionWorldPolicy
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptInstallPreview
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptInstallSourceType
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptListItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptLogItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptEditorReview
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptManagementPolicy
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptMatcher
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptMetadataParser
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptResourcePayload
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptRevisionInfo
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptRuntimeCapabilities
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptSourceTools
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.platform.network.KiyoriNetworkModule
import com.kiyori.platform.network.applyKiyoriNetworkProxy
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

internal class UserscriptRepository private constructor(
    context: Context
) {
    companion object {
        private const val TAG = "UserscriptRepository"
        private const val LOG_LIMIT = 200
        private const val ENTRY_TYPE_REQUIRE = "require"
        private const val ENTRY_TYPE_RESOURCE = "resource"
        private const val MAX_SOURCE_BYTES = 10L * 1024L * 1024L
        private const val MAX_REQUIRE_BYTES = 5L * 1024L * 1024L
        private const val MAX_RESOURCE_BYTES = 10L * 1024L * 1024L
        private const val MAX_DEPENDENCY_BYTES = 50L * 1024L * 1024L
        private const val MAX_REQUIRE_COUNT = 128
        private const val MAX_RESOURCE_COUNT = 128
        private const val NEW_DRAFT_PREFIX = "new-"

        @Volatile
        private var instance: UserscriptRepository? = null

        fun getInstance(context: Context): UserscriptRepository {
            return instance ?: synchronized(this) {
                instance ?: UserscriptRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContext = context.applicationContext
    private val store = UserscriptJsonStore.getInstance(context)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val installMutex = Mutex()
    private val sourceTools = UserscriptSourceTools(appContext)
    private val httpClient =
        OkHttpClient.Builder()
            .applyKiyoriNetworkProxy(KiyoriNetworkModule.BROWSER)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    val installedScriptsFlow: Flow<List<UserscriptListItem>> =
        store.observeUserscripts().map { entities ->
            entities.map(::entityToListItem)
        }

    val userScriptsAllowedFlow: Flow<Boolean> = store.observeUserScriptsAllowed()

    fun observeRecentLogs(limit: Int = LOG_LIMIT): Flow<List<UserscriptLogItem>> =
        store.observeRecentLogs(limit).map { logs ->
            logs.map { log ->
                UserscriptLogItem(
                    id = log.id,
                    userscriptId = log.userscriptId,
                    level = log.level,
                    message = log.message,
                    pageUrl = log.pageUrl,
                    createdAt = log.createdAt
                )
            }
        }

    suspend fun prepareInstallPreview(
        rawSource: String,
        sourceType: UserscriptInstallSourceType,
        sourceUrl: String? = null,
        sourceDisplay: String? = null,
        isUpdate: Boolean = false,
        existingScriptId: Long? = null
    ): UserscriptInstallPreview {
        require(rawSource.toByteArray(Charsets.UTF_8).size <= MAX_SOURCE_BYTES) {
            "Userscript source exceeds the 10 MiB limit"
        }
        val metadata = UserscriptMetadataParser.parse(rawSource)
        require(metadata.requires.size <= MAX_REQUIRE_COUNT) {
            "Userscript declares more than $MAX_REQUIRE_COUNT @require entries"
        }
        require(metadata.resources.size <= MAX_RESOURCE_COUNT) {
            "Userscript declares more than $MAX_RESOURCE_COUNT @resource entries"
        }
        val knownGrants = UserscriptCapabilityRegistry.knownGrants(metadata.grants)
        val unknownGrants = UserscriptCapabilityRegistry.unknownGrants(metadata.grants)
        val worldResolution =
            UserscriptExecutionWorldPolicy.resolve(
                metadata = metadata,
                capabilities = UserscriptRuntimeCapabilities.current(),
            )
        val blockedReasons =
            (
                UserscriptCapabilityRegistry.blockedReasons(metadata.grants) +
                    worldResolution.blockedReasons
                ).distinct()
        return UserscriptInstallPreview(
            metadata = metadata,
            rawSource = rawSource,
            sourceType = sourceType,
            sourceUrl = sourceUrl,
            sourceDisplay = sourceDisplay,
            knownGrants = knownGrants,
            unknownGrants = unknownGrants,
            blockedReasons = blockedReasons,
            executionWorld = worldResolution.world,
            unsafeWindowMode = worldResolution.unsafeWindowMode,
            isUpdate = isUpdate,
            existingScriptId = existingScriptId
        )
    }

    suspend fun fetchRemotePreview(
        rawUrl: String,
        sourceType: UserscriptInstallSourceType
    ): UserscriptInstallPreview {
        val normalizedUrl = rawUrl.trim()
        require(normalizedUrl.isNotBlank()) { "Userscript URL is empty" }
        val response = httpClient.newCall(Request.Builder().url(normalizedUrl).get().build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IllegalStateException("Failed to load userscript: HTTP ${response.code}")
        }
        val body = response.body
            ?: run {
                response.close()
                throw IllegalStateException("No response body for $normalizedUrl")
            }
        val declaredLength = body.contentLength()
        if (declaredLength > MAX_SOURCE_BYTES) {
            response.close()
            throw IllegalStateException("Userscript source exceeds the 10 MiB limit")
        }
        val finalUrl = response.request.url.toString()
        val etag = response.header("ETag")
        val lastModified = response.header("Last-Modified")
        val bodyBytes =
            try {
                body.byteStream().use { input ->
                    input.readUserscriptLimitedBytes(MAX_SOURCE_BYTES)
                }
            } finally {
                response.close()
            }
        return prepareInstallPreview(
            rawSource = bodyBytes.toString(Charsets.UTF_8),
            sourceType = sourceType,
            sourceUrl = finalUrl,
            sourceDisplay = normalizedUrl,
        ).copy(
            sourceEtag = etag,
            sourceLastModifiedHeader = lastModified,
        )
    }

    suspend fun install(preview: UserscriptInstallPreview): UserscriptListItem =
        withContext(Dispatchers.IO) {
            installMutex.withLock {
                val existing = resolveExistingScript(preview)
                // 更新检查和提交是两个用户动作；基准 revision 变化后必须重新检查，不能覆盖较新的本地编辑。
                preview.expectedRevisionId?.let { expectedRevisionId ->
                    require(existing?.activeRevisionId == expectedRevisionId) {
                        "Userscript changed after this update was checked"
                    }
                }
                if (
                    existing != null &&
                        compareVersions(preview.metadata.version, existing.version) < 0
                ) {
                    throw IllegalStateException(
                        "Refusing to install older userscript version " +
                            "${preview.metadata.version} over ${existing.version}",
                    )
                }
                if (preview.sourceType == UserscriptInstallSourceType.UPDATE && existing != null) {
                    require(preview.metadata.name == existing.name) {
                        "Userscript update changed the script name"
                    }
                    require(preview.metadata.namespace == existing.namespace) {
                        "Userscript update changed the script namespace"
                    }
                }

                val now = System.currentTimeMillis()
                val sourceHash = sha256(preview.rawSource)
                val scriptId = existing?.id ?: store.getNextScriptId()
                val revisionNumber = (existing?.revisionNumber ?: 0L) + 1L
                val revisionId =
                    buildRevisionId(
                        now = now,
                        sourceHash = sourceHash,
                    )
                val transactionId = UUID.randomUUID().toString()
                val journal =
                    UserscriptTransactionJournal(
                        transactionId = transactionId,
                        userscriptId = scriptId,
                        revisionId = revisionId,
                        isNewScript = existing == null,
                        createdAt = now,
                    )
                store.beginTransaction(journal)

                val entity: UserscriptEntity
                try {
                    val stagedRevisionDir = store.layout.stagedRevisionDir(transactionId)
                    val finalRevisionDir = store.layout.revisionDir(scriptId, revisionId)
                    val stagedSourceFile = File(stagedRevisionDir, "source.user.js")
                    writeSyncedBytes(
                        stagedSourceFile,
                        preview.rawSource.toByteArray(Charsets.UTF_8),
                    )
                    require(sha256(stagedSourceFile.readBytes()) == sourceHash) {
                        "Userscript staging source hash mismatch"
                    }

                    val stagedResources =
                        fetchAndStageResources(
                            metadata = preview.metadata,
                            userscriptId = scriptId,
                            revisionId = revisionId,
                            sourceUrl = preview.sourceUrl ?: existing?.sourceUrl,
                            stagedRevisionDir = stagedRevisionDir,
                            finalRevisionDir = finalRevisionDir,
                        )
                    val manifest =
                        UserscriptRevisionManifest(
                            userscriptId = scriptId,
                            revisionId = revisionId,
                            revisionNumber = revisionNumber,
                            name = preview.metadata.name,
                            namespace = preview.metadata.namespace,
                            version = preview.metadata.version,
                            sourceHash = sourceHash,
                            sourceType = preview.sourceType.name,
                            sourceUrl = preview.sourceUrl ?: existing?.sourceUrl,
                            sourceEtag = preview.sourceEtag,
                            sourceLastModifiedHeader = preview.sourceLastModifiedHeader,
                            createdAt = now,
                            resources =
                                stagedResources.map { staged ->
                                    staged.manifestResource
                                },
                        )
                    writeAtomicText(
                        File(stagedRevisionDir, "manifest.json"),
                        json.encodeToString(manifest),
                    )
                    moveDirectoryAtomically(stagedRevisionDir, finalRevisionDir)

                    val installBlockedReasons =
                        UserscriptCompatibilityPolicy.blockedReasons(
                            metadata = preview.metadata,
                            runtimeCapabilities = UserscriptRuntimeCapabilities.current(),
                        )
                    entity =
                        UserscriptEntity(
                            id = scriptId,
                            name = preview.metadata.name,
                            namespace = preview.metadata.namespace,
                            version = preview.metadata.version,
                            description = preview.metadata.description,
                            author = preview.metadata.author,
                            homepageUrl = preview.metadata.homepage,
                            sourceUrl = preview.sourceUrl ?: existing?.sourceUrl,
                            sourceDisplay = preview.sourceDisplay ?: existing?.sourceDisplay,
                            downloadUrl = preview.metadata.downloadUrl,
                            updateUrl = preview.metadata.updateUrl,
                            runAt = preview.metadata.runAt.rawValue,
                            noFrames = preview.metadata.noFrames,
                            enabled =
                                installBlockedReasons.isEmpty() &&
                                    (existing?.enabled ?: true),
                            sourceHash = sourceHash,
                            scriptFilePath =
                                File(finalRevisionDir, "source.user.js").absolutePath,
                            activeRevisionId = revisionId,
                            revisionNumber = revisionNumber,
                            installSourceType = preview.sourceType.name,
                            metadataJson = json.encodeToString(preview.metadata),
                            grantsJson = json.encodeToString(preview.metadata.grants),
                            matchesJson = json.encodeToString(preview.metadata.matches),
                            includesJson = json.encodeToString(preview.metadata.includes),
                            excludesJson = json.encodeToString(preview.metadata.excludes),
                            excludeMatchesJson =
                                json.encodeToString(preview.metadata.excludeMatches),
                            connectsJson = json.encodeToString(preview.metadata.connects),
                            requiresJson = json.encodeToString(preview.metadata.requires),
                            resourcesJson = json.encodeToString(preview.metadata.resources),
                            installedAt = existing?.installedAt ?: now,
                            updatedAt = now,
                        )
                    store.commitUserscriptRevision(
                        entity = entity,
                        resources = stagedResources.map(StagedUserscriptResource::entity),
                        isNewScript = existing == null,
                    )
                } catch (error: Throwable) {
                    withContext(NonCancellable) {
                        store.abortTransaction(journal)
                    }
                    throw error
                }

                store.completeTransaction(journal)
                runCatching {
                    log(
                        userscriptId = scriptId,
                        level = "info",
                        pageUrl = null,
                        message =
                            if (existing == null) {
                                "Installed userscript ${preview.metadata.name} " +
                                    preview.metadata.version
                            } else {
                                "Updated userscript ${preview.metadata.name} " +
                                    preview.metadata.version
                            },
                    )
                }.onFailure { error ->
                    AppLogger.e(TAG, "Userscript revision committed but audit log failed", error)
                }
                entityToListItem(entity)
            }
        }

    suspend fun checkForUpdate(scriptId: Long): UserscriptInstallPreview? {
        val entity = store.getUserscriptById(scriptId) ?: return null
        val updateTarget = entity.updateUrl ?: entity.downloadUrl ?: entity.sourceUrl ?: return null
        val preview =
            fetchRemotePreview(
                rawUrl = updateTarget,
                sourceType = UserscriptInstallSourceType.UPDATE
            ).copy(
                isUpdate = true,
                existingScriptId = scriptId,
                expectedRevisionId = entity.activeRevisionId,
            )
        return if (compareVersions(preview.metadata.version, entity.version) > 0) {
            preview
        } else {
            null
        }
    }

    suspend fun setEnabled(scriptId: Long, enabled: Boolean) {
        val entity = store.getUserscriptById(scriptId) ?: return
        if (enabled) {
            val metadata = entityToMetadata(entity)
            val blockedReasons =
                UserscriptCompatibilityPolicy.blockedReasons(
                    metadata = metadata,
                    runtimeCapabilities = UserscriptRuntimeCapabilities.current(),
                )
            if (blockedReasons.isNotEmpty()) {
                log(
                    userscriptId = scriptId,
                    level = "error",
                    pageUrl = null,
                    message = "Cannot enable userscript ${entity.name}: ${blockedReasons.joinToString()}",
                )
                return
            }
        }
        store.updateUserscript(entity.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
        log(
            userscriptId = scriptId,
            level = "info",
            pageUrl = null,
            message = if (enabled) "Enabled userscript ${entity.name}" else "Disabled userscript ${entity.name}"
        )
    }

    suspend fun setUserScriptsAllowed(allowed: Boolean) {
        if (!store.setUserScriptsAllowed(allowed)) {
            return
        }
        log(
            userscriptId = null,
            level = "info",
            pageUrl = null,
            message =
                if (allowed) {
                    "Allowed userscript execution"
                } else {
                    "Revoked userscript execution permission"
                },
        )
    }

    suspend fun deleteUserscript(scriptId: Long) {
        val entity = store.getUserscriptById(scriptId) ?: return
        store.deleteUserscriptById(scriptId)
        val revisionDir = store.layout.revisionScriptDir(scriptId)
        if (revisionDir.exists() && !revisionDir.deleteRecursively()) {
            AppLogger.w(TAG, "Unable to remove userscript revisions at ${revisionDir.absolutePath}")
        }
        AppLogger.i(TAG, "Deleted userscript ${entity.name}")
    }

    suspend fun readSource(scriptId: Long): String? =
        withContext(Dispatchers.IO) {
            val entity = store.getUserscriptById(scriptId) ?: return@withContext null
            val sourceFile = File(entity.scriptFilePath)
            require(sourceFile.isFile) {
                "Userscript $scriptId active source is missing"
            }
            sourceFile.readText()
        }

    suspend fun createDraft(
        source: String = defaultNewUserscriptSource(),
    ): UserscriptDraft =
        withContext(Dispatchers.IO) {
            val draft =
                UserscriptDraftEntity(
                    draftId = NEW_DRAFT_PREFIX + UUID.randomUUID().toString().replace("-", ""),
                    sourceHash = sha256(source),
                    source = source,
                    updatedAt = System.currentTimeMillis(),
                )
            store.saveDraft(draft)
            draft.toModel()
        }

    suspend fun saveDraft(
        draftId: String,
        source: String,
    ): UserscriptDraft =
        withContext(Dispatchers.IO) {
            require(source.isNotBlank()) { "Userscript draft is empty" }
            val currentDraft = store.readDraft(draftId)
            val userscriptId =
                currentDraft?.userscriptId
                    ?: draftId
                        .removePrefix("script-")
                        .takeIf { draftId.startsWith("script-") }
                        ?.toLongOrNull()
            val entity =
                userscriptId?.let { scriptId ->
                    store.getUserscriptById(scriptId)
                        ?: throw IllegalArgumentException("Userscript $scriptId does not exist")
                }
            val draft =
                UserscriptDraftEntity(
                    draftId = draftId,
                    userscriptId = userscriptId,
                    baseRevisionId = currentDraft?.baseRevisionId ?: entity?.activeRevisionId,
                    sourceHash = sha256(source),
                    source = source,
                    updatedAt = System.currentTimeMillis(),
                )
            store.saveDraft(draft)
            draft.toModel()
        }

    suspend fun readDraft(draftId: String): UserscriptDraft? =
        withContext(Dispatchers.IO) {
            store.readDraft(draftId)?.toModel()
        }

    suspend fun listDrafts(): List<UserscriptDraft> =
        withContext(Dispatchers.IO) {
            store.listDrafts().map { draft -> draft.toModel() }
        }

    suspend fun discardDraft(draftId: String) {
        withContext(Dispatchers.IO) {
            store.deleteDraft(draftId)
        }
    }

    suspend fun installDraft(draftId: String): UserscriptListItem =
        withContext(Dispatchers.IO) {
            val draft =
                store.readDraft(draftId)
                    ?: throw IllegalStateException("Userscript draft $draftId does not exist")
            val entity =
                draft.userscriptId?.let { scriptId ->
                    store.getUserscriptById(scriptId)
                        ?: throw IllegalArgumentException("Userscript $scriptId does not exist")
                }
            if (entity != null) {
                require(draft.baseRevisionId == entity.activeRevisionId) {
                    "Userscript changed after this draft was created"
                }
            }
            sourceTools.validateSyntax(draft.source)?.let { error ->
                throw IllegalArgumentException(error)
            }
            val preview =
                prepareInstallPreview(
                    rawSource = draft.source,
                    sourceType = UserscriptInstallSourceType.TOOL_INPUT,
                    sourceUrl = entity?.sourceUrl,
                    sourceDisplay = entity?.sourceDisplay,
                    isUpdate = entity != null,
                    existingScriptId = entity?.id,
                )
            install(preview).also {
                store.deleteDraft(draftId)
            }
        }

    suspend fun reviewDraft(draftId: String): UserscriptEditorReview =
        withContext(Dispatchers.IO) {
            val draft =
                store.readDraft(draftId)
                    ?: throw IllegalStateException("Userscript draft $draftId does not exist")
            val current =
                draft.userscriptId?.let { scriptId ->
                    store.getUserscriptById(scriptId)?.let(::entityToListItem)
                }
            val activeSource =
                draft.userscriptId?.let { scriptId ->
                    readSource(scriptId)
                }.orEmpty()
            val syntaxError = sourceTools.validateSyntax(draft.source)
            val previewResult =
                runCatching {
                    prepareInstallPreview(
                        rawSource = draft.source,
                        sourceType = UserscriptInstallSourceType.TOOL_INPUT,
                        sourceUrl = current?.sourceUrl,
                        sourceDisplay = current?.sourceDisplay,
                        isUpdate = current != null,
                        existingScriptId = current?.id,
                    )
                }
            val preview = previewResult.getOrNull()
            UserscriptEditorReview(
                preview = preview,
                syntaxError = syntaxError ?: previewResult.exceptionOrNull()?.message,
                updateDiff =
                    if (current != null && preview != null) {
                        UserscriptManagementPolicy.buildUpdateDiff(current, preview)
                    } else {
                        null
                    },
                sourceDiff =
                    UserscriptManagementPolicy.buildSourceDiff(
                        original = activeSource,
                        revised = draft.source,
                    ),
            )
        }

    suspend fun formatDraftSource(draftId: String): UserscriptDraft =
        withContext(Dispatchers.IO) {
            val draft =
                store.readDraft(draftId)
                    ?: throw IllegalStateException("Userscript draft $draftId does not exist")
            saveDraft(
                draftId = draftId,
                source = sourceTools.format(draft.source),
            )
        }

    suspend fun listRevisions(scriptId: Long): List<UserscriptRevisionInfo> =
        withContext(Dispatchers.IO) {
            val entity =
                store.getUserscriptById(scriptId)
                    ?: return@withContext emptyList()
            store.listRevisionManifests(scriptId).map { manifest ->
                manifest.toModel(activeRevisionId = entity.activeRevisionId)
            }
        }

    suspend fun readRevisionSource(
        scriptId: Long,
        revisionId: String,
    ): String =
        withContext(Dispatchers.IO) {
            store.readRevisionSource(scriptId, revisionId)
        }

    suspend fun buildBootstrapPayload(
        sessionId: String,
        pageUrl: String,
        isTopFrame: Boolean,
        executionWorld: UserscriptExecutionWorld,
        runtimeCapabilities: UserscriptRuntimeCapabilities,
        userscriptId: Long? = null,
    ): UserscriptBootstrapPayload = withContext(Dispatchers.IO) {
        val entities = store.getAllUserscripts()
        if (entities.isEmpty()) {
            return@withContext UserscriptBootstrapPayload()
        }

        val matched =
            entities.filter { entity ->
                if (userscriptId != null && entity.id != userscriptId) {
                    return@filter false
                }
                if (!entity.enabled) {
                    return@filter false
                }
                val metadata = entityToMetadata(entity)
                if (
                    UserscriptCompatibilityPolicy
                        .blockedReasons(metadata, runtimeCapabilities)
                        .isNotEmpty()
                ) {
                    return@filter false
                }
                val worldResolution =
                    UserscriptExecutionWorldPolicy.resolve(metadata, runtimeCapabilities)
                if (
                    worldResolution.world != executionWorld ||
                        worldResolution.blockedReasons.isNotEmpty()
                ) {
                    return@filter false
                }
                UserscriptMatcher.matches(
                    metadata = metadata,
                    pageUrl = pageUrl,
                    isTopFrame = isTopFrame
                )
            }
        if (matched.isEmpty()) {
            return@withContext UserscriptBootstrapPayload()
        }

        val resourcesByScript =
            store.getResourcesForScripts(matched.map { it.id })
                .groupBy { it.userscriptId }

        val payloads =
            matched.map { entity ->
                val sourceFile = File(entity.scriptFilePath)
                require(sourceFile.isFile) {
                    "Userscript ${entity.id} active source is missing"
                }
                val source = sourceFile.readText()
                val metadata = entityToMetadata(entity)
                val values =
                    store.getValuesForScript(entity.id).associate { value ->
                        value.storageKey to value.valueJson
                    }
                val resourceEntities = resourcesByScript[entity.id].orEmpty().sortedBy { it.resourceKey }
                val requireBodies =
                    resourceEntities
                        .filter { it.entryType == ENTRY_TYPE_REQUIRE }
                        .map { resource ->
                            val file = File(resource.localPath)
                            require(file.isFile) {
                                "Userscript ${entity.id} require is missing: ${resource.resourceKey}"
                            }
                            file.readText()
                        }
                val resourcePayloads =
                    resourceEntities
                        .filter { it.entryType == ENTRY_TYPE_RESOURCE }
                        .associate { resource ->
                            val file = File(resource.localPath)
                            require(file.isFile) {
                                "Userscript ${entity.id} resource is missing: ${resource.resourceKey}"
                            }
                            val bytes = file.readBytes()
                            val mimeType = resource.mimeType ?: "application/octet-stream"
                            val dataUrl =
                                "data:$mimeType;base64," +
                                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                            resource.resourceKey to
                                UserscriptResourcePayload(
                                    text = bytes.toString(Charsets.UTF_8),
                                    dataUrl = dataUrl,
                                )
                        }
                UserscriptExecutionPayload(
                    scriptId = entity.id,
                    sessionId = sessionId,
                    pageUrl = pageUrl,
                    name = entity.name,
                    namespace = entity.namespace,
                    version = entity.version,
                    runAt = entity.runAt,
                    grants = metadata.grants,
                    capabilities = UserscriptCapabilityRegistry.knownGrants(metadata.grants),
                    metadataJson = json.encodeToString(metadata),
                    code = source,
                    requires = requireBodies,
                    values = values,
                    resources = resourcePayloads
                )
            }
        UserscriptBootstrapPayload(scripts = payloads)
    }

    suspend fun log(
        userscriptId: Long?,
        level: String,
        pageUrl: String?,
        message: String
    ) {
        if (message.isBlank()) {
            return
        }
        store.insertLog(
            UserscriptLogEntity(
                userscriptId = userscriptId,
                level = level.trim().ifBlank { "info" },
                message = message.trim(),
                pageUrl = pageUrl?.trim()?.ifBlank { null },
                createdAt = System.currentTimeMillis()
            )
        )
        store.trimLogs(LOG_LIMIT)
        AppLogger.d(TAG, "userscript[$userscriptId][$level] $message")
    }

    suspend fun getInstalledScript(scriptId: Long): UserscriptListItem? =
        store.getUserscriptById(scriptId)?.let(::entityToListItem)

    suspend fun listInstalledScripts(): List<UserscriptListItem> =
        store.getAllUserscripts()
            .map(::entityToListItem)
            .sortedWith(compareBy<UserscriptListItem> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })

    suspend fun persistValue(
        scriptId: Long,
        key: String,
        valueJson: String
    ) {
        store.insertValue(
            UserscriptValueEntity(
                userscriptId = scriptId,
                storageKey = key,
                valueJson = valueJson,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun persistValues(
        scriptId: Long,
        values: Map<String, String>
    ) {
        values.forEach { (key, valueJson) ->
            persistValue(scriptId, key, valueJson)
        }
    }

    suspend fun readValueJson(
        scriptId: Long,
        key: String
    ): String? = store.getValue(scriptId, key)?.valueJson

    suspend fun deleteValue(
        scriptId: Long,
        key: String
    ) {
        store.deleteValue(scriptId, key)
    }

    suspend fun deleteValues(
        scriptId: Long,
        keys: Collection<String>
    ) {
        keys.forEach { key ->
            store.deleteValue(scriptId, key)
        }
    }

    private fun fetchAndStageResources(
        metadata: ParsedUserscriptMetadata,
        userscriptId: Long,
        revisionId: String,
        sourceUrl: String?,
        stagedRevisionDir: File,
        finalRevisionDir: File,
    ): List<StagedUserscriptResource> {
        val stagedResources = ArrayList<StagedUserscriptResource>()
        var totalDependencyBytes = 0L
        metadata.requires.forEachIndexed { index, entry ->
                val absoluteUrl = resolveRemoteUrl(sourceUrl, entry.url)
                val staged = stageResource(
                    userscriptId = userscriptId,
                    revisionId = revisionId,
                    entryType = ENTRY_TYPE_REQUIRE,
                    resourceKey = "require:${index.toString().padStart(4, '0')}",
                    absoluteUrl = absoluteUrl,
                    relativePath =
                        "requires/${index.toString().padStart(4, '0')}.js",
                    stagedRevisionDir = stagedRevisionDir,
                    finalRevisionDir = finalRevisionDir,
                    maxBytes = MAX_REQUIRE_BYTES,
                )
                totalDependencyBytes += staged.length
                require(totalDependencyBytes <= MAX_DEPENDENCY_BYTES) {
                    "Userscript dependencies exceed the 50 MiB total limit"
                }
                stagedResources += staged
            }

        metadata.resources.forEachIndexed { index, entry ->
                val absoluteUrl = resolveRemoteUrl(sourceUrl, entry.url)
                val extension = MimeTypeMap.getFileExtensionFromUrl(absoluteUrl).takeIf { !it.isNullOrBlank() }
                val staged = stageResource(
                    userscriptId = userscriptId,
                    revisionId = revisionId,
                    entryType = ENTRY_TYPE_RESOURCE,
                    resourceKey = entry.name,
                    absoluteUrl = absoluteUrl,
                    relativePath =
                        "resources/${index.toString().padStart(4, '0')}.${extension ?: "bin"}",
                    stagedRevisionDir = stagedRevisionDir,
                    finalRevisionDir = finalRevisionDir,
                    maxBytes = MAX_RESOURCE_BYTES,
                )
                totalDependencyBytes += staged.length
                require(totalDependencyBytes <= MAX_DEPENDENCY_BYTES) {
                    "Userscript dependencies exceed the 50 MiB total limit"
                }
                stagedResources += staged
            }
        return stagedResources
    }

    private fun stageResource(
        userscriptId: Long,
        revisionId: String,
        entryType: String,
        resourceKey: String,
        absoluteUrl: String,
        relativePath: String,
        stagedRevisionDir: File,
        finalRevisionDir: File,
        maxBytes: Long,
    ): StagedUserscriptResource {
        val stagedFile = File(stagedRevisionDir, relativePath)
        val fetched = fetchRemoteAsset(absoluteUrl, stagedFile, maxBytes)
        val contentHash = sha256(fetched.file.readBytes())
        val updatedAt = System.currentTimeMillis()
        return StagedUserscriptResource(
            entity =
                UserscriptResourceEntity(
                    userscriptId = userscriptId,
                    revisionId = revisionId,
                    entryType = entryType,
                    resourceKey = resourceKey,
                    remoteUrl = fetched.finalUrl,
                    localPath = File(finalRevisionDir, relativePath).absolutePath,
                    mimeType = fetched.mimeType,
                    etag = fetched.etag,
                    lastModifiedHeader = fetched.lastModified,
                    updatedAt = updatedAt,
                ),
            manifestResource =
                UserscriptRevisionResource(
                    entryType = entryType,
                    resourceKey = resourceKey,
                    remoteUrl = fetched.finalUrl,
                    relativePath = relativePath,
                    contentHash = contentHash,
                    mimeType = fetched.mimeType,
                    etag = fetched.etag,
                    lastModifiedHeader = fetched.lastModified,
                ),
            length = fetched.length,
        )
    }

    private fun entityToListItem(entity: UserscriptEntity): UserscriptListItem {
        val metadata = entityToMetadata(entity)
        val unknownGrants = UserscriptCapabilityRegistry.unknownGrants(metadata.grants)
        val worldResolution =
            UserscriptExecutionWorldPolicy.resolve(
                metadata = metadata,
                capabilities = UserscriptRuntimeCapabilities.current(),
            )
        val blockedReasons =
            (
                UserscriptCapabilityRegistry.blockedReasons(metadata.grants) +
                    worldResolution.blockedReasons
                ).distinct()
        return UserscriptListItem(
            id = entity.id,
            name = entity.name,
            namespace = entity.namespace,
            version = entity.version,
            description = entity.description,
            sourceDisplay = entity.sourceDisplay,
            enabled = entity.enabled,
            unknownGrants = unknownGrants,
            blockedReasons = blockedReasons,
            executionWorld = worldResolution.world,
            unsafeWindowMode = worldResolution.unsafeWindowMode,
            runAt = metadata.runAt,
            grants = metadata.grants,
            matches = metadata.matches,
            includes = metadata.includes,
            excludes = metadata.excludes,
            excludeMatches = metadata.excludeMatches,
            connects = metadata.connects,
            requires = metadata.requires,
            resources = metadata.resources,
            homepage = entity.homepageUrl,
            website = metadata.website,
            supportUrl = metadata.supportUrl,
            icons = metadata.icons,
            tags = metadata.tags,
            injectInto = metadata.injectInto,
            sandbox = metadata.sandbox,
            runIn = metadata.runIn,
            noFrames = metadata.noFrames,
            unwrap = metadata.unwrap,
            webRequestRules = metadata.webRequestRules,
            sourceUrl = entity.sourceUrl,
            updateUrl = entity.updateUrl,
            downloadUrl = entity.downloadUrl,
            installedAt = entity.installedAt,
            updatedAt = entity.updatedAt
        )
    }

    private suspend fun resolveExistingScript(preview: UserscriptInstallPreview): UserscriptEntity? {
        preview.existingScriptId?.let { scriptId ->
            return store.getUserscriptById(scriptId)
        }
        val namespace = preview.metadata.namespace
        if (!namespace.isNullOrBlank()) {
            return store.getUserscriptByScope(preview.metadata.name, namespace)
        }
        return store.getAllUserscripts().firstOrNull { entity ->
            entity.name == preview.metadata.name &&
                entity.namespace == null &&
                entity.sourceUrl == preview.sourceUrl
        }
    }

    private fun entityToMetadata(entity: UserscriptEntity): ParsedUserscriptMetadata {
        require(entity.metadataJson.isNotBlank()) {
            "Userscript ${entity.id} has no canonical metadata"
        }
        return json.decodeFromString(entity.metadataJson)
    }

    private fun sha256(raw: String): String {
        return sha256(raw.toByteArray(Charsets.UTF_8))
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun buildRevisionId(
        now: Long,
        sourceHash: String,
    ): String = "$now-${sourceHash.take(16)}-${UUID.randomUUID().toString().take(8)}"

    private fun resolveRemoteUrl(baseUrl: String?, candidate: String): String {
        val trimmed = candidate.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("Empty remote dependency URL")
        }
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("data:", ignoreCase = true)
        ) {
            return trimmed
        }
        val base = baseUrl?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Relative dependency URL without install source: $trimmed")
        return URI(base).resolve(trimmed).toString()
    }

    private fun fetchRemoteAsset(
        url: String,
        targetFile: File,
        maxBytes: Long,
    ): FetchedAsset {
        if (url.startsWith("data:", ignoreCase = true)) {
            val header = url.substringAfter("data:", "").substringBefore(',', "")
            val payload = url.substringAfter(',', "")
            val mimeType = header.substringBefore(';', "application/octet-stream")
            val bytes =
                if (header.contains(";base64", ignoreCase = true)) {
                    Base64.decode(payload, Base64.DEFAULT)
                } else {
                    URLDecoder.decode(payload, Charsets.UTF_8.name()).toByteArray(Charsets.UTF_8)
                }
            require(bytes.size <= maxBytes) {
                "Userscript dependency exceeds its size limit: $url"
            }
            writeSyncedBytes(targetFile, bytes)
            return FetchedAsset(
                file = targetFile,
                mimeType = mimeType,
                etag = null,
                lastModified = null,
                length = bytes.size.toLong(),
                finalUrl = url,
            )
        }
        val response = httpClient.newCall(Request.Builder().url(url).get().build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IllegalStateException("Failed to fetch $url: HTTP ${response.code}")
        }
        val body = response.body ?: run {
            response.close()
            throw IllegalStateException("No response body for $url")
        }
        val declaredLength = body.contentLength()
        if (declaredLength > maxBytes) {
            response.close()
            throw IllegalStateException("Userscript dependency exceeds its size limit: $url")
        }
        val finalUrl = response.request.url.toString()
        val mimeType =
            response.header("Content-Type")
                ?.substringBefore(';')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: guessMimeTypeFromUrl(finalUrl)
        val etag = response.header("ETag")
        val lastModified = response.header("Last-Modified")
        val bytes =
            try {
                body.byteStream().use { input ->
                    input.readUserscriptLimitedBytes(maxBytes)
                }
            } finally {
                response.close()
            }
        writeSyncedBytes(targetFile, bytes)
        return FetchedAsset(
            file = targetFile,
            mimeType = mimeType,
            etag = etag,
            lastModified = lastModified,
            length = bytes.size.toLong(),
            finalUrl = finalUrl,
        )
    }

    private fun guessMimeTypeFromUrl(url: String): String {
        val extension =
            MimeTypeMap.getFileExtensionFromUrl(url)
                ?.lowercase(Locale.ROOT)
                ?.trim('.')
                .orEmpty()
        return if (extension.isBlank()) {
            "application/octet-stream"
        } else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }
    }

    private fun compareVersions(candidate: String, current: String): Int {
        if (candidate == current) {
            return 0
        }
        val candidateParts = candidate.split(Regex("""[^A-Za-z0-9]+""")).filter { it.isNotBlank() }
        val currentParts = current.split(Regex("""[^A-Za-z0-9]+""")).filter { it.isNotBlank() }
        val max = maxOf(candidateParts.size, currentParts.size)
        for (index in 0 until max) {
            val left = candidateParts.getOrNull(index).orEmpty()
            val right = currentParts.getOrNull(index).orEmpty()
            val leftNumber = left.toLongOrNull()
            val rightNumber = right.toLongOrNull()
            val comparison =
                when {
                    leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                    else -> left.compareTo(right, ignoreCase = true)
                }
            if (comparison != 0) {
                return comparison
            }
        }
        return candidate.compareTo(current, ignoreCase = true)
    }

    private data class FetchedAsset(
        val file: File,
        val mimeType: String?,
        val etag: String?,
        val lastModified: String?,
        val length: Long,
        val finalUrl: String,
    )

    private data class StagedUserscriptResource(
        val entity: UserscriptResourceEntity,
        val manifestResource: UserscriptRevisionResource,
        val length: Long,
    )

    private fun UserscriptDraftEntity.toModel(): UserscriptDraft =
        UserscriptDraft(
            draftId = draftId,
            userscriptId = userscriptId,
            baseRevisionId = baseRevisionId,
            sourceHash = sourceHash,
            source = source,
            updatedAt = updatedAt,
        )

    private fun UserscriptRevisionManifest.toModel(
        activeRevisionId: String,
    ): UserscriptRevisionInfo =
        UserscriptRevisionInfo(
            userscriptId = userscriptId,
            revisionId = revisionId,
            revisionNumber = revisionNumber,
            version = version,
            sourceHash = sourceHash,
            sourceType = UserscriptInstallSourceType.valueOf(sourceType),
            sourceUrl = sourceUrl,
            sourceEtag = sourceEtag,
            sourceLastModifiedHeader = sourceLastModifiedHeader,
            createdAt = createdAt,
            active = revisionId == activeRevisionId,
        )

    private fun defaultNewUserscriptSource(): String =
        """
        // ==UserScript==
        // @name New userscript
        // @namespace kiyori.local
        // @version 0.1.0
        // @description Created in Kiyori
        // @match https://*/*
        // @grant none
        // ==/UserScript==

        (() => {
            "use strict";

        })();
        """.trimIndent() + "\n"

}
