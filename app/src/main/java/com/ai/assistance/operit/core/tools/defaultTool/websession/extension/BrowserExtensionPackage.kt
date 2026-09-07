package com.ai.assistance.operit.core.tools.defaultTool.websession.extension

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class BrowserExtensionManifest(
    val manifest_version: Int,
    val name: String,
    val version: String,
    val description: String = "",
    val kiyori: BrowserExtensionIdentity,
    val content_scripts: List<BrowserExtensionContent>,
    val action: BrowserExtensionAction? = null,
)

@Serializable
internal data class BrowserExtensionIdentity(
    val schema_version: Int,
    val id: String,
    val minimum_runtime_version: Int = 1,
)

@Serializable
internal data class BrowserExtensionAction(val default_title: String)

@Serializable
internal data class BrowserExtensionContent(
    val matches: List<String>,
    val exclude_matches: List<String> = emptyList(),
    val js: List<String> = emptyList(),
    val css: List<String> = emptyList(),
    val run_at: String = "document_end",
    val world: String = "ISOLATED",
    val all_frames: Boolean = false,
) {
    fun matchesUrl(url: String): Boolean = BrowserExtensionRuntime.matches(this, url)
}

@Serializable
internal data class BrowserExtensionPackage(
    val manifest: BrowserExtensionManifest,
    val files: Map<String, String>,
) {
    val id: String get() = manifest.kiyori.id
    val revision: String get() = MessageDigest.getInstance("SHA-256")
        .digest(codec.encodeToString(copy(files = files.toSortedMap())).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun validate(validateJavaScript: (String) -> String? = { null }): BrowserExtensionPackage {
        require(manifest.manifest_version == 3 && manifest.kiyori.schema_version == 1 &&
            manifest.kiyori.minimum_runtime_version == 1) { "UNSUPPORTED_SCHEMA" }
        require(id.length <= 128 && ID.matches(id) && !id.startsWith("kiyori.browser.")) { "INVALID_EXTENSION_ID" }
        require(manifest.name.isNotBlank() && manifest.name.length <= 120) { "INVALID_NAME" }
        require(VERSION.matches(manifest.version)) { "INVALID_VERSION" }
        require(manifest.description.length <= 4096) { "DESCRIPTION_TOO_LARGE" }
        require(manifest.action == null || manifest.action.default_title.let { it.isNotBlank() && it.length <= 120 }) {
            "INVALID_ACTION_TITLE"
        }
        require(files.size in 1..MAX_FILES && manifest.content_scripts.size in 1..16) { "INVALID_ENTRY_COUNT" }
        var bytes = codec.encodeToString(manifest).toByteArray(Charsets.UTF_8).size.toLong()
        val normalizedPaths = hashSetOf<String>()
        files.forEach { (path, source) ->
            validatePath(path)
            require(path != "manifest.json" && normalizedPaths.add(path.lowercase(java.util.Locale.ROOT))) {
                "DUPLICATE_PATH: $path"
            }
            bytes += source.toByteArray(Charsets.UTF_8).size
            require(bytes <= MAX_BYTES) { "PACKAGE_TOO_LARGE" }
        }
        val referenced = linkedSetOf<String>()
        manifest.content_scripts.forEach { script ->
            require(script.world == "ISOLATED" && !script.all_frames) { "UNSUPPORTED_EXECUTION_WORLD_OR_FRAMES" }
            require(script.run_at in setOf("document_start", "document_end", "document_idle")) { "UNSUPPORTED_RUN_AT" }
            require(script.matches.size in 1..32 && script.exclude_matches.size <= 32) { "INVALID_MATCH_COUNT" }
            (script.matches + script.exclude_matches).forEach { pattern ->
                require(pattern.length <= MAX_MATCH_PATTERN_CHARACTERS && MATCH.matches(pattern)) {
                    "INVALID_MATCH: $pattern"
                }
                val port = pattern.substringAfter("://").substringBefore('/').substringAfter(':', "")
                require(port.isEmpty() || port.toInt() <= 65535) { "INVALID_MATCH_PORT: $pattern" }
            }
            require(script.js.isNotEmpty() || script.css.isNotEmpty()) { "EMPTY_CONTENT_SCRIPT" }
            (script.js + script.css).forEach { path ->
                require(files.containsKey(path)) { "MISSING_FILE: $path" }
                require(referenced.add(path)) { "DUPLICATE_CONTENT_FILE: $path" }
            }
            script.js.forEach { path ->
                require(path.endsWith(".js")) { "INVALID_JS_PATH" }
                validateJavaScript(files.getValue(path))?.let { throw IllegalArgumentException("SYNTAX_ERROR: $path: $it") }
            }
            script.css.forEach { require(it.endsWith(".css")) { "INVALID_CSS_PATH" } }
        }
        require(referenced == files.keys) { "UNDECLARED_FILE" }
        return this
    }

    fun writeZip(output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            (mapOf("manifest.json" to codec.encodeToString(manifest)) + files.toSortedMap()).forEach { (path, source) ->
                zip.putNextEntry(ZipEntry(path).apply { time = 0 })
                zip.write(source.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    companion object {
        const val MAX_BYTES = 1024 * 1024
        const val MAX_FILES = 64
        const val MAX_MATCH_PATTERN_CHARACTERS = 2048
        val codec = Json { encodeDefaults = true; ignoreUnknownKeys = false }
        private val ID = Regex("[a-z][a-z0-9_-]*(?:\\.[a-z][a-z0-9_-]*){1,15}")
        private val VERSION = Regex("(?:0|[1-9][0-9]{0,8})(?:\\.(?:0|[1-9][0-9]{0,8})){0,3}")
        private val MATCH = Regex("(?:https?|\\*)://(?:\\*|(?:\\*\\.)?[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*)(?::[0-9]{1,5})?/[^\\s]*")

        fun validatePath(path: String) {
            require(path.length in 1..160 && path.split('/').all { segment ->
                segment.isNotEmpty() && segment != "." && segment != ".." &&
                    Regex("[a-zA-Z0-9_.-]+").matches(segment)
            }) { "INVALID_PACKAGE_PATH" }
        }

        fun readZip(input: InputStream): BrowserExtensionPackage {
            val files = linkedMapOf<String, String>()
            val seen = hashSetOf<String>()
            var total = 0
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    validatePath(entry.name)
                    require(!entry.isDirectory && seen.add(entry.name.lowercase(java.util.Locale.ROOT))) { "DUPLICATE_OR_DIRECTORY_ENTRY" }
                    require(seen.size <= MAX_FILES + 1) { "TOO_MANY_FILES" }
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count == -1) break
                        total += count
                        require(total <= MAX_BYTES) { "PACKAGE_TOO_LARGE" }
                        output.write(buffer, 0, count)
                    }
                    files[entry.name] = Charsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(output.toByteArray())).toString()
                }
            }
            val manifest = files.remove("manifest.json") ?: error("MISSING_MANIFEST")
            return BrowserExtensionPackage(codec.decodeFromString(manifest), files).validate()
        }

        fun compareVersions(left: String, right: String): Int {
            val a = left.split('.').map(String::toLong)
            val b = right.split('.').map(String::toLong)
            repeat(4) { i -> (a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }).let { if (it != 0L) return it.compareTo(0) } }
            return 0
        }
    }
}
