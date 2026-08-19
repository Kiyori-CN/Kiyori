package com.ai.assistance.operit.ui.main.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBookmarkSheet
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionHistorySheet
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KiyoriBrowserBackOwnershipAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun historySheetComposesOutsideBrowserProviderWithExplicitBackOwner() {
        var compositionCompleted = false

        composeTestRule.setContent {
            MaterialTheme {
                WebSessionHistorySheet(
                    entries = emptyList(),
                    bookmarkFolders = emptyList(),
                    systemBackEnabled = true,
                    onOpenEntry = { false },
                    onOpenWebUrl = {},
                    onBookmarkMutation = {},
                    onDeleteHistory = { _, _ -> },
                    onDeleteHistoryEntries = {},
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

    @Test
    fun bookmarkSheetComposesOutsideBrowserProviderWithExplicitBackOwner() {
        var compositionCompleted = false

        composeTestRule.setContent {
            MaterialTheme {
                WebSessionBookmarkSheet(
                    folders = emptyList(),
                    bookmarks = emptyList(),
                    systemBackEnabled = true,
                    onMutation = {},
                    onOpenBookmark = {},
                    onOpenBookmarkInTab = { _, _ -> },
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
