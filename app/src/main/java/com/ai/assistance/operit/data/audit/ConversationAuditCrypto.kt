package com.ai.assistance.operit.data.audit

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ConversationAuditEncryptedBytes(
    val ciphertext: ByteArray,
    val nonceBase64: String,
    val algorithm: String,
    val keyAlias: String,
)

data class ConversationAuditSignature(
    val signatureBase64: String,
    val publicKeyBase64: String,
    val algorithm: String,
)

interface ConversationAuditCrypto {
    fun encrypt(plaintext: ByteArray): ConversationAuditEncryptedBytes

    fun decrypt(
        ciphertext: ByteArray,
        nonceBase64: String,
        keyAlias: String,
    ): ByteArray

    fun sign(value: ByteArray): ConversationAuditSignature

    fun requireSigningPublicKeyBase64(): String

    fun verify(
        value: ByteArray,
        signatureBase64: String,
        publicKeyBase64: String,
        algorithm: String,
    ): Boolean
}

/**
 * 审计正文与链封印的唯一生产加密实现。
 *
 * payload 与签名使用不同 Keystore key，避免正文解密能力和防篡改身份共享同一个密钥用途。
 */
class AndroidConversationAuditCrypto : ConversationAuditCrypto {
    override fun encrypt(plaintext: ByteArray): ConversationAuditEncryptedBytes {
        val cipher = Cipher.getInstance(PAYLOAD_CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreatePayloadKey())
        return ConversationAuditEncryptedBytes(
            ciphertext = cipher.doFinal(plaintext),
            nonceBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            algorithm = PAYLOAD_CIPHER,
            keyAlias = PAYLOAD_KEY_ALIAS,
        )
    }

    override fun decrypt(
        ciphertext: ByteArray,
        nonceBase64: String,
        keyAlias: String,
    ): ByteArray {
        require(keyAlias == PAYLOAD_KEY_ALIAS) {
            "Unexpected conversation audit payload key alias: $keyAlias"
        }
        val cipher = Cipher.getInstance(PAYLOAD_CIPHER)
        cipher.init(
            Cipher.DECRYPT_MODE,
            requirePayloadKey(),
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(nonceBase64, Base64.NO_WRAP)),
        )
        return cipher.doFinal(ciphertext)
    }

    override fun sign(value: ByteArray): ConversationAuditSignature {
        val entry =
            keyStore().getEntry(SIGNING_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
                ?: createSigningKey()
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(entry.privateKey)
        signature.update(value)
        return ConversationAuditSignature(
            signatureBase64 = Base64.encodeToString(signature.sign(), Base64.NO_WRAP),
            publicKeyBase64 =
                Base64.encodeToString(entry.certificate.publicKey.encoded, Base64.NO_WRAP),
            algorithm = SIGNATURE_ALGORITHM,
        )
    }

    override fun requireSigningPublicKeyBase64(): String {
        val entry =
            keyStore().getEntry(SIGNING_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
                ?: throw ConversationAuditKeyUnavailableException(SIGNING_KEY_ALIAS)
        return Base64.encodeToString(entry.certificate.publicKey.encoded, Base64.NO_WRAP)
    }

    override fun verify(
        value: ByteArray,
        signatureBase64: String,
        publicKeyBase64: String,
        algorithm: String,
    ): Boolean {
        require(algorithm == SIGNATURE_ALGORITHM) {
            "Unsupported conversation audit signature algorithm: $algorithm"
        }
        val keyFactory = java.security.KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)
        val publicKey =
            keyFactory.generatePublic(
                java.security.spec.X509EncodedKeySpec(
                    Base64.decode(publicKeyBase64, Base64.NO_WRAP)
                )
            )
        val signature = Signature.getInstance(algorithm)
        signature.initVerify(publicKey)
        signature.update(value)
        return signature.verify(Base64.decode(signatureBase64, Base64.NO_WRAP))
    }

    private fun getOrCreatePayloadKey(): SecretKey {
        val existing = keyStore().getKey(PAYLOAD_KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            return existing
        }
        val generator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE,
            )
        generator.init(
            KeyGenParameterSpec
                .Builder(
                    PAYLOAD_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun requirePayloadKey(): SecretKey =
        keyStore().getKey(PAYLOAD_KEY_ALIAS, null) as? SecretKey
            ?: throw ConversationAuditKeyUnavailableException(PAYLOAD_KEY_ALIAS)

    private fun createSigningKey(): KeyStore.PrivateKeyEntry {
        val generator =
            KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEY_STORE,
            )
        generator.initialize(
            KeyGenParameterSpec
                .Builder(
                    SIGNING_KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        generator.generateKeyPair()
        return keyStore().getEntry(SIGNING_KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    companion object {
        const val PAYLOAD_KEY_ALIAS = "kiyori_conversation_audit_payload_v1"
        const val SIGNING_KEY_ALIAS = "kiyori_conversation_audit_signing_v1"
        const val PAYLOAD_CIPHER = "AES/GCM/NoPadding"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val EC_CURVE = "secp256r1"
        private const val GCM_TAG_BITS = 128
    }
}

class ConversationAuditKeyUnavailableException(keyAlias: String) :
    IllegalStateException("Conversation audit key is unavailable: $keyAlias")
