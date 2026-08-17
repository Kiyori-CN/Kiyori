package com.ai.assistance.operit.util

import android.util.Base64
import android.util.Base64InputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaPoolManager {
    private const val TAG = "MediaPoolManager"

    private const val MAX_INPUT_BYTES = 20 * 1024 * 1024

    private data class TranscodedMedia(
        val file: File,
        val mimeType: String
    )

    private fun transcodeToFit(source: File, mimeType: String, targetBytes: Long): TranscodedMedia? {
        val baseDir = cacheDir ?: return null
        val dir = File(baseDir, "media_pool_transcoded")
        if (!dir.isDirectory && !dir.mkdirs()) {
            AppLogger.e(TAG, "无法创建媒体转码目录: ${dir.absolutePath}")
            return null
        }

        fun ok(out: File): Boolean = out.exists() && out.isFile && out.length() in 1..targetBytes

        val probeResponse =
            try {
                FFmpegUtil.probeMedia(source.absolutePath)
            } catch (error: Exception) {
                AppLogger.e(TAG, "媒体探测失败: ${source.absolutePath}", error)
                return null
            }
        if (!probeResponse.succeeded) {
            AppLogger.e(
                TAG,
                "媒体探测返回失败: state=${probeResponse.result.terminalState}, " +
                    "returnCode=${probeResponse.result.returnCode}, output=${probeResponse.output}",
            )
            return null
        }
        val durationSeconds =
            probeResponse.result.mediaInformation
                ?.duration
                ?.toDoubleOrNull()
                ?.takeIf { duration -> duration.isFinite() && duration > 0.0 }
        if (durationSeconds == null) {
            AppLogger.e(TAG, "无法确定媒体时长，拒绝执行不可验证的容量转码: ${source.absolutePath}")
            return null
        }

        if (mimeType.startsWith("audio/", ignoreCase = true)) {
            val plan =
                resolveMediaPoolTranscodePlan(
                    kind = MediaPoolTranscodeKind.AUDIO,
                    durationSeconds = durationSeconds,
                    targetBytes = targetBytes,
                )
            if (plan == null) {
                AppLogger.e(TAG, "音频时长无法在单次编码中满足容量上限: duration=$durationSeconds")
                return null
            }
            val out = File(dir, "${UUID.randomUUID()}.mp3")
            val arguments =
                listOf(
                    "-n",
                    "-i",
                    source.absolutePath,
                    "-map",
                    "0:a:0",
                    "-vn",
                    "-sn",
                    "-dn",
                    "-ac",
                    "1",
                    "-ar",
                    "16000",
                    "-c:a",
                    "libmp3lame",
                    "-b:a",
                    "${plan.audioBitrateKbps}k",
                    out.absolutePath,
                )
            val execution =
                try {
                    FFmpegUtil.executeArguments(arguments)
                } catch (error: Exception) {
                    AppLogger.e(TAG, "音频单次转码执行失败", error)
                    runCatching { out.delete() }
                    return null
                }
            val succeeded = execution.succeeded
            if (succeeded && ok(out)) {
                return TranscodedMedia(out, "audio/mpeg")
            }
            AppLogger.e(
                TAG,
                "音频单次转码未满足容量合同: state=${execution.result.terminalState}, " +
                    "returnCode=${execution.result.returnCode}, bytes=${out.length()}, " +
                    "target=$targetBytes, output=${execution.output}",
            )
            runCatching { out.delete() }
            return null
        }

        if (mimeType.startsWith("video/", ignoreCase = true)) {
            val plan =
                resolveMediaPoolTranscodePlan(
                    kind = MediaPoolTranscodeKind.VIDEO,
                    durationSeconds = durationSeconds,
                    targetBytes = targetBytes,
                )
            val videoBitrateKbps = plan?.videoBitrateKbps
            if (plan == null || videoBitrateKbps == null) {
                AppLogger.e(TAG, "视频时长无法在单次编码中满足容量上限: duration=$durationSeconds")
                return null
            }
            val out = File(dir, "${UUID.randomUUID()}.mp4")
            val arguments =
                listOf(
                    "-n",
                    "-i",
                    source.absolutePath,
                    "-map",
                    "0:v:0",
                    "-map",
                    "0:a:0?",
                    "-vf",
                    FFmpegUtil.scaleFilterMaxWidth(640),
                    "-c:v",
                    "libopenh264",
                    "-profile:v",
                    "constrained_baseline",
                    "-pix_fmt",
                    "yuv420p",
                    "-b:v",
                    "${videoBitrateKbps}k",
                    "-c:a",
                    "aac",
                    "-b:a",
                    "${plan.audioBitrateKbps}k",
                    "-ac",
                    "1",
                    "-ar",
                    "32000",
                    "-sn",
                    "-dn",
                    "-movflags",
                    "+faststart",
                    out.absolutePath,
                )
            val execution =
                try {
                    FFmpegUtil.executeArguments(arguments)
                } catch (error: Exception) {
                    AppLogger.e(TAG, "视频单次转码执行失败", error)
                    runCatching { out.delete() }
                    return null
                }
            val succeeded = execution.succeeded
            if (succeeded && ok(out)) {
                return TranscodedMedia(out, "video/mp4")
            }
            AppLogger.e(
                TAG,
                "视频单次转码未满足容量合同: state=${execution.result.terminalState}, " +
                    "returnCode=${execution.result.returnCode}, bytes=${out.length()}, " +
                    "target=$targetBytes, output=${execution.output}",
            )
            runCatching { out.delete() }
            return null
        }

        return null
    }

    private fun extForMimeType(mimeType: String): String {
        val mt = mimeType.lowercase().substringBefore(';')
        return when (mt) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/ogg", "audio/opus" -> "ogg"
            "audio/webm" -> "webm"
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/ogg" -> "ogv"
            else -> mt.substringAfter('/', "bin").ifBlank { "bin" }
        }
    }

    private fun decodeBase64ToFile(base64: String, outFile: File) {
        val input: InputStream = Base64InputStream(AsciiStringInputStream(base64), Base64.DEFAULT)
        input.use { ins ->
            FileOutputStream(outFile).use { fos ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = ins.read(buffer)
                    if (read <= 0) break
                    fos.write(buffer, 0, read)
                }
            }
        }
    }

    private class AsciiStringInputStream(private val data: String) : InputStream() {
        private var index = 0

        override fun read(): Int {
            if (index >= data.length) return -1
            return data[index++].code and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (index >= data.length) return -1
            val count = minOf(len, data.length - index)
            for (i in 0 until count) {
                b[off + i] = data[index + i].code.toByte()
            }
            index += count
            return count
        }
    }

    var maxPoolSize = 12
        set(value) {
            if (value > 0) {
                field = value
                AppLogger.d(TAG, "池子大小限制已更新为: $value")
            }
        }

    private var cacheDir: File? = null

    data class MediaData(
        val base64: String,
        val mimeType: String
    )

    fun initialize(cacheDirPath: File, preloadNow: Boolean = true) {
        cacheDir = OperitPaths.mediaPoolDir(cacheDirPath)
        if (!cacheDir!!.exists()) {
            cacheDir!!.mkdirs()
            AppLogger.d(TAG, "创建媒体缓存目录: ${cacheDir!!.absolutePath}")
        }
        if (preloadNow) {
            loadAllFromDisk()
        }
    }

    @Synchronized
    fun preloadFromDisk() {
        loadAllFromDisk()
    }

    private val mediaPool = object : LinkedHashMap<String, MediaData>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaData>?): Boolean {
            val shouldRemove = size > maxPoolSize
            if (shouldRemove && eldest != null) {
                AppLogger.d(TAG, "池子已满，移除最旧的媒体: ${eldest.key}")
                deleteFromDisk(eldest.key)
            }
            return shouldRemove
        }
    }

    suspend fun addMedia(filePath: String, mimeType: String): String =
        withContext(Dispatchers.IO) {
            addMediaOnBackground(filePath, mimeType)
        }

    @Synchronized
    private fun addMediaOnBackground(filePath: String, mimeType: String): String {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                AppLogger.e(TAG, "文件不存在或不是文件: $filePath")
                return "error"
            }

            val fileSize = runCatching { file.length() }.getOrNull() ?: -1L
            var effectiveFile = file
            var effectiveMimeType = mimeType
            if (fileSize > MAX_INPUT_BYTES) {
                val transcoded = transcodeToFit(file, mimeType, MAX_INPUT_BYTES.toLong())
                if (transcoded == null) {
                    AppLogger.e(TAG, "媒体文件过大且压缩失败，拒绝加入池子: $filePath, bytes=$fileSize")
                    return "error"
                }
                effectiveFile = transcoded.file
                effectiveMimeType = transcoded.mimeType
            }

            val bytes = try {
                FileInputStream(effectiveFile).use { it.readBytes() }
            } catch (e: Exception) {
                AppLogger.e(TAG, "读取文件失败", e)
                if (effectiveFile != file) {
                    runCatching { effectiveFile.delete() }
                }
                return "error"
            }

            if (effectiveFile != file) {
                runCatching { effectiveFile.delete() }
            }

            if (bytes.size > MAX_INPUT_BYTES) {
                AppLogger.e(TAG, "媒体文件过大，拒绝加入池子: $filePath, bytes=${bytes.size}")
                return "error"
            }

            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val id = UUID.randomUUID().toString()
            val mediaData = MediaData(base64 = base64, mimeType = effectiveMimeType)
            mediaPool[id] = mediaData
            saveToDisk(id, mediaData)
            AppLogger.d(TAG, "成功添加媒体到池子: $id, mimeType=$mimeType, sizeBytes=${bytes.size}")
            id
        } catch (e: Exception) {
            AppLogger.e(TAG, "添加媒体时发生异常: $filePath", e)
            "error"
        }
    }

    suspend fun addMediaFromBase64(base64: String, mimeType: String): String =
        withContext(Dispatchers.IO) {
            addMediaFromBase64OnBackground(base64, mimeType)
        }

    @Synchronized
    private fun addMediaFromBase64OnBackground(base64: String, mimeType: String): String {
        return try {
            val estimated = MediaBase64Limiter.estimateDecodedSizeBytes(base64)
            if (estimated == null) {
                AppLogger.e(TAG, "媒体base64估算失败，拒绝加入池子: mimeType=$mimeType")
                return "error"
            }

            if (estimated <= MAX_INPUT_BYTES) {
                val bytes = try {
                    Base64.decode(base64, Base64.DEFAULT)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "媒体base64解码失败", e)
                    return "error"
                }

                if (bytes.size > MAX_INPUT_BYTES) {
                    AppLogger.e(TAG, "媒体base64解码后过大，拒绝加入池子: mimeType=$mimeType, bytes=${bytes.size}")
                    return "error"
                }

                val id = UUID.randomUUID().toString()
                val normalizedBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val mediaData = MediaData(base64 = normalizedBase64, mimeType = mimeType)
                mediaPool[id] = mediaData
                saveToDisk(id, mediaData)
                AppLogger.d(TAG, "成功从base64添加媒体到池子: $id, mimeType=$mimeType, sizeChars=${base64.length}")
                return id
            }

            val baseDir = cacheDir
            if (baseDir == null) {
                AppLogger.e(TAG, "媒体base64过大且缓存目录未初始化，拒绝加入池子: mimeType=$mimeType, estimatedBytes=$estimated")
                return "error"
            }

            val dir = File(baseDir, "media_pool_base64_decode")
            runCatching { if (!dir.exists()) dir.mkdirs() }
            val inFile = File(dir, "${UUID.randomUUID()}.${extForMimeType(mimeType)}")
            try {
                decodeBase64ToFile(base64, inFile)
            } catch (e: Exception) {
                AppLogger.e(TAG, "媒体base64流式解码失败", e)
                runCatching { inFile.delete() }
                return "error"
            }

            val transcoded = transcodeToFit(inFile, mimeType, MAX_INPUT_BYTES.toLong())
            runCatching { inFile.delete() }
            if (transcoded == null) {
                AppLogger.e(TAG, "媒体base64过大且压缩失败，拒绝加入池子: mimeType=$mimeType, estimatedBytes=$estimated")
                return "error"
            }

            val bytes = try {
                FileInputStream(transcoded.file).use { it.readBytes() }
            } catch (e: Exception) {
                AppLogger.e(TAG, "读取转码后的媒体失败", e)
                runCatching { transcoded.file.delete() }
                return "error"
            }

            runCatching { transcoded.file.delete() }

            if (bytes.size > MAX_INPUT_BYTES) {
                AppLogger.e(TAG, "转码后的媒体仍然过大，拒绝加入池子: mimeType=${transcoded.mimeType}, bytes=${bytes.size}")
                return "error"
            }

            val id = UUID.randomUUID().toString()
            val normalizedBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val mediaData = MediaData(base64 = normalizedBase64, mimeType = transcoded.mimeType)
            mediaPool[id] = mediaData
            saveToDisk(id, mediaData)
            AppLogger.d(TAG, "成功从base64添加媒体到池子(已压缩): $id, mimeType=${transcoded.mimeType}, sizeChars=${base64.length}")
            id
        } catch (e: Exception) {
            AppLogger.e(TAG, "从base64添加媒体时发生异常", e)
            "error"
        }
    }

    @Synchronized
    fun getMedia(id: String): MediaData? {
        val cached = mediaPool[id]
        if (cached != null) return cached

        val loaded = loadOneFromDisk(id) ?: return null
        mediaPool[id] = loaded
        return loaded
    }

    private fun loadOneFromDisk(id: String): MediaData? {
        val dir = cacheDir ?: return null
        return try {
            val metaFile = File(dir, "$id.meta")
            val b64File = File(dir, "$id.b64")
            if (!metaFile.exists() || !b64File.exists()) {
                return null
            }
            val mimeType = runCatching { metaFile.readText().trim() }.getOrNull() ?: return null
            val base64 = runCatching { b64File.readText().trim() }.getOrNull() ?: return null
            if (mimeType.isBlank() || base64.isBlank()) {
                return null
            }
            MediaData(base64 = base64, mimeType = mimeType)
        } catch (e: Exception) {
            AppLogger.e(TAG, "从磁盘加载媒体失败: $id", e)
            null
        }
    }

    @Synchronized
    fun removeMedia(id: String) {
        mediaPool.remove(id)
        deleteFromDisk(id)
    }

    private fun saveToDisk(id: String, data: MediaData) {
        val dir = cacheDir ?: return
        try {
            val metaFile = File(dir, "$id.meta")
            val b64File = File(dir, "$id.b64")
            metaFile.writeText(data.mimeType)
            b64File.writeText(data.base64)
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存媒体到磁盘失败: $id", e)
        }
    }

    private fun deleteFromDisk(id: String) {
        val dir = cacheDir ?: return
        try {
            File(dir, "$id.meta").delete()
            File(dir, "$id.b64").delete()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun loadAllFromDisk() {
        val dir = cacheDir ?: return
        try {
            val metaFiles = dir.listFiles { file -> file.isFile && file.name.endsWith(".meta") } ?: return
            metaFiles.forEach { metaFile ->
                val id = metaFile.name.removeSuffix(".meta")
                val loaded = loadOneFromDisk(id) ?: return@forEach
                mediaPool[id] = loaded
            }
            AppLogger.d(TAG, "从磁盘加载媒体缓存完成: size=${mediaPool.size}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "从磁盘加载媒体缓存失败", e)
        }
    }
}
