package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript

import androidx.webkit.WebViewFeature

internal data class UserscriptRuntimeCapabilities(
    val pageWorldSupported: Boolean,
    val isolatedWorldSupported: Boolean,
    val unsafeWindowBridgeSupported: Boolean =
        pageWorldSupported && isolatedWorldSupported,
) {
    companion object {
        fun current(): UserscriptRuntimeCapabilities =
            UserscriptRuntimeCapabilities(
                pageWorldSupported =
                    WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
                        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER),
                isolatedWorldSupported =
                    WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD) &&
                        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER),
            )
    }
}

internal data class UserscriptExecutionWorldResolution(
    val world: UserscriptExecutionWorld?,
    val blockedReasons: List<String>,
    val unsafeWindowMode: UserscriptUnsafeWindowMode,
)

internal object UserscriptExecutionWorldPolicy {
    fun resolve(
        metadata: ParsedUserscriptMetadata,
        capabilities: UserscriptRuntimeCapabilities,
    ): UserscriptExecutionWorldResolution {
        val knownGrants = UserscriptCapabilityRegistry.knownGrants(metadata.grants).toSet()
        val hasUnsafeWindow = "unsafeWindow" in knownGrants
        val privilegedGrants = knownGrants - setOf("none", "unsafeWindow")
        val blockedReasons = mutableListOf<String>()
        if (metadata.runAt == UserscriptRunAt.UNSUPPORTED) {
            blockedReasons += "Unsupported @run-at value"
        }
        if (!metadata.sandbox.isNullOrBlank()) {
            blockedReasons += "@sandbox is not supported by the current userscript runtime"
        }
        if (!metadata.runIn.isNullOrBlank()) {
            blockedReasons += "@run-in is not supported by the current userscript runtime"
        }
        if (metadata.unwrap) {
            blockedReasons += "@unwrap is not supported by the current userscript runtime"
        }

        val world =
            when (metadata.injectInto) {
                UserscriptInjectInto.PAGE -> {
                    if (privilegedGrants.isNotEmpty()) {
                        blockedReasons += "@inject-into page cannot use privileged grants"
                        null
                    } else if (!capabilities.pageWorldSupported) {
                        blockedReasons += "Current WebView does not support page-world userscripts"
                        null
                    } else {
                        UserscriptExecutionWorld.PAGE
                    }
                }

                UserscriptInjectInto.CONTENT -> {
                    if (!capabilities.isolatedWorldSupported) {
                        blockedReasons += "Current WebView does not support isolated userscript worlds"
                        null
                    } else if (hasUnsafeWindow && !capabilities.unsafeWindowBridgeSupported) {
                        blockedReasons +=
                            "Current WebView cannot bridge unsafeWindow into an isolated userscript world"
                        null
                    } else {
                        UserscriptExecutionWorld.ISOLATED
                    }
                }

                UserscriptInjectInto.AUTO -> {
                    when {
                        hasUnsafeWindow && privilegedGrants.isNotEmpty() -> {
                            when {
                                !capabilities.isolatedWorldSupported -> {
                                    blockedReasons +=
                                        "Current WebView does not support isolated userscript worlds"
                                    null
                                }

                                !capabilities.unsafeWindowBridgeSupported -> {
                                    blockedReasons +=
                                        "Current WebView cannot bridge unsafeWindow into an isolated userscript world"
                                    null
                                }

                                else -> UserscriptExecutionWorld.ISOLATED
                            }
                        }

                        hasUnsafeWindow || privilegedGrants.isEmpty() -> {
                            if (!capabilities.pageWorldSupported) {
                                blockedReasons += "Current WebView does not support page-world userscripts"
                                null
                            } else {
                                UserscriptExecutionWorld.PAGE
                            }
                        }

                        !capabilities.isolatedWorldSupported -> {
                            blockedReasons += "Current WebView does not support isolated userscript worlds"
                            null
                        }

                        else -> UserscriptExecutionWorld.ISOLATED
                    }
                }

                UserscriptInjectInto.UNSUPPORTED -> {
                    blockedReasons += "Unsupported @inject-into value"
                    null
                }
            }

        val unsafeWindowMode =
            when {
                !hasUnsafeWindow || world == null -> UserscriptUnsafeWindowMode.NONE
                world == UserscriptExecutionWorld.PAGE -> UserscriptUnsafeWindowMode.DIRECT_PAGE
                else -> UserscriptUnsafeWindowMode.ISOLATED_PAGE_BRIDGE
            }
        return UserscriptExecutionWorldResolution(
            world = world,
            blockedReasons = blockedReasons,
            unsafeWindowMode = unsafeWindowMode,
        )
    }
}
