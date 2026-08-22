package com.ai.assistance.operit.core.tools.javascript.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.ai.assistance.operit.util.AppLogger
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface ScriptNetworkStoreState {
    data class Ready(val config: ScriptNetworkConfig) : ScriptNetworkStoreState
    data class Unreadable(val message: String) : ScriptNetworkStoreState
}

class ScriptNetworkConfigStore private constructor(context: Context) {
    companion object {
        private const val TAG = "ScriptNetworkStore"
        private const val KEY_ALIAS = "kiyori_script_network_v1"
        private const val DIRECTORY_NAME = "script_network"
        private const val FILE_NAME = "config.v1.enc"
        private const val FILE_VERSION = 1
        private const val IV_SIZE = 12
        private const val GCM_TAG_BITS = 128
        private val MAGIC = byteArrayOf('K'.code.toByte(), 'S'.code.toByte(), 'N'.code.toByte(), '1'.code.toByte())

        @Volatile
        private var instance: ScriptNetworkConfigStore? = null

        fun getInstance(context: Context): ScriptNetworkConfigStore =
            instance
                ?: synchronized(this) {
                    instance
                        ?: ScriptNetworkConfigStore(context.applicationContext).also { instance = it }
                }
    }

    private val appContext = context.applicationContext
    private val rootDirectory = appContext.noBackupFilesDir.resolve(DIRECTORY_NAME)
    private val atomicFile = AtomicFile(rootDirectory.resolve(FILE_NAME))
    private val secureRandom = SecureRandom()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = false
    }
    private val lock = Any()
    private val mutableState = MutableStateFlow(loadState())
    val state: StateFlow<ScriptNetworkStoreState> = mutableState.asStateFlow()

    fun currentConfig(): ScriptNetworkConfig =
        when (val current = mutableState.value) {
            is ScriptNetworkStoreState.Ready -> current.config
            is ScriptNetworkStoreState.Unreadable ->
                throw ScriptNetworkException(
                    ScriptNetworkErrorCode.CONFIG_INVALID,
                    current.message,
                )
        }

    fun update(transform: (ScriptNetworkConfig) -> ScriptNetworkConfig): ScriptNetworkConfig =
        synchronized(lock) {
            val current = currentConfig()
            val updated = transform(current).also(::validateSchema)
            writeEncrypted(updated)
            mutableState.value = ScriptNetworkStoreState.Ready(updated)
            updated
        }

    fun replace(config: ScriptNetworkConfig): ScriptNetworkConfig = update { config }

    fun reset(): ScriptNetworkConfig =
        synchronized(lock) {
            // AtomicFile.delete() removes the base, backup, and pending-new files as one reset boundary.
            atomicFile.delete()
            val keyStore = openKeyStore()
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
            val initial = ScriptNetworkConfig()
            mutableState.value = ScriptNetworkStoreState.Ready(initial)
            initial
        }

    private fun loadState(): ScriptNetworkStoreState {
        if (!atomicFile.baseFile.exists()) {
            return ScriptNetworkStoreState.Ready(ScriptNetworkConfig())
        }
        return try {
            val plaintext = decrypt(atomicFile.readFully())
            val config = json.decodeFromString<ScriptNetworkConfig>(plaintext.toString(Charsets.UTF_8))
            validateSchema(config)
            ScriptNetworkStoreState.Ready(config)
        } catch (error: Exception) {
            AppLogger.e(TAG, "Encrypted script network configuration is unreadable", error)
            ScriptNetworkStoreState.Unreadable(
                "Encrypted script proxy settings cannot be read. Reset them from the script settings page.",
            )
        }
    }

    private fun validateSchema(config: ScriptNetworkConfig) {
        require(config.schemaVersion == ScriptNetworkConfig.CURRENT_SCHEMA_VERSION) {
            "Unsupported script network configuration schema: ${config.schemaVersion}"
        }
    }

    private fun writeEncrypted(config: ScriptNetworkConfig) {
        if (!rootDirectory.exists() && !rootDirectory.mkdirs()) {
            throw IllegalStateException("Unable to create the private script network directory")
        }
        val plaintext = json.encodeToString(config).toByteArray(Charsets.UTF_8)
        val payload = encrypt(plaintext)
        val output = atomicFile.startWrite()
        try {
            output.write(payload)
            output.flush()
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_SIZE).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
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
            throw IllegalArgumentException("Encrypted script network configuration is truncated")
        }
        val buffer = ByteBuffer.wrap(payload)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        require(magic.contentEquals(MAGIC)) { "Encrypted script network configuration has invalid magic" }
        require(buffer.int == FILE_VERSION) { "Unsupported encrypted script network file version" }
        val ivLength = buffer.get().toInt() and 0xff
        require(ivLength == IV_SIZE && buffer.remaining() > ivLength + 16) {
            "Encrypted script network configuration has an invalid IV"
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
            ?: throw IllegalStateException("The script network encryption key is missing")

    private fun openKeyStore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
