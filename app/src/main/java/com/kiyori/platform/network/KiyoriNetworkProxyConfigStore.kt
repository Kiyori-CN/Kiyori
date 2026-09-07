package com.kiyori.platform.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.kiyori.platform.logging.KiyoriLogger
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal fun decodeKiyoriNetworkProxyConfigJson(
    rawJson: String,
    json: Json,
): KiyoriNetworkProxyConfig {
    val root = json.decodeFromString<JsonObject>(rawJson)
    // Schema 4's unpublished module override map is the only removed field. Remove exactly that
    // field before strict decoding so unrelated misspellings or future incompatibilities remain
    // visible as an unreadable configuration instead of being silently accepted.
    val migratedRoot = JsonObject(root - "moduleModes")
    return json.decodeFromJsonElement<KiyoriNetworkProxyConfig>(migratedRoot)
}

internal fun migrateKiyoriNetworkProxyConfig(
    config: KiyoriNetworkProxyConfig,
): KiyoriNetworkProxyConfig =
    when (config.schemaVersion) {
        KiyoriNetworkProxyConfig.CURRENT_SCHEMA_VERSION -> config
        2 ->
            config.copy(
                schemaVersion = KiyoriNetworkProxyConfig.CURRENT_SCHEMA_VERSION,
                customRules = emptyList(),
            )
        3 ->
            config.copy(
                schemaVersion = KiyoriNetworkProxyConfig.CURRENT_SCHEMA_VERSION,
                customRules = config.customRules.map(::migrateKiyoriNetworkProxyCustomRule),
            )
        4 -> config.copy(schemaVersion = KiyoriNetworkProxyConfig.CURRENT_SCHEMA_VERSION)
        else -> config
    }

internal fun migrateKiyoriNetworkProxyCustomRule(
    rule: KiyoriNetworkProxyRule,
): KiyoriNetworkProxyRule {
    val rawPattern = rule.pattern.trim().lowercase()
    val suffix = rawPattern.startsWith("*.") || rawPattern.startsWith(".")
    return rule.copy(
        pattern = if (suffix) rawPattern.removePrefix("*.").removePrefix(".") else rawPattern,
        type = if (suffix) KiyoriNetworkRuleType.DOMAIN_SUFFIX else rule.type,
    )
}

sealed interface KiyoriNetworkProxyStoreState {
    data class Ready(val config: KiyoriNetworkProxyConfig) : KiyoriNetworkProxyStoreState

    data class Unreadable(val message: String) : KiyoriNetworkProxyStoreState
}

class KiyoriNetworkProxyConfigStore private constructor(context: Context) {
    companion object {
        private const val TAG = "KiyoriNetworkProxyStore"
        private const val KEY_ALIAS = "kiyori_network_proxy_v2"
        private const val OBSOLETE_KEY_ALIAS = "kiyori_network_proxy_v1"
        private const val DIRECTORY_NAME = "network_proxy"
        private const val FILE_NAME = "config.v2.enc"
        private const val OBSOLETE_FILE_NAME = "config.v1.enc"
        private const val FILE_VERSION = 1
        private const val IV_SIZE = 12
        // The encrypted header plus the random GCM IV changes on every successful save.
        private const val FILE_STATE_PREFIX_BYTES = 21
        private const val GCM_TAG_BITS = 128
        private val MAGIC =
            byteArrayOf(
                'K'.code.toByte(),
                'N'.code.toByte(),
                'P'.code.toByte(),
                '1'.code.toByte(),
            )

        @Volatile
        private var instance: KiyoriNetworkProxyConfigStore? = null

        fun getInstance(context: Context): KiyoriNetworkProxyConfigStore =
            instance
                ?: synchronized(this) {
                    instance
                        ?: KiyoriNetworkProxyConfigStore(context.applicationContext).also {
                            instance = it
                        }
                }
    }

    private val rootDirectory = context.noBackupFilesDir.resolve(DIRECTORY_NAME)
    private val atomicFile = AtomicFile(rootDirectory.resolve(FILE_NAME))
    private val obsoleteAtomicFile = AtomicFile(rootDirectory.resolve(OBSOLETE_FILE_NAME))
    private val json =
        Json {
            encodeDefaults = true
            explicitNulls = false
        }
    private val lock = Any()
    private val mutableState = MutableStateFlow(initializeState())
    private var observedFileState = readFileState()
    val state: StateFlow<KiyoriNetworkProxyStoreState> = mutableState.asStateFlow()

    fun currentConfig(): KiyoriNetworkProxyConfig = synchronized(lock) {
        reloadIfChangedLocked()
        when (val current = mutableState.value) {
            is KiyoriNetworkProxyStoreState.Ready -> current.config
            is KiyoriNetworkProxyStoreState.Unreadable ->
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CONFIG_INVALID,
                    current.message,
                )
        }
    }

    fun update(
        transform: (KiyoriNetworkProxyConfig) -> KiyoriNetworkProxyConfig,
    ): KiyoriNetworkProxyConfig =
        synchronized(lock) {
            val updated = transform(currentConfig()).also(KiyoriNetworkProxyPolicy::validateSchema)
            KiyoriNetworkProxyPolicy.validateEnabledConfig(updated)
            try {
                writeEncrypted(updated)
            } catch (error: KiyoriNetworkException) {
                throw error
            } catch (error: Exception) {
                KiyoriLogger.e(TAG, "Unable to persist encrypted application proxy settings", error)
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.SETTINGS_WRITE_FAILED,
                    "The encrypted network proxy settings could not be written.",
                    error,
                )
            }
            mutableState.value = KiyoriNetworkProxyStoreState.Ready(updated)
            observedFileState = readFileState()
            updated
        }

    fun replace(config: KiyoriNetworkProxyConfig): KiyoriNetworkProxyConfig = update { config }

    fun reset(): KiyoriNetworkProxyConfig =
        synchronized(lock) {
            try {
                // AtomicFile owns its base, backup and pending-new files as one reset boundary.
                atomicFile.delete()
                obsoleteAtomicFile.delete()
                val keyStore = openKeyStore()
                if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
                if (keyStore.containsAlias(OBSOLETE_KEY_ALIAS)) {
                    keyStore.deleteEntry(OBSOLETE_KEY_ALIAS)
                }
                val initial = KiyoriNetworkProxyConfig()
                mutableState.value = KiyoriNetworkProxyStoreState.Ready(initial)
                observedFileState = readFileState()
                initial
            } catch (error: Exception) {
                KiyoriLogger.e(TAG, "Unable to reset encrypted application proxy settings", error)
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.SETTINGS_WRITE_FAILED,
                    "The encrypted network proxy settings could not be reset.",
                    error,
                )
            }
        }

    private fun initializeState(): KiyoriNetworkProxyStoreState {
        // Schema v1 never completed its first Android Keystore write on device. Remove that
        // unpublished development state so schema v2 has one unambiguous storage owner.
        obsoleteAtomicFile.delete()
        return loadState()
    }

    private fun loadState(): KiyoriNetworkProxyStoreState {
        if (!atomicFile.baseFile.exists()) {
            return KiyoriNetworkProxyStoreState.Ready(KiyoriNetworkProxyConfig())
        }
        return try {
            val plaintext = decrypt(atomicFile.readFully())
            val config = migrateKiyoriNetworkProxyConfig(
                decodeKiyoriNetworkProxyConfigJson(plaintext.toString(Charsets.UTF_8), json),
            )
            KiyoriNetworkProxyPolicy.validateSchema(config)
            KiyoriNetworkProxyStoreState.Ready(config)
        } catch (error: Exception) {
            KiyoriLogger.e(TAG, "Encrypted application proxy configuration is unreadable", error)
            KiyoriNetworkProxyStoreState.Unreadable(
                "Encrypted network proxy settings cannot be read. Reset them from Network Proxy settings.",
            )
        }
    }

    private fun reloadIfChangedLocked() {
        val currentFileState = readFileState()
        if (currentFileState == observedFileState) return
        mutableState.value = loadState()
        observedFileState = currentFileState
    }

    private fun readFileState(): ConfigFileState =
        atomicFile.baseFile.let { file ->
            ConfigFileState(
                exists = file.isFile,
                lastModifiedEpochMillis = file.lastModified(),
                length = file.length(),
                encryptedPrefix = readEncryptedPrefix(file),
            )
        }

    private fun readEncryptedPrefix(file: java.io.File): String {
        if (!file.isFile) return ""
        return file.inputStream().use { input ->
            val bytes = ByteArray(FILE_STATE_PREFIX_BYTES)
            var offset = 0
            while (offset < bytes.size) {
                val count = input.read(bytes, offset, bytes.size - offset)
                if (count <= 0) break
                offset += count
            }
            bytes.copyOf(offset).toString(Charsets.ISO_8859_1)
        }
    }

    private data class ConfigFileState(
        val exists: Boolean,
        val lastModifiedEpochMillis: Long,
        val length: Long,
        val encryptedPrefix: String,
    )

    private fun writeEncrypted(config: KiyoriNetworkProxyConfig) {
        if (!rootDirectory.exists() && !rootDirectory.mkdirs()) {
            throw IllegalStateException("Unable to create the private network proxy directory")
        }
        val plaintext = json.encodeToString(config).toByteArray(Charsets.UTF_8)
        val output = atomicFile.startWrite()
        try {
            output.write(encrypt(plaintext))
            output.flush()
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // randomizedEncryptionRequired forbids caller-provided IVs. AndroidKeyStore must create
        // the nonce during ENCRYPT_MODE initialization; supplying one makes every save fail.
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        require(iv.size == IV_SIZE) { "AndroidKeyStore returned an invalid GCM IV" }
        cipher.updateAAD(fileHeader())
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArrayOutputStream(MAGIC.size + 4 + 1 + iv.size + ciphertext.size).use { output ->
            output.write(MAGIC)
            output.write(ByteBuffer.allocate(4).putInt(FILE_VERSION).array())
            output.write(iv.size)
            output.write(iv)
            output.write(ciphertext)
            output.toByteArray()
        }
    }

    private fun decrypt(payload: ByteArray): ByteArray {
        if (payload.size <= MAGIC.size + 4 + 1 + IV_SIZE + 16) {
            throw IllegalArgumentException("Encrypted network proxy configuration is truncated")
        }
        val buffer = ByteBuffer.wrap(payload)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        require(magic.contentEquals(MAGIC)) {
            "Encrypted network proxy configuration has invalid magic"
        }
        require(buffer.int == FILE_VERSION) {
            "Unsupported encrypted network proxy file version"
        }
        val ivLength = buffer.get().toInt() and 0xff
        require(ivLength == IV_SIZE && buffer.remaining() > ivLength + 16) {
            "Encrypted network proxy configuration has an invalid IV"
        }
        val iv = ByteArray(ivLength).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, requireExistingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(fileHeader())
        return cipher.doFinal(ciphertext)
    }

    private fun fileHeader(): ByteArray =
        ByteBuffer.allocate(MAGIC.size + 4)
            .put(MAGIC)
            .putInt(FILE_VERSION)
            .array()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = openKeyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
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
        return generator.generateKey()
    }

    private fun requireExistingKey(): SecretKey =
        openKeyStore().getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw IllegalStateException("The network proxy encryption key is missing")

    private fun openKeyStore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
