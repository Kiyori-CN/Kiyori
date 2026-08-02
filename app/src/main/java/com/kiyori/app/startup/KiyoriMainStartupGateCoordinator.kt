package com.kiyori.app.startup

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.preferences.AgreementPreferences
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.ui.features.agreement.screens.AgreementScreen
import com.ai.assistance.operit.ui.features.permission.screens.PermissionGuideScreen
import com.kiyori.platform.logging.KiyoriLogger

internal enum class KiyoriMainStartupDestination {
    AGREEMENT,
    PERMISSION_GUIDE,
    CONTENT,
}

internal fun resolveKiyoriMainStartupDestination(
    agreementAccepted: Boolean,
    showPermissionGuide: Boolean,
): KiyoriMainStartupDestination =
    when {
        !agreementAccepted -> KiyoriMainStartupDestination.AGREEMENT
        showPermissionGuide -> KiyoriMainStartupDestination.PERMISSION_GUIDE
        else -> KiyoriMainStartupDestination.CONTENT
    }

private data class KiyoriMainStartupGateDependencies(
    val isAgreementAccepted: () -> Boolean,
    val acceptCurrentAgreement: () -> Unit,
    val readPermissionLevel: () -> AndroidPermissionLevel?,
)

private fun createKiyoriMainStartupGateDependencies(
    context: Context,
): KiyoriMainStartupGateDependencies {
    val agreementPreferences = AgreementPreferences(context)
    return KiyoriMainStartupGateDependencies(
        isAgreementAccepted = agreementPreferences::isAgreementAccepted,
        acceptCurrentAgreement = agreementPreferences::acceptCurrentAgreement,
        readPermissionLevel = androidPermissionPreferences::getPreferredPermissionLevel,
    )
}

private val kiyoriMainStartupGateLogger: (tag: String, message: String) -> Unit =
    { tag, message ->
        KiyoriLogger.d(tag, message)
    }

/**
 * MainActivity 用户协议与权限级别引导的唯一 UI 状态 owner。
 *
 * AgreementPreferences 与 AndroidPermissionPreferences 继续持有唯一持久事实；该
 * coordinator 只保存是否展示权限引导的 Compose 投影，并在每次刷新时读取现有 owner。
 */
internal class KiyoriMainStartupGateCoordinator private constructor(
    private val dependencies: KiyoriMainStartupGateDependencies,
    private val logger: (tag: String, message: String) -> Unit,
) {
    constructor(context: Context) :
        this(
            dependencies = createKiyoriMainStartupGateDependencies(context),
            logger = kiyoriMainStartupGateLogger,
        )

    internal constructor(
        isAgreementAccepted: () -> Boolean,
        acceptCurrentAgreement: () -> Unit,
        readPermissionLevel: () -> AndroidPermissionLevel?,
        logger: (tag: String, message: String) -> Unit = kiyoriMainStartupGateLogger,
    ) : this(
        dependencies =
            KiyoriMainStartupGateDependencies(
                isAgreementAccepted = isAgreementAccepted,
                acceptCurrentAgreement = acceptCurrentAgreement,
                readPermissionLevel = readPermissionLevel,
            ),
        logger = logger,
    )

    private companion object {
        const val TAG = "MainActivity"
    }

    private var showPermissionGuide by mutableStateOf(false)

    val destination: KiyoriMainStartupDestination
        get() =
            resolveKiyoriMainStartupDestination(
                agreementAccepted = dependencies.isAgreementAccepted(),
                showPermissionGuide = showPermissionGuide,
            )

    val isReadyForContent: Boolean
        get() = destination == KiyoriMainStartupDestination.CONTENT

    fun refreshPermissionLevel() {
        val permissionLevel = dependencies.readPermissionLevel()
        logger(TAG, "当前权限级别: $permissionLevel")
        showPermissionGuide = permissionLevel == null
        logger(
            TAG,
            "权限级别检查: 已设置=${!showPermissionGuide}, " +
                "将${if (showPermissionGuide) "" else "不"}显示权限引导界面",
        )
    }

    fun acceptCurrentAgreement() {
        dependencies.acceptCurrentAgreement()
    }

    fun completePermissionGuide() {
        showPermissionGuide = false
    }
}

@Composable
internal fun KiyoriMainStartupGate(
    destination: KiyoriMainStartupDestination,
    onAgreementAccepted: () -> Unit,
    onPermissionGuideComplete: () -> Unit,
    content: @Composable () -> Unit,
) {
    when (destination) {
        KiyoriMainStartupDestination.AGREEMENT ->
            AgreementScreen(onAgreementAccepted = onAgreementAccepted)

        KiyoriMainStartupDestination.PERMISSION_GUIDE ->
            PermissionGuideScreen(onComplete = onPermissionGuideComplete)

        KiyoriMainStartupDestination.CONTENT -> content()
    }
}
