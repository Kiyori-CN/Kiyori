package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.storage

import android.content.Context
import android.util.AtomicFile
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ParsedUserscriptMetadata
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptRequireEntry
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptResourceEntry
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptRunAt
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.platform.storage.KiyoriPaths
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val DEFAULT_USER_SCRIPTS_ALLOWED = true

internal class UserscriptJsonStore private constructor(context: Context) {
    @Serializable
    private data class StoreState(
        val schemaVersion: Int = STORE_SCHEMA_VERSION,
        val nextScriptId: Long = 1L,
        val nextResourceId: Long = 1L,
        val userScriptsAllowed: Boolean = DEFAULT_USER_SCRIPTS_ALLOWED,
        val scripts: List<UserscriptEntity> = emptyList(),
        val resources: List<UserscriptResourceEntity> = emptyList(),
    )

    @Serializable
    private data class LogState(
        val nextLogId: Long = 1L,
        val logs: List<UserscriptLogEntity> = emptyList(),
    )

    @Serializable
    private data class ValueState(
        val values: List<UserscriptValueEntity> = emptyList(),
    )

    companion object {
        private const val TAG = "UserscriptJsonStore"
        private const val STORE_SCHEMA_VERSION = 2
        @Volatile
        private var instance: UserscriptJsonStore? = null

        fun getInstance(context: Context): UserscriptJsonStore {
            return instance ?: synchronized(this) {
                instance ?: UserscriptJsonStore(context.applicationContext).also { instance = it }
            }
        }
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
    private val legacyRootDir = KiyoriPaths.webSessionUserscriptsDir()
    internal val layout =
        UserscriptStorageLayout(
            KiyoriPaths.privateWebSessionUserscriptsDir(context),
        ).apply {
            ensureDirectories()
        }
    private val mutex = Mutex()
    private val initialState = initializeStoreState()
    private val stateFlow = MutableStateFlow(initialState)
    private val logFlow = MutableStateFlow(readLogState())

    init {
        recoverTransactions(initialState)
        removeLegacyStorageAfterMigration()
    }

    fun observeUserscripts(): Flow<List<UserscriptEntity>> =
        stateFlow.map { state ->
            state.scripts.sortedWith(
                compareBy<UserscriptEntity> { it.name.lowercase(Locale.ROOT) }
                    .thenBy { it.id },
            )
        }

    fun observeUserScriptsAllowed(): Flow<Boolean> =
        stateFlow
            .map { state -> state.userScriptsAllowed }
            .distinctUntilChanged()

    fun observeRecentLogs(limit: Int): Flow<List<UserscriptLogEntity>> =
        logFlow.map { state ->
            state.logs.take(limit.coerceAtLeast(0))
        }

    suspend fun getUserscriptById(scriptId: Long): UserscriptEntity? =
        mutex.withLock {
            stateFlow.value.scripts.firstOrNull { it.id == scriptId }
        }

    suspend fun getUserscriptByScope(
        name: String,
        namespace: String?,
    ): UserscriptEntity? =
        mutex.withLock {
            stateFlow.value.scripts.firstOrNull { script ->
                script.name == name &&
                    ((namespace == null && script.namespace == null) ||
                        script.namespace == namespace)
            }
        }

    suspend fun getAllUserscripts(): List<UserscriptEntity> =
        mutex.withLock {
            stateFlow.value.scripts
        }

    suspend fun getNextScriptId(): Long =
        mutex.withLock {
            stateFlow.value.nextScriptId
        }

    suspend fun getResourcesForScript(scriptId: Long): List<UserscriptResourceEntity> =
        mutex.withLock {
            stateFlow.value.resources.filter { it.userscriptId == scriptId }
        }

    suspend fun getResourcesForScripts(scriptIds: List<Long>): List<UserscriptResourceEntity> =
        mutex.withLock {
            stateFlow.value.resources.filter { it.userscriptId in scriptIds }
        }

    suspend fun getValuesForScript(scriptId: Long): List<UserscriptValueEntity> =
        mutex.withLock {
            readValueState(scriptId).values
        }

    suspend fun getValue(
        scriptId: Long,
        key: String,
    ): UserscriptValueEntity? =
        mutex.withLock {
            readValueState(scriptId).values.firstOrNull { it.storageKey == key }
        }

    suspend fun insertValue(value: UserscriptValueEntity) {
        mutex.withLock {
            val state = readValueState(value.userscriptId)
            val nextValues = state.values.filterNot { it.storageKey == value.storageKey } + value
            writeValueState(value.userscriptId, state.copy(values = nextValues))
        }
    }

    suspend fun deleteValue(
        scriptId: Long,
        key: String,
    ) {
        mutex.withLock {
            val state = readValueState(scriptId)
            val nextValues = state.values.filterNot { it.storageKey == key }
            if (nextValues.isEmpty()) {
                AtomicFile(layout.valuesFile(scriptId)).delete()
            } else {
                writeValueState(scriptId, state.copy(values = nextValues))
            }
        }
    }

    suspend fun commitUserscriptRevision(
        entity: UserscriptEntity,
        resources: List<UserscriptResourceEntity>,
        isNewScript: Boolean,
    ) {
        mutex.withLock {
            require(entity.id > 0L) { "Userscript transaction has no script ID" }
            require(entity.activeRevisionId.isNotBlank()) {
                "Userscript transaction has no active revision"
            }
            require(File(entity.scriptFilePath).isFile) {
                "Userscript revision source is missing: ${entity.scriptFilePath}"
            }
            require(resources.all { resource -> resource.userscriptId == entity.id }) {
                "Userscript resources contain a different owner"
            }
            require(resources.all { resource -> resource.revisionId == entity.activeRevisionId }) {
                "Userscript resources contain a different revision"
            }
            require(resources.all { resource -> File(resource.localPath).isFile }) {
                "Userscript revision contains a missing resource"
            }

            val state = stateFlow.value
            val existing = state.scripts.firstOrNull { script -> script.id == entity.id }
            if (isNewScript) {
                require(existing == null) {
                    "Userscript ${entity.id} already exists"
                }
                require(entity.id == state.nextScriptId) {
                    "Userscript ID allocation changed during transaction"
                }
            } else {
                require(existing != null) {
                    "Userscript ${entity.id} no longer exists"
                }
            }

            var nextResourceId = state.nextResourceId
            val insertedResources =
                resources.map { resource ->
                    val resourceId =
                        if (resource.id > 0L) {
                            resource.id
                        } else {
                            nextResourceId++
                        }
                    resource.copy(id = resourceId)
                }
            val nextScripts =
                if (isNewScript) {
                    state.scripts + entity
                } else {
                    state.scripts.map { script ->
                        if (script.id == entity.id) entity else script
                    }
                }
            writeStoreState(
                state.copy(
                    schemaVersion = STORE_SCHEMA_VERSION,
                    nextScriptId =
                        if (isNewScript) {
                            state.nextScriptId + 1L
                        } else {
                            state.nextScriptId
                        },
                    nextResourceId = nextResourceId,
                    scripts = nextScripts,
                    resources =
                        state.resources.filterNot { resource ->
                            resource.userscriptId == entity.id
                        } + insertedResources,
                ),
            )
        }
    }

    suspend fun updateUserscript(entity: UserscriptEntity) {
        mutex.withLock {
            val state = stateFlow.value
            require(state.scripts.any { script -> script.id == entity.id }) {
                "Userscript ${entity.id} does not exist"
            }
            writeStoreState(
                state.copy(
                    scripts =
                        state.scripts.map { script ->
                            if (script.id == entity.id) entity else script
                        },
                ),
            )
        }
    }

    suspend fun setUserScriptsAllowed(allowed: Boolean): Boolean =
        mutex.withLock {
            val state = stateFlow.value
            if (state.userScriptsAllowed == allowed) {
                false
            } else {
                writeStoreState(state.copy(userScriptsAllowed = allowed))
                true
            }
        }

    suspend fun insertLog(log: UserscriptLogEntity): Long =
        mutex.withLock {
            val state = logFlow.value
            val nextId = state.nextLogId
            val inserted = log.copy(id = nextId)
            writeLogState(
                state.copy(
                    nextLogId = nextId + 1L,
                    logs = listOf(inserted) + state.logs,
                ),
            )
            nextId
        }

    suspend fun trimLogs(keepCount: Int) {
        mutex.withLock {
            val state = logFlow.value
            writeLogState(state.copy(logs = state.logs.take(keepCount.coerceAtLeast(0))))
        }
    }

    suspend fun deleteUserscriptById(scriptId: Long) {
        mutex.withLock {
            val state = stateFlow.value
            writeStoreState(
                state.copy(
                    scripts = state.scripts.filterNot { it.id == scriptId },
                    resources = state.resources.filterNot { it.userscriptId == scriptId },
                ),
            )
            AtomicFile(layout.valuesFile(scriptId)).delete()
            AtomicFile(
                layout.draftFile(UserscriptStorageLayout.draftIdForScript(scriptId)),
            ).delete()
            val logs = logFlow.value
            writeLogState(logs.copy(logs = logs.logs.filterNot { it.userscriptId == scriptId }))
        }
    }

    suspend fun saveDraft(draft: UserscriptDraftEntity) {
        mutex.withLock {
            draft.userscriptId?.let { userscriptId ->
                require(stateFlow.value.scripts.any { script -> script.id == userscriptId }) {
                    "Userscript $userscriptId does not exist"
                }
            }
            writeAtomicText(
                layout.draftFile(draft.draftId),
                json.encodeToString(draft),
            )
        }
    }

    suspend fun readDraft(draftId: String): UserscriptDraftEntity? =
        mutex.withLock {
            val file = layout.draftFile(draftId)
            if (!atomicFileExists(file)) {
                null
            } else {
                json.decodeFromString(readAtomicText(file))
            }
        }

    suspend fun listDrafts(): List<UserscriptDraftEntity> =
        mutex.withLock {
            layout.draftDir
                .listFiles()
                .orEmpty()
                .filter { file -> file.isFile && file.extension == "json" }
                .map { file ->
                    json.decodeFromString<UserscriptDraftEntity>(readAtomicText(file))
                }
                .sortedByDescending(UserscriptDraftEntity::updatedAt)
        }

    suspend fun deleteDraft(draftId: String) {
        mutex.withLock {
            AtomicFile(layout.draftFile(draftId)).delete()
        }
    }

    suspend fun listRevisionManifests(scriptId: Long): List<UserscriptRevisionManifest> =
        mutex.withLock {
            val scriptDir = layout.revisionScriptDir(scriptId)
            if (!scriptDir.isDirectory) {
                emptyList()
            } else {
                scriptDir
                    .listFiles()
                    .orEmpty()
                    .filter { file -> file.isDirectory }
                    .map { revisionDir ->
                        val manifestFile = File(revisionDir, "manifest.json")
                        require(atomicFileExists(manifestFile)) {
                            "Userscript revision manifest is missing: ${revisionDir.absolutePath}"
                        }
                        json.decodeFromString<UserscriptRevisionManifest>(
                            readAtomicText(manifestFile),
                        )
                    }
                    .sortedByDescending(UserscriptRevisionManifest::revisionNumber)
            }
        }

    suspend fun readRevisionSource(
        scriptId: Long,
        revisionId: String,
    ): String =
        mutex.withLock {
            val sourceFile = layout.revisionSourceFile(scriptId, revisionId)
            require(sourceFile.isFile) {
                "Userscript revision source is missing: ${sourceFile.absolutePath}"
            }
            sourceFile.readText()
        }

    suspend fun beginTransaction(journal: UserscriptTransactionJournal) {
        mutex.withLock {
            require(journal.transactionId.isNotBlank()) {
                "Userscript transaction ID is empty"
            }
            require(journal.revisionId.isNotBlank()) {
                "Userscript revision ID is empty"
            }
            val journalFile = layout.journalFile(journal.transactionId)
            require(!atomicFileExists(journalFile)) {
                "Userscript transaction already exists: ${journal.transactionId}"
            }
            val transactionDir = layout.transactionDir(journal.transactionId)
            require(!transactionDir.exists()) {
                "Userscript staging directory already exists: ${journal.transactionId}"
            }
            require(transactionDir.mkdirs()) {
                "Unable to create userscript staging directory"
            }
            writeAtomicText(journalFile, json.encodeToString(journal))
        }
    }

    suspend fun completeTransaction(journal: UserscriptTransactionJournal) {
        mutex.withLock {
            val transactionDir = layout.transactionDir(journal.transactionId)
            if (transactionDir.exists() && !transactionDir.deleteRecursively()) {
                AppLogger.w(
                    TAG,
                    "Unable to remove committed userscript staging directory ${transactionDir.absolutePath}",
                )
            }
            AtomicFile(layout.journalFile(journal.transactionId)).delete()
        }
    }

    suspend fun abortTransaction(journal: UserscriptTransactionJournal) {
        mutex.withLock {
            val activeRevisionId =
                stateFlow.value.scripts
                    .firstOrNull { script -> script.id == journal.userscriptId }
                    ?.activeRevisionId
            if (
                decideUserscriptTransactionRecovery(
                    activeRevisionId = activeRevisionId,
                    journalRevisionId = journal.revisionId,
                ) == UserscriptTransactionRecoveryDecision.REMOVE_UNCOMMITTED_REVISION
            ) {
                val revisionDir =
                    layout.revisionDir(
                        scriptId = journal.userscriptId,
                        revisionId = journal.revisionId,
                    )
                if (revisionDir.exists() && !revisionDir.deleteRecursively()) {
                    AppLogger.w(
                        TAG,
                        "Unable to remove aborted userscript revision ${revisionDir.absolutePath}",
                    )
                }
            }
            val transactionDir = layout.transactionDir(journal.transactionId)
            if (transactionDir.exists() && !transactionDir.deleteRecursively()) {
                AppLogger.w(
                    TAG,
                    "Unable to remove aborted userscript staging directory ${transactionDir.absolutePath}",
                )
            }
            AtomicFile(layout.journalFile(journal.transactionId)).delete()
        }
    }

    private fun initializeStoreState(): StoreState {
        if (!atomicFileExists(layout.registryFile)) {
            migrateLegacyStore()
        }
        if (!atomicFileExists(layout.registryFile)) {
            writeAtomicText(
                layout.registryFile,
                json.encodeToString(StoreState()),
            )
        }
        val state = json.decodeFromString<StoreState>(readAtomicText(layout.registryFile))
        require(state.schemaVersion == STORE_SCHEMA_VERSION) {
            "Unsupported userscript registry schema ${state.schemaVersion}"
        }
        validateStoreState(state)
        return state
    }

    private fun migrateLegacyStore() {
        val legacyRegistry = File(File(legacyRootDir, "state"), "registry.json")
        if (!legacyRegistry.isFile) {
            return
        }

        layout.revisionRootDir.deleteRecursively()
        layout.transactionRootDir.deleteRecursively()
        require(layout.revisionRootDir.mkdirs()) {
            "Unable to prepare private userscript revisions for migration"
        }
        require(layout.transactionRootDir.mkdirs()) {
            "Unable to prepare private userscript staging for migration"
        }

        val legacyState = json.decodeFromString<StoreState>(legacyRegistry.readText())
        val migratedScripts = ArrayList<UserscriptEntity>(legacyState.scripts.size)
        val migratedResources = ArrayList<UserscriptResourceEntity>(legacyState.resources.size)

        legacyState.scripts.forEach { entity ->
            val sourceFile = File(entity.scriptFilePath)
            require(sourceFile.isFile) {
                "Legacy userscript source is missing: ${sourceFile.absolutePath}"
            }
            val sourceBytes = sourceFile.readBytes()
            val sourceHash = sha256(sourceBytes)
            require(entity.sourceHash.isBlank() || entity.sourceHash.equals(sourceHash, ignoreCase = true)) {
                "Legacy userscript source hash mismatch for ${entity.name}"
            }

            val revisionNumber = 1L
            val revisionId =
                "legacy-${entity.updatedAt}-${sourceHash.take(16)}"
            val transactionId = "migration-${entity.id}"
            val stagedRevisionDir = layout.stagedRevisionDir(transactionId)
            val finalRevisionDir = layout.revisionDir(entity.id, revisionId)
            stagedRevisionDir.parentFile?.deleteRecursively()
            finalRevisionDir.deleteRecursively()
            require(stagedRevisionDir.mkdirs()) {
                "Unable to create migration staging for ${entity.name}"
            }

            val stagedSourceFile = File(stagedRevisionDir, "source.user.js")
            writeSyncedBytes(stagedSourceFile, sourceBytes)
            val finalSourceFile = File(finalRevisionDir, "source.user.js")
            val revisionResources = ArrayList<UserscriptRevisionResource>()
            val scriptResources =
                legacyState.resources.filter { resource -> resource.userscriptId == entity.id }
            scriptResources.forEachIndexed { index, resource ->
                val legacyFile = File(resource.localPath)
                require(legacyFile.isFile) {
                    "Legacy userscript resource is missing: ${legacyFile.absolutePath}"
                }
                val resourceBytes = legacyFile.readBytes()
                val relativePath =
                    legacyResourceRelativePath(
                        resource = resource,
                        index = index,
                        sourceFile = legacyFile,
                    )
                val stagedResourceFile = File(stagedRevisionDir, relativePath)
                writeSyncedBytes(stagedResourceFile, resourceBytes)
                revisionResources +=
                    UserscriptRevisionResource(
                        entryType = resource.entryType,
                        resourceKey = resource.resourceKey,
                        remoteUrl = resource.remoteUrl,
                        relativePath = relativePath,
                        contentHash = sha256(resourceBytes),
                        mimeType = resource.mimeType,
                        etag = resource.etag,
                        lastModifiedHeader = resource.lastModifiedHeader,
                    )
                migratedResources +=
                    resource.copy(
                        revisionId = revisionId,
                        localPath = File(finalRevisionDir, relativePath).absolutePath,
                    )
            }

            val manifest =
                UserscriptRevisionManifest(
                    userscriptId = entity.id,
                    revisionId = revisionId,
                    revisionNumber = revisionNumber,
                    name = entity.name,
                    namespace = entity.namespace,
                    version = entity.version,
                    sourceHash = sourceHash,
                    sourceType = entity.installSourceType,
                    sourceUrl = entity.sourceUrl,
                    sourceEtag = null,
                    sourceLastModifiedHeader = null,
                    createdAt = entity.updatedAt,
                    resources = revisionResources,
                )
            writeAtomicText(
                File(stagedRevisionDir, "manifest.json"),
                json.encodeToString(manifest),
            )
            moveDirectoryAtomically(stagedRevisionDir, finalRevisionDir)
            layout.transactionDir(transactionId).deleteRecursively()

            migratedScripts +=
                entity.copy(
                    sourceHash = sourceHash,
                    scriptFilePath = finalSourceFile.absolutePath,
                    activeRevisionId = revisionId,
                    revisionNumber = revisionNumber,
                    metadataJson = canonicalMetadataJson(entity),
                )
        }

        val migratedState =
            legacyState.copy(
                schemaVersion = STORE_SCHEMA_VERSION,
                scripts = migratedScripts,
                resources = migratedResources,
            )
        migrateLegacyLogs()
        migrateLegacyValues(migratedScripts.map(UserscriptEntity::id))
        validateStoreState(migratedState)
        writeAtomicText(layout.registryFile, json.encodeToString(migratedState))
        AppLogger.i(
            TAG,
            "Migrated ${migratedScripts.size} userscripts into private revision storage",
        )
    }

    private fun migrateLegacyLogs() {
        val legacyLogFile = File(File(legacyRootDir, "state"), "logs.json")
        if (!legacyLogFile.isFile) {
            return
        }
        val state = json.decodeFromString<LogState>(legacyLogFile.readText())
        writeAtomicText(layout.logFile, json.encodeToString(state))
    }

    private fun migrateLegacyValues(scriptIds: List<Long>) {
        val legacyValuesDir = File(File(legacyRootDir, "state"), "values")
        scriptIds.forEach { scriptId ->
            val legacyFile = File(legacyValuesDir, "$scriptId.json")
            if (legacyFile.isFile) {
                val state = json.decodeFromString<ValueState>(legacyFile.readText())
                writeAtomicText(layout.valuesFile(scriptId), json.encodeToString(state))
            }
        }
    }

    private fun removeLegacyStorageAfterMigration() {
        if (!legacyRootDir.exists()) {
            return
        }
        if (legacyRootDir.canonicalFile == layout.rootDir.canonicalFile) {
            throw IllegalStateException("Legacy userscript root overlaps private canonical root")
        }
        if (!legacyRootDir.deleteRecursively()) {
            AppLogger.w(
                TAG,
                "Private userscript migration completed, but legacy storage could not be removed: " +
                    legacyRootDir.absolutePath,
            )
        }
    }

    private fun recoverTransactions(state: StoreState) {
        val journalFiles =
            layout.journalDir
                .listFiles()
                .orEmpty()
                .filter { file -> file.isFile && file.extension == "json" }
                .sortedBy { file -> file.name }
        val journalTransactionIds = HashSet<String>()
        journalFiles.forEach { file ->
            val journal =
                json.decodeFromString<UserscriptTransactionJournal>(
                    readAtomicText(file),
                )
            require(journalTransactionIds.add(journal.transactionId)) {
                "Duplicate userscript transaction journal ${journal.transactionId}"
            }
            val activeRevisionId =
                state.scripts
                    .firstOrNull { script -> script.id == journal.userscriptId }
                    ?.activeRevisionId
            if (
                decideUserscriptTransactionRecovery(
                    activeRevisionId = activeRevisionId,
                    journalRevisionId = journal.revisionId,
                ) == UserscriptTransactionRecoveryDecision.REMOVE_UNCOMMITTED_REVISION
            ) {
                layout
                    .revisionDir(journal.userscriptId, journal.revisionId)
                    .takeIf { file -> file.exists() }
                    ?.deleteRecursively()
            }
            layout
                .transactionDir(journal.transactionId)
                .takeIf { file -> file.exists() }
                ?.deleteRecursively()
            AtomicFile(file).delete()
        }

        layout.transactionRootDir
            .listFiles()
            .orEmpty()
            .filter { file -> file.isDirectory }
            .filterNot { directory -> directory.name in journalTransactionIds }
            .forEach { orphan ->
                if (!orphan.deleteRecursively()) {
                    AppLogger.w(
                        TAG,
                        "Unable to remove orphan userscript staging directory ${orphan.absolutePath}",
                    )
                }
            }
    }

    private fun validateStoreState(state: StoreState) {
        require(state.nextScriptId > 0L) {
            "Userscript registry has an invalid next script ID"
        }
        require(state.nextResourceId > 0L) {
            "Userscript registry has an invalid next resource ID"
        }
        val scriptIds = HashSet<Long>()
        state.scripts.forEach { entity ->
            require(entity.id > 0L && scriptIds.add(entity.id)) {
                "Userscript registry contains an invalid or duplicate script ID ${entity.id}"
            }
            require(entity.activeRevisionId.isNotBlank()) {
                "Userscript ${entity.id} has no active revision"
            }
            require(entity.revisionNumber > 0L) {
                "Userscript ${entity.id} has no revision number"
            }
            require(entity.metadataJson.isNotBlank()) {
                "Userscript ${entity.id} has no canonical metadata"
            }
            val metadata =
                json.decodeFromString<ParsedUserscriptMetadata>(entity.metadataJson)
            require(metadata.name == entity.name) {
                "Userscript ${entity.id} metadata name does not match the registry"
            }
            require(metadata.namespace == entity.namespace) {
                "Userscript ${entity.id} metadata namespace does not match the registry"
            }
            require(metadata.version == entity.version) {
                "Userscript ${entity.id} metadata version does not match the registry"
            }
            val expectedSource =
                layout.revisionSourceFile(
                    scriptId = entity.id,
                    revisionId = entity.activeRevisionId,
                )
            require(File(entity.scriptFilePath).canonicalFile == expectedSource.canonicalFile) {
                "Userscript ${entity.id} source path is outside its active revision"
            }
            require(expectedSource.isFile) {
                "Userscript ${entity.id} active source is missing"
            }
            require(sha256(expectedSource.readBytes()).equals(entity.sourceHash, ignoreCase = true)) {
                "Userscript ${entity.id} active source hash mismatch"
            }
            val manifestFile =
                layout.revisionManifestFile(
                    scriptId = entity.id,
                    revisionId = entity.activeRevisionId,
                )
            require(atomicFileExists(manifestFile)) {
                "Userscript ${entity.id} active revision manifest is missing"
            }
            val manifest =
                json.decodeFromString<UserscriptRevisionManifest>(readAtomicText(manifestFile))
            require(manifest.schemaVersion == 1) {
                "Userscript ${entity.id} has an unsupported revision manifest"
            }
            require(manifest.userscriptId == entity.id) {
                "Userscript ${entity.id} revision manifest has a different owner"
            }
            require(manifest.revisionId == entity.activeRevisionId) {
                "Userscript ${entity.id} revision manifest has a different revision ID"
            }
            require(manifest.revisionNumber == entity.revisionNumber) {
                "Userscript ${entity.id} revision number does not match its manifest"
            }
            require(manifest.sourceHash.equals(entity.sourceHash, ignoreCase = true)) {
                "Userscript ${entity.id} revision manifest source hash mismatch"
            }
        }
        val resourceIds = HashSet<Long>()
        state.resources.forEach { resource ->
            require(resource.id > 0L && resourceIds.add(resource.id)) {
                "Userscript registry contains an invalid or duplicate resource ID ${resource.id}"
            }
            require(resource.userscriptId in scriptIds) {
                "Userscript resource ${resource.id} has no installed owner"
            }
            val owner =
                state.scripts.first { entity -> entity.id == resource.userscriptId }
            require(resource.revisionId == owner.activeRevisionId) {
                "Userscript resource ${resource.id} is not part of the active revision"
            }
            val revisionDir =
                layout.revisionDir(
                    scriptId = resource.userscriptId,
                    revisionId = resource.revisionId,
                )
            require(File(resource.localPath).canonicalFile.toPath().startsWith(revisionDir.canonicalFile.toPath())) {
                "Userscript resource ${resource.id} escapes its revision"
            }
            require(File(resource.localPath).isFile) {
                "Userscript resource ${resource.id} is missing"
            }
            val manifest =
                json.decodeFromString<UserscriptRevisionManifest>(
                    readAtomicText(
                        layout.revisionManifestFile(
                            scriptId = resource.userscriptId,
                            revisionId = resource.revisionId,
                        ),
                    ),
                )
            val manifestResource =
                manifest.resources.singleOrNull { entry ->
                    entry.entryType == resource.entryType &&
                        entry.resourceKey == resource.resourceKey &&
                        File(revisionDir, entry.relativePath).canonicalFile ==
                        File(resource.localPath).canonicalFile
                }
                    ?: throw IllegalStateException(
                        "Userscript resource ${resource.id} is absent from its revision manifest",
                    )
            require(
                sha256(File(resource.localPath).readBytes())
                    .equals(manifestResource.contentHash, ignoreCase = true),
            ) {
                "Userscript resource ${resource.id} content hash mismatch"
            }
        }
        require(state.nextScriptId > (state.scripts.maxOfOrNull(UserscriptEntity::id) ?: 0L)) {
            "Userscript registry next script ID is not monotonic"
        }
        require(state.nextResourceId > (state.resources.maxOfOrNull(UserscriptResourceEntity::id) ?: 0L)) {
            "Userscript registry next resource ID is not monotonic"
        }
    }

    private fun readLogState(): LogState {
        if (!atomicFileExists(layout.logFile)) {
            val empty = LogState()
            writeAtomicText(layout.logFile, json.encodeToString(empty))
            return empty
        }
        return json.decodeFromString(readAtomicText(layout.logFile))
    }

    private fun writeStoreState(state: StoreState) {
        validateStoreState(state)
        writeAtomicText(layout.registryFile, json.encodeToString(state))
        stateFlow.value = state
    }

    private fun writeLogState(state: LogState) {
        writeAtomicText(layout.logFile, json.encodeToString(state))
        logFlow.value = state
    }

    private fun readValueState(scriptId: Long): ValueState {
        val file = layout.valuesFile(scriptId)
        if (!atomicFileExists(file)) {
            return ValueState()
        }
        return json.decodeFromString(readAtomicText(file))
    }

    private fun writeValueState(
        scriptId: Long,
        state: ValueState,
    ) {
        writeAtomicText(layout.valuesFile(scriptId), json.encodeToString(state))
    }

    private fun legacyResourceRelativePath(
        resource: UserscriptResourceEntity,
        index: Int,
        sourceFile: File,
    ): String {
        val directory =
            when (resource.entryType) {
                "require" -> "requires"
                "resource" -> "resources"
                else -> throw IllegalStateException(
                    "Unknown legacy userscript resource type ${resource.entryType}",
                )
            }
        val extension =
            sourceFile.extension
                .lowercase(Locale.ROOT)
                .takeIf { value -> value.matches(Regex("[a-z0-9]{1,12}")) }
                ?: if (resource.entryType == "require") "js" else "bin"
        return "$directory/${index.toString().padStart(4, '0')}.$extension"
    }

    private fun canonicalMetadataJson(entity: UserscriptEntity): String {
        if (entity.metadataJson.isNotBlank()) {
            return json.encodeToString(
                json.decodeFromString<ParsedUserscriptMetadata>(entity.metadataJson),
            )
        }
        return json.encodeToString(
            ParsedUserscriptMetadata(
                name = entity.name,
                namespace = entity.namespace,
                version = entity.version,
                description = entity.description,
                author = entity.author,
                homepage = entity.homepageUrl,
                downloadUrl = entity.downloadUrl,
                updateUrl = entity.updateUrl,
                runAt = UserscriptRunAt.fromRaw(entity.runAt),
                grants = json.decodeFromString(entity.grantsJson),
                matches = json.decodeFromString(entity.matchesJson),
                includes = json.decodeFromString(entity.includesJson),
                excludes = json.decodeFromString(entity.excludesJson),
                excludeMatches = json.decodeFromString(entity.excludeMatchesJson),
                connects = json.decodeFromString(entity.connectsJson),
                requires =
                    json.decodeFromString<List<UserscriptRequireEntry>>(entity.requiresJson),
                resources =
                    json.decodeFromString<List<UserscriptResourceEntry>>(entity.resourcesJson),
                noFrames = entity.noFrames,
            ),
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
}
