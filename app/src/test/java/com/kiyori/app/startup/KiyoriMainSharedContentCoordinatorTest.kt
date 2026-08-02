package com.kiyori.app.startup

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock

class KiyoriMainSharedContentCoordinatorTest {
    @Test
    fun `pending files defer standalone shared text transfer`() {
        val uri = mock<Uri>()

        assertNull(
            resolvePendingSharedText(
                sharedFileUris = listOf(uri),
                sharedText = " shared text ",
            )
        )
    }

    @Test
    fun `standalone shared text is trimmed and blank text is ignored`() {
        assertEquals(
            "shared text",
            resolvePendingSharedText(
                sharedFileUris = null,
                sharedText = " shared text ",
            ),
        )
        assertNull(
            resolvePendingSharedText(
                sharedFileUris = null,
                sharedText = " ",
            )
        )
        assertNull(
            resolvePendingSharedText(
                sharedFileUris = null,
                sharedText = null,
            )
        )
    }

    @Test
    fun `shared files preserve URI list identity and trim attached text`() {
        val uri = mock<Uri>()
        val uris = listOf(uri)

        val content =
            resolvePendingSharedFiles(
                sharedFileUris = uris,
                sharedText = " attached text ",
            )

        requireNotNull(content)
        assertSame(uris, content.uris)
        assertEquals("attached text", content.text)
        assertNull(
            resolvePendingSharedFiles(
                sharedFileUris = null,
                sharedText = "text",
            )
        )
    }
}
