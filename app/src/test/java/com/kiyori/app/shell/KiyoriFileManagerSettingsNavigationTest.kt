package com.kiyori.app.shell

import com.kiyori.capability.settings.navigation.KiyoriSettingsRoute
import org.junit.Assert.*
import org.junit.Test

class KiyoriFileManagerSettingsNavigationTest {
    @Test fun `file settings and permissions return to the retained file session`() {
        val files = KiyoriShellState().openFileManager()
        val settings = files.openSettings(KiyoriSettingsOrigin.FILE_MANAGER, KiyoriSettingsRoute.FILE_MANAGER)
        val permissions = settings.openSettingsRoute(KiyoriSettingsRoute.PERMISSIONS)
        val returnedSettings = permissions.closeSettingsRoute()
        assertEquals(KiyoriSettingsRoute.FILE_MANAGER, returnedSettings.settingsNavigation?.currentRoute)
        val returnedFiles = returnedSettings.closeSettingsRoute()
        assertEquals(KiyoriShellChild.FILE_MANAGER, returnedFiles.child)
        assertTrue(returnedFiles.fileManagerSessionOpen)
        assertNull(returnedFiles.settingsNavigation)
    }

    @Test fun `settings home file entry returns to settings home`() {
        val settings = KiyoriShellState().openSettings(KiyoriSettingsOrigin.BOTTOM_NAVIGATION, KiyoriSettingsRoute.FILE_MANAGER)
        val returned = settings.closeSettingsRoute()
        assertEquals(KiyoriSettingsRoute.HOME, returned.settingsNavigation?.currentRoute)
        assertNull(returned.child)
    }
}
