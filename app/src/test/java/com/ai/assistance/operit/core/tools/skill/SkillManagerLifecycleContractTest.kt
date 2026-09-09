package com.ai.assistance.operit.core.tools.skill

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillManagerLifecycleContractTest {
    @Test
    fun `skill cache publishes one complete snapshot under the mutation lock`() {
        val source = repositoryFile(
            "app/src/main/java/com/ai/assistance/operit/core/tools/skill/SkillManager.kt",
        ).readText()

        assertTrue(source.contains("private val mutationLock = Any()"))
        assertTrue(source.contains("private var availableSkills: Map<String, SkillPackage> = emptyMap()"))
        assertTrue(source.contains("private var skillLoadErrors: Map<String, String> = emptyMap()"))

        val refreshStart = source.indexOf("fun refreshAvailableSkills()")
        val refreshBody = source.substring(refreshStart)
        assertTrue(refreshBody.contains("synchronized(mutationLock)"))
        assertTrue(refreshBody.contains("val refreshedSkills = mutableMapOf<String, SkillPackage>()"))
        assertTrue(refreshBody.contains("availableSkills = refreshedSkills.toMap()"))
        assertTrue(refreshBody.contains("skillLoadErrors = refreshedErrors.toMap()"))

        val deleteStart = source.indexOf("fun deleteSkill(skillName: String)")
        assertTrue(source.substring(deleteStart).contains("synchronized(mutationLock)"))
        val importStart = source.indexOf("fun importSkillFromZipDetailed(zipFile: File, subDirPathInZip: String?)")
        val zipImport = source.substring(importStart).substringBefore("internal fun importPreparedSkill(")
        assertTrue(zipImport.contains("synchronized(mutationLock)"))
        assertTrue(zipImport.contains("return importSkillFromZipDetailedLocked(zipFile, subDirPathInZip, archiveName, publication, checkCancelled)"))
        val preparedImport = source.substringAfter("internal fun importPreparedSkill(")
            .substringBefore("private fun importSkillFromZipDetailedLocked")
        assertTrue(preparedImport.contains("synchronized(mutationLock)"))
        assertTrue(preparedImport.indexOf("prepare(staging)") < preparedImport.indexOf("publishSkillImportDirectory(staging, root, skillName)"))

        val contentStart = source.indexOf("fun readSkillContent(skillName: String)")
        assertTrue(source.substring(contentStart).contains("synchronized(mutationLock)"))
        val promptStart = source.indexOf("fun getSkillSystemPrompt(skillName: String)")
        assertTrue(source.substring(promptStart).contains("synchronized(mutationLock)"))
        val snapshotStart = source.indexOf("fun getAvailableSkillsSnapshot()")
        assertTrue(source.substring(snapshotStart).contains("return availableSkills to skillLoadErrors"))
    }

    private fun repositoryFile(relativePath: String): File {
        var current: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(4) {
            val candidate = current?.let { directory -> File(directory, relativePath) }
            if (candidate?.isFile == true) return candidate
            current = current?.parentFile
        }
        throw AssertionError("Repository file not found: $relativePath")
    }
}
