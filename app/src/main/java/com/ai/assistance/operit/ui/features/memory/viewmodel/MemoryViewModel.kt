package com.ai.assistance.operit.ui.features.memory.viewmodel

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.MemoryLibraryPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.ai.assistance.operit.data.model.DocumentChunk
import com.ai.assistance.operit.data.model.CloudEmbeddingConfig
import com.ai.assistance.operit.data.model.EmbeddingDimensionUsage
import com.ai.assistance.operit.data.model.EmbeddingRebuildProgress
import com.ai.assistance.operit.data.model.MemorySearchConfig
import com.ai.assistance.operit.data.model.MemorySearchDebugInfo
import com.ai.assistance.operit.data.preferences.MemorySearchSettingsPreferences
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.kiyori.capability.ai.memory.MemoryGraphEdge
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Node
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.toPresentationGraph
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.R
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/** Memory UI State Represents the current state of the Memory screen. */
data class MemoryUiState(
        val libraryKind: String = MemoryLibraryPolicy.MEMORY,
        val showArchived: Boolean = false,
        val showGraph: Boolean = false,
        val categoryFilter: String? = null,
        val tagFilter: String? = null,
        val sortByTitle: Boolean = false,
        val availableTags: List<String> = emptyList(),
        val isSaving: Boolean = false,
        val isImporting: Boolean = false,
        val canRetryImport: Boolean = false,
        val importProgress: String? = null,
        val importFailures: List<String> = emptyList(),
        val embeddingUsageError: String? = null,
        val isEmbeddingUsageLoading: Boolean = false,
        val memories: List<Memory> = emptyList(), // Keep for potential list view
        val graph: Graph = Graph(emptyList(), emptyList()),
        val selectedMemory: Memory? = null,
        val selectedNodeId: String? = null,
        val isLoading: Boolean = false,
        val searchQuery: String = "",
        val appliedSearchQuery: String = "",
        val error: String? = null,
        val editingMemory: Memory? = null, // 新增：用于编辑/新建
        val isEditing: Boolean = false, // 新增：是否处于编辑/新建状态
        val isLinkingMode: Boolean = false, // 是否处于连接模式
        val linkingNodeIds: List<String> = emptyList(), // 已选择的连接节点
        val selectedEdge: MemoryGraphEdge? = null,
        val editingEdge: MemoryGraphEdge? = null,
        val isEditingEdge: Boolean = false,
        val isBoxSelectionMode: Boolean = false, // 新增：是否处于框选模式
        val boxSelectedNodeIds: Set<String> = emptySet(), // 新增：框选中的节点ID
        val showBatchDeleteConfirm: Boolean = false, // 新增：是否显示批量删除确认对话框

        // --- 新增：文档相关状态 ---
        val selectedDocumentChunks: List<DocumentChunk> = emptyList(),
        val documentSearchQuery: String = "",
        val isDocumentViewOpen: Boolean = false,

        // --- 新增：工具测试相关状态 ---
        val isToolTestDialogVisible: Boolean = false,
        val toolTestResult: String = "",
        val isToolTestLoading: Boolean = false,

        // --- 新增：文件夹相关状态 ---
        val folderPaths: List<String> = emptyList(), // 所有文件夹路径
        val selectedFolderPath: String = "", // 当前选中的文件夹路径，空字符串表示显示全部

        // --- 新增：搜索设置 ---
        val isSearchSettingsDialogVisible: Boolean = false,
        val searchConfig: MemorySearchConfig = MemorySearchConfig(),
        val autoSaveIntervalMinutes: Int = MemorySearchSettingsPreferences.DEFAULT_AUTO_SAVE_INTERVAL_MINUTES,
        val memoryExtractionCustomRules: String =
            MemorySearchSettingsPreferences.DEFAULT_MEMORY_EXTRACTION_CUSTOM_RULES,
        val cloudEmbeddingConfig: CloudEmbeddingConfig = CloudEmbeddingConfig(),
        val embeddingDimensionUsage: EmbeddingDimensionUsage = EmbeddingDimensionUsage(),
        val isEmbeddingRebuildRunning: Boolean = false,
        val embeddingRebuildProgress: EmbeddingRebuildProgress = EmbeddingRebuildProgress(),

        // --- 搜索模拟 ---
        val isSearchSimulationDialogVisible: Boolean = false,
        val searchSimulationQuery: String = "",
        val isSearchSimulationRunning: Boolean = false,
        val searchSimulationResult: MemorySearchDebugInfo? = null,
        val searchSimulationError: String? = null,
        val message: String? = null
)

/**
 * ViewModel for the Memory/Memory Library screen. It handles the business logic for interacting
 * with the MemoryRepository.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class MemoryViewModel(
    private val repository: MemoryRepository,
    private val context: Context,
    private val profileId: String
) : ViewModel() {

    companion object {
        private const val TAG = "MemoryViewModel"
    }

    private val searchSettingsPreferences = MemorySearchSettingsPreferences(context, profileId)
    private val _uiState = MutableStateFlow(MemoryUiState(showGraph = searchSettingsPreferences.loadGraphVisible(), searchConfig = searchSettingsPreferences.load()))
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()
    /** 搜索、文件夹切换和刷新可能重叠；旧图谱不能覆盖最新查询结果。 */
    private val searchGeneration = AtomicLong(0)
    private var searchJob: Job? = null
    private var embeddingUsageJob: Job? = null
    private var folderJob: Job? = null
    private val simulationGeneration = AtomicLong(0)
    private val documentSearchGeneration = AtomicLong(0)

    init {
        loadSearchSettings()
        loadCloudEmbeddingSettings()
        // 索引统计仅在设置打开时扫描，普通列表首屏不读取整库向量。
        // Initially load the graph
        loadMemoryGraph()
        loadFolderPaths()
        viewModelScope.launch {
            repository.observeChanges().debounce(150).collect {
                // 外部写入刷新已应用查询，不提交用户尚未搜索的输入草稿。
                // 语义检索首次会顺带补写文档索引路径，因此可能多触发一轮；补写后条件不再成立，
                // 不会持续自激。真正的死循环只会出现在「每次查询都写库」的实现里。
                refreshCurrentSearch()
                loadFolderPaths()
            }
        }
    }

    private suspend fun refreshGraph(): Graph {
        val generation = searchGeneration.get()
        val snapshot = _uiState.value
        val config = snapshot.searchConfig
        val scopeMemories = repository.searchMemories(
            query = snapshot.appliedSearchQuery,
            folderPath = snapshot.selectedFolderPath.takeIf { it.isNotBlank() },
            scoreMode = config.scoreMode,
            keywordWeight = config.keywordWeight,
            tagWeight = config.tagWeight,
            semanticWeight = config.vectorWeight,
            edgeWeight = config.edgeWeight,
            libraryKind = snapshot.libraryKind,
            archived = snapshot.showArchived
        )
        val availableTags = withContext(Dispatchers.IO) {
            scopeMemories.flatMap { memory -> memory.tags.map { it.name } }.distinct().sorted()
        }
        val memories = withContext(Dispatchers.IO) { scopeMemories.filter { memory ->
            (snapshot.categoryFilter == null || MemoryLibraryPolicy.category(memory) == snapshot.categoryFilter) &&
                (snapshot.tagFilter == null || memory.tags.any { it.name == snapshot.tagFilter })
        }.let { records ->
            when {
                snapshot.sortByTitle -> records.sortedBy { it.title.lowercase() }
                snapshot.appliedSearchQuery.isBlank() -> records.sortedByDescending { it.updatedAt.time }
                else -> records
            }
        }
        }
        if (searchGeneration.get() == generation && _uiState.value.appliedSearchQuery == snapshot.appliedSearchQuery &&
            _uiState.value.selectedFolderPath == snapshot.selectedFolderPath &&
            _uiState.value.libraryKind == snapshot.libraryKind &&
            _uiState.value.showArchived == snapshot.showArchived) {
            _uiState.update { it.copy(memories = memories, availableTags = availableTags) }
        }
        // 图谱仅在用户选择时构建，限制物理布局规模，普通列表不运行力导向计算。
        if (!snapshot.showGraph) return Graph(emptyList(), emptyList())
        val graph = repository.getGraphForMemories(memories.take(200))
        return withContext(Dispatchers.Default) { graph.toPresentationGraph() }
    }

    /** 首屏加载：此时草稿与已应用查询都为空，提交草稿没有副作用。 */
    fun loadMemoryGraph() = searchMemories()

    /**
     * 顶栏刷新：重跑「已经应用」的查询和目录，不把用户尚未提交的输入草稿当成搜索条件。
     * 走 searchMemories 会静默应用草稿，让结果和搜索框下方的待搜索提示对不上。
     */
    fun refresh() {
        refreshCurrentSearch()
        loadFolderPaths()
    }

    fun searchMemories() {
        _uiState.update { it.copy(appliedSearchQuery = it.searchQuery.trim()) }
        refreshCurrentSearch()
    }

    private fun refreshCurrentSearch(reuseResults: Boolean = false) {
        searchJob?.cancel()
        val generation = searchGeneration.incrementAndGet()
        _uiState.update(MemoryUiPolicy::beginSearch)
        searchJob = viewModelScope.launch {
            try {
                val graph = if (reuseResults) {
                    if (_uiState.value.showGraph) repository.getGraphForMemories(_uiState.value.memories.take(200)).let {
                        withContext(Dispatchers.Default) { it.toPresentationGraph() }
                    } else Graph(emptyList(), emptyList())
                } else refreshGraph()
                if (searchGeneration.get() == generation) {
                    _uiState.update { it.copy(graph = graph, isLoading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (searchGeneration.get() == generation) _uiState.update {
                    it.copy(isLoading = false, error = context.getString(R.string.memory_error_search, e.message ?: "Unknown error"))
                }
            }
        }
    }

    fun setLibraryKind(kind: String) {
        if (_uiState.value.libraryKind == kind) return
        clearSelection()
        _uiState.update { it.copy(libraryKind = kind, categoryFilter = null, tagFilter = null, selectedMemory = null) }
        searchMemories()
    }

    fun setArchivedFilter(archived: Boolean) {
        clearSelection()
        _uiState.update { it.copy(showArchived = archived) }
        searchMemories()
    }

    fun setGraphVisible(visible: Boolean) {
        if (_uiState.value.showGraph == visible) return
        val reuseResults = !_uiState.value.isLoading && _uiState.value.error == null
        searchSettingsPreferences.saveGraphVisible(visible)
        _uiState.update { it.copy(showGraph = visible, isLinkingMode = false, isBoxSelectionMode = false, boxSelectedNodeIds = emptySet(), linkingNodeIds = emptyList()) }
        refreshCurrentSearch(reuseResults)
    }

    fun setCategoryFilter(category: String?) {
        _uiState.update { it.copy(categoryFilter = category) }
        searchMemories()
    }

    fun setTagFilter(tag: String?) {
        _uiState.update { it.copy(tagFilter = tag) }
        searchMemories()
    }

    fun resetFilters() {
        clearSelection()
        _uiState.update { it.copy(searchQuery = "", appliedSearchQuery = "", selectedFolderPath = "", categoryFilter = null, tagFilter = null, showArchived = false, sortByTitle = false) }
        searchMemories()
    }

    fun resetOrganizationFilters() {
        clearSelection()
        _uiState.update(MemoryUiPolicy::resetOrganizationFilters)
        refreshCurrentSearch()
    }

    fun setSortByTitle(value: Boolean) {
        _uiState.update { it.copy(sortByTitle = value) }
        searchMemories()
    }

    fun archiveMemory(memory: Memory) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                repository.setArchived(memory.id, !memory.archived)
                clearSelection()
                refreshCurrentSearch()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /** Updates the search query in the state. */
    fun onSearchQueryChange(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }
    }

    fun showSearchSettingsDialog(visible: Boolean) {
        _uiState.update { it.copy(isSearchSettingsDialogVisible = visible) }
        if (visible) {
            loadSearchSettings()
            loadCloudEmbeddingSettings()
            refreshEmbeddingDimensionUsage()
        }
    }

    fun openSearchSimulationDialog() {
        _uiState.update {
            it.copy(
                isSearchSettingsDialogVisible = false,
                isSearchSimulationDialogVisible = true,
                searchSimulationQuery = it.searchQuery,
                searchSimulationResult = null,
                searchSimulationError = null
            )
        }
    }

    fun showSearchSimulationDialog(visible: Boolean) {
        if (!visible) simulationGeneration.incrementAndGet()
        _uiState.update {
            it.copy(
                isSearchSimulationDialogVisible = visible,
                searchSimulationResult = if (visible) it.searchSimulationResult else null,
                searchSimulationError = if (visible) it.searchSimulationError else null,
                isSearchSimulationRunning = if (visible) it.isSearchSimulationRunning else false
            )
        }
    }

    fun onSearchSimulationQueryChange(newQuery: String) {
        _uiState.update { it.copy(searchSimulationQuery = newQuery) }
    }

    fun runSearchSimulation() {
        if (_uiState.value.isSearchSimulationRunning) return
        val generation = simulationGeneration.incrementAndGet()
        val querySnapshot = _uiState.value.searchSimulationQuery
        val configSnapshot = _uiState.value.searchConfig
        val folderSnapshot = _uiState.value.selectedFolderPath.takeIf { it.isNotBlank() }
        val kindSnapshot = _uiState.value.libraryKind
        val archivedSnapshot = _uiState.value.showArchived
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearchSimulationRunning = true,
                    searchSimulationError = null
                )
            }

            try {
                val debugInfo = repository.searchMemoriesDebug(
                    query = querySnapshot,
                    folderPath = folderSnapshot,
                    scoreMode = configSnapshot.scoreMode,
                    keywordWeight = configSnapshot.keywordWeight,
                    tagWeight = configSnapshot.tagWeight,
                    semanticWeight = configSnapshot.vectorWeight,
                    edgeWeight = configSnapshot.edgeWeight,
                    libraryKind = kindSnapshot, archived = archivedSnapshot
                )
                if (simulationGeneration.get() == generation) _uiState.update {
                    it.copy(
                        isSearchSimulationRunning = false,
                        searchSimulationResult = debugInfo,
                        searchSimulationError = null
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (simulationGeneration.get() == generation) _uiState.update {
                    it.copy(
                        isSearchSimulationRunning = false,
                        searchSimulationError = context.getString(
                            R.string.memory_error_search,
                            e.message ?: "Unknown error"
                        )
                    )
                }
            }
        }
    }

    fun saveSearchSettings(
        newConfig: MemorySearchConfig,
        newCloudConfig: CloudEmbeddingConfig,
        autoSaveIntervalMinutes: Int,
        memoryExtractionCustomRules: String
    ) {
        val normalizedSearchConfig = newConfig.normalized()
        val normalizedCloudConfig = newCloudConfig.normalized()
        val normalizedInterval =
            autoSaveIntervalMinutes.coerceIn(
                MemorySearchSettingsPreferences.MIN_AUTO_SAVE_INTERVAL_MINUTES,
                MemorySearchSettingsPreferences.MAX_AUTO_SAVE_INTERVAL_MINUTES
            )
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                withContext(Dispatchers.IO) {
                    searchSettingsPreferences.save(normalizedSearchConfig)
                    searchSettingsPreferences.saveAutoSaveIntervalMinutes(normalizedInterval)
                    searchSettingsPreferences.saveMemoryExtractionCustomRules(memoryExtractionCustomRules)
                    repository.saveCloudEmbeddingConfig(normalizedCloudConfig)
                }
                _uiState.update { it.copy(searchConfig = normalizedSearchConfig,
                    cloudEmbeddingConfig = normalizedCloudConfig, autoSaveIntervalMinutes = normalizedInterval,
                    memoryExtractionCustomRules = memoryExtractionCustomRules, isSaving = false,
                    message = context.getString(R.string.settings_saved)) }
                refreshEmbeddingDimensionUsage()
                refreshCurrentSearch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun resetSearchSettings() {
        val defaults = MemorySearchConfig()
        _uiState.update { it.copy(searchConfig = defaults) }
    }

    private fun loadSearchSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = searchSettingsPreferences.load()
                val autoSaveIntervalMinutes = searchSettingsPreferences.loadAutoSaveIntervalMinutes()
                val memoryExtractionCustomRules =
                    searchSettingsPreferences.loadMemoryExtractionCustomRules()
                _uiState.update {
                    it.copy(
                        searchConfig = config,
                        autoSaveIntervalMinutes = autoSaveIntervalMinutes,
                        memoryExtractionCustomRules = memoryExtractionCustomRules
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    private fun loadCloudEmbeddingSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = repository.loadCloudEmbeddingConfig()
                _uiState.update { it.copy(cloudEmbeddingConfig = config) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun refreshEmbeddingDimensionUsage() {
        embeddingUsageJob?.cancel()
        embeddingUsageJob = viewModelScope.launch {
            _uiState.update { it.copy(isEmbeddingUsageLoading = true, embeddingUsageError = null) }
            try {
                val usage = repository.getEmbeddingDimensionUsage()
                _uiState.update { it.copy(embeddingDimensionUsage = usage, isEmbeddingUsageLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isEmbeddingUsageLoading = false,
                    embeddingUsageError = context.getString(R.string.library_index_usage_failed, e.message ?: e.javaClass.simpleName)) }
            }
        }
    }

    fun rebuildVectorIndex() {
        if (_uiState.value.isEmbeddingRebuildRunning) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isEmbeddingRebuildRunning = true,
                    error = null,
                    message = null,
                    embeddingRebuildProgress = EmbeddingRebuildProgress(
                        total = 0,
                        processed = 0,
                        failed = 0,
                        currentStage = "preparing"
                    )
                )
            }

            try {
                val finalProgress = repository.rebuildVectorIndices { progress ->
                    _uiState.update { state ->
                        state.copy(embeddingRebuildProgress = progress)
                    }
                }
                val usage = repository.getEmbeddingDimensionUsage()
                _uiState.update {
                    it.copy(
                        isEmbeddingRebuildRunning = false,
                        embeddingDimensionUsage = usage,
                        embeddingRebuildProgress = finalProgress,
                        error = if (finalProgress.failed > 0) "${finalProgress.failed} 项在构建期间发生变化或未生成向量，请重试" else null,
                        message = if (finalProgress.failed > 0) null else if (finalProgress.total > 0) {
                            context.getString(
                                R.string.memory_embedding_rebuild_completed,
                                finalProgress.processed
                            )
                        } else {
                            context.getString(R.string.memory_embedding_rebuild_empty)
                        }
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isEmbeddingRebuildRunning = false,
                        error = context.getString(
                            R.string.memory_embedding_rebuild_failed,
                            e.message ?: "Unknown error"
                        )
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    // --- 文件夹相关方法 ---

    /** 加载所有文件夹路径列表 */
    fun loadFolderPaths() {
        folderJob?.cancel()
        folderJob = viewModelScope.launch {
            try {
                val folders = repository.getAllFolderPaths()
                _uiState.update { it.copy(folderPaths = folders) }
                // 保持空字符串选中态表示“全部”，避免刷新目录后自动跳转到具体文件夹。
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = context.getString(R.string.memory_error_load_folders, e.message ?: "Unknown error")) }
            }
        }
    }

    /** 手动刷新文件夹列表 */
    fun refreshFolderList() = loadFolderPaths()

    /** 选择文件夹并加载该文件夹的图谱 */
    fun selectFolder(folderPath: String) {
        clearSelection()
        _uiState.update { it.copy(selectedFolderPath = folderPath) }
        refreshCurrentSearch()
    }

    /** 移动选中的记忆到目标文件夹 */
    fun moveSelectedMemoriesToFolder(targetFolderPath: String) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            val selectedIds = _uiState.value.boxSelectedNodeIds
            if (selectedIds.isEmpty()) return@launch
            
            _uiState.update { it.copy(isSaving = true) }
            try {
                // 将UUID转换为Memory ID
                val memoryIds = selectedIds.mapNotNull { uuid ->
                    repository.findMemoryByUuid(uuid)?.id
                }
                
                val success = repository.moveMemoriesToFolder(memoryIds, targetFolderPath)
                if (success) {
                    loadFolderPaths()
                    refreshCurrentSearch()
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            boxSelectedNodeIds = emptySet(),
                            isBoxSelectionMode = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isSaving = false, error = context.getString(R.string.memory_error_move_memories)) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isSaving = false, error = context.getString(R.string.memory_error_move_memories_detail, e.message ?: "Unknown error")) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /** Selects a memory to view its details. */
    fun selectMemory(memory: Memory) {
        val generation = documentSearchGeneration.incrementAndGet()
        viewModelScope.launch {
            try {
                val chunks = if (memory.isDocumentNode) repository.getChunksForMemory(memory.id) else emptyList()
                if (documentSearchGeneration.get() == generation) _uiState.update {
                    it.copy(selectedMemory = memory, isDocumentViewOpen = memory.isDocumentNode,
                        selectedDocumentChunks = chunks, documentSearchQuery = "")
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (documentSearchGeneration.get() == generation) _uiState.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    /** Selects a node in the graph. Fetches the full memory details for the selected node. */
    fun selectNode(node: Node) {
        val selectionGeneration = documentSearchGeneration.incrementAndGet()
        viewModelScope.launch {
            try {
            if (_uiState.value.isLinkingMode) {
                // 连接模式
                val currentLinkingIds = _uiState.value.linkingNodeIds.toMutableList()
                if (node.id !in currentLinkingIds) {
                    currentLinkingIds.add(node.id)
                }
                _uiState.update { it.copy(linkingNodeIds = currentLinkingIds) }
            } else if (_uiState.value.isBoxSelectionMode) {
                // 框选模式
                val currentSelectedIds = _uiState.value.boxSelectedNodeIds.toMutableSet()
                if (node.id in currentSelectedIds) {
                    currentSelectedIds.remove(node.id)
                } else {
                    currentSelectedIds.add(node.id)
                }
                _uiState.update { it.copy(boxSelectedNodeIds = currentSelectedIds) }
            } else {
                // 普通模式
                val memory = repository.getMemoryByUuid(node.id)
                if (memory?.isDocumentNode == true) {
                    // 如果是文档节点，检查是否有全局搜索词
                    val globalQuery = _uiState.value.searchQuery

                    val chunks = if (globalQuery.isNotBlank()) {
                        com.ai.assistance.operit.util.AppLogger.d("MemoryVM", "Node click on doc, searching with global query: '$globalQuery'")
                        repository.searchChunksInDocument(memory.id, globalQuery)
                    } else {
                        com.ai.assistance.operit.util.AppLogger.d("MemoryVM", "Node click on doc, no global query. Getting all chunks.")
                        repository.getChunksForMemory(memory.id)
                    }

                    if (documentSearchGeneration.get() == selectionGeneration) _uiState.update {
                        it.copy(
                            selectedNodeId = node.id,
                            selectedMemory = memory,
                            selectedEdge = null,
                            isDocumentViewOpen = true,
                            selectedDocumentChunks = chunks,
                            documentSearchQuery = globalQuery // 预填内部搜索框
                        )
                    }
                } else {
                    if (documentSearchGeneration.get() == selectionGeneration) _uiState.update { it.copy(selectedNodeId = node.id, selectedMemory = memory, selectedEdge = null, isDocumentViewOpen = false) }
                }
            }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (documentSearchGeneration.get() == selectionGeneration) _uiState.update {
                    it.copy(error = e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    /** Selects an edge in the graph. */
    fun selectEdge(edge: MemoryGraphEdge) {
        _uiState.update { it.copy(selectedEdge = edge, selectedNodeId = null, selectedMemory = null) }
    }

    /** Clears any selection (node or edge). */
    fun clearSelection() {
        documentSearchGeneration.incrementAndGet()
        _uiState.update { it.copy(selectedMemory = null, selectedNodeId = null, selectedEdge = null, isDocumentViewOpen = false, selectedDocumentChunks = emptyList()) }
    }

    /** 关闭文档视图 */
    fun closeDocumentView() {
        documentSearchGeneration.incrementAndGet()
        _uiState.update { it.copy(isDocumentViewOpen = false, documentSearchQuery = "", selectedDocumentChunks = emptyList(), selectedMemory = null, selectedNodeId = null) }
    }

    /** 更新文档内搜索的查询词 */
    fun onDocumentSearchQueryChange(query: String) {
        _uiState.update { it.copy(documentSearchQuery = query) }
    }

    /** 在选定文档中执行搜索 */
    fun performSearchInDocument() {
        val query = _uiState.value.documentSearchQuery
        val generation = documentSearchGeneration.incrementAndGet()
        val memoryId = _uiState.value.selectedMemory?.id ?: return
        viewModelScope.launch {
            try {
                val chunks = if (query.isBlank()) repository.getChunksForMemory(memoryId)
                    else repository.searchChunksInDocument(memoryId, query)
                if (documentSearchGeneration.get() == generation) _uiState.update { it.copy(selectedDocumentChunks = chunks, error = null) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (documentSearchGeneration.get() == generation) _uiState.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    /**
     * 显示或隐藏工具测试对话框
     */
    fun showToolTestDialog(visible: Boolean) {
        _uiState.update { it.copy(isToolTestDialogVisible = visible, toolTestResult = "") } // 打开时清空上次结果
    }

    /**
     * 执行记忆查询工具的测试
     */
    fun testQueryTool(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isToolTestLoading = true, toolTestResult = "") }
            try {
                val aiToolHandler = AIToolHandler.getInstance(context)
                // 确保工具已注册
                if (aiToolHandler.getToolExecutor("query_memory") == null) {
                    aiToolHandler.registerDefaultTools()
                }

                val tool = AITool(
                    name = "query_memory",
                    parameters = listOf(ToolParameter("query", query))
                )

                val result = aiToolHandler.executeTool(tool)

                val resultString = if (result.success) {
                    // 使用Gson进行格式化输出，更美观
                    Gson().newBuilder().setPrettyPrinting().create().toJson(result.result)
                } else {
                    "Error: ${result.error}"
                }
                _uiState.update { it.copy(isToolTestLoading = false, toolTestResult = resultString) }

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isToolTestLoading = false, toolTestResult = "An unexpected error occurred: ${e.message}") }
            }
        }
    }

    /**
     * 更新文档区块的内容。
     * @param chunkId 要更新的区块ID。
     * @param newContent 新内容。
     */
    fun saveDocument(title: String, edits: Map<Long, String>) {
        if (_uiState.value.isSaving) return
        val memory = _uiState.value.selectedMemory ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                repository.updateDocument(memory, title, edits)
                _uiState.update { it.copy(isSaving = false) }
                closeDocumentView()
                clearSelection()
                refreshCurrentSearch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun updateChunkContent(chunkId: Long, newContent: String) {
        viewModelScope.launch {
            repository.updateChunk(chunkId, newContent)
            // 可选：更新后刷新当前文档的区块列表
            val memoryId = _uiState.value.selectedMemory?.id ?: return@launch
            val chunks = repository.getChunksForMemory(memoryId)
            _uiState.update { it.copy(selectedDocumentChunks = chunks) }
        }
    }

    private var importJob: Job? = null
    private var failedImportUris: List<android.net.Uri> = emptyList()
    private var importTargetFolder: String = ""

    fun cancelImport() { importJob?.cancel() }
    fun retryImport() = importDocuments(failedImportUris, importTargetFolder)

    fun importDocuments(uris: List<android.net.Uri>, folderPath: String) {
        if (_uiState.value.isSaving || uris.isEmpty()) return
        val inputs = uris.distinct()
        importTargetFolder = folderPath
        _uiState.update { it.copy(isSaving = true, isImporting = true, canRetryImport = false, importFailures = emptyList(), error = null) }
        importJob = viewModelScope.launch {
            val failed = mutableListOf<android.net.Uri>()
            var completed = 0
            var processed = 0
            try {
                val reader = com.ai.assistance.operit.data.repository.MemoryDocumentReader(context)
                inputs.forEachIndexed { index, uri ->
                    _uiState.update { it.copy(importProgress = context.getString(R.string.library_import_reading, index + 1, inputs.size)) }
                    try {
                        val (title, content) = reader.read(uri)
                        _uiState.update { it.copy(importProgress = context.getString(R.string.library_import_saving, index + 1, inputs.size, title)) }
                        repository.createMemoryFromDocument(title, uri.toString(), content, folderPath)
                        completed++
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (failure: Exception) {
                        failed.add(uri)
                        _uiState.update { it.copy(importFailures = it.importFailures + context.getString(R.string.library_import_failure_item, index + 1, failure.message ?: failure.javaClass.simpleName)) }
                    }
                    processed = index + 1
                }
            } finally {
                // 取消后保留未处理 URI 供显式重试；已保存条目不回滚、不重复导入。
                failedImportUris = (failed + inputs.drop(processed)).distinct()
                _uiState.update { it.copy(isSaving = false, isImporting = false, canRetryImport = failedImportUris.isNotEmpty(),
                    importProgress = context.getString(R.string.library_import_summary, completed, failedImportUris.size),
                    message = context.getString(R.string.library_import_result, completed, failedImportUris.size)) }
                refreshCurrentSearch()
                loadFolderPaths()
            }
        }
    }

    /** 新建记忆 */
    fun createMemory(title: String, content: String, contentType: String = "text/plain",
        source: String = "user_input", credibility: Float = 0.8f, importance: Float = 0.5f,
        folderPath: String = _uiState.value.selectedFolderPath, tags: List<String> = emptyList(),
        category: String = "other") {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                repository.createMemory(title.trim(), content.trim(), contentType, source,
                    folderPath, tags, credibility, importance, _uiState.value.libraryKind, category)
                _uiState.update { it.copy(isEditing = false, editingMemory = null) }
                refreshCurrentSearch()
                loadFolderPaths()
                _uiState.update {
                    it.copy(isSaving = false, isEditing = false, editingMemory = null)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(isSaving = false, error = context.getString(R.string.memory_error_create_memory, e.message ?: "Unknown error"))
                }
            }
        }
    }
    /** 编辑记忆 */
    fun updateMemory(
        memory: Memory,
        newTitle: String,
        newContent: String,
        newContentType: String,
        newSource: String,
        newCredibility: Float,
        newImportance: Float,
        newFolderPath: String,
        newTags: List<String>,
        newCategory: String = MemoryLibraryPolicy.category(memory)
    ) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                // 文档节点的内容不允许自由编辑，保持固定格式
                val contentToUpdate = if (memory.isDocumentNode) memory.content else newContent
                repository.updateMemory(
                    memory = memory,
                    newTitle = newTitle,
                    newContent = contentToUpdate,
                    newContentType = newContentType,
                    newSource = newSource,
                    newCredibility = newCredibility,
                    newImportance = newImportance,
                    newFolderPath = newFolderPath.ifBlank { null }, // 空字符串视为未分类
                    newTags = newTags,
                    newCategory = newCategory
                )
                _uiState.update { it.copy(isEditing = false, editingMemory = null) }
                refreshCurrentSearch()
                // 刷新文件夹列表（如果记忆移动到新文件夹或从文件夹移出）
                loadFolderPaths()
                _uiState.update {
                    it.copy(isSaving = false, isEditing = false, editingMemory = null, isDocumentViewOpen = false)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(isSaving = false, error = context.getString(R.string.memory_error_update_memory, e.message ?: "Unknown error"))
                }
            }
        }
    }
    /** 删除记忆 */
    fun deleteMemory(memoryId: Long) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                repository.deleteMemory(memoryId)
                clearSelection()
                loadFolderPaths()
                refreshCurrentSearch()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(error = context.getString(R.string.memory_error_delete_memory, e.message ?: "Unknown error"))
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /** 显示批量删除确认对话框 */
    fun showBatchDeleteConfirm() {
        val selectedIds = _uiState.value.boxSelectedNodeIds
        if (selectedIds.isEmpty()) {
            com.ai.assistance.operit.util.AppLogger.d("MemoryViewModel", "No nodes selected, aborting delete.")
            return
        }
        _uiState.update { it.copy(showBatchDeleteConfirm = true) }
    }

    /** 隐藏批量删除确认对话框 */
    fun dismissBatchDeleteConfirm() {
        _uiState.update { it.copy(showBatchDeleteConfirm = false) }
    }

    /** 批量删除框选中的记忆（确认后执行） */
    fun deleteSelectedNodes() {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            val selectedIds = _uiState.value.boxSelectedNodeIds
            com.ai.assistance.operit.util.AppLogger.d("MemoryViewModel", "deleteSelectedNodes called with ${selectedIds.size} nodes.")
            if (selectedIds.isEmpty()) {
                com.ai.assistance.operit.util.AppLogger.d("MemoryViewModel", "No nodes selected, aborting delete.")
                return@launch
            }

            _uiState.update { it.copy(isSaving = true, showBatchDeleteConfirm = false) }
            try {
                com.ai.assistance.operit.util.AppLogger.d("MemoryViewModel", "Calling repository.deleteMemoriesByUuids with IDs: $selectedIds")
                check(repository.deleteMemoriesByUuids(selectedIds)) { "批量删除未完成，请刷新后重试" }
                refreshCurrentSearch()
                com.ai.assistance.operit.util.AppLogger.d("MemoryViewModel", "Graph refreshed after deletion.")
                // 刷新文件夹列表（批量删除可能导致文件夹变空）
                loadFolderPaths()
                _uiState.update {
                    it.copy(
                            isSaving = false,
                            isBoxSelectionMode = false,
                            boxSelectedNodeIds = emptySet()
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                com.ai.assistance.operit.util.AppLogger.e("MemoryViewModel", "Failed to delete selected memories", e)
                _uiState.update {
                    it.copy(isSaving = false, error = context.getString(R.string.memory_error_delete_selected, e.message ?: "Unknown error"))
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /** 将框选的节点添加到已选择集合中 */
    fun addNodesToSelection(nodeIds: Set<String>) {
        _uiState.update {
            it.copy(boxSelectedNodeIds = it.boxSelectedNodeIds + nodeIds)
        }
    }

    /** 进入新建/编辑状态 */
    fun startEditing(memory: Memory? = null) {
        _uiState.update { it.copy(isEditing = true, editingMemory = memory, error = null) }
    }
    /** 取消编辑 */
    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false, editingMemory = null) }
    }

    /** 进入/退出边的编辑状态 */
    fun startEditingEdge(edge: MemoryGraphEdge) {
        _uiState.update { it.copy(isEditingEdge = true, editingEdge = edge) }
    }
    fun cancelEditingEdge() {
        _uiState.update { it.copy(isEditingEdge = false, editingEdge = null) }
    }

    /** 更新边的信息 */
    fun updateEdge(edge: MemoryGraphEdge, type: String, weight: Float, description: String) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, isEditingEdge = false, editingEdge = null) }
            try {
                repository.updateLink(edge.id, type, weight, description)
                refreshCurrentSearch()
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        selectedEdge = null, // 彻底清空选中状态
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isSaving = false, error = context.getString(R.string.memory_error_update_link, e.message ?: "Unknown error")) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /** 删除边 */
    fun deleteEdge(edgeId: Long) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                repository.deleteLink(edgeId)
                refreshCurrentSearch()
                _uiState.update { it.copy(isSaving = false, selectedEdge = null) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isSaving = false, error = context.getString(R.string.memory_error_delete_link, e.message ?: "Unknown error")) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /** 切换连接模式 */
    fun toggleLinkingMode(enabled: Boolean) {
        if (enabled) {
            // 进入连接模式，确保退出其他模式，并清理状态
            _uiState.update {
                it.copy(
                    isLinkingMode = true,
                    linkingNodeIds = emptyList(),
                    isBoxSelectionMode = false,
                    boxSelectedNodeIds = emptySet()
                )
            }
        } else {
            // 退出连接模式
            _uiState.update {
                it.copy(isLinkingMode = false, linkingNodeIds = emptyList())
            }
        }
    }

    /** 切换框选模式 */
    fun toggleBoxSelectionMode(enabled: Boolean) {
        if (enabled) {
            // 进入框选模式，确保退出其他模式，并清理状态
            _uiState.update {
                it.copy(
                    isBoxSelectionMode = true,
                    boxSelectedNodeIds = emptySet(),
                    isLinkingMode = false,
                    linkingNodeIds = emptyList()
                )
            }
        } else {
            // 退出框选模式
            _uiState.update {
                it.copy(isBoxSelectionMode = false, boxSelectedNodeIds = emptySet())
            }
        }
    }

    /** 创建两个记忆之间的连接 */
    fun linkMemories(
            sourceUuid: String,
            targetUuid: String,
            type: String = "related",
            weight: Float = 1.0f,
            description: String = ""
    ) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val source = repository.findMemoryByUuid(sourceUuid)
                val target = repository.findMemoryByUuid(targetUuid)
                if (source != null && target != null) {
                    repository.linkMemories(source, target, type, weight, description)
                    refreshCurrentSearch()
                    _uiState.update { it.copy(isSaving = false, isLinkingMode = false, linkingNodeIds = emptyList()) }
                } else {
                    _uiState.update { it.copy(isSaving = false) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(isSaving = false, error = context.getString(R.string.memory_error_link_memories, e.message ?: "Unknown error"))
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /**
     * 创建新文件夹
     */
    fun createFolder(folderPath: String) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                // 创建一个空的占位记忆，确保文件夹路径存在；失败时仓库抛出具体原因，不在这里重写成一句通用文案。
                repository.createFolder(folderPath)
                // 重新加载文件夹列表
                loadFolderPaths()
                // 自动选择新创建的文件夹
                selectFolder(folderPath)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(error = context.getString(R.string.memory_error_create_folder, e.message ?: "Unknown error"))
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /**
     * 重命名文件夹
     */
    fun renameFolder(oldPath: String, newPath: String) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                check(repository.renameFolder(oldPath, newPath)) { context.getString(R.string.library_folder_rename_failed) }
                // 重新加载文件夹列表
                loadFolderPaths()
                // 父目录重命名时，子目录选择必须随之移动，且卡片路径需要刷新。
                val selected = _uiState.value.selectedFolderPath
                val renamed = MemoryUiPolicy.renamedSelection(selected, oldPath, newPath)
                selectFolder(renamed)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(isSaving = false, error = context.getString(R.string.memory_error_rename_folder, e.message ?: "Unknown error"))
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /**
     * 删除文件夹
     */
    fun deleteFolder(folderPath: String) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            AppLogger.d(TAG, "deleteFolder() 开始删除文件夹: $folderPath")
            _uiState.update { it.copy(isSaving = true) }
            try {
                repository.deleteFolder(folderPath)
                AppLogger.d(TAG, "deleteFolder() 文件夹删除成功: $folderPath")
                // 重新加载文件夹列表
                loadFolderPaths()
                val selected = _uiState.value.selectedFolderPath
                selectFolder(if (MemoryUiPolicy.isWithinFolder(selected, folderPath)) "" else selected)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.e(TAG, "deleteFolder() 删除文件夹失败: $folderPath", e)
                _uiState.update {
                    it.copy(isSaving = false, error = context.getString(R.string.memory_error_delete_folder, e.message ?: "Unknown error"))
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}

/** Factory for creating MemoryViewModel instances with dependencies. */
class MemoryViewModelFactory(private val context: Context, private val profileId: String) :
        ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MemoryViewModel::class.java)) {
            val repository = MemoryRepository(context, profileId)
            return requireNotNull(
                modelClass.cast(MemoryViewModel(repository, context.applicationContext, profileId))
            )
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
