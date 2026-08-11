package com.kiyori.platform.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.OutputStream

data class KiyoriPublicWriteResult(
    val uri: Uri,
    val displayName: String,
    val displayPath: String,
)

/**
 * Kiyori 自有公开文件的唯一 MediaStore 写入 owner。
 *
 * pending URI 必须在完整写入后才提交；失败时删除目标，否则文件管理器会长期看到半成品。
 */
class KiyoriPublicStore(private val appContext: Context) {

    fun writeBytes(
        location: KiyoriPublicLocation,
        requestedFileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): KiyoriPublicWriteResult {
        require(bytes.isNotEmpty()) { "Public file content must not be empty" }
        return write(location, requestedFileName, mimeType) { output ->
            output.write(bytes)
        }
    }

    fun write(
        location: KiyoriPublicLocation,
        requestedFileName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit,
    ): KiyoriPublicWriteResult {
        require(mimeType.isNotBlank()) { "MIME type is required" }
        val projection = KiyoriPaths.publicProjection(location)
        val baseName = sanitizeFileName(requestedFileName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeWithMediaStore(projection, baseName, mimeType, writer)
        } else {
            writeToLegacyPublicDirectory(projection, baseName, mimeType, writer)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeWithMediaStore(
        projection: KiyoriPublicPathProjection,
        baseName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit,
    ): KiyoriPublicWriteResult {
        val resolver = appContext.contentResolver
        val collectionUri =
            when (projection.collection) {
                KiyoriPublicCollection.DOWNLOADS -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
                KiyoriPublicCollection.PICTURES -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        val relativePath = projection.relativePath.trimEnd('/') + "/"
        val displayName =
            resolveAvailableName(baseName) { candidate ->
                resolver.query(
                    collectionUri,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
                        "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf(relativePath, candidate),
                    null,
                )?.use { cursor -> cursor.moveToFirst() } == true
            }
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val uri =
            requireNotNull(resolver.insert(collectionUri, values)) {
                "Unable to create public file in ${projection.relativePath}"
            }
        try {
            requireNotNull(resolver.openOutputStream(uri, "w")) {
                "Unable to open public output stream"
            }.use { output ->
                writer(output)
                output.flush()
            }
            check(
                resolver.update(
                    uri,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    },
                    null,
                    null,
                ) == 1,
            ) {
                "Unable to commit public file"
            }
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
        return KiyoriPublicWriteResult(
            uri = uri,
            displayName = displayName,
            displayPath = "${projection.relativePath}/$displayName",
        )
    }

    private fun writeToLegacyPublicDirectory(
        projection: KiyoriPublicPathProjection,
        baseName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit,
    ): KiyoriPublicWriteResult {
        val root =
            when (projection.collection) {
                KiyoriPublicCollection.DOWNLOADS -> KiyoriPaths.downloadsDir()
                KiyoriPublicCollection.PICTURES ->
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_PICTURES,
                    )
            }
        val collectionPrefix =
            when (projection.collection) {
                KiyoriPublicCollection.DOWNLOADS -> "Download/"
                KiyoriPublicCollection.PICTURES -> "Pictures/"
            }
        val relativeWithinCollection = projection.relativePath.removePrefix(collectionPrefix)
        val directory = File(root, relativeWithinCollection)
        require(directory.isDirectory || directory.mkdirs()) {
            "Unable to create public directory: ${directory.absolutePath}"
        }
        val displayName = resolveAvailableName(baseName) { candidate -> File(directory, candidate).exists() }
        val file = File(directory, displayName)
        try {
            file.outputStream().use { output ->
                writer(output)
                output.flush()
            }
            require(file.isFile && file.length() > 0L) {
                "Public file write produced no content"
            }
        } catch (error: Exception) {
            file.delete()
            throw error
        }
        MediaScannerConnection.scanFile(
            appContext,
            arrayOf(file.absolutePath),
            arrayOf(mimeType),
            null,
        )
        return KiyoriPublicWriteResult(
            uri = Uri.fromFile(file),
            displayName = displayName,
            displayPath = "${projection.relativePath}/$displayName",
        )
    }

    private fun sanitizeFileName(rawName: String): String {
        val normalized =
            rawName
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .trim()
                .map { character ->
                    when {
                        character.code < 32 -> '_'
                        character in setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|') -> '_'
                        else -> character
                    }
                }
                .joinToString("")
                .trim('.', ' ')
                .take(180)
        require(normalized.isNotBlank()) { "Public file name is invalid" }
        return normalized
    }

    private fun resolveAvailableName(
        requestedName: String,
        exists: (String) -> Boolean,
    ): String {
        if (!exists(requestedName)) {
            return requestedName
        }
        val dotIndex = requestedName.lastIndexOf('.')
        val stem = if (dotIndex > 0) requestedName.substring(0, dotIndex) else requestedName
        val suffix = if (dotIndex > 0) requestedName.substring(dotIndex) else ""
        for (index in 1..10_000) {
            val candidate = "$stem ($index)$suffix"
            if (!exists(candidate)) {
                return candidate
            }
        }
        throw IllegalStateException("Unable to allocate a unique public file name")
    }
}
