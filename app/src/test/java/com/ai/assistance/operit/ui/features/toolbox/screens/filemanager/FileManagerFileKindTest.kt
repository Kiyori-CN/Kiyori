package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import org.junit.Assert.*
import org.junit.Test

class FileManagerFileKindTest {
    @Test fun `directory identity wins over extension and parent is a navigation action`() {
        assertEquals(FileManagerFileKind.FOLDER, fileManagerFileKind(FileItem("images.zip", true)))
        assertEquals(FileManagerFileKind.PARENT, fileManagerFileKind(FileItem("..", true)))
    }

    @Test fun `modern media code and package extensions share consistent case insensitive kinds`() {
        val cases = mapOf(
            "照片.AVIF" to FileManagerFileKind.IMAGE, "voice.opus" to FileManagerFileKind.AUDIO,
            "video.WEBM" to FileManagerFileKind.VIDEO, "app.tsx" to FileManagerFileKind.CODE,
            "备份.tar.zst" to FileManagerFileKind.ARCHIVE, "app.APK" to FileManagerFileKind.PACKAGE,
            "report.ods" to FileManagerFileKind.SHEET, "notes.md" to FileManagerFileKind.TEXT,
            "slide.pptx" to FileManagerFileKind.PRESENTATION, "book.PDF" to FileManagerFileKind.PDF,
        )
        cases.forEach { (name, kind) -> assertEquals(name, kind, fileManagerFileKind(FileItem(name, false))) }
    }

    @Test fun `unknown and misleading suffixes remain generic files without content inference`() {
        listOf("README", "file.", "photo.jpg.exe", "archive.zip ").forEach {
            assertEquals(it, FileManagerFileKind.FILE, fileManagerFileKind(FileItem(it, false)))
        }
    }
}
