package com.kiyori.app.shell

import com.kiyori.capability.settings.navigation.KiyoriSettingsRoute
import org.junit.Assert.*
import org.junit.Test

class KiyoriFileManagerSettingsNavigationTest {
    @Test fun `closing file restore indicator from settings does not resurrect its session on back`() {
        val settings = KiyoriShellState().openFileManager()
            .openSettings(KiyoriSettingsOrigin.FILE_MANAGER, KiyoriSettingsRoute.FILE_MANAGER)
            .closeMinimizedFileManager()
        val returned = settings.closeSettingsRoute()
        assertFalse(returned.fileManagerSessionOpen)
        assertFalse(returned.showsFileManagerIndicator)
        assertNull(returned.child)
    }
    @Test fun `retained file session exposes restore across all navigation exits`() {
        val files = KiyoriShellState().openFileManager()
        assertFalse(files.showsFileManagerIndicator)
        assertTrue(files.showSoftwareHomePage(SoftwareHomePage.AI_HOME).showsFileManagerIndicator)
        assertTrue(files.selectPrimary(PrimaryDestination.MINI_APP_HOME).showsFileManagerIndicator)
        assertTrue(files.openBrowser(KiyoriBrowserReturnTarget.AI_HOME).showsFileManagerIndicator)
        assertTrue(files.openSettings(KiyoriSettingsOrigin.FILE_MANAGER).showsFileManagerIndicator)
        assertFalse(files.closeChild().showsFileManagerIndicator)
        assertFalse(files.minimizeFileManager().closeMinimizedFileManager().showsFileManagerIndicator)
        assertFalse(files.minimizeFileManager().openFileManager().showsFileManagerIndicator)
    }
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
