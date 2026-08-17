package com.kiyori.app.shell

import com.kiyori.capability.settings.navigation.KiyoriSettingsRoute
import java.util.UUID

enum class KiyoriSettingsOrigin {
    BOTTOM_NAVIGATION,
    BROWSER_HOME,
    AI_HOST,
    EXTERNAL_BROWSER_PRESENTATION,
}

enum class KiyoriSettingsPresentation {
    PRIMARY_ROOT,
    SOURCE_OVERLAY,
    SUSPENDED_FOR_BROWSER_WORKSPACE,
    OPERIT_ROUTE_DETAIL,
}

data class KiyoriSettingsNavigationState(
    val sessionId: String,
    val origin: KiyoriSettingsOrigin,
    val routes: List<KiyoriSettingsRoute>,
    val presentation: KiyoriSettingsPresentation,
) {
    init {
        require(sessionId.isNotBlank()) { "Settings session id must not be blank" }
        require(routes.isNotEmpty()) { "Settings route stack must not be empty" }
        require(routes.first() == KiyoriSettingsRoute.HOME) {
            "Settings route stack must start at Settings Home"
        }
        require(routes.drop(1).none { route -> route == KiyoriSettingsRoute.HOME }) {
            "Settings Home may appear only at the root of the route stack"
        }
        when (origin) {
            KiyoriSettingsOrigin.BOTTOM_NAVIGATION ->
                require(
                    presentation == KiyoriSettingsPresentation.PRIMARY_ROOT ||
                        presentation == KiyoriSettingsPresentation.OPERIT_ROUTE_DETAIL ||
                        presentation ==
                        KiyoriSettingsPresentation.SUSPENDED_FOR_BROWSER_WORKSPACE,
                ) {
                    "Bottom navigation settings cannot use a source-overlay presentation"
                }
            KiyoriSettingsOrigin.BROWSER_HOME,
            KiyoriSettingsOrigin.AI_HOST,
            KiyoriSettingsOrigin.EXTERNAL_BROWSER_PRESENTATION,
            -> require(presentation != KiyoriSettingsPresentation.PRIMARY_ROOT) {
                "Source-preserving settings cannot become the primary settings owner"
            }
        }
    }

    val currentRoute: KiyoriSettingsRoute
        get() = routes.last()

    val canPopRoute: Boolean
        get() = routes.size > 1

    fun push(route: KiyoriSettingsRoute): KiyoriSettingsNavigationState {
        require(route != KiyoriSettingsRoute.HOME) {
            "Settings Home is the route-stack root and cannot be pushed"
        }
        return if (currentRoute == route) {
            this
        } else {
            copy(routes = routes + route)
        }
    }

    fun popRoute(): KiyoriSettingsNavigationState {
        check(canPopRoute) { "Settings Home has no parent route" }
        return copy(routes = routes.dropLast(1))
    }

    fun showOperitRoute(): KiyoriSettingsNavigationState =
        copy(presentation = KiyoriSettingsPresentation.OPERIT_ROUTE_DETAIL)

    fun restoreSettingsPresentation(): KiyoriSettingsNavigationState =
        copy(
            presentation =
                when (origin) {
                    KiyoriSettingsOrigin.BOTTOM_NAVIGATION ->
                        KiyoriSettingsPresentation.PRIMARY_ROOT
                    KiyoriSettingsOrigin.BROWSER_HOME,
                    KiyoriSettingsOrigin.AI_HOST,
                    KiyoriSettingsOrigin.EXTERNAL_BROWSER_PRESENTATION,
                    -> KiyoriSettingsPresentation.SOURCE_OVERLAY
                },
        )

    fun suspendForBrowserWorkspace(): KiyoriSettingsNavigationState =
        copy(presentation = KiyoriSettingsPresentation.SUSPENDED_FOR_BROWSER_WORKSPACE)

    companion object {
        fun start(
            origin: KiyoriSettingsOrigin,
            initialRoute: KiyoriSettingsRoute = KiyoriSettingsRoute.HOME,
            sessionId: String = UUID.randomUUID().toString(),
        ): KiyoriSettingsNavigationState {
            val presentation =
                when (origin) {
                    KiyoriSettingsOrigin.BOTTOM_NAVIGATION ->
                        KiyoriSettingsPresentation.PRIMARY_ROOT
                    KiyoriSettingsOrigin.BROWSER_HOME,
                    KiyoriSettingsOrigin.AI_HOST,
                    KiyoriSettingsOrigin.EXTERNAL_BROWSER_PRESENTATION,
                    -> KiyoriSettingsPresentation.SOURCE_OVERLAY
                }
            val root =
                KiyoriSettingsNavigationState(
                    sessionId = sessionId,
                    origin = origin,
                    routes = listOf(KiyoriSettingsRoute.HOME),
                    presentation = presentation,
                )
            return if (initialRoute == KiyoriSettingsRoute.HOME) {
                root
            } else {
                root.push(initialRoute)
            }
        }
    }
}

internal data class BrowserWorkspaceReturnToken(
    val settingsSessionId: String,
    val settingsRoutes: List<KiyoriSettingsRoute>,
    val sourceBrowserSessionId: String?,
    val initialPluginRouteId: String,
) {
    init {
        require(settingsSessionId.isNotBlank()) {
            "Browser workspace return token requires a settings session id"
        }
        require(settingsRoutes.isNotEmpty()) {
            "Browser workspace return token requires a settings route stack"
        }
        require(settingsRoutes.first() == KiyoriSettingsRoute.HOME) {
            "Browser workspace return route stack must start at Settings Home"
        }
        require(initialPluginRouteId.isNotBlank()) {
            "Browser workspace return token requires a plugin route id"
        }
    }
}
