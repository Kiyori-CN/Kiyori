package com.ai.assistance.operit.core.player.runtime

import com.ai.assistance.operit.core.player.PlayerDebugLogLevel
import com.ai.assistance.operit.core.player.PlayerDecoderBackend
import com.ai.assistance.operit.core.player.PlayerNetworkCachePolicy
import com.ai.assistance.operit.core.player.PlayerRenderingProfile
import com.ai.assistance.operit.core.player.PlayerSettings
import `is`.xyz.mpv.MPVNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRuntimeProtocolPolicyTest {
    @Test
    fun eventGate_acceptsOnlyCurrentGenerationAndIncreasingSequence() {
        val initial = PlayerRuntimeEventCursor(runtimeGeneration = 7L)
        val first = acceptPlayerRuntimeEvent(initial, runtimeGeneration = 7L, eventSequence = 1L)
        val duplicate =
            acceptPlayerRuntimeEvent(first.cursor, runtimeGeneration = 7L, eventSequence = 1L)
        val stale =
            acceptPlayerRuntimeEvent(first.cursor, runtimeGeneration = 6L, eventSequence = 2L)
        val second =
            acceptPlayerRuntimeEvent(first.cursor, runtimeGeneration = 7L, eventSequence = 2L)

        assertTrue(first.accepted)
        assertFalse(duplicate.accepted)
        assertFalse(stale.accepted)
        assertTrue(second.accepted)
        assertEquals(2L, second.cursor.lastEventSequence)
    }

    @Test(expected = IllegalArgumentException::class)
    fun eventCursor_rejectsNonPositiveGeneration() {
        PlayerRuntimeEventCursor(runtimeGeneration = 0L)
    }

    @Test
    fun seekLifecycleRequiresMpvSeekBeforePlaybackRestartCompletion() {
        val started =
            reducePlayerSeeking(
                currentSeeking = false,
                event = PlayerSeekLifecycleEvent.MPV_SEEK,
            )
        val repeated =
            reducePlayerSeeking(
                currentSeeking = started,
                event = PlayerSeekLifecycleEvent.MPV_SEEK,
            )
        val completed =
            reducePlayerSeeking(
                currentSeeking = repeated,
                event = PlayerSeekLifecycleEvent.MPV_PLAYBACK_RESTART,
            )
        val lateRestart =
            reducePlayerSeeking(
                currentSeeking = completed,
                event = PlayerSeekLifecycleEvent.MPV_PLAYBACK_RESTART,
            )

        assertTrue(started)
        assertTrue(repeated)
        assertFalse(completed)
        assertFalse(lateRestart)
    }

    @Test
    fun diagnosticLogLevelsHaveStableWireValues() {
        PlayerDebugLogLevel.entries.forEach { level ->
            assertEquals(level, PlayerDebugLogLevel.fromWireValue(level.wireValue))
        }
        assertEquals(null, PlayerDebugLogLevel.fromWireValue(Int.MIN_VALUE))
    }

    @Test
    fun runtimeConfigKeepsDecoderBackendAndRenderingProfileIndependent() {
        val config =
            PlayerSettings(
                decoderBackend = PlayerDecoderBackend.MEDIACODEC_COPY,
                renderingProfile = PlayerRenderingProfile.LOW_LATENCY,
                networkCachePolicy = PlayerNetworkCachePolicy.COMPACT,
            ).toRuntimeConfig(shaderFiles = emptyList())

        assertEquals("mediacodec-copy", config.decoderBackendId)
        assertEquals("low-latency", config.renderingProfileId)
        assertEquals("compact", config.networkCachePolicyId)

        val restored = config.toPlayerSettings()
        assertEquals(PlayerDecoderBackend.MEDIACODEC_COPY, restored.decoderBackend)
        assertEquals(PlayerRenderingProfile.LOW_LATENCY, restored.renderingProfile)
        assertEquals(PlayerNetworkCachePolicy.COMPACT, restored.networkCachePolicy)
    }

    @Test
    fun runtimeCapabilitySnapshotIsBoundedStableAndSeparatesRuntimeEvidence() {
        val first =
            buildPlayerRuntimeCapabilitySnapshot(
                mpvVersion = "mpv 0.41.0-dev-g2339eb727",
                ffmpegVersion = "FFmpeg n8.1.2",
                availableProtocols = linkedSetOf("https", "file", "http"),
                availableDemuxers =
                    linkedSetOf(
                        "mov,mp4,m4a,3gp,3g2,mj2",
                        "matroska,webm",
                        "mpegts",
                        "hls",
                        "dash",
                    ),
                availableDecoderCodecs =
                    linkedSetOf("h264", "hevc", "vp9", "av1", "aac", "opus", "mp3"),
                hardwareDecoderMetadata =
                    PlayerRuntimeHardwareDecoderMetadata(
                        evidence = PlayerRuntimeHardwareDecoderEvidence.CHOICES_AVAILABLE,
                        optionType = "String-list",
                        availableHardwareDecoders =
                            linkedSetOf("mediacodec-copy", "mediacodec"),
                    ),
            )
        val reordered =
            buildPlayerRuntimeCapabilitySnapshot(
                mpvVersion = "mpv 0.41.0-dev-g2339eb727",
                ffmpegVersion = "FFmpeg n8.1.2",
                availableProtocols = linkedSetOf("http", "file", "https"),
                availableDemuxers =
                    linkedSetOf(
                        "dash",
                        "hls",
                        "mpegts",
                        "matroska,webm",
                        "mov,mp4,m4a,3gp,3g2,mj2",
                    ),
                availableDecoderCodecs =
                    linkedSetOf("mp3", "opus", "aac", "av1", "vp9", "hevc", "h264"),
                hardwareDecoderMetadata =
                    PlayerRuntimeHardwareDecoderMetadata(
                        evidence = PlayerRuntimeHardwareDecoderEvidence.CHOICES_AVAILABLE,
                        optionType = "String-list",
                        availableHardwareDecoders =
                            linkedSetOf("mediacodec", "mediacodec-copy"),
                    ),
            )

        assertTrue(requireNotNull(first.protocols["https"]))
        assertTrue(requireNotNull(first.demuxers["mov"]))
        assertTrue(requireNotNull(first.demuxers["matroska"]))
        assertTrue(requireNotNull(first.decoders["av1"]))
        assertFalse(requireNotNull(first.decoders["vorbis"]))
        assertEquals(
            PlayerRuntimeCapabilityState.AVAILABLE,
            first.hardwareDecoders["mediacodec-copy"],
        )
        assertEquals(first.digest, reordered.digest)
        assertEquals(16, first.digest.length)
        assertTrue(first.diagnosticSummary().length < 1_024)
    }

    @Test
    fun hwdecOptionInfoParserSeparatesAvailableAbsentAndMalformedChoices() {
        val available =
            parsePlayerRuntimeHardwareDecoderMetadata(
                MPVNode.MapNode(
                    mapOf(
                        "type" to MPVNode.StringNode("String list"),
                        "choices" to
                            MPVNode.ArrayNode(
                                arrayOf<MPVNode>(
                                    MPVNode.StringNode("mediacodec-copy"),
                                    MPVNode.StringNode("mediacodec"),
                                ),
                            ),
                    ),
                ),
            )
        val empty =
            parsePlayerRuntimeHardwareDecoderMetadata(
                MPVNode.MapNode(
                    mapOf(
                        "type" to MPVNode.StringNode("String list"),
                        "choices" to MPVNode.ArrayNode(emptyArray()),
                    ),
                ),
            )
        val absent =
            parsePlayerRuntimeHardwareDecoderMetadata(
                MPVNode.MapNode(mapOf("type" to MPVNode.StringNode("String list"))),
            )
        val unavailable = parsePlayerRuntimeHardwareDecoderMetadata(null)
        val invalidOptionInfo =
            parsePlayerRuntimeHardwareDecoderMetadata(MPVNode.StringNode("not-a-map"))
        val invalidChoices =
            parsePlayerRuntimeHardwareDecoderMetadata(
                MPVNode.MapNode(
                    mapOf(
                        "type" to MPVNode.StringNode("String list"),
                        "choices" to MPVNode.StringNode("not-an-array"),
                    ),
                ),
            )
        val invalidChoiceEntry =
            parsePlayerRuntimeHardwareDecoderMetadata(
                MPVNode.MapNode(
                    mapOf(
                        "type" to MPVNode.StringNode("String list"),
                        "choices" to
                            MPVNode.ArrayNode(
                                arrayOf<MPVNode>(
                                    MPVNode.StringNode("mediacodec"),
                                    MPVNode.IntNode(1L),
                                ),
                            ),
                    ),
                ),
            )

        assertEquals(
            linkedSetOf("mediacodec-copy", "mediacodec"),
            available.availableHardwareDecoders,
        )
        assertEquals(PlayerRuntimeHardwareDecoderEvidence.CHOICES_AVAILABLE, available.evidence)
        assertEquals("String-list", available.optionType)
        assertEquals(PlayerRuntimeHardwareDecoderEvidence.CHOICES_AVAILABLE, empty.evidence)
        assertTrue(empty.availableHardwareDecoders.isEmpty())
        assertEquals(PlayerRuntimeHardwareDecoderEvidence.CHOICES_ABSENT, absent.evidence)
        assertEquals(
            PlayerRuntimeHardwareDecoderEvidence.OPTION_INFO_UNAVAILABLE,
            unavailable.evidence,
        )
        assertEquals(
            PlayerRuntimeHardwareDecoderEvidence.OPTION_INFO_NOT_MAP,
            invalidOptionInfo.evidence,
        )
        assertEquals(
            PlayerRuntimeHardwareDecoderEvidence.CHOICES_NOT_ARRAY,
            invalidChoices.evidence,
        )
        assertEquals(
            PlayerRuntimeHardwareDecoderEvidence.CHOICE_ENTRY_NOT_STRING,
            invalidChoiceEntry.evidence,
        )
        assertTrue(invalidOptionInfo.evidence.malformed)
        assertTrue(invalidChoices.evidence.malformed)
        assertTrue(invalidChoiceEntry.evidence.malformed)
    }

    @Test
    fun hwdecCapabilityDigestDistinguishesConfirmedUnsupportedFromUnknownMetadata() {
        fun snapshot(metadata: PlayerRuntimeHardwareDecoderMetadata) =
            buildPlayerRuntimeCapabilitySnapshot(
                mpvVersion = "mpv 0.41.0-dev-g2339eb727",
                ffmpegVersion = "FFmpeg n8.1.2",
                availableProtocols = setOf("file", "http", "https"),
                availableDemuxers = setOf("mov"),
                availableDecoderCodecs = setOf("h264"),
                hardwareDecoderMetadata = metadata,
            )

        val confirmedUnsupported =
            snapshot(
                PlayerRuntimeHardwareDecoderMetadata(
                    evidence = PlayerRuntimeHardwareDecoderEvidence.CHOICES_AVAILABLE,
                    optionType = "String-list",
                    availableHardwareDecoders = emptySet(),
                ),
            )
        val unknown =
            snapshot(
                PlayerRuntimeHardwareDecoderMetadata(
                    evidence = PlayerRuntimeHardwareDecoderEvidence.CHOICES_ABSENT,
                    optionType = "String-list",
                    availableHardwareDecoders = emptySet(),
                ),
            )

        assertEquals(
            PlayerRuntimeCapabilityState.UNAVAILABLE,
            confirmedUnsupported.hardwareDecoders["mediacodec"],
        )
        assertEquals(
            PlayerRuntimeCapabilityState.UNKNOWN,
            unknown.hardwareDecoders["mediacodec"],
        )
        assertNotEquals(confirmedUnsupported.digest, unknown.digest)
        assertTrue(unknown.diagnosticSummary().contains("hwdecEvidence=choices-absent"))
        assertTrue(unknown.diagnosticSummary().contains("mediacodec=?"))
    }

    @Test
    fun networkSnapshotPublishingRequiresAVisibleFactChange() {
        val baseline =
            PlayerNetworkSnapshot(
                activeNetworkPresent = true,
                processBoundNetworkPresent = false,
                processBoundMatchesActive = false,
                effectiveNetworkSource = "active",
                activeTransports = setOf("wifi"),
                effectiveTransports = setOf("wifi"),
                internet = true,
                validated = true,
                notSuspended = true,
                captivePortal = false,
                metered = false,
                restrictedBackground = false,
                privateDnsActive = true,
                privateDnsServerPresent = true,
                ipv4DnsCount = 2,
                ipv6DnsCount = 1,
                ipv4DefaultRouteCount = 1,
                ipv6DefaultRouteCount = 1,
                proxyType = "absent",
            )

        assertTrue(shouldPublishPlayerNetworkSnapshot(previous = null, current = baseline))
        assertFalse(shouldPublishPlayerNetworkSnapshot(previous = baseline, current = baseline.copy()))
        assertTrue(
            shouldPublishPlayerNetworkSnapshot(
                previous = baseline,
                current =
                    baseline.copy(
                        activeTransports = setOf("vpn"),
                        effectiveTransports = setOf("vpn"),
                    ),
            ),
        )
        assertFalse(baseline.diagnosticSummary().contains("://"))
        assertFalse(baseline.diagnosticSummary().contains('@'))
    }

    @Test
    fun mpvHeadersKeepRequestIdentityButLeaveRangeToTheTransport() {
        val plan =
            buildPlayerMpvHttpHeaderPlan(
                linkedMapOf(
                    "Origin" to "https://page.example",
                    "User-Agent" to "Kiyori/1",
                    "Referer" to "https://page.example/watch",
                    "Cookie" to "session=exact",
                    "cookie" to "session=latest",
                    "rAnGe" to "bytes=19890176-",
                    "Accept" to "video/mp4",
                    "Accept-Encoding" to "gzip, br",
                    "Sec-Fetch-Mode" to "no-cors",
                    "Sec-CH-UA-Full-Version-List" to "\"Chromium\";v=\"140\"",
                    "Transfer-Encoding" to "chunked",
                    "Proxy-Authorization" to "Basic redacted",
                    "Purpose" to "prefetch",
                    "X-Media-Token" to "opaque-exact-value",
                ),
            )

        assertTrue(plan.rangeHeaderObserved)
        assertEquals(
            linkedMapOf(
                "Origin" to "https://page.example",
                "User-Agent" to "Kiyori/1",
                "Referer" to "https://page.example/watch",
                "cookie" to "session=latest",
                "Accept" to "video/mp4",
                "X-Media-Token" to "opaque-exact-value",
            ),
            plan.forwardedHeaders,
        )
        assertFalse(plan.forwardedHeaders.keys.any { it.equals("Range", ignoreCase = true) })
        assertFalse(plan.forwardedHeaders.keys.any { it.equals("Accept-Encoding", ignoreCase = true) })
        assertFalse(plan.forwardedHeaders.keys.any { it.startsWith("Sec-Fetch-", ignoreCase = true) })
        assertFalse(plan.forwardedHeaders.keys.any { it.startsWith("Sec-CH-UA", ignoreCase = true) })
        assertFalse(plan.forwardedHeaders.keys.any { it.equals("Transfer-Encoding", ignoreCase = true) })
        assertFalse(plan.forwardedHeaders.keys.any { it.equals("Proxy-Authorization", ignoreCase = true) })
        assertFalse(plan.forwardedHeaders.keys.any { it.equals("Purpose", ignoreCase = true) })
    }

    @Test
    fun runtimeTransportHeaderBoundaryKeepsCredentialsOutOfLoopbackBridge() {
        val headers =
            linkedMapOf(
                "Cookie" to "session=private",
                "Authorization" to "Bearer private",
                "Referer" to "https://example.test/watch",
            )

        assertEquals(
            headers,
            headersForPlayerRuntimeTransport(PlayerRuntimeMediaTransport.DIRECT, headers),
        )
        assertTrue(
            headersForPlayerRuntimeTransport(
                PlayerRuntimeMediaTransport.MAIN_PROCESS_PROXY_BRIDGE,
                headers,
            ).isEmpty(),
        )
        assertTrue(
            headersForPlayerRuntimeTransport(
                PlayerRuntimeMediaTransport.LOCAL_DESCRIPTOR,
                headers,
            ).isEmpty(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun mpvHeadersRejectInjectedLineBreaksBeforeLoggingOrSerialization() {
        buildPlayerMpvHttpHeaderPlan(
            mapOf("Referer" to "https://page.example/watch\r\nRange: bytes=1-"),
        )
    }

    @Test
    fun endFileSchemaUsesStringReasonAndFileError() {
        val failed =
            resolvePlayerMpvEndFileState(
                reason = "error",
                fileError = "loading failed",
            )
        val completed =
            resolvePlayerMpvEndFileState(
                reason = "eof",
                fileError = null,
            )

        assertTrue(failed.failed)
        assertEquals("loading failed", failed.fileError)
        assertFalse(completed.failed)
        assertEquals("eof", completed.reason)
    }

    @Test
    fun playbackFailureClassificationPreservesTheFirstConcreteTransportStage() {
        val refused =
            requireNotNull(
                classifyPlayerPlaybackFailureLog(
                    "tcp: Connection to tcp://redacted:443 failed: Connection refused",
                ),
            )
        assertEquals(PlayerPlaybackFailureKind.TCP_CONNECTION_REFUSED, refused.kind)
        assertTrue(refused.userMessage.contains("HTTP"))
        assertEquals(
            PlayerPlaybackFailureKind.DNS,
            requireNotNull(
                classifyPlayerPlaybackFailureLog("Temporary failure in name resolution"),
            ).kind,
        )
        assertEquals(null, classifyPlayerPlaybackFailureLog("Operation timed out while reading data"))
    }

    @Test
    fun playbackFailureClassificationSeparatesHttpTlsAndMediaStages() {
        val cases =
            linkedMapOf(
                "TLS handshake failed" to PlayerPlaybackFailureKind.TLS_HANDSHAKE,
                "HTTP error 401 Unauthorized" to PlayerPlaybackFailureKind.HTTP_AUTH_REQUIRED,
                "HTTP error 403 Forbidden" to PlayerPlaybackFailureKind.HTTP_FORBIDDEN,
                "HTTP error 404 Not Found" to PlayerPlaybackFailureKind.HTTP_NOT_FOUND,
                "HTTP error 416 Range Not Satisfiable" to PlayerPlaybackFailureKind.HTTP_RANGE_REJECTED,
                "HTTP error 429 Too Many Requests" to PlayerPlaybackFailureKind.HTTP_RATE_LIMITED,
                "HTTP error 503 Service Unavailable" to PlayerPlaybackFailureKind.HTTP_SERVER_ERROR,
                "redirected too many times" to PlayerPlaybackFailureKind.REDIRECT,
                "unsupported content encoding br" to
                    PlayerPlaybackFailureKind.UNSUPPORTED_CONTENT_ENCODING,
                "invalid HLS playlist" to PlayerPlaybackFailureKind.MANIFEST,
                "demuxer failed to open input" to PlayerPlaybackFailureKind.DEMUX,
                "no decoder found for codec av1" to PlayerPlaybackFailureKind.UNSUPPORTED_CODEC,
                "decoder initialization failed" to PlayerPlaybackFailureKind.DECODER,
                "surface initialization failed" to PlayerPlaybackFailureKind.SURFACE,
                "native player process exited" to PlayerPlaybackFailureKind.RUNTIME_NATIVE_EXIT,
            )

        cases.forEach { (message, expected) ->
            assertEquals(
                message,
                expected,
                requireNotNull(classifyPlayerPlaybackFailureLog(message)).kind,
            )
        }
    }

    @Test
    fun fullVideoCachePlanUsesFiniteMetadataBudgetsAndActualFileSizeEvidence() {
        val fileSize = 2L * 1024L * 1024L * 1024L
        val plan =
            resolvePlayerFullVideoCachePlan(
                PlayerFullVideoCacheQualification(
                    viaNetwork = true,
                    videoTrackCount = 1,
                    fileFormat = "mov,mp4,m4a,3gp,3g2,mj2",
                    seekable = true,
                    partiallySeekable = false,
                    durationSeconds = 7_200.0,
                    fileSizeBytes = fileSize,
                    availableBytes = fileSize + PLAYER_FULL_CACHE_MINIMUM_FREE_BYTES,
                    selectedForwardBytes = 64L * 1024L * 1024L,
                    selectedBackwardBytes = 32L * 1024L * 1024L,
                    selectedCacheSeconds = 60,
                ),
            )

        assertTrue(plan.active)
        assertEquals(PlayerFullVideoCacheReason.NONE, plan.reason)
        assertEquals(fileSize, plan.expectedFileBytes)
        assertEquals(128L * 1024L * 1024L, plan.metadataForwardBytes)
        assertEquals(128L * 1024L * 1024L, plan.metadataBackwardBytes)
        assertEquals(7_260, plan.cacheSeconds)
        assertEquals(
            fileSize + PLAYER_FULL_CACHE_MINIMUM_FREE_BYTES,
            plan.requiredFreeBytes,
        )
    }

    @Test
    fun fullVideoCachePlanRejectsSegmentedOrUnboundedInputsWithoutExpandingPolicy() {
        val base =
            PlayerFullVideoCacheQualification(
                viaNetwork = true,
                videoTrackCount = 1,
                fileFormat = "hls",
                seekable = true,
                partiallySeekable = false,
                durationSeconds = 600.0,
                fileSizeBytes = 512L * 1024L * 1024L,
                availableBytes = 4L * 1024L * 1024L * 1024L,
                selectedForwardBytes = 64L * 1024L * 1024L,
                selectedBackwardBytes = 32L * 1024L * 1024L,
                selectedCacheSeconds = 60,
            )
        val manifest = resolvePlayerFullVideoCachePlan(base)
        val unknownSize =
            resolvePlayerFullVideoCachePlan(
                base.copy(
                    fileFormat = "matroska,webm",
                    fileSizeBytes = null,
                ),
            )

        assertFalse(manifest.active)
        assertEquals(PlayerFullVideoCachePhase.INELIGIBLE, manifest.phase)
        assertEquals(PlayerFullVideoCacheReason.SEGMENTED_MANIFEST, manifest.reason)
        assertEquals(base.selectedForwardBytes, manifest.metadataForwardBytes)
        assertEquals(base.selectedBackwardBytes, manifest.metadataBackwardBytes)
        assertEquals(PlayerFullVideoCacheReason.SIZE_UNKNOWN, unknownSize.reason)
    }

    @Test
    fun fullVideoCacheLimitsTrackDiskBytesInsteadOfMisusingMetadataOptions() {
        assertEquals(
            256L * 1024L * 1024L,
            playerFullVideoCacheMetadataBudget(3.1 * 60.0 * 60.0),
        )
        assertFalse(playerFullVideoCacheFileLimitExceeded(PLAYER_FULL_CACHE_MAX_FILE_BYTES))
        assertTrue(playerFullVideoCacheFileLimitExceeded(PLAYER_FULL_CACHE_MAX_FILE_BYTES + 1L))
        assertFalse(playerFullVideoCacheStorageFloorReached(PLAYER_FULL_CACHE_HARD_FLOOR_BYTES))
        assertTrue(playerFullVideoCacheStorageFloorReached(PLAYER_FULL_CACHE_HARD_FLOOR_BYTES - 1L))
    }

    @Test
    fun fullVideoCacheCompletionRequiresBothBoundsAndOneContinuousRange() {
        val completeRange = listOf(0.0 to 7_200.0)

        assertTrue(
            isPlayerFullVideoCacheComplete(
                phase = PlayerFullVideoCachePhase.ACTIVE,
                bofCached = true,
                eofCached = true,
                seekableRanges = completeRange,
            ),
        )
        assertFalse(
            isPlayerFullVideoCacheComplete(
                phase = PlayerFullVideoCachePhase.ACTIVE,
                bofCached = true,
                eofCached = false,
                seekableRanges = completeRange,
            ),
        )
        assertFalse(
            isPlayerFullVideoCacheComplete(
                phase = PlayerFullVideoCachePhase.INELIGIBLE,
                bofCached = true,
                eofCached = true,
                seekableRanges = completeRange,
            ),
        )
        assertFalse(
            isPlayerFullVideoCacheComplete(
                phase = PlayerFullVideoCachePhase.ACTIVE,
                bofCached = true,
                eofCached = true,
                seekableRanges = listOf(0.0 to 300.0, 600.0 to 7_200.0),
            ),
        )
        assertTrue(
            isPlayerFullVideoCacheComplete(
                phase = PlayerFullVideoCachePhase.COMPLETE,
                bofCached = false,
                eofCached = false,
                seekableRanges = emptyList(),
            ),
        )
    }

    @Test
    fun fullVideoCacheStateSeparatesUnavailableMalformedAndAvailableEvidence() {
        assertEquals(
            PlayerFullVideoCacheStateEvidence.UNAVAILABLE,
            resolvePlayerFullVideoCacheStateObservation(null).evidence,
        )
        assertEquals(
            PlayerFullVideoCacheStateEvidence.MALFORMED,
            resolvePlayerFullVideoCacheStateObservation(
                PlayerFullVideoCacheStateInput(
                    structurallyValid = false,
                    fileCacheBytes = null,
                    bofCached = null,
                    eofCached = null,
                    seekableRanges = null,
                ),
            ).evidence,
        )

        val malformedInputs =
            listOf(
                PlayerFullVideoCacheStateInput(
                    structurallyValid = true,
                    fileCacheBytes = null,
                    bofCached = true,
                    eofCached = true,
                    seekableRanges = emptyList(),
                ),
                PlayerFullVideoCacheStateInput(
                    structurallyValid = true,
                    fileCacheBytes = 1L,
                    bofCached = null,
                    eofCached = true,
                    seekableRanges = emptyList(),
                ),
                PlayerFullVideoCacheStateInput(
                    structurallyValid = true,
                    fileCacheBytes = 1L,
                    bofCached = true,
                    eofCached = true,
                    seekableRanges =
                        listOf(
                            PlayerFullVideoCacheRangeInput(
                                startSeconds = 10.0,
                                endSeconds = 5.0,
                            ),
                        ),
                ),
            )
        malformedInputs.forEach { input ->
            assertEquals(
                PlayerFullVideoCacheStateEvidence.MALFORMED,
                resolvePlayerFullVideoCacheStateObservation(input).evidence,
            )
        }

        val observation =
            resolvePlayerFullVideoCacheStateObservation(
                PlayerFullVideoCacheStateInput(
                    structurallyValid = true,
                    fileCacheBytes = 768L * 1024L * 1024L,
                    bofCached = true,
                    eofCached = true,
                    seekableRanges =
                        listOf(
                            PlayerFullVideoCacheRangeInput(
                                startSeconds = -0.02,
                                endSeconds = 600.0,
                            ),
                        ),
                ),
            )

        assertEquals(PlayerFullVideoCacheStateEvidence.AVAILABLE, observation.evidence)
        assertEquals(768L * 1024L * 1024L, observation.fileCacheBytes)
        assertEquals(listOf(0.0 to 600.0), observation.seekableRanges)
        assertTrue(
            isPlayerFullVideoCacheComplete(
                phase = PlayerFullVideoCachePhase.ACTIVE,
                bofCached = observation.bofCached,
                eofCached = observation.eofCached,
                seekableRanges = observation.seekableRanges,
            ),
        )
    }

    @Test
    fun userSeekEventsRequireThePendingCommandToMatchTheCurrentLoad() {
        assertTrue(
            isPlayerUserSeekEvent(
                pendingUserSeekLoadCommandId = 41L,
                eventLoadCommandId = 41L,
            ),
        )
        assertFalse(
            isPlayerUserSeekEvent(
                pendingUserSeekLoadCommandId = null,
                eventLoadCommandId = 41L,
            ),
        )
        assertFalse(
            isPlayerUserSeekEvent(
                pendingUserSeekLoadCommandId = 40L,
                eventLoadCommandId = 41L,
            ),
        )
        assertFalse(
            isPlayerUserSeekEvent(
                pendingUserSeekLoadCommandId = 0L,
                eventLoadCommandId = 0L,
            ),
        )
    }

    @Test
    fun activeHardwareDecoderNormalizesMpvInactiveSentinels() {
        listOf(null, "", " ", "no", "NO", "none", "None").forEach { value ->
            assertEquals(null, normalizePlayerActiveHardwareDecoder(value))
        }
        assertEquals("mediacodec", normalizePlayerActiveHardwareDecoder(" mediacodec "))
    }

    @Test
    fun surfaceAspectRatioUsesTheActualPlayerGeometry() {
        assertEquals(2696.0 / 1260.0, requireNotNull(resolvePlayerSurfaceAspectRatio(2696, 1260)), 0.000001)
        assertEquals(1260.0 / 2696.0, requireNotNull(resolvePlayerSurfaceAspectRatio(1260, 2696)), 0.000001)
        assertEquals(1260.0 / 709.0, requireNotNull(resolvePlayerSurfaceAspectRatio(1260, 709)), 0.000001)
        assertEquals(1.0, requireNotNull(resolvePlayerSurfaceAspectRatio(1024, 1024)), 0.000001)
        assertEquals(null, resolvePlayerSurfaceAspectRatio(0, 1080))
        assertEquals(null, resolvePlayerSurfaceAspectRatio(1920, 0))
    }
}
