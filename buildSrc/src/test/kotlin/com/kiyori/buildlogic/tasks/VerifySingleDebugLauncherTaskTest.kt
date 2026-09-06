package com.kiyori.buildlogic.tasks

import java.io.File
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerifySingleDebugLauncherTaskTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val launcher = """
        <activity android:name="com.ai.assistance.operit.ui.main.MainActivity">
          <intent-filter>
            <action android:name="android.intent.action.MAIN"/>
            <category android:name="android.intent.category.LAUNCHER"/>
          </intent-filter>
        </activity>
    """.trimIndent()

    private fun verify(components: String) {
        val project = ProjectBuilder.builder().withProjectDir(temporary.root).build()
        val manifest = File(temporary.root, "AndroidManifest.xml").apply {
            writeText("""
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                  <application>$components</application>
                </manifest>
            """.trimIndent())
        }
        project.tasks.register("verifyLauncher", VerifySingleDebugLauncherTask::class.java).get().apply {
            mergedManifest.set(manifest)
            verify()
        }
    }

    @Test
    fun acceptsTheStableLauncherWithOtherNonLauncherActivities() {
        verify(launcher + "<activity android:name=\"example.DetailActivity\"/>")
    }

    @Test
    fun rejectsAnAdditionalLauncherAlias() {
        val alias = launcher.replace("activity", "activity-alias").replace("MainActivity", "OtherLauncher")
        val error = assertThrows(IllegalStateException::class.java) { verify(launcher + alias) }
        assertTrue(error.message.orEmpty().contains("exactly one launcher"))
    }

    @Test
    fun rejectsMissingLauncher() {
        assertThrows(IllegalStateException::class.java) { verify("") }
    }

    @Test
    fun rejectsTheDependencySampleActivityEvenWithoutAnIntentFilter() {
        val error = assertThrows(IllegalStateException::class.java) {
            verify(launcher + "<activity android:name=\"live.pw.renderX.LatexView\"/>")
        }
        assertTrue(error.message.orEmpty().contains("sample LatexView leaked"))
    }
}
