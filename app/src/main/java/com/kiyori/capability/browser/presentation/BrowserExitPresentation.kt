package com.kiyori.capability.browser.presentation

enum class KiyoriBrowserExitPresentation {
    CLOSE,
    MINIMIZED_INDICATOR,
}

enum class KiyoriBrowserSearchSource {
    SOFTWARE_HOME,
    BROWSER_HOME,
    SEARCH_HISTORY,
}

sealed interface KiyoriBrowserWorkspaceRoute {
    val stableId: String

    data object Overview : KiyoriBrowserWorkspaceRoute {
        override val stableId: String = "overview"
    }

    data object Diagnostics : KiyoriBrowserWorkspaceRoute {
        override val stableId: String = "userscripts"
    }

    data class UserscriptDetail(
        val scriptId: Long,
    ) : KiyoriBrowserWorkspaceRoute {
        init {
            require(scriptId > 0L) {
                "Browser workspace userscript id must be positive"
            }
        }

        override val stableId: String = "userscript-detail:$scriptId"
    }
}
