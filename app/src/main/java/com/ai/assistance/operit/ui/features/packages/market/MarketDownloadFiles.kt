package com.ai.assistance.operit.ui.features.packages.market

import java.io.File
import java.nio.file.Files
import java.util.Locale

/** 外部资产名只用于识别格式，永远不参与本地路径拼接。 */
internal fun prepareMarketDownload(directory: File, assetName: String, download: (File) -> Unit): File {
    val extension = assetName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    require(extension in setOf("toolpkg", "js", "ts", "hjson")) { "Unsupported market artifact format" }
    Files.createDirectories(directory.toPath())
    val target = File.createTempFile("market_", ".$extension", directory)
    try {
        download(target)
        check(target.isFile && target.length() > 0L) { "Downloaded artifact is empty" }
        return target
    } catch (failure: Exception) {
        try { Files.deleteIfExists(target.toPath()) }
        catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
        throw failure
    }
}
