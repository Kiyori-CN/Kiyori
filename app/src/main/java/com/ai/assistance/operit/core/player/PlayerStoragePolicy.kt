package com.ai.assistance.operit.core.player

internal enum class PlayerScreenshotDestinationKind {
    DOCUMENT_TREE,
    PUBLIC_DOWNLOAD_DIRECTORY,
    APPLICATION_DOWNLOAD_DIRECTORY,
}

internal data class PlayerScreenshotDestination(
    val kind: PlayerScreenshotDestinationKind,
    val treeUri: String = "",
) {
    init {
        require(
            (kind == PlayerScreenshotDestinationKind.DOCUMENT_TREE) == treeUri.isNotBlank(),
        ) {
            "Player screenshot document-tree destination must own exactly one URI"
        }
    }
}

internal fun resolvePlayerScreenshotDestination(
    playerDirectoryUri: String,
    browserUsesSystemDownloader: Boolean,
    browserDirectoryUri: String,
    browserAutoTransferToPublicDirectory: Boolean,
): PlayerScreenshotDestination =
    when {
        playerDirectoryUri.isNotBlank() ->
            PlayerScreenshotDestination(
                kind = PlayerScreenshotDestinationKind.DOCUMENT_TREE,
                treeUri = playerDirectoryUri,
            )
        browserUsesSystemDownloader ->
            PlayerScreenshotDestination(
                kind = PlayerScreenshotDestinationKind.PUBLIC_DOWNLOAD_DIRECTORY,
            )
        browserDirectoryUri.isNotBlank() ->
            PlayerScreenshotDestination(
                kind = PlayerScreenshotDestinationKind.DOCUMENT_TREE,
                treeUri = browserDirectoryUri,
            )
        browserAutoTransferToPublicDirectory ->
            PlayerScreenshotDestination(
                kind = PlayerScreenshotDestinationKind.PUBLIC_DOWNLOAD_DIRECTORY,
            )
        else ->
            PlayerScreenshotDestination(
                kind = PlayerScreenshotDestinationKind.APPLICATION_DOWNLOAD_DIRECTORY,
            )
    }
