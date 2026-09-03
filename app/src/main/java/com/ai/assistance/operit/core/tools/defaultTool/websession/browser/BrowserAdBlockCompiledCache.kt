package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.UUID

/**
 * 编译合同独立于应用版本。只有规则语义、固定枚举编号或索引算法变化时才更新合同，
 * 否则与广告拦截无关的应用升级会无意义地重建五份大型订阅。
 */
internal object BrowserAdBlockCompilerContract {
    const val CACHE_FORMAT_VERSION = 1
    const val PAYLOAD_STORAGE_VERSION = 1
    const val COMPILER_CONTRACT_ID = "kiyori-adblock-compiled-v1"

    const val MIN_INDEX_KEY_LENGTH = 4
    const val MAX_INDEX_KEY_LENGTH = 8
    const val TOKEN_HASH_BASE = 257L

    fun resourceTypeCode(type: BrowserAdBlockResourceType): Int =
        when (type) {
            BrowserAdBlockResourceType.DOCUMENT -> 0
            BrowserAdBlockResourceType.SUBDOCUMENT -> 1
            BrowserAdBlockResourceType.SCRIPT -> 2
            BrowserAdBlockResourceType.STYLESHEET -> 3
            BrowserAdBlockResourceType.IMAGE -> 4
            BrowserAdBlockResourceType.MEDIA -> 5
            BrowserAdBlockResourceType.FONT -> 6
            BrowserAdBlockResourceType.OBJECT -> 7
            BrowserAdBlockResourceType.XMLHTTPREQUEST -> 8
            BrowserAdBlockResourceType.WEBSOCKET -> 9
            BrowserAdBlockResourceType.PING -> 10
            BrowserAdBlockResourceType.OTHER -> 11
        }

    fun resourceTypeFromCode(code: Int): BrowserAdBlockResourceType =
        when (code) {
            0 -> BrowserAdBlockResourceType.DOCUMENT
            1 -> BrowserAdBlockResourceType.SUBDOCUMENT
            2 -> BrowserAdBlockResourceType.SCRIPT
            3 -> BrowserAdBlockResourceType.STYLESHEET
            4 -> BrowserAdBlockResourceType.IMAGE
            5 -> BrowserAdBlockResourceType.MEDIA
            6 -> BrowserAdBlockResourceType.FONT
            7 -> BrowserAdBlockResourceType.OBJECT
            8 -> BrowserAdBlockResourceType.XMLHTTPREQUEST
            9 -> BrowserAdBlockResourceType.WEBSOCKET
            10 -> BrowserAdBlockResourceType.PING
            11 -> BrowserAdBlockResourceType.OTHER
            else -> throw BrowserAdBlockCompiledCacheException("resource_type_code")
        }
}

internal data class BrowserAdBlockCompiledCacheIdentity(
    val subscriptionId: String,
    val subscriptionName: String,
    val payloadSha256: String,
    val payloadByteCount: Long,
    val networkBlockingRuleCount: Int,
    val networkExceptionRuleCount: Int,
    val elementBlockingRuleCount: Int,
    val elementExceptionRuleCount: Int,
) {
    init {
        require(BROWSER_AD_BLOCK_CACHE_ID_REGEX.matches(subscriptionId)) {
            "Invalid browser ad-block cache subscription ID"
        }
        require(subscriptionName.isNotBlank()) {
            "Browser ad-block cache subscription name is blank"
        }
        require(BROWSER_AD_BLOCK_SHA_256_REGEX.matches(payloadSha256)) {
            "Invalid browser ad-block payload SHA-256"
        }
        require(payloadByteCount in 1..BROWSER_AD_BLOCK_CACHE_MAX_PAYLOAD_BYTES) {
            "Invalid browser ad-block payload byte count"
        }
        require(
            listOf(
                networkBlockingRuleCount,
                networkExceptionRuleCount,
                elementBlockingRuleCount,
                elementExceptionRuleCount,
            ).all { count -> count >= 0 },
        ) {
            "Invalid browser ad-block rule count"
        }
    }
}

internal data class BrowserAdBlockNetworkIndexSnapshot(
    val requiresPartyClassification: Boolean,
    val hostRuleIndexes: Map<String, List<Int>>,
    val tokenRuleIndexes: Map<Int, Map<Long, List<Int>>>,
    val unindexedRuleIndexes: List<Int>,
)

internal data class BrowserAdBlockElementIndexSnapshot(
    val genericRuleIndexes: List<Int>,
    val domainRuleIndexes: Map<String, List<Int>>,
)

internal data class BrowserAdBlockCompiledPartition(
    val ruleSet: BrowserAdBlockCompiledRuleSet,
    val engine: BrowserAdBlockEngine,
    val networkIndexSnapshot: BrowserAdBlockNetworkIndexSnapshot,
    val elementIndexSnapshot: BrowserAdBlockElementIndexSnapshot,
)

internal class BrowserAdBlockCompiledCacheException(
    val invalidReason: String,
    cause: Throwable? = null,
) : IllegalStateException("Invalid browser ad-block compiled cache: $invalidReason", cause)

internal object BrowserAdBlockCompiledCacheCodec {
    fun write(
        target: File,
        identity: BrowserAdBlockCompiledCacheIdentity,
        partition: BrowserAdBlockCompiledPartition,
    ) {
        require(partition.ruleSet.id == identity.subscriptionId) {
            "Compiled partition does not belong to the requested subscription"
        }
        validateCompiledPartition(partition)
        val parent =
            requireNotNull(target.parentFile) {
                "Browser ad-block compiled cache target has no parent"
            }
        require(parent.exists() || parent.mkdirs()) {
            "Cannot create browser ad-block compiled cache directory: ${parent.absolutePath}"
        }
        val nonce = UUID.randomUUID().toString()
        val bodyFile = File(parent, ".${target.name}.$nonce.body")
        val completeFile = File(parent, ".${target.name}.$nonce.complete")
        try {
            // 最大订阅的快照可能达到数十 MiB。先流式生成正文并取得长度/摘要，再拼接文件头，
            // 可避免在首次编译的内存峰值上再复制一份完整 ByteArray。
            val bodyDigest = MessageDigest.getInstance(BROWSER_AD_BLOCK_CACHE_DIGEST_ALGORITHM)
            val bodyOutput = FileOutputStream(bodyFile)
            val bodyCounter = CountingOutputStream(bodyOutput)
            val digestOutput = DigestOutputStream(bodyCounter, bodyDigest)
            DataOutputStream(BufferedOutputStream(digestOutput)).use { output ->
                writeBody(output, partition)
                output.flush()
                bodyOutput.fd.sync()
            }
            val bodyByteCount = bodyCounter.byteCount
            require(bodyByteCount in 1..BROWSER_AD_BLOCK_CACHE_MAX_BODY_BYTES) {
                "Browser ad-block compiled cache body is too large"
            }
            val bodySha256 = bodyDigest.digest()

            val finalOutput = FileOutputStream(completeFile)
            DataOutputStream(BufferedOutputStream(finalOutput)).use { output ->
                output.write(BROWSER_AD_BLOCK_CACHE_MAGIC)
                output.writeInt(BrowserAdBlockCompilerContract.CACHE_FORMAT_VERSION)
                writeString(
                    output,
                    BrowserAdBlockCompilerContract.COMPILER_CONTRACT_ID,
                    BROWSER_AD_BLOCK_CACHE_MAX_CONTRACT_BYTES,
                )
                writeString(
                    output,
                    identity.subscriptionId,
                    BROWSER_AD_BLOCK_CACHE_MAX_ID_BYTES,
                )
                writeString(
                    output,
                    identity.subscriptionName,
                    BROWSER_AD_BLOCK_CACHE_MAX_NAME_BYTES,
                )
                writeString(
                    output,
                    identity.payloadSha256,
                    BROWSER_AD_BLOCK_CACHE_SHA_256_HEX_BYTES,
                )
                output.writeLong(identity.payloadByteCount)
                output.writeInt(identity.networkBlockingRuleCount)
                output.writeInt(identity.networkExceptionRuleCount)
                output.writeInt(identity.elementBlockingRuleCount)
                output.writeInt(identity.elementExceptionRuleCount)
                output.writeLong(bodyByteCount)
                output.write(bodySha256)
                FileInputStream(bodyFile).use { input ->
                    input.copyTo(output, BROWSER_AD_BLOCK_CACHE_COPY_BUFFER_BYTES)
                }
                output.flush()
                finalOutput.fd.sync()
            }
            require(completeFile.length() <= BROWSER_AD_BLOCK_CACHE_MAX_FILE_BYTES) {
                "Browser ad-block compiled cache file is too large"
            }
            java.nio.file.Files.move(
                completeFile.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            bodyFile.delete()
            completeFile.delete()
        }
    }

    fun read(
        source: File,
        expectedIdentity: BrowserAdBlockCompiledCacheIdentity,
    ): BrowserAdBlockCompiledPartition {
        if (!source.isFile) {
            throw BrowserAdBlockCompiledCacheException("missing")
        }
        if (source.length() !in 1..BROWSER_AD_BLOCK_CACHE_MAX_FILE_BYTES) {
            throw BrowserAdBlockCompiledCacheException("file_size")
        }
        try {
            val fileInput = FileInputStream(source)
            val counter = CountingInputStream(BufferedInputStream(fileInput))
            DataInputStream(counter).use { input ->
                val magic = ByteArray(BROWSER_AD_BLOCK_CACHE_MAGIC.size)
                input.readFully(magic)
                if (!magic.contentEquals(BROWSER_AD_BLOCK_CACHE_MAGIC)) {
                    throw BrowserAdBlockCompiledCacheException("magic")
                }
                if (input.readInt() != BrowserAdBlockCompilerContract.CACHE_FORMAT_VERSION) {
                    throw BrowserAdBlockCompiledCacheException("format_version")
                }
                if (
                    readString(input, BROWSER_AD_BLOCK_CACHE_MAX_CONTRACT_BYTES) !=
                        BrowserAdBlockCompilerContract.COMPILER_CONTRACT_ID
                ) {
                    throw BrowserAdBlockCompiledCacheException("compiler_contract")
                }
                if (
                    readString(input, BROWSER_AD_BLOCK_CACHE_MAX_ID_BYTES) !=
                        expectedIdentity.subscriptionId
                ) {
                    throw BrowserAdBlockCompiledCacheException("subscription_id")
                }
                if (
                    readString(input, BROWSER_AD_BLOCK_CACHE_MAX_NAME_BYTES) !=
                        expectedIdentity.subscriptionName
                ) {
                    throw BrowserAdBlockCompiledCacheException("subscription_name")
                }
                if (
                    readString(input, BROWSER_AD_BLOCK_CACHE_SHA_256_HEX_BYTES) !=
                        expectedIdentity.payloadSha256
                ) {
                    throw BrowserAdBlockCompiledCacheException("payload_sha256")
                }
                if (input.readLong() != expectedIdentity.payloadByteCount) {
                    throw BrowserAdBlockCompiledCacheException("payload_byte_count")
                }
                if (input.readInt() != expectedIdentity.networkBlockingRuleCount) {
                    throw BrowserAdBlockCompiledCacheException("network_blocking_count")
                }
                if (input.readInt() != expectedIdentity.networkExceptionRuleCount) {
                    throw BrowserAdBlockCompiledCacheException("network_exception_count")
                }
                if (input.readInt() != expectedIdentity.elementBlockingRuleCount) {
                    throw BrowserAdBlockCompiledCacheException("element_blocking_count")
                }
                if (input.readInt() != expectedIdentity.elementExceptionRuleCount) {
                    throw BrowserAdBlockCompiledCacheException("element_exception_count")
                }
                val bodyByteCount = input.readLong()
                if (bodyByteCount !in 1..BROWSER_AD_BLOCK_CACHE_MAX_BODY_BYTES) {
                    throw BrowserAdBlockCompiledCacheException("body_size")
                }
                val expectedBodySha256 = ByteArray(BROWSER_AD_BLOCK_CACHE_SHA_256_BYTES)
                input.readFully(expectedBodySha256)
                val remainingFileBytes = source.length() - counter.byteCount
                if (remainingFileBytes != bodyByteCount) {
                    throw BrowserAdBlockCompiledCacheException("body_length")
                }

                val limitedInput = LimitedInputStream(input, bodyByteCount)
                val bodyDigest = MessageDigest.getInstance(BROWSER_AD_BLOCK_CACHE_DIGEST_ALGORITHM)
                val digestInput = DigestInputStream(limitedInput, bodyDigest)
                val bodyInput = DataInputStream(BufferedInputStream(digestInput))
                val partition =
                    readBody(
                        input = bodyInput,
                        expectedRuleSetId = expectedIdentity.subscriptionId,
                    )
                if (limitedInput.remainingByteCount != 0L || bodyInput.read() != -1) {
                    throw BrowserAdBlockCompiledCacheException("body_trailing_bytes")
                }
                if (!bodyDigest.digest().contentEquals(expectedBodySha256)) {
                    throw BrowserAdBlockCompiledCacheException("body_sha256")
                }
                validateCompiledPartition(partition)
                return partition
            }
        } catch (error: BrowserAdBlockCompiledCacheException) {
            throw error
        } catch (error: Exception) {
            throw BrowserAdBlockCompiledCacheException("decode", error)
        }
    }

    private fun writeBody(
        output: DataOutputStream,
        partition: BrowserAdBlockCompiledPartition,
    ) {
        val ruleSet = partition.ruleSet
        writeString(output, ruleSet.id, BROWSER_AD_BLOCK_CACHE_MAX_ID_BYTES)
        writeCount(output, ruleSet.networkRules.size, BROWSER_AD_BLOCK_CACHE_MAX_NETWORK_RULES)
        ruleSet.networkRules.forEach { rule ->
            writeNetworkRule(output, rule)
        }
        writeCount(output, ruleSet.elementRules.size, BROWSER_AD_BLOCK_CACHE_MAX_ELEMENT_RULES)
        ruleSet.elementRules.forEach { rule ->
            writeElementRule(output, rule)
        }
        val sortedBadFilters =
            ruleSet.badFilters.sortedWith(
                compareBy<BrowserAdBlockBadFilter>(
                    { filter -> ruleSourceCode(filter.source) },
                    BrowserAdBlockBadFilter::canonicalRuleKey,
                ),
            )
        writeCount(output, sortedBadFilters.size, BROWSER_AD_BLOCK_CACHE_MAX_BAD_FILTERS)
        sortedBadFilters.forEach { filter ->
            output.writeByte(ruleSourceCode(filter.source))
            writeString(
                output,
                filter.canonicalRuleKey,
                BROWSER_AD_BLOCK_CACHE_MAX_RULE_BYTES,
            )
        }
        writeNetworkIndex(output, partition.networkIndexSnapshot)
        writeElementIndex(output, partition.elementIndexSnapshot)
    }

    private fun readBody(
        input: DataInputStream,
        expectedRuleSetId: String,
    ): BrowserAdBlockCompiledPartition {
        val domainInterner = BrowserAdBlockDomainInterner()
        val ruleSetId = readString(input, BROWSER_AD_BLOCK_CACHE_MAX_ID_BYTES)
        if (ruleSetId != expectedRuleSetId) {
            throw BrowserAdBlockCompiledCacheException("body_rule_set_id")
        }
        val networkRules =
            List(readCount(input, BROWSER_AD_BLOCK_CACHE_MAX_NETWORK_RULES)) {
                readNetworkRule(input, ruleSetId, domainInterner)
            }
        val elementRules =
            List(readCount(input, BROWSER_AD_BLOCK_CACHE_MAX_ELEMENT_RULES)) {
                readElementRule(input, ruleSetId, domainInterner)
            }
        val badFilters =
            LinkedHashSet<BrowserAdBlockBadFilter>().apply {
                repeat(readCount(input, BROWSER_AD_BLOCK_CACHE_MAX_BAD_FILTERS)) {
                    add(
                        BrowserAdBlockBadFilter(
                            ruleSetId = ruleSetId,
                            source = ruleSourceFromCode(input.readUnsignedByte()),
                            canonicalRuleKey =
                                readString(input, BROWSER_AD_BLOCK_CACHE_MAX_RULE_BYTES),
                        ),
                    )
                }
            }
        val networkIndex = readNetworkIndex(input, domainInterner)
        val elementIndex = readElementIndex(input, domainInterner)
        val ruleSet =
            BrowserAdBlockCompiledRuleSet.fromCompiled(
                id = ruleSetId,
                networkRules = networkRules,
                elementRules = elementRules,
                badFilters = badFilters,
            )
        return restoreBrowserAdBlockCompiledPartition(
            ruleSet = ruleSet,
            networkIndexSnapshot = networkIndex,
            elementIndexSnapshot = elementIndex,
        )
    }

    private fun writeNetworkRule(
        output: DataOutputStream,
        rule: CompiledBrowserAdBlockNetworkRule,
    ) {
        writeString(output, rule.spec.id, BROWSER_AD_BLOCK_CACHE_MAX_RULE_ID_BYTES)
        writeString(output, rule.spec.rule, BROWSER_AD_BLOCK_CACHE_MAX_RULE_BYTES)
        output.writeByte(ruleSourceCode(rule.spec.source))
        writeString(output, rule.spec.sourceName, BROWSER_AD_BLOCK_CACHE_MAX_NAME_BYTES)
        output.writeBoolean(rule.spec.enabled)
        output.writeBoolean(rule.exception)
        writeNullableString(output, rule.hostAnchor, BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_BYTES)
        writeStringCollection(
            output,
            rule.domainIncludes,
            BROWSER_AD_BLOCK_CACHE_MAX_DOMAINS_PER_RULE,
            BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_BYTES,
        )
        writeStringCollection(
            output,
            rule.domainExcludes,
            BROWSER_AD_BLOCK_CACHE_MAX_DOMAINS_PER_RULE,
            BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_BYTES,
        )
        output.writeInt(resourceTypeBits(rule.resourceIncludes))
        output.writeInt(resourceTypeBits(rule.resourceExcludes))
        output.writeByte(
            when (rule.thirdParty) {
                null -> 0
                false -> 1
                true -> 2
            },
        )
        writeStringCollection(
            output,
            rule.denyAllowDomains,
            BROWSER_AD_BLOCK_CACHE_MAX_DOMAINS_PER_RULE,
            BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_BYTES,
        )
        output.writeBoolean(rule.matchCase)
        output.writeBoolean(rule.important)
        output.writeBoolean(rule.generic)
        output.writeByte(pagePolicyBits(rule.pagePolicy))
        writeNullableString(output, rule.indexKey, BROWSER_AD_BLOCK_CACHE_MAX_INDEX_KEY_BYTES)
        writeNullableString(output, rule.literalPattern, BROWSER_AD_BLOCK_CACHE_MAX_RULE_BYTES)
        writeNullableString(output, rule.wildcardPattern, BROWSER_AD_BLOCK_CACHE_MAX_RULE_BYTES)
        writeNullableString(
            output,
            rule.patternRegex?.pattern,
            BROWSER_AD_BLOCK_CACHE_MAX_RULE_BYTES,
        )
        output.writeBoolean(rule.matchAll)
    }

    private fun readNetworkRule(
        input: DataInputStream,
        ruleSetId: String,
        domainInterner: BrowserAdBlockDomainInterner,
    ): CompiledBrowserAdBlockNetworkRule {
        val spec =
            BrowserAdBlockNetworkRuleSpec(
                id = readString(input, BROWSER_AD_BLOCK_CACHE_MAX_RULE_ID_BYTES),
                rule = readString(input, BROWSER_AD_BLOCK_CACHE_MAX_RULE_BYTES),
                source = ruleSourceFromCode(input.readUnsignedByte()),
                sourceName = readString(input, BROWSER_AD_BLOCK_CACHE_MAX_NAME_BYTES),
                enabled = input.readBoolean(),
            )
        val exception = input.readBoolean()
        val hostAnchor =
            readNullableString(input, BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_BYTES)
                ?.let(domainInterner::intern)
        val domainIncludes =
            readStringList(
                input,
                BROWSER_AD_BLOCK_CACHE_MAX_DOMAINS_PER_RULE,
                BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_BYTES,
                domainInterner,
            )
        val domainExcludes =
            readStringList(
                input,
                BROWSER_AD_BLOCK_CACHE_MAX_DOMAINS_PER_RULE,
                BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_BYTES,
                domainInterner,
            )
        val resourceIncludes = resourceTypesFromBits(input.readInt())
        val resourceExcludes = resourceTypesFromBits(input.readInt())
        val thirdParty =
            when (input.readUnsignedByte()) {
                0 -> null
                1 -> false
                2 -> true
                else -> throw BrowserAdBlockCompiledCacheException("third_party")
            }
        val denyAllowDomains =
            readStringList(
                input,
                BROWSER_AD_BLOCK_CACHE_MAX_DOMAINS_PER_RULE,
                BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_BYTES,
                domainInterner,
            )
        val matchCase = input.readBoolean()
        val important = input.readBoolean()
        val generic = input.readBoolean()
        val pagePolicy = pagePolicyFromBits(input.readUnsignedByte())
        val indexKey = readNullableString(input, BROWSER_AD_BLOCK_CACHE_MAX_INDEX_KEY_BYTES)
        val literalPattern = readNullableString(input, BROWSER_AD_BLOCK_CACHE_MAX_RULE_BYTES)
        val wildcardPattern = readNullableString(input, BROWSER_AD_BLOCK_CACHE_MAX_RULE_BYTES)
        val regexSource = readNullableString(input, BROWSER_AD_BLOCK_CACHE_MAX_RULE_BYTES)
        val patternRegex =
            regexSource?.let { source ->
                try {
                    Regex(
                        source,
                        if (matchCase) {
                            emptySet()
                        } else {
                            setOf(RegexOption.IGNORE_CASE)
                        },
                    )
                } catch (error: Exception) {
                    throw BrowserAdBlockCompiledCacheException("regex", error)
                }
            }
        return CompiledBrowserAdBlockNetworkRule(
            ruleSetId = ruleSetId,
            spec = spec,
            exception = exception,
            hostAnchor = hostAnchor,
            domainIncludes = domainIncludes,
            domainExcludes = domainExcludes,
            resourceIncludes = resourceIncludes,
            resourceExcludes = resourceExcludes,
            thirdParty = thirdParty,
            denyAllowDomains = denyAllowDomains,
            matchCase = matchCase,
            important = important,
            generic = generic,
            pagePolicy = pagePolicy,
            indexKey = indexKey,
            literalPattern = literalPattern,
            wildcardPattern = wildcardPattern,
            patternRegex = patternRegex,
            matchAll = input.readBoolean(),
        )
    }

    private fun writeElementRule(
        output: DataOutputStream,
        rule: CompiledBrowserAdBlockElementRule,
    ) {
        writeString(output, rule.spec.id, BROWSER_AD_BLOCK_CACHE_MAX_RULE_ID_BYTES)
        writeString(
            output,
            rule.spec.domainExpression,
            BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_EXPRESSION_BYTES,
        )
        writeString(output, rule.spec.selector, BROWSER_AD_BLOCK_CACHE_MAX_SELECTOR_BYTES)
        output.writeByte(ruleSourceCode(rule.spec.source))
        writeString(output, rule.spec.sourceName, BROWSER_AD_BLOCK_CACHE_MAX_NAME_BYTES)
        output.writeBoolean(rule.spec.exception)
        output.writeBoolean(rule.spec.enabled)
        writeString(output, rule.selector, BROWSER_AD_BLOCK_CACHE_MAX_SELECTOR_BYTES)
        output.writeBoolean(rule.exception)
        writeStringCollection(
            output,
            rule.domainIncludes,
            BROWSER_AD_BLOCK_CACHE_MAX_DOMAINS_PER_RULE,
            BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_BYTES,
        )
        writeStringCollection(
            output,
            rule.domainExcludes,
            BROWSER_AD_BLOCK_CACHE_MAX_DOMAINS_PER_RULE,
            BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_BYTES,
        )
        output.writeBoolean(rule.generic)
    }

    private fun readElementRule(
        input: DataInputStream,
        ruleSetId: String,
        domainInterner: BrowserAdBlockDomainInterner,
    ): CompiledBrowserAdBlockElementRule {
        val spec =
            BrowserAdBlockElementRuleSpec(
                id = readString(input, BROWSER_AD_BLOCK_CACHE_MAX_RULE_ID_BYTES),
                domainExpression =
                    readString(input, BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_EXPRESSION_BYTES),
                selector = readString(input, BROWSER_AD_BLOCK_CACHE_MAX_SELECTOR_BYTES),
                source = ruleSourceFromCode(input.readUnsignedByte()),
                sourceName = readString(input, BROWSER_AD_BLOCK_CACHE_MAX_NAME_BYTES),
                exception = input.readBoolean(),
                enabled = input.readBoolean(),
            )
        return CompiledBrowserAdBlockElementRule(
            ruleSetId = ruleSetId,
            spec = spec,
            selector = readString(input, BROWSER_AD_BLOCK_CACHE_MAX_SELECTOR_BYTES),
            exception = input.readBoolean(),
            domainIncludes =
                readStringList(
                    input,
                    BROWSER_AD_BLOCK_CACHE_MAX_DOMAINS_PER_RULE,
                    BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_BYTES,
                    domainInterner,
                ),
            domainExcludes =
                readStringList(
                    input,
                    BROWSER_AD_BLOCK_CACHE_MAX_DOMAINS_PER_RULE,
                    BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_BYTES,
                    domainInterner,
                ),
            generic = input.readBoolean(),
        )
    }

    private fun writeNetworkIndex(
        output: DataOutputStream,
        snapshot: BrowserAdBlockNetworkIndexSnapshot,
    ) {
        output.writeBoolean(snapshot.requiresPartyClassification)
        val hostEntries = snapshot.hostRuleIndexes.toSortedMap()
        writeCount(output, hostEntries.size, BROWSER_AD_BLOCK_CACHE_MAX_INDEX_BUCKETS)
        hostEntries.forEach { (host, indexes) ->
            writeString(output, host, BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_BYTES)
            writeRuleIndexes(output, indexes)
        }
        val tokenEntries = snapshot.tokenRuleIndexes.toSortedMap()
        writeCount(output, tokenEntries.size, BrowserAdBlockCompilerContract.MAX_INDEX_KEY_LENGTH)
        tokenEntries.forEach { (length, buckets) ->
            output.writeInt(length)
            val sortedBuckets = buckets.toSortedMap()
            writeCount(output, sortedBuckets.size, BROWSER_AD_BLOCK_CACHE_MAX_INDEX_BUCKETS)
            sortedBuckets.forEach { (hash, indexes) ->
                output.writeLong(hash)
                writeRuleIndexes(output, indexes)
            }
        }
        writeRuleIndexes(output, snapshot.unindexedRuleIndexes)
    }

    private fun readNetworkIndex(
        input: DataInputStream,
        domainInterner: BrowserAdBlockDomainInterner,
    ): BrowserAdBlockNetworkIndexSnapshot {
        val requiresPartyClassification = input.readBoolean()
        val hostRuleIndexes =
            LinkedHashMap<String, List<Int>>().apply {
                repeat(readCount(input, BROWSER_AD_BLOCK_CACHE_MAX_INDEX_BUCKETS)) {
                    val host =
                        domainInterner.intern(
                            readString(input, BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_BYTES),
                        )
                    if (put(host, readRuleIndexes(input)) != null) {
                        throw BrowserAdBlockCompiledCacheException("duplicate_host_bucket")
                    }
                }
            }
        val tokenRuleIndexes =
            LinkedHashMap<Int, Map<Long, List<Int>>>().apply {
                repeat(
                    readCount(
                        input,
                        BrowserAdBlockCompilerContract.MAX_INDEX_KEY_LENGTH,
                    ),
                ) {
                    val length = input.readInt()
                    if (
                        length !in
                        BrowserAdBlockCompilerContract.MIN_INDEX_KEY_LENGTH..
                            BrowserAdBlockCompilerContract.MAX_INDEX_KEY_LENGTH
                    ) {
                        throw BrowserAdBlockCompiledCacheException("token_length")
                    }
                    val buckets =
                        LinkedHashMap<Long, List<Int>>().apply buckets@{
                            repeat(
                                readCount(
                                    input,
                                    BROWSER_AD_BLOCK_CACHE_MAX_INDEX_BUCKETS,
                                ),
                            ) {
                                val hash = input.readLong()
                                if (put(hash, readRuleIndexes(input)) != null) {
                                    throw BrowserAdBlockCompiledCacheException(
                                        "duplicate_token_bucket",
                                    )
                                }
                            }
                        }
                    if (put(length, buckets) != null) {
                        throw BrowserAdBlockCompiledCacheException("duplicate_token_length")
                    }
                }
            }
        return BrowserAdBlockNetworkIndexSnapshot(
            requiresPartyClassification = requiresPartyClassification,
            hostRuleIndexes = hostRuleIndexes,
            tokenRuleIndexes = tokenRuleIndexes,
            unindexedRuleIndexes = readRuleIndexes(input),
        )
    }

    private fun writeElementIndex(
        output: DataOutputStream,
        snapshot: BrowserAdBlockElementIndexSnapshot,
    ) {
        writeRuleIndexes(output, snapshot.genericRuleIndexes)
        val domainEntries = snapshot.domainRuleIndexes.toSortedMap()
        writeCount(output, domainEntries.size, BROWSER_AD_BLOCK_CACHE_MAX_INDEX_BUCKETS)
        domainEntries.forEach { (domain, indexes) ->
            writeString(output, domain, BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_BYTES)
            writeRuleIndexes(output, indexes)
        }
    }

    private fun readElementIndex(
        input: DataInputStream,
        domainInterner: BrowserAdBlockDomainInterner,
    ): BrowserAdBlockElementIndexSnapshot {
        val genericRuleIndexes = readRuleIndexes(input)
        val domainRuleIndexes =
            LinkedHashMap<String, List<Int>>().apply {
                repeat(readCount(input, BROWSER_AD_BLOCK_CACHE_MAX_INDEX_BUCKETS)) {
                    val domain =
                        domainInterner.intern(
                            readString(input, BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_BYTES),
                        )
                    if (put(domain, readRuleIndexes(input)) != null) {
                        throw BrowserAdBlockCompiledCacheException("duplicate_element_bucket")
                    }
                }
            }
        return BrowserAdBlockElementIndexSnapshot(
            genericRuleIndexes = genericRuleIndexes,
            domainRuleIndexes = domainRuleIndexes,
        )
    }

    private fun writeRuleIndexes(
        output: DataOutputStream,
        indexes: List<Int>,
    ) {
        writeCount(output, indexes.size, BROWSER_AD_BLOCK_CACHE_MAX_RULE_INDEXES_PER_BUCKET)
        indexes.forEach(output::writeInt)
    }

    private fun readRuleIndexes(
        input: DataInputStream,
    ): List<Int> =
        List(readCount(input, BROWSER_AD_BLOCK_CACHE_MAX_RULE_INDEXES_PER_BUCKET)) {
            input.readInt()
        }

    private fun validateCompiledPartition(partition: BrowserAdBlockCompiledPartition) {
        val networkRules = partition.ruleSet.networkRules
        networkRules.forEach { rule ->
            if (
                !rule.domainIncludes.isStrictlyIncreasing() ||
                    !rule.domainExcludes.isStrictlyIncreasing() ||
                    !rule.denyAllowDomains.isStrictlyIncreasing()
            ) {
                throw BrowserAdBlockCompiledCacheException("network_domain_order")
            }
        }
        val networkSeen = BooleanArray(networkRules.size)
        var networkReferenceCount = 0
        fun validateNetworkReference(
            index: Int,
            kind: String,
            host: String? = null,
            tokenLength: Int? = null,
            tokenHash: Long? = null,
        ) {
            if (index !in networkRules.indices || networkSeen[index]) {
                throw BrowserAdBlockCompiledCacheException("network_index_reference")
            }
            val rule = networkRules[index]
            when (kind) {
                "host" -> {
                    if (rule.hostAnchor != host || rule.indexKey != null) {
                        throw BrowserAdBlockCompiledCacheException("host_index_semantics")
                    }
                }
                "token" -> {
                    val key = rule.indexKey
                    if (
                        rule.hostAnchor != null ||
                            key == null ||
                            key.length != tokenLength ||
                            browserAdBlockCompiledCacheTokenHash(key) != tokenHash
                    ) {
                        throw BrowserAdBlockCompiledCacheException("token_index_semantics")
                    }
                }
                "unindexed" -> {
                    if (rule.hostAnchor != null || rule.indexKey != null) {
                        throw BrowserAdBlockCompiledCacheException("unindexed_semantics")
                    }
                }
            }
            networkSeen[index] = true
            networkReferenceCount += 1
        }
        partition.networkIndexSnapshot.hostRuleIndexes.forEach { (host, indexes) ->
            indexes.forEach { index ->
                validateNetworkReference(index, kind = "host", host = host)
            }
        }
        partition.networkIndexSnapshot.tokenRuleIndexes.forEach { (length, buckets) ->
            buckets.forEach { (hash, indexes) ->
                indexes.forEach { index ->
                    validateNetworkReference(
                        index,
                        kind = "token",
                        tokenLength = length,
                        tokenHash = hash,
                    )
                }
            }
        }
        partition.networkIndexSnapshot.unindexedRuleIndexes.forEach { index ->
            validateNetworkReference(index, kind = "unindexed")
        }
        if (
            networkReferenceCount != networkRules.size ||
                networkSeen.any { seen -> !seen } ||
                partition.networkIndexSnapshot.requiresPartyClassification !=
                networkRules.any { rule -> rule.thirdParty != null }
        ) {
            throw BrowserAdBlockCompiledCacheException("network_index_coverage")
        }

        val elementRules = partition.ruleSet.elementRules
        elementRules.forEach { rule ->
            if (
                !rule.domainIncludes.isStrictlyIncreasing() ||
                    !rule.domainExcludes.isStrictlyIncreasing()
            ) {
                throw BrowserAdBlockCompiledCacheException("element_domain_order")
            }
        }
        val elementReferenceCounts = IntArray(elementRules.size)
        partition.elementIndexSnapshot.genericRuleIndexes.forEach { index ->
            if (index !in elementRules.indices || !elementRules[index].generic) {
                throw BrowserAdBlockCompiledCacheException("generic_element_index")
            }
            elementReferenceCounts[index] += 1
        }
        partition.elementIndexSnapshot.domainRuleIndexes.forEach { (domain, indexes) ->
            indexes.forEach { index ->
                if (
                        index !in elementRules.indices ||
                        elementRules[index].generic ||
                        elementRules[index].domainIncludes.binarySearch(domain) < 0
                ) {
                    throw BrowserAdBlockCompiledCacheException("domain_element_index")
                }
                elementReferenceCounts[index] += 1
            }
        }
        elementRules.forEachIndexed { index, rule ->
            val expectedCount = if (rule.generic) 1 else rule.domainIncludes.size
            if (elementReferenceCounts[index] != expectedCount) {
                throw BrowserAdBlockCompiledCacheException("element_index_coverage")
            }
        }
    }

    private fun List<String>.isStrictlyIncreasing(): Boolean {
        for (index in 1 until size) {
            if (this[index - 1] >= this[index]) {
                return false
            }
        }
        return true
    }
}

internal fun browserAdBlockSha256(bytes: ByteArray): String =
    MessageDigest
        .getInstance(BROWSER_AD_BLOCK_CACHE_DIGEST_ALGORITHM)
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

internal fun browserAdBlockSha256(value: String): String =
    browserAdBlockSha256(value.toByteArray(Charsets.UTF_8))

private fun writeCount(
    output: DataOutputStream,
    count: Int,
    maximum: Int,
) {
    require(count in 0..maximum) {
        "Browser ad-block compiled cache collection is too large"
    }
    output.writeInt(count)
}

private fun readCount(
    input: DataInputStream,
    maximum: Int,
): Int =
    input.readInt().also { count ->
        if (count !in 0..maximum) {
            throw BrowserAdBlockCompiledCacheException("collection_count")
        }
    }

private fun writeString(
    output: DataOutputStream,
    value: String,
    maximumBytes: Int,
) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    require(bytes.size <= maximumBytes) {
        "Browser ad-block compiled cache string is too large"
    }
    output.writeInt(bytes.size)
    output.write(bytes)
}

private fun readString(
    input: DataInputStream,
    maximumBytes: Int,
): String {
    val byteCount = input.readInt()
    if (byteCount !in 0..maximumBytes) {
        throw BrowserAdBlockCompiledCacheException("string_length")
    }
    val bytes = ByteArray(byteCount)
    input.readFully(bytes)
    return bytes.toString(Charsets.UTF_8)
}

private fun writeNullableString(
    output: DataOutputStream,
    value: String?,
    maximumBytes: Int,
) {
    output.writeBoolean(value != null)
    if (value != null) {
        writeString(output, value, maximumBytes)
    }
}

private fun readNullableString(
    input: DataInputStream,
    maximumBytes: Int,
): String? =
    if (input.readBoolean()) {
        readString(input, maximumBytes)
    } else {
        null
    }

private fun writeStringCollection(
    output: DataOutputStream,
    values: Collection<String>,
    maximumEntries: Int,
    maximumStringBytes: Int,
) {
    val sorted = values.sorted()
    writeCount(output, sorted.size, maximumEntries)
    sorted.forEach { value ->
        writeString(output, value, maximumStringBytes)
    }
}

private fun readStringList(
    input: DataInputStream,
    maximumEntries: Int,
    maximumStringBytes: Int,
    domainInterner: BrowserAdBlockDomainInterner,
): List<String> {
    val count = readCount(input, maximumEntries)
    var previousValue: String? = null
    return ArrayList<String>(count).apply {
        repeat(count) {
            val decodedValue = readString(input, maximumStringBytes)
            val previous = previousValue
            // 写入端固定排序，因此相邻比较即可同时验证顺序与重复值，无需在解码峰值上再建 HashSet。
            if (previous != null && decodedValue <= previous) {
                throw BrowserAdBlockCompiledCacheException(
                    if (decodedValue == previous) "duplicate_set_value" else "set_order",
                )
            }
            previousValue = decodedValue
            add(domainInterner.intern(decodedValue))
        }
    }
}

private fun ruleSourceCode(source: BrowserAdBlockRuleSource): Int =
    when (source) {
        BrowserAdBlockRuleSource.CUSTOM -> 0
        BrowserAdBlockRuleSource.SUBSCRIPTION -> 1
    }

private fun ruleSourceFromCode(code: Int): BrowserAdBlockRuleSource =
    when (code) {
        0 -> BrowserAdBlockRuleSource.CUSTOM
        1 -> BrowserAdBlockRuleSource.SUBSCRIPTION
        else -> throw BrowserAdBlockCompiledCacheException("rule_source")
    }

private fun resourceTypeBits(types: Set<BrowserAdBlockResourceType>): Int =
    types.fold(0) { bits, type ->
        bits or (1 shl BrowserAdBlockCompilerContract.resourceTypeCode(type))
    }

private fun resourceTypesFromBits(bits: Int): Set<BrowserAdBlockResourceType> {
    val supportedMask = (1 shl BrowserAdBlockResourceType.entries.size) - 1
    if (bits and supportedMask.inv() != 0) {
        throw BrowserAdBlockCompiledCacheException("resource_type_bits")
    }
    return BrowserAdBlockResourceType.entries
        .filterTo(LinkedHashSet()) { type ->
            bits and (1 shl BrowserAdBlockCompilerContract.resourceTypeCode(type)) != 0
        }
}

private fun pagePolicyBits(policy: BrowserAdBlockPagePolicy): Int =
    (if (policy.document) 1 else 0) or
        (if (policy.elementHide) 1 shl 1 else 0) or
        (if (policy.genericHide) 1 shl 2 else 0) or
        (if (policy.genericBlock) 1 shl 3 else 0)

private fun pagePolicyFromBits(bits: Int): BrowserAdBlockPagePolicy {
    if (bits and BROWSER_AD_BLOCK_CACHE_PAGE_POLICY_MASK.inv() != 0) {
        throw BrowserAdBlockCompiledCacheException("page_policy_bits")
    }
    return BrowserAdBlockPagePolicy(
        document = bits and 1 != 0,
        elementHide = bits and (1 shl 1) != 0,
        genericHide = bits and (1 shl 2) != 0,
        genericBlock = bits and (1 shl 3) != 0,
    )
}

private fun browserAdBlockCompiledCacheTokenHash(value: String): Long {
    var hash = 0L
    value.forEach { character ->
        hash = hash * BrowserAdBlockCompilerContract.TOKEN_HASH_BASE + character.code
    }
    return hash
}

private class CountingOutputStream(
    output: OutputStream,
) : FilterOutputStream(output) {
    var byteCount: Long = 0L
        private set

    override fun write(value: Int) {
        out.write(value)
        byteCount += 1L
    }

    override fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) {
        out.write(bytes, offset, length)
        byteCount += length.toLong()
    }
}

private class CountingInputStream(
    input: InputStream,
) : FilterInputStream(input) {
    var byteCount: Long = 0L
        private set

    override fun read(): Int =
        super.read().also { value ->
            if (value >= 0) {
                byteCount += 1L
            }
        }

    override fun read(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): Int =
        super.read(bytes, offset, length).also { count ->
            if (count > 0) {
                byteCount += count.toLong()
            }
        }
}

private class LimitedInputStream(
    input: InputStream,
    byteCount: Long,
) : FilterInputStream(input) {
    var remainingByteCount: Long = byteCount
        private set

    override fun read(): Int {
        if (remainingByteCount == 0L) {
            return -1
        }
        return super.read().also { value ->
            if (value >= 0) {
                remainingByteCount -= 1L
            }
        }
    }

    override fun read(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (remainingByteCount == 0L) {
            return -1
        }
        val requested = minOf(length.toLong(), remainingByteCount).toInt()
        return super.read(bytes, offset, requested).also { count ->
            if (count > 0) {
                remainingByteCount -= count.toLong()
            }
        }
    }
}

private val BROWSER_AD_BLOCK_CACHE_MAGIC =
    byteArrayOf(
        'K'.code.toByte(),
        'A'.code.toByte(),
        'D'.code.toByte(),
        'B'.code.toByte(),
        'C'.code.toByte(),
    )
private val BROWSER_AD_BLOCK_CACHE_ID_REGEX = Regex("[A-Za-z0-9._-]{1,96}")
private val BROWSER_AD_BLOCK_SHA_256_REGEX = Regex("[a-f0-9]{64}")
private const val BROWSER_AD_BLOCK_CACHE_DIGEST_ALGORITHM = "SHA-256"
private const val BROWSER_AD_BLOCK_CACHE_SHA_256_BYTES = 32
private const val BROWSER_AD_BLOCK_CACHE_SHA_256_HEX_BYTES = 64
private const val BROWSER_AD_BLOCK_CACHE_MAX_CONTRACT_BYTES = 128
private const val BROWSER_AD_BLOCK_CACHE_MAX_ID_BYTES = 128
private const val BROWSER_AD_BLOCK_CACHE_MAX_RULE_ID_BYTES = 256
private const val BROWSER_AD_BLOCK_CACHE_MAX_NAME_BYTES = 512
private const val BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_BYTES = 1_024
private const val BROWSER_AD_BLOCK_CACHE_MAX_DOMAIN_EXPRESSION_BYTES = 262_144
private const val BROWSER_AD_BLOCK_CACHE_MAX_SELECTOR_BYTES = 16_384
private const val BROWSER_AD_BLOCK_CACHE_MAX_RULE_BYTES = 262_144
private const val BROWSER_AD_BLOCK_CACHE_MAX_INDEX_KEY_BYTES = 64
private const val BROWSER_AD_BLOCK_CACHE_MAX_NETWORK_RULES = 1_000_000
private const val BROWSER_AD_BLOCK_CACHE_MAX_ELEMENT_RULES = 1_000_000
private const val BROWSER_AD_BLOCK_CACHE_MAX_BAD_FILTERS = 250_000
private const val BROWSER_AD_BLOCK_CACHE_MAX_DOMAINS_PER_RULE = 16_384
private const val BROWSER_AD_BLOCK_CACHE_MAX_INDEX_BUCKETS = 1_000_000
private const val BROWSER_AD_BLOCK_CACHE_MAX_RULE_INDEXES_PER_BUCKET = 1_000_000
private const val BROWSER_AD_BLOCK_CACHE_MAX_PAYLOAD_BYTES = 32L * 1024L * 1024L
private const val BROWSER_AD_BLOCK_CACHE_MAX_BODY_BYTES = 512L * 1024L * 1024L
private const val BROWSER_AD_BLOCK_CACHE_MAX_FILE_BYTES = 513L * 1024L * 1024L
private const val BROWSER_AD_BLOCK_CACHE_COPY_BUFFER_BYTES = 64 * 1024
private const val BROWSER_AD_BLOCK_CACHE_PAGE_POLICY_MASK = 0x0f
