package com.ai.assistance.operit.ui.features.packages.screens

import java.util.concurrent.atomic.AtomicReference

/**
 * One-shot host request for opening the native ToolPkg environment editor.
 *
 * The requesting host service fixes the package identity before writing here. Compose DSL code
 * cannot provide a package name, which prevents one ToolPkg settings page from opening another
 * container's host-only or sensitive variables.
 */
internal object ToolPkgHostEnvironmentEditRequestStore {
    private val requestedPackageName = AtomicReference<String?>(null)

    fun request(packageName: String) {
        val normalizedPackageName = packageName.trim()
        require(normalizedPackageName.isNotEmpty()) {
            "ToolPkg environment editor package name must not be blank"
        }
        requestedPackageName.set(normalizedPackageName)
    }

    fun consume(): String? = requestedPackageName.getAndSet(null)
}
