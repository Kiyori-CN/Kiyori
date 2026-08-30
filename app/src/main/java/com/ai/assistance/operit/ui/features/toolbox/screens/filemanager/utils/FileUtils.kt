package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.log10
import kotlin.math.pow
import kotlinx.serialization.json.Json

/** 获取文件图标 */
fun getFileIcon(file: FileItem): ImageVector {
    return if (file.isDirectory) {
        Icons.Default.Folder
    } else {
        when {
            file.name.endsWith(".pdf") -> Icons.Default.PictureAsPdf
            file.name.endsWith(".jpg", ignoreCase = true) ||
                    file.name.endsWith(".jpeg", ignoreCase = true) ||
                    file.name.endsWith(".png", ignoreCase = true) ||
                    file.name.endsWith(".gif", ignoreCase = true) ||
                    file.name.endsWith(".bmp", ignoreCase = true) -> Icons.Default.Image
            file.name.endsWith(".mp3", ignoreCase = true) ||
                    file.name.endsWith(".wav", ignoreCase = true) ||
                    file.name.endsWith(".ogg", ignoreCase = true) -> Icons.Default.AudioFile
            file.name.endsWith(".mp4", ignoreCase = true) ||
                    file.name.endsWith(".avi", ignoreCase = true) ||
                    file.name.endsWith(".mkv", ignoreCase = true) ||
                    file.name.endsWith(".mov", ignoreCase = true) -> Icons.Default.VideoFile
            file.name.endsWith(".zip", ignoreCase = true) ||
                    file.name.endsWith(".rar", ignoreCase = true) ||
                    file.name.endsWith(".7z", ignoreCase = true) ||
                    file.name.endsWith(".tar", ignoreCase = true) -> Icons.Default.FolderZip
            file.name.endsWith(".txt", ignoreCase = true) -> Icons.AutoMirrored.Filled.TextSnippet
            file.name.endsWith(".doc", ignoreCase = true) ||
                    file.name.endsWith(".docx", ignoreCase = true) -> Icons.Default.Description
            file.name.endsWith(".xls", ignoreCase = true) ||
                    file.name.endsWith(".xlsx", ignoreCase = true) -> Icons.Default.TableChart
            file.name.endsWith(".ppt", ignoreCase = true) ||
                    file.name.endsWith(".pptx", ignoreCase = true) -> Icons.Default.Description
            file.name.endsWith(".js", ignoreCase = true) ||
                    file.name.endsWith(".py", ignoreCase = true) ||
                    file.name.endsWith(".html", ignoreCase = true) ||
                    file.name.endsWith(".css", ignoreCase = true) ||
                    file.name.endsWith(".xml", ignoreCase = true) -> Icons.Default.Code
            else -> Icons.AutoMirrored.Filled.InsertDriveFile
        }
    }
}

/** MT 风格的文件类型色，图标形状和底色同时表达常见扩展名。 */
fun getFileIconColor(file: FileItem): Color {
    if (file.isDirectory || file.name == "..") return Color(0xFF242424)
    return when {
        file.name.endsWith(".pdf", ignoreCase = true) -> Color(0xFFE51C23)
        file.name.endsWith(".mp3", ignoreCase = true) ||
            file.name.endsWith(".wav", ignoreCase = true) ||
            file.name.endsWith(".ogg", ignoreCase = true) -> Color(0xFFE8323B)
        file.name.endsWith(".mp4", ignoreCase = true) ||
            file.name.endsWith(".avi", ignoreCase = true) ||
            file.name.endsWith(".mkv", ignoreCase = true) ||
            file.name.endsWith(".mov", ignoreCase = true) -> Color(0xFF858585)
        file.name.endsWith(".jpg", ignoreCase = true) ||
            file.name.endsWith(".jpeg", ignoreCase = true) ||
            file.name.endsWith(".png", ignoreCase = true) ||
            file.name.endsWith(".gif", ignoreCase = true) ||
            file.name.endsWith(".bmp", ignoreCase = true) -> Color(0xFF858585)
        file.name.endsWith(".doc", ignoreCase = true) ||
            file.name.endsWith(".docx", ignoreCase = true) -> Color(0xFF3769BD)
        file.name.endsWith(".ppt", ignoreCase = true) ||
            file.name.endsWith(".pptx", ignoreCase = true) -> Color(0xFFD8665B)
        file.name.endsWith(".xls", ignoreCase = true) ||
            file.name.endsWith(".xlsx", ignoreCase = true) -> Color(0xFF64A900)
        file.name.endsWith(".zip", ignoreCase = true) ||
            file.name.endsWith(".rar", ignoreCase = true) ||
            file.name.endsWith(".7z", ignoreCase = true) ||
            file.name.endsWith(".tar", ignoreCase = true) -> Color(0xFF7D5A4E)
        file.name.endsWith(".js", ignoreCase = true) ||
            file.name.endsWith(".md", ignoreCase = true) ||
            file.name.endsWith(".txt", ignoreCase = true) ||
            file.name.endsWith(".json", ignoreCase = true) ||
            file.name.endsWith(".xml", ignoreCase = true) ||
            file.name.endsWith(".html", ignoreCase = true) ||
            file.name.endsWith(".css", ignoreCase = true) -> Color(0xFF3769BD)
        file.name.endsWith(".py", ignoreCase = true) -> Color(0xFF3769BD)
        else -> Color(0xFF607D8B)
    }
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
    if (size <= 0) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()

    return String.format(java.util.Locale.getDefault(), "%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
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
