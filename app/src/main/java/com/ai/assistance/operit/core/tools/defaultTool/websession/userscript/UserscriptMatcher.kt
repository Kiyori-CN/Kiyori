package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript

import java.net.IDN
import java.net.URI
import java.util.Locale

internal enum class UserscriptMatchReason {
    NO_PAGE_URL,
    CHILD_FRAME_BLOCKED,
    EXCLUDED_BY_MATCH,
    EXCLUDED_BY_INCLUDE,
    MATCHED_ALL_PAGES,
    MATCHED_BY_MATCH,
    MATCHED_BY_INCLUDE,
    NO_POSITIVE_RULE_MATCH,
}

internal data class UserscriptMatchResult(
    val matches: Boolean,
    val reason: UserscriptMatchReason,
    val rule: String? = null,
) {
    val detail: String
        get() =
            when (reason) {
                UserscriptMatchReason.NO_PAGE_URL -> ""
                UserscriptMatchReason.CHILD_FRAME_BLOCKED -> "@noframes"
                UserscriptMatchReason.EXCLUDED_BY_MATCH ->
                    "@exclude-match · ${rule.orEmpty()}"
                UserscriptMatchReason.EXCLUDED_BY_INCLUDE ->
                    "@exclude · ${rule.orEmpty()}"
                UserscriptMatchReason.MATCHED_ALL_PAGES ->
                    "@match · *"
                UserscriptMatchReason.MATCHED_BY_MATCH -> "@match · ${rule.orEmpty()}"
                UserscriptMatchReason.MATCHED_BY_INCLUDE -> "@include · ${rule.orEmpty()}"
                UserscriptMatchReason.NO_POSITIVE_RULE_MATCH ->
                    "@match / @include"
            }
}

internal object UserscriptMatcher {
    fun matches(
        metadata: ParsedUserscriptMetadata,
        pageUrl: String,
        isTopFrame: Boolean,
    ): Boolean =
        diagnose(metadata, pageUrl, isTopFrame).matches

    fun diagnose(
        metadata: ParsedUserscriptMetadata,
        pageUrl: String,
        isTopFrame: Boolean,
    ): UserscriptMatchResult {
        if (metadata.noFrames && !isTopFrame) {
            return UserscriptMatchResult(
                matches = false,
                reason = UserscriptMatchReason.CHILD_FRAME_BLOCKED,
            )
        }
        if (pageUrl.isBlank()) {
            return UserscriptMatchResult(
                matches = false,
                reason = UserscriptMatchReason.NO_PAGE_URL,
            )
        }
        metadata.excludeMatches.firstOrNull { matchPattern(it, pageUrl) }?.let { rule ->
            return UserscriptMatchResult(
                matches = false,
                reason = UserscriptMatchReason.EXCLUDED_BY_MATCH,
                rule = rule,
            )
        }
        metadata.excludes.firstOrNull { includePattern(it, pageUrl) }?.let { rule ->
            return UserscriptMatchResult(
                matches = false,
                reason = UserscriptMatchReason.EXCLUDED_BY_INCLUDE,
                rule = rule,
            )
        }

        if (metadata.matches.isEmpty() && metadata.includes.isEmpty()) {
            return UserscriptMatchResult(
                matches = true,
                reason = UserscriptMatchReason.MATCHED_ALL_PAGES,
            )
        }
        metadata.matches.firstOrNull { matchPattern(it, pageUrl) }?.let { rule ->
            return UserscriptMatchResult(
                matches = true,
                reason = UserscriptMatchReason.MATCHED_BY_MATCH,
                rule = rule,
            )
        }
        metadata.includes.firstOrNull { includePattern(it, pageUrl) }?.let { rule ->
            return UserscriptMatchResult(
                matches = true,
                reason = UserscriptMatchReason.MATCHED_BY_INCLUDE,
                rule = rule,
            )
        }
        return UserscriptMatchResult(
            matches = false,
            reason = UserscriptMatchReason.NO_POSITIVE_RULE_MATCH,
        )
    }

    fun isConnectAllowed(
        metadata: ParsedUserscriptMetadata,
        pageUrl: String,
        targetUrl: String,
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
        val targetHost = normalizedHost(hostOf(targetUri)) ?: return false
        val targetPort = effectivePort(targetUri)
        return metadata.connects.any { rule ->
            val normalizedRule = rule.trim()
            when {
                normalizedRule == "*" -> true
                normalizedRule.equals("self", ignoreCase = true) ->
                    pageOrigin != null && pageOrigin == targetOrigin
                else -> connectRuleMatches(normalizedRule, targetHost, targetPort)
            }
        }
    }

    private fun matchPattern(
        pattern: String,
        url: String,
    ): Boolean {
        val trimmed = pattern.trim()
        if (trimmed.isBlank()) {
            return false
        }
        val uri = parseUri(url) ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
        if (trimmed == "<all_urls>") {
            return scheme in setOf("http", "https", "file", "ftp", "urn")
        }

        val schemeSeparator = trimmed.indexOf("://")
        if (schemeSeparator <= 0) {
            return false
        }
        val schemePattern = trimmed.substring(0, schemeSeparator)
        val authorityAndPath = trimmed.substring(schemeSeparator + 3)
        val pathStart = authorityAndPath.indexOf('/')
        val authorityPattern =
            if (pathStart >= 0) {
                authorityAndPath.substring(0, pathStart)
            } else {
                authorityAndPath
            }
        val pathPattern =
            if (pathStart >= 0) {
                authorityAndPath.substring(pathStart)
            } else {
                "/*"
            }

        if (!schemeMatches(schemePattern, scheme)) {
            return false
        }
        val authority = splitAuthorityPattern(authorityPattern) ?: return false
        val targetHost = normalizedHost(hostOf(uri)).orEmpty()
        if (!hostMatches(authority.host, targetHost, scheme)) {
            return false
        }
        if (!portMatches(authority.port, effectivePort(uri))) {
            return false
        }
        val targetPath = uri.rawPath?.takeIf(String::isNotBlank) ?: "/"
        return globToRegex(pathPattern).matches(targetPath)
    }

    private fun includePattern(
        pattern: String,
        url: String,
    ): Boolean {
        val trimmed = pattern.trim()
        if (trimmed.isBlank()) {
            return false
        }
        parseRegexPattern(trimmed)?.let { regex ->
            return runCatching { regex.containsMatchIn(url) }.getOrDefault(false)
        }
        val comparableUrl =
            if (trimmed.contains("://")) {
                url.substringBefore('#')
            } else {
                url
            }
        val comparablePattern =
            if (trimmed.contains("://")) {
                trimmed.substringBefore('#')
            } else {
                trimmed
            }
        return globToRegex(comparablePattern).matches(comparableUrl)
    }

    private fun parseRegexPattern(pattern: String): Regex? {
        if (!pattern.startsWith('/')) {
            return null
        }
        val closingSlash = findClosingRegexSlash(pattern)
        if (closingSlash <= 0) {
            return null
        }
        val flags = pattern.substring(closingSlash + 1)
        if (flags.any { it !in setOf('g', 'i', 'm', 's', 'u', 'y') }) {
            return null
        }
        val options =
            buildSet {
                if ('i' in flags) add(RegexOption.IGNORE_CASE)
                if ('m' in flags) add(RegexOption.MULTILINE)
                if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
            }
        return runCatching {
            Regex(pattern.substring(1, closingSlash), options)
        }.getOrNull()
    }

    private fun findClosingRegexSlash(pattern: String): Int {
        for (index in pattern.lastIndex downTo 1) {
            if (pattern[index] != '/') {
                continue
            }
            var precedingBackslashes = 0
            var cursor = index - 1
            while (cursor >= 0 && pattern[cursor] == '\\') {
                precedingBackslashes += 1
                cursor -= 1
            }
            if (precedingBackslashes % 2 == 0) {
                return index
            }
        }
        return -1
    }

    private fun schemeMatches(
        pattern: String,
        scheme: String,
    ): Boolean =
        when {
            pattern == "*" || pattern.equals("http*", ignoreCase = true) ->
                scheme == "http" || scheme == "https"
            else -> pattern.equals(scheme, ignoreCase = true)
        }

    private fun hostMatches(
        rawPattern: String,
        targetHost: String,
        scheme: String,
    ): Boolean {
        val pattern = normalizeHostPattern(rawPattern)
        if (scheme == "file" && pattern.isBlank()) {
            return targetHost.isBlank()
        }
        if (pattern == "*") {
            return targetHost.isNotBlank()
        }
        if (pattern.startsWith("*.")) {
            val suffix = pattern.removePrefix("*.")
            return targetHost == suffix || targetHost.endsWith(".$suffix")
        }
        if (pattern.startsWith('.')) {
            val suffix = pattern.removePrefix(".")
            return targetHost == suffix || targetHost.endsWith(".$suffix")
        }
        return if ('*' in pattern) {
            globToRegex(pattern).matches(targetHost)
        } else {
            targetHost == pattern
        }
    }

    private fun connectRuleMatches(
        rawRule: String,
        targetHost: String,
        targetPort: Int?,
    ): Boolean {
        val authorityText =
            if (rawRule.contains("://")) {
                rawRule.substringAfter("://").substringBefore('/').substringBefore('?')
            } else {
                rawRule.substringBefore('/').substringBefore('?')
            }
        val authority = splitAuthorityPattern(authorityText) ?: return false
        if (!portMatches(authority.port, targetPort)) {
            return false
        }
        val pattern = normalizeHostPattern(authority.host)
        if (pattern == "*") {
            return true
        }
        if (pattern.startsWith("*.")) {
            val suffix = pattern.removePrefix("*.")
            return targetHost == suffix || targetHost.endsWith(".$suffix")
        }
        if (pattern.startsWith('.')) {
            val suffix = pattern.removePrefix(".")
            return targetHost == suffix || targetHost.endsWith(".$suffix")
        }
        if ('*' in pattern) {
            return globToRegex(pattern).matches(targetHost)
        }
        if (isIpLiteral(pattern) || pattern == "localhost") {
            return targetHost == pattern
        }
        return targetHost == pattern || targetHost.endsWith(".$pattern")
    }

    private fun portMatches(
        portPattern: String?,
        targetPort: Int?,
    ): Boolean =
        when {
            portPattern == null -> true
            portPattern == "*" -> targetPort != null
            else -> portPattern.toIntOrNull() == targetPort
        }

    private data class AuthorityPattern(
        val host: String,
        val port: String?,
    )

    private fun splitAuthorityPattern(rawAuthority: String): AuthorityPattern? {
        val authority = rawAuthority.substringAfterLast('@')
        if (authority.startsWith('[')) {
            val closingBracket = authority.indexOf(']')
            if (closingBracket < 0) {
                return null
            }
            val host = authority.substring(1, closingBracket)
            val remainder = authority.substring(closingBracket + 1)
            val port =
                when {
                    remainder.isBlank() -> null
                    remainder.startsWith(':') -> remainder.removePrefix(":").takeIf(String::isNotBlank)
                    else -> return null
                }
            return AuthorityPattern(host = host, port = port)
        }
        val lastColon = authority.lastIndexOf(':')
        if (lastColon > 0 && authority.indexOf(':') == lastColon) {
            val portCandidate = authority.substring(lastColon + 1)
            if (portCandidate == "*" || portCandidate.all(Char::isDigit)) {
                return AuthorityPattern(
                    host = authority.substring(0, lastColon),
                    port = portCandidate,
                )
            }
        }
        return AuthorityPattern(host = authority, port = null)
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

    private fun hostOf(uri: URI): String? {
        uri.host?.let { return it }
        val authority = uri.rawAuthority?.substringAfterLast('@') ?: return null
        if (authority.startsWith('[')) {
            val closingBracket = authority.indexOf(']')
            return authority.substring(1, closingBracket.takeIf { it > 1 } ?: return null)
        }
        return if (authority.count { it == ':' } == 1) {
            authority.substringBefore(':')
        } else {
            authority
        }
    }

    private fun normalizedHost(rawHost: String?): String? {
        val host =
            rawHost
                ?.trim()
                ?.removePrefix("[")
                ?.removeSuffix("]")
                ?.trimEnd('.')
                ?.lowercase(Locale.ROOT)
                ?: return null
        if (host.isBlank()) {
            return ""
        }
        return if (isIpLiteral(host)) {
            host
        } else {
            runCatching { IDN.toASCII(host).lowercase(Locale.ROOT) }.getOrDefault(host)
        }
    }

    private fun normalizeHostPattern(rawPattern: String): String {
        val pattern = rawPattern.trim().trimEnd('.').lowercase(Locale.ROOT)
        val prefix =
            when {
                pattern.startsWith("*.") -> "*."
                pattern.startsWith('.') -> "."
                else -> ""
            }
        val body = pattern.removePrefix("*.").removePrefix(".")
        if (body.isBlank() || body == "*" || '*' in body || isIpLiteral(body)) {
            return pattern
        }
        return prefix + runCatching { IDN.toASCII(body).lowercase(Locale.ROOT) }.getOrDefault(body)
    }

    private fun isIpLiteral(host: String): Boolean =
        host.contains(':') || host.matches(Regex("""\d{1,3}(?:\.\d{1,3}){3}"""))

    private fun effectivePort(uri: URI): Int? =
        when {
            uri.port >= 0 -> uri.port
            uri.scheme.equals("http", ignoreCase = true) -> 80
            uri.scheme.equals("https", ignoreCase = true) -> 443
            uri.scheme.equals("ftp", ignoreCase = true) -> 21
            else -> null
        }

    private fun originOf(uri: URI): String? {
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = normalizedHost(hostOf(uri)) ?: return null
        val port = effectivePort(uri)
        val defaultPort =
            (scheme == "http" && port == 80) ||
                (scheme == "https" && port == 443) ||
                (scheme == "ftp" && port == 21)
        return if (port == null || defaultPort) {
            "$scheme://$host"
        } else {
            "$scheme://$host:$port"
        }
    }
}
