package com.ai.assistance.operit.data.audit

import java.io.File
import org.json.JSONObject

internal data class ConversationAuditSavedOutput(
    val payloads: List<ConversationAuditPayloadInput> = emptyList(),
    val complete: Boolean = true,
) {
    companion object {
        fun supports(toolName: String) = toolName.startsWith("linux_ssh:") || toolName.startsWith("windows_control:")

        /** 只接收内置远程工具的显式输出文件，不跟随任意工具正文中的路径。 */
        fun capture(toolName: String, result: String, root: File): ConversationAuditSavedOutput {
            if (!supports(toolName)) return ConversationAuditSavedOutput()
            val json = try { JSONObject(result) } catch (_: org.json.JSONException) { return ConversationAuditSavedOutput() }
            val path = json.optString("output_saved_to", "").ifBlank { json.optString("outputSavedTo", "") }
            if (path.isBlank()) return ConversationAuditSavedOutput()
            val payloads = mutableListOf<ConversationAuditPayloadInput>()
            var status = "UNAVAILABLE"
            try {
                val file = File(path)
                val prefix = if (toolName.startsWith("linux_ssh:")) "linux_ssh_" else "windows_exec_output_"
                val canonical = file.canonicalFile
                if (canonical.parentFile != root.canonicalFile || !canonical.name.startsWith(prefix) || canonical.extension != "log") {
                    status = "REJECTED_PATH"
                } else if (canonical.isFile) {
                    // 在读取期间增长的文件也必须有界；超过限额明确记录缺口，不伪装为完整附件。
                    val bytes = canonical.inputStream().use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= MAX_BYTES) {
                            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    if (bytes.size > MAX_BYTES) status = "EXCEEDS_LIMIT"
                    else {
                        val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                        payloads += ConversationAuditPayloadInput.text("saved_tool_output", "tool", text)
                        status = "CAPTURED"
                    }
                }
            } catch (error: java.io.IOException) {
                status = "UNAVAILABLE_${error.javaClass.simpleName}"
            } catch (_: SecurityException) {
                status = "PERMISSION_DENIED"
            }
            payloads += ConversationAuditPayloadInput.text("saved_tool_output_capture", "metadata",
                JSONObject().put("status", status).put("sourcePath", path).put("limitBytes", MAX_BYTES).toString(),
                "application/json")
            return ConversationAuditSavedOutput(payloads, status == "CAPTURED")
        }

        private const val MAX_BYTES = 8 * 1024 * 1024
    }
}
