package com.ai.assistance.operit.data.audit

import android.content.Context
import com.ai.assistance.operit.data.model.ConversationAuditPayloadEntity
import com.kiyori.platform.storage.KiyoriPaths
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 审计 payload 的唯一文件 owner。
 *
 * 正式文件在 Room 引用前已经完成同文件系统原子 rename，因此数据库不会指向半写文件。
 */
class ConversationAuditPayloadStore(
    context: Context,
    private val crypto: ConversationAuditCrypto = AndroidConversationAuditCrypto(),
) {
    private val applicationContext = context.applicationContext
    private val rootDir = KiyoriPaths.conversationAuditRootDir(applicationContext).canonicalFile
    private val payloadsDir = KiyoriPaths.conversationAuditPayloadsDir(applicationContext).canonicalFile
    private val stagingDir = KiyoriPaths.conversationAuditStagingDir(applicationContext).canonicalFile
    private val writeMutex = Mutex()

    suspend fun write(
        bytes: ByteArray,
        mediaType: String,
        encoding: String,
        createdAt: Long = System.currentTimeMillis(),
    ): ConversationAuditPayloadEntity =
        writeMutex.withLock {
            val payloadSha256 = ConversationAuditHasher.sha256(bytes)
            val relativePath =
                "${payloadSha256.substring(0, 2)}${File.separator}$payloadSha256.audit"
            val finalFile = checkedFile(File(payloadsDir, relativePath))
            if (finalFile.exists()) {
                val existing =
                    readMetadataFromFile(
                    file = finalFile,
                    payloadSha256 = payloadSha256,
                    mediaType = mediaType,
                    encoding = encoding,
                    plainByteCount = bytes.size.toLong(),
                    createdAt = createdAt,
                )
                require(read(existing).contentEquals(bytes)) {
                    "Existing conversation audit payload content does not match its address"
                }
                return@withLock existing
            }

            val compressed = gzip(bytes)
            val encrypted = crypto.encrypt(compressed)
            val container =
                ConversationAuditPayloadContainer.encode(
                    nonceBase64 = encrypted.nonceBase64,
                    ciphertext = encrypted.ciphertext,
                )
            require(finalFile.parentFile?.mkdirs() == true || finalFile.parentFile?.isDirectory == true) {
                "Unable to create conversation audit payload directory"
            }
            val stagingFile =
                checkedFile(File(stagingDir, "${UUID.randomUUID()}.tmp"))
            FileOutputStream(stagingFile).use { output ->
                output.write(container)
                output.fd.sync()
            }
            require(stagingFile.renameTo(finalFile)) {
                "Unable to atomically commit conversation audit payload $payloadSha256"
            }
            return@withLock ConversationAuditPayloadEntity(
                payloadSha256 = payloadSha256,
                relativePath = rootDir.toPath().relativize(finalFile.toPath()).toString(),
                plainByteCount = bytes.size.toLong(),
                storedByteCount = finalFile.length(),
                mediaType = mediaType,
                encoding = encoding,
                compression = COMPRESSION_GZIP,
                encryptionAlgorithm = encrypted.algorithm,
                keyAlias = encrypted.keyAlias,
                nonceBase64 = encrypted.nonceBase64,
                createdAt = createdAt,
            )
        }

    fun read(payload: ConversationAuditPayloadEntity): ByteArray {
        val file = checkedFile(File(rootDir, payload.relativePath))
        require(file.isFile) {
            "Conversation audit payload is missing: ${payload.payloadSha256}"
        }
        val container = ConversationAuditPayloadContainer.decode(FileInputStream(file).use { it.readBytes() })
        require(container.nonceBase64 == payload.nonceBase64) {
            "Conversation audit payload nonce metadata mismatch"
        }
        val compressed =
            crypto.decrypt(
                ciphertext = container.ciphertext,
                nonceBase64 = payload.nonceBase64,
                keyAlias = payload.keyAlias,
            )
        val plaintext = gunzip(compressed)
        require(plaintext.size.toLong() == payload.plainByteCount) {
            "Conversation audit payload byte count mismatch"
        }
        require(ConversationAuditHasher.sha256(plaintext) == payload.payloadSha256) {
            "Conversation audit payload hash mismatch"
        }
        return plaintext
    }

    fun delete(payload: ConversationAuditPayloadEntity) {
        val file = checkedFile(File(rootDir, payload.relativePath))
        if (file.exists()) {
            require(file.delete()) {
                "Unable to delete unreferenced conversation audit payload ${payload.payloadSha256}"
            }
        }
    }

    private fun readMetadataFromFile(
        file: File,
        payloadSha256: String,
        mediaType: String,
        encoding: String,
        plainByteCount: Long,
        createdAt: Long,
    ): ConversationAuditPayloadEntity {
        val container = ConversationAuditPayloadContainer.decode(FileInputStream(file).use { it.readBytes() })
        return ConversationAuditPayloadEntity(
            payloadSha256 = payloadSha256,
            relativePath = rootDir.toPath().relativize(file.toPath()).toString(),
            plainByteCount = plainByteCount,
            storedByteCount = file.length(),
            mediaType = mediaType,
            encoding = encoding,
            compression = COMPRESSION_GZIP,
            encryptionAlgorithm = AndroidConversationAuditCrypto.PAYLOAD_CIPHER,
            keyAlias = AndroidConversationAuditCrypto.PAYLOAD_KEY_ALIAS,
            nonceBase64 = container.nonceBase64,
            createdAt = createdAt,
        )
    }

    private fun checkedFile(file: File): File {
        val canonical = file.canonicalFile
        require(canonical.toPath().startsWith(rootDir.toPath())) {
            "Conversation audit path escaped its private root"
        }
        return canonical
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip -> gzip.write(bytes) }
        return output.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }

    private object ConversationAuditPayloadContainer {
        private val magic = "KIYORI_AUDIT_PAYLOAD_V1\n".toByteArray(Charsets.US_ASCII)

        fun encode(
            nonceBase64: String,
            ciphertext: ByteArray,
        ): ByteArray {
            val nonce = "$nonceBase64\n".toByteArray(Charsets.US_ASCII)
            return magic + nonce + ciphertext
        }

        fun decode(bytes: ByteArray): DecodedPayload {
            require(bytes.size > magic.size) { "Conversation audit payload container is truncated" }
            require(bytes.copyOfRange(0, magic.size).contentEquals(magic)) {
                "Conversation audit payload container magic is invalid"
            }
            val nonceEnd = bytes.indexOf('\n'.code.toByte(), startIndex = magic.size)
            require(nonceEnd > magic.size) {
                "Conversation audit payload container nonce is missing"
            }
            val nonce =
                bytes.copyOfRange(magic.size, nonceEnd).toString(Charsets.US_ASCII)
            val ciphertext = bytes.copyOfRange(nonceEnd + 1, bytes.size)
            require(ciphertext.isNotEmpty()) {
                "Conversation audit payload container ciphertext is missing"
            }
            return DecodedPayload(nonceBase64 = nonce, ciphertext = ciphertext)
        }

        private fun ByteArray.indexOf(
            value: Byte,
            startIndex: Int,
        ): Int {
            for (index in startIndex until size) {
                if (this[index] == value) {
                    return index
                }
            }
            return -1
        }
    }

    private data class DecodedPayload(
        val nonceBase64: String,
        val ciphertext: ByteArray,
    )

    companion object {
        const val COMPRESSION_GZIP = "gzip"
    }
}
