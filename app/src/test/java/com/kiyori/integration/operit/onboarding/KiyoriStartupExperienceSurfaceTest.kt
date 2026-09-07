package com.kiyori.integration.operit.onboarding

import com.ai.assistance.operit.data.preferences.AgreementPreferences
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriStartupExperienceSurfaceTest {
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
    fun `first run renders the shared grouped catalog with explicit global selection`() {
        val onboardingSource =
            repositoryFile(
                "app/src/main/java/com/kiyori/integration/operit/onboarding/" +
                    "KiyoriOnboardingScreen.kt",
            ).readText()
        val permissionPageBlock =
            onboardingSource.substringAfter("private fun KiyoriPermissionAuthorizationPage(")

        assertTrue(permissionPageBlock.contains("kiyoriPermissionGroups.forEach"))
        assertTrue(permissionPageBlock.contains("items = group.permissionIds"))
        assertTrue(permissionPageBlock.contains("summarizeKiyoriPermissions(snapshot)"))
        assertTrue(permissionPageBlock.contains("onClearSelection"))

        val defaultStrings =
            repositoryFile("app/src/main/res/values/strings.xml").readText()
        assertTrue(defaultStrings.contains("kiyori_onboarding_permissions_clear_all"))
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
