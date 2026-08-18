package com.ai.assistance.operit.ui.main.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionIncognitoAvailability
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionProfile
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserSearchScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KiyoriFullScreenWebSearchAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun fullScreenSearchComposesWithItsExplicitBackOwner() {
        var compositionCompleted = false

        composeTestRule.setContent {
            MaterialTheme {
                WebSessionBrowserSearchScreen(
                    currentUrl = "",
                    currentTitle = "",
                    searchEngine = WebSessionSearchEngine.DEFAULT,
                    searchHistory = emptyList(),
                    draft = "",
                    isEnginePanelVisible = false,
                    onDraftChange = {},
                    onEnginePanelVisibleChange = {},
                    onBack = {},
                    systemBackEnabled = true,
                    onSubmit = {},
                    onSelectEngine = {},
                    onOpenSearchRecord = {},
                    onDeleteSearchRecord = {},
                    onClearSearchHistory = {},
                    onCopyCurrentUrl = {},
                    onOpenCurrentUrl = {},
                    onEditCurrentUrl = {},
                    selectedProfile = WebSessionProfile.NORMAL,
                    incognitoAvailability = WebSessionIncognitoAvailability.AVAILABLE,
                    onToggleProfile = {},
                    profileFeedback = null,
                )
                SideEffect {
                    compositionCompleted = true
                }
            }
        }

        composeTestRule.runOnIdle {
            assertTrue(compositionCompleted)
        }
    }
}
