package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val DOWNLOAD_INSTALL_TAG = "BrowserDownloadInstall"
private const val BROWSER_DOWNLOAD_APK_INSTALL_DIRECTORY = "browser-download-apk-installs"

internal data class BrowserDownloadPreparedApkInstall(
    val packageName: String,
    val versionCode: Long,
    val previouslyInstalledVersionCode: Long?,
    val installUri: Uri,
)

internal fun matchesBrowserDownloadInstalledPackage(
    pendingPackageName: String?,
    pendingVersionCode: Long?,
    installedPackageName: String,
    installedVersionCode: Long,
): Boolean =
    !pendingPackageName.isNullOrBlank() &&
        pendingVersionCode != null &&
        pendingPackageName == installedPackageName &&
        pendingVersionCode == installedVersionCode

internal fun shouldReconcileBrowserDownloadInstalledPackage(
    pendingPackageName: String?,
    pendingVersionCode: Long?,
    previouslyInstalledVersionCode: Long?,
    installedPackageName: String,
    installedVersionCode: Long,
): Boolean =
    matchesBrowserDownloadInstalledPackage(
        pendingPackageName = pendingPackageName,
        pendingVersionCode = pendingVersionCode,
        installedPackageName = installedPackageName,
        installedVersionCode = installedVersionCode,
    ) && previouslyInstalledVersionCode != installedVersionCode

internal fun prepareBrowserDownloadApkInstall(
    context: Context,
    task: BrowserDownloadTaskRecord,
): BrowserDownloadPreparedApkInstall {
    require(task.status == BrowserDownloadStatus.COMPLETED) {
        "Only completed browser downloads can be installed"
    }
    val directArchiveFile =
        task.destinationPath
            .takeIf(String::isNotBlank)
            ?.let(::File)
            ?.takeIf(File::isFile)
    val archiveFile = directArchiveFile ?: stageBrowserDownloadApk(context, task)
    try {
        val packageInfo =
            requireNotNull(readBrowserDownloadArchivePackageInfo(context.packageManager, archiveFile)) {
                "无法读取安装包信息"
            }
        val packageName = packageInfo.packageName.takeIf(String::isNotBlank)
            ?: throw IOException("安装包缺少包名")
        val versionCode = packageInfo.browserDownloadVersionCode()
        return BrowserDownloadPreparedApkInstall(
            packageName = packageName,
            versionCode = versionCode,
            previouslyInstalledVersionCode =
                readInstalledBrowserDownloadPackageVersion(context, packageName),
            installUri =
                directArchiveFile?.let { file ->
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                } ?: requireNotNull(task.destinationUri?.takeIf(String::isNotBlank)?.toUri()) {
                    "安装包 URI 不可用"
                },
        )
    } finally {
        if (directArchiveFile == null && archiveFile.exists() && !archiveFile.delete()) {
            AppLogger.w(
                DOWNLOAD_INSTALL_TAG,
                "Failed to delete browser download APK metadata staging file",
            )
        }
    }
}

internal fun readInstalledBrowserDownloadPackageVersion(
    context: Context,
    packageName: String,
): Long? =
    try {
        val packageInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0L),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
        packageInfo.browserDownloadVersionCode()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

internal fun deleteBrowserDownloadApkMetadataStaging(context: Context) {
    val directory = File(context.cacheDir, BROWSER_DOWNLOAD_APK_INSTALL_DIRECTORY)
    if (!directory.isDirectory) {
        return
    }
    directory.listFiles()?.forEach { file ->
        if (file.isFile && !file.delete()) {
            AppLogger.w(
                DOWNLOAD_INSTALL_TAG,
                "Failed to delete browser download APK metadata staging file",
            )
        }
    }
}

private fun stageBrowserDownloadApk(
    context: Context,
    task: BrowserDownloadTaskRecord,
): File {
    val sourceUri =
        requireNotNull(task.destinationUri?.takeIf(String::isNotBlank)?.toUri()) {
            "安装包文件不存在"
        }
    val stagingFile = browserDownloadApkInstallStagingFile(context, task.id)
    val temporaryFile = File(stagingFile.parentFile, "${stagingFile.name}.tmp")
    require(temporaryFile.delete() || !temporaryFile.exists()) {
        "无法重建安装包暂存文件"
    }
    try {
        val input =
            requireNotNull(context.contentResolver.openInputStream(sourceUri)) {
                "无法读取安装包"
            }
        input.use { source ->
            FileOutputStream(temporaryFile).use { output ->
                source.copyTo(output)
                output.flush()
                output.fd.sync()
            }
        }
        require(temporaryFile.length() > 0L) { "安装包内容为空" }
        require(stagingFile.delete() || !stagingFile.exists()) {
            "无法替换安装包暂存文件"
        }
        require(temporaryFile.renameTo(stagingFile)) {
            "无法提交安装包暂存文件"
        }
        return stagingFile
    } catch (error: Throwable) {
        temporaryFile.delete()
        throw error
    }
}

private fun browserDownloadApkInstallStagingFile(context: Context, taskId: String): File {
    val directory = File(context.cacheDir, BROWSER_DOWNLOAD_APK_INSTALL_DIRECTORY)
    require(directory.isDirectory || directory.mkdirs()) {
        "无法创建安装包暂存目录"
    }
    val digest =
        MessageDigest.getInstance("SHA-256")
            .digest(taskId.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
    return File(directory, "$digest.apk")
}

private fun readBrowserDownloadArchivePackageInfo(
    packageManager: PackageManager,
    archiveFile: File,
): PackageInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageArchiveInfo(
            archiveFile.absolutePath,
            PackageManager.PackageInfoFlags.of(0L),
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageArchiveInfo(archiveFile.absolutePath, 0)
    }

private fun PackageInfo.browserDownloadVersionCode(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }

class BrowserDownloadInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_PACKAGE_ADDED &&
                intent.action != Intent.ACTION_PACKAGE_REPLACED
        ) {
            return
        }
        if (intent.getBooleanExtra("android.intent.extra.ARCHIVAL", false)) {
            return
        }
        val packageName = intent.data?.schemeSpecificPart?.takeIf(String::isNotBlank) ?: return
        val installedVersionCode =
            readInstalledBrowserDownloadPackageVersion(context, packageName) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                BrowserDownloadManager.getInstance(context)
                    .handleInstalledPackage(packageName, installedVersionCode)
            } catch (error: Throwable) {
                AppLogger.e(
                    DOWNLOAD_INSTALL_TAG,
                    "Failed to reconcile installed browser download package",
                    error,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
