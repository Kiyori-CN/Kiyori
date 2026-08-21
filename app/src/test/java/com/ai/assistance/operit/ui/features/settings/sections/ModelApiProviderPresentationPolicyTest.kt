package com.ai.assistance.operit.ui.features.settings.sections

import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ApiProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelApiProviderPresentationPolicyTest {
    @Test
    fun `compatibility provider ids collapse to canonical visible suppliers`() {
        assertEquals(
            ApiProviderType.OPENAI,
            ModelApiProviderPresentationPolicy.canonicalProvider(ApiProviderType.OPENAI),
        )
        assertEquals(
            ProviderEndpointKind.OFFICIAL,
            ModelApiProviderPresentationPolicy.endpointKind(ApiProviderType.OPENAI),
        )
        assertEquals(
            ApiProviderType.OPENAI,
            ModelApiProviderPresentationPolicy.canonicalProvider(
                ApiProviderType.OPENAI_GENERIC
            ),
        )
        assertEquals(
            ProviderSelectionSection.INTERNATIONAL,
            ModelApiProviderPresentationPolicy.section(ApiProviderType.OPENAI_GENERIC),
        )
        assertEquals(
            ApiProviderType.ANTHROPIC,
            ModelApiProviderPresentationPolicy.canonicalProvider(
                ApiProviderType.ANTHROPIC_GENERIC
            ),
        )
        assertEquals(
            ApiProviderType.GOOGLE,
            ModelApiProviderPresentationPolicy.canonicalProvider(
                ApiProviderType.GEMINI_GENERIC
            ),
        )
        assertFalse(
            ModelApiProviderPresentationPolicy.isVisibleProvider(
                ApiProviderType.OPENAI_RESPONSES
            )
        )
        assertFalse(
            ModelApiProviderPresentationPolicy.isVisibleProvider(
                ApiProviderType.ANTHROPIC_GENERIC
            )
        )
        assertEquals(
            ProviderEndpointKind.COMPATIBLE,
            ModelApiProviderPresentationPolicy.endpointKind(
                ApiProviderType.OPENAI_GENERIC
            ),
        )
    }

    @Test
    fun `visible providers are grouped in the fixed international domestic local order`() {
        val input =
            listOf(
                ApiProviderType.OTHER,
                ApiProviderType.OPENAI_RESPONSES_GENERIC,
                ApiProviderType.OLLAMA,
                ApiProviderType.NOVITA,
                ApiProviderType.BAIDU,
                ApiProviderType.ANTHROPIC,
                ApiProviderType.OPENAI,
                ApiProviderType.DEEPSEEK,
                ApiProviderType.MISTRAL,
                ApiProviderType.OPENAI_GENERIC,
            )

        assertEquals(
            listOf(
                ApiProviderType.OPENAI,
                ApiProviderType.ANTHROPIC,
                ApiProviderType.MISTRAL,
                ApiProviderType.NOVITA,
                ApiProviderType.DEEPSEEK,
                ApiProviderType.BAIDU,
                ApiProviderType.OLLAMA,
                ApiProviderType.OTHER,
            ),
            ModelApiProviderPresentationPolicy.orderBuiltInProviders(input),
        )
    }

    @Test
    fun `full visible provider order keeps international and domestic leaders fixed`() {
        assertEquals(
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
                ApiProviderType.LMSTUDIO,
                ApiProviderType.OLLAMA,
                ApiProviderType.OPENAI_LOCAL,
                ApiProviderType.MNN,
                ApiProviderType.LLAMA_CPP,
                ApiProviderType.OTHER,
            ),
            ModelApiProviderPresentationPolicy.orderBuiltInProviders(ApiProviderType.entries),
        )
    }

    @Test
    fun `protocol options keep supplier identity separate from wire protocol`() {
        assertEquals(
            listOf(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                ApiProtocol.OPENAI_RESPONSES,
            ),
            ModelApiProviderPresentationPolicy.protocolOptions(ApiProviderType.OPENAI)
                .map(ProviderProtocolOption::protocol),
        )
        assertEquals(
            listOf(
                ApiProtocol.ANTHROPIC_MESSAGES,
                ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            ),
            ModelApiProviderPresentationPolicy.protocolOptions(ApiProviderType.ANTHROPIC)
                .map(ProviderProtocolOption::protocol),
        )
        assertEquals(
            listOf(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                ApiProtocol.OPENAI_RESPONSES,
                ApiProtocol.ANTHROPIC_MESSAGES,
            ),
            ModelApiProviderPresentationPolicy.protocolOptions(ApiProviderType.DEEPSEEK)
                .map(ProviderProtocolOption::protocol),
        )
        assertEquals(
            listOf(
                ApiProtocol.PROVIDER_NATIVE,
                ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            ),
            ModelApiProviderPresentationPolicy.protocolOptions(ApiProviderType.GOOGLE)
                .map(ProviderProtocolOption::protocol),
        )
        assertEquals(
            listOf(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                ApiProtocol.ANTHROPIC_MESSAGES,
            ),
            ModelApiProviderPresentationPolicy.protocolOptions(ApiProviderType.NOVITA)
                .map(ProviderProtocolOption::protocol),
        )
        assertEquals(
            listOf(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                ApiProtocol.OPENAI_RESPONSES,
                ApiProtocol.ANTHROPIC_MESSAGES,
            ),
            ModelApiProviderPresentationPolicy.protocolOptions(ApiProviderType.OTHER)
                .map(ProviderProtocolOption::protocol),
        )
        assertEquals(
            listOf(ApiProtocol.OPENAI_CHAT_COMPLETIONS),
            ModelApiProviderPresentationPolicy.protocolOptions(ApiProviderType.MISTRAL)
                .map(ProviderProtocolOption::protocol),
        )
    }

    @Test
    fun `endpoint kind remains official only for canonical first party suppliers`() {
        assertEquals(
            ProviderEndpointKind.OFFICIAL,
            ModelApiProviderPresentationPolicy.endpointKind(ApiProviderType.ANTHROPIC),
        )
        assertEquals(
            ProviderEndpointKind.OFFICIAL,
            ModelApiProviderPresentationPolicy.endpointKind(ApiProviderType.GOOGLE),
        )
        assertEquals(
            ProviderEndpointKind.COMPATIBLE,
            ModelApiProviderPresentationPolicy.endpointKind(
                ApiProviderType.OPENAI_GENERIC
            ),
        )
    }

    @Test
    fun `rows retain ToolPkg providers in their own section`() {
        val options =
            listOf(
                option(
                    id = ApiProviderType.OPENAI.name,
                    section = ProviderSelectionSection.INTERNATIONAL,
                ),
                option(
                    id = "toolpkg.custom",
                    section = ProviderSelectionSection.TOOLPKG,
                ),
            )

        val input =
            listOf(
                ProviderSelectionRow.Header(ProviderSelectionSection.INTERNATIONAL),
                ProviderSelectionRow.Option(options[0]),
                ProviderSelectionRow.Header(ProviderSelectionSection.TOOLPKG),
                ProviderSelectionRow.Option(options[1]),
            )

        assertEquals(
            input,
            ModelApiProviderPresentationPolicy.buildRows(options),
        )
    }

    @Test
    fun `provider search includes display name summary and stable id`() {
        val option =
            ProviderSelectionOption(
                id = ApiProviderType.OPENAI.name,
                displayName = "OpenAI",
                summary = "Chat Completions and Responses",
                section = ProviderSelectionSection.INTERNATIONAL,
                endpointKind = ProviderEndpointKind.OFFICIAL,
            )

        assertEquals(
            ProviderSelectionSection.INTERNATIONAL,
            ModelApiProviderPresentationPolicy.section(ApiProviderType.OPENAI),
        )

        assertTrue(ModelApiProviderPresentationPolicy.matchesSearch(option, "Responses"))
        assertTrue(ModelApiProviderPresentationPolicy.matchesSearch(option, "OPENAI"))
        assertFalse(ModelApiProviderPresentationPolicy.matchesSearch(option, "Claude"))
    }

    private fun option(
        id: String,
        section: ProviderSelectionSection,
    ): ProviderSelectionOption =
        ProviderSelectionOption(
            id = id,
            displayName = id,
            summary = id,
            section = section,
            endpointKind = ProviderEndpointKind.OTHER,
        )
}
