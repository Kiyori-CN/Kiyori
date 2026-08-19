package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript

internal object UserscriptPageStatusPolicy {
    fun resolve(
        script: UserscriptListItem,
        userScriptsAllowed: Boolean,
        siteScriptsAllowed: Boolean = true,
        pageUrl: String?,
        runtimeSupported: Boolean = true,
        runtimeUnsupportedReason: String? = null,
    ): UserscriptPageRuntimeStatus =
        when {
            !runtimeSupported ->
                UserscriptPageRuntimeStatus(
                    state = UserscriptPageRuntimeState.UNSUPPORTED,
                    detail = runtimeUnsupportedReason,
                )
            !script.enabled ->
                UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.DISABLED)
            !userScriptsAllowed ->
                UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.PERMISSION_REQUIRED)
            !siteScriptsAllowed ->
                UserscriptPageRuntimeStatus(
                    state = UserscriptPageRuntimeState.PERMISSION_REQUIRED,
                    detail = "当前网站配置已禁用用户脚本",
                )
            script.blockedReasons.isNotEmpty() ->
                UserscriptPageRuntimeStatus(
                    state = UserscriptPageRuntimeState.UNSUPPORTED,
                    detail = script.blockedReasons.joinToString(),
                )
            pageUrl.isNullOrBlank() || pageUrl == "about:blank" ->
                UserscriptPageRuntimeStatus(
                    state = UserscriptPageRuntimeState.NO_ACTIVE_PAGE,
                )
            else -> {
                val result =
                    UserscriptMatcher.diagnose(
                        metadata = script.toParsedMetadata(),
                        pageUrl = pageUrl,
                        isTopFrame = true,
                    )
                UserscriptPageRuntimeStatus(
                    state =
                        if (result.matches) {
                            UserscriptPageRuntimeState.MATCHED
                        } else {
                            UserscriptPageRuntimeState.NOT_MATCHED
                        },
                    detail = result.detail,
                )
            }
        }
}

internal fun UserscriptListItem.toParsedMetadata(): ParsedUserscriptMetadata =
    ParsedUserscriptMetadata(
        name = name,
        namespace = namespace,
        version = version,
        description = description,
        homepage = homepage,
        website = website,
        supportUrl = supportUrl,
        downloadUrl = downloadUrl,
        updateUrl = updateUrl,
        runAt = runAt,
        grants = grants,
        matches = matches,
        includes = includes,
        excludes = excludes,
        excludeMatches = excludeMatches,
        connects = connects,
        requires = requires,
        resources = resources,
        icons = icons,
        tags = tags,
        injectInto = injectInto,
        sandbox = sandbox,
        runIn = runIn,
        noFrames = noFrames,
        unwrap = unwrap,
        webRequestRules = webRequestRules,
    )
