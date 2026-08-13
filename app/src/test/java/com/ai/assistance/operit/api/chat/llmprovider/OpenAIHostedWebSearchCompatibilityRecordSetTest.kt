package com.ai.assistance.operit.api.chat.llmprovider

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OpenAIHostedWebSearchCompatibilityRecordSetTest {
    @Test
    fun repositoryCommitsSuccessfulProbeEvidenceSynchronously() {
        val preferences = mock<SharedPreferences>()
        val editor = mock<SharedPreferences.Editor>()
        whenever(preferences.getString(any(), anyOrNull())).thenReturn(null)
        whenever(preferences.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.remove(any())).thenReturn(editor)
        whenever(editor.commit()).thenReturn(true)

        OpenAIHostedWebSearchCompatibilityRepository(preferences)
            .write(success("key-a", 1L))

        verify(editor).commit()
        verify(editor, never()).apply()
    }

    @Test
    fun repositoryExposesSynchronousPersistenceFailure() {
        val preferences = mock<SharedPreferences>()
        val editor = mock<SharedPreferences.Editor>()
        whenever(preferences.getString(any(), anyOrNull())).thenReturn(null)
        whenever(preferences.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.remove(any())).thenReturn(editor)
        whenever(editor.commit()).thenReturn(false)

        val failure =
            runCatching {
                OpenAIHostedWebSearchCompatibilityRepository(preferences)
                    .write(success("key-a", 1L))
            }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("could not be persisted") == true)
        verify(editor, never()).apply()
    }

    @Test
    fun multipleKeySuccessesRemainAvailableByExactFingerprint() {
        val first = success("key-a", 1L)
        val second = success("key-b", 2L)
        val recordSet =
            OpenAIHostedWebSearchCompatibilityRecordSetPolicy.recordSuccess(
                current =
                    OpenAIHostedWebSearchCompatibilityRecordSetPolicy.recordSuccess(
                        current = OpenAIHostedWebSearchCompatibilityRecordSet(),
                        record = first,
                    ),
                record = second,
            )

        assertEquals(first, recordSet.successFor("key-a"))
        assertEquals(second, recordSet.successFor("key-b"))
        assertTrue(recordSet.failures.isEmpty())
    }

    @Test
    fun failureForOneKeyDoesNotDeleteAnotherKeysSuccess() {
        val first = success("key-a", 1L)
        val recordSet =
            OpenAIHostedWebSearchCompatibilityRecordSetPolicy.recordFailure(
                current =
                    OpenAIHostedWebSearchCompatibilityRecordSetPolicy.recordSuccess(
                        current = OpenAIHostedWebSearchCompatibilityRecordSet(),
                        record = first,
                    ),
                record = failure("key-b", 2L),
            )

        assertEquals(first, recordSet.successFor("key-a"))
        assertNull(recordSet.successFor("key-b"))
        assertEquals("key-b", recordSet.failureFor("key-b")?.fingerprintDigest)
    }

    @Test
    fun successAndFailureAreMutuallyExclusiveForOneDigest() {
        val failed =
            OpenAIHostedWebSearchCompatibilityRecordSetPolicy.recordFailure(
                current = OpenAIHostedWebSearchCompatibilityRecordSet(),
                record = failure("key-a", 1L),
            )
        val succeeded =
            OpenAIHostedWebSearchCompatibilityRecordSetPolicy.recordSuccess(
                current = failed,
                record = success("key-a", 2L),
            )

        assertEquals("key-a", succeeded.successFor("key-a")?.fingerprintDigest)
        assertNull(succeeded.failureFor("key-a"))
    }

    @Test
    fun boundedSetRetainsTheSixteenMostRecentFingerprints() {
        val recordSet =
            (0 until 20).fold(OpenAIHostedWebSearchCompatibilityRecordSet()) {
                    current,
                    index,
                ->
                OpenAIHostedWebSearchCompatibilityRecordSetPolicy.recordSuccess(
                    current = current,
                    record = success("key-$index", index.toLong()),
                )
            }

        assertEquals(16, recordSet.successes.size + recordSet.failures.size)
        assertNull(recordSet.successFor("key-0"))
        assertNull(recordSet.successFor("key-3"))
        assertEquals("key-19", recordSet.successFor("key-19")?.fingerprintDigest)
    }

    @Test
    fun codecRoundTripPreservesMultipleKeysWithoutPlainCredentials() {
        val original =
            OpenAIHostedWebSearchCompatibilityRecordSet(
                successes = listOf(success("digest-a", 1L), success("digest-b", 2L)),
                failures = listOf(failure("digest-c", 3L)),
            )

        val encoded = OpenAIHostedWebSearchCompatibilityRecordSetCodec.encode(original)
        val decoded = OpenAIHostedWebSearchCompatibilityRecordSetCodec.decode(encoded)

        assertEquals(original, decoded)
        assertFalse(encoded.contains("credential-a"))
        assertFalse(encoded.contains("credential-b"))
    }

    @Test
    fun revisionFiveRecordSetIsRejectedByRevisionSixCodec() {
        val failure =
            runCatching {
                OpenAIHostedWebSearchCompatibilityRecordSetCodec.decode(
                    """
                    {
                      "schema_revision": 5,
                      "successes": [],
                      "failures": []
                    }
                    """.trimIndent()
                )
            }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun otherFingerprintsProduceStaleStatusForTheCurrentBinding() {
        val binding =
            OpenAIHostedWebSearchTestFixtures.binding(
                providerContract =
                    OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
            )

        assertEquals(
            "stale",
            OpenAIHostedWebSearchCompatibilityStatusResolver.resolve(
                binding = binding,
                compatibilityRecord = null,
                compatibilityFailureRecord = null,
                hasOtherCompatibilityRecords = true,
            ).state,
        )
        assertEquals(
            "missing",
            OpenAIHostedWebSearchCompatibilityStatusResolver.resolve(
                binding = binding,
                compatibilityRecord = null,
                compatibilityFailureRecord = null,
                hasOtherCompatibilityRecords = false,
            ).state,
        )
    }

    private fun success(
        fingerprintDigest: String,
        testedAtEpochMillis: Long,
    ): OpenAIHostedWebSearchCompatibilityRecord =
        OpenAIHostedWebSearchCompatibilityRecord(
            fingerprintDigest = fingerprintDigest,
            testedAtEpochMillis = testedAtEpochMillis,
            responseId = "resp_$fingerprintDigest",
            evidenceMode =
                OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS,
            schemaRevision = OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION,
        )

    private fun failure(
        fingerprintDigest: String,
        testedAtEpochMillis: Long,
    ): OpenAIHostedWebSearchCompatibilityFailureRecord =
        OpenAIHostedWebSearchCompatibilityFailureRecord(
            fingerprintDigest = fingerprintDigest,
            testedAtEpochMillis = testedAtEpochMillis,
            errorCode = OpenAIHostedWebSearchErrorCode.AUTH_REJECTED,
            httpStatus = 401,
            sanitizedMessage = "Rejected",
            providerErrorType = "authentication_error",
            providerErrorCode = "invalid_key",
            providerRequestId = "request-$fingerprintDigest",
            schemaRevision = OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION,
        )
}
