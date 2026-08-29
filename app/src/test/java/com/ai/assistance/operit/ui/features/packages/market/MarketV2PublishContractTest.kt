package com.ai.assistance.operit.ui.features.packages.market

import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2NewVersionEntryPatch
import com.ai.assistance.operit.data.api.MarketV2NewVersionRequest
import com.ai.assistance.operit.data.api.MarketV2PublishVersion
import com.ai.assistance.operit.ui.features.packages.screens.toArtifactPublishClusterContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketV2PublishContractTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    @Test
    fun `contributor patch serializes only mutable description fields`() {
        val encoded =
            encodeRequest(
                MarketV2NewVersionEntryPatch(
                    description = "Updated summary",
                    detail = "Updated detail"
                )
            )
        val entry = encoded.jsonObject.getValue("entry").jsonObject

        assertEquals(setOf("description", "detail"), entry.keys)
        assertEquals("Updated summary", entry.getValue("description").jsonPrimitive.content)
        assertEquals("Updated detail", entry.getValue("detail").jsonPrimitive.content)
    }

    @Test
    fun `owner patch serializes each changed entry field`() {
        val encoded =
            encodeRequest(
                MarketV2NewVersionEntryPatch(
                    title = "New title",
                    description = "New summary",
                    detail = "New detail",
                    categoryId = "tools",
                    allowPublicUpdates = false
                )
            )
        val entry = encoded.jsonObject.getValue("entry").jsonObject

        assertEquals(
            setOf("title", "description", "detail", "categoryId", "allowPublicUpdates"),
            entry.keys
        )
        assertFalse(entry.getValue("allowPublicUpdates").jsonPrimitive.boolean)
    }

    @Test
    fun `unchanged entry omits patch object from new version request`() {
        val encoded = encodeRequest(entryPatch = null)

        assertFalse(encoded.jsonObject.containsKey("entry"))
        assertTrue(encoded.jsonObject.containsKey("version"))
    }

    @Test
    fun `market entry decodes server provided logo URL`() {
        val entry =
            json.decodeFromString<MarketV2Entry>(
                """{"id":"entry-1","title":"Logo","logoUrl":"https://static.operit.app/logo.webp"}"""
            )

        assertEquals("https://static.operit.app/logo.webp", entry.logoUrl)
    }

    @Test
    fun `artifact continuation keeps original market fields as patch baseline`() {
        val context = MarketV2Entry(
            id = "entry-1",
            title = "Title",
            description = "Summary",
            detail = "Detail",
            categoryId = "tools",
            allowPublicUpdates = false,
        ).toArtifactPublishClusterContext(canEditEntry = false)

        assertEquals("Summary", context.marketDescription)
        assertEquals("Detail", context.marketDetail)
        assertFalse(context.marketAllowPublicUpdates)
        assertFalse(context.canEditEntry)
    }

    @Test
    fun `owner continuation emits every changed mutable entry field`() {
        val context = publishContext(canEditEntry = true)
        val patch = buildNewVersionEntryPatch(
            payload = registrationPayload(
                displayName = "New title",
                description = "New summary",
                detail = "New detail",
                categoryId = "new-category",
                allowPublicUpdates = false,
            ),
            publishContext = context,
        )

        requireNotNull(patch)
        assertEquals("New title", patch.title)
        assertEquals("New summary", patch.description)
        assertEquals("New detail", patch.detail)
        assertEquals("new-category", patch.categoryId)
        assertFalse(requireNotNull(patch.allowPublicUpdates))
    }

    @Test
    fun `contributor continuation cannot emit owner-only fields`() {
        val patch = buildNewVersionEntryPatch(
            payload = registrationPayload(
                displayName = "Attempted title",
                description = "New summary",
                detail = "New detail",
                categoryId = "attempted-category",
                allowPublicUpdates = false,
            ),
            publishContext = publishContext(canEditEntry = false),
        )

        requireNotNull(patch)
        assertEquals("New summary", patch.description)
        assertEquals("New detail", patch.detail)
        assertEquals(null, patch.title)
        assertEquals(null, patch.categoryId)
        assertEquals(null, patch.allowPublicUpdates)
    }

    private fun publishContext(canEditEntry: Boolean) =
        ArtifactPublishClusterContext(
            entryId = "entry-1",
            projectId = "project-1",
            runtimePackageId = "package-1",
            lockedDisplayName = "Original title",
            projectDisplayName = "Original title",
            projectDescription = "Original detail",
            marketDescription = "Original summary",
            marketDetail = "Original detail",
            marketAllowPublicUpdates = true,
            categoryId = "original-category",
            canEditEntry = canEditEntry,
        )

    private fun registrationPayload(
        displayName: String,
        description: String,
        detail: String,
        categoryId: String,
        allowPublicUpdates: Boolean,
    ) = MarketRegistrationPayload(
        type = PublishArtifactType.PACKAGE,
        projectId = "project-1",
        projectDisplayName = displayName,
        projectDescription = detail,
        runtimePackageId = "package-1",
        publisherLogin = "publisher",
        releaseOwner = "owner",
        releaseRepository = "repo",
        releaseTag = "v1.0.1",
        assetName = "package-1-v1.0.1.toolpkg",
        downloadUrl = "https://example.test/package.toolpkg",
        sha256 = "sha256",
        version = "1.0.1",
        displayName = displayName,
        description = description,
        detail = detail,
        categoryId = categoryId,
        allowPublicUpdates = allowPublicUpdates,
        sourceFileName = "package.toolpkg",
        minSupportedAppVersion = "1.12.1+3",
        maxSupportedAppVersion = "1.99.99",
    )

    private fun encodeRequest(entryPatch: MarketV2NewVersionEntryPatch?) =
        json.parseToJsonElement(
            json.encodeToString(
                MarketV2NewVersionRequest(
                    entry = entryPatch,
                    version =
                        MarketV2PublishVersion(
                            version = "1.0.1",
                            formatVer = "2",
                            minAppVer = "1.12.1+3"
                        )
                )
            )
        )
}
