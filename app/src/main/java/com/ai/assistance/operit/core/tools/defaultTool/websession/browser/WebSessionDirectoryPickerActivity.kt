package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.ai.assistance.operit.util.AppLogger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal object WebSessionDirectoryPickerCoordinator {
    private const val TAG = "WebSessionDirectoryPicker"
    private const val EXTRA_REQUEST_ID = "web_session_directory_picker_request_id"
    private val pendingRequests = ConcurrentHashMap<String, (String?) -> Unit>()

    fun launch(
        context: Context,
        onResult: (String?) -> Unit,
    ) {
        val requestId = registerRequest(onResult)
        val intent =
            Intent(context.applicationContext, WebSessionDirectoryPickerActivity::class.java).apply {
                putExtra(EXTRA_REQUEST_ID, requestId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        runCatching {
            context.applicationContext.startActivity(intent)
        }.onFailure { error ->
            AppLogger.e(TAG, "Failed to launch the WebSession directory picker", error)
            completeRequest(requestId, null)
        }
    }

    internal fun registerRequest(onResult: (String?) -> Unit): String {
        val requestId = UUID.randomUUID().toString()
        pendingRequests[requestId] = onResult
        return requestId
    }

    internal fun completeRequest(
        requestId: String,
        treeUriString: String?,
    ) {
        val callback = pendingRequests.remove(requestId) ?: return
        runCatching {
            callback(treeUriString)
        }.onFailure { error ->
            AppLogger.e(TAG, "Failed to deliver the WebSession directory picker result", error)
        }
    }

    internal fun requestIdExtra(): String = EXTRA_REQUEST_ID
}

class WebSessionDirectoryPickerActivity : ComponentActivity() {
    private var requestId: String = ""
    private var resultDelivered = false

    private val directoryLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            resultDelivered = true
            WebSessionDirectoryPickerCoordinator.completeRequest(requestId, uri?.toString())
            finishPicker()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A WindowManager overlay has no ActivityResultRegistryOwner. This transparent activity
        // owns the real registry so the SAF picker remains valid even when MainActivity is absent.
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.decorView.alpha = 0f

        requestId = intent.getStringExtra(WebSessionDirectoryPickerCoordinator.requestIdExtra()).orEmpty()
        if (requestId.isBlank()) {
            finishPicker()
            return
        }

        if (savedInstanceState == null) {
            directoryLauncher.launch(null)
        }
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations && !resultDelivered && requestId.isNotBlank()) {
            WebSessionDirectoryPickerCoordinator.completeRequest(requestId, null)
        }
        super.onDestroy()
    }

    private fun finishPicker() {
        finish()
    }
}
