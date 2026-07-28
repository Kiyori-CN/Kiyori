package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.shell.KiyoriShellExternalDestination
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
            KiyoriShellExternalDestination.DOWNLOAD_SETTINGS,
            resolveKiyoriShellExternalDestination(
                MainActivity.ACTION_OPEN_KIYORI_DOWNLOAD_SETTINGS,
            ),
        )
        assertNull(resolveKiyoriShellExternalDestination(null))
        assertNull(resolveKiyoriShellExternalDestination("com.kiyori.action.UNKNOWN"))
    }
}
