package com.ai.assistance.operit.ui.features.toolbox.screens.htmlpackager

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Html
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.chat.components.AndroidExportDialog
import com.ai.assistance.operit.ui.features.chat.components.ExportCompleteDialog
import com.ai.assistance.operit.ui.features.chat.components.ExportPlatformDialog
import com.ai.assistance.operit.ui.features.chat.components.ExportProgressDialog
import com.ai.assistance.operit.ui.features.chat.components.WindowsExportDialog
import com.ai.assistance.operit.ui.features.chat.components.exportAndroidApp
import com.ai.assistance.operit.ui.features.chat.components.exportWindowsApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlPackagerScreen(onGoBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val toolHandler = AIToolHandler.getInstance(context)

    var webProjectUri by remember { mutableStateOf<Uri?>(null) }
    var htmlFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var selectedIndexFile by remember { mutableStateOf<DocumentFile?>(null) }
    var isIndexFileDropdownExpanded by remember { mutableStateOf(false) }

    var showExportPlatformDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showWindowsExportDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var showCompleteDialog by remember { mutableStateOf(false) }

    var exportProgress by remember { mutableStateOf(0f) }
    var exportStatus by remember { mutableStateOf("") }
    var exportResult by remember { mutableStateOf<Result<String>?>(null) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { exportJob?.cancel() } }

    fun startExport(export: suspend (File) -> Unit) {
        if (exportJob != null) return
        val sourceUri = webProjectUri ?: return
        val entryName = selectedIndexFile?.name ?: return
        showProgressDialog = true
        exportProgress = 0f
        exportStatus = context.getString(R.string.export_copy_web_content)
        exportResult = null
        exportJob = coroutineScope.launch {
            val owner = currentCoroutineContext()[Job]
            var temporary: File? = null
            try {
                val directory = withContext(Dispatchers.IO) {
                    val target = File(context.cacheDir, "html_packager_${java.util.UUID.randomUUID()}")
                    temporary = target
                    check(target.mkdirs()) { "Unable to create HTML export directory" }
                    val sourceDocumentUri = DocumentsContract.buildDocumentUriUsingTree(sourceUri, DocumentsContract.getTreeDocumentId(sourceUri))
                    copyDocumentTreeTo(context, sourceDocumentUri, target)
                    val original = File(target, entryName)
                    check(com.ai.assistance.operit.ui.features.chat.webview.workspace.isWorkspaceEntryNameValid(entryName) && original.isFile) { "Selected entry file is unavailable" }
                    val index = File(target, "index.html")
                    if (original.name != "index.html") {
                        check(!index.exists() || index.delete()) { "Unable to replace the temporary index file" }
                        check(original.renameTo(index)) { "Unable to prepare the selected entry file" }
                    }
                    target
                }
                export(directory)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                exportResult = Result.failure(error)
                showProgressDialog = false
                showCompleteDialog = true
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    temporary?.let { if (it.exists() && !it.deleteRecursively()) com.ai.assistance.operit.util.AppLogger.w("HtmlPackager", "Unable to remove temporary export directory") }
                }
                if (exportJob === owner) exportJob = null
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            webProjectUri = uri
            val docFile = DocumentFile.fromTreeUri(context, uri)
            if (docFile != null && docFile.isDirectory) {
                htmlFiles = docFile.listFiles().filter { it.isFile && it.name?.endsWith(".html", true) == true }
                selectedIndexFile = htmlFiles.find { it.name.equals("index.html", ignoreCase = true) } ?: htmlFiles.firstOrNull()
            }
        }
    }

    CustomScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp),
        ) {

            // Step 1: Select Folder
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = "Step 1", modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(R.string.htmlpackager_step1_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { folderPickerLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.htmlpackager_select_folder))
                    }
                    if (webProjectUri != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.htmlpackager_selected, DocumentFile.fromTreeUri(context, webProjectUri!!)?.name ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 2: Select Index File
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Html, contentDescription = "Step 2", modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(R.string.htmlpackager_step2_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    ExposedDropdownMenuBox(
                        expanded = isIndexFileDropdownExpanded,
                        onExpandedChange = { isIndexFileDropdownExpanded = !isIndexFileDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedIndexFile?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.htmlpackager_main_html)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isIndexFileDropdownExpanded) },
                            modifier =
                                Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                            enabled = webProjectUri != null && htmlFiles.isNotEmpty()
                        )
                        ExposedDropdownMenu(
                            expanded = isIndexFileDropdownExpanded,
                            onDismissRequest = { isIndexFileDropdownExpanded = false }
                        ) {
                            htmlFiles.forEach { file ->
                                DropdownMenuItem(
                                    text = { Text(file.name ?: "") },
                                    onClick = {
                                        selectedIndexFile = file
                                        isIndexFileDropdownExpanded = false
                                    },
                                    trailingIcon = if (selectedIndexFile?.uri == file.uri) { { Icon(Icons.Default.Check, "Selected") } } else null
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Step 3: Package
            Button(
                onClick = { showExportPlatformDialog = true },
                enabled = selectedIndexFile != null && exportJob == null,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.Build, contentDescription = "Package", modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.htmlpackager_generate_package), style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (showExportPlatformDialog) {
        ExportPlatformDialog(
            onDismiss = { showExportPlatformDialog = false },
            onSelectAndroid = {
                showExportPlatformDialog = false
                showExportDialog = true
            },
            onSelectWindows = {
                showExportPlatformDialog = false
                showWindowsExportDialog = true
            }
        )
    }

    val workDirForDialog = remember(webProjectUri) {
        context.cacheDir.resolve("html_export_${java.util.UUID.nameUUIDFromBytes(webProjectUri.toString().toByteArray())}")
    }
    if (showExportDialog && webProjectUri != null && selectedIndexFile != null) {
        AndroidExportDialog(
            workDir = workDirForDialog,
            onDismiss = { showExportDialog = false },
            onExport = { packageName, appName, iconUri, versionName, versionCode ->
                showExportDialog = false
                startExport { directory ->
                    exportAndroidApp(context, packageName, appName, versionName, versionCode, iconUri, directory,
                        onProgress = { progress, status -> exportProgress = progress; exportStatus = status },
                        onComplete = { success, path, error ->
                            exportResult = if (success && path != null) Result.success(path) else Result.failure(IOException(error))
                            showProgressDialog = false
                            showCompleteDialog = true
                        })
                }
            }
        )
    }
    if (showWindowsExportDialog && webProjectUri != null && selectedIndexFile != null) {
        WindowsExportDialog(
            workDir = workDirForDialog,
            onDismiss = { showWindowsExportDialog = false },
            onExport = { appName, iconUri ->
                showWindowsExportDialog = false
                startExport { directory ->
                    exportWindowsApp(context, appName, iconUri, directory,
                        onProgress = { progress, status -> exportProgress = progress; exportStatus = status },
                        onComplete = { success, path, error ->
                            exportResult = if (success && path != null) Result.success(path) else Result.failure(IOException(error))
                            showProgressDialog = false
                            showCompleteDialog = true
                        })
                }
            }
        )
    }

    if (showProgressDialog) {
        ExportProgressDialog(
            progress = exportProgress,
            status = exportStatus,
            onCancel = { exportJob?.cancel(); showProgressDialog = false }
        )
    }

    if (showCompleteDialog) {
        val result = exportResult
        ExportCompleteDialog(
            success = result?.isSuccess ?: false,
            filePath = result?.getOrNull(),
            errorMessage = result?.exceptionOrNull()?.message,
            onDismiss = { showCompleteDialog = false },
            onOpenFile = { filePath ->
                val openFileTool = AITool(
                    name = "open_file",
                    parameters = listOf(ToolParameter("path", filePath))
                )
                toolHandler.executeTool(openFileTool)
            }
        )
    }
}

/**
 * Recursively copies a directory from a Storage Access Framework (SAF) Uri to a local File directory.
 */
private suspend fun copyDocumentTreeTo(context: Context, sourceUri: Uri, destDir: File, ancestors: Set<String> = emptySet()) {
    currentCoroutineContext().ensureActive()
    val id = DocumentsContract.getDocumentId(sourceUri)
    check(id !in ancestors) { "The document provider returned a directory cycle" }
    check(destDir.isDirectory || destDir.mkdirs()) { "Unable to create export directory" }
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(sourceUri, id)
    val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
    val cursor = context.contentResolver.query(childrenUri, columns, null, null, null)
        ?: throw IOException("Unable to read the selected document directory")
    cursor.use {
        while (it.moveToNext()) {
            currentCoroutineContext().ensureActive()
            val childId = it.getString(0) ?: throw IOException("Document ID is missing")
            val name = it.getString(1) ?: throw IOException("Document name is missing")
            check(com.ai.assistance.operit.ui.features.chat.webview.workspace.isWorkspaceEntryNameValid(name)) { "Invalid document name" }
            val childUri = DocumentsContract.buildDocumentUriUsingTree(sourceUri, childId)
            val target = File(destDir, name)
            check(!target.exists()) { "The document provider returned duplicate file names" }
            if (it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                copyDocumentTreeTo(context, childUri, target, ancestors + id)
            } else {
                val input = context.contentResolver.openInputStream(childUri)
                    ?: throw IOException("Unable to read a selected document")
                input.use { stream ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
        }
    }
}
