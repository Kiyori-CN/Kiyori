package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

internal enum class BrowserWebElementResourceKind {
    NONE,
    IMAGE,
    VIDEO,
    AUDIO,
    FRAME,
    OTHER;

    companion object {
        fun fromWireValue(value: String): BrowserWebElementResourceKind =
            when (value.trim().lowercase()) {
                "image" -> IMAGE
                "video" -> VIDEO
                "audio" -> AUDIO
                "frame" -> FRAME
                "other" -> OTHER
                else -> NONE
            }
    }
}

internal enum class BrowserWebElementTargetKind {
    IMAGE_LINK,
    IMAGE,
    LINK,
    MEDIA_LINK,
    MEDIA,
    TEXT,
    ELEMENT,
}

internal enum class BrowserWebElementAction {
    OPEN_NEW_WINDOW,
    OPEN_BACKGROUND,
    VIEW_IMAGE,
    SAVE_IMAGE,
    IMAGE_MODE,
    COPY_LINK,
    COPY_IMAGE_LINK,
    COPY_RESOURCE_LINK,
    OPEN_EXTERNAL,
    COPY_TEXT,
    SELECT_TEXT,
    RECOGNIZE_QR,
    BLOCK_ELEMENT_QUICK,
    BLOCK_ELEMENT_ADVANCED,
    BLOCK_URL,
}

internal data class BrowserWebElementActionPlan(
    val targetKind: BrowserWebElementTargetKind,
    val quickActions: List<BrowserWebElementAction>,
    val contentActions: List<BrowserWebElementAction>,
    val blockingActions: List<BrowserWebElementAction>,
)

internal fun resolveBrowserWebElementTargetKind(
    hasLink: Boolean,
    resourceKind: BrowserWebElementResourceKind,
    hasText: Boolean,
): BrowserWebElementTargetKind =
    when {
        resourceKind == BrowserWebElementResourceKind.IMAGE && hasLink ->
            BrowserWebElementTargetKind.IMAGE_LINK
        resourceKind == BrowserWebElementResourceKind.IMAGE ->
            BrowserWebElementTargetKind.IMAGE
        resourceKind != BrowserWebElementResourceKind.NONE && hasLink ->
            BrowserWebElementTargetKind.MEDIA_LINK
        resourceKind != BrowserWebElementResourceKind.NONE ->
            BrowserWebElementTargetKind.MEDIA
        hasLink -> BrowserWebElementTargetKind.LINK
        hasText -> BrowserWebElementTargetKind.TEXT
        else -> BrowserWebElementTargetKind.ELEMENT
    }

internal fun buildBrowserWebElementActionPlan(
    state: WebSessionWebElementActionState,
): BrowserWebElementActionPlan {
    val linkUrl = state.linkUrl?.takeIf(::isHttpBrowserNetworkUrl)
    val resourceUrl = state.resourceUrl?.takeIf(::isHttpBrowserNetworkUrl)
    val imageUrl =
        resourceUrl.takeIf {
            state.resourceKind == BrowserWebElementResourceKind.IMAGE
        }
    val targetKind =
        resolveBrowserWebElementTargetKind(
            hasLink = linkUrl != null,
            resourceKind = state.resourceKind,
            hasText = state.text.isNotBlank(),
        )
    val quickActions = buildList {
        if (linkUrl != null) {
            add(BrowserWebElementAction.OPEN_NEW_WINDOW)
            add(BrowserWebElementAction.OPEN_BACKGROUND)
        }
        if (imageUrl != null) {
            add(BrowserWebElementAction.VIEW_IMAGE)
            add(BrowserWebElementAction.SAVE_IMAGE)
            add(BrowserWebElementAction.IMAGE_MODE)
        }
        if (linkUrl != null) {
            add(BrowserWebElementAction.COPY_LINK)
        }
        if (imageUrl != null && imageUrl != linkUrl) {
            add(BrowserWebElementAction.COPY_IMAGE_LINK)
        } else if (resourceUrl != null && resourceUrl != linkUrl) {
            add(BrowserWebElementAction.COPY_RESOURCE_LINK)
        }
        if (linkUrl != null || resourceUrl != null) {
            add(BrowserWebElementAction.OPEN_EXTERNAL)
        }
    }
    val contentActions = buildList {
        if (state.text.isNotBlank()) {
            add(BrowserWebElementAction.COPY_TEXT)
            add(BrowserWebElementAction.SELECT_TEXT)
        }
        if (imageUrl != null) {
            add(BrowserWebElementAction.RECOGNIZE_QR)
        }
    }
    val blockingActions = buildList {
        add(BrowserWebElementAction.BLOCK_ELEMENT_QUICK)
        add(BrowserWebElementAction.BLOCK_ELEMENT_ADVANCED)
        if (resourceUrl != null || linkUrl != null) {
            add(BrowserWebElementAction.BLOCK_URL)
        }
    }
    return BrowserWebElementActionPlan(
        targetKind = targetKind,
        quickActions = quickActions,
        contentActions = contentActions,
        blockingActions = blockingActions,
    )
}

internal fun WebSessionWebElementActionState.browserOpenUrl(): String? =
    linkUrl?.takeIf(::isHttpBrowserNetworkUrl)

internal fun WebSessionWebElementActionState.imageUrl(): String? =
    resourceUrl
        ?.takeIf(::isHttpBrowserNetworkUrl)
        ?.takeIf { resourceKind == BrowserWebElementResourceKind.IMAGE }

internal fun WebSessionWebElementActionState.externalOpenUrl(): String? =
    linkUrl?.takeIf(::isHttpBrowserNetworkUrl)
        ?: resourceUrl?.takeIf(::isHttpBrowserNetworkUrl)

internal fun WebSessionWebElementActionState.blockableUrl(): String? =
    resourceUrl?.takeIf(::isHttpBrowserNetworkUrl)
        ?: linkUrl?.takeIf(::isHttpBrowserNetworkUrl)
