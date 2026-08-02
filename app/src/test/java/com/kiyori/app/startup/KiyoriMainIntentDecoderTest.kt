package com.kiyori.app.startup

import android.content.Intent
import android.net.Uri
import com.ai.assistance.operit.widget.ToolPkgDesktopWidgetHost
import com.kiyori.app.shell.KiyoriShellExternalDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KiyoriMainIntentDecoderTest {
    @Test
    fun `player and download commands preserve priority and trimmed task id`() {
        val restartIntent = mock<Intent>()
        whenever(restartIntent.action)
            .thenReturn(KiyoriMainIntentContract.ACTION_RESTART_PLAYER_AFTER_CRASH)
        whenever(
            restartIntent.getLongExtra(
                KiyoriMainIntentContract.EXTRA_PLAYER_RUNTIME_GENERATION,
                0L,
            )
        ).thenReturn(17L)

        val restart = decodeKiyoriMainIntent(restartIntent)

        assertEquals(
            KiyoriMainIntentCommand.RestartPlayer(runtimeGeneration = 17L),
            restart.command,
        )
        assertFalse(restart.processPendingSharedContent)

        val downloadIntent = mock<Intent>()
        whenever(downloadIntent.action)
            .thenReturn(KiyoriMainIntentContract.ACTION_OPEN_KIYORI_DOWNLOAD_TASK)
        whenever(
            downloadIntent.getStringExtra(
                KiyoriMainIntentContract.EXTRA_KIYORI_DOWNLOAD_TASK_ID
            )
        ).thenReturn(" task-17 ")

        val download = decodeKiyoriMainIntent(downloadIntent)

        assertEquals(
            KiyoriMainIntentCommand.OpenDownloadedTask(taskId = "task-17"),
            download.command,
        )
        assertFalse(download.processPendingSharedContent)
    }

    @Test
    fun `shell settings and route commands keep existing resolution order`() {
        val shellIntent = mock<Intent>()
        whenever(shellIntent.action)
            .thenReturn(KiyoriMainIntentContract.ACTION_OPEN_KIYORI_DOWNLOAD_SETTINGS)
        assertEquals(
            KiyoriMainIntentCommand.OpenShellDestination(
                KiyoriShellExternalDestination.DOWNLOAD_SETTINGS
            ),
            decodeKiyoriMainIntent(shellIntent).command,
        )

        val settingsIntent = mock<Intent>()
        whenever(settingsIntent.action)
            .thenReturn(KiyoriMainIntentContract.ACTION_OPEN_SETTINGS_SHORTCUT)
        assertEquals(
            KiyoriMainIntentCommand.OpenSettingsShortcut,
            decodeKiyoriMainIntent(settingsIntent).command,
        )

        val routeIntent = mock<Intent>()
        whenever(routeIntent.action).thenReturn("com.kiyori.action.WIDGET")
        whenever(
            routeIntent.getStringExtra(ToolPkgDesktopWidgetHost.EXTRA_OPEN_ROUTE_ID)
        ).thenReturn(" editor ")
        whenever(
            routeIntent.getStringExtra(
                ToolPkgDesktopWidgetHost.EXTRA_OPEN_ROUTE_ARGS_JSON
            )
        ).thenReturn("""{"path":"/tmp/file"}""")

        assertEquals(
            KiyoriMainIntentCommand.OpenRoute(
                routeId = "editor",
                routeArgsJson = """{"path":"/tmp/file"}""",
            ),
            decodeKiyoriMainIntent(routeIntent).command,
        )
    }

    @Test
    fun `OAuth redirect is decoded before view content`() {
        val authUri = mock<Uri>()
        whenever(authUri.scheme).thenReturn("operit")
        whenever(authUri.host).thenReturn("github-oauth-callback")
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn(Intent.ACTION_VIEW)
        whenever(intent.data).thenReturn(authUri)

        val decoding = decodeKiyoriMainIntent(intent)

        assertEquals(
            KiyoriMainIntentCommand.CompleteGitHubAuth(authUri),
            decoding.command,
        )
        assertTrue(decoding.processPendingSharedContent)
    }

    @Test
    fun `view command distinguishes browser URL file URI and missing data`() {
        val browserUri = mock<Uri>()
        whenever(browserUri.scheme).thenReturn("HTTPS")
        whenever(browserUri.toString()).thenReturn("https://example.com/path")
        val browserIntent = mock<Intent>()
        whenever(browserIntent.action).thenReturn(Intent.ACTION_VIEW)
        whenever(browserIntent.data).thenReturn(browserUri)

        val browser = decodeKiyoriMainIntent(browserIntent)

        assertEquals(
            KiyoriMainIntentCommand.OpenBrowser("https://example.com/path"),
            browser.command,
        )
        assertTrue(browser.processPendingSharedContent)

        val fileUri = mock<Uri>()
        whenever(fileUri.scheme).thenReturn("content")
        val fileIntent = mock<Intent>()
        whenever(fileIntent.action).thenReturn(Intent.ACTION_VIEW)
        whenever(fileIntent.data).thenReturn(fileUri)

        assertEquals(
            KiyoriMainIntentCommand.OpenSharedFile(fileUri),
            decodeKiyoriMainIntent(fileIntent).command,
        )

        val emptyViewIntent = mock<Intent>()
        whenever(emptyViewIntent.action).thenReturn(Intent.ACTION_VIEW)
        assertEquals(
            KiyoriMainIntentCommand.None,
            decodeKiyoriMainIntent(emptyViewIntent).command,
        )
        assertTrue(
            decodeKiyoriMainIntent(emptyViewIntent)
                .processPendingSharedContent
        )
    }

    @Test
    fun `single and multiple share commands preserve URI and nonblank text`() {
        val singleUri = mock<Uri>()
        val singleIntent = mock<Intent>()
        whenever(singleIntent.action).thenReturn(Intent.ACTION_SEND)
        @Suppress("DEPRECATION")
        whenever(
            singleIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        ).thenReturn(singleUri)
        whenever(singleIntent.getStringExtra(Intent.EXTRA_TEXT)).thenReturn("shared text")

        assertEquals(
            KiyoriMainIntentCommand.ShareSingle(singleUri, "shared text"),
            decodeKiyoriMainIntent(singleIntent).command,
        )

        val firstUri = mock<Uri>()
        val secondUri = mock<Uri>()
        val multipleIntent = mock<Intent>()
        whenever(multipleIntent.action).thenReturn(Intent.ACTION_SEND_MULTIPLE)
        @Suppress("DEPRECATION")
        whenever(
            multipleIntent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        ).thenReturn(arrayListOf(firstUri, secondUri))
        whenever(multipleIntent.getStringExtra(Intent.EXTRA_TEXT)).thenReturn(" ")

        val multiple = decodeKiyoriMainIntent(multipleIntent)

        assertEquals(
            KiyoriMainIntentCommand.ShareMultiple(
                uris = listOf(firstUri, secondUri),
                text = null,
            ),
            multiple.command,
        )
        assertTrue(multiple.processPendingSharedContent)
    }
}
