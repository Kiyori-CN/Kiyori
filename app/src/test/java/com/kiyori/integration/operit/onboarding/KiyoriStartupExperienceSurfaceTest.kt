package com.kiyori.integration.operit.onboarding

import com.ai.assistance.operit.data.preferences.AgreementPreferences
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriStartupExperienceSurfaceTest {
    @Test
    fun `sixteen feature descriptions have exactly two complete short lines and unique titles`() {
        val strings = repositoryFile("app/src/main/res/values/strings_onboarding_redesign.xml").readText()
        val cards = Regex("<string name=\"onb_card_([^\"]+)_(title|line1|line2)\"[^>]*>([^<]+)</string>")
            .findAll(strings).toList()
        assertEquals(16, cards.count { it.groupValues[2] == "title" })
        assertEquals(16, cards.count { it.groupValues[2] == "line1" })
        assertEquals(16, cards.count { it.groupValues[2] == "line2" })
        assertEquals(16, cards.filter { it.groupValues[2] == "title" }.map { it.groupValues[3] }.toSet().size)
        cards.filter { it.groupValues[2] != "title" }.forEach { card ->
            assertTrue(card.value, card.groupValues[3].isNotBlank() && card.groupValues[3].length <= 16)
        }

        val source = repositoryFile(
            "app/src/main/java/com/kiyori/integration/operit/onboarding/KiyoriOnboardingScreen.kt",
        ).readText()
        val orderedTitles = listOf(
            "browser", "ai", "miniapp", "files",
            "adblock", "download", "video", "music",
            "model", "voice", "memory", "toolbox",
            "terminal", "workflow", "theme", "backup",
        )
        assertEquals(
            orderedTitles,
            cards.filter { it.groupValues[2] == "title" }.map { it.groupValues[1] },
        )
        orderedTitles.forEach { id -> assertTrue(id, source.contains("onb_card_${id}_title")) }
        assertEquals(3, Regex("badge = stringResource\\(R.string.onb_badge_setup\\)").findAll(source).count())
        assertEquals(2, Regex("badge = stringResource\\(R.string.onb_badge_wip\\)").findAll(source).count())
    }

    @Test
    fun `onboarding redesign keeps the reference light dark and geometry tokens`() {
        val design = repositoryFile(
            "app/src/main/java/com/kiyori/integration/operit/onboarding/KiyoriOnboardingDesign.kt",
        ).readText()
        listOf(
            "0xFFF3F5F8", "0xFFFFFFFF", "0xFF2F6BFF", "0xFF0E1116", "0xFF171B22", "0xFF5E8DFF",
            "TopBarHeight = 48", "ProgressHeight = 4", "ProgressGap = 6", "MapHeight = 104",
            "CardRadius = 16", "CardMinHeight = 122", "PrimaryButtonHeight = 50",
            "PrimaryButtonRadius = 15",
        ).forEach { token -> assertTrue(token, design.contains(token)) }
    }

    @Test
    fun `review pager ignores saved page while first run retains native restoration`() {
        val source = repositoryFile(
            "app/src/main/java/com/kiyori/integration/operit/onboarding/KiyoriOnboardingScreen.kt",
        ).readText()
        val pager = source.substringAfter("val pagerState =").substringBefore("val pagerScope")
        assertTrue(pager.contains("if (startFromBeginning) {"))
        assertTrue(pager.contains("currentPage = KiyoriOnboardingStep.WELCOME.ordinal"))
        assertTrue(pager.contains("else {"))
        assertTrue(source.contains("OnboardingPageStateHost(startFromBeginning = startFromBeginning)"))
        assertTrue(source.contains("if (startFromBeginning)"))
        assertTrue(source.contains("stateHolder.SaveableStateProvider(\"onboarding_pages\", content)"))
        assertFalse(source.contains("System.currentTimeMillis()"))
        assertFalse(source.contains("enabled = !navigationBusy"))
        assertFalse(source.contains("navigationEnabled = !navigationBusy"))

        val preferences = repositoryFile(
            "app/src/main/java/com/kiyori/integration/operit/onboarding/KiyoriOnboardingPreferences.kt",
        ).readText()
        assertTrue(preferences.contains("fun hasSelectedPermissionChoice()"))
        assertTrue(preferences.contains("preferences.contains(KEY_SELECTED_PERMISSIONS)"))
        assertTrue(source.contains("preferences.hasSelectedPermissionChoice()"))
        assertTrue(source.contains("setOf(KiyoriPermissionId.NOTIFICATIONS, KiyoriPermissionId.MEDIA)"))
    }

    @Test
    fun `startup legal and plugin surfaces consume safe drawing insets`() {
        val agreementSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/agreement/" +
                    "screens/KiyoriAgreementScreen.kt",
            ).readText()
        val confirmationBlock =
            agreementSource
                .substringAfter("internal fun KiyoriAgreementConfirmationScreen(")
                .substringBefore("internal fun KiyoriAgreementSummary(")
        assertTrue(
            confirmationBlock.contains(
                ".windowInsetsPadding(WindowInsets.safeDrawing)",
            ),
        )

        val onboardingSource =
            repositoryFile(
                "app/src/main/java/com/kiyori/integration/operit/onboarding/" +
                    "KiyoriOnboardingScreen.kt",
            ).readText()
        assertTrue(
            onboardingSource.contains(
                ".windowInsetsPadding(WindowInsets.safeDrawing)",
            ),
        )

        val pluginSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/startup/" +
                    "screens/PluginLoadingScreen.kt",
            ).readText()
        assertTrue(
            pluginSource.contains(
                ".windowInsetsPadding(WindowInsets.safeDrawing)",
            ),
        )
        assertTrue(pluginSource.contains("R.string.plugin_collapse"))
    }

    @Test
    fun `first run renders the shared grouped catalog with individual permission selection`() {
        val onboardingSource =
            repositoryFile(
                "app/src/main/java/com/kiyori/integration/operit/onboarding/" +
                    "KiyoriOnboardingScreen.kt",
            ).readText()
        val permissionPageBlock =
            onboardingSource.substringAfter("private fun KiyoriPermissionAuthorizationPage(")

        assertTrue(permissionPageBlock.contains("kiyoriPermissionGroups.forEach"))
        assertTrue(permissionPageBlock.contains("group.permissionIds.forEach"))
        assertFalse(permissionPageBlock.contains("onSelectAll"))
        assertFalse(permissionPageBlock.contains("PermissionOverviewMetric"))
        assertTrue(permissionPageBlock.contains("onSelectRecommended"))
        assertTrue(permissionPageBlock.contains("R.string.onb_p6_summary"))
        assertTrue(permissionPageBlock.contains("autoExpandWhenSelected = false"))
        assertTrue(permissionPageBlock.contains("onClearSelection"))

        val defaultStrings =
            repositoryFile("app/src/main/res/values/strings.xml").readText()
        assertTrue(defaultStrings.contains("kiyori_onboarding_permissions_clear_all"))
    }

    @Test
    fun `review uses a modal window rather than consuming child gestures`() {
        val shell = repositoryFile("app/src/main/java/com/kiyori/app/shell/KiyoriAppShell.kt").readText()
        val review = shell.substringAfter("KiyoriSettingsRoute.ONBOARDING_REVIEW ->")
            .substringBefore("KiyoriSettingsRoute.AGREEMENT ->")
        assertTrue(review.contains("startFromBeginning = true"))
        assertTrue(review.contains("onExitReview = { onStateChange(state.closeSettingsRoute()) }"))
        assertFalse(review.contains(".pointerInput("))
        assertFalse(review.contains(".consume()"))
        val screen = repositoryFile(
            "app/src/main/java/com/kiyori/integration/operit/onboarding/KiyoriOnboardingScreen.kt",
        ).readText()
        val presentation = screen.substringAfter("private fun KiyoriOnboardingPresentation(")
            .substringBefore("private fun OnboardingProgressHeader(")
        assertTrue(presentation.contains("Dialog("))
        assertTrue(presentation.contains("dismissOnClickOutside = false"))
        assertTrue(presentation.contains("dismissOnBackPress = true"))
        assertTrue(presentation.contains("onDismissRequest = onBack"))
    }

    @Test
    fun `six pages share a fixed header and one native swipe owner`() {
        val source = repositoryFile(
            "app/src/main/java/com/kiyori/integration/operit/onboarding/KiyoriOnboardingScreen.kt",
        ).readText()
        val header = source.substringAfter("private fun OnboardingProgressHeader(")
            .substringBefore("private val KiyoriOnboardingStep.onboardingLabelResId")
        assertTrue(header.contains(".height(KiyoriOnboardingMetrics.TopBarHeight.dp)"))
        assertTrue(header.contains(".size(48.dp)"))
        assertFalse(source.contains("detectHorizontalDragGestures"))
        assertTrue(source.contains("pageCount = { kiyoriOnboardingPageCount(agreementAcceptedState) }"))
        assertTrue(source.contains("finally {\n                navigationInFlight = false"))
    }

    @Test
    fun `drag cancellation reaches native clickable before release without blocking accessibility`() {
        val source = repositoryFile(
            "app/src/main/java/com/kiyori/integration/operit/onboarding/KiyoriOnboardingScreen.kt",
        ).readText()
        val modifier = source.substringAfter("private fun Modifier.onboardingTapOnly(")
            .substringBefore("private val KiyoriOnboardingStep.onboardingLabelResId")
        assertTrue(modifier.contains("it.previousPressed && !it.pressed"))
        assertTrue(modifier.indexOf("it.consume()") < modifier.indexOf("awaitPointerEvent(PointerEventPass.Final)"))
        assertFalse(source.contains("onClick = { if (gesture.allowsClick)"))
        assertTrue(source.contains("KiyoriOnboardingAgreementPage("))
        assertTrue(source.contains(".onboardingTapOnly(canStartTap)"))
    }

    @Test
    fun `agreement and permission pages retain the reference structure without false permission claims`() {
        val source = repositoryFile(
            "app/src/main/java/com/kiyori/integration/operit/onboarding/KiyoriOnboardingScreen.kt",
        ).readText()
        val strings = repositoryFile("app/src/main/res/values/strings_onboarding_redesign.xml").readText()
        val manifest = repositoryFile("app/src/main/AndroidManifest.xml").readText()

        listOf(
            "AgreementNotice()", "AgreementDocumentCard(", "AgreementPreferences.CURRENT_AGREEMENT_VERSION",
            "userAgreementRead = true", "privacyPolicyRead = true", "showAgreementExitConfirmation = true",
            "PermissionSelectionSummary(", "KiyoriPermissionId.NOTIFICATIONS", "KiyoriPermissionId.MEDIA",
            "KiyoriPermissionId.MICROPHONE", "R.string.onb_p6_use_when_needed",
        ).forEach { contract -> assertTrue(contract, source.contains(contract)) }
        assertTrue(strings.contains("onb_p5_point1_title"))
        assertTrue(strings.contains("onb_p6_permission_media_desc"))
        assertFalse(strings.contains("Kiyori 不申请定位、通讯录、短信与通话记录"))
        assertFalse(manifest.contains("android.permission.READ_MEDIA_IMAGES"))
    }

    @Test
    fun `large font introduction keeps all text and uses one column instead of shrinking`() {
        val source = repositoryFile(
            "app/src/main/java/com/kiyori/integration/operit/onboarding/KiyoriOnboardingScreen.kt",
        ).readText()
        val cards = source.substringAfter("private fun OnboardingFeatureGrid(")
            .substringBefore("private fun KiyoriWelcomePage(")
        assertTrue(cards.contains("fontScale >= 1.3f"))
        assertFalse(cards.contains("maxWidth < 340.dp"))
        assertTrue(cards.contains("else 2"))
        assertFalse(cards.contains("fittedStyle"))
        assertFalse(cards.contains("maxLines ="))
        assertFalse(cards.contains("softWrap = false"))
        assertTrue(cards.contains("semantics(mergeDescendants = true)"))
    }

    @Test
    fun `system permission returns do not complete onboarding or dispatch another request automatically`() {
        val source = repositoryFile(
            "app/src/main/java/com/kiyori/integration/operit/onboarding/KiyoriOnboardingScreen.kt",
        ).readText()
        val queue = source.substringAfter("LaunchedEffect(\n        authorizationActive,")
            .substringBefore("fun startAuthorization()")
        assertTrue(queue.contains("authorizationNeedsContinue"))
        assertFalse(queue.contains("completeOnboarding()"))
        assertTrue(source.contains("onStopAuthorization = ::stopAuthorization"))
        assertTrue(source.contains("generation != authorizationGeneration"))
        assertTrue(source.contains("if (!startFromBeginning) preferences.complete()"))
    }

    @Test
    fun `legal body version is formatted from the agreement owner`() {
        val agreementSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/agreement/" +
                    "screens/KiyoriAgreementScreen.kt",
            ).readText()
        assertTrue(
            agreementSource.contains(
                "AgreementPreferences.CURRENT_AGREEMENT_VERSION",
            ),
        )
        assertTrue(agreementSource.contains("document.contentResId to"))

        val defaultStrings =
            repositoryFile("app/src/main/res/values/strings.xml").readText()
        val legalBodyBlock =
            defaultStrings
                .substringAfter("kiyori_onboarding_user_agreement_content")
                .substringBefore("kiyori_onboarding_legal_documents_title")
        assertTrue(legalBodyBlock.countOccurrences("%1\$s") >= 4)
        assertFalse(
            legalBodyBlock.contains(
                AgreementPreferences.CURRENT_AGREEMENT_VERSION,
            ),
        )
    }

    @Test
    fun `media copy matches the declared runtime permission scope`() {
        val manifest = repositoryFile("app/src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("android.permission.READ_MEDIA_IMAGES"))

        val defaultStrings =
            repositoryFile("app/src/main/res/values/strings.xml").readText()
        val mediaCopy =
            defaultStrings
                .substringAfter("kiyori_onboarding_permission_media_title")
                .substringBefore("kiyori_onboarding_permission_camera_title")
        assertTrue(mediaCopy.contains("视频与音频"))
        assertTrue(mediaCopy.contains("系统文件选择器按次选择"))
        assertFalse(mediaCopy.contains("照片、视频与音频"))
    }

    private fun String.countOccurrences(value: String): Int =
        windowed(size = value.length, step = 1, partialWindows = false)
            .count { candidate -> candidate == value }

    private fun repositoryFile(relativePath: String): File {
        var current: File? =
            File(checkNotNull(System.getProperty("user.dir"))).absoluteFile
        while (current != null) {
            val candidate = File(current, relativePath)
            if (candidate.isFile) {
                return candidate
            }
            current = current.parentFile
        }
        error("Unable to locate repository file: $relativePath")
    }
}
