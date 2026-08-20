package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import okio.Buffer
import java.io.EOFException

class BrowserAdBlockSubscriptionCatalogTest {
    @Test
    fun `catalog contains five enabled built-ins in the requested groups`() {
        val merged = mergeBrowserAdBlockBuiltInSubscriptions(emptyList())

        assertEquals(5, BrowserAdBlockBuiltInSubscriptions.size)
        assertEquals(5, merged.size)
        assertEquals(
            3,
            merged.count { subscription ->
                subscription.group == BrowserAdBlockSubscriptionGroup.PRO
            },
        )
        assertEquals(
            2,
            merged.count { subscription ->
                subscription.group == BrowserAdBlockSubscriptionGroup.ADBLOCK_PLUS
            },
        )
        assertTrue(merged.all(BrowserAdBlockSubscription::builtIn))
        assertTrue(merged.all(BrowserAdBlockSubscription::enabled))
        assertEquals(
            BrowserAdBlockBuiltInSubscriptions.map { definition -> definition.url },
            merged.map(BrowserAdBlockSubscription::url),
        )
    }

    @Test
    fun `catalog merge preserves disabled built-in state and custom subscriptions`() {
        val disabledByUrl =
            subscription(
                id = "legacy-id",
                url = BrowserAdBlockBuiltInSubscriptions.first().url,
                enabled = false,
            )
        val custom =
            subscription(
                id = "custom-id",
                url = "https://example.org/custom.txt",
                enabled = true,
            )

        val merged =
            mergeBrowserAdBlockBuiltInSubscriptions(
                listOf(disabledByUrl, custom),
            )

        assertFalse(merged.first().enabled)
        assertEquals(BrowserAdBlockBuiltInSubscriptions.first().id, merged.first().id)
        assertEquals(custom.id, merged.last().id)
        assertEquals(BrowserAdBlockSubscriptionGroup.CUSTOM, merged.last().group)
        assertFalse(merged.last().builtIn)
    }

    @Test
    fun `payload metadata is committed only when parsed rule counts are present`() {
        val empty = subscription(id = "empty", url = "https://example.org/empty.txt", enabled = true)
        val committed =
            empty.copy(
                lastUpdatedAt = 123L,
                networkBlockingRuleCount = 1,
                payloadSha256 = "a".repeat(64),
                payloadByteCount = 128L,
                payloadStorageVersion = BrowserAdBlockCompilerContract.PAYLOAD_STORAGE_VERSION,
            )

        assertFalse(empty.hasCommittedPayloadMetadata)
        assertTrue(committed.hasCommittedPayloadMetadata)
        assertTrue(
            committed.matchesCommittedPayload(
                payloadSha256 = "a".repeat(64),
                payloadByteCount = 128L,
            ),
        )
        assertFalse(
            committed.matchesCommittedPayload(
                payloadSha256 = "b".repeat(64),
                payloadByteCount = 128L,
            ),
        )
    }

    @Test
    fun `refresh merge preserves current name and enabled state while enforcing the original url`() {
        val current =
            subscription(
                id = "custom-id",
                url = "https://example.org/list.txt",
                enabled = false,
            ).copy(name = "Renamed while refreshing")
        val parsed =
            BrowserAdBlockSubscriptionParseResult(
                networkRules =
                    listOf(
                        BrowserAdBlockNetworkRuleSpec(
                            id = "custom-id:network:0",
                            rule = "||ads.example.org^",
                            source = BrowserAdBlockRuleSource.SUBSCRIPTION,
                            sourceName = "Old name",
                        ),
                    ),
                elementRules = emptyList(),
                ignoredLineCount = 2,
            )

        val merged =
            mergeBrowserAdBlockRefreshedSubscription(
                current = current,
                expectedUrl = current.url,
                expectedName = current.name,
                counts = parsed.toRuleCounts(),
                updatedAt = 456L,
                payloadSha256 = "b".repeat(64),
                payloadByteCount = 256L,
            )

        assertEquals("Renamed while refreshing", merged.name)
        assertFalse(merged.enabled)
        assertEquals(456L, merged.lastUpdatedAt)
        assertEquals(1, merged.networkBlockingRuleCount)
        assertEquals(0, merged.networkExceptionRuleCount)
        assertTrue(merged.hasLocalRules)
        assertEquals(2, merged.ignoredLineCount)
        val committed =
            mergeBrowserAdBlockPreparedSubscriptionAtCommit(
                current = current.copy(enabled = true),
                expectedUrl = current.url,
                expectedName = current.name,
                prepared = merged.copy(enabled = false),
            )
        assertTrue(committed.enabled)
        assertEquals(merged.payloadSha256, committed.payloadSha256)
        assertThrows(IllegalArgumentException::class.java) {
            mergeBrowserAdBlockRefreshedSubscription(
                current = current.copy(url = "https://example.org/new.txt"),
                expectedUrl = current.url,
                expectedName = current.name,
                counts = parsed.toRuleCounts(),
                updatedAt = 789L,
                payloadSha256 = "c".repeat(64),
                payloadByteCount = 512L,
            )
        }
    }

    @Test
    fun `unchanged payload refresh keeps the rule revision and compiled identity`() {
        val subscription =
            subscription(
                id = "same-content",
                url = "https://example.org/list.txt",
                enabled = true,
            ).copy(
                lastUpdatedAt = 100L,
                lastError = "previous error",
                networkBlockingRuleCount = 1,
                payloadSha256 = "d".repeat(64),
                payloadByteCount = 1024L,
                payloadStorageVersion = BrowserAdBlockCompilerContract.PAYLOAD_STORAGE_VERSION,
            )
        val state =
            BrowserAdBlockState(
                subscriptions = listOf(subscription),
                ruleRevision = 17L,
            )

        val commit =
            browserAdBlockUnchangedRefreshCommit(
                currentState = state,
                id = subscription.id,
                expectedUrl = subscription.url,
                expectedName = subscription.name,
                updatedAt = 200L,
            )

        assertEquals(17L, commit.state.ruleRevision)
        assertEquals(200L, commit.subscription.lastUpdatedAt)
        assertEquals(null, commit.subscription.lastError)
        assertEquals(subscription.payloadSha256, commit.subscription.payloadSha256)
        assertEquals(subscription.payloadByteCount, commit.subscription.payloadByteCount)
    }

    @Test
    fun `bounded subscription reader accepts a normal short response until EOF`() {
        val source =
            Buffer().writeUtf8(
                "[Adblock Plus 2.0]\n||ads.example.org^\n",
            )

        val bytes =
            readBrowserAdBlockSubscriptionPayload(
                source = source,
                declaredLength = -1L,
                maxBytes = 64L,
            )

        assertEquals(
            "[Adblock Plus 2.0]\n||ads.example.org^\n",
            bytes.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun `bounded subscription reader accepts payload exactly at the limit`() {
        val source = Buffer().writeUtf8("12345678")

        val bytes =
            readBrowserAdBlockSubscriptionPayload(
                source = source,
                declaredLength = 8L,
                maxBytes = 8L,
            )

        assertEquals("12345678", bytes.toString(Charsets.UTF_8))
    }

    @Test
    fun `bounded subscription reader rejects declared and streamed payloads above the limit`() {
        assertThrows(IllegalStateException::class.java) {
            readBrowserAdBlockSubscriptionPayload(
                source = Buffer().writeUtf8("123456789"),
                declaredLength = 9L,
                maxBytes = 8L,
            )
        }
        assertThrows(IllegalStateException::class.java) {
            readBrowserAdBlockSubscriptionPayload(
                source = Buffer().writeUtf8("123456789"),
                declaredLength = -1L,
                maxBytes = 8L,
            )
        }
    }

    @Test
    fun `subscription payload validation rejects html responses and maps transport errors`() {
        assertThrows(IllegalStateException::class.java) {
            validateBrowserAdBlockSubscriptionPayload(
                "<!doctype html><html></html>".toByteArray(),
            )
        }
        assertEquals(
            "订阅内容传输未完成",
            browserAdBlockSubscriptionRefreshErrorMessage(EOFException()),
        )
        assertEquals(
            "订阅服务器返回 HTTP 503",
            browserAdBlockSubscriptionRefreshErrorMessage(
                IllegalStateException("订阅服务器返回 HTTP 503"),
            ),
        )
    }

    private fun subscription(
        id: String,
        url: String,
        enabled: Boolean,
    ): BrowserAdBlockSubscription =
        BrowserAdBlockSubscription(
            id = id,
            name = id,
            url = url,
            enabled = enabled,
            lastUpdatedAt = null,
            lastError = null,
            ignoredLineCount = 0,
        )
}
