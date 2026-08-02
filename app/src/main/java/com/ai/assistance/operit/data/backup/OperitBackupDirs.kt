package com.ai.assistance.operit.data.backup

import com.kiyori.platform.storage.KiyoriBackupPaths
import java.io.File

/**
 * 旧备份路径 FQCN 的兼容入口。
 *
 * 该对象保留原方法 ABI，但不再拥有目录名、层级或创建逻辑。
 */
object OperitBackupDirs {

    fun kiyoriRootDir(): File = KiyoriBackupPaths.kiyoriRootDir()

    fun backupRootDir(): File = KiyoriBackupPaths.backupRootDir()

    fun rawSnapshotDir(): File = KiyoriBackupPaths.rawSnapshotDir()

    fun roomDbDir(): File = KiyoriBackupPaths.roomDbDir()

    fun chatDir(): File = KiyoriBackupPaths.chatDir()

    fun memoryDir(): File = KiyoriBackupPaths.memoryDir()

    fun modelConfigDir(): File = KiyoriBackupPaths.modelConfigDir()

    fun characterCardsDir(): File = KiyoriBackupPaths.characterCardsDir()
}
