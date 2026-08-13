package com.ai.assistance.operit.ui.features.settings.sections

import com.ai.assistance.operit.data.model.ApiProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelApiProviderPresentationPolicyTest {
    @Test
    fun `four persisted OpenAI providers map to protocol and endpoint semantics`() {
        assertEquals(
            ProviderSelectionSection.OPENAI_CHAT_COMPLETIONS,
            ModelApiProviderPresentationPolicy.section(ApiProviderType.OPENAI),
        )
        assertEquals(
            ProviderEndpointKind.OFFICIAL,
            ModelApiProviderPresentationPolicy.endpointKind(ApiProviderType.OPENAI),
        )
        assertEquals(
            ProviderSelectionSection.OPENAI_CHAT_COMPLETIONS,
            ModelApiProviderPresentationPolicy.section(ApiProviderType.OPENAI_GENERIC),
        )
        assertEquals(
            ProviderEndpointKind.COMPATIBLE,
            ModelApiProviderPresentationPolicy.endpointKind(
                ApiProviderType.OPENAI_GENERIC
            ),
        )
        assertEquals(
            ProviderSelectionSection.OPENAI_RESPONSES,
            ModelApiProviderPresentationPolicy.section(
                ApiProviderType.OPENAI_RESPONSES
            ),
        )
        assertEquals(
            ProviderEndpointKind.OFFICIAL,
            ModelApiProviderPresentationPolicy.endpointKind(
                ApiProviderType.OPENAI_RESPONSES
            ),
        )
        assertEquals(
            ProviderSelectionSection.OPENAI_RESPONSES,
            ModelApiProviderPresentationPolicy.section(
                ApiProviderType.OPENAI_RESPONSES_GENERIC
            ),
        )
        assertEquals(
            ProviderEndpointKind.COMPATIBLE,
            ModelApiProviderPresentationPolicy.endpointKind(
                ApiProviderType.OPENAI_RESPONSES_GENERIC
            ),
        )
    }

    @Test
    fun `OpenAI protocols use the fixed user facing order without dropping others`() {
        val input =
            listOf(
                ApiProviderType.ANTHROPIC,
                ApiProviderType.OPENAI_RESPONSES_GENERIC,
                ApiProviderType.OPENAI,
                ApiProviderType.OPENAI_RESPONSES,
                ApiProviderType.DEEPSEEK,
                ApiProviderType.OPENAI_GENERIC,
            )

        assertEquals(
            listOf(
                ApiProviderType.OPENAI,
                ApiProviderType.OPENAI_GENERIC,
                ApiProviderType.OPENAI_RESPONSES,
                ApiProviderType.OPENAI_RESPONSES_GENERIC,
                ApiProviderType.ANTHROPIC,
                ApiProviderType.DEEPSEEK,
            ),
            ModelApiProviderPresentationPolicy.orderBuiltInProviders(input),
        )
    }

    @Test
    fun `rows retain ToolPkg providers in their own section`() {
        val options =
            listOf(
                option(
                    id = ApiProviderType.OPENAI.name,
                    section = ProviderSelectionSection.OPENAI_CHAT_COMPLETIONS,
                ),
                option(
                    id = "toolpkg.custom",
                    section = ProviderSelectionSection.TOOLPKG,
                ),
            )

        assertEquals(
            listOf(
                ProviderSelectionRow.Header(
                    ProviderSelectionSection.OPENAI_CHAT_COMPLETIONS
                ),
                ProviderSelectionRow.Option(options[0]),
                ProviderSelectionRow.Header(ProviderSelectionSection.TOOLPKG),
                ProviderSelectionRow.Option(options[1]),
            ),
            ModelApiProviderPresentationPolicy.buildRows(options),
        )
    }

    @Test
    fun `provider search includes display name summary and stable id`() {
        val option =
            ProviderSelectionOption(
                id = ApiProviderType.OPENAI_RESPONSES_GENERIC.name,
                displayName = "OpenAI Responses (Compatible endpoint)",
                summary = "Responses API · Custom endpoint",
                section = ProviderSelectionSection.OPENAI_RESPONSES,
                endpointKind = ProviderEndpointKind.COMPATIBLE,
            )

        assertTrue(ModelApiProviderPresentationPolicy.matchesSearch(option, "Responses"))
        assertTrue(ModelApiProviderPresentationPolicy.matchesSearch(option, "custom"))
        assertTrue(
            ModelApiProviderPresentationPolicy.matchesSearch(
                option,
                "OPENAI_RESPONSES_GENERIC",
            )
        )
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
