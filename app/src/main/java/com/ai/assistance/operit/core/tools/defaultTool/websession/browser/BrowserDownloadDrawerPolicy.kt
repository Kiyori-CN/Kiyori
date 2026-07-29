package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.util.Locale

internal enum class BrowserDownloadDrawerTab {
    DOWNLOADED,
    DOWNLOADING,
}

internal enum class BrowserDownloadSortMode {
    NEWEST,
    OLDEST,
    NAME,
}

internal enum class BrowserDownloadStatusFilter {
    ALL,
    ACTIVE,
    QUEUED,
    PAUSED,
    FAILED,
    CANCELED,
}

internal enum class BrowserDownloadCategory {
    VIDEO,
    AUDIO,
    IMAGE,
    APPLICATION,
    ARCHIVE,
    DOCUMENT,
    OTHER,
}

internal enum class BrowserDownloadDrawerActionType {
    DELETE,
    BATCH_DELETE,
    REDOWNLOAD,
    RENAME,
    CHANGE_SUFFIX,
    MOVE_FOLDER,
    COPY_URL,
    SHARE_FILE,
    COPY_LOCATION,
    TRANSFER_TO_PUBLIC,
    MERGE_TO_MP4,
    PAUSE,
    RESUME,
    CANCEL,
    RETRY,
    BATCH_CANCEL,
}

internal enum class BrowserDownloadBatchAction {
    DELETE,
    CANCEL,
}

internal data class BrowserDownloadSection(
    val category: BrowserDownloadCategory?,
    val items: List<BrowserDownloadItem>,
)

internal fun filterBrowserDownloadDrawerItems(
    items: List<BrowserDownloadItem>,
    tab: BrowserDownloadDrawerTab,
    statusFilter: BrowserDownloadStatusFilter = BrowserDownloadStatusFilter.ALL,
    query: String = "",
): List<BrowserDownloadItem> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    return items.filter { item ->
        val tabMatches =
            when (tab) {
                BrowserDownloadDrawerTab.DOWNLOADED -> item.status == "completed"
                BrowserDownloadDrawerTab.DOWNLOADING ->
                    item.status in
                        setOf(
                            "queued",
                            "connecting",
                            "downloading",
                            "paused",
                            "failed",
                            "canceled",
                        )
            }
        val statusMatches =
            tab == BrowserDownloadDrawerTab.DOWNLOADED ||
                when (statusFilter) {
                    BrowserDownloadStatusFilter.ALL -> true
                    BrowserDownloadStatusFilter.ACTIVE ->
                        item.status == "connecting" || item.status == "downloading"
                    BrowserDownloadStatusFilter.QUEUED -> item.status == "queued"
                    BrowserDownloadStatusFilter.PAUSED -> item.status == "paused"
                    BrowserDownloadStatusFilter.FAILED -> item.status == "failed"
                    BrowserDownloadStatusFilter.CANCELED -> item.status == "canceled"
                }
        val queryMatches =
            normalizedQuery.isBlank() ||
                sequenceOf(
                    item.fileName,
                    item.sourceUrl.orEmpty(),
                    item.mimeType.orEmpty(),
                    item.errorMessage.orEmpty(),
                ).any { value -> value.lowercase(Locale.ROOT).contains(normalizedQuery) }
        tabMatches && statusMatches && queryMatches
    }
}

internal fun browserDownloadBatchEligibleTaskIds(
    items: List<BrowserDownloadItem>,
    action: BrowserDownloadBatchAction,
): Set<String> =
    items
        .asSequence()
        .filter { item -> browserDownloadBatchSelectionEligible(item, action) }
        .map { item -> item.id }
        .toSet()

internal fun toggleAllBrowserDownloadSelections(
    selectedTaskIds: Set<String>,
    eligibleTaskIds: Set<String>,
): Set<String> =
    if (eligibleTaskIds.isNotEmpty() && eligibleTaskIds.all(selectedTaskIds::contains)) {
        selectedTaskIds - eligibleTaskIds
    } else {
        selectedTaskIds + eligibleTaskIds
    }

internal fun sortBrowserDownloadDrawerItems(
    items: List<BrowserDownloadItem>,
    sortMode: BrowserDownloadSortMode,
): List<BrowserDownloadItem> =
    when (sortMode) {
        BrowserDownloadSortMode.NEWEST ->
            items.sortedWith(
                compareByDescending<BrowserDownloadItem> { it.completedAt ?: it.createdAt }
                    .thenBy { it.fileName.lowercase(Locale.ROOT) },
            )
        BrowserDownloadSortMode.OLDEST ->
            items.sortedWith(
                compareBy<BrowserDownloadItem> { it.completedAt ?: it.createdAt }
                    .thenBy { it.fileName.lowercase(Locale.ROOT) },
            )
        BrowserDownloadSortMode.NAME ->
            items.sortedWith(
                compareBy<BrowserDownloadItem> { it.fileName.lowercase(Locale.ROOT) }
                    .thenByDescending { it.completedAt ?: it.createdAt },
            )
    }

internal fun buildBrowserDownloadSections(
    items: List<BrowserDownloadItem>,
    classify: Boolean,
): List<BrowserDownloadSection> {
    if (!classify) {
        return listOf(BrowserDownloadSection(category = null, items = items))
    }
    val itemsByCategory = items.groupBy(::browserDownloadCategory)
    return BrowserDownloadCategory.entries.mapNotNull { category ->
        itemsByCategory[category]
            ?.takeIf { categoryItems -> categoryItems.isNotEmpty() }
            ?.let { categoryItems ->
                BrowserDownloadSection(category = category, items = categoryItems)
            }
    }
}

internal fun browserDownloadCategory(item: BrowserDownloadItem): BrowserDownloadCategory {
    val normalizedMimeType = item.mimeType?.lowercase(Locale.ROOT).orEmpty()
    val extension = item.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
        normalizedMimeType.startsWith("video/") || extension in VIDEO_EXTENSIONS ->
            BrowserDownloadCategory.VIDEO
        normalizedMimeType.startsWith("audio/") || extension in AUDIO_EXTENSIONS ->
            BrowserDownloadCategory.AUDIO
        normalizedMimeType.startsWith("image/") || extension in IMAGE_EXTENSIONS ->
            BrowserDownloadCategory.IMAGE
        normalizedMimeType == "application/vnd.android.package-archive" ||
            extension in APPLICATION_EXTENSIONS -> BrowserDownloadCategory.APPLICATION
        normalizedMimeType in ARCHIVE_MIME_TYPES || extension in ARCHIVE_EXTENSIONS ->
            BrowserDownloadCategory.ARCHIVE
        normalizedMimeType in DOCUMENT_MIME_TYPES || extension in DOCUMENT_EXTENSIONS ->
            BrowserDownloadCategory.DOCUMENT
        else -> BrowserDownloadCategory.OTHER
    }
}

internal fun browserDownloadDrawerActions(
    item: BrowserDownloadItem,
): List<BrowserDownloadDrawerActionType> =
    when {
        item.status == "completed" ->
            buildList {
                add(BrowserDownloadDrawerActionType.DELETE)
                add(BrowserDownloadDrawerActionType.BATCH_DELETE)
                if (item.canRedownload) {
                    add(BrowserDownloadDrawerActionType.REDOWNLOAD)
                }
                add(BrowserDownloadDrawerActionType.RENAME)
                if (item.isM3u8Package) {
                    if (item.sourceUrl?.let(::isBrowserDownloadNetworkUrl) == true) {
                        add(BrowserDownloadDrawerActionType.COPY_URL)
                    }
                    if (item.canMergeToMp4) {
                        add(BrowserDownloadDrawerActionType.MERGE_TO_MP4)
                    }
                } else {
                    add(BrowserDownloadDrawerActionType.CHANGE_SUFFIX)
                    add(BrowserDownloadDrawerActionType.MOVE_FOLDER)
                    if (item.sourceUrl?.let(::isBrowserDownloadNetworkUrl) == true) {
                        add(BrowserDownloadDrawerActionType.COPY_URL)
                    }
                    add(BrowserDownloadDrawerActionType.SHARE_FILE)
                    add(BrowserDownloadDrawerActionType.COPY_LOCATION)
                    add(BrowserDownloadDrawerActionType.TRANSFER_TO_PUBLIC)
                }
            }
        item.status in setOf("queued", "connecting", "downloading") ->
            buildList {
                if (item.canPause) {
                    add(BrowserDownloadDrawerActionType.PAUSE)
                }
                if (item.canCancel) {
                    add(BrowserDownloadDrawerActionType.CANCEL)
                }
                add(BrowserDownloadDrawerActionType.BATCH_CANCEL)
            }
        item.status == "paused" ->
            buildList {
                if (item.canResume) {
                    add(BrowserDownloadDrawerActionType.RESUME)
                }
                if (item.canCancel) {
                    add(BrowserDownloadDrawerActionType.CANCEL)
                }
                add(BrowserDownloadDrawerActionType.BATCH_CANCEL)
            }
        item.status in setOf("failed", "canceled") ->
            buildList {
                if (item.canRetry) {
                    add(BrowserDownloadDrawerActionType.RETRY)
                }
                add(BrowserDownloadDrawerActionType.DELETE)
                add(BrowserDownloadDrawerActionType.BATCH_DELETE)
            }
        else -> throw IllegalArgumentException("Unsupported browser download status: ${item.status}")
    }

internal fun extractBrowserDownloadSuffix(
    requestedFileName: String,
    url: String,
): String? {
    val fileNameSuffix = requestedFileName.trim().substringAfterLast('.', "").trim()
    if (fileNameSuffix.isNotBlank()) {
        return fileNameSuffix.lowercase(Locale.ROOT)
    }
    val urlFileName = browserDownloadUrlFileName(url)
    return urlFileName.substringAfterLast('.', "").trim().takeIf { it.isNotBlank() }
        ?.lowercase(Locale.ROOT)
}

internal fun resolveManualBrowserDownloadFileName(
    requestedFileName: String,
    url: String,
    requestedSuffix: String,
): String {
    val normalizedName = requestedFileName.trim()
    val urlFileName = browserDownloadUrlFileName(url)
    val baseName = normalizedName.ifBlank { urlFileName.ifBlank { "download" } }
    val normalizedSuffix = requestedSuffix.trim().removePrefix(".")
    if (normalizedSuffix.isBlank()) {
        return baseName
    }
    val currentSuffix = baseName.substringAfterLast('.', "")
    return if (currentSuffix.equals(normalizedSuffix, ignoreCase = true)) {
        baseName
    } else {
        "${baseName.substringBeforeLast('.', baseName)}.$normalizedSuffix"
    }
}

internal fun resolveBrowserDownloadMp4FileName(fileName: String): String {
    val baseName = fileName.substringBeforeLast('.', fileName).trim().ifBlank { "download" }
    return "$baseName.mp4"
}

internal fun toggleBrowserDownloadSelection(
    selectedTaskIds: Set<String>,
    taskId: String,
): Set<String> =
    if (taskId in selectedTaskIds) {
        selectedTaskIds - taskId
    } else {
        selectedTaskIds + taskId
    }

internal fun browserDownloadBatchSelectionEligible(
    item: BrowserDownloadItem,
    action: BrowserDownloadBatchAction,
): Boolean =
    when (action) {
        BrowserDownloadBatchAction.DELETE -> item.canDelete
        BrowserDownloadBatchAction.CANCEL -> item.canCancel
    }

private fun browserDownloadUrlFileName(url: String): String =
    url.trim()
        .substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('/')
        .trim()

private val VIDEO_EXTENSIONS =
    setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "m4v", "ts", "m3u8")
private val AUDIO_EXTENSIONS =
    setOf("mp3", "aac", "flac", "wav", "ogg", "m4a", "opus", "amr")
private val IMAGE_EXTENSIONS =
    setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "avif")
private val APPLICATION_EXTENSIONS = setOf("apk", "apks", "xapk")
private val ARCHIVE_EXTENSIONS =
    setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz")
private val DOCUMENT_EXTENSIONS =
    setOf("pdf", "txt", "md", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "epub", "csv")
private val ARCHIVE_MIME_TYPES =
    setOf(
        "application/zip",
        "application/x-rar-compressed",
        "application/x-7z-compressed",
        "application/x-tar",
        "application/gzip",
    )
private val DOCUMENT_MIME_TYPES =
    setOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/epub+zip",
        "text/plain",
        "text/markdown",
        "text/csv",
    )
