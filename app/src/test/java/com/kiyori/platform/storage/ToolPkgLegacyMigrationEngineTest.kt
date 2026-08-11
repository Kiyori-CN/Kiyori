package com.kiyori.platform.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPkgLegacyMigrationEngineTest {
    @Test
    fun `replace import commits one active generation and rejects the same source digest`() {
        withRoots { roots ->
            val migrator =
                MappingMigrator(
                    migratorVersion = 1,
                    conflictPolicy = ToolPkgLegacyConflictPolicy.REPLACE,
                )
            val contract = contract(version = 1, ToolPkgLegacyConflictPolicy.REPLACE)
            val engine =
                engine(
                    roots = roots,
                    migrators = listOf(migrator),
                    generationId = GENERATION_ONE,
                )
            val sourceFiles =
                linkedMapOf(
                    "trigger_state.json" to """{"lastAnalyzed":42}""".toByteArray(),
                )

            val result =
                engine.import(
                    contract = contract,
                    sourceIdentity = "content://selected/tree",
                    sourceReader = reader(sourceFiles),
                )

            val active =
                requireNotNull(
                    ToolPkgPrivateDataLayout.readActiveGeneration(
                        roots.activeFile,
                        roots.generations,
                    ),
                )
            assertEquals(GENERATION_ONE, result.generationId)
            assertEquals(GENERATION_ONE, active.generationId)
            assertEquals(
                """{"lastAnalyzed":42}""",
                File(
                    roots.generations,
                    "$GENERATION_ONE/data/state/sidebar_analysis_state.json",
                ).readText(),
            )
            assertTrue(File(roots.audit, "$GENERATION_ONE.json").isFile)
            assertEquals("""{"lastAnalyzed":42}""", sourceFiles.getValue("trigger_state.json").decodeToString())

            assertThrows(IllegalArgumentException::class.java) {
                engine.import(
                    contract = contract,
                    sourceIdentity = "content://selected/tree",
                    sourceReader = reader(sourceFiles),
                )
            }
        }
    }

    @Test
    fun `merge import preserves current private data and overlays migrated output`() {
        withRoots { roots ->
            File(roots.defaultData, "existing").mkdirs()
            File(roots.defaultData, "existing/value.json").writeText("""{"kept":true}""")
            val engine =
                engine(
                    roots = roots,
                    migrators =
                        listOf(
                            MappingMigrator(
                                migratorVersion = 1,
                                conflictPolicy = ToolPkgLegacyConflictPolicy.MERGE,
                            ),
                        ),
                    generationId = GENERATION_ONE,
                )

            engine.import(
                contract = contract(version = 1, ToolPkgLegacyConflictPolicy.MERGE),
                sourceIdentity = "content://selected/tree",
                sourceReader =
                    reader(
                        mapOf(
                            "trigger_state.json" to """{"lastAnalyzed":7}""".toByteArray(),
                        ),
                    ),
            )

            val generationData = File(roots.generations, "$GENERATION_ONE/data")
            assertEquals(
                """{"kept":true}""",
                File(generationData, "existing/value.json").readText(),
            )
            assertEquals(
                """{"lastAnalyzed":7}""",
                File(generationData, "state/sidebar_analysis_state.json").readText(),
            )
        }
    }

    @Test
    fun `migrator failure leaves the previous active generation unchanged`() {
        withRoots { roots ->
            val firstEngine =
                engine(
                    roots = roots,
                    migrators =
                        listOf(
                            MappingMigrator(
                                migratorVersion = 1,
                                conflictPolicy = ToolPkgLegacyConflictPolicy.REPLACE,
                            ),
                        ),
                    generationId = GENERATION_ONE,
                )
            firstEngine.import(
                contract = contract(version = 1, ToolPkgLegacyConflictPolicy.REPLACE),
                sourceIdentity = "content://selected/first",
                sourceReader =
                    reader(
                        mapOf(
                            "trigger_state.json" to """{"lastAnalyzed":1}""".toByteArray(),
                        ),
                    ),
            )
            val failingEngine =
                engine(
                    roots = roots,
                    migrators = listOf(FailingMigrator),
                    generationId = GENERATION_TWO,
                )

            assertThrows(IllegalStateException::class.java) {
                failingEngine.import(
                    contract = contract(version = 2, ToolPkgLegacyConflictPolicy.REPLACE),
                    sourceIdentity = "content://selected/second",
                    sourceReader =
                        reader(
                            mapOf(
                                "trigger_state.json" to """{"lastAnalyzed":2}""".toByteArray(),
                            ),
                        ),
                )
            }

            val active =
                requireNotNull(
                    ToolPkgPrivateDataLayout.readActiveGeneration(
                        roots.activeFile,
                        roots.generations,
                    ),
                )
            assertEquals(GENERATION_ONE, active.generationId)
            assertFalse(File(roots.generations, GENERATION_TWO).exists())
        }
    }

    @Test
    fun `missing package-specific migrator changes no generation state`() {
        withRoots { roots ->
            val engine =
                engine(
                    roots = roots,
                    migrators = emptyList(),
                    generationId = GENERATION_ONE,
                )

            assertThrows(IllegalArgumentException::class.java) {
                engine.import(
                    contract = contract(version = 1, ToolPkgLegacyConflictPolicy.REPLACE),
                    sourceIdentity = "content://selected/tree",
                    sourceReader =
                        reader(
                            mapOf(
                                "trigger_state.json" to "{}".toByteArray(),
                            ),
                        ),
                )
            }

            assertFalse(roots.activeFile.exists())
            assertTrue(roots.generations.listFiles().orEmpty().isEmpty())
        }
    }

    private fun engine(
        roots: TestRoots,
        migrators: List<ToolPkgLegacyMigrator>,
        generationId: String,
    ): ToolPkgLegacyMigrationEngine {
        return ToolPkgLegacyMigrationEngine(
            packageKey = ToolPkgStorageService.packageKey(CONTAINER_ID),
            defaultDataDir = roots.defaultData,
            generationsDir = roots.generations,
            activeGenerationFile = roots.activeFile,
            migrationAuditDir = roots.audit,
            registry = ToolPkgLegacyMigratorRegistry(migrators),
            nowMillis = { 1234L },
            generationIdFactory = { generationId },
        )
    }

    private fun contract(
        version: Int,
        conflictPolicy: ToolPkgLegacyConflictPolicy,
    ): ToolPkgLegacyImportContract {
        return ToolPkgLegacyImportContract(
            containerPackageName = CONTAINER_ID,
            migratorId = MIGRATOR_ID,
            migratorVersion = version,
            targetSchemaVersion = 1,
            conflictPolicy = conflictPolicy,
        )
    }

    private fun reader(files: Map<String, ByteArray>): ToolPkgLegacySourceReader {
        return ToolPkgLegacySourceReader { relativePath, maximumBytes ->
            files[relativePath]?.also { bytes ->
                require(bytes.size <= maximumBytes)
            }
        }
    }

    private fun withRoots(block: (TestRoots) -> Unit) {
        val root = Files.createTempDirectory("toolpkg-migration-test").toFile()
        try {
            val packageRoot = File(root, "package").apply { mkdirs() }
            block(
                TestRoots(
                    defaultData = File(packageRoot, "data").apply { mkdirs() },
                    generations = File(packageRoot, "generations").apply { mkdirs() },
                    activeFile = File(packageRoot, "active-generation.json"),
                    audit = File(packageRoot, "migration-audit").apply { mkdirs() },
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private data class TestRoots(
        val defaultData: File,
        val generations: File,
        val activeFile: File,
        val audit: File,
    )

    private class MappingMigrator(
        override val migratorVersion: Int,
        override val conflictPolicy: ToolPkgLegacyConflictPolicy,
    ) : ToolPkgLegacyMigrator {
        override val containerPackageName: String = CONTAINER_ID
        override val migratorId: String = MIGRATOR_ID
        override val targetSchemaVersion: Int = 1
        override val inputs: List<ToolPkgLegacyInputDeclaration> =
            listOf(ToolPkgLegacyInputDeclaration("trigger_state.json"))

        override fun migrate(input: ToolPkgLegacyMigrationInput): ToolPkgLegacyMigrationOutput {
            return ToolPkgLegacyMigrationOutput(
                files =
                    mapOf(
                        "state/sidebar_analysis_state.json" to
                            input.files.getValue("trigger_state.json").text,
                    ),
            )
        }
    }

    private object FailingMigrator : ToolPkgLegacyMigrator {
        override val containerPackageName: String = CONTAINER_ID
        override val migratorId: String = MIGRATOR_ID
        override val migratorVersion: Int = 2
        override val targetSchemaVersion: Int = 1
        override val conflictPolicy: ToolPkgLegacyConflictPolicy =
            ToolPkgLegacyConflictPolicy.REPLACE
        override val inputs: List<ToolPkgLegacyInputDeclaration> =
            listOf(ToolPkgLegacyInputDeclaration("trigger_state.json"))

        override fun migrate(input: ToolPkgLegacyMigrationInput): ToolPkgLegacyMigrationOutput {
            throw IllegalStateException("schema validation failed")
        }
    }

    companion object {
        private const val CONTAINER_ID = "com.operit.memory_system"
        private const val MIGRATOR_ID = "memory-system-v1"
        private const val GENERATION_ONE =
            "generation-00000000-0000-0000-0000-000000000001"
        private const val GENERATION_TWO =
            "generation-00000000-0000-0000-0000-000000000002"
    }
}
