package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAdBlockCompiledCacheTest {
    @Test
    fun `compiler contract fixes every resource type code`() {
        assertEquals("kiyori-adblock-compiled-v1", BrowserAdBlockCompilerContract.COMPILER_CONTRACT_ID)
        BrowserAdBlockResourceType.entries.forEachIndexed { expectedCode, type ->
            assertEquals(expectedCode, BrowserAdBlockCompilerContract.resourceTypeCode(type))
            assertEquals(type, BrowserAdBlockCompilerContract.resourceTypeFromCode(expectedCode))
        }
    }

    @Test
    fun `compiled cache is deterministic and preserves matcher semantics`() {
        val fixture = fixture()
        val directory = Files.createTempDirectory("kiyori-adblock-cache").toFile()
        val first = directory.resolve("first.bin")
        val second = directory.resolve("second.bin")
        try {
            BrowserAdBlockCompiledCacheCodec.write(
                target = first,
                identity = fixture.identity,
                partition = fixture.partition,
            )
            BrowserAdBlockCompiledCacheCodec.write(
                target = second,
                identity = fixture.identity,
                partition = fixture.partition,
            )

            assertArrayEquals(first.readBytes(), second.readBytes())
            val restored =
                BrowserAdBlockCompiledCacheCodec.read(
                    source = first,
                    expectedIdentity = fixture.identity,
                )
            assertEquals(
                fixture.partition.networkIndexSnapshot,
                restored.networkIndexSnapshot,
            )
            assertEquals(
                fixture.partition.elementIndexSnapshot,
                restored.elementIndexSnapshot,
            )
            assertEquals(
                fixture.partition.ruleSet.badFilters,
                restored.ruleSet.badFilters,
            )
            val originalMatcher =
                fixture.partition.engine.createMatcher(
                    enabled = true,
                    allowlistedDomains = emptySet(),
                    activeRuleSetIds = setOf(fixture.identity.subscriptionId),
                )
            val restoredMatcher =
                restored.engine.createMatcher(
                    enabled = true,
                    allowlistedDomains = emptySet(),
                    activeRuleSetIds = setOf(fixture.identity.subscriptionId),
                )
            val contexts =
                listOf(
                    BrowserAdBlockRequestContext(
                        pageUrl = "https://news.example/article",
                        requestUrl = "https://ads.example/banner.js",
                        resourceType = BrowserAdBlockResourceType.SCRIPT,
                    ),
                    BrowserAdBlockRequestContext(
                        pageUrl = "https://allowed.example/article",
                        requestUrl = "https://ads.example/banner.js",
                        resourceType = BrowserAdBlockResourceType.SCRIPT,
                    ),
                    BrowserAdBlockRequestContext(
                        pageUrl = "https://news.example/article",
                        requestUrl = "https://cdn.example/tracker-42.gif",
                        resourceType = BrowserAdBlockResourceType.IMAGE,
                    ),
                    BrowserAdBlockRequestContext(
                        pageUrl = "https://news.example/article",
                        requestUrl = "https://metrics.example/pixel",
                        resourceType = BrowserAdBlockResourceType.IMAGE,
                    ),
                )
            contexts.forEach { context ->
                assertEquals(
                    originalMatcher.decide(context),
                    restoredMatcher.decide(context),
                )
            }
            listOf(
                "https://news.example/article",
                "https://shop.example/product",
            ).forEach { pageUrl ->
                assertEquals(
                    originalMatcher.elementDecisionsForPage(pageUrl),
                    restoredMatcher.elementDecisionsForPage(pageUrl),
                )
            }
        } finally {
            first.delete()
            second.delete()
            directory.delete()
        }
    }

    @Test
    fun `compiled cache rejects identity mismatch truncation and body corruption`() {
        val fixture = fixture()
        val directory = Files.createTempDirectory("kiyori-adblock-invalid").toFile()
        val valid = directory.resolve("valid.bin")
        val truncated = directory.resolve("truncated.bin")
        val corrupted = directory.resolve("corrupted.bin")
        try {
            BrowserAdBlockCompiledCacheCodec.write(
                target = valid,
                identity = fixture.identity,
                partition = fixture.partition,
            )
            assertThrows(BrowserAdBlockCompiledCacheException::class.java) {
                BrowserAdBlockCompiledCacheCodec.read(
                    source = valid,
                    expectedIdentity =
                        fixture.identity.copy(
                            payloadSha256 = "0".repeat(64),
                        ),
                )
            }

            val validBytes = valid.readBytes()
            truncated.writeBytes(validBytes.copyOf(validBytes.size - 7))
            assertThrows(BrowserAdBlockCompiledCacheException::class.java) {
                BrowserAdBlockCompiledCacheCodec.read(
                    source = truncated,
                    expectedIdentity = fixture.identity,
                )
            }

            val corruptedBytes = validBytes.copyOf()
            corruptedBytes[corruptedBytes.lastIndex] =
                (corruptedBytes.last().toInt() xor 0x01).toByte()
            corrupted.writeBytes(corruptedBytes)
            assertThrows(BrowserAdBlockCompiledCacheException::class.java) {
                BrowserAdBlockCompiledCacheCodec.read(
                    source = corrupted,
                    expectedIdentity = fixture.identity,
                )
            }
        } finally {
            valid.delete()
            truncated.delete()
            corrupted.delete()
            directory.delete()
        }
    }

    @Test
    fun `compiled cache keeps explicit regex and cross rule decisions`() {
        val fixture = fixture()
        val directory = Files.createTempDirectory("kiyori-adblock-regex").toFile()
        val cache = directory.resolve("partition.bin")
        try {
            BrowserAdBlockCompiledCacheCodec.write(
                target = cache,
                identity = fixture.identity,
                partition = fixture.partition,
            )
            val restored =
                BrowserAdBlockCompiledCacheCodec.read(
                    source = cache,
                    expectedIdentity = fixture.identity,
                )
            val matcher =
                restored.engine.createMatcher(
                    enabled = true,
                    allowlistedDomains = emptySet(),
                    activeRuleSetIds = setOf(fixture.identity.subscriptionId),
                )

            assertNotNull(
                matcher.decide(
                    BrowserAdBlockRequestContext(
                        pageUrl = "https://news.example/",
                        requestUrl = "https://cdn.example/tracker-42.gif",
                        resourceType = BrowserAdBlockResourceType.IMAGE,
                    ),
                ),
            )
            assertNull(
                matcher.decide(
                    BrowserAdBlockRequestContext(
                        pageUrl = "https://allowed.example/",
                        requestUrl = "https://ads.example/banner.js",
                        resourceType = BrowserAdBlockResourceType.SCRIPT,
                    ),
                ),
            )
            assertTrue(
                matcher
                    .selectorsForPage("https://news.example/article")
                    .contains(".sponsored-card"),
            )
        } finally {
            cache.delete()
            directory.delete()
        }
    }

    private fun fixture(): CacheFixture {
        val payload =
            """
            [Adblock Plus 2.0]
            ||ads.example^${'$'}script,third-party
            @@||ads.example^${'$'}domain=allowed.example
            /tracker-[0-9]+\.gif/${'$'}image
            ||metrics.example^${'$'}badfilter
            news.example##.sponsored-card
            shop.example##.sponsored-card
            news.example#@#.native-promo
            """.trimIndent().toByteArray()
        val subscriptionId = "fixture-subscription"
        val subscriptionName = "Fixture subscription"
        val parsed =
            parseBrowserAdBlockSubscription(
                text = payload.toString(Charsets.UTF_8),
                subscriptionId = subscriptionId,
                subscriptionName = subscriptionName,
            )
        val ruleSet =
            BrowserAdBlockCompiledRuleSet.compile(
                id = subscriptionId,
                networkRules = parsed.networkRules,
                elementRules = parsed.elementRules,
            )
        val partition = compileBrowserAdBlockCompiledPartition(ruleSet)
        val identity =
            BrowserAdBlockCompiledCacheIdentity(
                subscriptionId = subscriptionId,
                subscriptionName = subscriptionName,
                payloadSha256 = browserAdBlockSha256(payload),
                payloadByteCount = payload.size.toLong(),
                networkBlockingRuleCount =
                    parsed.networkRules.count { rule -> !rule.rule.startsWith("@@") },
                networkExceptionRuleCount =
                    parsed.networkRules.count { rule -> rule.rule.startsWith("@@") },
                elementBlockingRuleCount =
                    parsed.elementRules.count { rule -> !rule.exception },
                elementExceptionRuleCount =
                    parsed.elementRules.count(BrowserAdBlockElementRuleSpec::exception),
            )
        return CacheFixture(
            identity = identity,
            partition = partition,
        )
    }

    private data class CacheFixture(
        val identity: BrowserAdBlockCompiledCacheIdentity,
        val partition: BrowserAdBlockCompiledPartition,
    )
}
