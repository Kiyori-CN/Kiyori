package com.ai.assistance.operit.ui.features.chat.webview.workspace

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.BinaryFileContentData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.DocumentConversionUtil
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

private const val DOCUMENT_PREVIEW_TAG = "WorkspaceDocPreview"

internal data class WorkspacePreviewFileState(
    val loading: Boolean = true,
    val file: File? = null,
    val errorMessage: String? = null
)

private data class WorkspaceHtmlPreviewState(
    val loading: Boolean = true,
    val html: String? = null,
    val errorMessage: String? = null
)

private data class WorkspacePdfPageRenderState(val loading: Boolean = true, val bitmap: Bitmap? = null)

private data class WorkspacePdfPreviewState(
    val loading: Boolean = true,
    val pageCount: Int = 0,
    val errorMessage: String? = null
)

@Composable
internal fun WorkspaceReadOnlyDocumentPreview(
    fileInfo: OpenFileInfo,
    workspaceEnv: String?,
    toolHandler: AIToolHandler,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewFileState by rememberWorkspacePreviewFileState(
        fileInfo = fileInfo,
        workspaceEnv = workspaceEnv,
        toolHandler = toolHandler
    )

    when {
        previewFileState.loading -> {
            PreviewLoadingBox(modifier = modifier)
        }

        previewFileState.file == null -> {
            PreviewErrorBox(
                message = previewFileState.errorMessage
                    ?: context.getString(R.string.cannot_open_file, fileInfo.name),
                modifier = modifier
            )
        }

        fileInfo.isPdf -> {
            WorkspacePdfPreview(
                sourceFile = previewFileState.file,
                modifier = modifier
            )
        }

        fileInfo.isWordDocument || fileInfo.isSpreadsheetDocument -> {
            WorkspaceHtmlDocumentPreview(
                fileInfo = fileInfo,
                sourceFile = previewFileState.file,
                modifier = modifier
            )
        }

        else -> {
            PreviewErrorBox(
                message = context.getString(R.string.cannot_open_file, fileInfo.name),
                modifier = modifier
            )
        }
    }
}
@Composable
internal fun rememberWorkspacePreviewFileState(
    fileInfo: OpenFileInfo,
    workspaceEnv: String?,
    toolHandler: AIToolHandler
): State<WorkspacePreviewFileState> {
    val context = LocalContext.current
    return key(fileInfo.path, fileInfo.lastModified, workspaceEnv) {
        produceState(initialValue = WorkspacePreviewFileState()) {
            var temporaryFile: File? = null
            try {
                value = withContext(Dispatchers.IO) {
                    resolvePreviewSourceFile(context, toolHandler, fileInfo, workspaceEnv) { temporaryFile = it }
                }
                // 临时文件属于本次可见预览；切文件或环境时释放，不能按路径 hash 共用。
                awaitDispose { }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    temporaryFile?.let { if (it.exists() && !it.delete()) AppLogger.w(DOCUMENT_PREVIEW_TAG, "Unable to remove preview file") }
                }
            }
        }
    }
}

internal fun workspacePreviewUriFromFile(file: File?): Uri? = file?.let { Uri.fromFile(it) }

@Composable
private fun WorkspaceHtmlDocumentPreview(
    fileInfo: OpenFileInfo,
    sourceFile: File?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val htmlPreviewState by produceState(
        initialValue = WorkspaceHtmlPreviewState(),
        key1 = sourceFile?.absolutePath,
        key2 = sourceFile?.lastModified(),
        key3 = fileInfo.resolvedMimeType
    ) {
        value = WorkspaceHtmlPreviewState()
        value = withContext(Dispatchers.IO) {
            buildHtmlPreviewState(context, sourceFile, fileInfo)
        }
    }

    when {
        htmlPreviewState.loading -> {
            PreviewLoadingBox(modifier = modifier)
        }

        htmlPreviewState.html == null -> {
            PreviewErrorBox(
                message = htmlPreviewState.errorMessage
                    ?: context.getString(R.string.cannot_open_file, fileInfo.name),
                modifier = modifier
            )
        }

        else -> {
            ReadOnlyHtmlWebView(
                html = htmlPreviewState.html,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun WorkspacePdfPreview(
    sourceFile: File?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pdfPreviewState by produceState(
        initialValue = WorkspacePdfPreviewState(),
        key1 = sourceFile?.absolutePath,
        key2 = sourceFile?.lastModified()
    ) {
        value = WorkspacePdfPreviewState()
        value = withContext(Dispatchers.IO) {
            resolvePdfPreviewState(sourceFile)
        }
    }

    when {
        pdfPreviewState.loading -> {
            PreviewLoadingBox(modifier = modifier)
        }

        pdfPreviewState.pageCount <= 0 -> {
            PreviewErrorBox(
                message = pdfPreviewState.errorMessage
                    ?: context.getString(R.string.cannot_open_file, sourceFile?.name ?: ""),
                modifier = modifier
            )
        }

        else -> {
            val pageIndexes = List(pdfPreviewState.pageCount) { it }
            LazyColumn(
                modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(pageIndexes) { pageIndex ->
                    WorkspacePdfPage(
                        sourceFile = sourceFile,
                        pageIndex = pageIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspacePdfPage(
    sourceFile: File?,
    pageIndex: Int
) {
    val context = LocalContext.current
    val pageState by produceState(
        initialValue = WorkspacePdfPageRenderState(),
        key1 = sourceFile?.absolutePath,
        key2 = sourceFile?.lastModified(),
        key3 = pageIndex
    ) {
        value = WorkspacePdfPageRenderState()
        value = withContext(Dispatchers.IO) {
            WorkspacePdfPageRenderState(loading = false, bitmap = renderPdfPage(sourceFile, pageIndex))
        }
    }
    Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 2.dp) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val bitmap = pageState.bitmap
            when {
                pageState.loading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                bitmap != null -> Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = context.getString(R.string.workspace_pdf_page, pageIndex + 1),
                    modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth
                )
                else -> Text(
                    text = context.getString(R.string.workspace_pdf_page_failed, pageIndex + 1),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ReadOnlyHtmlWebView(
    html: String?,
    modifier: Modifier = Modifier
) {
    var loadedHtml by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    AndroidView(
        factory = { androidContext ->
            ParentInterceptingWebView(androidContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
            }
        },
        update = { webView ->
            if (loadedHtml != html) {
                loadedHtml = html
                webView.loadDataWithBaseURL(
                "https://workspace-preview.local/",
                html.orEmpty(),
                "text/html",
                "UTF-8",
                null
                )
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.removeAllViews()
            webView.destroy()
        },
        modifier = modifier
    )
}

@Composable
private fun PreviewLoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun PreviewErrorBox(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

private fun resolvePreviewSourceFile(
    context: android.content.Context,
    toolHandler: AIToolHandler,
    fileInfo: OpenFileInfo,
    workspaceEnv: String?,
    onTemporaryFile: (File) -> Unit
): WorkspacePreviewFileState {
    return try {
        if (workspaceEnv.isNullOrBlank()) {
            val localFile = File(fileInfo.path)
            return if (localFile.exists()) {
                WorkspacePreviewFileState(loading = false, file = localFile)
            } else {
                WorkspacePreviewFileState(
                    loading = false,
                    errorMessage = context.getString(R.string.cannot_open_file, fileInfo.name)
                )
            }
        }

        val result = toolHandler.executeTool(
            AITool(
                "read_file_binary",
                listOf(
                    ToolParameter("path", fileInfo.path),
                    ToolParameter("environment", workspaceEnv)
                )
            )
        )

        if (!result.success || result.result !is BinaryFileContentData) {
            return WorkspacePreviewFileState(
                loading = false,
                errorMessage = result.error ?: context.getString(R.string.cannot_open_file, fileInfo.name)
            )
        }

        val binaryData = result.result
        val bytes = Base64.decode(binaryData.contentBase64, Base64.DEFAULT)
        val previewDir = File(context.cacheDir, "workspace_document_preview").apply { mkdirs() }
        val extension = fileInfo.name.substringAfterLast('.', "bin").takeIf { it.matches(Regex("[A-Za-z0-9]{1,12}")) } ?: "bin"
        val previewFile = File.createTempFile("preview-", ".$extension", previewDir)
        onTemporaryFile(previewFile)
        FileOutputStream(previewFile).use { output ->
            output.write(bytes)
        }
        WorkspacePreviewFileState(loading = false, file = previewFile)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        AppLogger.e(DOCUMENT_PREVIEW_TAG, "Failed to resolve preview source file", e)
        WorkspacePreviewFileState(
            loading = false,
            errorMessage = context.getString(R.string.cannot_open_file, fileInfo.name)
        )
    }
}

private fun buildHtmlPreviewState(
    context: android.content.Context,
    sourceFile: File?,
    fileInfo: OpenFileInfo
): WorkspaceHtmlPreviewState {
    if (sourceFile == null || !sourceFile.exists()) {
        return WorkspaceHtmlPreviewState(
            loading = false,
            errorMessage = context.getString(R.string.cannot_open_file, fileInfo.name)
        )
    }

    var temporaryHtml: File? = null
    return try {
        val htmlDir = File(context.cacheDir, "workspace_document_html").apply { mkdirs() }
        val htmlFile = File.createTempFile("preview-", ".html", htmlDir)
        temporaryHtml = htmlFile

        val extension = sourceFile.extension.lowercase(Locale.ROOT)
        val success = when {
            fileInfo.isWordDocument -> {
                DocumentConversionUtil.convertToHtml(context, sourceFile, htmlFile, extension)
            }

            fileInfo.isSpreadsheetDocument -> {
                DocumentConversionUtil.convertSpreadsheetToHtml(sourceFile, htmlFile)
            }

            else -> false
        }

        if (!success || !htmlFile.exists()) {
            return WorkspaceHtmlPreviewState(
                loading = false,
                errorMessage = context.getString(R.string.cannot_open_file, fileInfo.name)
            )
        }

        WorkspaceHtmlPreviewState(
            loading = false,
            html = htmlFile.readText()
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        AppLogger.e(DOCUMENT_PREVIEW_TAG, "Failed to build HTML document preview", e)
        WorkspaceHtmlPreviewState(
            loading = false,
            errorMessage = context.getString(R.string.cannot_open_file, fileInfo.name)
        )
    } finally {
        temporaryHtml?.let { if (it.exists() && !it.delete()) AppLogger.w(DOCUMENT_PREVIEW_TAG, "Unable to remove preview HTML") }
    }
}

private fun resolvePdfPreviewState(sourceFile: File?): WorkspacePdfPreviewState {
    if (sourceFile == null || !sourceFile.exists()) {
        return WorkspacePdfPreviewState(
            loading = false,
            errorMessage = "PDF file unavailable"
        )
    }

    return try {
        ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                WorkspacePdfPreviewState(
                    loading = false,
                    pageCount = renderer.pageCount,
                    errorMessage = if (renderer.pageCount <= 0) "Empty PDF document" else null
                )
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        AppLogger.e(DOCUMENT_PREVIEW_TAG, "Failed to inspect PDF preview", e)
        WorkspacePdfPreviewState(
            loading = false,
            errorMessage = "Unable to render PDF"
        )
    }
}

private fun renderPdfPage(sourceFile: File?, pageIndex: Int): Bitmap? {
    if (sourceFile == null || !sourceFile.exists()) return null

    return try {
        ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) return null

                renderer.openPage(pageIndex).use { page ->
                    require(page.width > 0 && page.height > 0) { "Invalid PDF page size" }
                    // 单页最长边不超过 2048px，避免大幅面 PDF 按原尺寸翻倍分配位图。
                    val scale = minOf(2.0, 2048.0 / maxOf(page.width, page.height))
                    val bitmap =
                        Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        AppLogger.e(DOCUMENT_PREVIEW_TAG, "Failed to render PDF page $pageIndex", e)
        null
    }
}
