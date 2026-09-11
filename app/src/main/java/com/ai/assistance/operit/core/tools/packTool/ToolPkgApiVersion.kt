package com.ai.assistance.operit.core.tools.packTool

internal data class ToolPkgApiVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<ToolPkgApiVersion> {
    override fun compareTo(other: ToolPkgApiVersion): Int {
        return compareValuesBy(
            this,
            other,
            ToolPkgApiVersion::major,
            ToolPkgApiVersion::minor,
            ToolPkgApiVersion::patch
        )
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val VERSION_PATTERN = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$")

        fun parse(value: String): ToolPkgApiVersion {
            val normalized = value.trim()
            val match = VERSION_PATTERN.matchEntire(normalized)
                ?: throw IllegalArgumentException(
                    "ToolPkg API version must use major.minor.patch format: '$value'"
                )
            return ToolPkgApiVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt()
            )
        }
    }
}

internal object ToolPkgApiCompatibility {
    const val LEGACY_API_VERSION = "1.0.0"
    const val API_VERSION_1_0_1 = "1.0.1"

    // 支持集来自已实现的宿主能力，不依赖产品版本或市场的 minAppVer。
    // 只有 manifest 缺键才使用默认值；显式空值不能被当成旧版插件。
    fun parseDeclaredApiVersion(apiVersion: String): ToolPkgApiVersion = ToolPkgApiVersion.parse(apiVersion)

    fun supportedApiVersions(): List<ToolPkgApiVersion> =
        listOf(LEGACY_API_VERSION, API_VERSION_1_0_1).map(ToolPkgApiVersion::parse)

    fun supportedApiVersionText(): String = supportedApiVersions().joinToString(", ")

    fun requireSupported(apiVersion: String): ToolPkgApiVersion {
        val declared = parseDeclaredApiVersion(apiVersion)
        require(declared in supportedApiVersions()) {
            "ToolPkg API '$declared' is not supported by Kiyori. Supported: ${supportedApiVersionText()}."
        }
        return declared
    }
}
