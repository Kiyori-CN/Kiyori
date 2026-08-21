package com.ai.assistance.operit.ui.features.settings.sections

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelApiSettingsSelectionSheetContractTest {
    @Test
    fun providerAndProtocolSelectorsUseSingleOwnerBottomSheets() {
        val source = settingsSource()
        val providerSheet =
            source
                .substringAfter("private fun ApiProviderSelectionSheet(")
                .substringBefore("private fun getProviderColor(")
        val protocolSheet =
            source
                .substringAfter("private fun ApiProtocolSelectionSheet(")
                .substringBefore("private fun ApiProviderSelectionSheet(")

        assertTrue(providerSheet.contains("KiyoriModalBottomDrawer("))
        assertFalse(providerSheet.contains("ModalBottomSheet("))
        assertTrue(providerSheet.contains("var searchQuery by remember"))
        assertTrue(providerSheet.contains("ModelApiProviderPresentationPolicy.buildRows("))
        assertTrue(providerSheet.contains("val isSelected = provider.id == selectedProviderTypeId"))
        assertTrue(providerSheet.contains("Icons.Default.Check"))
        assertFalse(providerSheet.contains("Dialog("))

        assertTrue(protocolSheet.contains("KiyoriModalBottomDrawer("))
        assertFalse(protocolSheet.contains("ModalBottomSheet("))
        assertTrue(protocolSheet.contains(".heightIn(min = 280.dp, max = 620.dp)"))
        assertTrue(protocolSheet.contains(".weight(1f, fill = false)"))
        assertFalse(protocolSheet.contains("Dialog("))
        assertFalse(protocolSheet.contains("onAutoDetect"))
        assertFalse(protocolSheet.contains("detectionMessage"))
        assertFalse(source.contains("ProviderProtocolDetectionResult"))
        assertFalse(source.contains("protocolDetectionMessage"))
        assertFalse(source.contains("api_protocol_auto_detect"))
    }

    @Test
    fun internationalProviderWarningIsInlineOnlyAndUsesPresentationGrouping() {
        val source = settingsSource()
        val warningEffect =
            source
                .substringAfter("LaunchedEffect(selectedApiProvider)")
                .substringBefore("// 当API提供商或协议改变时更新端点。")

        assertTrue(warningEffect.contains("ModelApiProviderPresentationPolicy::section"))
        assertTrue(warningEffect.contains("ProviderSelectionSection.INTERNATIONAL"))
        assertTrue(warningEffect.contains("LocationUtils.isDeviceInMainlandChina(context)"))
        assertFalse(warningEffect.contains("showNotification("))
        assertFalse(
            source.contains(
                "showNotification(resources.getString(R.string.overseas_provider_warning))"
            )
        )
    }

    private fun settingsSource(): String {
        return repositoryFile(
            "app/src/main/java/com/ai/assistance/operit/ui/features/settings/sections/ModelApiSettingsSection.kt"
        ).readText()
    }

    private fun repositoryFile(relativePath: String): File {
        var current: File? =
            File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(4) {
            val candidate = current?.let { directory -> File(directory, relativePath) }
            if (candidate?.isFile == true) {
                return candidate
            }
            current = current?.parentFile
        }
        throw AssertionError("Repository file not found: $relativePath")
    }
}
