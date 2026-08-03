package com.ai.assistance.operit.core.tools.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.shower.IShowerService
import com.ai.assistance.shower.ShowerBinderContainer

class ShowerBinderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOWER_BINDER_READY) {
            return
        }
        val container =
            IntentCompat.getParcelableExtra(
                intent,
                EXTRA_BINDER_CONTAINER,
                ShowerBinderContainer::class.java
            )
        val binder = container?.binder
        if (binder == null) {
            AppLogger.w(TAG, "Rejected Shower Binder handoff without a Binder")
            return
        }
        val descriptor =
            runCatching { binder.interfaceDescriptor }
                .getOrElse { error ->
                    AppLogger.e(TAG, "Failed to read Shower Binder descriptor", error)
                    return
                }
        if (descriptor != SHOWER_SERVICE_DESCRIPTOR) {
            AppLogger.w(TAG, "Rejected Shower Binder with descriptor: $descriptor")
            return
        }
        val service = IShowerService.Stub.asInterface(binder)
        if (!service.asBinder().isBinderAlive) {
            AppLogger.w(TAG, "Rejected dead Shower Binder")
            return
        }
        AppLogger.d(TAG, "Accepted live Shower Binder")
        ShowerBinderRegistry.setService(service)
    }

    companion object {
        private const val TAG = "ShowerBinderReceiver"
        private const val SHOWER_SERVICE_DESCRIPTOR =
            "com.ai.assistance.shower.IShowerService"
        const val ACTION_SHOWER_BINDER_READY = "com.ai.assistance.operit.action.SHOWER_BINDER_READY"
        const val EXTRA_BINDER_CONTAINER = "binder_container"
    }
}
