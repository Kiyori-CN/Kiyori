package com.kiyori.platform.storage

import android.content.Context

class KiyoriStorageService private constructor(context: Context) {
    private val appContext = context.applicationContext

    val publicStore: KiyoriPublicStore by lazy {
        KiyoriPublicStore(appContext)
    }

    fun toolPkgStorage(containerPackageName: String): ToolPkgStorageService {
        return ToolPkgStorageService(appContext, containerPackageName)
    }

    internal fun toolPkgLegacyImportCoordinator(
        migrators: Iterable<ToolPkgLegacyMigrator>,
    ): ToolPkgLegacyImportCoordinator {
        return ToolPkgLegacyImportCoordinator(
            context = appContext,
            registry = ToolPkgLegacyMigratorRegistry(migrators),
        )
    }

    companion object {
        @Volatile
        private var instance: KiyoriStorageService? = null

        fun getInstance(context: Context): KiyoriStorageService {
            return instance
                ?: synchronized(this) {
                    instance
                        ?: KiyoriStorageService(context).also { created ->
                            instance = created
                        }
                }
        }
    }
}
