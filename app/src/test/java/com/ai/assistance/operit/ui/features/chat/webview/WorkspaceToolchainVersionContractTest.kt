package com.ai.assistance.operit.ui.features.chat.webview

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the user-facing workspace bootstrap assets aligned with the version decisions recorded
 * for the Ubuntu environment. These are separate ecosystem contracts, so the test checks each
 * template's supported line instead of requiring one global Gradle version.
 */
class WorkspaceToolchainVersionContractTest {
    @Test
    fun javaWorkspaceUsesThePinnedStableGradleLine() {
        val source = repositoryFile(
            "app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/WorkspaceUtils.kt",
        ).readText()
        val readme = repositoryFile("app/src/main/assets/templates/java/README.md").readText()

        assertTrue(source.contains("JAVA_WORKSPACE_GRADLE_VERSION = \"9.7.1\""))
        assertTrue(source.contains("gradle wrapper --gradle-version ${'$'}JAVA_WORKSPACE_GRADLE_VERSION"))
        assertFalse(source.contains("gradle wrapper --gradle-version 8.5"))
        assertTrue(readme.contains("OpenJDK 25 LTS"))
        assertTrue(readme.contains("Gradle 9.7.1"))
    }

    @Test
    fun androidWorkspaceUsesVerifiedGradle971Archive() {
        val script = repositoryFile("app/src/main/assets/templates/android/setup_android_env.sh").readText()
        val wrapper = repositoryFile(
            "app/src/main/assets/templates/android/gradle/wrapper/gradle-wrapper.properties",
        ).readText()

        assertTrue(script.contains("GRADLE_VERSION=\"9.7.1\""))
        assertTrue(script.contains("GRADLE_SHA256=\"acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a\""))
        assertTrue(script.contains("verify_gradle_archive \"${'$'}GRADLE_ZIP\""))
        assertTrue(script.contains("kiyori android env"))
        assertTrue(script.contains("(operit|kiyori) android env"))
        assertFalse(script.contains("9.5.0"))
        assertTrue(wrapper.contains("gradle-9.7.1-bin.zip"))
        assertTrue(wrapper.contains("distributionSha256Sum=acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a"))
        assertFalse(wrapper.contains("9.5.0"))
    }

    @Test
    fun flutterWorkspaceTracksFlutter347StableGradleContract() {
        val script = repositoryFile("app/src/main/assets/templates/flutter/android/setup_android_env.sh").readText()
        val wrapper = repositoryFile(
            "app/src/main/assets/templates/flutter/android/gradle/wrapper/gradle-wrapper.properties",
        ).readText()
        val settings = repositoryFile("app/src/main/assets/templates/flutter/android/settings.gradle.kts").readText()

        assertTrue(script.contains("GRADLE_VERSION=\"9.3.1\""))
        assertTrue(script.contains("GRADLE_SHA256=\"b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06\""))
        assertTrue(script.contains("kiyori flutter android env"))
        assertTrue(script.contains("(operit|kiyori) flutter android env"))
        assertFalse(script.contains("8.14"))
        assertTrue(wrapper.contains("gradle-9.3.1-all.zip"))
        assertTrue(wrapper.contains("distributionSha256Sum=17f277867f6914d61b1aa02efab1ba7bb439ad652ca485cd8ca6842fccec6e43"))
        assertFalse(wrapper.contains("8.14"))
        assertTrue(settings.contains("id(\"com.android.application\") version \"9.1.0\""))
        assertTrue(settings.contains("id(\"org.jetbrains.kotlin.android\") version \"2.4.0\""))
        assertFalse(settings.contains("8.11.1"))
        assertFalse(settings.contains("2.2.20"))
    }

    private fun repositoryFile(relativePath: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir")))
        repeat(8) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) {
                return candidate
            }
            directory = directory.parentFile ?: return@repeat
        }
        throw AssertionError("Repository file not found: $relativePath")
    }
}
