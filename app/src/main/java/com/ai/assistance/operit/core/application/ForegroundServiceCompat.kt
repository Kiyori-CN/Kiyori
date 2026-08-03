package com.ai.assistance.operit.core.application

import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build

object ForegroundServiceCompat {
    fun buildTypes(
        dataSync: Boolean = true,
        specialUse: Boolean = false,
        microphone: Boolean = false
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0

        var types = 0
        if (dataSync) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        if (microphone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (specialUse && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
        return types
    }

    fun startForeground(service: Service, notificationId: Int, notification: Notification, types: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && types != 0) {
            service.startForeground(notificationId, notification, types)
        } else {
            service.startForeground(notificationId, notification)
        }
    }

}
