package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.net.IDN
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Locale
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.concurrent.withLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal data class BrowserSavedCredentialSummary(
    val id: String,
    val origin: String,
    val pageUrl: String,
    val host: String,
    val username: String,
    val updatedAtEpochMillis: Long,
)

@Serializable
internal data class BrowserSavedCredential(
    val id: String,
    val origin: String,
    val pageUrl: String,
    val username: String,
    val password: String,
    val usernameSelector: String,
    val passwordSelector: String,
    val updatedAtEpochMillis: Long,
)

internal data class BrowserCredentialVaultSnapshot(
    val credentials: List<BrowserSavedCredentialSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val isAvailable: Boolean
        get() = !isLoading && errorMessage == null
}

@Serializable
internal data class BrowserCredentialCapture(
    val pageUrl: String,
    val username: String,
    val password: String,
    val usernameSelector: String,
    val passwordSelector: String,
)

internal enum class BrowserCredentialMutationResult {
    CREATED,
    UPDATED,
    UNCHANGED,
}

/**
 * 网站凭据的唯一持久化 owner。
 *
 * 凭据文件放在 noBackupFilesDir，并以 Android Keystore 中不可导出的 AES-GCM 密钥逐条加密。
 * 这样原始快照和 Android 自动备份都不会产生无法在其他设备解密的凭据副本。
 */
internal class BrowserCredentialVault private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val lock = ReentrantLock()
    private val atomicFile = AtomicFile(resolveVaultFile(appContext))
    private val cipher = BrowserCredentialCipher()
    private val readiness = CompletableDeferred<Unit>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var credentials: List<BrowserSavedCredential> = emptyList()
    private var unavailableCause: Throwable? = null
    private val _state =
        MutableStateFlow(
            BrowserCredentialVaultSnapshot(isLoading = true),
        )

    val state: StateFlow<BrowserCredentialVaultSnapshot> = _state.asStateFlow()

    init {
        ioScope.launch {
            try {
                load()
            } finally {
                readiness.complete(Unit)
            }
        }
    }

    suspend fun credential(id: String): BrowserSavedCredential? {
        readiness.await()
        return lock.withLock {
            requireAvailable()
            credentials.firstOrNull { credential -> credential.id == id }
        }
    }

    suspend fun credentialForPage(pageUrl: String): BrowserSavedCredential? {
        readiness.await()
        return lock.withLock {
            requireAvailable()
            val origin = normalizeBrowserCredentialOrigin(pageUrl) ?: return@withLock null
            credentials
                .asSequence()
                .filter { credential -> credential.origin == origin }
                .maxByOrNull(BrowserSavedCredential::updatedAtEpochMillis)
        }
    }

    suspend fun saveCapture(
        capture: BrowserCredentialCapture,
    ): BrowserCredentialMutationResult {
        readiness.await()
        return lock.withLock {
            requireAvailable()
            val normalized = normalizeBrowserCredentialCapture(capture)
            val existingIndex =
                credentials.indexOfFirst { credential ->
                    credential.origin == normalized.origin &&
                        credential.username == normalized.username
                }
            val result: BrowserCredentialMutationResult
            val updatedCredentials =
                if (existingIndex >= 0) {
                    val existing = credentials[existingIndex]
                    val updated =
                        existing.copy(
                            pageUrl = normalized.pageUrl,
                            password = normalized.password,
                            usernameSelector = normalized.usernameSelector,
                            passwordSelector = normalized.passwordSelector,
                            updatedAtEpochMillis = System.currentTimeMillis(),
                        )
                    if (
                        existing.pageUrl == updated.pageUrl &&
                            existing.password == updated.password &&
                            existing.usernameSelector == updated.usernameSelector &&
                            existing.passwordSelector == updated.passwordSelector
                    ) {
                        result = BrowserCredentialMutationResult.UNCHANGED
                        credentials
                    } else {
                        result = BrowserCredentialMutationResult.UPDATED
                        credentials.toMutableList().apply { set(existingIndex, updated) }
                    }
                } else {
                    result = BrowserCredentialMutationResult.CREATED
                    credentials +
                        BrowserSavedCredential(
                            id = UUID.randomUUID().toString(),
                            origin = normalized.origin,
                            pageUrl = normalized.pageUrl,
                            username = normalized.username,
                            password = normalized.password,
                            usernameSelector = normalized.usernameSelector,
                            passwordSelector = normalized.passwordSelector,
                            updatedAtEpochMillis = System.currentTimeMillis(),
                        )
                }
            if (result != BrowserCredentialMutationResult.UNCHANGED) {
                persist(updatedCredentials)
            }
            result
        }
    }

    suspend fun updateCredential(id: String, username: String, password: String) {
        readiness.await()
        lock.withLock {
            requireAvailable()
            val normalizedUsername = normalizeBrowserCredentialUsername(username)
            val normalizedPassword = normalizeBrowserCredentialPassword(password)
            val index =
                credentials.indexOfFirst { credential -> credential.id == id }
                    .also { credentialIndex ->
                        require(credentialIndex >= 0) { "Browser credential does not exist: $id" }
                    }
            val current = credentials[index]
            require(
                credentials.none { credential ->
                    credential.id != id &&
                        credential.origin == current.origin &&
                        credential.username == normalizedUsername
                },
            ) {
                "The website already has a saved credential for this account"
            }
            val updated =
                current.copy(
                    username = normalizedUsername,
                    password = normalizedPassword,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            persist(credentials.toMutableList().apply { set(index, updated) })
        }
    }

    suspend fun deleteCredential(id: String): Boolean {
        readiness.await()
        return lock.withLock {
            requireAvailable()
            val updated = credentials.filterNot { credential -> credential.id == id }
            if (updated.size == credentials.size) {
                false
            } else {
                persist(updated)
                true
            }
        }
    }

    private fun load() {
        lock.withLock {
            try {
                credentials =
                    if (atomicFile.baseFile.exists() || atomicBackupFile().exists()) {
                        atomicFile.openRead().use { input ->
                            decodeEncryptedBrowserCredentialVault(
                                input.readBytes().toString(StandardCharsets.UTF_8),
                                cipher,
                            )
                        }
                    } else {
                        emptyList()
                    }
                unavailableCause = null
                publishSnapshot()
            } catch (error: Exception) {
                credentials = emptyList()
                unavailableCause = error
                _state.value =
                    BrowserCredentialVaultSnapshot(
                        errorMessage = "网站密码保险库无法解锁，请检查设备安全状态",
                    )
                AppLogger.e(TAG, "Unable to load encrypted browser credential vault", error)
            }
        }
    }

    private fun persist(updated: List<BrowserSavedCredential>) {
        val directory =
            requireNotNull(atomicFile.baseFile.parentFile) {
                "Browser credential vault has no parent directory"
            }
        require(directory.isDirectory || directory.mkdirs()) {
            "Unable to create browser credential vault directory"
        }
        val payload = encodeEncryptedBrowserCredentialVault(updated, cipher)
        var output: java.io.FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(payload.toByteArray(StandardCharsets.UTF_8))
            atomicFile.finishWrite(output)
            output = null
            credentials = updated.sortedWith(browserCredentialOrdering())
            publishSnapshot()
        } catch (error: Exception) {
            output?.let(atomicFile::failWrite)
            throw error
        }
    }

    private fun publishSnapshot() {
        _state.value =
            BrowserCredentialVaultSnapshot(
                credentials =
                    credentials
                        .sortedWith(browserCredentialOrdering())
                        .map(BrowserSavedCredential::toSummary),
            )
    }

    private fun requireAvailable() {
        val cause = unavailableCause
        check(cause == null) {
            "Browser credential vault is unavailable: ${cause?.javaClass?.simpleName}"
        }
    }

    private fun atomicBackupFile(): File =
        File(atomicFile.baseFile.parentFile, "${atomicFile.baseFile.name}.bak")

    companion object {
        private const val TAG = "BrowserCredentialVault"

        @Volatile private var instance: BrowserCredentialVault? = null

        fun getInstance(context: Context): BrowserCredentialVault =
            instance ?: synchronized(this) {
                instance
                    ?: BrowserCredentialVault(context.applicationContext).also { vault ->
                        instance = vault
                    }
            }
    }
}

internal fun normalizeBrowserCredentialOrigin(rawUrl: String): String? {
    val uri =
        try {
            URI(rawUrl.trim())
        } catch (_: Exception) {
            return null
        }
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
    if (scheme != "http" && scheme != "https") {
        return null
    }
    if (uri.rawUserInfo != null) {
        return null
    }
    val rawHost = uri.host?.trim()?.trimEnd('.') ?: return null
    if (rawHost.isEmpty()) {
        return null
    }
    val host =
        if (rawHost.contains(':')) {
            rawHost.lowercase(Locale.ROOT)
        } else {
            try {
                IDN.toASCII(rawHost).lowercase(Locale.ROOT)
            } catch (_: IllegalArgumentException) {
                return null
            }
        }
    val port =
        when {
            uri.port < 0 -> -1
            scheme == "http" && uri.port == 80 -> -1
            scheme == "https" && uri.port == 443 -> -1
            else -> uri.port
        }
    val authorityHost = if (host.contains(':')) "[$host]" else host
    return if (port >= 0) "$scheme://$authorityHost:$port" else "$scheme://$authorityHost"
}

internal fun sanitizeBrowserCredentialPageUrl(rawUrl: String): String? {
    val origin = normalizeBrowserCredentialOrigin(rawUrl) ?: return null
    val uri =
        try {
            URI(rawUrl.trim())
        } catch (_: Exception) {
            return null
        }
    val path = uri.rawPath?.takeIf(String::isNotBlank) ?: "/"
    val sanitized = "$origin$path"
    return sanitized.takeIf { value -> value.length <= MAX_PAGE_URL_LENGTH }
}

internal fun decodeBrowserCredentialCapturePayload(rawPayload: String): BrowserCredentialCapture {
    require(rawPayload.length <= MAX_CAPTURE_PAYLOAD_LENGTH) {
        "Browser credential capture payload is too large"
    }
    return BrowserCredentialJson.decodeFromString(rawPayload)
}

private fun normalizeBrowserCredentialCapture(
    capture: BrowserCredentialCapture,
): BrowserSavedCredential {
    val origin =
        requireNotNull(normalizeBrowserCredentialOrigin(capture.pageUrl)) {
            "Browser credential capture URL is not a supported HTTP/HTTPS page"
        }
    val pageUrl =
        requireNotNull(sanitizeBrowserCredentialPageUrl(capture.pageUrl)) {
            "Browser credential capture URL is invalid"
        }
    return BrowserSavedCredential(
        id = "",
        origin = origin,
        pageUrl = pageUrl,
        username = normalizeBrowserCredentialUsername(capture.username),
        password = normalizeBrowserCredentialPassword(capture.password),
        usernameSelector =
            normalizeBrowserCredentialSelector(
                capture.usernameSelector,
                label = "username",
            ),
        passwordSelector =
            normalizeBrowserCredentialSelector(
                capture.passwordSelector,
                label = "password",
            ),
        updatedAtEpochMillis = 0L,
    )
}

private fun normalizeBrowserCredentialUsername(username: String): String {
    val normalized = username.trim()
    require(normalized.isNotEmpty()) { "Browser credential username must not be blank" }
    require(normalized.length <= MAX_USERNAME_LENGTH) {
        "Browser credential username is too long"
    }
    return normalized
}

private fun normalizeBrowserCredentialPassword(password: String): String {
    require(password.isNotEmpty()) { "Browser credential password must not be blank" }
    require(password.length <= MAX_PASSWORD_LENGTH) {
        "Browser credential password is too long"
    }
    return password
}

private fun normalizeBrowserCredentialSelector(selector: String, label: String): String {
    val normalized = selector.trim()
    require(normalized.isNotEmpty()) { "Browser credential $label selector must not be blank" }
    require(normalized.length <= MAX_SELECTOR_LENGTH) {
        "Browser credential $label selector is too long"
    }
    return normalized
}

private fun BrowserSavedCredential.toSummary(): BrowserSavedCredentialSummary =
    BrowserSavedCredentialSummary(
        id = id,
        origin = origin,
        pageUrl = pageUrl,
        host =
            requireNotNull(URI(origin).host) {
                "Stored browser credential origin has no host: $origin"
            },
        username = username,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

private fun browserCredentialOrdering(): Comparator<BrowserSavedCredential> =
    compareBy<BrowserSavedCredential>(
        { credential -> credential.origin },
        { credential -> credential.username.lowercase(Locale.ROOT) },
    ).thenByDescending(BrowserSavedCredential::updatedAtEpochMillis)

internal fun encodeEncryptedBrowserCredentialVault(
    credentials: List<BrowserSavedCredential>,
    cipher: BrowserCredentialRecordCipher,
): String {
    require(credentials.size <= MAX_CREDENTIAL_COUNT) {
        "Browser credential vault exceeds $MAX_CREDENTIAL_COUNT entries"
    }
    val ids = HashSet<String>()
    val uniqueAccounts = HashSet<Pair<String, String>>()
    val records =
        credentials.map { credential ->
            validateBrowserSavedCredential(credential)
            require(ids.add(credential.id)) {
                "Duplicate browser credential id: ${credential.id}"
            }
            require(uniqueAccounts.add(credential.origin to credential.username)) {
                "Duplicate browser credential account for ${credential.origin}"
            }
            val encrypted =
                cipher.encrypt(
                    recordId = credential.id,
                    plaintext = encodeBrowserSavedCredential(credential),
                )
            EncryptedBrowserCredentialRecord(
                id = credential.id,
                ivBase64 = encrypted.ivBase64,
                ciphertextBase64 = encrypted.ciphertextBase64,
            )
        }
    return BrowserCredentialJson.encodeToString(
        BrowserCredentialVaultPayload(
            schemaVersion = BROWSER_CREDENTIAL_VAULT_SCHEMA_VERSION,
            records = records,
        ),
    )
}

internal fun decodeEncryptedBrowserCredentialVault(
    rawPayload: String,
    cipher: BrowserCredentialRecordCipher,
): List<BrowserSavedCredential> {
    val root = BrowserCredentialJson.decodeFromString<BrowserCredentialVaultPayload>(rawPayload)
    require(root.schemaVersion == BROWSER_CREDENTIAL_VAULT_SCHEMA_VERSION) {
        "Unsupported browser credential vault schema"
    }
    require(root.records.size <= MAX_CREDENTIAL_COUNT) {
        "Browser credential vault exceeds $MAX_CREDENTIAL_COUNT entries"
    }
    val ids = HashSet<String>()
    val uniqueAccounts = HashSet<Pair<String, String>>()
    return buildList {
        root.records.forEach { record ->
            val id = record.id
            require(ids.add(id)) { "Duplicate browser credential id: $id" }
            val plaintext =
                cipher.decrypt(
                    recordId = id,
                    ivBase64 = record.ivBase64,
                    ciphertextBase64 = record.ciphertextBase64,
                )
            val credential = decodeBrowserSavedCredential(plaintext)
            require(credential.id == id) {
                "Encrypted browser credential id does not match its envelope"
            }
            require(uniqueAccounts.add(credential.origin to credential.username)) {
                "Duplicate browser credential account for ${credential.origin}"
            }
            add(credential)
        }
    }.sortedWith(browserCredentialOrdering())
}

private fun encodeBrowserSavedCredential(credential: BrowserSavedCredential): ByteArray =
    BrowserCredentialJson
        .encodeToString(credential)
        .toByteArray(StandardCharsets.UTF_8)

private fun decodeBrowserSavedCredential(payload: ByteArray): BrowserSavedCredential =
    BrowserCredentialJson
        .decodeFromString<BrowserSavedCredential>(
            payload.toString(StandardCharsets.UTF_8),
        )
        .also(::validateBrowserSavedCredential)

private fun validateBrowserSavedCredential(credential: BrowserSavedCredential) {
    require(runCatching { UUID.fromString(credential.id) }.isSuccess) {
        "Browser credential id is invalid"
    }
    require(normalizeBrowserCredentialOrigin(credential.origin) == credential.origin) {
        "Browser credential origin is not normalized"
    }
    require(normalizeBrowserCredentialOrigin(credential.pageUrl) == credential.origin) {
        "Browser credential page URL does not match its origin"
    }
    require(sanitizeBrowserCredentialPageUrl(credential.pageUrl) == credential.pageUrl) {
        "Browser credential page URL is not sanitized"
    }
    normalizeBrowserCredentialUsername(credential.username)
    normalizeBrowserCredentialPassword(credential.password)
    normalizeBrowserCredentialSelector(credential.usernameSelector, "username")
    normalizeBrowserCredentialSelector(credential.passwordSelector, "password")
    require(credential.updatedAtEpochMillis > 0L) {
        "Browser credential update timestamp must be positive"
    }
}

internal data class EncryptedBrowserCredential(
    val ivBase64: String,
    val ciphertextBase64: String,
)

@Serializable
private data class BrowserCredentialVaultPayload(
    val schemaVersion: Int,
    val records: List<EncryptedBrowserCredentialRecord>,
)

@Serializable
private data class EncryptedBrowserCredentialRecord(
    val id: String,
    val ivBase64: String,
    val ciphertextBase64: String,
)

internal interface BrowserCredentialRecordCipher {
    fun encrypt(recordId: String, plaintext: ByteArray): EncryptedBrowserCredential

    fun decrypt(
        recordId: String,
        ivBase64: String,
        ciphertextBase64: String,
    ): ByteArray
}

private class BrowserCredentialCipher : BrowserCredentialRecordCipher {
    override fun encrypt(
        recordId: String,
        plaintext: ByteArray,
    ): EncryptedBrowserCredential {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(recordId.toByteArray(StandardCharsets.UTF_8))
        val encrypted = cipher.doFinal(plaintext)
        return EncryptedBrowserCredential(
            ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            ciphertextBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP),
        )
    }

    override fun decrypt(
        recordId: String,
        ivBase64: String,
        ciphertextBase64: String,
    ): ByteArray {
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        require(iv.size == GCM_IV_SIZE_BYTES) { "Browser credential IV length is invalid" }
        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        cipher.updateAAD(recordId.toByteArray(StandardCharsets.UTF_8))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore =
            KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply {
                load(null)
            }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing != null) {
            return existing as SecretKey
        }
        val keyGenerator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE_PROVIDER,
            )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "kiyori_browser_credentials_v1"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_SIZE_BYTES = 12
    }
}

private fun resolveVaultFile(context: Context): File {
    val directory = File(context.noBackupFilesDir, BROWSER_CREDENTIAL_DIRECTORY_NAME)
    return File(directory, BROWSER_CREDENTIAL_FILE_NAME)
}

private const val BROWSER_CREDENTIAL_DIRECTORY_NAME = "browser_credentials"
private const val BROWSER_CREDENTIAL_FILE_NAME = "vault.json"
private const val BROWSER_CREDENTIAL_VAULT_SCHEMA_VERSION = 1
private const val MAX_CREDENTIAL_COUNT = 1_000
private const val MAX_PAGE_URL_LENGTH = 4_096
private const val MAX_USERNAME_LENGTH = 512
private const val MAX_PASSWORD_LENGTH = 4_096
private const val MAX_SELECTOR_LENGTH = 2_048
private const val MAX_CAPTURE_PAYLOAD_LENGTH = 16_384

private val BrowserCredentialJson =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = false
    }
