package com.ai.assistance.operit.ui.features.settings.sections

import com.ai.assistance.operit.data.collects.ApiProviderConfigs
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ApiProtocol
import java.util.Locale

internal enum class ProviderSelectionSection {
    INTERNATIONAL,
    DOMESTIC,
    LOCAL_AND_CUSTOM,
    TOOLPKG,
}

internal enum class ProviderEndpointKind {
    OFFICIAL,
    COMPATIBLE,
    OTHER,
}

internal data class ProviderSelectionOption(
    val id: String,
    val displayName: String,
    val summary: String,
    val section: ProviderSelectionSection,
    val endpointKind: ProviderEndpointKind,
)

internal data class ProviderProtocolOption(
    val protocol: ApiProtocol,
)

internal sealed interface ProviderSelectionRow {
    data class Header(
        val section: ProviderSelectionSection,
    ) : ProviderSelectionRow

    data class Option(
        val provider: ProviderSelectionOption,
    ) : ProviderSelectionRow
}

internal object ModelApiProviderPresentationPolicy {
    private val hiddenCompatibilityProviders =
        listOf(
            ApiProviderType.OPENAI_RESPONSES,
            ApiProviderType.OPENAI_GENERIC,
            ApiProviderType.OPENAI_RESPONSES_GENERIC,
            ApiProviderType.ANTHROPIC_GENERIC,
            ApiProviderType.GEMINI_GENERIC,
        )

    private val internationalProviderOrder =
        listOf(
            ApiProviderType.OPENAI,
            ApiProviderType.ANTHROPIC,
            ApiProviderType.GOOGLE,
            ApiProviderType.MISTRAL,
            ApiProviderType.OPENROUTER,
            ApiProviderType.FOUR_ROUTER,
            ApiProviderType.NOUS_PORTAL,
            ApiProviderType.NVIDIA,
            ApiProviderType.NOVITA,
        )

    private val domesticProviderOrder =
        listOf(
            ApiProviderType.DEEPSEEK,
            ApiProviderType.ALIYUN,
            ApiProviderType.BAIDU,
            ApiProviderType.XUNFEI,
            ApiProviderType.ZHIPU,
            ApiProviderType.BAICHUAN,
            ApiProviderType.MOONSHOT,
            ApiProviderType.MIMO,
            ApiProviderType.SILICONFLOW,
            ApiProviderType.IFLOW,
            ApiProviderType.INFINIAI,
            ApiProviderType.ALIPAY_BAILING,
            ApiProviderType.DOUBAO,
            ApiProviderType.PPINFRA,
        )

    private val localAndCustomProviderOrder =
        listOf(
            ApiProviderType.LMSTUDIO,
            ApiProviderType.OLLAMA,
            ApiProviderType.OPENAI_LOCAL,
            ApiProviderType.MNN,
            ApiProviderType.LLAMA_CPP,
            ApiProviderType.OTHER,
        )

    fun canonicalProvider(provider: ApiProviderType): ApiProviderType =
        when (provider) {
            ApiProviderType.OPENAI_RESPONSES,
            ApiProviderType.OPENAI_RESPONSES_GENERIC,
            ApiProviderType.OPENAI_GENERIC -> ApiProviderType.OPENAI
            ApiProviderType.ANTHROPIC_GENERIC -> ApiProviderType.ANTHROPIC
            ApiProviderType.GEMINI_GENERIC -> ApiProviderType.GOOGLE
            else -> provider
        }

    fun isVisibleProvider(provider: ApiProviderType): Boolean =
        provider !in hiddenCompatibilityProviders

    fun section(provider: ApiProviderType): ProviderSelectionSection =
        when (canonicalProvider(provider)) {
            in internationalProviderOrder -> ProviderSelectionSection.INTERNATIONAL
            in domesticProviderOrder -> ProviderSelectionSection.DOMESTIC
            else -> ProviderSelectionSection.LOCAL_AND_CUSTOM
        }

    fun endpointKind(provider: ApiProviderType): ProviderEndpointKind =
        when (provider) {
            ApiProviderType.OPENAI,
            ApiProviderType.ANTHROPIC,
            ApiProviderType.GOOGLE ->
                ProviderEndpointKind.OFFICIAL

            ApiProviderType.OPENAI_GENERIC,
            ApiProviderType.OPENAI_RESPONSES_GENERIC,
            ApiProviderType.ANTHROPIC_GENERIC,
            ApiProviderType.GEMINI_GENERIC ->
                ProviderEndpointKind.COMPATIBLE

            else -> ProviderEndpointKind.OTHER
        }

    fun defaultProtocol(provider: ApiProviderType): ApiProtocol =
        ApiProviderConfigs.getDefaultProtocol(canonicalProvider(provider))

    fun protocolOptions(provider: ApiProviderType): List<ProviderProtocolOption> {
        return ApiProviderConfigs
            .getSupportedProtocols(canonicalProvider(provider))
            .map(::ProviderProtocolOption)
    }

    fun formatSettingsSaveLog(
        provider: ApiProviderType,
        modelCount: Int,
        credentialConfigured: Boolean,
    ): String {
        require(modelCount >= 0) { "modelCount must not be negative" }
        return "保存API设置: " +
            "provider_type=${provider.name}, " +
            "model_count=$modelCount, " +
            "credential_configured=$credentialConfigured, " +
            "endpoint_kind=${endpointKind(provider).name.lowercase(Locale.ROOT)}"
    }

    fun orderBuiltInProviders(
        providers: List<ApiProviderType>,
    ): List<ApiProviderType> {
        val visibleProviders =
            providers
                .filter(::isVisibleProvider)
                .map(::canonicalProvider)
                .distinct()
        val preferredOrder =
            internationalProviderOrder +
                domesticProviderOrder +
                localAndCustomProviderOrder
        return preferredOrder.filter(visibleProviders::contains) +
            visibleProviders.filterNot(preferredOrder::contains)
    }

    fun buildRows(
        options: List<ProviderSelectionOption>,
    ): List<ProviderSelectionRow> =
        buildList {
            ProviderSelectionSection.entries.forEach { section ->
                val sectionOptions =
                    options.filter { option -> option.section == section }
                if (sectionOptions.isNotEmpty()) {
                    add(ProviderSelectionRow.Header(section))
                    sectionOptions.forEach { option ->
                        add(ProviderSelectionRow.Option(option))
                    }
                }
            }
        }

    fun matchesSearch(
        option: ProviderSelectionOption,
        query: String,
    ): Boolean {
        val normalized = query.trim()
        return normalized.isEmpty() ||
            option.displayName.contains(normalized, ignoreCase = true) ||
            option.summary.contains(normalized, ignoreCase = true) ||
            option.id.contains(normalized, ignoreCase = true)
    }
}
