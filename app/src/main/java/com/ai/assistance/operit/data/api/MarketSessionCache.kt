package com.ai.assistance.operit.data.api

import java.security.MessageDigest

/** 唯一市场会话缓存只服务当前凭据；不自动重发已经提交的市场操作。 */
internal class MarketSessionCache {
    private var credentialFingerprint: ByteArray? = null
    private var session: String? = null

    @Synchronized
    fun get(readCredential: () -> String?, authenticate: (String) -> String): String {
        val credential = readCredential()?.takeIf { it.isNotBlank() }
        if (credential == null) {
            clear()
            error("GitHub login required")
        }
        val credentialHash = fingerprint(credential)
        if (credentialFingerprint?.contentEquals(credentialHash) == true) session?.let { return it }
        clear()
        val authenticated = authenticate(credential)
        check(authenticated.isNotBlank()) { "Market authentication returned an empty session" }
        val latest = readCredential()
        check(latest != null && fingerprint(latest).contentEquals(credentialHash)) { "GitHub account changed during market authentication" }
        credentialFingerprint = credentialHash
        session = authenticated
        return authenticated
    }

    private fun clear() {
        credentialFingerprint = null
        session = null
    }

    private fun fingerprint(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
}
