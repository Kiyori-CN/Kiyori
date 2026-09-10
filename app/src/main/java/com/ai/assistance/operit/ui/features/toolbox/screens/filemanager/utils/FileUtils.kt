package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.rounded.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerFileKind
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.fileManagerFileKind
import androidx.compose.ui.graphics.vector.ImageVector
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.log10
import kotlin.math.pow
import kotlinx.serialization.json.Json

/** 图标与语义色使用同一个文件类别，避免相同扩展名出现不同分类。 */
fun getFileIcon(file: FileItem): ImageVector = when (fileManagerFileKind(file)) {
    FileManagerFileKind.PARENT -> Icons.Rounded.ArrowUpward
    FileManagerFileKind.FOLDER -> Icons.Rounded.Folder
    FileManagerFileKind.IMAGE -> Icons.Rounded.Image
    FileManagerFileKind.AUDIO -> Icons.Rounded.AudioFile
    FileManagerFileKind.VIDEO -> Icons.Rounded.VideoFile
    FileManagerFileKind.PDF -> Icons.Rounded.PictureAsPdf
    FileManagerFileKind.DOCUMENT -> Icons.Rounded.Description
    FileManagerFileKind.SHEET -> Icons.Rounded.TableChart
    FileManagerFileKind.PRESENTATION -> Icons.Rounded.Slideshow
    FileManagerFileKind.ARCHIVE -> Icons.Rounded.FolderZip
    FileManagerFileKind.CODE -> Icons.Rounded.Code
    FileManagerFileKind.TEXT -> Icons.AutoMirrored.Filled.TextSnippet
    FileManagerFileKind.PACKAGE -> Icons.Rounded.Android
    FileManagerFileKind.FILE -> Icons.AutoMirrored.Filled.InsertDriveFile
}

/** 获取文件类型描述 */
fun getFileType(context: Context, fileName: String): String {
    return when {
        fileName.endsWith(".pdf", ignoreCase = true) -> context.getString(R.string.file_type_pdf)
        fileName.endsWith(".jpg", ignoreCase = true) ||
                fileName.endsWith(".jpeg", ignoreCase = true) -> context.getString(R.string.file_type_jpeg)
        fileName.endsWith(".png", ignoreCase = true) -> context.getString(R.string.file_type_png)
        fileName.endsWith(".gif", ignoreCase = true) -> context.getString(R.string.file_type_gif)
        fileName.endsWith(".bmp", ignoreCase = true) -> context.getString(R.string.file_type_bmp)
        fileName.endsWith(".mp3", ignoreCase = true) -> context.getString(R.string.file_type_mp3)
        fileName.endsWith(".wav", ignoreCase = true) -> context.getString(R.string.file_type_wav)
        fileName.endsWith(".ogg", ignoreCase = true) -> context.getString(R.string.file_type_ogg)
        fileName.endsWith(".mp4", ignoreCase = true) -> context.getString(R.string.file_type_mp4)
        fileName.endsWith(".avi", ignoreCase = true) -> context.getString(R.string.file_type_avi)
        fileName.endsWith(".mkv", ignoreCase = true) -> context.getString(R.string.file_type_mkv)
        fileName.endsWith(".mov", ignoreCase = true) -> context.getString(R.string.file_type_mov)
        fileName.endsWith(".zip", ignoreCase = true) -> context.getString(R.string.file_type_zip)
        fileName.endsWith(".rar", ignoreCase = true) -> context.getString(R.string.file_type_rar)
        fileName.endsWith(".7z", ignoreCase = true) -> context.getString(R.string.file_type_7z)
        fileName.endsWith(".tar", ignoreCase = true) -> context.getString(R.string.file_type_tar)
        fileName.endsWith(".txt", ignoreCase = true) -> context.getString(R.string.file_type_txt)
        fileName.endsWith(".doc", ignoreCase = true) ||
                fileName.endsWith(".docx", ignoreCase = true) -> context.getString(R.string.file_type_doc)
        fileName.endsWith(".xls", ignoreCase = true) ||
                fileName.endsWith(".xlsx", ignoreCase = true) -> context.getString(R.string.file_type_xls)
        fileName.endsWith(".ppt", ignoreCase = true) ||
                fileName.endsWith(".pptx", ignoreCase = true) -> context.getString(R.string.file_type_ppt)
        else -> context.getString(R.string.file_type_unknown)
    }
}

/** 格式化文件大小 */
fun formatFileSize(size: Long): String {
    if (size <= 0) return "0.00B"

    val units = arrayOf("B", "K", "M", "G", "T", "P", "E")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)

    return String.format(java.util.Locale.US, "%.2f%s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}

/** 格式化日期 */
fun formatDate(dateString: String): String {
    return try {
        val simpleDateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH)
        val date = simpleDateFormat.parse(dateString)
        if (date != null) {
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            outputFormat.format(date)
        } else {
            dateString
        }
    } catch (e: Exception) {
        dateString
    }
}

/** 解析文件列表 */
fun parseFileList(result: String): List<FileItem> {
    return try {
        if (result.isBlank()) {
            AppLogger.d("FileUtils", "Empty result string")
            return emptyList()
        }

        AppLogger.d("FileUtils", "Parsing directory listing: $result")

        val directoryListing = Json.decodeFromString<DirectoryListingData>(result)
        AppLogger.d("FileUtils", "Parsed directory listing: $directoryListing")

        directoryListing.entries.map { entry ->
            FileItem(
                    name = entry.name,
                    isDirectory = entry.isDirectory,
                    size = entry.size,
                    lastModified = entry.lastModified.toLongOrNull() ?: 0
            )
        }
    } catch (e: Exception) {
        AppLogger.e("FileUtils", "Error parsing file list", e)
        emptyList()
    }
}
