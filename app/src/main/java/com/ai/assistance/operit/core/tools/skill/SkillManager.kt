package com.ai.assistance.operit.core.tools.skill

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import java.nio.file.Files
import kotlinx.coroutines.CancellationException

class SkillManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SkillManager"

        @Volatile private var INSTANCE: SkillManager? = null

        fun getInstance(context: Context): SkillManager {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE ?: SkillManager(context.applicationContext).also { INSTANCE = it }
                }
        }
    }

    /** Serializes filesystem mutations with scans so stale scans cannot republish deleted content. */
    private val mutationLock = Any()

    @Volatile
    private var availableSkills: Map<String, SkillPackage> = emptyMap()

    @Volatile
    private var skillLoadErrors: Map<String, String> = emptyMap()

    private fun getSkillsRootDir(): File {
        return OperitPaths.skillsDir()
    }

    fun getSkillsDirectoryPath(): String {
        return getSkillsRootDir().absolutePath
    }

    fun refreshAvailableSkills() {
        synchronized(mutationLock) {
            refreshAvailableSkillsLocked()
        }
    }

    private fun refreshAvailableSkillsLocked() {
        val refreshedSkills = mutableMapOf<String, SkillPackage>()
        val refreshedErrors = mutableMapOf<String, String>()

        val skillsDir = try {
            getSkillsRootDir()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting skills directory", e)
            refreshedErrors[context.getString(R.string.ai_extensions_tab_skill)] =
                context.getString(R.string.skill_error_cannot_access_dir, e.message ?: "")
            availableSkills = emptyMap()
            skillLoadErrors = refreshedErrors.toMap()
            return
        }

        if (!skillsDir.exists() || !skillsDir.isDirectory) {
            availableSkills = emptyMap()
            skillLoadErrors = emptyMap()
            return
        }

        val children = skillsDir.listFiles() ?: emptyArray()
        for (child in children) {
            if (!child.isDirectory || child.name.startsWith(".import_tmp_")) continue

            val skillFile = File(child, "SKILL.md").let { primary ->
                if (primary.exists()) primary else File(child, "skill.md")
            }

            if (!skillFile.exists() || !skillFile.isFile) {
                refreshedErrors[child.name] = context.getString(
                    R.string.skill_error_missing_skill_md,
                    child.absolutePath
                )
                continue
            }

            try {
                val (name, description) = parseSkillMetadata(skillFile)
                val skillName = name.ifBlank { child.name }
                val skillDesc = description.ifBlank { "" }

                if (refreshedSkills.containsKey(skillName)) {
                    val existingDirName = refreshedSkills[skillName]?.directory?.name ?: skillName
                    refreshedErrors[child.name] = context.getString(
                        R.string.skill_error_duplicate_scanned_name,
                        skillName,
                        existingDirName
                    )
                    continue
                }

                refreshedSkills[skillName] = SkillPackage(
                    name = skillName,
                    description = skillDesc,
                    directory = child,
                    skillFile = skillFile
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error loading skill from ${skillFile.absolutePath}", e)
                refreshedErrors[child.name] = context.getString(
                    R.string.skill_error_scan_failed,
                    e.message ?: e.javaClass.simpleName
                )
            }
        }

        availableSkills = refreshedSkills.toMap()
        skillLoadErrors = refreshedErrors.toMap()
    }

    private fun parseSkillMetadata(skillFile: File): Pair<String, String> {
        val lines = skillFile.bufferedReader().use { it.readLines() }

        var name = ""
        var description = ""

        if (lines.isNotEmpty() && lines[0].trim() == "---") {
            val endIndex = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (endIndex >= 0) {
                val frontmatter = lines.subList(1, endIndex + 1)
                frontmatter.forEach { lineRaw ->
                    val line = lineRaw.trim()
                    val idx = line.indexOf(':')
                    if (idx <= 0) return@forEach
                    val key = line.substring(0, idx).trim()
                    val value = unquote(line.substring(idx + 1).trim())
                    when (key.lowercase()) {
                        "name" -> if (name.isBlank()) name = value
                        "description" -> if (description.isBlank()) description = value
                    }
                }
            }
        }

        if (name.isBlank() || description.isBlank()) {
            lines.take(40).forEach { lineRaw ->
                val line = lineRaw.trim()
                val idx = line.indexOf(':')
                if (idx <= 0) return@forEach
                val key = line.substring(0, idx).trim()
                val value = unquote(line.substring(idx + 1).trim())
                when (key.lowercase()) {
                    "name" -> if (name.isBlank()) name = value
                    "description" -> if (description.isBlank()) description = value
                }
            }
        }

        return Pair(name, description)
    }

    private fun unquote(valueRaw: String): String {
        var value = valueRaw
        if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\''))) {
            if (value.length >= 2) value = value.substring(1, value.length - 1)
        }
        return value
    }

    fun getAvailableSkills(): Map<String, SkillPackage> {
        synchronized(mutationLock) {
            refreshAvailableSkillsLocked()
            return availableSkills
        }
    }

    fun getAvailableSkillsSnapshot(): Pair<Map<String, SkillPackage>, Map<String, String>> {
        synchronized(mutationLock) {
            refreshAvailableSkillsLocked()
            return availableSkills to skillLoadErrors
        }
    }

    fun getSkillLoadErrors(): Map<String, String> {
        synchronized(mutationLock) {
            refreshAvailableSkillsLocked()
            return skillLoadErrors
        }
    }

    fun readSkillContent(skillName: String): String? {
        synchronized(mutationLock) {
            refreshAvailableSkillsLocked()
            val skill = availableSkills[skillName] ?: return null
            return try {
                skill.skillFile.readText()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to read SKILL.md for $skillName", e)
                null
            }
        }
    }

    fun deleteSkill(skillName: String): Boolean {
        synchronized(mutationLock) {
            refreshAvailableSkillsLocked()
            val skill = availableSkills[skillName] ?: return false
            return try {
                deleteSkillTree(skill.directory)
                refreshAvailableSkillsLocked()
                true
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to delete skill $skillName", e)
                false
            }
        }
    }

    fun getSkillSystemPrompt(skillName: String): String? {
        synchronized(mutationLock) {
            refreshAvailableSkillsLocked()
            val skill = availableSkills[skillName] ?: return null
            val content = try {
                skill.skillFile.readText()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to read skill content: ${skill.skillFile.absolutePath}", e)
                ""
            }

            val sb = StringBuilder()
            sb.appendLine("Using package (Skill): ${skill.name}")
            sb.appendLine("Use Time: ${java.time.LocalDateTime.now()}")
            sb.appendLine("Execution policy:")
            sb.appendLine("Prioritize using the skill-provided instructions and bundled scripts, and complete tasks with terminal-related tools.")
            if (skill.description.isNotBlank()) {
                sb.appendLine("Description: ${skill.description}")
            }
            sb.appendLine("SKILL.md path: ${skill.skillFile.absolutePath}")
            sb.appendLine("Skill directory: ${skill.directory.absolutePath}")
            sb.appendLine("Directory structure:")
            sb.appendLine(buildDirectoryTreeText(skill.directory))
            sb.appendLine()
            sb.appendLine("SKILL.md:")
            sb.appendLine(content)

            return sb.toString()
        }
    }

    private fun buildDirectoryTreeText(rootDir: File): String {
        val sb = StringBuilder()

        fun walk(dir: File, indent: String) {
            val children = dir.listFiles()
                ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
                ?: emptyList()

            for (child in children) {
                sb.append(indent)
                sb.append("- ")
                sb.append(child.name)
                if (child.isDirectory) {
                    sb.appendLine("/")
                    walk(child, indent + "  ")
                } else {
                    sb.appendLine()
                }
            }
        }

        walk(rootDir, indent = "")

        if (sb.length == 0) return "(empty directory)"
        return sb.toString().trimEnd()
    }

    fun importSkillFromZip(zipFile: File): String {
        return importSkillFromZip(zipFile, null)
    }

    data class SkillImportResult(
        val message: String,
        val installedDir: File?
    )

    fun importSkillFromZip(zipFile: File, subDirPathInZip: String?): String {
        return importSkillFromZipDetailed(zipFile, subDirPathInZip).message
    }

    fun importSkillFromZipDetailed(zipFile: File, subDirPathInZip: String?): SkillImportResult =
        importSkillFromZipDetailed(zipFile, subDirPathInZip, zipFile.name) {}

    internal fun importSkillFromZipDetailed(
        zipFile: File,
        subDirPathInZip: String?,
        archiveName: String,
        checkCancelled: () -> Unit,
    ): SkillImportResult {
        return importSkillFromZipDetailed(zipFile, subDirPathInZip, archiveName, null, checkCancelled)
    }

    internal fun importSkillFromZipDetailed(
        zipFile: File,
        subDirPathInZip: String?,
        archiveName: String,
        publication: SkillImportPublication?,
        checkCancelled: () -> Unit,
    ): SkillImportResult {
        synchronized(mutationLock) {
            return importSkillFromZipDetailedLocked(zipFile, subDirPathInZip, archiveName, publication, checkCancelled)
        }
    }

    /** 手动导入同样经唯一 mutationLock 与 staging 发布，不暴露半份技能目录。 */
    internal fun importPreparedSkill(
        skillName: String,
        description: String,
        checkCancelled: () -> Unit,
        prepare: (File) -> Unit,
    ): SkillImportResult = synchronized(mutationLock) {
        val root = getSkillsRootDir()
        val target = resolveSkillImportTarget(root, skillName)
        if (Files.exists(target.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return@synchronized SkillImportResult(context.getString(R.string.skill_error_import_duplicate_name, target.name), null)
        }
        val staging = Files.createTempDirectory(root.toPath(), ".import_tmp_").toFile()
        var published: File? = null
        try {
            checkCancelled()
            prepare(staging)
            checkCancelled()
            published = publishSkillImportDirectory(staging, root, skillName)
            refreshAvailableSkillsLocked()
            SkillImportResult(
                if (description.isNotBlank()) context.getString(R.string.skill_imported_with_desc, published.name, description)
                else context.getString(R.string.skill_imported, published.name),
                published,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            SkillImportResult(
                if (published != null) context.getString(R.string.skill_import_published_warning, published.absolutePath)
                else context.getString(R.string.skill_error_import_failed, error.message.orEmpty()),
                published,
            )
        } finally {
            deleteSkillTree(staging)
        }
    }

    private fun importSkillFromZipDetailedLocked(zipFile: File, subDirPathInZip: String?, archiveName: String, publication: SkillImportPublication?, checkCancelled: () -> Unit): SkillImportResult {
        if (!zipFile.exists() || !zipFile.canRead()) {
            return SkillImportResult(context.getString(R.string.skill_error_cannot_read_file, zipFile.absolutePath), null)
        }
        if (!zipFile.name.endsWith(".zip", ignoreCase = true)) {
            return SkillImportResult(context.getString(R.string.skill_error_only_support_zip), null)
        }

        val skillsRoot = try {
            getSkillsRootDir()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting skills directory", e)
            return SkillImportResult(context.getString(R.string.skill_error_cannot_access_dir, e.message ?: ""), null)
        }

        val tmpDir = Files.createTempDirectory(skillsRoot.toPath(), ".import_tmp_").toFile()
        var publishedDir: File? = null
        fun cleanupTmp() = deleteSkillTree(tmpDir)

        try {
            unzipToDirectory(zipFile, tmpDir, checkCancelled)

            val normalizedSubDir = subDirPathInZip
                ?.trim()
                ?.trimStart('/')
                ?.trimEnd('/')
                ?.takeIf { it.isNotBlank() }

            val zipRootDir = tmpDir
                .listFiles()
                ?.filter { it.isDirectory }
                ?.singleOrNull()
                ?: tmpDir

            val searchRoot: File = if (normalizedSubDir == null) {
                tmpDir
            } else {
                val baseCanonical = zipRootDir.canonicalFile
                val resolved = File(zipRootDir, normalizedSubDir)
                val resolvedCanonical = resolved.canonicalFile
                if (!resolvedCanonical.path.startsWith(baseCanonical.path + File.separator)) {
                    cleanupTmp()
                    return SkillImportResult(context.getString(R.string.skill_error_import_invalid_path), null)
                }
                if (!resolvedCanonical.exists()) {
                    cleanupTmp()
                    return SkillImportResult(context.getString(R.string.skill_error_import_path_not_found, normalizedSubDir), null)
                }
                resolvedCanonical
            }

            val directSkillFile = if (searchRoot.isDirectory) {
                File(searchRoot, "SKILL.md").let { primary ->
                    if (primary.exists()) primary else File(searchRoot, "skill.md")
                }.takeIf { it.exists() && it.isFile }
            } else {
                null
            }

            val skillMdCandidates = if (directSkillFile != null) {
                listOf(directSkillFile)
            } else {
                searchRoot.walkTopDown().onEach { checkCancelled() }
                    .filter { it.isFile && (it.name.equals("SKILL.md", ignoreCase = true) || it.name.equals("skill.md", ignoreCase = true)) }
                    .take(2)
                    .toList()
            }

            if (skillMdCandidates.isEmpty()) {
                cleanupTmp()
                return SkillImportResult(if (normalizedSubDir == null) {
                    context.getString(R.string.skill_error_import_no_skill_md)
                } else {
                    context.getString(R.string.skill_error_import_no_skill_md_in_path)
                }, null)
            }

            if (skillMdCandidates.size > 1) {
                cleanupTmp()
                return SkillImportResult(context.getString(R.string.skill_import_multiple_candidates), null)
            }
            val selectedSkillFile = skillMdCandidates.single()
            val selectedSkillDir = selectedSkillFile.parentFile ?: run {
                cleanupTmp()
                return SkillImportResult(context.getString(R.string.skill_error_import_skill_md_path_invalid), null)
            }

            val (metaName, metaDesc) = parseSkillMetadata(selectedSkillFile)
            val baseName = metaName.ifBlank {
                val isTmpRoot = try {
                    selectedSkillDir.canonicalFile == tmpDir.canonicalFile
                } catch (_: Exception) {
                    selectedSkillDir.absolutePath == tmpDir.absolutePath
                }
                if (isTmpRoot) {
                    File(archiveName).nameWithoutExtension
                } else {
                    selectedSkillDir.name.ifBlank { File(archiveName).nameWithoutExtension }
                }
            }
            val finalDir = resolveSkillImportTarget(skillsRoot, baseName)

            if (finalDir.exists() && publication?.existingDirectory == null) {
                cleanupTmp()
                return SkillImportResult(context.getString(R.string.skill_error_import_duplicate_name, finalDir.name), null)
            }

            checkCancelled()
            publishSkillImportDirectory(selectedSkillDir, skillsRoot, baseName, publication) { publishedDir = it }
            cleanupTmp()

            // refresh cache
            refreshAvailableSkillsLocked()

            val desc = metaDesc.ifBlank { "" }
            return SkillImportResult(if (desc.isNotBlank()) {
                context.getString(R.string.skill_imported_with_desc, finalDir.name, desc)
            } else {
                context.getString(R.string.skill_imported, finalDir.name)
            }, finalDir)
        } catch (cancelled: CancellationException) {
            try { cleanupTmp() } catch (cleanup: Exception) { cancelled.addSuppressed(cleanup) }
            throw cancelled
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to import skill from zip", e)
            try { cleanupTmp() } catch (cleanup: Exception) { e.addSuppressed(cleanup) }
            return SkillImportResult(
                if (publishedDir != null) context.getString(R.string.skill_import_published_warning, publishedDir.absolutePath)
                else context.getString(R.string.skill_error_import_failed, e.message.orEmpty()),
                publishedDir,
            )
        }
    }


    private fun unzipToDirectory(zipFile: File, destinationDir: File, checkCancelled: () -> Unit) {
        val destCanonical = destinationDir.canonicalFile
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkCancelled()
                val entry = zis.nextEntry ?: break

                val outFile = File(destinationDir, entry.name)
                val outCanonical = outFile.canonicalFile
                if (!outCanonical.path.startsWith(destCanonical.path + File.separator)) {
                    zis.closeEntry()
                    throw IllegalArgumentException("Zip entry is outside target dir: ${entry.name}")
                }

                if (entry.isDirectory) {
                    check(outFile.isDirectory || outFile.mkdirs()) { "Cannot create skill archive directory" }
                    zis.closeEntry()
                    continue
                }

                outFile.parentFile?.let { check(it.isDirectory || it.mkdirs()) { "Cannot create skill archive parent" } }
                FileOutputStream(outFile).use { fos ->
                    while (true) {
                        checkCancelled()
                        val read = zis.read(buffer)
                        if (read <= 0) break
                        fos.write(buffer, 0, read)
                    }
                }
                zis.closeEntry()
            }
        }
    }
}
