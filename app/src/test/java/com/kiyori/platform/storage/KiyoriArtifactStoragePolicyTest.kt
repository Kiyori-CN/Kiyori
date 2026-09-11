package com.kiyori.platform.storage

import android.content.SharedPreferences
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*

class KiyoriArtifactStoragePolicyTest {
    @Test fun failedDiskCommitRestoresPreviouslyPublishedMemoryValues() {
        val values = mutableMapOf<String, String>("android_root" to "/old/android", "linux_root" to "/old/linux")
        val prefs = preferenceFixture(values, firstCommitSucceeds = false)
        assertThrows(IOException::class.java) {
            KiyoriArtifactStoragePolicy.persistRoots(prefs, KiyoriArtifactStoragePolicy.Roots("/new/android", "/new/linux"))
        }
        assertEquals(mapOf("android_root" to "/old/android", "linux_root" to "/old/linux"), values)
    }

    @Test fun failedFirstSaveRestoresAbsentKeysInsteadOfPersistingNewDefaults() {
        val values = mutableMapOf<String, String>()
        val prefs = preferenceFixture(values, firstCommitSucceeds = false)
        assertThrows(IOException::class.java) {
            KiyoriArtifactStoragePolicy.persistRoots(prefs, KiyoriArtifactStoragePolicy.Roots("/new/android", "/new/linux"))
        }
        assertEquals(emptyMap<String, String>(), values)
    }

    @Test fun successfulSavePublishesBothRootsWithoutRollback() {
        val values = mutableMapOf<String, String>()
        val prefs = preferenceFixture(values, firstCommitSucceeds = true)
        KiyoriArtifactStoragePolicy.persistRoots(prefs, KiyoriArtifactStoragePolicy.Roots("/new/android", "/new/linux"))
        assertEquals(mapOf("android_root" to "/new/android", "linux_root" to "/new/linux"), values)
        verify(prefs, times(1)).edit()
    }

    private fun preferenceFixture(values: MutableMap<String, String>, firstCommitSucceeds: Boolean): SharedPreferences {
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(prefs.all).thenAnswer { values.toMap() }
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument(1)
            editor
        }
        `when`(editor.remove(anyString())).thenAnswer { values.remove(it.getArgument<String>(0)); editor }
        `when`(editor.commit()).thenReturn(firstCommitSucceeds, true)
        return prefs
    }
}
