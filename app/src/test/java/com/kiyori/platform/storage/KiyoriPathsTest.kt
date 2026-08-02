package com.kiyori.platform.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriPathsTest {

    @Test
    fun `public sdcard paths keep exact Kiyori layout`() {
        assertEquals("/sdcard/Download/Kiyori", KiyoriPaths.kiyoriRootPathSdcard())
        assertEquals(
            "/sdcard/Download/Kiyori/cleanOnExit",
            KiyoriPaths.cleanOnExitPathSdcard(),
        )
        assertEquals("/sdcard/Download/Kiyori/plugins", KiyoriPaths.pluginsPathSdcard())
        assertEquals("/sdcard/Download/Kiyori/bridge", KiyoriPaths.bridgePathSdcard())
        assertEquals("/sdcard/Download/Kiyori/exports", KiyoriPaths.exportsPathSdcard())
        assertEquals(
            "/sdcard/Download/Kiyori/workspace/chat-1",
            KiyoriPaths.workspacePathSdcard("chat-1"),
        )
        assertEquals("/sdcard/Download/Kiyori/test", KiyoriPaths.testPathSdcard())
        assertEquals(
            "/sdcard/Download/Kiyori/websession/userscripts",
            KiyoriPaths.webSessionUserscriptsPathSdcard(),
        )
    }

    @Test
    fun `raw snapshot exclusions keep exact names`() {
        assertEquals(
            setOf(
                ".sherpa_ncnn_models",
                ".vector_index",
                "image_pool",
                "media_pool",
                "skill_repo_zip_pool",
            ),
            KiyoriPaths.rawSnapshotExcludedFilesTopLevelDirNames(),
        )
    }

    @Test
    fun `plugin directory names keep existing sanitization`() {
        assertEquals("demo.plugin", KiyoriPaths.pluginConfigDirectoryName("demo.plugin"))
        assertEquals(
            "folder_name-3f3438c",
            KiyoriPaths.pluginConfigDirectoryName("folder/name"),
        )
        assertEquals("plugin-0", KiyoriPaths.pluginConfigDirectoryName("  "))
    }

    @Test
    fun `backup directories keep exact hierarchy`() {
        val root = Files.createTempDirectory("kiyori-backup-paths").toFile()
        try {
            assertDirectory(root, "backup", KiyoriPaths.backupRootDir(root))
            assertDirectory(root, "backup/raw_snapshot", KiyoriPaths.rawSnapshotDir(root))
            assertDirectory(root, "backup/room_db", KiyoriPaths.roomDbDir(root))
            assertDirectory(root, "backup/chat", KiyoriPaths.chatDir(root))
            assertDirectory(root, "backup/memory", KiyoriPaths.memoryDir(root))
            assertDirectory(root, "backup/model_config", KiyoriPaths.modelConfigDir(root))
            assertDirectory(
                root,
                "backup/character_cards",
                KiyoriPaths.characterCardsDir(root),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `base directory pools keep exact names`() {
        val root = Files.createTempDirectory("kiyori-pool-paths").toFile()
        try {
            assertDirectory(root, "image_pool", KiyoriPaths.imagePoolDir(root))
            assertDirectory(root, "media_pool", KiyoriPaths.mediaPoolDir(root))
            assertDirectory(
                root,
                "skill_repo_zip_pool",
                KiyoriPaths.skillRepoZipPoolDir(root),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun assertDirectory(
        root: File,
        relativePath: String,
        actual: File,
    ) {
        assertEquals(File(root, relativePath).canonicalFile, actual.canonicalFile)
        assertTrue(actual.isDirectory)
    }
}
