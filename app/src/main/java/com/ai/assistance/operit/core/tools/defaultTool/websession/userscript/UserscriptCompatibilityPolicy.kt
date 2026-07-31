package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript

internal object UserscriptCompatibilityPolicy {
    fun blockedReasons(
        metadata: ParsedUserscriptMetadata,
        runtimeCapabilities: UserscriptRuntimeCapabilities,
    ): List<String> =
        (
            UserscriptCapabilityRegistry.blockedReasons(metadata.grants) +
                UserscriptExecutionWorldPolicy
                    .resolve(metadata, runtimeCapabilities)
                    .blockedReasons
            ).distinct()
}
