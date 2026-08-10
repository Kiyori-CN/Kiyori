package com.kiyori.app.startup

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.data.preferences.AgreementPreferences
import com.ai.assistance.operit.ui.features.agreement.screens.KiyoriAgreementConfirmationScreen
import com.kiyori.integration.operit.onboarding.KiyoriOnboardingPreferences
import com.kiyori.integration.operit.onboarding.KiyoriOnboardingScreen

internal enum class KiyoriMainStartupDestination {
    ONBOARDING,
    AGREEMENT,
    CONTENT,
}

internal fun resolveKiyoriMainStartupDestination(
    agreementAccepted: Boolean,
    onboardingCompleted: Boolean,
): KiyoriMainStartupDestination =
    when {
        !onboardingCompleted -> KiyoriMainStartupDestination.ONBOARDING
        !agreementAccepted -> KiyoriMainStartupDestination.AGREEMENT
        else -> KiyoriMainStartupDestination.CONTENT
    }

private data class KiyoriMainStartupGateDependencies(
    val isAgreementAccepted: () -> Boolean,
    val acceptCurrentAgreement: () -> Unit,
    val isOnboardingCompleted: () -> Boolean,
    val completeOnboarding: () -> Unit,
)

private fun createKiyoriMainStartupGateDependencies(
    context: Context,
): KiyoriMainStartupGateDependencies {
    val agreementPreferences = AgreementPreferences(context)
    val onboardingPreferences = KiyoriOnboardingPreferences(context)
    return KiyoriMainStartupGateDependencies(
        isAgreementAccepted = agreementPreferences::isAgreementAccepted,
        acceptCurrentAgreement = agreementPreferences::acceptCurrentAgreement,
        isOnboardingCompleted = onboardingPreferences::isCompleted,
        completeOnboarding = onboardingPreferences::complete,
    )
}

/**
 * MainActivity 首次启动与协议重新确认的唯一 UI 目的地 owner。
 *
 * AgreementPreferences 与 KiyoriOnboardingPreferences 继续持有各自唯一持久事实；该
 * coordinator 只保存用于 Compose 重组的内存投影，不复制权限、插件或运行时状态。
 */
internal class KiyoriMainStartupGateCoordinator private constructor(
    private val dependencies: KiyoriMainStartupGateDependencies,
) {
    constructor(context: Context) :
        this(
            dependencies = createKiyoriMainStartupGateDependencies(context),
        )

    internal constructor(
        isAgreementAccepted: () -> Boolean,
        acceptCurrentAgreement: () -> Unit,
        isOnboardingCompleted: () -> Boolean,
        completeOnboarding: () -> Unit,
    ) : this(
        dependencies =
            KiyoriMainStartupGateDependencies(
                isAgreementAccepted = isAgreementAccepted,
                acceptCurrentAgreement = acceptCurrentAgreement,
                isOnboardingCompleted = isOnboardingCompleted,
                completeOnboarding = completeOnboarding,
            ),
    )

    private var agreementAccepted by
        mutableStateOf(dependencies.isAgreementAccepted())
    private var onboardingCompleted by
        mutableStateOf(dependencies.isOnboardingCompleted())

    val destination: KiyoriMainStartupDestination
        get() =
            resolveKiyoriMainStartupDestination(
                agreementAccepted = agreementAccepted,
                onboardingCompleted = onboardingCompleted,
            )

    val isAgreementAccepted: Boolean
        get() = agreementAccepted

    val isReadyForContent: Boolean
        get() = destination == KiyoriMainStartupDestination.CONTENT

    fun acceptCurrentAgreement() {
        dependencies.acceptCurrentAgreement()
        agreementAccepted = true
    }

    fun completeOnboarding() {
        dependencies.completeOnboarding()
        onboardingCompleted = true
    }
}

@Composable
internal fun KiyoriMainStartupGate(
    destination: KiyoriMainStartupDestination,
    agreementAccepted: Boolean,
    onAgreementAccepted: () -> Unit,
    onAgreementDeclined: () -> Unit,
    onOnboardingComplete: () -> Unit,
    content: @Composable () -> Unit,
) {
    when (destination) {
        KiyoriMainStartupDestination.ONBOARDING ->
            KiyoriOnboardingScreen(
                agreementAccepted = agreementAccepted,
                onAgreementAccepted = onAgreementAccepted,
                onComplete = onOnboardingComplete,
            )

        KiyoriMainStartupDestination.AGREEMENT ->
            KiyoriAgreementConfirmationScreen(
                onAccepted = onAgreementAccepted,
                onDeclined = onAgreementDeclined,
            )

        KiyoriMainStartupDestination.CONTENT -> content()
    }
}
