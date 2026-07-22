package com.ai.assistance.operit.ui.main.shell

enum class PrimaryDestination {
    SOFTWARE_HOME,
    BROWSER_HOME,
    MINI_APP_HOME,
    FILE_MANAGEMENT_HOME,
    SETTINGS_HOME,
}

enum class SoftwareHomePage(val pagerIndex: Int) {
    MINUS_ONE(0),
    HOME(1),
    AI_HOME(2),
    ;

    companion object {
        fun fromPagerIndex(index: Int): SoftwareHomePage =
            entries.single { page -> page.pagerIndex == index }
    }
}

enum class KiyoriShellChild {
    FULL_SCREEN_WEB_SEARCH,
    AI_CENTER,
}

enum class AiCenterDestination {
    PACKAGES,
    PERMISSIONS,
    WORKFLOW,
    ASSISTANT_CONFIG,
    MEMORY,
    TOOLBOX,
    AI_SETTINGS,
}

enum class KiyoriShellBackResult {
    CONSUMED,
    REQUEST_EXIT,
}

data class KiyoriShellBackTransition(
    val state: KiyoriShellState,
    val result: KiyoriShellBackResult,
)

data class KiyoriShellState(
    val primaryDestination: PrimaryDestination = PrimaryDestination.SOFTWARE_HOME,
    val softwareHomePage: SoftwareHomePage = SoftwareHomePage.HOME,
    val child: KiyoriShellChild? = null,
) {
    val showsBottomBar: Boolean
        get() =
            child == null &&
                (primaryDestination != PrimaryDestination.SOFTWARE_HOME ||
                    softwareHomePage == SoftwareHomePage.HOME)

    fun selectPrimary(destination: PrimaryDestination): KiyoriShellState =
        copy(
            primaryDestination = destination,
            softwareHomePage =
                if (destination == PrimaryDestination.SOFTWARE_HOME) {
                    SoftwareHomePage.HOME
                } else {
                    softwareHomePage
                },
            child = null,
        )

    fun showSoftwareHomePage(page: SoftwareHomePage): KiyoriShellState =
        copy(
            primaryDestination = PrimaryDestination.SOFTWARE_HOME,
            softwareHomePage = page,
            child = null,
        )

    fun openChild(destination: KiyoriShellChild): KiyoriShellState =
        copy(child = destination)

    fun handleBack(): KiyoriShellBackTransition =
        when {
            child != null ->
                KiyoriShellBackTransition(
                    state = copy(child = null),
                    result = KiyoriShellBackResult.CONSUMED,
                )
            primaryDestination == PrimaryDestination.SOFTWARE_HOME &&
                softwareHomePage != SoftwareHomePage.HOME ->
                KiyoriShellBackTransition(
                    state = copy(softwareHomePage = SoftwareHomePage.HOME),
                    result = KiyoriShellBackResult.CONSUMED,
                )
            primaryDestination != PrimaryDestination.SOFTWARE_HOME ->
                KiyoriShellBackTransition(
                    state =
                        copy(
                            primaryDestination = PrimaryDestination.SOFTWARE_HOME,
                            softwareHomePage = SoftwareHomePage.HOME,
                        ),
                    result = KiyoriShellBackResult.CONSUMED,
                )
            else ->
                KiyoriShellBackTransition(
                    state = this,
                    result = KiyoriShellBackResult.REQUEST_EXIT,
                )
        }
}
