package com.ai.assistance.operit.core.player

import android.content.Context
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

internal class Anime4KShaderManager(context: Context) {
    private val appContext = context.applicationContext
    private val shaderDirectory = File(appContext.filesDir, "player-shaders/32f5f169")
    private val verifiedShaderPaths = mutableMapOf<String, String>()

    fun resolveShaderFiles(mode: Anime4KMode): List<String> {
        if (mode == Anime4KMode.OFF) return emptyList()
        check(shaderDirectory.mkdirs() || shaderDirectory.isDirectory) {
            "Unable to create Anime4K shader directory: $shaderDirectory"
        }
        return mode.shaderFiles.map { fileName ->
            verifiedShaderPaths[fileName]?.let { verifiedPath ->
                val verifiedFile = File(verifiedPath)
                check(verifiedFile.isFile && verifiedFile.length() > 0L) {
                    "Verified Anime4K shader disappeared: $fileName"
                }
                return@map verifiedFile.absolutePath
            }
            val expectedHash =
                requireNotNull(EXPECTED_SHADER_HASHES[fileName]) {
                    "Anime4K shader has no locked source hash: $fileName"
                }
            val packagedHash =
                appContext.assets.open("shaders/$fileName").use(InputStream::sha256Hex)
            check(packagedHash == expectedHash) {
                "Packaged Anime4K shader differs from mpv-android-anime4k@32f5f169: $fileName"
            }
            val destination = File(shaderDirectory, fileName)
            if (!destination.isFile || destination.inputStream().use(InputStream::sha256Hex) != expectedHash) {
                appContext.assets.open("shaders/$fileName").use { input ->
                    destination.outputStream().use(input::copyTo)
                }
            }
            check(
                destination.isFile &&
                    destination.length() > 0L &&
                    destination.inputStream().use(InputStream::sha256Hex) == expectedHash,
            ) {
                "Anime4K shader cache verification failed: $fileName"
            }
            destination.absolutePath.also { path ->
                verifiedShaderPaths[fileName] = path
            }
        }
    }

    private companion object {
        val EXPECTED_SHADER_HASHES =
            mapOf(
                "Anime4K_AutoDownscalePre_x2.glsl" to
                    "9141668ced0b26512253e6396e805820716f35b57c92950d9da489f8b96a7ba4",
                "Anime4K_AutoDownscalePre_x4.glsl" to
                    "dadb7b713cfa1d810c55b5deff616072f3390e546fed1e6a54f80ea555f7b95d",
                "Anime4K_Clamp_Highlights.glsl" to
                    "8c5fb67c76bed3021f8a27b050c3b97a6ac1b284f9ce91c04189015c354c0217",
                "Anime4K_Restore_CNN_M.glsl" to
                    "dd515c307d97d8e5c809f263dd94174cc5667b8c1299082cdff14e6ddfc8d4bc",
                "Anime4K_Restore_CNN_S.glsl" to
                    "fca48f8322be4c7c5b14393a6eb6d733bbefffea0ca29694cb6c4b0335dad5ce",
                "Anime4K_Restore_CNN_Soft_M.glsl" to
                    "df1cdc360d6fbfd51b6d6deec99aefb747d0de72cc1c0ff271cd48758a6a0c5a",
                "Anime4K_Restore_CNN_Soft_S.glsl" to
                    "17fe08df911bd7ae67235da8076701d51647a5fcacab8f1f1f042a1a85f0bb50",
                "Anime4K_Upscale_CNN_x2_M.glsl" to
                    "249dc3be467f556ed3361deea79f42bac1ae57456c22588c2cc3c2ee8808909c",
                "Anime4K_Upscale_CNN_x2_S.glsl" to
                    "90b65a4f36950852a34e5f12beb179fafed59fa8d911887e0f5f184337998edf",
                "Anime4K_Upscale_Denoise_CNN_x2_M.glsl" to
                    "ca51390eabca94ed3e1d9b40dc15b34045cc966f232ba9490ae0b4c1834d94f6",
            )
    }
}

private fun InputStream.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}
