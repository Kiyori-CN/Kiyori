package com.ai.assistance.operit.core.player

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

internal object PlayerQueueResolver {
    fun resolve(
        context: Context,
        current: PlayerMediaRequest,
    ): List<PlayerMediaRequest> {
        if (current.source != PlayerMediaSource.EXTERNAL_INTENT) return listOf(current)
        val uri = current.uri.toUri()
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            "file" -> resolveFileQueue(current, uri)
            "content" -> resolveMediaStoreQueue(context, current, uri)
            else -> listOf(current)
        }
    }

    private fun resolveFileQueue(
        current: PlayerMediaRequest,
        uri: Uri,
    ): List<PlayerMediaRequest> {
        val currentFile = uri.path?.let(::File) ?: return listOf(current)
        val directory = currentFile.parentFile?.takeIf(File::isDirectory) ?: return listOf(current)
        val seriesKey = playerSeriesKey(currentFile.name)
        if (seriesKey.isBlank()) return listOf(current)
        val candidates =
            directory.listFiles()
                ?.asSequence()
                ?.filter(File::isFile)
                ?.filter { file -> file.extension.lowercase(Locale.ROOT) in VIDEO_EXTENSIONS }
                ?.filter { file -> playerSeriesKey(file.name) == seriesKey }
                ?.sortedWith(compareByNaturalName { file -> file.name })
                ?.map { file ->
                    val candidateUri = Uri.fromFile(file).toString()
                    if (candidateUri == current.uri) {
                        current
                    } else {
                        current.copy(
                            requestId = playerQueueRequestId(candidateUri),
                            uri = candidateUri,
                            title = file.name,
                            headers = emptyMap(),
                            sourceSessionId = null,
                            cookieScopeUrl = null,
                        )
                    }
                }
                ?.toList()
                .orEmpty()
        return candidates.takeIf { queue -> queue.any { it.uri == current.uri } } ?: listOf(current)
    }

    private fun resolveMediaStoreQueue(
        context: Context,
        current: PlayerMediaRequest,
        uri: Uri,
    ): List<PlayerMediaRequest> {
        if (uri.authority != MediaStore.AUTHORITY) return listOf(current)
        val resolver = context.contentResolver
        val seriesKey = playerSeriesKey(current.title)
        if (seriesKey.isBlank()) return listOf(current)
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val volumeName = uri.pathSegments.firstOrNull().orEmpty()
                if (volumeName.isBlank()) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Video.Media.getContentUri(volumeName)
                }
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        val location =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.query(
                    uri,
                    arrayOf(MediaStore.Video.Media.RELATIVE_PATH),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val index = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                    if (index >= 0) cursor.getString(index) else null
                }?.let { relativePath ->
                    MediaStoreLocation(
                        selection = "${MediaStore.Video.Media.RELATIVE_PATH} = ?",
                        argument = relativePath,
                    )
                }
            } else {
                resolver.query(
                    uri,
                    arrayOf(MediaStore.Video.Media.DATA),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val index = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    if (index >= 0) cursor.getString(index) else null
                }?.let { path ->
                    File(path).parentFile?.absolutePath
                }?.let { directory ->
                    MediaStoreLocation(
                        selection = "${MediaStore.Video.Media.DATA} LIKE ?",
                        argument = "$directory${File.separator}%",
                    )
                }
            } ?: return listOf(current)
        val candidates =
            resolver.query(
                collection,
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                ),
                location.selection,
                arrayOf(location.argument),
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameIndex =
                    cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                buildList {
                    while (cursor.moveToNext()) {
                        val title = cursor.getString(nameIndex)?.takeIf(String::isNotBlank) ?: continue
                        if (playerSeriesKey(title) != seriesKey) continue
                        val candidateUri =
                            Uri.withAppendedPath(
                                collection,
                                cursor.getLong(idIndex).toString(),
                            ).toString()
                        add(
                            if (candidateUri == current.uri) {
                                current
                            } else {
                                current.copy(
                                    requestId = playerQueueRequestId(candidateUri),
                                    uri = candidateUri,
                                    title = title,
                                    headers = emptyMap(),
                                    sourceSessionId = null,
                                    cookieScopeUrl = null,
                                )
                            },
                        )
                    }
                }
            }.orEmpty()
                .sortedWith(compareByNaturalName(PlayerMediaRequest::title))
        return candidates.takeIf { queue -> queue.any { it.uri == current.uri } } ?: listOf(current)
    }
}

internal fun playerSeriesKey(title: String): String =
    title
        .substringBeforeLast('.', title)
        .lowercase(Locale.ROOT)
        .replace(Regex("""\[[^]]*]|\([^)]*\)|【[^】]*】"""), " ")
        .replace(Regex("""第\s*\d+\s*[集话話期]"""), " ")
        .replace(Regex("""\b(?:s\d{1,2}\s*)?e\d{1,4}\b"""), " ")
        .replace(Regex("""\b(?:ep|episode)\s*\d{1,4}\b"""), " ")
        .replace(Regex("""(?:^|[\s._-])\d{1,4}(?=$|[\s._-])"""), " ")
        .replace(Regex("""\b(?:2160p|1440p|1080p|720p|480p|4k|8k)\b"""), " ")
        .replace(Regex("""[\s._-]+"""), " ")
        .trim()

internal fun naturalPlayerTitleCompare(left: String, right: String): Int {
    val leftParts = NATURAL_PART_REGEX.findAll(left.lowercase(Locale.ROOT)).map { it.value }.toList()
    val rightParts =
        NATURAL_PART_REGEX.findAll(right.lowercase(Locale.ROOT)).map { it.value }.toList()
    val comparedParts = minOf(leftParts.size, rightParts.size)
    repeat(comparedParts) { index ->
        val leftPart = leftParts[index]
        val rightPart = rightParts[index]
        val comparison =
            if (leftPart.firstOrNull()?.isDigit() == true &&
                rightPart.firstOrNull()?.isDigit() == true
            ) {
                leftPart.trimStart('0').ifEmpty { "0" }.let { normalizedLeft ->
                    val normalizedRight = rightPart.trimStart('0').ifEmpty { "0" }
                    normalizedLeft.length.compareTo(normalizedRight.length)
                        .takeIf { it != 0 }
                        ?: normalizedLeft.compareTo(normalizedRight)
                        .takeIf { it != 0 }
                        ?: leftPart.length.compareTo(rightPart.length)
                }
            } else {
                leftPart.compareTo(rightPart)
            }
        if (comparison != 0) return comparison
    }
    return leftParts.size.compareTo(rightParts.size).takeIf { it != 0 }
        ?: left.compareTo(right, ignoreCase = true)
}

private fun <T> compareByNaturalName(selector: (T) -> String): Comparator<T> =
    Comparator { left, right ->
        naturalPlayerTitleCompare(selector(left), selector(right))
    }

private fun playerQueueRequestId(uri: String): String =
    "queue-${UUID.nameUUIDFromBytes(uri.toByteArray(StandardCharsets.UTF_8))}"

private val VIDEO_EXTENSIONS =
    setOf(
        "3gp",
        "avi",
        "flv",
        "m2ts",
        "m4v",
        "mkv",
        "mov",
        "mp4",
        "mpeg",
        "mpg",
        "mts",
        "ts",
        "webm",
        "wmv",
    )

private val NATURAL_PART_REGEX = Regex("""\d+|\D+""")

private data class MediaStoreLocation(
    val selection: String,
    val argument: String,
)
