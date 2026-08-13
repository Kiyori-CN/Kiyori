package com.ai.assistance.operit.ui.features.settings.sections

import com.ai.assistance.operit.data.model.ApiProviderType
import java.util.Locale

internal enum class ProviderSelectionSection {
    OPENAI_CHAT_COMPLETIONS,
    OPENAI_RESPONSES,
    OTHER_BUILT_IN,
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

internal sealed interface ProviderSelectionRow {
    data class Header(
        val section: ProviderSelectionSection,
    ) : ProviderSelectionRow

    data class Option(
        val provider: ProviderSelectionOption,
    ) : ProviderSelectionRow
}

internal object ModelApiProviderPresentationPolicy {
    private val openAIProviderOrder =
        listOf(
            ApiProviderType.OPENAI,
            ApiProviderType.OPENAI_GENERIC,
            ApiProviderType.OPENAI_RESPONSES,
            ApiProviderType.OPENAI_RESPONSES_GENERIC,
        )

    fun section(provider: ApiProviderType): ProviderSelectionSection =
        when (provider) {
            ApiProviderType.OPENAI,
            ApiProviderType.OPENAI_GENERIC ->
                ProviderSelectionSection.OPENAI_CHAT_COMPLETIONS

            ApiProviderType.OPENAI_RESPONSES,
            ApiProviderType.OPENAI_RESPONSES_GENERIC ->
                ProviderSelectionSection.OPENAI_RESPONSES

            else -> ProviderSelectionSection.OTHER_BUILT_IN
        }

    fun endpointKind(provider: ApiProviderType): ProviderEndpointKind =
        when (provider) {
            ApiProviderType.OPENAI,
            ApiProviderType.OPENAI_RESPONSES ->
                ProviderEndpointKind.OFFICIAL

            ApiProviderType.OPENAI_GENERIC,
            ApiProviderType.OPENAI_RESPONSES_GENERIC ->
                ProviderEndpointKind.COMPATIBLE

            else -> ProviderEndpointKind.OTHER
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
        val openAIProviders = openAIProviderOrder.filter(providers::contains)
        val otherProviders = providers.filterNot(openAIProviderOrder::contains)
        return openAIProviders + otherProviders
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
