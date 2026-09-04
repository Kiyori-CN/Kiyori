package com.kiyori.capability.extensions.market

private const val PLACEHOLDER_MARKET_ARTIFACT_ID = "artifact"
private val NON_ALPHANUMERIC_RX = Regex("[^a-z0-9]+")
private val MULTI_DASH_RX = Regex("-+")

// API、发布与本地安装必须使用同一身份规则，否则同一个包会被识别为不同项目。
fun normalizeMarketArtifactId(raw: String): String {
    val normalized =
        raw.trim()
            .lowercase()
            .replace(NON_ALPHANUMERIC_RX, "-")
            .replace(MULTI_DASH_RX, "-")
            .trim('-')
    return normalized.ifBlank { PLACEHOLDER_MARKET_ARTIFACT_ID }
}

fun isPlaceholderMarketArtifactId(raw: String): Boolean {
    return normalizeMarketArtifactId(raw) == PLACEHOLDER_MARKET_ARTIFACT_ID
}

fun requiresStandaloneArtifactIdUpgrade(runtimePackageId: String): Boolean {
    val trimmed = runtimePackageId.trim()
    return trimmed.isNotBlank() &&
        isPlaceholderMarketArtifactId(trimmed) &&
        !trimmed.equals(PLACEHOLDER_MARKET_ARTIFACT_ID, ignoreCase = true)
}

fun validateStandaloneArtifactRuntimePackageId(runtimePackageId: String) {
    require(!requiresStandaloneArtifactIdUpgrade(runtimePackageId)) {
        "当前包 ID「$runtimePackageId」无法生成稳定的市场项目 ID。请改用包含英文字母或数字的包 ID（可含 -、_、.），再重新发布。"
    }
}

fun sameArtifactRuntimePackageId(
    left: String,
    right: String
): Boolean {
    val trimmedLeft = left.trim()
    val trimmedRight = right.trim()
    if (trimmedLeft.isBlank() || trimmedRight.isBlank()) {
        return false
    }
    return trimmedLeft.equals(trimmedRight, ignoreCase = true) ||
        normalizeMarketArtifactId(trimmedLeft) == normalizeMarketArtifactId(trimmedRight)
}
