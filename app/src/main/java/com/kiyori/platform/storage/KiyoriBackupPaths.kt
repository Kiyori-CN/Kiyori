package com.kiyori.platform.storage

import java.io.File

/**
 * 备份领域的稳定命名投影。
 *
 * 该对象不拥有目录常量或创建逻辑；全部计算继续由 [KiyoriPaths] 唯一完成。
 */
object KiyoriBackupPaths {

    fun kiyoriRootDir(): File = KiyoriPaths.kiyoriRootDir()

    fun backupRootDir(): File = KiyoriPaths.backupRootDir()

    fun rawSnapshotDir(): File = KiyoriPaths.rawSnapshotDir()

    fun roomDbDir(): File = KiyoriPaths.roomDbDir()

    fun chatDir(): File = KiyoriPaths.chatDir()

    fun memoryDir(): File = KiyoriPaths.memoryDir()

    fun modelConfigDir(): File = KiyoriPaths.modelConfigDir()

    fun characterCardsDir(): File = KiyoriPaths.characterCardsDir()
}
