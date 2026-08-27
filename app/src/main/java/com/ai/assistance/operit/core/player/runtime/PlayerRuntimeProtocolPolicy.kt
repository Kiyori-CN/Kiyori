package com.ai.assistance.operit.core.player.runtime

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

internal enum class PlayerRuntimeCommandType {
    INITIALIZE,
    LOAD,
    ATTACH_SURFACE,
    UPDATE_SURFACE,
    DETACH_SURFACE,
    SET_PAUSED,
    SEEK,
    SET_SPEED,
    SET_AUDIO_TRACK,
    SET_SUBTITLE_TRACK,
    APPLY_SETTINGS,
    APPLY_VIDEO_FIT,
    THUMBNAIL,
    SCREENSHOT,
    CLOSE,
}

internal data class PlayerRuntimeEventCursor(
    val runtimeGeneration: Long,
    val lastEventSequence: Long = 0L,
) {
    init {
        require(runtimeGeneration > 0L) { "Player runtime generation must be positive" }
        require(lastEventSequence >= 0L) { "Player runtime event sequence cannot be negative" }
    }
}

internal data class PlayerRuntimeEventAcceptance(
    val cursor: PlayerRuntimeEventCursor,
    val accepted: Boolean,
)

internal enum class PlayerSeekLifecycleEvent {
    MPV_SEEK,
    MPV_PLAYBACK_RESTART,
}

internal fun reducePlayerSeeking(
    currentSeeking: Boolean,
    event: PlayerSeekLifecycleEvent,
): Boolean =
    when (event) {
        PlayerSeekLifecycleEvent.MPV_SEEK -> true
        PlayerSeekLifecycleEvent.MPV_PLAYBACK_RESTART -> false
    }

internal fun isPlayerUserSeekEvent(
    pendingUserSeekLoadCommandId: Long?,
    eventLoadCommandId: Long,
): Boolean =
    eventLoadCommandId > 0L &&
        pendingUserSeekLoadCommandId == eventLoadCommandId

internal data class PlayerMpvHttpHeaderPlan(
    val forwardedHeaders: Map<String, String>,
    val rangeHeaderObserved: Boolean,
)

internal data class PlayerMpvEndFileState(
    val reason: String?,
    val fileError: String?,
    val failed: Boolean,
)

internal enum class PlayerPlaybackFailureKind {
    DNS,
    TCP_CONNECTION_REFUSED,
    TCP_CONNECT_TIMEOUT,
    NETWORK_UNREACHABLE,
    TLS_CERTIFICATE,
    TLS_HANDSHAKE,
    HTTP_AUTH_REQUIRED,
    HTTP_FORBIDDEN,
    HTTP_NOT_FOUND,
    HTTP_RANGE_REJECTED,
    HTTP_RATE_LIMITED,
    HTTP_SERVER_ERROR,
    HTTP_STATUS,
    REDIRECT,
    UNSUPPORTED_CONTENT_ENCODING,
    MANIFEST,
    DEMUX,
    UNSUPPORTED_CODEC,
    DECODER,
    SURFACE,
    RUNTIME_NATIVE_EXIT,
}

internal data class PlayerPlaybackFailureEvidence(
    val kind: PlayerPlaybackFailureKind,
    val userMessage: String,
)

internal enum class PlayerFullVideoCachePhase {
    DISABLED,
    PREPARING,
    INELIGIBLE,
    ACTIVE,
    COMPLETE,
    TERMINATED,
}

internal enum class PlayerFullVideoCacheStateEvidence {
    NOT_APPLICABLE,
    AVAILABLE,
    UNAVAILABLE,
    MALFORMED,
}

internal enum class PlayerFullVideoCacheReason {
    NONE,
    NOT_NETWORK,
    NOT_VIDEO,
    SEGMENTED_MANIFEST,
    NOT_FINITE,
    NOT_FULLY_SEEKABLE,
    SIZE_UNKNOWN,
    SIZE_LIMIT_REACHED,
    SPACE_INSUFFICIENT,
    PREPARATION_ERROR,
    STORAGE_FLOOR_REACHED,
    FILE_LIMIT_REACHED,
}

internal data class PlayerFullVideoCacheQualification(
    val viaNetwork: Boolean?,
    val videoTrackCount: Int,
    val fileFormat: String?,
    val seekable: Boolean?,
    val partiallySeekable: Boolean?,
    val durationSeconds: Double?,
    val fileSizeBytes: Long?,
    val availableBytes: Long,
    val selectedForwardBytes: Long,
    val selectedBackwardBytes: Long,
    val selectedCacheSeconds: Int,
)

internal data class PlayerFullVideoCachePlan(
    val phase: PlayerFullVideoCachePhase,
    val reason: PlayerFullVideoCacheReason,
    val expectedFileBytes: Long?,
    val metadataForwardBytes: Long,
    val metadataBackwardBytes: Long,
    val cacheSeconds: Int,
    val requiredFreeBytes: Long?,
) {
    val active: Boolean
        get() = phase == PlayerFullVideoCachePhase.ACTIVE
}

internal data class PlayerFullVideoCacheRangeInput(
    val startSeconds: Double?,
    val endSeconds: Double?,
)

internal data class PlayerFullVideoCacheStateInput(
    val structurallyValid: Boolean,
    val fileCacheBytes: Long?,
    val bofCached: Boolean?,
    val eofCached: Boolean?,
    val seekableRanges: List<PlayerFullVideoCacheRangeInput>?,
)

internal data class PlayerFullVideoCacheStateObservation(
    val evidence: PlayerFullVideoCacheStateEvidence,
    val fileCacheBytes: Long,
    val bofCached: Boolean,
    val eofCached: Boolean,
    val seekableRanges: List<Pair<Double, Double>>,
)

internal enum class PlayerRuntimeCapabilityState(
    val diagnosticValue: String,
) {
    AVAILABLE("1"),
    UNAVAILABLE("0"),
    UNKNOWN("?"),
}

internal enum class PlayerRuntimeHardwareDecoderEvidence(
    val diagnosticValue: String,
    val malformed: Boolean,
) {
    CHOICES_AVAILABLE("choices-available", false),
    CHOICES_ABSENT("choices-absent", false),
    OPTION_INFO_UNAVAILABLE("option-info-unavailable", false),
    OPTION_INFO_NOT_MAP("option-info-not-map", true),
    CHOICES_NOT_ARRAY("choices-not-array", true),
    CHOICE_ENTRY_NOT_STRING("choice-entry-not-string", true),
}

internal data class PlayerRuntimeHardwareDecoderMetadata(
    val evidence: PlayerRuntimeHardwareDecoderEvidence,
    val optionType: String?,
    val availableHardwareDecoders: Set<String>,
) {
    init {
        require(
            evidence == PlayerRuntimeHardwareDecoderEvidence.CHOICES_AVAILABLE ||
                availableHardwareDecoders.isEmpty(),
        ) {
            "Hardware decoder choices require available choices evidence"
        }
    }
}

internal data class PlayerRuntimeCapabilitySnapshot(
    val mpvVersion: String,
    val ffmpegVersion: String,
    val protocols: Map<String, Boolean>,
    val demuxers: Map<String, Boolean>,
    val decoders: Map<String, Boolean>,
    val hardwareDecoders: Map<String, PlayerRuntimeCapabilityState>,
    val hardwareDecoderEvidence: PlayerRuntimeHardwareDecoderEvidence,
    val hardwareDecoderOptionType: String?,
    val digest: String,
) {
    fun diagnosticSummary(): String =
        "mpv=$mpvVersion ffmpeg=$ffmpegVersion digest=$digest " +
            "protocols=${protocols.renderCapabilityMap()} " +
            "demuxers=${demuxers.renderCapabilityMap()} " +
            "decoders=${decoders.renderCapabilityMap()} " +
            "hwdecEvidence=${hardwareDecoderEvidence.diagnosticValue} " +
            "hwdecType=${hardwareDecoderOptionType ?: "unknown"} " +
            "hwdec=${hardwareDecoders.renderCapabilityStateMap()}"
}

internal fun acceptPlayerRuntimeEvent(
    cursor: PlayerRuntimeEventCursor,
    runtimeGeneration: Long,
    eventSequence: Long,
): PlayerRuntimeEventAcceptance {
    val accepted =
        runtimeGeneration == cursor.runtimeGeneration &&
            eventSequence > cursor.lastEventSequence
    return PlayerRuntimeEventAcceptance(
        cursor =
            if (accepted) {
                cursor.copy(lastEventSequence = eventSequence)
            } else {
                cursor
            },
        accepted = accepted,
    )
}

internal fun buildPlayerMpvHttpHeaderPlan(
    headers: Map<String, String>,
): PlayerMpvHttpHeaderPlan {
    val forwardedHeaders = LinkedHashMap<String, String>()
    var rangeHeaderObserved = false
    headers.forEach { (name, value) ->
        val canonicalName = name.trim()
        require(
            canonicalName.isNotBlank() &&
                canonicalName == name &&
                !canonicalName.contains(':') &&
                !canonicalName.contains('\n') &&
                !canonicalName.contains('\r'),
        ) {
            "Invalid player request header name"
        }
        require(!value.contains('\n') && !value.contains('\r')) {
            "Invalid player request header value"
        }
        val normalizedName = canonicalName.lowercase(Locale.ROOT)
        if (normalizedName == "range") {
            // Browser capture records one concrete request. Replaying that byte range as a
            // permanent custom header would override FFmpeg's current offset during open/seek.
            rangeHeaderObserved = true
        } else if (!isPlayerMpvOwnedOrBrowserOnlyHeader(normalizedName)) {
            forwardedHeaders.keys
                .firstOrNull { current -> current.equals(canonicalName, ignoreCase = true) }
                ?.let(forwardedHeaders::remove)
            forwardedHeaders[canonicalName] = value
        }
    }
    return PlayerMpvHttpHeaderPlan(
        forwardedHeaders = forwardedHeaders.toMap(),
        rangeHeaderObserved = rangeHeaderObserved,
    )
}

/**
 * A loopback bridge already owns the original upstream request headers in the main process.
 * Passing those headers through Binder would make mpv send credentials to the local bridge a
 * second time, so only direct media keeps the caller-provided header snapshot.
 */
internal fun headersForPlayerRuntimeTransport(
    transport: PlayerRuntimeMediaTransport,
    headers: Map<String, String>,
): Map<String, String> =
    when (transport) {
        PlayerRuntimeMediaTransport.DIRECT -> headers
        PlayerRuntimeMediaTransport.MAIN_PROCESS_PROXY_BRIDGE,
        PlayerRuntimeMediaTransport.LOCAL_DESCRIPTOR,
        -> emptyMap()
    }

private val PlayerMpvOwnedOrBrowserOnlyHeaders =
    setOf(
        "accept-encoding",
        "connection",
        "content-length",
        "expect",
        "host",
        "keep-alive",
        "proxy-authorization",
        "proxy-connection",
        "purpose",
        "sec-purpose",
        "te",
        "trailer",
        "transfer-encoding",
        "upgrade",
        "upgrade-insecure-requests",
    )

private fun isPlayerMpvOwnedOrBrowserOnlyHeader(normalizedName: String): Boolean =
    normalizedName in PlayerMpvOwnedOrBrowserOnlyHeaders ||
        normalizedName.startsWith("sec-ch-ua") ||
        normalizedName.startsWith("sec-fetch-")

internal fun classifyPlayerPlaybackFailureLog(message: String): PlayerPlaybackFailureEvidence? {
    val normalized = message.lowercase(Locale.ROOT)
    val httpStatus =
        PlayerHttpStatusPattern
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    return when {
        "connection refused" in normalized ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.TCP_CONNECTION_REFUSED,
                "TCP 连接被拒绝；连接尚未进入 HTTP、TLS 或媒体解析阶段",
            )
        "connection timed out" in normalized ||
            "connect timeout" in normalized ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.TCP_CONNECT_TIMEOUT,
                "TCP 连接超时；请结合播放器网络快照检查 VPN、路由和目标服务",
            )
        "network is unreachable" in normalized ||
            "no route to host" in normalized ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.NETWORK_UNREACHABLE,
                "当前播放器进程没有可达目标的网络路由",
            )
        "name or service not known" in normalized ||
            "temporary failure in name resolution" in normalized ||
            "no address associated with hostname" in normalized ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.DNS,
                "域名解析失败；请结合 Private DNS、VPN 和 DNS 地址族快照检查",
            )
        "certificate verify failed" in normalized ||
            "certificate verification failed" in normalized ||
            ("x509" in normalized && "verify" in normalized) ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.TLS_CERTIFICATE,
                "TLS 证书验证失败",
            )
        "tls handshake" in normalized && ("fail" in normalized || "error" in normalized) ||
            "ssl handshake" in normalized && ("fail" in normalized || "error" in normalized) ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.TLS_HANDSHAKE,
                "TLS 握手失败；连接已建立，但安全会话未能完成",
            )
        httpStatus == 401 ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.HTTP_AUTH_REQUIRED,
                "服务器要求身份认证；请检查当前媒体请求的认证信息是否仍然有效",
            )
        httpStatus == 403 ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.HTTP_FORBIDDEN,
                "服务器拒绝访问当前媒体资源",
            )
        httpStatus == 404 ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.HTTP_NOT_FOUND,
                "服务器未找到当前媒体资源",
            )
        httpStatus == 416 ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.HTTP_RANGE_REJECTED,
                "服务器拒绝了当前媒体字节范围请求",
            )
        httpStatus == 429 ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.HTTP_RATE_LIMITED,
                "服务器限制了当前媒体请求频率",
            )
        httpStatus != null && httpStatus in 500..599 ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.HTTP_SERVER_ERROR,
                "媒体服务器返回服务端错误",
            )
        httpStatus != null && httpStatus in 400..499 ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.HTTP_STATUS,
                "服务器返回 HTTP $httpStatus 错误状态",
            )
        "too many redirects" in normalized ||
            "redirect loop" in normalized ||
            "redirected too many times" in normalized ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.REDIRECT,
                "媒体地址重定向失败",
            )
        "unsupported content encoding" in normalized ||
            ("content encoding" in normalized && "not supported" in normalized) ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.UNSUPPORTED_CONTENT_ENCODING,
                "服务器返回了当前播放器网络栈不支持的内容编码",
            )
        (
            "manifest" in normalized ||
                "playlist" in normalized ||
                "m3u8" in normalized ||
                "mpd" in normalized
            ) &&
            ("invalid" in normalized || "failed" in normalized || "error" in normalized) ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.MANIFEST,
                "媒体清单解析失败",
            )
        "failed to recognize file format" in normalized ||
            "could not find codec parameters" in normalized ||
            ("demux" in normalized && ("failed" in normalized || "error" in normalized)) ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.DEMUX,
                "媒体容器解析失败",
            )
        "unsupported codec" in normalized ||
            "codec not found" in normalized ||
            "no decoder found for codec" in normalized ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.UNSUPPORTED_CODEC,
                "当前播放器运行时不支持该媒体编码",
            )
        (
            "decoder" in normalized ||
                "decoding" in normalized
            ) &&
            ("initializ" in normalized || "failed" in normalized || "error" in normalized) ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.DECODER,
                "媒体解码器初始化或解码失败",
            )
        "surface" in normalized && ("failed" in normalized || "error" in normalized) ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.SURFACE,
                "播放器画面输出 Surface 失败",
            )
        "native player process exited" in normalized ||
            "mpv core shutdown unexpectedly" in normalized ->
            PlayerPlaybackFailureEvidence(
                PlayerPlaybackFailureKind.RUNTIME_NATIVE_EXIT,
                "播放器原生运行时异常退出",
            )
        else -> null
    }
}

internal fun resolvePlayerSurfaceAspectRatio(
    width: Int,
    height: Int,
): Double? =
    if (width > 0 && height > 0) {
        width / height.toDouble()
    } else {
        null
    }

internal fun isPlayerFullVideoCacheComplete(
    phase: PlayerFullVideoCachePhase,
    bofCached: Boolean,
    eofCached: Boolean,
    seekableRanges: List<Pair<Double, Double>>,
): Boolean =
    phase == PlayerFullVideoCachePhase.COMPLETE ||
        (
            phase == PlayerFullVideoCachePhase.ACTIVE &&
                bofCached &&
                eofCached &&
                seekableRanges.size == 1
            )

internal fun resolvePlayerFullVideoCacheStateObservation(
    input: PlayerFullVideoCacheStateInput?,
): PlayerFullVideoCacheStateObservation {
    fun empty(evidence: PlayerFullVideoCacheStateEvidence) =
        PlayerFullVideoCacheStateObservation(
            evidence = evidence,
            fileCacheBytes = 0L,
            bofCached = false,
            eofCached = false,
            seekableRanges = emptyList(),
        )

    if (input == null) {
        return empty(PlayerFullVideoCacheStateEvidence.UNAVAILABLE)
    }
    if (!input.structurallyValid) {
        return empty(PlayerFullVideoCacheStateEvidence.MALFORMED)
    }
    val fileCacheBytes =
        input.fileCacheBytes?.takeIf { bytes -> bytes >= 0L }
            ?: return empty(PlayerFullVideoCacheStateEvidence.MALFORMED)
    val bofCached =
        input.bofCached
            ?: return empty(PlayerFullVideoCacheStateEvidence.MALFORMED)
    val eofCached =
        input.eofCached
            ?: return empty(PlayerFullVideoCacheStateEvidence.MALFORMED)
    val ranges =
        input.seekableRanges
            ?: return empty(PlayerFullVideoCacheStateEvidence.MALFORMED)
    val normalizedRanges =
        ranges.map { range ->
            val start = range.startSeconds
            val end = range.endSeconds
            if (
                start == null ||
                    end == null ||
                    !start.isFinite() ||
                    !end.isFinite() ||
                    end < start
            ) {
                return empty(PlayerFullVideoCacheStateEvidence.MALFORMED)
            }
            val normalizedStart = start.coerceAtLeast(0.0)
            if (end < normalizedStart) {
                return empty(PlayerFullVideoCacheStateEvidence.MALFORMED)
            }
            normalizedStart to end
        }
    return PlayerFullVideoCacheStateObservation(
        evidence = PlayerFullVideoCacheStateEvidence.AVAILABLE,
        fileCacheBytes = fileCacheBytes,
        bofCached = bofCached,
        eofCached = eofCached,
        seekableRanges = normalizedRanges,
    )
}

internal fun playerFullVideoCacheStorageFloorReached(availableBytes: Long): Boolean =
    availableBytes < PLAYER_FULL_CACHE_HARD_FLOOR_BYTES

internal fun resolvePlayerFullVideoCachePlan(
    qualification: PlayerFullVideoCacheQualification,
): PlayerFullVideoCachePlan {
    fun ineligible(reason: PlayerFullVideoCacheReason, expectedFileBytes: Long? = null) =
        PlayerFullVideoCachePlan(
            phase = PlayerFullVideoCachePhase.INELIGIBLE,
            reason = reason,
            expectedFileBytes = expectedFileBytes,
            metadataForwardBytes = qualification.selectedForwardBytes,
            metadataBackwardBytes = qualification.selectedBackwardBytes,
            cacheSeconds = qualification.selectedCacheSeconds,
            requiredFreeBytes = null,
        )

    if (qualification.viaNetwork != true) {
        return ineligible(PlayerFullVideoCacheReason.NOT_NETWORK)
    }
    if (qualification.videoTrackCount <= 0) {
        return ineligible(PlayerFullVideoCacheReason.NOT_VIDEO)
    }
    if (
        qualification.fileFormat
            ?.lowercase(Locale.ROOT)
            ?.let { format -> format == "hls" || format == "dash" } == true
    ) {
        return ineligible(PlayerFullVideoCacheReason.SEGMENTED_MANIFEST)
    }
    val durationSeconds =
        qualification.durationSeconds
            ?.takeIf { duration ->
                duration.isFinite() &&
                    duration > 0.0 &&
                    duration <= PLAYER_FULL_CACHE_MAX_DURATION_SECONDS
            }
            ?: return ineligible(PlayerFullVideoCacheReason.NOT_FINITE)
    if (qualification.seekable != true || qualification.partiallySeekable != false) {
        return ineligible(PlayerFullVideoCacheReason.NOT_FULLY_SEEKABLE)
    }
    val fileSizeBytes =
        qualification.fileSizeBytes
            ?.takeIf { size -> size > 0L }
            ?: return ineligible(PlayerFullVideoCacheReason.SIZE_UNKNOWN)
    if (fileSizeBytes > PLAYER_FULL_CACHE_MAX_FILE_BYTES) {
        return ineligible(
            reason = PlayerFullVideoCacheReason.SIZE_LIMIT_REACHED,
            expectedFileBytes = fileSizeBytes,
        )
    }
    val reserveBytes =
        max(
            PLAYER_FULL_CACHE_MINIMUM_FREE_BYTES,
            ceil(fileSizeBytes * PLAYER_FULL_CACHE_SPACE_RESERVE_RATIO).toLong(),
        )
    val requiredFreeBytes = fileSizeBytes + reserveBytes
    if (qualification.availableBytes < requiredFreeBytes) {
        return ineligible(
            reason = PlayerFullVideoCacheReason.SPACE_INSUFFICIENT,
            expectedFileBytes = fileSizeBytes,
        )
    }
    val metadataBudget = playerFullVideoCacheMetadataBudget(durationSeconds)
    return PlayerFullVideoCachePlan(
        phase = PlayerFullVideoCachePhase.ACTIVE,
        reason = PlayerFullVideoCacheReason.NONE,
        expectedFileBytes = fileSizeBytes,
        metadataForwardBytes = max(qualification.selectedForwardBytes, metadataBudget),
        metadataBackwardBytes = max(qualification.selectedBackwardBytes, metadataBudget),
        cacheSeconds = ceil(durationSeconds).toInt() + PLAYER_FULL_CACHE_EXTRA_SECONDS,
        requiredFreeBytes = requiredFreeBytes,
    )
}

internal fun normalizePlayerActiveHardwareDecoder(value: String?): String? {
    val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return normalized.takeUnless { decoder ->
        decoder.equals("no", ignoreCase = true) ||
            decoder.equals("none", ignoreCase = true)
    }
}

internal fun playerFullVideoCacheMetadataBudget(durationSeconds: Double): Long {
    require(durationSeconds.isFinite() && durationSeconds > 0.0) {
        "Full video cache duration must be finite and positive"
    }
    val durationHours = ceil(durationSeconds / SECONDS_PER_HOUR).toLong().coerceAtLeast(1L)
    val calculated = durationHours * PLAYER_FULL_CACHE_METADATA_BYTES_PER_HOUR
    return calculated.coerceIn(
        PLAYER_FULL_CACHE_MIN_METADATA_BYTES,
        PLAYER_FULL_CACHE_MAX_METADATA_BYTES,
    )
}

internal fun playerFullVideoCacheFileLimitExceeded(fileCacheBytes: Long): Boolean =
    fileCacheBytes > PLAYER_FULL_CACHE_MAX_FILE_BYTES

internal fun resolvePlayerMpvEndFileState(
    reason: String?,
    fileError: String?,
): PlayerMpvEndFileState =
    PlayerMpvEndFileState(
        reason = reason,
        fileError = fileError,
        failed = reason == "error",
    )

internal fun buildPlayerRuntimeCapabilitySnapshot(
    mpvVersion: String,
    ffmpegVersion: String,
    availableProtocols: Set<String>,
    availableDemuxers: Set<String>,
    availableDecoderCodecs: Set<String>,
    hardwareDecoderMetadata: PlayerRuntimeHardwareDecoderMetadata,
): PlayerRuntimeCapabilitySnapshot {
    val normalizedMpvVersion = normalizePlayerRuntimeVersion(mpvVersion, "mpv")
    val normalizedFfmpegVersion = normalizePlayerRuntimeVersion(ffmpegVersion, "FFmpeg")
    val protocols =
        buildPlayerRuntimeCapabilityMap(
            available = availableProtocols,
            targets = PlayerRuntimeTargetProtocols,
        )
    val demuxers =
        buildPlayerRuntimeCapabilityMap(
            available = availableDemuxers,
            targets = PlayerRuntimeTargetDemuxers,
        )
    val decoders =
        buildPlayerRuntimeCapabilityMap(
            available = availableDecoderCodecs,
            targets = PlayerRuntimeTargetDecoders,
        )
    val hardwareDecoders =
        buildPlayerRuntimeHardwareDecoderCapabilityMap(
            metadata = hardwareDecoderMetadata,
            targets = PlayerRuntimeTargetHardwareDecoders,
        )
    val canonical =
        buildString {
            append(normalizedMpvVersion)
            append('\n')
            append(normalizedFfmpegVersion)
            append('\n')
            append(protocols.renderCapabilityMap())
            append('\n')
            append(demuxers.renderCapabilityMap())
            append('\n')
            append(decoders.renderCapabilityMap())
            append('\n')
            append(hardwareDecoderMetadata.evidence.diagnosticValue)
            append('\n')
            append(hardwareDecoderMetadata.optionType ?: "unknown")
            append('\n')
            append(hardwareDecoders.renderCapabilityStateMap())
        }
    val digest =
        MessageDigest
            .getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02X".format(Locale.ROOT, byte.toInt() and 0xFF) }
            .take(16)
    return PlayerRuntimeCapabilitySnapshot(
        mpvVersion = normalizedMpvVersion,
        ffmpegVersion = normalizedFfmpegVersion,
        protocols = protocols,
        demuxers = demuxers,
        decoders = decoders,
        hardwareDecoders = hardwareDecoders,
        hardwareDecoderEvidence = hardwareDecoderMetadata.evidence,
        hardwareDecoderOptionType = hardwareDecoderMetadata.optionType,
        digest = digest,
    )
}

private fun normalizePlayerRuntimeVersion(value: String, label: String): String {
    val normalized = value.trim()
    require(
        normalized.isNotBlank() &&
            normalized.length <= PLAYER_RUNTIME_VERSION_MAX_LENGTH &&
            !normalized.contains('\r') &&
            !normalized.contains('\n'),
    ) {
        "$label runtime version is invalid"
    }
    return normalized
}

private fun buildPlayerRuntimeCapabilityMap(
    available: Set<String>,
    targets: List<String>,
): Map<String, Boolean> {
    val normalizedAvailable = normalizePlayerRuntimeCapabilityValues(available)
    return targets.associateWith { target -> target in normalizedAvailable }
}

private fun buildPlayerRuntimeHardwareDecoderCapabilityMap(
    metadata: PlayerRuntimeHardwareDecoderMetadata,
    targets: List<String>,
): Map<String, PlayerRuntimeCapabilityState> {
    if (metadata.evidence != PlayerRuntimeHardwareDecoderEvidence.CHOICES_AVAILABLE) {
        return targets.associateWith { PlayerRuntimeCapabilityState.UNKNOWN }
    }
    val normalizedAvailable =
        normalizePlayerRuntimeCapabilityValues(metadata.availableHardwareDecoders)
    return targets.associateWith { target ->
        if (target in normalizedAvailable) {
            PlayerRuntimeCapabilityState.AVAILABLE
        } else {
            PlayerRuntimeCapabilityState.UNAVAILABLE
        }
    }
}

private fun normalizePlayerRuntimeCapabilityValues(available: Set<String>): Set<String> =
    available
        .flatMap { value -> value.split(',') }
        .map { value -> value.trim().lowercase(Locale.ROOT) }
        .filter(String::isNotBlank)
        .toSet()

private fun Map<String, Boolean>.renderCapabilityMap(): String =
    entries.joinToString(",") { (name, available) -> "$name=${if (available) 1 else 0}" }

private fun Map<String, PlayerRuntimeCapabilityState>.renderCapabilityStateMap(): String =
    entries.joinToString(",") { (name, state) -> "$name=${state.diagnosticValue}" }

internal const val PLAYER_FULL_CACHE_MAX_FILE_BYTES = 20L * 1024L * 1024L * 1024L
internal const val PLAYER_FULL_CACHE_MINIMUM_FREE_BYTES = 1024L * 1024L * 1024L
internal const val PLAYER_FULL_CACHE_HARD_FLOOR_BYTES = 512L * 1024L * 1024L
internal const val PLAYER_FULL_CACHE_MAX_DURATION_SECONDS = 4.0 * 60.0 * 60.0
private const val PLAYER_FULL_CACHE_EXTRA_SECONDS = 60
private const val PLAYER_FULL_CACHE_SPACE_RESERVE_RATIO = 0.15
private const val SECONDS_PER_HOUR = 60.0 * 60.0
private const val PLAYER_FULL_CACHE_METADATA_BYTES_PER_HOUR = 64L * 1024L * 1024L
private const val PLAYER_FULL_CACHE_MIN_METADATA_BYTES = 128L * 1024L * 1024L
private const val PLAYER_FULL_CACHE_MAX_METADATA_BYTES = 256L * 1024L * 1024L
private const val PLAYER_RUNTIME_VERSION_MAX_LENGTH = 256
private val PlayerRuntimeTargetProtocols = listOf("file", "http", "https")
private val PlayerRuntimeTargetDemuxers =
    listOf("mov", "matroska", "mpegts", "hls", "dash", "flv", "avi", "ogg", "asf", "rm")
private val PlayerRuntimeTargetDecoders =
    listOf("h264", "hevc", "vp9", "av1", "mpeg4", "mpeg2video", "aac", "opus", "vorbis", "mp3")
private val PlayerRuntimeTargetHardwareDecoders = listOf("mediacodec", "mediacodec-copy")
private val PlayerHttpStatusPattern = Regex("""\bhttp(?: error)?\s+([45]\d\d)\b""")
