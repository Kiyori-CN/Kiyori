package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.Mockito

class EnvPreferencesBatchTest {
    @Test
    fun connectionFieldsPublishTogetherWithoutClearingOtherPackages() {
        val stored = mutableMapOf("url" to "old-host", "token" to "old-token", "other" to "keep", "obsolete" to "remove")
        val pending = mutableMapOf<String, String?>()
        val snapshots = mutableListOf<Map<String, String>>()
        val context = Mockito.mock(Context::class.java)
        val prefs = Mockito.mock(SharedPreferences::class.java)
        val editor = Mockito.mock(SharedPreferences.Editor::class.java)
        Mockito.`when`(context.applicationContext).thenReturn(context)
        Mockito.`when`(context.getSharedPreferences("env_preferences", Context.MODE_PRIVATE)).thenReturn(prefs)
        Mockito.`when`(prefs.edit()).thenReturn(editor)
        Mockito.`when`(editor.putString(Mockito.anyString(), Mockito.anyString())).thenAnswer {
            pending[it.getArgument(0)] = it.getArgument(1)
            assertEquals("old-host", stored["url"])
            assertEquals("old-token", stored["token"])
            editor
        }
        Mockito.`when`(editor.remove(Mockito.anyString())).thenAnswer {
            pending[it.getArgument(0)] = null
            editor
        }
        Mockito.doAnswer {
            pending.forEach { (key, value) -> if (value == null) stored.remove(key) else stored[key] = value }
            snapshots += stored.toMap()
            null
        }.`when`(editor).apply()
        val constructor = EnvPreferences::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
        constructor.newInstance(context).updateEnvs(mapOf("url" to "new-host", "token" to "new-token", "obsolete" to ""))
        assertEquals(1, snapshots.size)
        assertEquals("new-host", stored["url"])
        assertEquals("new-token", stored["token"])
        assertEquals("keep", stored["other"])
        assertFalse(stored.containsKey("obsolete"))
        Mockito.verify(editor, Mockito.never()).clear()
    }
}
