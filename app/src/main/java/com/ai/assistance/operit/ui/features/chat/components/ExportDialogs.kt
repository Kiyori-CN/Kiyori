package com.ai.assistance.operit.ui.features.chat.components

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.OperitPaths
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.core.subpack.ApkEditor
import com.ai.assistance.operit.core.subpack.ExeEditor
import com.ai.assistance.operit.core.subpack.KeyStoreHelper
import com.ai.assistance.operit.ui.common.rememberLocal
import com.ai.assistance.operit.util.UriSerializer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 导出选择对话框，用于选择导出平台 */
@Composable
fun ExportPlatformDialog(
        onDismiss: () -> Unit,
        onSelectAndroid: () -> Unit,
        onSelectWindows: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
                modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                        context.getString(R.string.select_export_platform),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PlatformButton(
                            icon = Icons.Default.Android,
                            text = "Android",
                            onClick = {
                                onSelectAndroid()
                                onDismiss()
                            }
                    )

                    PlatformButton(
                            icon = Icons.Default.DesktopWindows,
                            text = "Windows",
                            onClick = {
                                onSelectWindows()
                                onDismiss()
                            }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text(context.getString(R.string.cancel)) }
            }
        }
    }
}

@Composable
private fun PlatformButton(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        text: String,
        onClick: () -> Unit
) {
    Column(
            modifier = Modifier.width(100.dp).clickable(onClick = onClick).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
                modifier = Modifier.size(60.dp).align(Alignment.CenterHorizontally),
                shape = RoundedCornerShape(8.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                        imageVector = icon,
                        contentDescription = text,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
        )
    }
}

/** Android导出配置对话框 */
@Composable
fun AndroidExportDialog(
        workDir: File,
        onDismiss: () -> Unit,
        onExport: (
                packageName: String,
                appName: String,
                iconUri: Uri?,
                versionName: String,
                versionCode: String
        ) -> Unit
) {
    var packageName by rememberLocal(key = "export_package_name_${workDir.absolutePath}", "com.example.webproject")
    var appName by rememberLocal(key = "export_app_name_${workDir.absolutePath}", "Web Project")
    var versionName by rememberLocal(key = "export_version_name_${workDir.absolutePath}", "1.0.0")
    var versionCode by rememberLocal(key = "export_version_code_${workDir.absolutePath}", "1")
    var iconUri by rememberLocal<Uri?>(key = "export_icon_uri_${workDir.absolutePath}", null, serializer = UriSerializer)

    val isPackageNameError = packageName.isNotEmpty() && !validExportPackageName(packageName)
    val isVersionNameError = versionName.isNotEmpty() && !validExportVersionName(versionName)
    val isVersionCodeError = versionCode.isNotEmpty() && !validExportVersionCode(versionCode)

    val context = LocalContext.current
    val dialogMetrics = rememberCompactDialogMetrics()
    val compactScrollState = rememberCompactDialogScrollState()
    val cardModifier =
            Modifier
                    .fillMaxWidth(0.95f)
                    .compactDialogHeightOrWrapContent(dialogMetrics)
    val contentModifier =
            Modifier
                    .padding(20.dp)
                    .verticalScrollWhenShort(dialogMetrics, compactScrollState)
    val imageCropLauncher =
            rememberLauncherForActivityResult(contract = CropImageContract()) { result ->
                if (result.isSuccessful) {
                    result.uriContent?.let { croppedUri ->
                        iconUri = croppedUri
                    }
                } else {
                    val cropError = result.error
                    if (cropError != null) {
                        AppLogger.e("ExportDialogs", "Android export icon crop failed", cropError)
                    }
                }
            }
    val imagePicker =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
                uri?.let { sourceUri ->
                    imageCropLauncher.launch(createExportIconCropOptions(context, sourceUri))
                }
            }

    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
                modifier = cardModifier,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                    modifier = contentModifier,
                    horizontalAlignment = Alignment.Start
            ) {
                Text(
                        context.getString(R.string.configure_android_app),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                        value = packageName,
                        onValueChange = { 
                            // 只允许小写字母、数字和点号
                            packageName = it
                        },
                        label = { Text(context.getString(R.string.package_name_label)) },
                        placeholder = { Text("com.example.webproject") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = isPackageNameError,
                        supportingText = { 
                            if (isPackageNameError) {
                                Text(context.getString(R.string.export_invalid_package_name_format))
                            }
                        }
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                        value = appName,
                        onValueChange = { appName = it },
                        label = { Text(context.getString(R.string.app_name_label)) },
                        placeholder = { Text("Web Project") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 图标选择区域
                Row(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            context.getString(R.string.app_icon),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                    )

                    // 图标显示/选择区域
                    Box(
                            modifier =
                                    Modifier.size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outline,
                                                    RoundedCornerShape(8.dp)
                                            )
                                            .clickable { imagePicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                    ) {
                        ExportIconPreview(iconUri)
                    }

                    // 提示文字
                    Text(
                            context.getString(R.string.click_select_icon),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                        value = versionName,
                        onValueChange = { 
                            versionName = it
                        },
                        label = { Text(context.getString(R.string.version_name)) },
                        placeholder = { Text("1.0.0") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = isVersionNameError,
                        supportingText = {
                            if (isVersionNameError) {
                                Text(context.getString(R.string.export_invalid_version_format))
                            }
                        }
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                        value = versionCode,
                        onValueChange = { versionCode = it },
                        label = { Text(context.getString(R.string.version_code)) },
                        placeholder = { Text("1") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        isError = isVersionCodeError,
                        supportingText = { if (isVersionCodeError) Text(context.getString(R.string.export_invalid_version_code)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 按钮区域
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel)) }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                            onClick = { onExport(packageName, appName, iconUri, versionName, versionCode) },
                            enabled = validExportPackageName(packageName) && appName.isNotBlank() && validExportVersionName(versionName) && validExportVersionCode(versionCode)
                    ) { Text(context.getString(R.string.export)) }
                }
            }
        }
    }
}

/** Windows导出配置对话框 */
@Composable
fun WindowsExportDialog(
        workDir: File,
        onDismiss: () -> Unit,
        onExport: (appName: String, iconUri: Uri?) -> Unit
) {
    var appName by remember { mutableStateOf("Web Project") }
    var iconUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    val dialogMetrics = rememberCompactDialogMetrics()
    val compactScrollState = rememberCompactDialogScrollState()
    val cardModifier =
            Modifier
                    .fillMaxWidth(0.95f)
                    .compactDialogHeightOrWrapContent(dialogMetrics)
    val contentModifier =
            Modifier
                    .padding(20.dp)
                    .verticalScrollWhenShort(dialogMetrics, compactScrollState)
    val imageCropLauncher =
            rememberLauncherForActivityResult(contract = CropImageContract()) { result ->
                if (result.isSuccessful) {
                    result.uriContent?.let { croppedUri ->
                        iconUri = croppedUri
                    }
                } else {
                    val cropError = result.error
                    if (cropError != null) {
                        AppLogger.e("ExportDialogs", "Windows export icon crop failed", cropError)
                    }
                }
            }
    val imagePicker =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri
                ->
                uri?.let { sourceUri ->
                    imageCropLauncher.launch(createExportIconCropOptions(context, sourceUri))
                }
            }

    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
                modifier = cardModifier,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                    modifier = contentModifier,
                    horizontalAlignment = Alignment.Start
            ) {
                Text(
                        context.getString(R.string.configure_windows_app),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                        value = appName,
                        onValueChange = { appName = it },
                        label = { Text(context.getString(R.string.app_name)) },
                        placeholder = { Text("Web Project") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 图标选择区域
                Row(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            context.getString(R.string.app_icon),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                    )

                    // 图标显示/选择区域
                    Box(
                            modifier =
                                    Modifier.size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outline,
                                                    RoundedCornerShape(8.dp)
                                            )
                                            .clickable { imagePicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                    ) {
                        ExportIconPreview(iconUri)
                    }

                    // 提示文字
                    Text(
                            context.getString(R.string.click_select_icon),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 按钮区域
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel)) }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                            onClick = { onExport(appName, iconUri) },
                            enabled = appName.isNotBlank()
                    ) { Text(context.getString(R.string.export)) }
                }
            }
        }
    }
}

@Composable
private fun ExportIconPreview(uri: Uri?) {
    val context = LocalContext.current
    var loading by remember(uri) { mutableStateOf(uri != null) }
    var failed by remember(uri) { mutableStateOf(false) }
    if (uri == null) {
        Icon(Icons.Default.Image, context.getString(R.string.app_icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        coil.compose.AsyncImage(
            model = uri,
            contentDescription = context.getString(R.string.app_icon),
            modifier = Modifier.size(70.dp).clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Fit,
            onLoading = { loading = true; failed = false },
            onSuccess = { loading = false },
            onError = { loading = false; failed = true },
        )
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        if (failed) Icon(Icons.Default.Warning, context.getString(R.string.export_icon_load_failed), tint = MaterialTheme.colorScheme.error)
    }
}

private fun createExportIconCropOptions(context: Context, sourceUri: Uri): CropImageContractOptions {
    val isNightMode =
            context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

    var primaryColor: Int
    var onPrimaryColor: Int
    var surfaceColor: Int
    var statusBarColor: Int

    try {
        val typedValue = android.util.TypedValue()

        context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
        primaryColor = typedValue.data

        context.theme.resolveAttribute(android.R.attr.colorPrimaryDark, typedValue, true)
        statusBarColor = typedValue.data

        context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
        surfaceColor = typedValue.data

        context.theme.resolveAttribute(
            com.google.android.material.R.attr.colorOnPrimary,
            typedValue,
            true,
        )
        onPrimaryColor = typedValue.data
    } catch (_: Exception) {
        primaryColor = if (isNightMode) 0xFFF1F3F4.toInt() else 0xFF202124.toInt()
        statusBarColor = primaryColor
        surfaceColor = if (isNightMode) 0xFF121212.toInt() else 0xFFFFFFFF.toInt()
        onPrimaryColor = if (isNightMode) 0xFF202124.toInt() else 0xFFFFFFFF.toInt()
    }

    return CropImageContractOptions(
            sourceUri,
            CropImageOptions().apply {
                guidelines = CropImageView.Guidelines.ON
                outputCompressFormat = android.graphics.Bitmap.CompressFormat.PNG
                outputCompressQuality = 100
                fixAspectRatio = true
                aspectRatioX = 1
                aspectRatioY = 1
                cropMenuCropButtonTitle = context.getString(R.string.theme_crop_done)
                activityTitle = context.getString(R.string.theme_crop_image)
                toolbarColor = primaryColor
                toolbarBackButtonColor = onPrimaryColor
                toolbarTitleColor = onPrimaryColor
                activityBackgroundColor = surfaceColor
                backgroundColor = surfaceColor
                statusBarColor = statusBarColor
                activityMenuIconColor = onPrimaryColor
                showCropOverlay = true
                showProgressBar = true
                multiTouchEnabled = true
                autoZoomEnabled = true
            }
    )
}

/** 导出进度对话框 */
@Composable
fun ExportProgressDialog(progress: Float, status: String, onCancel: () -> Unit) {
    val context = LocalContext.current
    Dialog(
            onDismissRequest = { /* 不允许点击外部关闭 */},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
                modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                        context.getString(R.string.export_in_progress_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onCancel) { Text(context.getString(R.string.cancel)) }
            }
        }
    }
}

/** 导出完成对话框 */
@Composable
fun ExportCompleteDialog(
        success: Boolean,
        filePath: String?,
        errorMessage: String?,
        onDismiss: () -> Unit,
        onOpenFile: (String) -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                context.getString(if (success) R.string.export_success else R.string.export_failed),
                color = if (success) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
            )
        },
        text = {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (success && filePath != null) {
                        Text(context.getString(R.string.file_saved_to))
                        Text(filePath, style = MaterialTheme.typography.bodySmall)
                    } else if (errorMessage != null) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (success && filePath != null) {
                TextButton(onClick = { onOpenFile(filePath) }) { Text(context.getString(R.string.open_file)) }
            } else {
                TextButton(onClick = onDismiss) { Text(context.getString(R.string.close)) }
            }
        },
        dismissButton = {
            if (success && filePath != null) {
                TextButton(onClick = onDismiss) { Text(context.getString(R.string.close)) }
            }
        }
    )
}

/**
 * 处理Android应用导出
 * @param context 应用上下文
 * @param packageName 包名
 * @param appName 应用名称
 * @param iconUri 图标URI
 * @param webContentDir 网页内容目录
 * @param onProgress 进度回调
 * @param onComplete 完成回调
 */
suspend fun exportAndroidApp(
        context: Context,
        packageName: String,
        appName: String,
        versionName: String,
        versionCode: String,
        iconUri: Uri?,
        webContentDir: File,
        onProgress: (Float, String) -> Unit,
        onComplete: (success: Boolean, filePath: String?, errorMessage: String?) -> Unit
) {
    suspend fun reportProgress(value: Float, status: String) = withContext(Dispatchers.Main) { onProgress(value, status) }
    var exportFiles: WorkspaceExportFile? = null
    try {
        require(validExportPackageName(packageName) && appName.isNotBlank() && validExportVersionName(versionName) && validExportVersionCode(versionCode)) { "Invalid Android export settings" }
        val completedFile = withContext(Dispatchers.IO) {
            check(webContentDir.isDirectory) { "Export source directory is unavailable" }
            reportProgress(0.1f, context.getString(R.string.export_prepare_base_apk))
            currentCoroutineContext().ensureActive()

            // 1. 初始化APK编辑器
            val apkEditor = ApkEditor.fromAsset(context, "subpack/android.apk")

            try {
                // 2. 修改包名和应用名
                reportProgress(0.3f, context.getString(R.string.export_modify_app_info))
                apkEditor.changePackageName(packageName)
                apkEditor.changeAppName(appName)
                apkEditor.changeVersionName(versionName)
                apkEditor.changeVersionCode(versionCode)
                currentCoroutineContext().ensureActive()

                // 4. 更改图标（如果提供）
                if (iconUri != null) {
                    reportProgress(0.4f, context.getString(R.string.export_change_app_icon))
                    val inputStream =
                        context.contentResolver.openInputStream(iconUri)
                            ?: throw IOException("Unable to open selected Android app icon")
                    inputStream.use(apkEditor::changeIcon)
                }
                currentCoroutineContext().ensureActive()

                // 6. 准备签名文件
                reportProgress(0.5f, context.getString(R.string.export_prepare_signing))
                val keyStoreFile = KeyStoreHelper.getOrCreateKeystore(context)
                AppLogger.d(
                        "ExportDialogs",
                        "签名使用密钥库: ${keyStoreFile.absolutePath}, 大小: ${keyStoreFile.length()}"
                )

                // 7. 设置签名信息并执行签名
                reportProgress(0.6f, context.getString(R.string.export_signing_apk))
                val outputDir = OperitPaths.exportsDir()
                val files = WorkspaceExportFile(outputDir, "WebApp", "apk")
                exportFiles = files
                val androidOutputFile = files.temporary

                AppLogger.d("ExportDialogs", "即将签名APK，使用密钥: ${keyStoreFile.absolutePath}, 别名: androidkey")
                apkEditor
                        .withSignature(
                                keyStoreFile,
                                "android",
                                "androidkey",
                                "android"
                        )
                        .setOutput(androidOutputFile)

                reportProgress(0.7f, context.getString(R.string.export_pack_web_content))
                val exportContext = currentCoroutineContext()
                apkEditor.repackAndSignWithWebContent(webContentDir) { exportContext.ensureActive() }
                currentCoroutineContext().ensureActive()
                reportProgress(0.9f, context.getString(R.string.export_finish_packaging))
                files.publish()
            } finally {
                try {
                    apkEditor.cleanup()
                } catch (cleanupError: Exception) {
                    AppLogger.e("ExportDialogs", "清理Android导出工作目录失败", cleanupError)
                }
            }
        }
        withContext(Dispatchers.Main) {
            onProgress(1f, context.getString(R.string.export_completed))
            onComplete(true, completedFile.absolutePath, null)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (e: Exception) {
        AppLogger.e("ExportDialogs", "导出失败", e)
        withContext(Dispatchers.Main) { onComplete(false, null, context.getString(R.string.export_failed_with_reason, e.message ?: "")) }
    } finally {
        exportFiles?.temporary?.let { if (it.exists() && !it.delete()) AppLogger.w("ExportDialogs", "Unable to remove incomplete Android export") }
    }
}

/** 处理Windows应用导出 */
suspend fun exportWindowsApp(
        context: Context,
        appName: String,
        iconUri: Uri?,
        webContentDir: File,
        onProgress: (Float, String) -> Unit,
        onComplete: (success: Boolean, filePath: String?, errorMessage: String?) -> Unit
) {
    suspend fun reportProgress(value: Float, status: String) = withContext(Dispatchers.Main) { onProgress(value, status) }
    var exportFiles: WorkspaceExportFile? = null
    try {
        require(appName.isNotBlank()) { "Application name is empty" }
        val completedFile = withContext(Dispatchers.IO) {
            check(webContentDir.isDirectory) { "Export source directory is unavailable" }
            reportProgress(0.1f, context.getString(R.string.export_prepare_windows_template))
            currentCoroutineContext().ensureActive()

            val outputDir = OperitPaths.exportsDir()

            // 创建临时工作目录
            val tempDir =
                File(context.cacheDir, "windows_export_${UUID.randomUUID()}")
            if (!tempDir.mkdirs()) {
                throw IOException("Unable to create Windows export work directory")
            }

            try {
                // 1. 从assets复制windows.zip模板到临时目录
                reportProgress(0.2f, context.getString(R.string.export_copy_template))
                val templateZip = File(tempDir, "windows.zip")
                context.assets.open("subpack/windows.zip").use { input ->
                    FileOutputStream(templateZip).use { output -> input.copyTo(output) }
                }
                currentCoroutineContext().ensureActive()

                // 2. 解压windows.zip
                reportProgress(0.3f, context.getString(R.string.export_extract_template))
                val extractedDir = File(tempDir, "extracted")
                if (!extractedDir.mkdirs()) {
                    throw IOException("Unable to create Windows export extraction directory")
                }

                // 解压ZIP文件
                java.util.zip.ZipFile(templateZip).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        currentCoroutineContext().ensureActive()
                        val entry = entries.nextElement()
                        val entryFile = resolveZipEntryTarget(extractedDir, entry.name)

                        if (entry.isDirectory) {
                            if (!entryFile.exists() && !entryFile.mkdirs()) {
                                throw IOException("Unable to create Windows export template directory")
                            }
                        } else {
                            val parentDir = entryFile.parentFile
                            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                                throw IOException("Unable to create Windows export template directory")
                            }
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(entryFile).use { output -> input.copyTo(output) }
                            }
                        }
                    }
                }

                // 3. 如果提供了图标，修改assistance_subpack.exe的图标
                if (iconUri != null) {
                    reportProgress(0.4f, context.getString(R.string.export_change_app_icon))
                    val mainExe = File(extractedDir, "assistance_subpack.exe")
                    if (!mainExe.isFile) {
                        throw IOException("Windows export template is missing assistance_subpack.exe")
                    }
                    val inputStream =
                        context.contentResolver.openInputStream(iconUri)
                            ?: throw IOException("Unable to open selected Windows app icon")
                    val exeEditor = ExeEditor.fromFile(context, mainExe)
                    inputStream.use { input ->
                        exeEditor.changeIcon(input).setOutput(mainExe).process()
                    }
                    AppLogger.d("ExportDialogs", "已更换Windows应用图标")
                }
                currentCoroutineContext().ensureActive()

                // 4. 复制网页内容到data\flutter_assets\assets\web_content
                reportProgress(0.5f, context.getString(R.string.export_copy_web_content))
                val webContentTarget = File(extractedDir, "data/flutter_assets/assets/web_content")
                if (!webContentTarget.exists()) {
                    webContentTarget.mkdirs()
                }

                AppLogger.d(
                        "ExportDialogs",
                        "复制网页文件到Windows应用: ${webContentDir.absolutePath} -> ${webContentTarget.absolutePath}"
                )
                copyDirectory(webContentDir, webContentTarget)
                currentCoroutineContext().ensureActive()

                // 5. 创建最终输出文件名
                val safeName = appName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val files = WorkspaceExportFile(outputDir, safeName.take(80), "zip")
                exportFiles = files
                val windowsOutputZip = files.temporary

                // 6. 重新打包为ZIP
                reportProgress(0.8f, context.getString(R.string.export_pack_app))

                // 创建ZIP文件
                val buffer = ByteArray(1024)
                java.util.zip.ZipOutputStream(FileOutputStream(windowsOutputZip)).use { zipOut ->
                    // 添加文件到ZIP
                    addDirToZip(extractedDir, extractedDir, zipOut, buffer)
                }
                currentCoroutineContext().ensureActive()

                files.publish()
            } finally {
                // 7. 清理临时文件
                try {
                    tempDir.deleteRecursively()
                } catch (e: Exception) {
                    AppLogger.e("ExportDialogs", "清理临时文件失败", e)
                }
            }
        }
        withContext(Dispatchers.Main) {
            onProgress(1f, context.getString(R.string.export_completed))
            onComplete(true, completedFile.absolutePath, null)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (e: Exception) {
        AppLogger.e("ExportDialogs", "Windows应用导出失败", e)
        withContext(Dispatchers.Main) { onComplete(false, null, context.getString(R.string.export_failed_with_reason, e.message ?: "")) }
    } finally {
        exportFiles?.temporary?.let { if (it.exists() && !it.delete()) AppLogger.w("ExportDialogs", "Unable to remove incomplete Windows export") }
    }
}

/** 递归添加目录到ZIP文件 */
private suspend fun addDirToZip(
        rootDir: File,
        currentDir: File,
        zipOut: java.util.zip.ZipOutputStream,
        buffer: ByteArray
) {
    val files =
        currentDir.listFiles()
            ?: throw IOException("Unable to read export directory: ${currentDir.name}")
    files.forEach { file ->
        currentCoroutineContext().ensureActive()
        val relativePath =
                file.absolutePath.substring(rootDir.absolutePath.length + 1).replace("\\", "/")

        if (file.isDirectory) {
            if (file.listFiles()?.isNotEmpty() == true) {
                addDirToZip(rootDir, file, zipOut, buffer)
            } else {
                val entry = java.util.zip.ZipEntry("$relativePath/")
                zipOut.putNextEntry(entry)
                zipOut.closeEntry()
            }
        } else {
            val entry = java.util.zip.ZipEntry(relativePath)
            zipOut.putNextEntry(entry)

            FileInputStream(file).use { input ->
                var len: Int
                while (input.read(buffer).also { len = it } > 0) {
                    currentCoroutineContext().ensureActive()
                    zipOut.write(buffer, 0, len)
                }
            }

            zipOut.closeEntry()
            AppLogger.d("ExportDialogs", "已添加文件到ZIP: $relativePath")
        }
    }
}

/** 创建或获取应用签名密钥库 */
private fun createOrGetKeystore(context: Context): File {
    // 直接使用KeyStoreHelper
    return KeyStoreHelper.getOrCreateKeystore(context)
}

/** 验证密钥库文件是否有效 */
private fun validateKeystore(file: File, type: String, password: String): Boolean {
    // 直接使用KeyStoreHelper
    return KeyStoreHelper.validateKeystore(file, type, password)
}

/** 复制目录及其内容 */
private suspend fun copyDirectory(sourceDir: File, destDir: File) {
    val operation = currentCoroutineContext()
    val entries = com.ai.assistance.operit.core.subpack.collectWorkspaceExportEntries(sourceDir) { operation.ensureActive() }
    check(destDir.isDirectory || destDir.mkdirs()) { "Unable to create export directory" }
    for (entry in entries) {
        operation.ensureActive()
        val target = File(destDir, entry.relativePath)
        if (entry.isDirectory) {
            check(target.isDirectory || target.mkdirs()) { "Unable to create export directory" }
        } else {
            entry.file.inputStream().use { input ->
                FileOutputStream(target).use { output ->
                    com.ai.assistance.operit.core.subpack.copyWorkspaceExportBytes(input, output) { operation.ensureActive() }
                }
            }
        }
    }
}

internal fun resolveZipEntryTarget(rootDir: File, entryName: String): File {
    val canonicalRoot = rootDir.canonicalFile
    val normalizedEntryName = entryName.replace('\\', '/')
    val canonicalTarget = File(canonicalRoot, normalizedEntryName).canonicalFile
    val rootPath = canonicalRoot.toPath()
    if (!canonicalTarget.toPath().startsWith(rootPath)) {
        throw IOException("Windows export template contains an unsafe path: $entryName")
    }
    return canonicalTarget
}
