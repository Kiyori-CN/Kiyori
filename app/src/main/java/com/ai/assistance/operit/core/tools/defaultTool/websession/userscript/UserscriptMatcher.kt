package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript

import java.net.URI
import java.util.Locale

internal object UserscriptMatcher {
    fun matches(
        metadata: ParsedUserscriptMetadata,
        pageUrl: String,
        isTopFrame: Boolean
    ): Boolean {
        if (metadata.noFrames && !isTopFrame) {
            return false
        }
        if (pageUrl.isBlank()) {
            return false
        }
        if (metadata.excludeMatches.any { matchPattern(it, pageUrl) }) {
            return false
        }
        if (metadata.excludes.any { includePattern(it, pageUrl) }) {
            return false
        }

        val hasPositiveRules = metadata.matches.isNotEmpty() || metadata.includes.isNotEmpty()
        return !hasPositiveRules ||
            metadata.matches.any { matchPattern(it, pageUrl) } ||
            metadata.includes.any { includePattern(it, pageUrl) }
    }

    fun isConnectAllowed(
        metadata: ParsedUserscriptMetadata,
        pageUrl: String,
        targetUrl: String
    ): Boolean {
        val pageUri = parseUri(pageUrl) ?: return false
        val targetUri = parseUri(targetUrl) ?: return false
        val pageOrigin = originOf(pageUri)
        val targetOrigin = originOf(targetUri)
        if (pageOrigin != null && targetOrigin != null && pageOrigin == targetOrigin) {
            return true
        }
        if (metadata.connects.isEmpty()) {
            return false
        }
        val targetHost = targetUri.host?.lowercase(Locale.ROOT).orEmpty()
        return metadata.connects.any { rule ->
            when (val normalized = rule.trim().lowercase(Locale.ROOT)) {
                "*" -> true
                "self" -> pageOrigin != null && pageOrigin == targetOrigin
                targetHost -> true
                else -> {
                    if (normalized.startsWith("*.")) {
                        val suffix = normalized.removePrefix("*.")
                        targetHost == suffix || targetHost.endsWith(".$suffix")
                    } else {
                        false
                    }
                }
            }
        }
    }

    private fun matchPattern(pattern: String, url: String): Boolean {
        val trimmed = pattern.trim()
        if (trimmed.isBlank()) {
            return false
        }
        if (trimmed == "<all_urls>") {
            return parseUri(url)
                ?.scheme
                ?.lowercase(Locale.ROOT)
                ?.let { scheme -> scheme in setOf("http", "https", "file", "ftp") }
                ?: false
        }
        return runCatching {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            val fullPath = uri.rawPath?.takeIf { it.isNotBlank() } ?: "/"

            val schemePart = trimmed.substringBefore("://")
            val afterScheme = trimmed.substringAfter("://", "")
            val hostPart = afterScheme.substringBefore("/")
            val pathPart = afterScheme.substringAfter("/", "")

            val schemeMatches =
                when (schemePart) {
                    "*" -> scheme == "http" || scheme == "https"
                    else -> schemePart.equals(scheme, ignoreCase = true)
                }
            if (!schemeMatches) {
                return false
            }

            val normalizedHost = hostPart.lowercase(Locale.ROOT)
            val hostMatches =
                when {
                    scheme == "file" && normalizedHost.isBlank() -> host.isBlank()
                    normalizedHost == "*" -> host.isNotBlank()
                    normalizedHost.startsWith("*.") -> {
                        val suffix = normalizedHost.removePrefix("*.")
                        host == suffix || host.endsWith(".$suffix")
                    }
                    else -> host == normalizedHost
                }
            if (!hostMatches) {
                return false
            }

            val regex = globToRegex("/$pathPart")
            regex.matches(fullPath)
        }.getOrDefault(false)
    }

    private fun includePattern(pattern: String, url: String): Boolean {
        val trimmed = pattern.trim()
        if (trimmed.isBlank()) {
            return false
        }
        if (trimmed.length >= 2 && trimmed.startsWith('/') && trimmed.endsWith('/')) {
            return runCatching {
                Regex(trimmed.substring(1, trimmed.length - 1)).containsMatchIn(url)
            }.getOrDefault(false)
        }
        return globToRegex(trimmed).matches(url)
    }

    private fun globToRegex(pattern: String): Regex {
        val builder = StringBuilder("^")
        pattern.forEach { ch ->
            when (ch) {
                '*' -> builder.append(".*")
                '.', '?', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|', '\\' ->
                    builder.append('\\').append(ch)
                else -> builder.append(ch)
            }
        }
        builder.append('$')
        return Regex(builder.toString())
    }

    private fun parseUri(rawUrl: String): URI? =
        runCatching { URI(rawUrl.trim()) }.getOrNull()

    private fun originOf(uri: URI): String? {
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val portPart =
            when {
                uri.port < 0 -> ""
                scheme == "http" && uri.port == 80 -> ""
                scheme == "https" && uri.port == 443 -> ""
                else -> ":${uri.port}"
            }
        return "$scheme://$host$portPart"
    }
}
