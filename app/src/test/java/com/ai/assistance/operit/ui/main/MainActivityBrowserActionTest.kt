package com.ai.assistance.operit.ui.main

import com.kiyori.app.shell.KiyoriShellExternalDestination
import com.kiyori.app.startup.resolveKiyoriDownloadTaskId
import com.kiyori.app.startup.resolveKiyoriShellExternalDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityBrowserActionTest {
    @Test
    fun `browser actions resolve to one explicit shell destination`() {
        assertEquals(
            KiyoriShellExternalDestination.BROWSER_HOME,
            resolveKiyoriShellExternalDestination(MainActivity.ACTION_OPEN_KIYORI_BROWSER),
        )
        assertEquals(
            KiyoriShellExternalDestination.BROWSER_SETTINGS,
            resolveKiyoriShellExternalDestination(
                MainActivity.ACTION_OPEN_KIYORI_BROWSER_SETTINGS,
            ),
        )
        assertEquals(
            KiyoriShellExternalDestination.BROWSER_HOME_FROM_MINIMIZED_INDICATOR,
            resolveKiyoriShellExternalDestination(
                MainActivity.ACTION_RESTORE_KIYORI_BROWSER_FROM_INDICATOR,
            ),
        )
        assertEquals(
            KiyoriShellExternalDestination.DOWNLOADS,
            resolveKiyoriShellExternalDestination(MainActivity.ACTION_OPEN_KIYORI_DOWNLOADS),
        )
        assertEquals(
            KiyoriShellExternalDestination.DOWNLOAD_SETTINGS,
            resolveKiyoriShellExternalDestination(
                MainActivity.ACTION_OPEN_KIYORI_DOWNLOAD_SETTINGS,
            ),
        )
        assertNull(resolveKiyoriShellExternalDestination(null))
        assertNull(resolveKiyoriShellExternalDestination("com.kiyori.action.UNKNOWN"))
        assertEquals(
            "task-1",
            resolveKiyoriDownloadTaskId(
                MainActivity.ACTION_OPEN_KIYORI_DOWNLOAD_TASK,
                " task-1 ",
            ),
        )
        assertNull(
            resolveKiyoriDownloadTaskId(
                MainActivity.ACTION_OPEN_KIYORI_DOWNLOADS,
                "task-1",
            ),
        )
        assertNull(
            resolveKiyoriDownloadTaskId(
                MainActivity.ACTION_OPEN_KIYORI_DOWNLOAD_TASK,
                " ",
            ),
        )
    }
}
