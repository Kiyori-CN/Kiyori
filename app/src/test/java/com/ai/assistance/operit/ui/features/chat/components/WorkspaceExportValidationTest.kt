package com.ai.assistance.operit.ui.features.chat.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceExportValidationTest {
    @Test fun packageNameUsesAndroidIdentifiersWithoutSilentlyRemovingCharacters() {
        assertTrue(validExportPackageName("com.example.My_App"))
        listOf("app", "com..app", "com.9app", "com.example ", "com.示例").forEach { assertFalse(it, validExportPackageName(it)) }
    }
    @Test fun betaVersionIsAcceptedAndVersionCodeMustFitPositiveInt() {
        assertTrue(validExportVersionName("1.2.3-beta"))
        assertFalse(validExportVersionName("1.2.3-"))
        assertTrue(validExportVersionCode("2147483647"))
        listOf("", "0", "-1", "2147483648", "１").forEach { assertFalse(it, validExportVersionCode(it)) }
    }
}
