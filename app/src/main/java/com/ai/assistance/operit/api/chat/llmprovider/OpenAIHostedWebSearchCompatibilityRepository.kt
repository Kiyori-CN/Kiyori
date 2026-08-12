package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal data class OpenAIHostedWebSearchCompatibilityRecordSet(
    val successes: List<OpenAIHostedWebSearchCompatibilityRecord> = emptyList(),
    val failures: List<OpenAIHostedWebSearchCompatibilityFailureRecord> = emptyList(),
) {
    fun successFor(fingerprintDigest: String): OpenAIHostedWebSearchCompatibilityRecord? =
        successes.firstOrNull { record -> record.fingerprintDigest == fingerprintDigest }

    fun failureFor(
        fingerprintDigest: String,
    ): OpenAIHostedWebSearchCompatibilityFailureRecord? =
        failures.firstOrNull { record -> record.fingerprintDigest == fingerprintDigest }

    fun hasAnyRecord(): Boolean = successes.isNotEmpty() || failures.isNotEmpty()
}

/**
 * Maintains compatibility evidence independently for every exact binding fingerprint.
 *
 * A MODEL_CONFIG can rotate through multiple keys. Replacing one global success/failure slot would
 * discard evidence for the previous key, forcing an already-probed key to become stale whenever the
 * pool rotates. The set therefore keeps mutually exclusive success/failure state per digest and
 * bounds the combined history so app-private preferences cannot grow without limit.
 */
internal object OpenAIHostedWebSearchCompatibilityRecordSetPolicy {
    private const val MAX_RECORDS = 16

    fun recordSuccess(
        current: OpenAIHostedWebSearchCompatibilityRecordSet,
        record: OpenAIHostedWebSearchCompatibilityRecord,
    ): OpenAIHostedWebSearchCompatibilityRecordSet {
        requireCurrentSchema(record.schemaRevision)
        return trim(
            successes =
                current.successes.filterNot { existing ->
                    existing.fingerprintDigest == record.fingerprintDigest
                } + record,
            failures =
                current.failures.filterNot { existing ->
                    existing.fingerprintDigest == record.fingerprintDigest
                },
        )
    }

    fun recordFailure(
        current: OpenAIHostedWebSearchCompatibilityRecordSet,
        record: OpenAIHostedWebSearchCompatibilityFailureRecord,
    ): OpenAIHostedWebSearchCompatibilityRecordSet {
        requireCurrentSchema(record.schemaRevision)
        return trim(
            successes =
                current.successes.filterNot { existing ->
                    existing.fingerprintDigest == record.fingerprintDigest
                },
            failures =
                current.failures.filterNot { existing ->
                    existing.fingerprintDigest == record.fingerprintDigest
                } + record,
        )
    }

    private fun trim(
        successes: List<OpenAIHostedWebSearchCompatibilityRecord>,
        failures: List<OpenAIHostedWebSearchCompatibilityFailureRecord>,
    ): OpenAIHostedWebSearchCompatibilityRecordSet {
        val retained =
            buildList {
                successes.forEach { record ->
                    add(
                        TimedRecord(
                            fingerprintDigest = record.fingerprintDigest,
                            testedAtEpochMillis = record.testedAtEpochMillis,
                            success = record,
                            failure = null,
                        )
                    )
                }
                failures.forEach { record ->
                    add(
                        TimedRecord(
                            fingerprintDigest = record.fingerprintDigest,
                            testedAtEpochMillis = record.testedAtEpochMillis,
                            success = null,
                            failure = record,
                        )
                    )
                }
            }
                .sortedWith(
                    compareByDescending<TimedRecord>(TimedRecord::testedAtEpochMillis)
                        .thenBy(TimedRecord::fingerprintDigest)
                        .thenByDescending { entry -> entry.success != null }
                )
                .take(MAX_RECORDS)
        return OpenAIHostedWebSearchCompatibilityRecordSet(
            successes = retained.mapNotNull(TimedRecord::success),
            failures = retained.mapNotNull(TimedRecord::failure),
        )
    }

    private fun requireCurrentSchema(schemaRevision: Int) {
        require(schemaRevision == OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION) {
            "OpenAI Web Search compatibility record schema revision is invalid"
        }
    }

    private data class TimedRecord(
        val fingerprintDigest: String,
        val testedAtEpochMillis: Long,
        val success: OpenAIHostedWebSearchCompatibilityRecord?,
        val failure: OpenAIHostedWebSearchCompatibilityFailureRecord?,
    )
}

internal object OpenAIHostedWebSearchCompatibilityRecordSetCodec {
    fun encode(recordSet: OpenAIHostedWebSearchCompatibilityRecordSet): String =
        JSONObject()
            .put(
                "schema_revision",
                OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION,
            )
            .put(
                "successes",
                JSONArray().apply {
                    recordSet.successes.forEach { record ->
                        put(
                            JSONObject()
                                .put("fingerprint_digest", record.fingerprintDigest)
                                .put("tested_at_epoch_millis", record.testedAtEpochMillis)
                                .put("response_id", record.responseId)
                                .put("evidence_mode", record.evidenceMode.wireValue)
                        )
                    }
                },
            )
            .put(
                "failures",
                JSONArray().apply {
                    recordSet.failures.forEach { record ->
                        put(
                            JSONObject()
                                .put("fingerprint_digest", record.fingerprintDigest)
                                .put("tested_at_epoch_millis", record.testedAtEpochMillis)
                                .put("error_code", record.errorCode.name)
                                .put("http_status", record.httpStatus ?: JSONObject.NULL)
                                .put("sanitized_message", record.sanitizedMessage)
                                .put(
                                    "provider_error_type",
                                    record.providerErrorType ?: JSONObject.NULL,
                                )
                                .put(
                                    "provider_error_code",
                                    record.providerErrorCode ?: JSONObject.NULL,
                                )
                                .put(
                                    "provider_request_id",
                                    record.providerRequestId ?: JSONObject.NULL,
                                )
                        )
                    }
                },
            )
            .toString()

    fun decode(rawValue: String): OpenAIHostedWebSearchCompatibilityRecordSet {
        val root = JSONObject(rawValue)
        val schemaRevision = root.getInt("schema_revision")
        require(schemaRevision == OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION)
        val successes =
            root.getJSONArray("successes").mapObjects { value ->
                OpenAIHostedWebSearchCompatibilityRecord(
                    fingerprintDigest = value.getString("fingerprint_digest"),
                    testedAtEpochMillis = value.getLong("tested_at_epoch_millis"),
                    responseId = value.getString("response_id"),
                    evidenceMode =
                        OpenAIHostedWebSearchEvidenceMode.parse(
                            value.getString("evidence_mode")
                        ),
                    schemaRevision = schemaRevision,
                )
            }
        val failures =
            root.getJSONArray("failures").mapObjects { value ->
                OpenAIHostedWebSearchCompatibilityFailureRecord(
                    fingerprintDigest = value.getString("fingerprint_digest"),
                    testedAtEpochMillis = value.getLong("tested_at_epoch_millis"),
                    errorCode =
                        OpenAIHostedWebSearchErrorCode.valueOf(
                            value.getString("error_code")
                        ),
                    httpStatus =
                        if (value.isNull("http_status")) {
                            null
                        } else {
                            value.getInt("http_status")
                        },
                    sanitizedMessage = value.getString("sanitized_message"),
                    providerErrorType = value.optNullableString("provider_error_type"),
                    providerErrorCode = value.optNullableString("provider_error_code"),
                    providerRequestId = value.optNullableString("provider_request_id"),
                    schemaRevision = schemaRevision,
                )
            }
        require(
            successes.map(OpenAIHostedWebSearchCompatibilityRecord::fingerprintDigest)
                .intersect(
                    failures
                        .map(
                            OpenAIHostedWebSearchCompatibilityFailureRecord::fingerprintDigest
                        )
                        .toSet()
                )
                .isEmpty()
        )
        return OpenAIHostedWebSearchCompatibilityRecordSet(
            successes = successes,
            failures = failures,
        )
    }
}

internal class OpenAIHostedWebSearchCompatibilityRepository private constructor(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutationLock = Any()

    fun readSet(): OpenAIHostedWebSearchCompatibilityRecordSet {
        val rawValue = preferences.getString(RECORD_SET_KEY, null)?.trim().orEmpty()
        if (rawValue.isEmpty()) {
            return OpenAIHostedWebSearchCompatibilityRecordSet()
        }
        return runCatching {
            OpenAIHostedWebSearchCompatibilityRecordSetCodec.decode(rawValue)
        }.getOrElse {
            OpenAIHostedWebSearchCompatibilityRecordSet()
        }
    }

    fun write(record: OpenAIHostedWebSearchCompatibilityRecord) {
        synchronized(mutationLock) {
            val updated =
                OpenAIHostedWebSearchCompatibilityRecordSetPolicy.recordSuccess(
                    current = readSet(),
                    record = record,
                )
            preferences
                .edit()
                .putString(RECORD_SET_KEY, updated.encode())
                .remove(LEGACY_RECORD_KEY)
                .remove(LEGACY_FAILURE_RECORD_KEY)
                .apply()
        }
    }

    fun writeFailure(record: OpenAIHostedWebSearchCompatibilityFailureRecord) {
        synchronized(mutationLock) {
            val updated =
                OpenAIHostedWebSearchCompatibilityRecordSetPolicy.recordFailure(
                    current = readSet(),
                    record = record,
                )
            preferences
                .edit()
                .putString(RECORD_SET_KEY, updated.encode())
                .remove(LEGACY_RECORD_KEY)
                .remove(LEGACY_FAILURE_RECORD_KEY)
                .apply()
        }
    }

    fun clear() {
        synchronized(mutationLock) {
            preferences
                .edit()
                .remove(RECORD_SET_KEY)
                .remove(LEGACY_RECORD_KEY)
                .remove(LEGACY_FAILURE_RECORD_KEY)
                .apply()
        }
    }

    private fun OpenAIHostedWebSearchCompatibilityRecordSet.encode(): String =
        OpenAIHostedWebSearchCompatibilityRecordSetCodec.encode(this)

    companion object {
        private const val PREFERENCES_NAME = "openai_hosted_web_search_compatibility"
        private const val RECORD_SET_KEY =
            "${OpenAIHostedWebSearchContract.TOOLPKG_ID}.compatibility_record_set"
        private const val LEGACY_RECORD_KEY =
            "${OpenAIHostedWebSearchContract.TOOLPKG_ID}.compatibility"
        private const val LEGACY_FAILURE_RECORD_KEY =
            "${OpenAIHostedWebSearchContract.TOOLPKG_ID}.compatibility_failure"

        @Volatile
        private var instance: OpenAIHostedWebSearchCompatibilityRepository? = null

        fun getInstance(context: Context): OpenAIHostedWebSearchCompatibilityRepository =
            instance
                ?: synchronized(this) {
                    instance
                        ?: OpenAIHostedWebSearchCompatibilityRepository(
                            context.applicationContext
                        ).also { created ->
                            instance = created
                        }
                }
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) {
        null
    } else {
        optString(name).trim().takeIf(String::isNotEmpty)
    }

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    buildList {
        for (index in 0 until length()) {
            val value =
                opt(index) as? JSONObject
                    ?: throw IllegalArgumentException(
                        "Compatibility record item $index must be an object"
                    )
            add(transform(value))
        }
    }
