package com.ai.assistance.operit.core.player

import android.content.Context
import java.io.File

internal class Anime4KShaderManager(context: Context) {
    private val appContext = context.applicationContext
    private val shaderDirectory = File(appContext.filesDir, "player-shaders/32f5f169")

    fun resolveShaderFiles(mode: Anime4KMode): List<String> {
        if (mode == Anime4KMode.OFF) return emptyList()
        check(shaderDirectory.mkdirs() || shaderDirectory.isDirectory) {
            "Unable to create Anime4K shader directory: $shaderDirectory"
        }
        return mode.shaderFiles.map { fileName ->
            val destination = File(shaderDirectory, fileName)
            if (!destination.isFile) {
                appContext.assets.open("shaders/$fileName").use { input ->
                    destination.outputStream().use(input::copyTo)
                }
            }
            check(destination.isFile && destination.length() > 0L) {
                "Anime4K shader is unavailable: $fileName"
            }
            destination.absolutePath
        }
    }
}
