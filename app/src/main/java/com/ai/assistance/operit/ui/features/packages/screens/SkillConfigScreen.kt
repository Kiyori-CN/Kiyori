package com.ai.assistance.operit.ui.features.packages.screens

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.skill.SkillPackage
import com.ai.assistance.operit.data.preferences.SkillVisibilityPreferences
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.kiyori.design.theme.KiyoriUiShapes
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

internal fun skillImportTabIndices(): IntRange = 0..2

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun SkillConfigScreen(
    skillRepository: SkillRepository,
    snackbarHostState: SnackbarHostState,
    onNavigateToSkillMarket: () -> Unit = {},
    searchQuery: String = "",
    skillOrder: List<String> = emptyList(),
    onSaveSkillOrder: (List<String>) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val skillVisibilityPreferences = remember { SkillVisibilityPreferences.getInstance(context) }

    var skills by remember { mutableStateOf<Map<String, SkillPackage>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    var selectedSkill by remember { mutableStateOf<SkillPackage?>(null) }
    var selectedSkillDetail by remember { mutableStateOf<SkillDetailDialogData?>(null) }
    var isSkillDetailLoading by remember { mutableStateOf(false) }
    var skillDetailFailed by remember { mutableStateOf(false) }
    var skillDetailRevision by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedSkill, skillDetailRevision) {
        val target = selectedSkill ?: return@LaunchedEffect
        isSkillDetailLoading = true
        skillDetailFailed = false
        selectedSkillDetail = null
        try {
            selectedSkillDetail = withContext(Dispatchers.IO) {
                val operationContext = currentCoroutineContext()
                buildSkillDetailDialogData(target, checkNotNull(skillRepository.readSkillContent(target.name))) {
                    operationContext.ensureActive()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            skillDetailFailed = true
        } finally {
            if (currentCoroutineContext().isActive) isSkillDetailLoading = false
        }
    }
    var skillLoadErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showSkillLoadErrorsDialog by remember { mutableStateOf(false) }

    var showImportDialog by remember { mutableStateOf(false) }
    var importTabIndex by remember { mutableStateOf(0) }
    var repoUrlInput by remember { mutableStateOf("") }
    var zipUri by remember { mutableStateOf<Uri?>(null) }
    var zipFileName by remember { mutableStateOf("") }
    var manualSkillId by remember { mutableStateOf("") }
    var manualSkillDescription by remember { mutableStateOf("") }
    var manualSkillContent by remember { mutableStateOf("") }
    var manualAttachments by remember { mutableStateOf<List<ManualImportAttachment>>(emptyList()) }
    var isImporting by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }

    var skillsLoadFailed by remember { mutableStateOf(false) }
    val refreshLock = remember { Mutex() }
    val refreshSkills: suspend () -> Boolean = {
        refreshLock.withLock {
            isLoading = true
            skillsLoadFailed = false
            try {
                val loaded = withContext(Dispatchers.IO) { skillRepository.getAvailableSkillPackagesSnapshot() }
                skills = loaded.first
                skillLoadErrors = loaded.second
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                skillsLoadFailed = true
                false
            } finally {
                isLoading = false
            }
        }
    }

    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            zipUri = it
            zipFileName = resolveUriDisplayName(context, it, fallback = "skill.zip")
        }
    }

    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        val existingKeys = manualAttachments.map { it.uri.toString() }.toMutableSet()
        val updated = manualAttachments.toMutableList()
        uris.forEach { uri ->
            if (existingKeys.add(uri.toString())) {
                updated += ManualImportAttachment(
                    uri = uri,
                    displayName = resolveUriDisplayName(context, uri)
                )
            }
        }
        manualAttachments = updated
    }

    LaunchedEffect(Unit) {
        refreshSkills()
    }

    BindSkillTopBarActions(
        isBusy = isLoading || isImporting,
        isRefreshing = isLoading,
        hasLoadErrors = skillLoadErrors.isNotEmpty(),
        onLoadErrorsClick = { showSkillLoadErrorsDialog = true },
        onMarketClick = onNavigateToSkillMarket,
        onImportClick = { showImportDialog = true },
        onRefreshClick = {
            scope.launch {
                if (refreshSkills()) snackbarHostState.showSnackbar(context.getString(R.string.skillmgr_refreshed))
            }
        },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            if (skillsLoadFailed) {
                Text(stringResource(R.string.pkg_details_load_failed), color = MaterialTheme.colorScheme.error)
                TextButton(enabled = !isLoading, onClick = { scope.launch { refreshSkills() } }) {
                    Text(stringResource(R.string.pkg_details_retry))
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ai_extensions_tab_skill),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = skillRepository.getSkillsDirectoryPath(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val displayedSkills =
                remember(skills, searchQuery, skillOrder) {
                    val searchText = searchQuery.trim()
                    val filtered = skills.values
                        .filter { skill ->
                            searchText.isEmpty() ||
                                skill.name.contains(searchText, ignoreCase = true) ||
                                skill.description.contains(searchText, ignoreCase = true) ||
                                skill.directory.absolutePath.contains(searchText, ignoreCase = true)
                        }
                    if (searchText.isEmpty() && skillOrder.isNotEmpty()) {
                        val orderIndex = skillOrder.withIndex().associate { (i, name) -> name to i }
                        filtered.sortedBy { orderIndex[it.name] ?: Int.MAX_VALUE }
                    } else {
                        filtered.sortedBy { it.name }
                    }
                }

            if (skills.isEmpty()) {
                Text(
                    text = stringResource(R.string.skillmgr_no_skills_found, skillRepository.getSkillsDirectoryPath()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                var orderedSkills by remember(displayedSkills) {
                    mutableStateOf(displayedSkills)
                }
                val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
                val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    orderedSkills = orderedSkills.toMutableList().apply {
                        add(to.index, removeAt(from.index))
                    }
                    val newOrder = orderedSkills.map { it.name }
                    onSaveSkillOrder(newOrder)
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (orderedSkills.isEmpty()) {
                        item(key = "empty_skill_search_state") {
                            Text(
                                text = stringResource(R.string.no_matching_skills_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    itemsIndexed(
                        items = orderedSkills,
                        key = { _, skill -> skill.name }
                    ) { index, skill ->
                        ReorderableItem(
                            reorderableState,
                            key = skill.name,
                            animateItemModifier = Modifier.animateItem(
                            fadeInSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                            placementSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                            fadeOutSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
                        )
                        ) { isDragging ->
                            val elevation = if (isDragging) 8.dp else 0.dp
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isDragging) {
                                            Modifier.shadow(elevation, KiyoriUiShapes.control)
                                        } else {
                                            Modifier
                                        }
                                    ),
                                color = if (isDragging) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                shape = KiyoriUiShapes.control
                            ) {
                                SkillListItem(
                                    skill = skill,
                                    skillVisibilityPreferences = skillVisibilityPreferences,
                                    onClick = {
                                        selectedSkill = skill
                                        selectedSkillDetail = null
                                        isSkillDetailLoading = true
                                        skillDetailFailed = false
                                    },
                                    modifier = Modifier.longPressDraggableHandle()
                                )
                            }
                            if (index < orderedSkills.lastIndex) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (skills.isEmpty() && (isLoading || isImporting)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { if (!isImporting) showImportDialog = false },
            title = { Text(stringResource(R.string.import_or_install_skill)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    importMessage?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    SecondaryScrollableTabRow(
                        selectedTabIndex = importTabIndex,
                        edgePadding = 8.dp,
                        modifier = Modifier.heightIn(min = 48.dp),
                        divider = {},
                        indicator = {
                            if (importTabIndex in skillImportTabIndices()) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(importTabIndex)
                                )
                            }
                        }
                    ) {
                        Tab(
                            selected = importTabIndex == 0,
                            onClick = { importTabIndex = 0 },
                            enabled = !isImporting,
                            modifier = Modifier.heightIn(min = 48.dp),
                            text = { Text(stringResource(R.string.import_from_repo), maxLines = 1) }
                        )
                        Tab(
                            selected = importTabIndex == 1,
                            onClick = { importTabIndex = 1 },
                            enabled = !isImporting,
                            modifier = Modifier.heightIn(min = 48.dp),
                            text = { Text(stringResource(R.string.import_from_zip), maxLines = 1) }
                        )
                        Tab(
                            selected = importTabIndex == 2,
                            onClick = { importTabIndex = 2 },
                            enabled = !isImporting,
                            modifier = Modifier.heightIn(min = 48.dp),
                            text = { Text(stringResource(R.string.import_from_direct), maxLines = 1) }
                        )
                    }

                    when (importTabIndex) {
                        0 -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.enter_repo_info),
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    enabled = !isImporting,
                                    onClick = {
                                        showImportDialog = false
                                        onNavigateToSkillMarket()
                                    }
                                ) {
                                    Text(stringResource(R.string.get_skill))
                                }
                            }

                            OutlinedTextField(
                                value = repoUrlInput,
                                onValueChange = { repoUrlInput = it },
                                label = { Text(stringResource(R.string.repo_link)) },
                                placeholder = { Text("https://github.com/username/repo") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                enabled = !isImporting
                            )
                        }

                        1 -> {
                            Text(
                                text = stringResource(R.string.select_skill_plugin_zip),
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = zipFileName,
                                    onValueChange = { },
                                    label = { Text(stringResource(R.string.skill_zip_package)) },
                                    placeholder = { Text(stringResource(R.string.select_zip_file)) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    readOnly = true,
                                    enabled = !isImporting
                                )

                                IconButton(
                                    enabled = !isImporting,
                                    onClick = { zipPicker.launch("application/zip") }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Folder,
                                        contentDescription = stringResource(R.string.select_file)
                                    )
                                }
                            }
                        }

                        2 -> {
                            OutlinedTextField(
                                value = manualSkillId,
                                onValueChange = { manualSkillId = it },
                                label = { Text(stringResource(R.string.skillmgr_direct_skill_id)) },
                                placeholder = { Text(stringResource(R.string.skillmgr_direct_skill_id_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !isImporting
                            )

                            OutlinedTextField(
                                value = manualSkillDescription,
                                onValueChange = { manualSkillDescription = it },
                                label = { Text(stringResource(R.string.skillmgr_direct_description)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !isImporting
                            )

                            OutlinedTextField(
                                value = manualSkillContent,
                                onValueChange = { manualSkillContent = it },
                                label = { Text(stringResource(R.string.skillmgr_direct_content)) },
                                placeholder = { Text(stringResource(R.string.skillmgr_direct_content_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 6,
                                maxLines = 10,
                                enabled = !isImporting
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.attachments_count, manualAttachments.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )

                                TextButton(
                                    enabled = !isImporting,
                                    onClick = { attachmentPicker.launch(arrayOf("*/*")) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AttachFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = stringResource(R.string.add_attachment))
                                }
                            }

                            if (manualAttachments.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 140.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    manualAttachments.forEach { attachment ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = attachment.displayName,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            IconButton(
                                                enabled = !isImporting,
                                                onClick = {
                                                    manualAttachments = manualAttachments.filterNot { it.uri == attachment.uri }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Close,
                                                    contentDescription = stringResource(R.string.remove_attachment),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                text = stringResource(R.string.skillmgr_direct_files_saved_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isImporting) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.processing))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isImporting,
                    onClick = {
                        if (!isImporting) {
                            val tab = importTabIndex
                            val url = repoUrlInput.trim()
                            val archiveUri = zipUri
                            val archiveName = zipFileName.ifBlank { "skill.zip" }
                            val skillId = manualSkillId.trim()
                            val description = manualSkillDescription.trim()
                            val content = manualSkillContent.trim()
                            val attachments = manualAttachments.map { it.uri }
                            val validationError = when {
                                tab == 0 && url.isBlank() -> R.string.enter_repo_info
                                tab == 1 && archiveUri == null -> R.string.select_zip_file
                                tab == 1 && !archiveName.endsWith(".zip", ignoreCase = true) -> R.string.skillmgr_only_zip_files
                                tab == 2 && skillId.isBlank() -> R.string.skillmgr_direct_skill_id_required
                                tab == 2 && !isValidSkillId(skillId) -> R.string.skillmgr_direct_skill_id_invalid
                                tab == 2 && content.isBlank() -> R.string.skillmgr_direct_content_required
                                else -> null
                            }
                            if (validationError != null) {
                                importMessage = context.getString(validationError)
                            } else {
                                isImporting = true
                                importMessage = null
                                scope.launch {
                                    try {
                                        val result = when (tab) {
                                            0 -> skillRepository.importSkillFromGitHubRepoDetailed(url)
                                            1 -> withContext(Dispatchers.IO) {
                                                // 外部显示名仅用于导入名称推导，绝不作为缓存文件路径。
                                                val tempFile = File.createTempFile("skill_import_", ".zip", context.cacheDir)
                                                try {
                                                    context.contentResolver.openInputStream(requireNotNull(archiveUri))?.use { input ->
                                                        tempFile.outputStream().use { output ->
                                                            val buffer = ByteArray(64 * 1024)
                                                            while (true) {
                                                                currentCoroutineContext().ensureActive()
                                                                val count = input.read(buffer)
                                                                if (count < 0) break
                                                                output.write(buffer, 0, count)
                                                            }
                                                        }
                                                    } ?: error(context.getString(R.string.skillmgr_cannot_read_file))
                                                    skillRepository.importSkillFromZipDetailed(tempFile, archiveName)
                                                } finally {
                                                    java.nio.file.Files.deleteIfExists(tempFile.toPath())
                                                }
                                            }
                                            2 -> skillRepository.importSkillFromDirectInputDetailed(skillId, description, content, attachments)
                                            else -> error("Unsupported skill import tab")
                                        }
                                        if (result.installedDir != null) {
                                            showImportDialog = false
                                            if (tab == 2) {
                                                manualSkillId = ""
                                                manualSkillDescription = ""
                                                manualSkillContent = ""
                                                manualAttachments = emptyList()
                                            }
                                            refreshSkills()
                                            snackbarHostState.showSnackbar(result.message)
                                        } else {
                                            importMessage = result.message
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        importMessage = context.getString(R.string.skill_import_generic_failure)
                                    } finally {
                                        isImporting = false
                                    }
                                }
                            }
                        }
                    },
                ) { Text(stringResource(R.string.import_action)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !isImporting,
                    onClick = { showImportDialog = false }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showSkillLoadErrorsDialog) {
        SkillLoadErrorsDialog(
            errors = skillLoadErrors,
            onDismiss = { showSkillLoadErrorsDialog = false }
        )
    }

    selectedSkill?.let { skill ->
        key(skill.directory.absolutePath) {
            SkillDetailDialog(
                skill = skill,
                detail = selectedSkillDetail,
                isLoading = isSkillDetailLoading,
                loadFailed = skillDetailFailed,
                onReload = { skillDetailRevision++ },
                onDismiss = { selectedSkill = null; selectedSkillDetail = null },
                onDelete = { withContext(Dispatchers.IO) { skillRepository.deleteSkill(skill.name) } },
                onDeleted = {
                    selectedSkill = null
                    selectedSkillDetail = null
                    scope.launch {
                        refreshSkills()
                        snackbarHostState.showSnackbar(context.getString(R.string.skillmgr_deleted, skill.name))
                    }
                },
            )
        }
    }

}

private data class ManualImportAttachment(
    val uri: Uri,
    val displayName: String
)

private data class SkillDetailDialogData(
    val skillContent: String,
    val directoryPath: String,
    val skillFilePath: String,
    val fileCount: Int,
    val folderCount: Int,
    val directoryPreview: String,
    val hiddenEntryCount: Int
)

private val SKILL_ID_PATTERN = Regex("^[A-Za-z0-9._-]+$")

private fun isValidSkillId(skillId: String): Boolean {
    return SKILL_ID_PATTERN.matches(skillId) && skillId != "." && skillId != ".."
}

private fun resolveUriDisplayName(context: Context, uri: Uri, fallback: String = "file"): String {
    val resolver = context.contentResolver
    val displayName = runCatching {
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
    }.getOrNull()

    if (!displayName.isNullOrBlank()) {
        return displayName
    }

    val uriFallback = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    return uriFallback.ifBlank { fallback }
}

private fun buildSkillDetailDialogData(
    skill: SkillPackage,
    skillContent: String,
    checkCancelled: () -> Unit,
): SkillDetailDialogData {
    val preview = buildSkillDirectoryPreview(skill.directory, checkCancelled)
    return SkillDetailDialogData(
        skillContent = skillContent,
        directoryPath = skill.directory.absolutePath,
        skillFilePath = skill.skillFile.absolutePath,
        fileCount = preview.fileCount,
        folderCount = preview.folderCount,
        directoryPreview = preview.text,
        hiddenEntryCount = preview.hiddenEntryCount,
    )
}

@Composable
private fun SkillDetailDialog(
    skill: SkillPackage,
    detail: SkillDetailDialogData?,
    isLoading: Boolean,
    loadFailed: Boolean,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: suspend () -> Boolean,
    onDeleted: () -> Unit,
) {
    var showSkillMarkdown by remember(skill.directory.absolutePath) { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dismiss: () -> Unit = { if (!deleting) onDismiss() }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(text = skill.name) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (loadFailed) {
                    Text(stringResource(R.string.pkg_details_load_failed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onReload) { Text(stringResource(R.string.pkg_details_retry)) }
                } else if (isLoading || detail == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.skillmgr_detail_loading),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    if (skill.description.isNotBlank()) {
                        SkillDetailSection(title = stringResource(R.string.skillmgr_direct_description)) {
                            Text(
                                text = skill.description,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    SkillDetailSection(title = stringResource(R.string.skillmgr_detail_directory)) {
                        Text(
                            text = detail.directoryPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    SkillDetailSection(title = stringResource(R.string.skillmgr_detail_entry_file)) {
                        Text(
                            text = detail.skillFilePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    SkillDetailSection(title = stringResource(R.string.skillmgr_detail_imported_contents)) {
                        Text(
                            text = stringResource(
                                R.string.skillmgr_detail_counts,
                                detail.fileCount,
                                detail.folderCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = KiyoriUiShapes.control,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Text(
                                text = detail.directoryPreview,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        if (detail.hiddenEntryCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.skillmgr_detail_more_items,
                                    detail.hiddenEntryCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TextButton(
                        onClick = { showSkillMarkdown = !showSkillMarkdown }
                    ) {
                        Text(
                            text = if (showSkillMarkdown) {
                                stringResource(R.string.skillmgr_detail_hide_skill_md)
                            } else {
                                stringResource(R.string.skillmgr_detail_show_skill_md)
                            }
                        )
                    }

                    if (showSkillMarkdown) {
                        SkillDetailSection(title = stringResource(R.string.skillmgr_detail_skill_md)) {
                            MarkdownTextComposable(
                                text = detail.skillContent,
                                textColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !deleting, onClick = { deleteFailed = false; confirmDelete = true }) {
                Text(text = stringResource(R.string.skillmgr_delete))
            }
        },
        dismissButton = {
            TextButton(enabled = !deleting, onClick = dismiss) {
                Text(text = stringResource(R.string.skillmgr_close))
            }
        }
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!deleting) confirmDelete = false },
            title = { Text(stringResource(R.string.pkg_confirm_delete)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.skill_detail_delete_warning, skill.directory.absolutePath))
                    if (deleteFailed) Text(stringResource(R.string.pkg_details_delete_failed), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    if (!deleting) {
                        deleting = true
                        deleteFailed = false
                        scope.launch {
                            try {
                                if (onDelete()) { confirmDelete = false; onDeleted() }
                                else deleteFailed = true
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                deleteFailed = true
                            } finally { deleting = false }
                        }
                    }
                }) { Text(stringResource(if (deleting) R.string.pkg_details_deleting else R.string.skillmgr_delete)) }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

}

@Composable
private fun SkillDetailSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        content()
    }
}

@Composable
private fun SkillLoadErrorsDialog(
    errors: Map<String, String>,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.error_occurred_simple)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState)
            ) {
                errors.toSortedMap().forEach { (skillFolderName, errorText) ->
                    Text(
                        text = skillFolderName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.ok))
            }
        }
    )
}

@Composable
private fun SkillListItem(
    skill: SkillPackage,
    skillVisibilityPreferences: SkillVisibilityPreferences,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary
    var visibleToAi by remember(skill.name) {
        mutableStateOf(skillVisibilityPreferences.isSkillVisibleToAi(skill.name))
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = KiyoriUiShapes.control,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .width(3.dp)
                    .height(22.dp),
                color = accentColor,
                shape = RoundedCornerShape(2.dp)
            ) {}
            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = Icons.Filled.Build,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = accentColor
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = skill.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (skill.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Switch(
                modifier = Modifier.scale(0.8f),
                checked = visibleToAi,
                onCheckedChange = { checked ->
                    visibleToAi = checked
                    skillVisibilityPreferences.setSkillVisibleToAi(skill.name, checked)
                }
            )
        }
    }
}
