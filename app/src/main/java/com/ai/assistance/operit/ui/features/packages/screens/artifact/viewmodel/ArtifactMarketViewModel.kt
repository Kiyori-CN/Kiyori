package com.ai.assistance.operit.ui.features.packages.screens.artifact.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.api.MarketV2EntryUpdateRequest
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.canRetryRejectedMarketRegistration
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.ui.features.packages.market.ArtifactMarketScope
import com.ai.assistance.operit.ui.features.packages.market.ArtifactPublishClusterContext
import com.ai.assistance.operit.ui.features.packages.market.GitHubForgePublishService
import com.ai.assistance.operit.ui.features.packages.market.GitHubReleaseCatalog
import com.ai.assistance.operit.ui.features.packages.market.LocalPublishableArtifact
import com.ai.assistance.operit.ui.features.packages.market.MarketRegistrationPayload
import com.ai.assistance.operit.ui.features.packages.market.PublishArtifactRequest
import com.ai.assistance.operit.ui.features.packages.market.PublishArtifactSource
import com.ai.assistance.operit.ui.features.packages.market.PublishArtifactType
import com.ai.assistance.operit.ui.features.packages.market.PublishAttemptResult
import com.ai.assistance.operit.ui.features.packages.market.PublishProgressStage
import com.ai.assistance.operit.ui.features.packages.market.formatSupportedAppVersions
import com.kiyori.capability.extensions.market.normalizeMarketArtifactId
import com.ai.assistance.operit.ui.features.packages.market.normalizeAppVersionOrNull
import com.kiyori.capability.extensions.market.sameArtifactRuntimePackageId
import com.ai.assistance.operit.ui.features.packages.market.toMarketStatsType
import com.ai.assistance.operit.ui.features.packages.market.toRankMetric
import com.kiyori.capability.extensions.market.validateStandaloneArtifactRuntimePackageId
import com.ai.assistance.operit.ui.features.packages.market.validateSupportedAppVersions
import com.ai.assistance.operit.ui.features.github.GitHubOAuthCoordinator
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job

class ArtifactMarketViewModel(
    private val context: Context,
    private val scope: ArtifactMarketScope,
    private val marketStatsApiService: MarketStatsApiService = MarketStatsApiService(),
    private val githubAuth: GitHubAuthPreferences = GitHubAuthPreferences.getInstance(context),
    private val forgePublishService: GitHubForgePublishService = GitHubForgePublishService(context, GitHubApiService(context)),
    private val packageManager: PackageManager = PackageManager.getInstance(context, AIToolHandler.getInstance(context))
) : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _publishableArtifacts = MutableStateFlow<List<LocalPublishableArtifact>>(emptyList())
    val publishableArtifacts: StateFlow<List<LocalPublishableArtifact>> = _publishableArtifacts.asStateFlow()

    private val _publishProgressStage = MutableStateFlow(PublishProgressStage.IDLE)
    val publishProgressStage: StateFlow<PublishProgressStage> = _publishProgressStage.asStateFlow()

    private val _publishMessage = MutableStateFlow<String?>(null)
    val publishMessage: StateFlow<String?> = _publishMessage.asStateFlow()

    private val _publishErrorMessage = MutableStateFlow<String?>(null)
    val publishErrorMessage: StateFlow<String?> = _publishErrorMessage.asStateFlow()

    private val _publishSuccessMessage = MutableStateFlow<String?>(null)
    val publishSuccessMessage: StateFlow<String?> = _publishSuccessMessage.asStateFlow()

    private val _requiresForgeInitialization = MutableStateFlow(false)
    val requiresForgeInitialization: StateFlow<Boolean> = _requiresForgeInitialization.asStateFlow()

    private val _githubReleaseCatalog = MutableStateFlow<GitHubReleaseCatalog?>(null)
    val githubReleaseCatalog: StateFlow<GitHubReleaseCatalog?> = _githubReleaseCatalog.asStateFlow()

    private val _githubReleaseCatalogError = MutableStateFlow<String?>(null)
    val githubReleaseCatalogError: StateFlow<String?> = _githubReleaseCatalogError.asStateFlow()

    private val _isLoadingGitHubReleaseCatalog = MutableStateFlow(false)
    val isLoadingGitHubReleaseCatalog: StateFlow<Boolean> = _isLoadingGitHubReleaseCatalog.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> =
        githubAuth.isLoggedInFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val supportedTypes = scope.supportedTypes()
    private var pendingPublishRequest: PublishArtifactRequest? = null
    private val _pendingRegistration = MutableStateFlow<PublishAttemptResult.RegistrationFailed?>(null)
    val pendingRegistration = _pendingRegistration.asStateFlow()
    private var catalogJob: Job? = null
    private var catalogGeneration = 0L

    init {
        refreshPublishableArtifacts()
    }

    fun initiateGitHubLogin(context: Context) {
        viewModelScope.launch {
            try {
                val authUrl = GitHubOAuthCoordinator(context).createExternalAuthorizationUrl()
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to open GitHub login"
                AppLogger.e(TAG, "Failed to initiate GitHub login", e)
            }
        }
    }

    fun logoutFromGitHub() {
        viewModelScope.launch {
            try {
                githubAuth.logout()
                Toast.makeText(context, "Logged out from GitHub", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to logout from GitHub"
                AppLogger.e(TAG, "Failed to logout from GitHub", e)
            }
        }
    }

    fun refreshPublishableArtifacts() {
        if (_isLoading.value) return
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val artifacts =
                    withContext(Dispatchers.IO) {
                        packageManager.getPublishablePackageSources()
                            .mapNotNull { source ->
                                val type = inferArtifactType(source.isToolPkg, source.fileExtension) ?: return@mapNotNull null
                                if (type !in supportedTypes) return@mapNotNull null
                                LocalPublishableArtifact(
                                    type = type,
                                    packageName = source.packageName,
                                    displayName = source.displayName,
                                    description = source.description,
                                    sourceFile = File(source.sourcePath),
                                    inferredVersion = source.inferredVersion
                                )
                            }
                            .sortedWith(compareBy<LocalPublishableArtifact> { it.type.ordinal }.thenBy { it.displayName.lowercase() })
                    }
                _publishableArtifacts.value = artifacts
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _errorMessage.value = error.message ?: "Failed to load local artifacts"
            }
        }.invokeOnCompletion { _isLoading.value = false }
    }

    fun requestPublish(
        packageName: String,
        displayName: String,
        description: String,
        detail: String,
        categoryId: String,
        allowPublicUpdates: Boolean,
        version: String,
        minSupportedAppVersion: String?,
        maxSupportedAppVersion: String?,
        publishContext: ArtifactPublishClusterContext? = null,
        source: PublishArtifactSource
    ) {
        val localArtifact = _publishableArtifacts.value.firstOrNull { it.packageName == packageName }
        if (localArtifact == null) {
            _publishErrorMessage.value = "Local artifact not found"
            return
        }

        val resolvedDisplayName =
            publishContext?.takeUnless { it.canEditEntry }
                ?.lockedDisplayName
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: displayName

        val request =
            PublishArtifactRequest(
                localArtifact = localArtifact,
                displayName = resolvedDisplayName,
                description = description,
                detail = detail,
                categoryId = categoryId,
                allowPublicUpdates = allowPublicUpdates,
                version = version,
                minSupportedAppVersion = minSupportedAppVersion,
                maxSupportedAppVersion = maxSupportedAppVersion,
                publishContext = publishContext,
                source = source
            )
        executePublish(request, allowCreateForgeRepo = false)
    }

    fun loadGitHubReleaseCatalog(repositoryUrl: String) {
        clearGitHubReleaseCatalog()
        val generation = catalogGeneration
        _isLoadingGitHubReleaseCatalog.value = true
        catalogJob = viewModelScope.launch {
            try {
                val catalog = forgePublishService.loadGitHubReleaseCatalog(repositoryUrl).getOrThrow()
                if (generation == catalogGeneration) _githubReleaseCatalog.value = catalog
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (generation == catalogGeneration) {
                    _githubReleaseCatalogError.value = error.message ?: "Failed to load GitHub Releases"
                }
            }
        }.also { job -> job.invokeOnCompletion {
            if (generation == catalogGeneration) _isLoadingGitHubReleaseCatalog.value = false
        } }
    }

    fun clearGitHubReleaseCatalog() {
        catalogGeneration++
        catalogJob?.cancel()
        catalogJob = null
        _githubReleaseCatalog.value = null
        _githubReleaseCatalogError.value = null
        _isLoadingGitHubReleaseCatalog.value = false
    }

    fun updatePublishedArtifact(
        entry: com.ai.assistance.operit.data.api.MarketV2Entry,
        displayName: String,
        description: String,
        detail: String,
        categoryId: String,
        allowPublicUpdates: Boolean,
        minSupportedAppVersion: String?,
        maxSupportedAppVersion: String?
    ) {
        if (!_publishProgressStage.compareAndSet(PublishProgressStage.IDLE, PublishProgressStage.VALIDATING)) return
        viewModelScope.launch {
            try {
                check(githubAuth.isLoggedIn()) { "GitHub login required" }
                val accountId = githubAuth.getCurrentUserInfo()?.id
                _publishErrorMessage.value = null
                _publishSuccessMessage.value = null
                _publishMessage.value = getText(R.string.artifact_publish_updating_published_node)
                validateSupportedAppVersions(minSupportedAppVersion, maxSupportedAppVersion)
                val type = PublishArtifactType.fromWireValue(entry.type)
                    ?: throw IllegalStateException("Invalid artifact metadata")
                val payload = buildUpdatedMarketRegistrationPayload(
                    entry = entry,
                    type = type,
                    displayName = displayName,
                    description = description,
                    detail = detail,
                    minSupportedAppVersion = minSupportedAppVersion,
                    maxSupportedAppVersion = maxSupportedAppVersion
                )
                ensureArtifactDisplayNameAvailable(payload.displayName, entry.id)
                check(accountId != null && githubAuth.isLoggedIn() && githubAuth.getCurrentUserInfo()?.id == accountId) {
                    "GitHub account changed during validation"
                }
                marketStatsApiService.updateEntry(
                    entry.id,
                    MarketV2EntryUpdateRequest(
                        title = payload.displayName,
                        description = payload.description,
                        detail = payload.projectDescription,
                        categoryId = categoryId,
                        allowPublicUpdates = allowPublicUpdates
                    )
                ).getOrThrow()
                _publishProgressStage.value = PublishProgressStage.COMPLETED
                _publishMessage.value = null
                _publishSuccessMessage.value = appendMarketScheduleNotice(
                    getText(R.string.artifact_publish_node_updated, payload.displayName)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _publishErrorMessage.value = error.message ?: "Failed to update artifact"
                AppLogger.e(TAG, "Failed to update published artifact", error)
            } finally {
                if (_publishProgressStage.value != PublishProgressStage.COMPLETED) {
                    _publishProgressStage.value = PublishProgressStage.IDLE
                    _publishMessage.value = null
                }
            }
        }.invokeOnCompletion {
            _publishProgressStage.compareAndSet(PublishProgressStage.VALIDATING, PublishProgressStage.IDLE)
        }
    }

    fun confirmForgeInitializationAndPublish() {
        if (!_requiresForgeInitialization.value) return
        val request = pendingPublishRequest ?: return
        _requiresForgeInitialization.value = false
        executePublish(request, allowCreateForgeRepo = true)
    }

    fun dismissForgeInitializationPrompt() {
        if (_publishProgressStage.value != PublishProgressStage.IDLE) return
        pendingPublishRequest = null
        _requiresForgeInitialization.value = false
        _publishProgressStage.value = PublishProgressStage.IDLE
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearPublishMessages() {
        _publishMessage.value = null
        _publishErrorMessage.value = null
        _publishSuccessMessage.value = null
        if (_publishProgressStage.value == PublishProgressStage.COMPLETED) {
            _publishProgressStage.value = PublishProgressStage.IDLE
        }
    }

    fun clearPendingMarketRegistrationRetry() {
        if (_pendingRegistration.value != null) return
        _publishErrorMessage.value = null
        if (_publishProgressStage.value == PublishProgressStage.IDLE) {
            _publishMessage.value = null
        }
    }

    private fun executePublish(request: PublishArtifactRequest, allowCreateForgeRepo: Boolean) {
        if (_pendingRegistration.value != null) return
        if (!_publishProgressStage.compareAndSet(PublishProgressStage.IDLE, PublishProgressStage.VALIDATING)) return
        _publishMessage.value = getText(R.string.artifact_publish_checking_identity_conflicts)
        viewModelScope.launch {
            try {
                if (!githubAuth.isLoggedIn()) {
                    _publishErrorMessage.value = "GitHub login required"
                    return@launch
                }

                val resolvedDisplayName = resolvePublishDisplayName(request)
                val resolvedRequest =
                    if (resolvedDisplayName == request.displayName) {
                        request
                    } else {
                        request.copy(displayName = resolvedDisplayName)
                    }

                _publishErrorMessage.value = null
                _publishSuccessMessage.value = null
                pendingPublishRequest = resolvedRequest
                _publishProgressStage.value = PublishProgressStage.VALIDATING
                _publishMessage.value = getText(R.string.artifact_publish_checking_identity_conflicts)

                if (resolvedRequest.publishContext == null) {
                    ensureFreshPublishIdentityAvailable(
                        displayName = resolvedRequest.displayName,
                        runtimePackageId = resolvedRequest.localArtifact.packageName
                    )
                }
                validateContinuationVersion(resolvedRequest)

                forgePublishService.publishArtifact(
                    request = resolvedRequest,
                    allowCreateForgeRepo = allowCreateForgeRepo,
                    onProgress = { stage ->
                        // 完成态由本ViewModel接收结果后发布，避免服务回调提前解锁表单。
                        if (stage != PublishProgressStage.COMPLETED) {
                            _publishProgressStage.value = stage
                            _publishMessage.value = stageMessage(stage)
                        }
                    }
                ).fold(
                    onSuccess = { result ->
                        when (result) {
                            is PublishAttemptResult.NeedsForgeInitialization -> {
                                _requiresForgeInitialization.value = true
                                _publishProgressStage.value = PublishProgressStage.IDLE
                                _publishMessage.value = null
                            }

                            is PublishAttemptResult.Success -> {
                                pendingPublishRequest = null
                                _publishProgressStage.value = PublishProgressStage.COMPLETED
                                _publishSuccessMessage.value = buildSuccessMessage(result.payload)
                            }

                            is PublishAttemptResult.RegistrationFailed -> {
                                pendingPublishRequest = null
                                _pendingRegistration.value = result
                                _publishProgressStage.value = PublishProgressStage.IDLE
                                _publishErrorMessage.value = result.errorMessage
                            }
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        _publishProgressStage.value = PublishProgressStage.IDLE
                        _publishMessage.value = null
                        _publishErrorMessage.value = formatPublishErrorMessage(error.message ?: "Publish failed")
                        AppLogger.e(TAG, "Failed to publish artifact", error)
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pendingPublishRequest = null
                _publishProgressStage.value = PublishProgressStage.IDLE
                _publishMessage.value = null
                _publishErrorMessage.value = e.message ?: "Failed to publish artifact"
                AppLogger.e(TAG, "Failed to publish artifact", e)
            } finally {
                if (_publishProgressStage.value != PublishProgressStage.COMPLETED) {
                    _publishProgressStage.value = PublishProgressStage.IDLE
                    _publishMessage.value = null
                }
            }
        }.invokeOnCompletion {
            _publishProgressStage.compareAndSet(PublishProgressStage.VALIDATING, PublishProgressStage.IDLE)
        }
    }

    fun retryMarketRegistration() {
        val pending = _pendingRegistration.value ?: return
        if (!pending.retryAllowed) return
        if (!_publishProgressStage.compareAndSet(PublishProgressStage.IDLE, PublishProgressStage.REGISTERING_MARKET)) return
        _publishMessage.value = stageMessage(PublishProgressStage.REGISTERING_MARKET)
        _publishErrorMessage.value = null
        viewModelScope.launch {
            try {
                forgePublishService.retryMarketRegistration(pending).getOrThrow()
                _pendingRegistration.value = null
                _publishProgressStage.value = PublishProgressStage.COMPLETED
                _publishSuccessMessage.value = buildSuccessMessage(pending.payload)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _pendingRegistration.value = pending.copy(
                    errorMessage = error.message ?: "Failed to register market entry",
                    retryAllowed = canRetryRejectedMarketRegistration(error)
                )
                _publishErrorMessage.value = error.message ?: "Failed to register market entry"
            } finally {
                _publishMessage.value = null
                _publishProgressStage.compareAndSet(PublishProgressStage.REGISTERING_MARKET, PublishProgressStage.IDLE)
            }
        }.invokeOnCompletion {
            _publishProgressStage.compareAndSet(PublishProgressStage.REGISTERING_MARKET, PublishProgressStage.IDLE)
        }
    }

    fun dismissRegistrationRecovery() {
        if (_publishProgressStage.value != PublishProgressStage.IDLE) return
        // 未确认的同一次提交保持占位，关闭说明不能重新开放整套上传和登记。
        if (_pendingRegistration.value?.retryAllowed == false) return
        _pendingRegistration.value = null
    }

    private suspend fun validateContinuationVersion(request: PublishArtifactRequest) {
        val publishContext = request.publishContext ?: return
        val requestedVersion =
            request.version
                .trim()
                .removePrefix("v")
                .removePrefix("V")
                .ifBlank { "1.0.0" }
        val projectDetail =
            marketStatsApiService.getArtifactProject(publishContext.entryId).getOrElse { error ->
                throw error
            }
        val currentHighestVersion =
            projectDetail.versions
                .map { it.version }
                .filter(String::isNotBlank)
                .maxWithOrNull(::comparePublishVersions)
                ?: return
        if (comparePublishVersions(requestedVersion, currentHighestVersion) <= 0) {
            throw IllegalStateException(
                "Version $requestedVersion must be greater than existing version $currentHighestVersion"
            )
        }
    }

    private fun buildUpdatedMarketRegistrationPayload(
        entry: com.ai.assistance.operit.data.api.MarketV2Entry,
        type: PublishArtifactType,
        displayName: String,
        description: String,
        detail: String,
        minSupportedAppVersion: String?,
        maxSupportedAppVersion: String?
    ): MarketRegistrationPayload {
        val versionValue = entry.latestVersion
        val artifactValue = entry.artifact
        val assetValue = entry.assets.firstOrNull { it.versionId == versionValue?.id }
        val assetName = assetValue?.assetName.orEmpty().ifBlank { assetValue?.name.orEmpty() }
        val versionId = versionValue?.id.orEmpty().ifBlank { entry.id }
        val trimmedDisplayName = displayName.trim().ifBlank { entry.title }
        val trimmedDescription = description.trim().ifBlank { entry.description }
        val trimmedDetail = detail.trim().ifBlank { entry.detail.ifBlank { trimmedDescription } }
        return MarketRegistrationPayload(
            type = type,
            entryId = entry.id,
            projectId = artifactValue?.projectId.orEmpty().ifBlank { versionId },
            projectDisplayName = entry.title.ifBlank { trimmedDisplayName },
            projectDescription = trimmedDetail,
            runtimePackageId = versionValue?.runtimePackageId.orEmpty(),
            publisherLogin = entry.publisher?.login.orEmpty().ifBlank { entry.author?.login.orEmpty() },
            releaseOwner = "",
            releaseRepository = "",
            releaseTag = "",
            assetName = assetName,
            downloadUrl = assetValue?.url.orEmpty(),
            sha256 = assetValue?.sha256.orEmpty(),
            version = versionValue?.version.orEmpty().trim().removePrefix("v").removePrefix("V").ifBlank { "1.0.0" },
            displayName = trimmedDisplayName,
            description = trimmedDescription,
            detail = trimmedDetail,
            categoryId = entry.categoryId,
            sourceFileName = assetName,
            minSupportedAppVersion = normalizeAppVersionOrNull(minSupportedAppVersion),
            maxSupportedAppVersion = normalizeAppVersionOrNull(maxSupportedAppVersion)
        )
    }

    private suspend fun ensureArtifactDisplayNameAvailable(
        displayName: String,
        currentEntryId: String? = null,
        allowExistingOpenDuplicate: Boolean = false
    ) {
        if (allowExistingOpenDuplicate) return
        val normalizedTitle = normalizePublishTitle(displayName)
        val existing = searchOpenEntriesByExactTitle(displayName).getOrElse { error -> throw error }
            .firstOrNull { entry ->
                val entryNumber = entry.projectId.hashCode().let { if (it == Int.MIN_VALUE) 1 else kotlin.math.abs(it) }.toLong()
                entry.projectId != currentEntryId && normalizePublishTitle(entry.projectDisplayName) == normalizedTitle
            }
        if (existing != null) {
            throw IllegalStateException(getText(R.string.artifact_publish_display_name_taken_message, displayName))
        }
    }

    private suspend fun searchOpenEntriesByExactTitle(title: String): Result<List<com.ai.assistance.operit.data.api.ArtifactProjectRankEntryResponse>> {
        val normalizedTitle = normalizePublishTitle(title)
        return marketStatsApiService.getArtifactRankPage(
            type = supportedTypes.firstOrNull()?.toMarketStatsType()?.wireValue ?: PublishArtifactType.SCRIPT.wireValue,
            metric = com.ai.assistance.operit.ui.features.packages.market.MarketSortOption.UPDATED.toRankMetric(),
            page = 1
        ).map { page ->
            page.items.filter { entry ->
                normalizePublishTitle(entry.projectDisplayName) == normalizedTitle &&
                    entry.defaultVersion?.state == "open"
            }
        }
    }

    private suspend fun ensureFreshPublishIdentityAvailable(
        displayName: String,
        runtimePackageId: String
    ) {
        validateStandaloneArtifactRuntimePackageId(runtimePackageId)

        val normalizedTitle = normalizePublishTitle(displayName)
        val normalizedRuntimePackageId = normalizeMarketArtifactId(runtimePackageId)
        val entries = supportedTypes.flatMap { type ->
            marketStatsApiService.getArtifactRankPage(
                type = type.toMarketStatsType().wireValue,
                metric = com.ai.assistance.operit.ui.features.packages.market.MarketSortOption.UPDATED.toRankMetric(),
                page = 1
            ).getOrElse { error ->
                val searchError = error.message ?: getText(R.string.github_search_failed)
                throw IllegalStateException(
                    getText(R.string.artifact_publish_check_name_duplicate_failed, searchError)
                )
            }.items
        }

        val titleConflict = entries.firstOrNull { entry ->
            normalizePublishTitle(entry.projectDisplayName) == normalizedTitle
        }
        val runtimeIdConflict = entries.firstOrNull { entry ->
            val candidate = entry.defaultVersion?.runtimePackageId.orEmpty()
            candidate.isNotBlank() && sameArtifactRuntimePackageId(candidate, runtimePackageId)
        }
        val normalizedIdConflict = entries.firstOrNull { entry ->
            val candidates = listOf(entry.projectId, entry.defaultVersion?.runtimePackageId.orEmpty())
                .map(String::trim)
                .filter(String::isNotBlank)
            candidates.any { normalizeMarketArtifactId(it) == normalizedRuntimePackageId }
        }

        if (titleConflict == null && runtimeIdConflict == null && normalizedIdConflict == null) {
            return
        }

        val conflictReasons = mutableListOf<String>()
        if (titleConflict != null) {
            conflictReasons += getText(R.string.artifact_publish_name_exists, displayName)
        }
        if (runtimeIdConflict != null) {
            conflictReasons += getText(R.string.artifact_publish_runtime_id_exists, runtimePackageId)
        }
        if (normalizedIdConflict != null) {
            conflictReasons += getText(
                R.string.artifact_publish_normalized_id_exists,
                normalizedRuntimePackageId
            )
        }

        throw IllegalStateException(
            getText(
                R.string.artifact_publish_identity_conflict_message,
                conflictReasons.joinToString(getText(R.string.comma_separator))
            )
        )
    }
    private fun resolvePublishDisplayName(request: PublishArtifactRequest): String {
        val lockedDisplayName = request.publishContext?.lockedDisplayName?.trim().orEmpty()
        if (request.publishContext != null && !request.publishContext.canEditEntry) {
            if (lockedDisplayName.isBlank()) {
                throw IllegalStateException(getText(R.string.artifact_publish_locked_name_required))
            }
            return lockedDisplayName
        }
        return request.displayName.trim()
    }

    private fun normalizePublishTitle(title: String): String {
        return title.trim().replace(Regex("\\s+"), " ").lowercase()
    }

    private fun stageMessage(stage: PublishProgressStage): String? {
        return when (stage) {
            PublishProgressStage.IDLE -> null
            PublishProgressStage.VALIDATING -> getText(R.string.artifact_publish_stage_validating)
            PublishProgressStage.ENSURING_REPO -> getText(R.string.artifact_publish_stage_ensuring_repo)
            PublishProgressStage.CREATING_RELEASE -> getText(R.string.artifact_publish_stage_creating_release)
            PublishProgressStage.UPLOADING_ASSET -> getText(R.string.artifact_publish_stage_uploading_asset)
            PublishProgressStage.RESOLVING_RELEASE_ASSET -> getText(R.string.artifact_publish_stage_resolving_release_asset)
            PublishProgressStage.REGISTERING_MARKET -> getText(R.string.artifact_publish_stage_registering_market)
            PublishProgressStage.COMPLETED -> getText(R.string.artifact_publish_stage_completed)
        }
    }

    private fun buildSuccessMessage(payload: MarketRegistrationPayload): String {
        return appendMarketScheduleNotice(
            "${payload.displayName} published to ${payload.releaseOwner}/${payload.releaseRepository} ${payload.releaseTag} " +
                formatSupportedAppVersions(payload.minSupportedAppVersion, payload.maxSupportedAppVersion)
        )
    }

    private fun formatPublishErrorMessage(rawMessage: String): String {
        val message = rawMessage.trim()
        return when {
            message.contains("Repository is empty", ignoreCase = true) ->
                getText(R.string.artifact_publish_empty_repo_error)

            message.contains("Validation Failed", ignoreCase = true) ->
                getText(R.string.artifact_publish_validation_failed_error)

            message.startsWith("HTTP ", ignoreCase = true) ->
                message.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank {
                    getText(R.string.publish_failed_title)
                }

            else -> message
        }
    }

    private fun appendMarketScheduleNotice(baseMessage: String): String {
        return buildString {
            append(baseMessage.trim())
            append(getText(R.string.artifact_market_schedule_notice))
        }
    }

    private fun getText(resId: Int, vararg formatArgs: Any): String {
        return context.getString(resId, *formatArgs)
    }

    private fun inferArtifactType(isToolPkg: Boolean, fileExtension: String): PublishArtifactType? {
        val normalizedExtension = fileExtension.lowercase()
        return when {
            isToolPkg && normalizedExtension == "toolpkg" -> PublishArtifactType.PACKAGE
            !isToolPkg && normalizedExtension != "toolpkg" -> PublishArtifactType.SCRIPT
            else -> null
        }
    }

    private fun comparePublishVersions(left: String, right: String): Int {
        val leftVersion = parsePublishVersion(left)
        val rightVersion = parsePublishVersion(right)
        val maxSize = maxOf(leftVersion.parts.size, rightVersion.parts.size)
        for (index in 0 until maxSize) {
            val diff = (leftVersion.parts.getOrNull(index) ?: 0) -
                (rightVersion.parts.getOrNull(index) ?: 0)
            if (diff != 0) return diff
        }
        if (leftVersion.suffix == rightVersion.suffix) return 0
        if (leftVersion.suffix.isBlank()) return 1
        if (rightVersion.suffix.isBlank()) return -1
        return leftVersion.suffix.compareTo(rightVersion.suffix)
    }

    private fun parsePublishVersion(value: String): PublishVersionParts {
        val normalized = value.trim().removePrefix("v").removePrefix("V")
        val core = normalized.substringBefore("-").substringBefore("+")
        val suffix =
            normalized
                .substringAfter("-", "")
                .substringBefore("+")
        val parts =
            core.split(".")
                .filter(String::isNotBlank)
                .map { it.toIntOrNull() ?: 0 }
        return PublishVersionParts(parts = parts, suffix = suffix)
    }

    private data class PublishVersionParts(
        val parts: List<Int>,
        val suffix: String
    )

    class Factory(
        private val context: Context,
        private val scope: ArtifactMarketScope
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ArtifactMarketViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ArtifactMarketViewModel(context, scope) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "ArtifactMarketViewModel"
    }
}
